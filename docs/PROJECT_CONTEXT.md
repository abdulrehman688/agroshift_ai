# AgroShift Project Context

Last updated: 2026-11-22. Status: GitHub release APK is the phone-checked 0.8.0. Living Photo prototype 0.9.0 is built and awaiting phone test.

### Current build status

- **Working:** Java multi-module Android app, Zameen theme, language onboarding, Urdu right-to-left UI, five-tab navigation, season thread, add/delete/undo fields, Room persistence, sync outbox with idempotency keys, tested concurrency primitives, command menu, skeleton loading, smooth corners, press springs, and unsaved-sheet protection.
- **Preview APK:** `releases/AgroShift-0.8.0-preview.apk` includes a multi-farm Home, live sky, crop timeline, live canal scene, interactive Farm and Market pages, bilingual photo onboarding, a modern Home, irrigation estimate, canal turn alarm, danger alerts, map setup, NASA crop advice, What-If, weather, farm records, Market, Help, field-aware assistant, per-answer read aloud, copy, and a chat language selector. It passed build, lint and 100 automated tests, and the user reported that it worked on their phone on 2026-11-22.
- **Analysis display rule:** show a clear answer first, followed by the full useful analysis: scores, six score bars, water need, rain chart, field stats, risks, reasons and actions. The user rejected the over-simplified version on 2026-11-22.
- **Data flow:** see `docs/How Data Works.md`. The assistant receives the selected field's NASA analysis, without exact coordinates or field name. Its general farming advice is not yet restricted to a reviewed official knowledge base.
- **Current limits:** Market opens official AMIS prices instead of showing them in-app. Community groups, listings and rental need a backend. Field Lens needs a secure image AI service.
- **Sprint 1:** built in 0.5.0 and awaiting phone test, including Home and Field Setup map room. Irrigation is an estimate without crop coefficients. Danger alerts use AgroShift thresholds and are not official warnings.
- **Photography:** app photos are openly licensed Wikimedia Commons images, cropped, graded and credited in `docs/Image Credits.md` and in Help. Do not add images without a compatible license.
- **Design guidance:** Expo and React Native skills are used only as performance and UX guidance. The app stays Java because the course requires it. SnapAI is installed but unused until the user provides an image API key.
- **Multi-farm Home:** Home lists all farms, and the selected farm is saved and shared with Advisor and the assistant.
- **Live experience:** partly built, see `docs/Live Farm Plan.md`. Motion must represent real field state. Market charts use only the farmer's own sales until official price permission exists.
- **Version 0.6.1:** includes the Farm, Market, field details and bottom-tab fixes. App screen tests run locally with `:app:testDebugUnitTest`, without an emulator.
- **Version 0.7.0:** adds interactive Advisor and Help pages and global search. It passed build, lint and 83 automated tests, but has not been confirmed on a phone.
- **Version 0.8.0:** adds saved chat sessions, Sprint 2 course types, MPAndroidChart and the course report. It passed build, lint and 100 automated tests, and the user reported that it worked on their phone.
- **Scope:** the teacher encourages more features, so the app should stay feature-rich, with UI as the top priority.
- **Visual direction:** the user rejected cartoon-style farm drawings and chose the Living Photo Engine over full 3D. See `docs/Living Photo Engine.md`. Crop Plan Studio is approved and follows the canal prototype.
- **README process:** document major visual processes in the GitHub README with real process images.
- **Testing preference:** do not run emulator flows. Build the APK and run normal code checks. The user tests on their phone.
- **Website parity:** feature-by-feature status is in `docs/Website Parity.md`. All website features are planned for mobile except the Iowa location and sample result.
- **Not built yet:** Spring Boot backend, real sync gateway, Field Lens, server NASA pipeline, image placeholders, shared-element transitions, custom predictive-back transitions, safe-triangle menus, light sunlight theme, bundled Schibsted Grotesk and Nastaliq fonts. Exact pattern status is in `docs/UX Patterns.md`.
- **Documentation:** `docs/` is the Obsidian vault. Keep it, `README.md` and `CHANGELOG.md` updated at every milestone.
- **NASA access:** no account action has been taken. Public APIs need no login. Any Earthdata token must be created by the user and stored only in the backend's protected environment.
- **Chat languages:** English, Urdu, Punjabi and Sindhi work through the backend. Saraiki is beta. Pashto, Balochi, Hindko and Brahui stay marked coming soon until the backend supports them.
- **Languages:** Urdu is a machine draft needing native review. Punjabi, Sindhi, Pashto, Saraiki and Balochi are selectable with right-to-left layout but show English until translated.
- **Build setup:** use JDK 21 and set `GRADLE_USER_HOME=D:\AndroidBuild\gradle-home`, because C: is nearly full.

