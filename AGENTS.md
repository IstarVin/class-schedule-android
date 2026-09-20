# AGENTS.md

## Project overview

`class-schedule-2` is a single-module Android application (`:app`) written in Kotlin. It displays a class schedule in a WebView-based app shell and in configurable home-screen widgets.

Important areas:

- `app/src/main/java/com/istarvin/classschedule2/data/` — schedule caching and WorkManager synchronization.
- `app/src/main/java/com/istarvin/classschedule2/widget/` — widget state, layout selection, rendering, preferences, and refresh scheduling.
- `app/src/main/res/layout/` — compact, medium, and large widget layouts.
- `app/src/main/assets/schedule.json` — bundled fallback schedule used when no refreshed cache exists.
- `app/src/test/` — local JVM tests, especially widget time/state transitions.

The schedule data flow is: bundled asset or downloaded JSON → `ScheduleCache` → `WidgetScheduleReader` → `WidgetUpdateCoordinator` → `RemoteViews`.

## Development commands

Run commands from the repository root with the Gradle wrapper:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

For a broader verification pass, run:

```bash
./gradlew check
```

Use `./gradlew.bat` for Windows environments. Do not rely on generated files under `build/` or `app/build/` when reviewing changes.

## Implementation guidance

- Keep the app compatible with the configured Android constraints: namespace/application ID `com.istarvin.classschedule2`, minimum SDK 30, target/compile SDK 37, and Java 11 source/target compatibility.
- Prefer Kotlin and existing AndroidX/Material dependencies. Avoid adding a dependency for functionality that can be implemented with the current stack.
- Keep network and file I/O off the main thread. Schedule refresh work through `ScheduleSyncWorker`; widgets should read the local cache and must not perform network requests.
- Treat `ScheduleCache` as the shared data handoff. Validate downloaded JSON before replacing the cached file, and preserve the bundled fallback behavior.
- When changing widget behavior, update all three widget layouts and the corresponding `RemoteViews` binding paths where applicable: compact, medium, and large.
- Preserve widget configuration options such as style, appearance, color source, time format, countdown, room, next-class, and progress visibility.
- Keep time calculations deterministic and testable. Add or update JVM tests in `app/src/test/` for class boundaries, date transitions, empty schedules, and weekly rollover behavior.
- Use resources for user-visible strings and follow the existing light/dark and dynamic-color resource structure. Avoid hardcoding new user-facing text in Kotlin.
- Do not commit credentials, keystores, `local.properties`, or other machine-specific configuration. Release signing reads `keystore.properties`; keep its values local.

## Change and verification checklist

Before handing off a change:

1. Inspect the diff and make sure unrelated user changes are preserved.
2. Run the smallest relevant test task; for widget/data logic, run `./gradlew testDebugUnitTest`.
3. Run `./gradlew lintDebug` for Android-facing changes and resource changes.
4. If layouts or manifest/widget behavior changed, build with `./gradlew assembleDebug`.
5. Mention any verification that could not be run and why.

