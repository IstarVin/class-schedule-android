# Class Schedule

An Android app for viewing the AJ schedule and keeping the current class close at hand with selectable home-screen widgets.

The app uses a small native shell around [AJ Schedule](https://ajschedule.pages.dev/). It also keeps a validated local copy of the schedule so the app and widget can continue to show saved data when the network is unavailable.

## Features

- WebView-based schedule experience with a native loading and offline fallback UI.
- Three home-screen widget types in the widget picker: Compact, Now & Next, and Today.
- Widget states for the current class, next class, completed day, and days without classes.
- Widget customization for light/dark appearance, Material You colors, time format, countdown, room, next-class, and progress-bar visibility.
- Automatic schedule refresh every six hours when a network connection is available.
- Refreshes on widget actions, date/time/time-zone changes, locale changes, device boot, and app updates.
- Bundled schedule data for a useful first launch before the first successful network refresh.

## Requirements

- Android Studio with the Android SDK platform and build tools required by `compileSdk 37`.
- Android 11 (API 30) or newer.
- A Java runtime supported by the Android Gradle Plugin used by the project.

## Build and test

From the project root:

```bash
# Run local unit tests
./gradlew test

# Build the debug APK
./gradlew assembleDebug

# Install the debug APK on a connected device or emulator
./gradlew installDebug
```

The debug APK is written to `app/build/outputs/apk/debug/`.

Release builds use the signing values in the root-level `keystore.properties` file. Keep that file and the referenced keystore out of version control.

## Using the widget

1. Install and open Class Schedule once so the bundled schedule is initialized.
2. Choose **Class Schedule · Compact**, **Now & Next**, or **Class Schedule · Today** from the home-screen widget picker. Each type has its own minimum size and preview.
3. Set display options in the configuration screen.
4. Resize or reconfigure the widget later from the home screen as needed.

The widget reads the same cache used by the app and does not perform network requests from its broadcast receiver. Network synchronization is handled by WorkManager, while time and system events trigger a local redraw.

## Schedule data

The initial data lives at [`app/src/main/assets/schedule.json`](app/src/main/assets/schedule.json). The app refreshes the same format from:

```text
https://ajschedule.pages.dev/schedule.json
```

The JSON contains a `subjects` object and an `events` array. Each event uses a weekday number (`1` = Monday through `7` = Sunday), start and end times in minutes after midnight, a subject `code`, and an optional `room`:

```json
{
  "subjects": {
    "ITE183": { "name": "Web Systems and Technologies", "sec": "LM25-4" }
  },
  "events": [
    { "day": 2, "start": 750, "end": 900, "code": "ITE183", "room": "TBA" }
  ]
}
```

Adjacent events for the same subject are merged when the widget builds its daily view.

## Project structure

```text
app/src/main/java/com/istarvin/classschedule2/
├── MainActivity.kt                         WebView shell and app actions
├── data/
│   ├── ScheduleCache.kt                    Shared validated schedule cache
│   ├── ScheduleSyncWorker.kt               Periodic and manual synchronization
│   └── OfflineScheduleHtml.kt              Local HTML fallback
└── widget/
    ├── AjScheduleWidgetProvider.kt         AppWidget provider
    ├── WidgetConfigurationActivity.kt      Widget setup screen
    ├── WidgetRefreshReceiver.kt            Event-driven widget refreshes
    └── WidgetUpdateCoordinator.kt           Widget state and rendering
```

## License

No license has been declared for this project yet.