This file is the source of truth for product scope, architecture, UX rules and decisions. Update it when a decision changes.

## 1. Product

AgroShift is a complete farm companion for Pakistani farmers. It helps a farmer decide what to do on their own fields, track the farm as a business, sell produce, and get help in their own language.

It is a **new multi-screen Java Android system**, not a port of the earlier website.

### Repositories

| Purpose | Repository | Use |
|---|---|---|
| New system | https://github.com/abdulrehman688/agroshift_ai | All new work goes here |
| Earlier web project | https://github.com/asifnawaz23/nasa_agroshift_AI | Source of the idea and of anything worth reusing. It does not limit scope |

### Course requirements

From the Software Construction and Development proposal, "AgroShift: Crop Rotation Planner Using NASA Climate Data (Android)":

- Java Android app, using map-based field selection and three rotation comparisons (current, water-saving, soil-building).
- 20 years of NASA POWER climate data, with heat, drought and frost alerts.
- Multithreading with start, sleep and stop control.
- Inter-thread communication using synchronization.
- Multiple-thread resource handling with locks and deadlock prevention.
- Generic classes and methods, immutability and mutability, abstract data types.
- Exception handling for fault-tolerant data access.
- JUnit, test-driven development and test suites.
- Local storage with Room.
- Named libraries: Retrofit, Gson, Room, osmdroid, MPAndroidChart, WorkManager, AndroidX Navigation, RecyclerView, JUnit.
- The proposal states the app gives no fertilizer or pesticide doses and always advises checking with a local expert. Decision D1 changes this to official published guidance only, so the proposal wording needs updating.

## 2. Decisions

| ID | Decision | Status |
|---|---|---|
| D1 | Fertilizer and pesticide doses | Decided: official sources only, under the policy in section 5. AI and app logic never generate or calculate a dose |
| D2 | Build the full feature set in section 3 | Decided |
| D3 | Java for Android and backend | Decided for Android, Spring Boot proposed for backend |
| D4 | Data is collected from online sources and updated automatically every day | Decided |
| D5 | All Pakistani languages across the whole app and chat | Decided, delivered through the language registry in section 6 |
| D6 | All 19 UX patterns in section 8 are required | Decided |
| D7 | The unslop skill guides UI quality | Decided, adapted for Android in section 7 |
| D8 | Offline-first, with Room as the app's source of truth | Proposed |
| D9 | AI keys stay on the server and never ship in the APK | Required |
| D10 | Field Lens camera assistant uses photo, NASA field evidence and official sources | Decided, see section 13 |
| D11 | No unsupported claims. If evidence is missing, the assistant says so | Required |
| D12 | Realistic scenes use real licensed photos animated by GPU shaders from farm data | Decided, prototype pending phone test |

## 3. Features

Bottom navigation: **Home, My Farm, Advisor, Market, Community**. Global search and the command menu are reachable from every main screen.

### Home
- Daily farm briefing: what to do, what to avoid, and why
- Current weather and 7-day forecast
- Rain, heat, frost, flood, drought and wind alerts
- Irrigation, spraying and harvest suitability windows
- Today's tasks across all farms
- Price movement for the farmer's crops
- Voice action: Ask AgroShift

### My Farm
- Multiple farms and fields, with boundaries drawn on the map
- Local land units such as acre, kanal and marla
- Rabi and Kharif crop calendar with stage-by-stage tasks
- Field diary with photos, voice notes and history
- Irrigation planner with canal turn (warabandi) reminders
- Expense, income, profit and loan tracker by season and field
- Inventory for seed, fertilizer, fuel and equipment
- Labor and machinery records
- Soil test records and field health history
- Reports that can be exported and shared
- Family or helper access

### Advisor
- Rotation planner: current, water-saving, soil-building and income-focused
- What-If simulator for heat, rainfall and water shortage
- Crop suitability and sowing-date recommendations
- Climate risk score per field
- Satellite field health view
- Photo-based crop problem check with possible causes, confidence and expert referral. It never selects a product or dose
- Official fertilizer and pesticide guidance library, with source and date on every entry
- **Field Lens:** take a photo, ask by voice or text, and get an evidence-backed answer
- Multilingual AI chat by text and voice, grounded in the farmer's fields

