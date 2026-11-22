# Roadmap

Back to [[Home]]

## Done

- [x] Java multi-module Android project
- [x] Zameen theme and shared UI components
- [x] Language onboarding and Urdu right-to-left UI
- [x] Five-tab navigation
- [x] Add, delete, undo and persist fields offline
- [x] Sync outbox with idempotency keys
- [x] Tested concurrency primitives
- [x] Command menu, launcher shortcut and keyboard shortcut
- [x] Project documentation vault
- [x] Java climate, scoring, rotation and alert engine with unit tests

## In the preview APK, awaiting phone test

- [ ] Animated onboarding
- [ ] Map setup, GPS, search and satellite view
- [ ] Live 20-year NASA analysis with simple crop advice
- [ ] What-If, weather and saved offline results
- [ ] Farm tasks, costs, sales, notes and profit
- [ ] Market sales and official price link
- [ ] Help, crop guide, assistant and voice

## Sprints

1. **Useful every day (built in 0.5.0, awaiting phone test):** modern Home, Field Setup map room, irrigation answer, canal turn alarm and danger alerts.
2. **Course requirements:** proposal gaps, tests, topic-to-code report and proposal wording.
3. **Polish and performance:** remaining UX patterns, final fonts, sunlight theme and low-end performance.
4. **Big features:** Spring Boot backend, daily data pipeline, Field Lens and more languages.
5. **Later, if time allows:** listings, machinery rental and community.

## Planned experience

- [ ] Confirm 0.6.0 Live Farm work on a phone: farm switcher, canal scene, living Home, crop timeline, Farm, field records and Market
- [ ] Finish [[Live Farm Plan]]: Advisor What-If scene, all-farm map pins, assistant voice wave, optional sound

## High priority

- [ ] **Confirm map room on a phone.** Home has a 240dp map card and Field Setup has a full-screen map with a draggable form in 0.5.0. The user reported on 2026-11-22 that map screens feel congested. Applies to Field Setup (`fragment_field_setup.xml`) and Home (`fragment_home.xml`). Keep the map as the main element, move secondary controls into a draggable bottom sheet, keep controls clear of system bars and the keyboard, and keep 48dp touch targets, right-to-left layout and smooth sheet motion.

## Next

- [ ] Fix anything found in the phone test
- [ ] Translate Punjabi, Sindhi, Pashto, Saraiki and Balochi
- [ ] Add SoilGrids, SMAP and vegetation data
- [ ] Add in-app mandi prices after source permission
- [ ] Add listings, rental and farmer groups through a backend
- [ ] Bundle Schibsted Grotesk and Arabic-script fonts
- [ ] Complete remaining [[UX Patterns]]
- [ ] Spring Boot backend and real sync
- [ ] Map-based field boundaries
- [ ] NASA daily evidence pipeline
- [ ] [[Field Lens]]
- [ ] Farm records, finance and inventory
- [ ] Market data
- [ ] Community
- [ ] Language waves with native review
- [ ] Sunlight theme, accessibility and low-end device testing
