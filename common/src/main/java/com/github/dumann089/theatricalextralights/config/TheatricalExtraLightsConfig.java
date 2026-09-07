package com.github.dumann089.theatricalextralights.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TheatricalExtraLightsConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File FILE = new File("config/theatricalextralights.json");
    private static TheatricalExtraLightsConfig INSTANCE = new TheatricalExtraLightsConfig();

    /* ================= CAMPOS DE CONFIGURACIÓN ================= */



    private Float laserBeamLength = 400.0f;
    private Float rgbBarBeamLength = 9.0f;
    private Boolean renderLens = true;
    private Float maxGoboDistance = 500.0f;
    private Boolean render2DBeam = true;
    private List<String> laserPassThroughBlocks = null;

    private Boolean volumetricBeamEnabled = true;
    private Float volumetricBeamDistance = 64.0f;
    private Float volumetricBeamBrightness = 0.15f;

    private Integer volumetricBeamSlices = 128;
    private Float volumetricBeamDensity = 0.15f;
    private Float volumetricBeamMaxAlpha = 0.15f;
    private Float volumetricBeamFadeLength = 12.0f;

    /** RAYMARCH (default) or LEGACY_SLICES */
    private String volumetricEngine = "RAYMARCH";
    /** LOW, MEDIUM, HIGH, ULTRA */
    private String raymarchQuality = "HIGH";
    private Float raymarchAnisotropy = 0.55f;
    private Float raymarchDustAmount = 0.55f;
    private Integer raymarchMaxBeamsPerFrame = 128;

    private Integer maxConcurrentRockets = 768;
    private Integer maxSparksPerRocket = 600;
    private Double fireworkRenderDistance = 2048.0;
    /** Si true, la portée pyro suit la render distance client / view distance serveur (plafonnée par fireworkRenderDistance). */
    private Boolean fireworkDynamicRenderDistance = true;
    private Boolean fireworkDynamicLightEnabled = true;
    private Boolean fireworkSmokeEnabled = true;
    private Integer fireworkSmokeBudgetPerTick = 24;
    private Integer fireworkSmokeSpawnInterval = 3;

    private Integer ledFacadeMaxUniverses = 64;

    private Boolean etherDreamEnabled = true;
    private String etherDreamBindAddress = "0.0.0.0";
    private Integer etherDreamTcpPort = 7765;
    private Integer etherDreamBroadcastPort = 7654;
    private Integer etherDreamBufferCapacity = 1800;
    private Integer etherDreamMaxPointRate = 100000;
    private Integer etherDreamHwRevision = 2;
    private Integer etherDreamSwRevision = 2;
    private String etherDreamMac = "02:00:00:ED:01:00";
    private Integer laserDacMaxRays = 96;
    private Integer laserDacBlankThreshold = 256;
    /** Persistence window (ms) used to rebuild the scan picture: must cover a full ILDA frame (>= 1/fps). */
    private Integer laserDacPersistenceMs = 90;
    /** Volumetric haze budget per projector: raymarched needle rays and scan sheets. */
    private Integer laserDacVolumetricRays = 24;
    private Integer laserDacVolumetricSheets = 24;
    /** Haze radius of a needle ray (blocks) and thickness of a scan sheet (blocks). */
    private Float laserDacHazeRadius = 0.11f;
    private Float laserDacSheetThickness = 0.07f;

    private transient Set<String> laserPassThroughSet;

    static {
        load();
    }

    public static void reload() {
        load();
    }

    public static void load() {
        File parent = FILE.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        if (FILE.exists()) {
            try (FileReader reader = new FileReader(FILE)) {
                TheatricalExtraLightsConfig loaded = GSON.fromJson(reader, TheatricalExtraLightsConfig.class);
                if (loaded != null) {
                    INSTANCE = loaded;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        save();
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(FILE)) {
            GSON.toJson(INSTANCE, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /* ================= GETTERS ================= */

    public static boolean isVolumetricBeamEnabled() { return INSTANCE.volumetricBeamEnabled; }
    public static float getVolumetricBeamDistance() { return INSTANCE.volumetricBeamDistance; }
    public static float getVolumetricBeamBrightness() { return INSTANCE.volumetricBeamBrightness; }
    public static int getVolumetricBeamSlices() { return INSTANCE.volumetricBeamSlices; }
    public static float getVolumetricBeamDensity() { return INSTANCE.volumetricBeamDensity; }
    public static float getVolumetricBeamMaxAlpha() { return INSTANCE.volumetricBeamMaxAlpha; }
    public static float getVolumetricBeamFadeLength() { return INSTANCE.volumetricBeamFadeLength != null ? INSTANCE.volumetricBeamFadeLength : 2.0f; }

    public static boolean isRaymarchEngine() {
        String engine = INSTANCE.volumetricEngine;
        return engine == null || !"LEGACY_SLICES".equalsIgnoreCase(engine.trim());
    }

    public static String getRaymarchQuality() {
        return INSTANCE.raymarchQuality != null ? INSTANCE.raymarchQuality : "HIGH";
    }

    public static int getRaymarchSteps() {
        String q = getRaymarchQuality().trim().toUpperCase();
        return switch (q) {
            case "LOW" -> 8;
            case "MEDIUM" -> 16;
            case "ULTRA" -> 32;
            default -> 24; // HIGH
        };
    }

    public static float getRaymarchAnisotropy() {
        return INSTANCE.raymarchAnisotropy != null ? INSTANCE.raymarchAnisotropy : 0.55f;
    }

    public static float getRaymarchDustAmount() {
        return INSTANCE.raymarchDustAmount != null ? INSTANCE.raymarchDustAmount : 0.55f;
    }

    public static int getRaymarchMaxBeamsPerFrame() {
        int value = INSTANCE.raymarchMaxBeamsPerFrame != null ? INSTANCE.raymarchMaxBeamsPerFrame : 128;
        return Math.max(1, Math.min(128, value));
    }
    public static float getLaserBeamLength() { return INSTANCE.laserBeamLength; }
    public static float getRgbBarBeamLength() { return INSTANCE.rgbBarBeamLength; }
    public static boolean shouldRenderLens() { return INSTANCE.renderLens; }
    public static float getMaxGoboDistance() { return INSTANCE.maxGoboDistance; }
    public static boolean shouldRender2DBeam() { return INSTANCE.render2DBeam; }

    public static int getMaxConcurrentRockets() { return INSTANCE.maxConcurrentRockets != null ? INSTANCE.maxConcurrentRockets : 768; }
    public static int getMaxSparksPerRocket() { return INSTANCE.maxSparksPerRocket != null ? INSTANCE.maxSparksPerRocket : 600; }
    public static double getFireworkRenderDistance() { return INSTANCE.fireworkRenderDistance != null ? INSTANCE.fireworkRenderDistance : 2048.0; }
    public static boolean useFireworkDynamicRenderDistance() { return INSTANCE.fireworkDynamicRenderDistance == null || INSTANCE.fireworkDynamicRenderDistance; }
    public static boolean isFireworkDynamicLightEnabled() { return INSTANCE.fireworkDynamicLightEnabled == null || INSTANCE.fireworkDynamicLightEnabled; }
    public static boolean isFireworkSmokeEnabled() { return INSTANCE.fireworkSmokeEnabled == null || INSTANCE.fireworkSmokeEnabled; }
    public static int getFireworkSmokeBudgetPerTick() { return INSTANCE.fireworkSmokeBudgetPerTick != null ? INSTANCE.fireworkSmokeBudgetPerTick : 24; }
    public static int getFireworkSmokeSpawnInterval() { return Math.max(1, INSTANCE.fireworkSmokeSpawnInterval != null ? INSTANCE.fireworkSmokeSpawnInterval : 3); }
    public static int getLedFacadeMaxUniverses() { return INSTANCE.ledFacadeMaxUniverses != null ? INSTANCE.ledFacadeMaxUniverses : 64; }

    public static boolean isEtherDreamEnabled() { return INSTANCE.etherDreamEnabled == null || INSTANCE.etherDreamEnabled; }
    public static String getEtherDreamBindAddress() {
        return INSTANCE.etherDreamBindAddress == null || INSTANCE.etherDreamBindAddress.isBlank()
                ? "0.0.0.0" : INSTANCE.etherDreamBindAddress.trim();
    }
    public static int getEtherDreamTcpPort() {
        int port = INSTANCE.etherDreamTcpPort != null ? INSTANCE.etherDreamTcpPort : 7765;
        return Math.max(1, Math.min(65535, port));
    }
    public static int getEtherDreamBroadcastPort() {
        int port = INSTANCE.etherDreamBroadcastPort != null ? INSTANCE.etherDreamBroadcastPort : 7654;
        return Math.max(1, Math.min(65535, port));
    }
    public static int getEtherDreamBufferCapacity() {
        int value = INSTANCE.etherDreamBufferCapacity != null ? INSTANCE.etherDreamBufferCapacity : 1800;
        return Math.max(256, Math.min(32768, value));
    }
    public static int getEtherDreamMaxPointRate() {
        int value = INSTANCE.etherDreamMaxPointRate != null ? INSTANCE.etherDreamMaxPointRate : 100000;
        return Math.max(1000, Math.min(200000, value));
    }
    public static int getEtherDreamHwRevision() {
        return INSTANCE.etherDreamHwRevision != null ? INSTANCE.etherDreamHwRevision : 2;
    }
    public static int getEtherDreamSwRevision() {
        return INSTANCE.etherDreamSwRevision != null ? INSTANCE.etherDreamSwRevision : 2;
    }
    public static byte[] getEtherDreamMac() {
        byte[] parsed = parseMac(INSTANCE.etherDreamMac);
        return parsed != null ? parsed : com.github.dumann089.theatricalextralights.laser.dac.LaserProtocol.DEFAULT_MAC.clone();
    }
    public static int getLaserDacMaxRays() {
        int value = INSTANCE.laserDacMaxRays != null ? INSTANCE.laserDacMaxRays : 96;
        return Math.max(8, Math.min(256, value));
    }
    public static int getLaserDacPersistenceMs() {
        int value = INSTANCE.laserDacPersistenceMs != null ? INSTANCE.laserDacPersistenceMs : 90;
        return Math.max(20, Math.min(400, value));
    }
    public static int getLaserDacVolumetricRays() {
        int value = INSTANCE.laserDacVolumetricRays != null ? INSTANCE.laserDacVolumetricRays : 24;
        return Math.max(0, Math.min(32, value));
    }
    public static int getLaserDacVolumetricSheets() {
        int value = INSTANCE.laserDacVolumetricSheets != null ? INSTANCE.laserDacVolumetricSheets : 24;
        return Math.max(0, Math.min(32, value));
    }
    public static float getLaserDacHazeRadius() {
        float value = INSTANCE.laserDacHazeRadius != null ? INSTANCE.laserDacHazeRadius : 0.11f;
        return Math.max(0.03f, Math.min(0.5f, value));
    }
    public static float getLaserDacSheetThickness() {
        float value = INSTANCE.laserDacSheetThickness != null ? INSTANCE.laserDacSheetThickness : 0.07f;
        return Math.max(0.02f, Math.min(0.4f, value));
    }
    public static int getLaserDacBlankThreshold() {
        int value = INSTANCE.laserDacBlankThreshold != null ? INSTANCE.laserDacBlankThreshold : 256;
        return Math.max(1, Math.min(4096, value));
    }

    private static byte[] parseMac(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String[] parts = raw.trim().split("[:\\-]");
        if (parts.length != 6) {
            return null;
        }
        byte[] mac = new byte[6];
        try {
            for (int i = 0; i < 6; i++) {
                mac[i] = (byte) Integer.parseInt(parts[i], 16);
            }
            return mac;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /* ================= SETTERS ================= */

    public static void setVolumetricBeamEnabled(boolean value) { INSTANCE.volumetricBeamEnabled = value; save(); }
    public static void setVolumetricBeamDistance(float value) { INSTANCE.volumetricBeamDistance = value; save(); }
    public static void setVolumetricBeamBrightness(float value) { INSTANCE.volumetricBeamBrightness = value; save(); }
    public static void setVolumetricBeamFadeLength(float value) { INSTANCE.volumetricBeamFadeLength = value; save(); }
    public static void setLaserBeamLength(float value) { INSTANCE.laserBeamLength = Math.max(20f, value); save(); }
    public static void setRgbBarBeamLength(float value) { INSTANCE.rgbBarBeamLength = Math.max(1f, value); save(); }
    public static void setRenderLens(boolean value) { INSTANCE.renderLens = value; save(); }
    public static void setMaxGoboDistance(float value) { INSTANCE.maxGoboDistance = value; save(); }

    public static boolean isLaserPassThrough(String blockId) {
        if (INSTANCE.laserPassThroughSet == null) {
            INSTANCE.laserPassThroughSet = INSTANCE.laserPassThroughBlocks == null
                    ? new HashSet<>()
                    : new HashSet<>(INSTANCE.laserPassThroughBlocks);
        }
        return INSTANCE.laserPassThroughSet.contains(blockId);
    }
}