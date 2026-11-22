# Live Farm Plan

Back to [[Home]] and [[Roadmap]]

**Status:** partly built in 0.6.0 and awaiting phone test. Built: farm switcher, canal scene, soil meter, live Home sky, crop timeline, and interactive Farm, field record and Market pages. Planned: Advisor What-If scene, all-farm map pins, assistant voice wave, optional sound, and live prices after AMIS permission. Goal: make AgroShift feel alive while every animation still means something true about the farmer's field.

## Rules

- Motion shows real state. Water flows only during a real canal turn, and rain falls only when the forecast or current data supports it.
- Each screen has one live scene. Everything around it stays calm.
- Scenes pause off-screen and respect reduced-motion and battery saver.
- Scenes use lightweight Java Canvas views and animate transform or opacity where possible.
- Fun never replaces the clear answer, source or safety text.

## Scenes

| Screen | Live experience | Real data behind it |
|---|---|---|
| Canal turn | Top-down canal. The gate opens, water runs into the field, and a countdown fills during the turn | Saved turn day, start and length |
| Water plan | Soil water meter that drops over days and refills when the farmer taps Watered today | Irrigation estimate |
| Home | Living sky with day, night, cloud, rain, heat haze and wind moving the crop | Open-Meteo current and forecast |
| Season bar | Draggable crop timeline. Drag through months to see the crop grow and what happens next | Crop calendar and rotation |
| Advisor | What-If sliders for heat and rain that visibly change the field and animate scores | NASA analysis |
| My Farm | Farm cards with growing crop and water status, with tasks that move the crop journey forward | Records and crop stage |
| Map | Pins for all farms showing water and danger status | Saved fields, weather and alerts |
| Market | Animated price trend and sell-or-hold gauge after AMIS permission | Official prices only |
| Assistant | Voice waveform while listening or reading aloud | Microphone and speech state |

## Build order

1. **Farm switcher:** choose which farm Home shows, because every live scene depends on the correct field.
2. **Canal and water scene:** the signature feature.
3. **Living Home sky and draggable season timeline.**
4. **Advisor What-If and My Farm journey.**
5. **Map status pins, Market, assistant waveform, sound and haptics.**