### Market
- Daily mandi prices, trends and nearest-market comparison
- Best-time-to-sell insights, labelled as estimates
- Buy and sell listings for crops, seed and tools
- Machinery rental
- Government schemes, subsidies, crop insurance and loan guidance

### Community
- Local farmer groups and voice questions
- Expert and agriculture officer directory
- Video and audio lessons, available offline
- Pest and disease outbreak map from farmer reports, with moderation

### Account and system
- Phone-based account, guided onboarding and language selection
- Offline mode with visible sync state
- Notification preferences
- Data export and in-app account deletion
- Admin tools for moderation, source health and data correction

## 4. Architecture

```text
Online sources
     |
Scheduled collectors -> raw snapshots -> normalize -> validate -> publish
                                                          |
                                                    quarantine + alert
                                                          |
PostgreSQL <-> Spring Boot API <-> AI provider
                    |
             versioned sync API
                    |
Android: UI -> ViewModel -> Repository -> Room
                               |
                    WorkManager + outbox sync
```

### Android
- Java, single-activity, AndroidX Navigation, MVVM, repository pattern
- Room, Retrofit, Gson, WorkManager, RecyclerView
- osmdroid for maps, MPAndroidChart for charts, Material Components, Glide for images
- Feature modules: `home`, `farm`, `advisor`, `market`, `community`, `account`
- Shared modules: `core-domain`, `core-data`, `core-ui`, `core-i18n`, `core-sync`
- `core-domain` is plain Java with no Android imports, so JUnit can test it directly

### Backend (proposed)
- Java, Spring Boot, PostgreSQL, Flyway
- Jsoup for static pages and Playwright for Java only where a source needs a browser
- Modules: auth, sync, farm, market, ingestion, ai, community, notifications, admin
- Every write endpoint accepts an idempotency key
- Rate limits on auth, AI and expensive endpoints

### Reuse from the earlier repository

| Action | Items |
|---|---|
| Copy, then review | Crop data, calendars, rotation templates, locations, English and Urdu text, pre-pulled NASA data |
| Port to Java | Climate profile, ET0, scoring, strategies, comparison, alerts, What-If |
| Use as test reference | Existing Python engine tests and sample profiles |
| Rebuild | All screens, map, charts, voice, persistence, accounts, AI service |
| Leave behind | React code, mock data, unused assets |

### Known problems not to carry over
- The selected crop and farm area are ignored by analysis.
- Farms and analyses are not saved.
- `/analyze` is public with no rate limit.
- The AI rate limit trusts a client-controlled `X-Forwarded-For` value.
- The client supplies the analysis used in AI prompts, so it can be forged.
- Tokens never expire, and one token is shared across devices.
- Crop calendars have gaps that are silently skipped.
- Southern Hemisphere seasons are not handled.
- Missing soil data can be reported as success.
- Climate cache rows never expire.

## 5. Automatic daily data pipeline

Collection runs on the server, never on farmers' phones. Phones download only clean, published data.

### Flow
1. A scheduler starts each source's collector.
2. The raw response is saved with source, URL, time and checksum.
3. A source-specific parser extracts records.
4. Records are normalized for crop, market, unit, currency and date.
5. Validation checks required fields, ranges, duplicates and sudden jumps.
6. Valid records are published in one transaction with a new data version.
7. Suspicious records go to quarantine and do not reach farmers.
8. Phones fetch changes through WorkManager.

### Rules
- Every value stores its source, source date and collection time.
- The app shows when data was last updated and labels stale data.
- A failed collection keeps the last good data and never shows it as fresh.
- Collectors are idempotent, so rerunning a day does not create duplicates.
- Each source has its own adapter, schedule, timeout, retry policy and health status.
- A layout change in a source must fail visibly through parser contract tests.
- Check each source's terms and robots rules, use polite request rates, and prefer official APIs or downloads.
- Do not collect personal data from any source.
- The scheduler must run on infrastructure that does not sleep, or be triggered externally.

### Sources

Live check on 2026-11-21 from one network. A failed check may be temporary or caused by that network.

