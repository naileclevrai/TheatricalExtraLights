# Atomic Strobe

`theatricalextralights:atomic_strobe` · family: [Blinders & strobes](/fixtures/blinders-strobes)

LED strobe in the Atomic style: a nine-segment white strobe bar between two plates of four RGB zones, on a U yoke with a floor plate.

- Every flash is timed from the game clock, so all clients see the same flashes. A flash shorter than a frame still shows for one frame.
- The room light follows the flashes tick by tick.

## Personalities

| Mode | Channels |
|---|---|
| 34-Channel Pixel (Legacy) | 34 |
| 1-Channel Strobe | 1 |
| 4-Channel Atomic | 4 |
| 8-Channel Atomic + Aura | 8 |
| 10-Channel Compressed (Bar + Plate) | 10 |
| 40-Channel Pixel (Bar + Plate) | 40 |

Select the mode in the fixture config screen; the footprint changes immediately.

## 34-Channel Pixel (Legacy) (34 ch)

The original layout, everything lit continuously at its level. Kept first so fixtures placed before the modes existed keep working.

| Ch | Function | Values |
|---|---|---|
| 1 | Zone 1 red | 0 to 255 |
| 2 | Zone 1 green | 0 to 255 |
| 3 | Zone 1 blue | 0 to 255 |
| 4 | Zone 2 red | 0 to 255 |
| 5 | Zone 2 green | 0 to 255 |
| 6 | Zone 2 blue | 0 to 255 |
| 7 | Zone 3 red | 0 to 255 |
| 8 | Zone 3 green | 0 to 255 |
| 9 | Zone 3 blue | 0 to 255 |
| 10 | Zone 4 red | 0 to 255 |
| 11 | Zone 4 green | 0 to 255 |
| 12 | Zone 4 blue | 0 to 255 |
| 13 | Zone 5 red | 0 to 255 |
| 14 | Zone 5 green | 0 to 255 |
| 15 | Zone 5 blue | 0 to 255 |
| 16 | Zone 6 red | 0 to 255 |
| 17 | Zone 6 green | 0 to 255 |
| 18 | Zone 6 blue | 0 to 255 |
| 19 | Zone 7 red | 0 to 255 |
| 20 | Zone 7 green | 0 to 255 |
| 21 | Zone 7 blue | 0 to 255 |
| 22 | Zone 8 red | 0 to 255 |
| 23 | Zone 8 green | 0 to 255 |
| 24 | Zone 8 blue | 0 to 255 |
| 25 | White bar segment 1 | 0 to 255 |
| 26 | White bar segment 2 | 0 to 255 |
| 27 | White bar segment 3 | 0 to 255 |
| 28 | White bar segment 4 | 0 to 255 |
| 29 | White bar segment 5 | 0 to 255 |
| 30 | White bar segment 6 | 0 to 255 |
| 31 | White bar segment 7 | 0 to 255 |
| 32 | White bar segment 8 | 0 to 255 |
| 33 | White bar segment 9 | 0 to 255 |
| 34 | Focus | 1 to 255 |

### Channel by channel

**1 · Zone 1 red** — Red of RGB zone 1.

**2 · Zone 1 green** — Green of RGB zone 1.

**3 · Zone 1 blue** — Blue of RGB zone 1.

**4 · Zone 2 red** — Red of RGB zone 2.

**5 · Zone 2 green** — Green of RGB zone 2.

**6 · Zone 2 blue** — Blue of RGB zone 2.

**7 · Zone 3 red** — Red of RGB zone 3.

**8 · Zone 3 green** — Green of RGB zone 3.

**9 · Zone 3 blue** — Blue of RGB zone 3.

**10 · Zone 4 red** — Red of RGB zone 4.

**11 · Zone 4 green** — Green of RGB zone 4.

**12 · Zone 4 blue** — Blue of RGB zone 4.

**13 · Zone 5 red** — Red of RGB zone 5.

**14 · Zone 5 green** — Green of RGB zone 5.

**15 · Zone 5 blue** — Blue of RGB zone 5.

**16 · Zone 6 red** — Red of RGB zone 6.

**17 · Zone 6 green** — Green of RGB zone 6.

**18 · Zone 6 blue** — Blue of RGB zone 6.

**19 · Zone 7 red** — Red of RGB zone 7.

**20 · Zone 7 green** — Green of RGB zone 7.

**21 · Zone 7 blue** — Blue of RGB zone 7.

**22 · Zone 8 red** — Red of RGB zone 8.

**23 · Zone 8 green** — Green of RGB zone 8.

**24 · Zone 8 blue** — Blue of RGB zone 8.

**25 · White bar segment 1** — Level of white bar segment 1.

**26 · White bar segment 2** — Level of white bar segment 2.

**27 · White bar segment 3** — Level of white bar segment 3.

**28 · White bar segment 4** — Level of white bar segment 4.

**29 · White bar segment 5** — Level of white bar segment 5.

**30 · White bar segment 6** — Level of white bar segment 6.

**31 · White bar segment 7** — Level of white bar segment 7.

