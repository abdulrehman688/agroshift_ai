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

## [0.10.3] - 2026-11-22

### Added

- Recycle bin on My Farm for deleted farms, tasks, costs, sales and notes, with filters and restore
- Restoring a record from a deleted farm also restores that farm
- Restoring a farm schedules its canal alarm again

### Fixed

- Live images no longer build up a growing animation clock that could distort them after a few minutes

## [0.10.2] - 2026-11-22

### Changed

- Restored step-by-step crop stages, now with 5 or 6 real photos per crop
- Each new stage slides in cleanly, with no merged photos

### Removed

- Simulated one-photo season from 0.10.1
- Season bar from the reopen screen

## [0.10.1] - 2026-11-22

### Fixed

- Photos stay bright at every hour, with lighter overlays and brighter crop grading
- Normal What-If no longer drains green from the crop
- Berseem now changes through its season on the Help slider
- Photo decoding moved off the render thread for smoother motion

### Changed

- Each crop stays on one real field through its season
- Photos no longer merge. A different photo slides in beside the current one

## [0.10.0] - 2026-11-22

### Added

- Real photos for 11 crops, with motion masks
- Live crop season on the Home timeline and Help crop guide
- Living What-If scene in Advisor
- Device tiers, off-screen pausing and still-photo fallback
- New reopen screen with system splash, season photo and bilingual brand line
- Before and after GIFs in the README

### Changed

- Onboarding and language screens now use Living Photo
- Help cards and My Farm fields now show real crop photos

### Removed

- Drawn crop, What-If and sky views

