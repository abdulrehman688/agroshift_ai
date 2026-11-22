# Living Photo Engine

Back to [[Home]], [[Roadmap]] and [[Image Credits]]

**Status:** canal prototype built in version 0.9.0, awaiting phone test. Other screens still use their current visuals.

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

## Limits

- The photo is a representative canal, not the farmer's own land.
- The camera angle is fixed.
- Visual effects explain data, and the numbers and advice remain the source of truth.
- If approved on a phone, the engine can extend to Home, Advisor, the crop timeline and Crop Plan Studio.