**32 · White bar segment 8** — Level of white bar segment 8.

**33 · White bar segment 9** — Level of white bar segment 9.

**34 · Focus** — Size of the light spot on the ground.

## 1-Channel Strobe (1 ch)

White bar only.

| Ch | Function | Values |
|---|---|---|
| 1 | Strobe | 0 to 255 |

### Channel by channel

**1 · Strobe** — 0 off; 1 to 254 flash rate, slow to fast, with a short flash; 255 lamp on continuously.

## 4-Channel Atomic (4 ch)

White bar only, driven like a Martin Atomic 3000.

| Ch | Function | Values |
|---|---|---|
| 1 | Intensity | 0 to 255 |
| 2 | Flash duration | 0 to 255 |
| 3 | Flash rate | 0 to 255 |
| 4 | Effects | 0 to 255 |

### Channel by channel

**1 · Intensity** — Flash intensity.

**2 · Flash duration** — Length of each flash, 12 ms at 0 to 650 ms at 255.

**3 · Flash rate** — 0 stops the strobe (as on the real unit); 1 to 255 runs 0.5 to 25 flashes a second.

**4 · Effects** — 0-9 plain strobe; 10-39 blinder, lamp on continuously; 40-69 ramp up; 70-99 ramp down; 100-129 ramp up and down; 130-159 random; 160-189 lightning bursts; 190-219 spikes; 220-255 sparkle (each bar segment flashes on its own in the pixel modes).

## 8-Channel Atomic + Aura (8 ch)

The Atomic 3000 LED layout: the strobe bar plus the RGB plates as one coloured glow around it.

| Ch | Function | Values |
|---|---|---|
| 1 | Intensity | 0 to 255 |
| 2 | Flash duration | 0 to 255 |
| 3 | Flash rate | 0 to 255 |
| 4 | Effects | 0 to 255 |
| 5 | Aura intensity | 0 to 255 |
| 6 | Aura red | 0 to 255 |
| 7 | Aura green | 0 to 255 |
| 8 | Aura blue | 0 to 255 |

### Channel by channel

**1 · Intensity** — Flash intensity.

**2 · Flash duration** — Length of each flash, 12 ms at 0 to 650 ms at 255.

**3 · Flash rate** — 0 stops the strobe (as on the real unit); 1 to 255 runs 0.5 to 25 flashes a second.

**4 · Effects** — 0-9 plain strobe; 10-39 blinder, lamp on continuously; 40-69 ramp up; 70-99 ramp down; 100-129 ramp up and down; 130-159 random; 160-189 lightning bursts; 190-219 spikes; 220-255 sparkle (each bar segment flashes on its own in the pixel modes).

**5 · Aura intensity** — Level of the RGB plates, lit continuously as a backlight.

**6 · Aura red** — Red of the aura.

**7 · Aura green** — Green of the aura.

**8 · Aura blue** — Blue of the aura.

## 10-Channel Compressed (Bar + Plate) (10 ch)

All eight RGB zones act as one pixel with its own strobe, next to the bar.

| Ch | Function | Values |
|---|---|---|
| 1 | Intensity | 0 to 255 |
| 2 | Flash duration | 0 to 255 |
| 3 | Flash rate | 0 to 255 |
| 4 | Effects | 0 to 255 |
| 5 | Plate intensity | 0 to 255 |
| 6 | Plate flash duration | 0 to 255 |
| 7 | Plate flash rate | 0 to 255 |
| 8 | Plate red | 0 to 255 |
| 9 | Plate green | 0 to 255 |
| 10 | Plate blue | 0 to 255 |

### Channel by channel

**1 · Intensity** — Flash intensity.

**2 · Flash duration** — Length of each flash, 12 ms at 0 to 650 ms at 255.

**3 · Flash rate** — 0 stops the strobe (as on the real unit); 1 to 255 runs 0.5 to 25 flashes a second.

**4 · Effects** — 0-9 plain strobe; 10-39 blinder, lamp on continuously; 40-69 ramp up; 70-99 ramp down; 100-129 ramp up and down; 130-159 random; 160-189 lightning bursts; 190-219 spikes; 220-255 sparkle (each bar segment flashes on its own in the pixel modes).

**5 · Plate intensity** — Level of the RGB plates.

**6 · Plate flash duration** — Flash length of the plates, 12 ms to 650 ms.

**7 · Plate flash rate** — 0 leaves the plates on continuously; 1 to 255 strobes them at 0.5 to 25 Hz.

**8 · Plate red** — Red of all eight zones.

**9 · Plate green** — Green of all eight zones.

**10 · Plate blue** — Blue of all eight zones.

## 40-Channel Pixel (Bar + Plate) (40 ch)

Every segment and every zone on its own channel; the sparkle effect flashes segments individually.

