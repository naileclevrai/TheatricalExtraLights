package com.github.dumann089.theatricalextralights.laser.dac;

import com.github.dumann089.theatricalextralights.TheatricalExtraLights;
import com.github.dumann089.theatricalextralights.config.TheatricalExtraLightsConfig;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Ether Dream playback engine: light-engine / playback / source state machines,
 * point buffer drained at the negotiated PPS, and a reconstructed {@link LaserFrame}.
 */
public final class EtherDreamDevice implements LaserDacDevice {

    public static final String DEFAULT_ID = "etherdream-0";

    private final String id;
    private final int capacity;
    private final LaserPoint[] buffer;
    private int head;
    private int fullness;

    private int lightEngineState = LaserProtocol.LIGHT_READY;
    private int playbackState = LaserProtocol.PLAYBACK_IDLE;
    private int lightEngineFlags;
    private int playbackFlags;
    private int sourceFlags;
    private int pointRate;
    private long pointCount;

    private final ArrayDeque<Integer> rateQueue = new ArrayDeque<>(LaserProtocol.RATE_QUEUE_SIZE);

    private final LaserPoint[] history;
    private int historyWrite;
    private int historySize;

    private final AtomicReference<LaserFrame> frame = new AtomicReference<>(LaserFrame.EMPTY);
    /** Publishing every playback tick (~1 ms) is wasted work: the renderer runs at ≤ 240 Hz. */
    private static final long PUBLISH_INTERVAL_NANOS = 4_000_000L;
    private long lastPublishNanos;

    private volatile boolean connected;
    private volatile String remoteHost = "";
    private volatile boolean listening;

    private final Object lock = new Object();
    private Thread playbackThread;
    private volatile boolean playbackRun;

