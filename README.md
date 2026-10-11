# Class Schedule

An Android app for viewing the schedule and keeping the current class close at hand with selectable home-screen widgets.

The app uses a small native shell around [Alvin’s website](https://ajschedule.pages.dev/) and [Sue’s website](https://sue.ajschedule.pages.dev/). Both complete websites are packaged in the APK, including fonts, icons, and schedule data, so a fresh install works offline without visiting either website first.

## Features

- Full WebView-based schedule experience available offline from the first launch, for both profiles.
- Three home-screen widget types in the widget picker: Compact, Now & Next, and Today.
- Widget states for the current class, next class, completed day, and days without classes.
- Widget customization for light/dark appearance, Material You colors, time format, countdown, room, next-class, and progress-bar visibility.
- Automatic website and schedule refresh for both profiles every six hours when a network connection is available.
- Refreshes on widget actions, date/time/time-zone changes, locale changes, device boot, and app updates.
- Bundled websites and schedule data for first launch before the first successful network refresh.

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

The checked-in website files under `app/src/main/assets/websites/` are automatically included in every build. Builds do not need internet access to fetch the websites. To refresh those bundled copies before building, run:

```bash
python3 tools/bundle_websites.py
./gradlew assembleDebug
```

The downloader saves the pages, scripts, manifests, icons, schedule JSON, font stylesheet, and font files. It finishes downloading both sites before replacing the source bundles. Review updated website assets as executable source before distributing a build.

At runtime, the app immediately loads the last complete downloaded revision, or the packaged website if no usable downloaded revision exists. WorkManager queues refreshes on app launch, profile changes, and the Refresh action, and waits for a connection when offline. It validates the schedule and every discovered static dependency before atomically replacing a website. Failed downloads preserve the previous revision. An open page reloads after a successful update.

Each profile uses its own private HTTPS WebView origin and local storage. WebView requests are served exclusively from a single saved revision, including external fonts. The upstream service-worker registration is disabled in the served HTML because native WorkManager and internal storage manage updates; this prevents a second browser cache from masking new revisions. The original downloaded source files are preserved in the APK.

Release builds use the signing values in the root-level `keystore.properties` file. Keep that file and the referenced keystore out of version control.

## Using the widget

1. Install and open Class Schedule once so the bundled schedule is initialized.
2. Choose **Class Schedule · Compact**, **Now & Next**, or **Class Schedule · Today** from the home-screen widget picker. Each type has its own minimum size and preview.
3. Set display options in the configuration screen.
4. Resize or reconfigure the widget later from the home screen as needed.

The widget reads the same cache used by the app and does not perform network requests from its broadcast receiver. Network synchronization is handled by WorkManager, while time and system events trigger a local redraw.

## Schedule data

The initial widget data lives at [`app/src/main/assets/schedule.json`](app/src/main/assets/schedule.json) for Alvin and [`app/src/main/assets/schedule_sue.json`](app/src/main/assets/schedule_sue.json) for Sue. Website updates also supply the validated data to the widget cache. Alvin uses:

```text
https://ajschedule.pages.dev/schedule.json
```

Sue’s schedule is extracted from the literal subject and event tables in `https://sue.ajschedule.pages.dev/`.

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
│   ├── WebsiteCache.kt                     Complete downloaded website revisions
│   ├── WebsiteSnapshot.kt                  Asset discovery and revision validation
│   └── OfflineScheduleHtml.kt              Local HTML fallback
└── widget/
    ├── ClassScheduleWidgetProvider.kt     AppWidget provider
    ├── WidgetConfigurationActivity.kt      Widget setup screen
    ├── WidgetRefreshReceiver.kt            Event-driven widget refreshes
    └── WidgetUpdateCoordinator.kt           Widget state and rendering
```

## License

No license has been declared for this project yet.
