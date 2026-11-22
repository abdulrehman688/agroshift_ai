# AgroShift

Read and follow the project context before planning or changing anything:

@docs/PROJECT_CONTEXT.md

## Documentation rules

- `docs/` is the Obsidian vault and the project's documentation source of truth.
- When a product, architecture, data or UX decision changes, update `docs/PROJECT_CONTEXT.md` and the related note in the same task.
- At each milestone, update `README.md`, `CHANGELOG.md`, `docs/Home.md`, `docs/Roadmap.md` and `docs/UX Patterns.md`.
- When a visible screen changes, refresh its image in `docs/assets/screenshots/`.
- Mark features as built only after they run. Keep planned work labelled as planned.
- Never commit credentials, tokens, keystores or `.env` files.

## Local build

Use JDK 21 and keep Gradle on D: because C: is nearly full.

```bash
JAVA_HOME="C:/Program Files/java/jdk-21.0.1" GRADLE_USER_HOME="D:/AndroidBuild/gradle-home" ./gradlew :core-domain:test :app:assembleDebug
```