| Data | Source | Result | Plan |
|---|---|---|---|
| Daily climate, solar, humidity, wind, soil wetness | NASA POWER AG API | Working. For Faisalabad, weather ended 2026-11-18 and solar ended 2026-11-15 | Core source. Treat as recent history, not today's weather |
| Field vegetation, 30 m | NASA HLS L30 and S30 v2.0 | Active in NASA CMR | Core field evidence |
| Vegetation history | MOD13Q1, VNP13A1, VJ113A1, MCD15A3H | Active in CMR | Seasonal anomaly baseline |
| Root-zone soil moisture | SMAP L4 SPL4SMGP v008 | Active in CMR | Core water-stress evidence |
| Rainfall | GPM IMERG Early and Late daily v07 | Active in CMR. Final daily v07 is listed complete at 2025-09-30 | Recent rainfall, with product latency shown |
| Crop water stress | ECOSTRESS ECO_L3T_JET and ECO_L4T_ESI, MOD16A2GF | Active in CMR | Use when a clear recent pass exists |
| Land surface temperature | MOD11A1 | Active in CMR | Heat evidence |
| Groundwater indicator | GRACEDADM_CLSM025GL_7D v3.0 | Active in CMR | Regional context only |
| Fire and burned area | NASA FIRMS, MCD64A1 | API page reachable, MCD64A1 active | Residue-burning and fire context |
| Map imagery and extraction | NASA GIBS, AppEEARS | Both reachable | Map layers and point or field extraction |
| Soil | SoilGrids | Reachable | Modeled soil estimate, labelled as such |
| Mandi prices | AMIS Punjab | Working, about 1,041 city-commodity rows. Page table has no visible date | Punjab price source. Requires date, unit and permission check |
| Agromet advisories | PMD National Agromet Centre | Bulletins indexed, automated requests returned HTTP 500 | Do not bypass. Request access |
| General weather and warnings | PMD, NDMA | Main sites reachable | Parser review needed |
| Flood reports | Federal Flood Commission | Report PDF reachable. Site DNS was intermittent | Daily in flood season, 1 July to 30 September |
| Pesticide registration and guidance | Department of Plant Protection, Punjab Agriculture | Main sites reachable. Pest-warning subdomain timed out | Official dose library, human-reviewed |
| Crop research guidance | PARC, Punjab Agriculture | Main sites reachable | Reviewed knowledge base |
| Statistics | Pakistan Bureau of Statistics | Reachable | Context and validation |
| Sindh agriculture, IRSA | Tested domains did not resolve | Not confirmed | Find correct official domains |
| KP agriculture | Site responded with a very small page | Not confirmed | Manual review |
| Forecast | Open-Meteo | Working, but not a Pakistani government source | Label as model forecast. Prefer PMD where available |

### Source permission rules
- PMD's National Agromet site publishes `ai-train=no` and lists many AI crawlers as disallowed. Its content must not be used for AI training, and AI input needs written permission.
- Never bypass blocks, rate limits, login walls or bot protection.
- AMIS and other copyrighted government sites need terms review or written permission before public release.
- NASA data is generally open, but each dataset's license and citation requirements are stored with the source. Downloads may require a server-side Earthdata Login.

### Fertilizer and pesticide guidance policy

Dose information is displayed as published. It is never produced by AgroShift.

**Allowed sources:** government agriculture departments, national or provincial research institutes, public agricultural universities, and the registered product label where the regulator or manufacturer publishes it. Dealer sites, blogs, forums, videos, social media and farmer posts are not allowed.

**Every entry stores:** crop, growth stage, target problem, product or nutrient, published amount and unit exactly as written, region, season, safety instructions, pre-harvest interval, source name, document URL, publication date, retrieval date, checksum and reviewer.

**Publishing rules:**
- Automatic collection may detect new or changed guidance, but dose entries stay in quarantine until a person compares them with the source.
- A source change, parser doubt, missing unit or conflict between sources blocks publication.
- Conflicting official guidance is shown side by side only after review, with no app-selected winner.
- Expired, withdrawn or banned product guidance is removed from farmers' devices at next sync.
- Translations of amounts, units, warnings and safety text need native-speaker review. Numbers and units are stored separately from translated text.

**App and AI rules:**
- AI and app logic must not create, estimate, calculate, scale, convert, summarize or recommend a dose.
- Chat may only open a reviewed entry by ID and render its stored fields, with its source. If none matches, it says so and refers the farmer to a local agriculture officer.
- The app does not calculate quantities for a field, tank or sprayer.
- A photo diagnosis cannot link directly to a dose. The farmer must first confirm the problem with an expert or choose the published guidance themselves.
- Pesticide entries always show protective equipment, re-entry, pre-harvest, storage, disposal and poisoning-response information when the source provides it. Entries missing the source's safety section are not published.
- Each entry shows: follow the product label and check with a local agriculture officer.

