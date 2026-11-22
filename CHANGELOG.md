# Changelog

All notable changes to AgroShift are recorded here.

## [Unreleased]

### Added

- Java multi-module Android foundation
- Zameen theme, season thread and field hero
- Seven-language onboarding with Urdu right-to-left draft
- Offline field add, remove, undo and persistence with Room
- Sync outbox with idempotency keys and WorkManager
- Tested bounded buffer, ordered locks and stoppable worker
- Command menu, `Ctrl + K` shortcut and launcher shortcut
- Skeleton loading, smooth corners and interruptible press motion
- Unsaved-change protection for the add-field sheet
- Obsidian documentation vault, README and screenshots
- Java climate, rotation scoring and alert engine with a JUnit suite
- Preview APK with map setup, NASA POWER analysis, rotation comparison, alerts and charts (not yet device-tested)
- Version 0.3.0 with animated onboarding, plain-language crop advice, What-If, weather, farm records, Market, Help, assistant and voice (not yet device-tested)

- Version 0.3.1 restores detailed scores, score bars, water need, charts, field stats, risk reasons and actions
- Version 0.3.2 connects the assistant to each field's NASA analysis and saves chat history per field
- Version 0.3.3 redesigns the chatbot with answer cards, read aloud and copy on each answer, suggested questions and a chat language selector (not yet device-tested)

- Version 0.4.0 adds bilingual Urdu and English photo onboarding, a modern Home, licensed farm photography, photo headers on main screens and in-app photo credits (not yet device-tested)
- Version 0.5.0 adds a full-screen Field Setup map, daily irrigation estimate, canal turn alarm, danger alerts and notifications (not yet device-tested)
- Version 0.6.0 adds a multi-farm Home, live weather sky, weather icons and forecast, draggable crop timeline, live canal scene, soil water meter, and redesigned Farm, field record and Market pages (not yet device-tested)
- Version 0.7.0 adds a live What-If field, score ring, tappable rain chart, interactive crop guide, read-aloud voice wave, and global search across farms, records, crops and actions (not yet device-tested)
- Version 0.8.0 adds saved chat sessions with full history, visible plan previews, MPAndroidChart, generic repository and cache types, SyncOutbox, clearer errors, database upgrade tests and the course report (checked on the developer's phone)
- Version 0.9.0 adds the Living Photo Engine prototype: a real canal photo animated by a GPU shader from canal, weather and time data (not yet device-tested)

### Fixed

- Version 0.6.1 fixes the Farm, Market and field details crash, fixes bottom tabs after opening Crop plan from Home, and adds automated screen tests in English and Urdu (not yet confirmed on a phone)
- Version 0.5.1 restores screen margins on Farm, Market and field details, and lets the Field Setup form open fully to its submit button (not yet device-tested)
- Android lifecycle dependency crash at startup
- Dark status bar icons over the field view
- Field validation errors now clear when corrected