| Ch | Function | Values |
|---|---|---|
| 1 | Intensity | 0 to 255 |
| 2 | Flash duration | 0 to 255 |
| 3 | Flash rate | 0 to 255 |
| 4 | Effects | 0 to 255 |
| 5 | Bar segment 1 | 0 to 255 |
| 6 | Bar segment 2 | 0 to 255 |
| 7 | Bar segment 3 | 0 to 255 |
| 8 | Bar segment 4 | 0 to 255 |
| 9 | Bar segment 5 | 0 to 255 |
| 10 | Bar segment 6 | 0 to 255 |
| 11 | Bar segment 7 | 0 to 255 |
| 12 | Bar segment 8 | 0 to 255 |
| 13 | Bar segment 9 | 0 to 255 |
| 14 | Plate intensity | 0 to 255 |
| 15 | Plate flash duration | 0 to 255 |
| 16 | Plate flash rate | 0 to 255 |
| 17 | Zone 1 red | 0 to 255 |
| 18 | Zone 1 green | 0 to 255 |
| 19 | Zone 1 blue | 0 to 255 |
| 20 | Zone 2 red | 0 to 255 |
| 21 | Zone 2 green | 0 to 255 |
| 22 | Zone 2 blue | 0 to 255 |
| 23 | Zone 3 red | 0 to 255 |
| 24 | Zone 3 green | 0 to 255 |
| 25 | Zone 3 blue | 0 to 255 |
| 26 | Zone 4 red | 0 to 255 |
| 27 | Zone 4 green | 0 to 255 |
| 28 | Zone 4 blue | 0 to 255 |
| 29 | Zone 5 red | 0 to 255 |
| 30 | Zone 5 green | 0 to 255 |
| 31 | Zone 5 blue | 0 to 255 |
| 32 | Zone 6 red | 0 to 255 |
| 33 | Zone 6 green | 0 to 255 |
| 34 | Zone 6 blue | 0 to 255 |
| 35 | Zone 7 red | 0 to 255 |
| 36 | Zone 7 green | 0 to 255 |
| 37 | Zone 7 blue | 0 to 255 |
| 38 | Zone 8 red | 0 to 255 |
| 39 | Zone 8 green | 0 to 255 |
| 40 | Zone 8 blue | 0 to 255 |

### Channel by channel

**1 · Intensity** — Flash intensity.

**2 · Flash duration** — Length of each flash, 12 ms at 0 to 650 ms at 255.

**3 · Flash rate** — 0 stops the strobe (as on the real unit); 1 to 255 runs 0.5 to 25 flashes a second.

**4 · Effects** — 0-9 plain strobe; 10-39 blinder, lamp on continuously; 40-69 ramp up; 70-99 ramp down; 100-129 ramp up and down; 130-159 random; 160-189 lightning bursts; 190-219 spikes; 220-255 sparkle (each bar segment flashes on its own in the pixel modes).

**5 · Bar segment 1** — Level of white bar segment 1 under the bar's flashes.

**6 · Bar segment 2** — Level of white bar segment 2 under the bar's flashes.

**7 · Bar segment 3** — Level of white bar segment 3 under the bar's flashes.

**8 · Bar segment 4** — Level of white bar segment 4 under the bar's flashes.

**9 · Bar segment 5** — Level of white bar segment 5 under the bar's flashes.

**10 · Bar segment 6** — Level of white bar segment 6 under the bar's flashes.

**11 · Bar segment 7** — Level of white bar segment 7 under the bar's flashes.

**12 · Bar segment 8** — Level of white bar segment 8 under the bar's flashes.

**13 · Bar segment 9** — Level of white bar segment 9 under the bar's flashes.

**14 · Plate intensity** — Level of the RGB plates.

**15 · Plate flash duration** — Flash length of the plates.

**16 · Plate flash rate** — 0 continuous; 1 to 255 strobes the plates.

**17 · Zone 1 red** — Red of RGB zone 1.

**18 · Zone 1 green** — Green of RGB zone 1.

**19 · Zone 1 blue** — Blue of RGB zone 1.

**20 · Zone 2 red** — Red of RGB zone 2.

**21 · Zone 2 green** — Green of RGB zone 2.

**22 · Zone 2 blue** — Blue of RGB zone 2.

**23 · Zone 3 red** — Red of RGB zone 3.

**24 · Zone 3 green** — Green of RGB zone 3.

**25 · Zone 3 blue** — Blue of RGB zone 3.

**26 · Zone 4 red** — Red of RGB zone 4.

**27 · Zone 4 green** — Green of RGB zone 4.

**28 · Zone 4 blue** — Blue of RGB zone 4.

**29 · Zone 5 red** — Red of RGB zone 5.

**30 · Zone 5 green** — Green of RGB zone 5.

**31 · Zone 5 blue** — Blue of RGB zone 5.

**32 · Zone 6 red** — Red of RGB zone 6.

**33 · Zone 6 green** — Green of RGB zone 6.

**34 · Zone 6 blue** — Blue of RGB zone 6.

**35 · Zone 7 red** — Red of RGB zone 7.

**36 · Zone 7 green** — Green of RGB zone 7.

**37 · Zone 7 blue** — Blue of RGB zone 7.

**38 · Zone 8 red** — Red of RGB zone 8.

**39 · Zone 8 green** — Green of RGB zone 8.

**40 · Zone 8 blue** — Blue of RGB zone 8.