**Required tests:** AI output filter tests, source allowlist tests, quarantine tests, unit-preservation tests and withdrawn-entry sync tests.

## 6. Languages

The goal is every Pakistani language across all screens, notifications, reports, voice where possible, and chat. Adding a language must be a data and translation task, with no feature code changes.

| Wave | Languages |
|---|---|
| 1 | Urdu, English, Punjabi, Sindhi, Pashto, Saraiki, Balochi |
| 2 | Hindko, Brahui, Kashmiri, Shina, Balti, Khowar |
| 3 | Remaining regional languages, including Burushaski, Wakhi and others |

### Rules
- A language registry stores code, native name, script, direction, font, digit style, voice support and review status.
- Use Android per-app language support with a language picker that shows native names.
- No hardcoded user-facing text. Use string resources, plurals and locale-aware formatting.
- Maintain an agricultural glossary per language for crops, pests, units, seasons and actions.
- Machine translation may create drafts. Safety, alert and advice text needs native-speaker review.
- Chat replies in the farmer's selected language and falls back clearly when support is limited.
- Where speech recognition or text-to-speech is weak, use recorded audio for key content.
- Test each language for clipping, wrapping, right-to-left layout and mixed-direction numbers.

### Script rules
- Urdu reading text uses Noto Nastaliq Urdu with generous line height.
- Other Arabic-script languages use a font that covers their letters, tested per language.
- Letter spacing on Arabic-script text is always 0.
- No italics or forced uppercase.
- Body text is at least 16sp, and captions at least 13sp.
- Use `start` and `end`, never `left` and `right`.
- Mirror directional icons only. Do not mirror media, clocks, checks or logos.
- Isolate numbers, phone numbers, Latin words and user-entered text in mixed-direction strings.
- Use one digit system per screen.

## 7. Visual direction

