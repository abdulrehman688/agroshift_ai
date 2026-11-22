# Living Photo Engine

Back to [[Home]], [[Roadmap]] and [[Image Credits]]

**Status:** canal prototype confirmed on the user's phone in 0.9.0. The full rollout is built in version 0.10.2 and checked on the user's phone.

The engine turns one real farm photo into a live scene on the phone's GPU. It is not video and not 3D.

## How it is made

| Step | What happens | Result |
|---|---|---|
| 1. Choose | Pick an openly licensed photo of a real Pakistani canal | `1-original.jpg` |
| 2. Measure | Place a grid over the photo and trace the canal edge | `2-measure.jpg` |
| 3. Mask | Build a hidden map: red is water, green is plants, blue is sky | `3-mask.jpg` |
| 4. Shade | An OpenGL ES fragment shader moves only the marked parts | `living_photo_fragment.glsl` |
| 5. Connect | `LivingScene` converts canal, weather and time data into effect values | `LivingScene.java` |
| 6. Protect | `LivingPhotoView` limits resolution and speed, pauses when hidden and falls back to the still photo | `LivingPhotoView.java` |

## What real data controls

| Data | Visual effect |
|---|---|
| Canal turn running | Faster flow, with the banks getting wetter as the turn progresses |
| Turn starting soon | Water begins moving more |
| Rain | Rain streaks, surface rings, darker scene |
| Wind | Trees sway |
| Cloud cover | Moving cloud shadows and sky clouds |
| Temperature | Heat shimmer and warmer light |
| Farm time | Day, golden hour and night |

## Results

The scene images in `docs/assets/living-photo/` were rendered from the app's actual shader in WebGL on a computer. Phone rendering still needs confirmation.

## Version 0.10.2 rollout

Each crop has 5 or 6 real photos ordered from early growth to harvest. Timeline and slider steps move between them with a clean slide. Photos are never double-exposed. Scenes stay bright at every hour by user decision. The user rejected a simulated season on one photo in 0.10.1.

| Screen | Living Photo use |
|---|---|
| Home | Real crop hero, driven by the Rabi and Kharif timeline, weather and local time |
| Advisor | The chosen plan's crop reacts to hotter and drier What-If choices |
| Help | Still crop cards, and a live guide whose slider steps through the crop's real photos |
| My Farm | Fast still crop thumbnails and the live canal scene |
| Onboarding | Photos slide with the page swipe |
| Reopen screen | Season photo, brand mark, Urdu and English line, about 1.5 seconds, tap to skip |

### Device tiers

| Tier | Used for | Render width | Frame time |
|---|---|---|---|
| Still | Reduced motion or battery saver, or no GPU path | Photo only | None |
| Lite | Low-memory phones | 480 px | 50 ms |
| Balanced | Mid-range phones | 720 px | 33 ms |
| High | Stronger phones | 960 px | 33 ms |

Scenes pause when hidden or off screen. Frame rates have not yet been measured on real low-end phones.

## Limits

- Crop photos are representative real photos, and many were taken outside Pakistan.
- Stage photos come from different real fields. They show the crop's stage, not the farmer's actual field.
- Crop stress is a visual explanation of the What-If result, not a measurement of the field.

- The photo is a representative canal, not the farmer's own land.
- The camera angle is fixed.
- Visual effects explain data, and the numbers and advice remain the source of truth.
- If approved on a phone, the engine can extend to Home, Advisor, the crop timeline and Crop Plan Studio.