    public EtherDreamDevice(String id, int capacity) {
        this.id = id;
        this.capacity = Math.max(256, capacity);
        this.buffer = new LaserPoint[this.capacity];
        // Persistence window at 100 kpps × 400 ms worst case: 40 k points.
        this.history = new LaserPoint[65536];
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public String protocolName() {
        return "Ether Dream";
    }

    @Override
    public LaserFrame currentFrame() {
        return frame.get();
    }

    @Override
    public LaserDacStatus status() {
        synchronized (lock) {
            return new LaserDacStatus(
                    listening, connected, lightEngineState, playbackState,
                    fullness, capacity, pointRate, pointCount, remoteHost
            );
        }
    }

    @Override
    public void start() {
        listening = true;
    }

    @Override
    public void stop() {
        listening = false;
        connected = false;
        remoteHost = "";
        synchronized (lock) {
            stopPlaybackLocked(false);
            resetStreamLocked();
            playbackState = LaserProtocol.PLAYBACK_IDLE;
        }
        frame.set(LaserFrame.EMPTY);
    }

    public void setListening(boolean value) {
        listening = value;
    }

    public void onHostConnected(String host) {
        connected = true;
        remoteHost = host == null ? "" : host;
    }

    public void onHostDisconnected() {
        connected = false;
        remoteHost = "";
        synchronized (lock) {
            stopPlaybackLocked(false);
            resetStreamLocked();
            playbackState = LaserProtocol.PLAYBACK_IDLE;
        }
        frame.set(LaserFrame.EMPTY);
    }

    public void writeStatus(byte[] out, int offset) {
        synchronized (lock) {
            writeStatusLocked(out, offset);
        }
    }

    public void writeResponse(byte[] out, byte response, byte command) {
        synchronized (lock) {
            out[0] = response;
            out[1] = command;
            writeStatusLocked(out, 2);
        }
    }

    public void writeBroadcast(byte[] out, byte[] mac) {
        System.arraycopy(mac, 0, out, 0, 6);
        writeU16(out, 6, TheatricalExtraLightsConfig.getEtherDreamHwRevision());
        writeU16(out, 8, TheatricalExtraLightsConfig.getEtherDreamSwRevision());
        writeU16(out, 10, capacity);
        writeU32(out, 12, TheatricalExtraLightsConfig.getEtherDreamMaxPointRate());
        synchronized (lock) {
            writeStatusLocked(out, 16);
        }
    }

    public byte prepare() {
        synchronized (lock) {
            if (lightEngineState != LaserProtocol.LIGHT_READY || playbackState != LaserProtocol.PLAYBACK_IDLE) {
                return LaserProtocol.NAK_INVALID;
            }
            resetStreamLocked();
            playbackFlags &= ~(LaserProtocol.PLAY_FLAG_UNDERFLOW | LaserProtocol.PLAY_FLAG_ESTOP);
            playbackState = LaserProtocol.PLAYBACK_PREPARED;
            return LaserProtocol.ACK;
        }
    }

    public byte begin(int newRate) {
        synchronized (lock) {
            if (playbackState != LaserProtocol.PLAYBACK_PREPARED || fullness <= 0) {
                return LaserProtocol.NAK_INVALID;
            }
            if (lightEngineState != LaserProtocol.LIGHT_READY) {
                return LaserProtocol.NAK_INVALID;
            }
            pointRate = clampRate(newRate);
            playbackState = LaserProtocol.PLAYBACK_PLAYING;
            playbackFlags |= LaserProtocol.PLAY_FLAG_SHUTTER;
            startPlaybackLocked();
            return LaserProtocol.ACK;
        }
    }

    public byte queueRate(int newRate) {
        synchronized (lock) {
            if (playbackState != LaserProtocol.PLAYBACK_PREPARED && playbackState != LaserProtocol.PLAYBACK_PLAYING) {
                return LaserProtocol.NAK_INVALID;
            }
            if (rateQueue.size() >= LaserProtocol.RATE_QUEUE_SIZE) {
                return LaserProtocol.NAK_FULL;
            }
            rateQueue.addLast(clampRate(newRate));
            return LaserProtocol.ACK;
        }
    }

    public byte writePoints(LaserPoint[] points) {
        synchronized (lock) {
            if (playbackState != LaserProtocol.PLAYBACK_PREPARED && playbackState != LaserProtocol.PLAYBACK_PLAYING) {
                return LaserProtocol.NAK_INVALID;
            }
            if (points.length == 0) {
                return LaserProtocol.ACK;
            }
            if (points.length > capacity - fullness) {
                return LaserProtocol.NAK_FULL;
            }
            for (LaserPoint point : points) {
                buffer[(head + fullness) % capacity] = point;
                fullness++;
            }
            return LaserProtocol.ACK;
        }
    }

    public byte stopCommand() {
        synchronized (lock) {
            if (playbackState != LaserProtocol.PLAYBACK_PREPARED && playbackState != LaserProtocol.PLAYBACK_PLAYING) {
                return LaserProtocol.NAK_INVALID;
            }
            stopPlaybackLocked(false);
            resetStreamLocked();
            playbackState = LaserProtocol.PLAYBACK_IDLE;
            playbackFlags &= ~LaserProtocol.PLAY_FLAG_SHUTTER;
            return LaserProtocol.ACK;
        }
    }

    public byte emergencyStop() {
        synchronized (lock) {
            stopPlaybackLocked(true);
            resetStreamLocked();
            lightEngineState = LaserProtocol.LIGHT_ESTOP;
            lightEngineFlags |= LaserProtocol.LE_FLAG_ESTOP_PACKET;
            playbackState = LaserProtocol.PLAYBACK_IDLE;
            playbackFlags = LaserProtocol.PLAY_FLAG_ESTOP;
            frame.set(LaserFrame.EMPTY);
            return LaserProtocol.ACK;
        }
    }

    public byte clearEstop() {
        synchronized (lock) {
            if (lightEngineState != LaserProtocol.LIGHT_ESTOP) {
                return LaserProtocol.NAK_INVALID;
            }
            lightEngineState = LaserProtocol.LIGHT_READY;
            lightEngineFlags = 0;
            playbackFlags &= ~LaserProtocol.PLAY_FLAG_ESTOP;
            return LaserProtocol.ACK;
        }
    }

    public byte ping() {
        return LaserProtocol.ACK;
    }

    private void startPlaybackLocked() {
        if (playbackRun) {
            return;
        }
        playbackRun = true;
        playbackThread = new Thread(this::playbackLoop, "tel-etherdream-playback");
        playbackThread.setDaemon(true);
        playbackThread.start();
    }

    private void stopPlaybackLocked(boolean estop) {
        playbackRun = false;
        Thread thread = playbackThread;
        playbackThread = null;
        if (thread != null && thread != Thread.currentThread()) {
            thread.interrupt();
        }
        if (estop) {
            playbackFlags |= LaserProtocol.PLAY_FLAG_ESTOP;
        }
        playbackFlags &= ~LaserProtocol.PLAY_FLAG_SHUTTER;
    }

    private void resetStreamLocked() {
        head = 0;
        fullness = 0;
        pointCount = 0;
        pointRate = 0;
        rateQueue.clear();
        historyWrite = 0;
        historySize = 0;
    }

    private void playbackLoop() {
        long lastNanos = System.nanoTime();
        double residual = 0;
        while (playbackRun) {
            try {
                int rate;
                synchronized (lock) {
                    rate = Math.max(1, pointRate);
                }
                long now = System.nanoTime();
                double due = residual + (now - lastNanos) * (rate / 1_000_000_000.0);
                int consume = (int) due;
                residual = due - consume;
                lastNanos = now;
                if (consume > 0) {
                    playPoints(consume);
                }
                Thread.sleep(1L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                TheatricalExtraLights.LOGGER.warn("[EtherDream] playback loop failed", e);
                return;
            }
        }
    }

    private void playPoints(int count) {
        boolean underflow = false;
        synchronized (lock) {
            if (playbackState != LaserProtocol.PLAYBACK_PLAYING) {
                return;
            }
            int played = 0;
            while (played < count) {
                if (fullness <= 0) {
                    underflow = true;
                    break;
                }
                LaserPoint point = buffer[head];
                buffer[head] = null;
                head = (head + 1) % capacity;
                fullness--;
                if (point != null && point.changeRate() && !rateQueue.isEmpty()) {
                    pointRate = rateQueue.removeFirst();
                }
                remember(point);
                pointCount++;
                played++;
            }
            if (underflow) {
                stopPlaybackLocked(false);
                resetStreamLocked();
                playbackState = LaserProtocol.PLAYBACK_IDLE;
                playbackFlags |= LaserProtocol.PLAY_FLAG_UNDERFLOW;
                playbackFlags &= ~LaserProtocol.PLAY_FLAG_SHUTTER;
            }
        }
        if (underflow) {
            frame.set(LaserFrame.EMPTY);
            return;
        }
        long now = System.nanoTime();
        if (now - lastPublishNanos < PUBLISH_INTERVAL_NANOS) {
            return;
        }
        lastPublishNanos = now;
        publishFrame();
    }

    private void remember(LaserPoint point) {
        if (point == null) {
            return;
        }
        history[historyWrite] = point;
        historyWrite = (historyWrite + 1) % history.length;
        if (historySize < history.length) {
            historySize++;
        }
    }

    private void publishFrame() {
        int blank = TheatricalExtraLightsConfig.getLaserDacBlankThreshold();
        int maxRays = TheatricalExtraLightsConfig.getLaserDacMaxRays();
        LaserPoint[] snapshot;
        int size;
        int rate;
        long count;
        synchronized (lock) {
            size = historySize;
            if (size <= 0) {
                return;
            }
            // Persistence window: long enough to hold one complete ILDA frame at any
            // sane frame rate, so the picture never shows as a rotating partial arc.
            long wanted = (long) pointRate * TheatricalExtraLightsConfig.getLaserDacPersistenceMs() / 1000L;
            int window = (int) Math.min(size, Math.max(64L, Math.min(wanted, history.length - 1)));
            snapshot = new LaserPoint[window];
            int start = (historyWrite - window + history.length) % history.length;
            for (int i = 0; i < window; i++) {
                snapshot[i] = history[(start + i) % history.length];
            }
            rate = pointRate;
            count = pointCount;
        }

        int visibleCount = 0;
        for (LaserPoint point : snapshot) {
            if (point != null && !point.isBlank(blank)) {
                visibleCount++;
            }
        }
        if (visibleCount == 0) {
            frame.set(new LaserFrame(new LaserSegment[0], rate, count, System.nanoTime()));
            return;
        }

        int step = visibleCount <= maxRays ? 1 : Math.max(1, (visibleCount + maxRays - 1) / maxRays);
        List<LaserSegment> segments = new ArrayList<>(maxRays);
        LaserPoint prev = null;
        int sinceKeep = 0;
        int window = snapshot.length;
        for (int idx = 0; idx < window; idx++) {
            LaserPoint current = snapshot[idx];
            if (current == null || current.isBlank(blank)) {
                prev = null;
                sinceKeep = 0;
                continue;
            }
            // snapshot is oldest → newest; age 0 = the point the scanner just drew.
            float age = 1.0f - (idx + 1) / (float) window;
            if (prev == null) {
                segments.add(new LaserSegment(current.x, current.y, current.x, current.y,
                        current.rgb(), current.intensity01(), age));
                prev = current;
                sinceKeep = 0;
            } else {
                sinceKeep++;
                if (sinceKeep >= step) {
                    int dx = current.x - prev.x;
                    int dy = current.y - prev.y;
                    if ((long) dx * dx + (long) dy * dy < 90_000_000L) {
                        segments.add(new LaserSegment(prev.x, prev.y, current.x, current.y,
                                current.rgb(), current.intensity01(), age));
                    } else {
                        segments.add(new LaserSegment(current.x, current.y, current.x, current.y,
                                current.rgb(), current.intensity01(), age));
                    }
                    prev = current;
                    sinceKeep = 0;
                }
            }
            if (segments.size() >= maxRays) {
                break;
            }
        }
        frame.set(new LaserFrame(segments.toArray(new LaserSegment[0]), rate, count, System.nanoTime()));
    }

    private void writeStatusLocked(byte[] out, int offset) {
        out[offset] = 0;
        out[offset + 1] = (byte) lightEngineState;
        out[offset + 2] = (byte) playbackState;
        out[offset + 3] = LaserProtocol.SOURCE_NETWORK;
        writeU16(out, offset + 4, lightEngineFlags);
        writeU16(out, offset + 6, playbackFlags);
        writeU16(out, offset + 8, sourceFlags);
        writeU16(out, offset + 10, fullness);
        writeU32(out, offset + 12, playbackState == LaserProtocol.PLAYBACK_IDLE ? 0 : pointRate);
        writeU32(out, offset + 16, playbackState == LaserProtocol.PLAYBACK_PLAYING ? (int) Math.min(pointCount, 0xFFFF_FFFFL) : 0);
    }

    private static int clampRate(int rate) {
        int max = TheatricalExtraLightsConfig.getEtherDreamMaxPointRate();
        if (rate < 1) {
            return 1;
        }
        return Math.min(rate, max);
    }

    static void writeU16(byte[] out, int offset, int value) {
        out[offset] = (byte) value;
        out[offset + 1] = (byte) (value >> 8);
    }

    static void writeU32(byte[] out, int offset, long value) {
        out[offset] = (byte) value;
        out[offset + 1] = (byte) (value >> 8);
        out[offset + 2] = (byte) (value >> 16);
        out[offset + 3] = (byte) (value >> 24);
    }

    static int readU16(byte[] in, int offset) {
        return (in[offset] & 0xFF) | ((in[offset + 1] & 0xFF) << 8);
    }

    static int readI16(byte[] in, int offset) {
        return (short) readU16(in, offset);
    }

    static int readU32(byte[] in, int offset) {
        return (in[offset] & 0xFF)
                | ((in[offset + 1] & 0xFF) << 8)
                | ((in[offset + 2] & 0xFF) << 16)
                | ((in[offset + 3] & 0xFF) << 24);
    }
}
