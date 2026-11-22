<div align="center">

# AgroShift

**A field-first Android companion for Pakistani farmers, built with Java, NASA Earth data and local languages.**

![Version](https://img.shields.io/badge/version-0.10.2%20preview-D8F36F?style=flat-square&labelColor=10140D)
![Java](https://img.shields.io/badge/Java-17-D8F36F?style=flat-square&labelColor=10140D)
![Android](https://img.shields.io/badge/Android-7.0%2B-D8F36F?style=flat-square&labelColor=10140D)
![Offline first](https://img.shields.io/badge/offline-first-D8F36F?style=flat-square&labelColor=10140D)

<img src="docs/assets/readme-banner.jpg" alt="Pakistani rice, wheat and mustard fields with farmers at harvest" width="100%">

### [Download the Android APK (0.10.2 preview, 13.7 MB)](https://github.com/abdulrehman688/agroshift_ai/raw/main/releases/AgroShift-0.10.2-preview.apk)

</div>

> **Preview build:** it passes the build, lint and 100 automated tests, and the latest changes were checked on a real phone. It uses a development signature, so Android may ask you to allow installation from this source.

## Overview

AgroShift helps farmers decide what to do on their own land, keep farm records, and get help in their language. The app is designed for weak connectivity, low-cost phones and outdoor use.

It combines each farmer's field with NASA climate data. When evidence is missing, the app is designed to say so instead of guessing.

## What is in the app

- Bilingual Urdu and English onboarding with real Pakistani farm photography
- Modern Home with a live weather sky, all-farms switcher, 7-day forecast, draggable Rabi and Kharif crop timeline, field map and quick actions
- Live canal turn scene with countdown and soil water meter
- Interactive Farm, field records and Market pages with progress, filters and sales charts
- Live What-If field, score ring and tappable rain chart in Advisor
- Interactive crop guide with a season slider and crop strengths
- Global search across farms, records, crops and actions, with voice input
- Full-screen map field setup with a draggable form, GPS, place search and satellite view
- Daily irrigation estimate from forecast rain, evapotranspiration, soil and your last watering date
- Weekly canal turn (warabandi) alarm that survives phone restarts
- Frost, heat, heavy rain and strong wind alerts for the next three days
- Crop rotation analysis from 20 years of NASA POWER data, with scores, risks, reasons and actions
- What-If scenarios for heat and lower rainfall
- Farm tasks, costs, sales, notes and profit, saved offline with Room
- Field-aware assistant with voice input, read aloud, copy and saved chat history you can continue
- Chat in English, Urdu, Punjabi and Sindhi, with Saraiki in beta
- Market and Help sections, with official AMIS price access
- Zameen design system with safe areas, large touch targets and right-to-left layout

Urdu is a machine draft awaiting native review. Punjabi, Sindhi, Pashto, Saraiki and Balochi can be selected for the app, and their screens show English until translated.

## Sprint plan

| Sprint | Focus | Status |
|---|---|---|
| **1. Useful every day** | Map room, modern Home, irrigation answer, canal turn alarm, danger alerts | Built, awaiting phone test |
| **2. Course requirements** | Proposal gaps, tests, topic-to-code report, proposal wording | Built in 0.8.0 |
| **Living Photo Engine** | Real photos animated by GPU shaders and farm data | Built across Home, Advisor, Help, Farm, canal and onboarding in 0.10.2, checked on phone |
| **Crop Plan Studio** | All crop plans, filters, plan builder and comparison | Approved, starts after the prototype |
| **3. Polish and performance** | Remaining UX patterns, final fonts, sunlight theme, low-end performance | Planned |
| **4. Big features** | Spring Boot backend, daily data pipeline, Field Lens, more languages | Planned |
| **Later** | Listings, machinery rental, community | If time allows |

See the full [roadmap](docs/Roadmap.md).

## How the Living Photo Engine is made

> **Status:** version 0.10.2 brings bright real photos to the whole app and has been checked on a phone.

AgroShift has replaced its cartoon-style drawings with **real farm photos brought to life on the phone's GPU**. Farm data decides what moves.

### Before and after

**Canal turn**

<img src="docs/assets/living-photo/before-after-canal.gif" alt="Old drawn canal beside the real animated canal photo" width="724">

**Crop growth from the season timeline**

<img src="docs/assets/living-photo/before-after-crop.gif" alt="Old drawn wheat stems beside real wheat photos changing step by step from sprouts to harvest" width="724">

**What-If stress on a real crop**

<img src="docs/assets/living-photo/living-what-if.gif" alt="Real wheat field turning dry and golden under hotter and drier conditions" width="448">

The before frames come from the app's old drawing code. The after frames come from the app's current shader, rendered in WebGL on a computer.

### The process

| 1. Real photo | 2. Measure the canal | 3. Build the mask |
|:---:|:---:|:---:|
| <img src="docs/assets/living-photo/1-original.jpg" alt="Original photo of an irrigation canal in Faisalabad" width="270"> | <img src="docs/assets/living-photo/2-measure.jpg" alt="Canal photo with a measuring grid" width="270"> | <img src="docs/assets/living-photo/3-mask.jpg" alt="Mask with water in red, plants in green and sky in blue" width="270"> |
| Openly licensed canal photo | A grid is used to trace the water edge | Red is water, green is plants, blue is sky |

Then an OpenGL ES shader moves only the marked areas, using the field's real canal turn, weather and local time:

| Calm day | Canal turn running | Rain and wind |
|:---:|:---:|:---:|
| <img src="docs/assets/living-photo/scene-calm.jpg" alt="Calm canal in daylight" width="270"> | <img src="docs/assets/living-photo/scene-turn.jpg" alt="Canal with faster water during a turn" width="270"> | <img src="docs/assets/living-photo/scene-rain.jpg" alt="Canal in rain and wind" width="270"> |

| Golden evening | Night | Extreme heat |
|:---:|:---:|:---:|
| <img src="docs/assets/living-photo/scene-golden.jpg" alt="Canal in golden evening light" width="270"> | <img src="docs/assets/living-photo/scene-night.jpg" alt="Canal at night" width="270"> | <img src="docs/assets/living-photo/scene-heat.jpg" alt="Canal in extreme heat" width="270"> |

### What 0.10.2 adds

| Step | What happens |
|---|---|
| Real crop stages | 11 crops have 5 or 6 real photos each, from early growth to harvest |
| Step by step | The Rabi and Kharif timeline and crop guide slider move through those real photos |
| Clean changes | Each new photo slides in beside the last one, so two photos are never merged |
| Real conditions | Weather, canal turn and What-If choices change wind, rain, heat and crop stress. Photos stay bright at night |
| All devices | The engine picks a still, light, balanced or high tier from memory, processor, battery saver and motion settings |
| Saving power | Scenes pause when hidden or scrolled away, and lists use still photos |

These frames were rendered from the app's actual shader in WebGL on a computer. Read the full process in [Living Photo Engine](docs/Living%20Photo%20Engine.md).

## Architecture

```mermaid
flowchart LR
    App[app] --> Home[feature-home]
    App --> Account[feature-account]
    App --> Sync[core-sync]
    Home --> UI[core-ui]
    Home --> Data[core-data]
    Account --> UI
    Sync --> Data
    UI --> I18n[core-i18n]
    Data --> Domain[core-domain]
    I18n --> Domain
```

| Module | Responsibility |
|---|---|
| `app` | Activity, navigation, command menu and dependency wiring |
| `feature-home` | Home screen |
| `feature-farm` | Field setup, field details and farm records |
| `feature-advisor` | Crop rotation analysis and What-If |
| `feature-market` | Market section |
| `feature-help` | Help, crop guide and assistant |
| `feature-account` | Language selection and onboarding |
| `core-ui` | Zameen theme and reusable components |
| `core-i18n` | Language selection and translated strings |
| `core-data` | Room database and repositories |
| `core-sync` | WorkManager outbox synchronization |
| `core-domain` | Pure Java models, validation, season logic and concurrency |

## Tech stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| UI | Android Views, Material Components, AndroidX Navigation |
| Local data | Room |
| Background work | WorkManager |
| Testing | JUnit 4 |
| Planned backend | Spring Boot, PostgreSQL |
| Maps | osmdroid |
| Charts | MPAndroidChart |
| Networking | Retrofit, Gson, OkHttp |

## Getting started

### Requirements

- JDK 21 to run Gradle
- Android SDK 34
- Android 7.0 or later device or emulator

### Build and test

```bash
git clone https://github.com/abdulrehman688/agroshift_ai.git
cd agroshift_ai
./gradlew :core-domain:test :app:assembleDebug
```

The debug APK is created at `app/build/outputs/apk/debug/app-debug.apk`.

## Data and safety principles

- Every data value must keep its source and date.
- Phones receive processed field results and never download raw satellite files.
- NASA data describes field conditions. It does not identify a crop disease from a photo.
- Fertilizer and pesticide amounts may only be shown from human-reviewed official sources.
- AI must never create, estimate or calculate a dose.
- Credentials stay on the backend and are never committed or shipped in the app.

Planned data sources are listed in [Data Sources](docs/Data%20Sources.md).

## Documentation

The [`docs`](docs/Home.md) folder is an [Obsidian](https://obsidian.md) vault and also reads normally on GitHub.

- [Project home](docs/Home.md)
- [Roadmap](docs/Roadmap.md)
- [Architecture](docs/Architecture.md)
- [Design system](docs/Zameen%20Design%20System.md)
- [UX patterns](docs/UX%20Patterns.md)
- [Course mapping](docs/Course%20Mapping.md)
- [Course report](docs/Course%20Report.md)
- [Living Photo Engine](docs/Living%20Photo%20Engine.md)
- [Image credits](docs/Image%20Credits.md)
- [Changelog](CHANGELOG.md)

## Acknowledgments

- Project concept builds on the earlier [NASA AgroShift AI](https://github.com/asifnawaz23/nasa_agroshift_AI) work.
- Interface principles adapted from [unslop](https://github.com/heydamir/unslop) by Damir Sokolovsky, licensed CC BY 4.0.
- App photographs come from Wikimedia Commons contributors under CC BY and CC BY-SA licenses. See [Image Credits](docs/Image%20Credits.md).
- Climate and Earth observation data comes from NASA programs. AgroShift is not affiliated with or endorsed by NASA.

## Academic context

AgroShift is developed for the Software Construction and Development course at Sir Syed University of Engineering and Technology.