Based on [unslop](https://github.com/heydamir/unslop) by Damir Sokolovsky ([@heydamir](https://x.com/heydamir)), licensed CC BY 4.0. Its rules are adapted here from web to native Android. Run its audit on each major screen once built.

### Chosen direction: A. Zameen

Selected by the user on 2026-11-21. Concept canvas: https://claude.ai/artifact/LRyzZEqakBKGUfQjYJoDAb

- **Product:** Helps Pakistani farmers decide and manage work on their fields in their own language.
- **Character:** grounded, plainspoken, watchful.
- **World:** the farmer's land seen from above, with canals, soil and crop rows.
- **Moments:** the farmer's real field on satellite imagery with its boundary highlighted, the farmer's crop photo in Field Lens, and the one chart that matters today.
- **Signature:** a thin **season thread** showing where today sits between Rabi and Kharif, reused on Home, field and calendar screens.
- **Layout:** full-bleed field or photo on top, with a draggable dark bottom sheet holding the decision, evidence and one primary action.

### Zameen tokens

| Token | Value |
|---|---|
| Background | `#10140D` |
| Raised surface | `#1A2015` |
| Accent surface | `#26301A` |
| Hairline | `#30362A` |
| Primary text | `#F3F5E8` |
| Secondary text | `#B9C0A8` |
| Accent | `#D8F36F` |
| Text on accent | `#172006` |
| Canal | `#6FB7C8` |
| Latin typeface | Schibsted Grotesk, weights 400 to 600 |
| Sheet radius | 32dp |
| Card radius | 18dp |
| Primary action | 58dp tall, 20dp radius |

A high-contrast light theme for direct sunlight is still required and must be designed from these roles.

### Rules
- Keep surfaces neutral, with the Zameen lime accent used rarely. Use color mainly for meaning and key content.
- Each screen has one rich element, and everything around it stays quiet.
- No purple gradients, glows, gradient text, glass effects, blobs, emoji labels, sparkle icons or hype copy.
- Use a 4dp spacing grid and equal sizes for equal elements.
- One line icon set, with consistent stroke. Icons are 24dp for outdoor legibility.
- Use weights 400 to 600 and sentence case.
- Copy is short and specific: no exclamation marks, greeting headlines, unverified claims or "AI-powered" labels.
- Changes state their comparison, such as `+12 Rs vs yesterday`.
- One main chart per screen, with one accent series unless comparison requires more.
- Motion explains state. No looping decoration, bounce or animated layout properties.
- Respect the system's reduced-motion setting.

### Farmer-specific overrides
These take priority over unslop's desktop-oriented sizes:

- Minimum interactive target is 48dp, and primary field actions are 56dp.
- Contrast must work in direct sunlight.
- Status is never shown by color alone.
- Important actions use an icon plus text.
- Must run acceptably on low-cost Android phones and slow networks.

## 8. Required UX patterns

| Pattern | Android implementation | Used in |
|---|---|---|
| Skeleton loaders | Layout-matched placeholders, shown only while waiting, with reduced-motion support | Lists, briefing, prices, charts |
| Optimistic updates | Write to Room immediately, add to outbox, show pending state, reconcile or roll back | Diary, tasks, expenses, posts |
| Command menu | Global searchable action sheet with text and voice input | All main screens |
| Shortcut hints | Keycaps for physical keyboards, Android keyboard shortcut helper, launcher shortcuts, one-time gesture hints | Menus, tablets, Chromebooks |
| Undo toasts | Snackbar with Undo and delayed hard delete using tombstones | Delete, archive, complete |
| Stale-While-Revalidate | Show Room data at once, refresh in background, show last-updated state | All remote data |
| Pre-rendering | Baseline Profiles, server-prepared daily briefing, next-screen data prefetch, `PrecomputedTextCompat` for complex scripts | Startup, Home, long text |
| Blurry image placeholders | Server-generated BlurHash or ThumbHash, decoded before Glide loads the image | Listings, diary, lessons |
| View transitions | Material container and shared-element transitions | List to detail, map to field |
| Swipe to dismiss sheet | Material bottom sheets with drag handle, accessible close action, and unsaved-change protection | Filters, quick add, details |
| Interruptible animations | `SpringAnimation` or animators that continue from current value, critically damped | Sheets, cards, navigation |
| Predictive back | AndroidX back callbacks, enabled in the manifest, with progress-driven custom transitions | All navigation and sheets |
| Bigger tap targets | 48dp minimum, 56dp primary, expanded hit areas for small visuals | Entire app |
| Safe area insets | Edge-to-edge with `WindowInsetsCompat` for bars, cutouts, gestures and keyboard | Entire app |
| Tabular numbers | `tnum` feature in fonts that support it, or a dedicated numeric font | Prices, finance, weather |
| Balanced headlines | Balanced line breaking, tested per script | Titles, cards, onboarding |
| Smooth corners | Custom squircle treatment in a shared Material shape system | Cards, sheets, buttons |
| Idempotency keys | Client UUID per mutation, stored in outbox and enforced by a server unique constraint | All writes and payments if added |
| Amazon safe triangle | Hover-intent geometry for nested menus on mouse and trackpad. Touch uses tap-to-open | Tablet and Chromebook menus |

## 9. Course topic mapping

| Topic | Real use in AgroShift |
|---|---|
| Thread start, sleep, stop | Cancellable data refresh workers with retry backoff |
| Inter-thread communication | Bounded producer-consumer buffer for climate record parsing |
| Synchronization | Shared in-memory cache and sync state |
| Locks | Coordinated updates to farm, inventory and finance data |
| Deadlock prevention | Global lock order and timed `tryLock`, with a dedicated stress test |
| Generics | `Result<T>`, `Repository<T, ID>`, `Cache<K, V>`, `Validator<T>` |
| Immutability | Climate records, scores, strategies and published prices |
| Mutability | Controlled draft builders for farms and plans |
| Abstract data types | `RotationPlan`, `CropCalendar`, `AlertQueue`, `SyncOutbox` |
| Exception handling | Typed data errors, retries, fallbacks and user-readable recovery |
| JUnit, TDD, suites | Domain-first tests, plus engine, parser, sync, concurrency and language suites |

## 10. Build order

All features are in scope. This order controls dependencies only.

1. **Foundation:** repo, modules, design system, language system, navigation, Room, sync, backend skeleton.
2. **Core farm:** onboarding, farms, fields, map, calendar, tasks, diary.
3. **Intelligence:** Java climate engine, rotation planner, What-If, alerts, Home briefing.
4. **Data pipeline:** collectors, validation, quarantine, source health, daily publishing.
5. **Farm business:** finance, inventory, labor, machinery, reports.
6. **AI and voice:** grounded chat, voice, photo problem check.
7. **Market:** prices, trends, listings, rental, schemes.
8. **Community:** groups, questions, experts, lessons, outbreak map, moderation.
9. **Language expansion:** complete each wave with review.
10. **Quality:** unslop audit, accessibility, performance, security, low-end device testing, course documentation.

## 11. Definition of done for any feature

- Works offline or explains clearly why it cannot.
- Has loading, empty, error, stale and success states.
- Uses localized text and passes right-to-left checks.
- Follows sections 7 and 8.
- Domain logic has JUnit tests.
- Writes are idempotent.
- Remote data shows its source and update time.
- Advice states uncertainty and refers to a local expert where needed.

## 12. Open items

1. Update the proposal's no-dose wording to match D1, and name who reviews dose entries.
2. Request permission or data access from AMIS and PMD, and find official Sindh, KP, Balochistan and IRSA sources.
3. Choose hosting for the backend, database and daily scheduler.
4. Find native-speaker reviewers for each language.
5. Confirm the project deadline and team responsibilities.
6. Confirm the expanded scope with the course teacher if the signed proposal needs updating.
7. Build a labelled test set of real Pakistani crop photos, reviewed by agriculture experts, for Field Lens.

## 13. Field Lens camera assistant

The farmer opens the camera, takes a photo, asks a question by voice or text in their language, and receives an answer tied to their field, date, NASA observations and official guidance.

### What makes it different
A general AI sees only the photo. Field Lens combines:

1. **The photo:** visible symptoms and image quality.
2. **The field:** crop, variety if known, sowing date, growth stage, irrigation and diary history.
3. **NASA evidence:** recent rain, heat, humidity, soil moisture, vegetation change and water stress at that field.
4. **Official knowledge:** reviewed Pakistani crop, pest and advisory sources.
5. **Local reports:** moderated outbreak reports, always labelled as unverified unless an expert confirms them.

### What each source can prove
- NASA data can show field conditions and whether vegetation has changed.
- NASA data **cannot identify a leaf disease, insect or nutrient deficiency** from a farmer's photo.
- The photo can show symptoms, but many problems look alike.
- A specific cause is stated as confirmed only when an expert or suitable test confirms it.

### Answer format
1. **What the photo shows**
2. **What your field data shows**, with values, dates and NASA product names
3. **What official guidance says**, with the source
4. **Possible causes**, or "cannot determine from this photo"
5. **What to check next**, such as a specific leaf underside, root or neighboring plant
6. **When to contact an expert**

### No-guess rules
- Every factual claim must reference an evidence record.
- A server-side verifier compares every number, date, source and product name with the evidence pack. Unsupported claims are removed or the answer is regenerated.
- The language model never computes indicators. Java code calculates them and passes the results.
- Missing, cloudy, stale or low-resolution satellite data is stated plainly.
- Confidence percentages are not shown until they are calibrated on real field photos.
- Poor photos trigger a retake request with one clear instruction.
- Non-farming questions are outside this feature.
- Dose questions follow the guidance policy in section 5.
- Human, livestock or pesticide poisoning concerns immediately show emergency guidance and urge urgent professional help.

### Immediate response flow
1. On-device checks run for blur, darkness and subject framing.
2. The app sends a compressed image, question, field ID and language.
3. The server loads a **prebuilt daily evidence pack** for the field, so NASA calls do not delay the answer.
4. The app immediately shows the field evidence cards.
5. Visual analysis runs, then the verified answer streams section by section.
6. Follow-up questions keep the same photo and evidence.

Target: first useful content in under 2 seconds and a complete answer in under 8 seconds on 4G. These targets are not yet measured.

**Offline:** the app saves the photo and question, shows cached field evidence, and answers after reconnecting. It does not pretend to diagnose offline.

### Daily evidence pack
For each active field, a scheduled job stores recent weather, rainfall, soil moisture, vegetation trend against its historical baseline, heat and water stress flags, official alerts, data dates, and quality flags.

### Privacy
- Strip photo EXIF metadata before upload.
- Use field location only with consent.
- Photos are not used for model training unless the farmer opts in.
- Farmers can delete photos and their answers.

### Evaluation before release
Test on expert-labelled Pakistani field photos by crop, region, season and phone quality. Lab-dataset accuracy is not accepted as proof, because published studies show large drops on real field images. Track correct abstentions, unsupported-claim rate, harmful-advice rate, language quality and response time.
