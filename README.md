# Tempo — focus-first time tracker for Android

Tempo is a minimal Android app for the moment when you have something to do and do **not** want to lose concentration setting up a complicated productivity system.

The main flow is deliberately simple:

1. Open Tempo.
2. Write what you are about to work on / study / train / create.
3. Optionally choose a project and planned duration.
4. Tap **Start focus**.
5. Leave the app and do the work.

Projects, goals, analytics, history, backup and sync sit underneath that flow instead of getting in its way.

## What is included

- Focus-first launch screen: task/intention before timer
- Start / pause / resume / finish with exact timestamp-based tracking
- Separate work and break intervals inside every session
- Optional planned session length with live progress
- Project colors, symbols, archiving and weekly time goals
- Automatic **General** project when you just want to start without organizing first
- Goal Compass: remaining weekly time and suggested daily pace
- Editable history and manual time entry
- Notes, completion outcome and optional 1–5 focus-quality reflection
- Today / weekly / 30-day / all-time analytics
- Longest uninterrupted focus block, streaks, project split and estimate accuracy
- Context-switch tracking so fragmented work becomes visible
- Forgotten-timer guard
- Persistent timer notification with pause / resume / finish actions
- Local-first SQLite storage; the timer works without internet or an account
- Optional Firebase email/password and Google sign-in
- Optional Firestore multi-device sync with timestamp-based reconciliation
- JSON backup/import and CSV export
- System / light / dark themes, dynamic color, week-start and clock preferences
- Account deletion and local-data deletion controls
- GitHub Actions validation workflow
- Firebase, Play closed-testing, privacy and Data Safety setup documentation

## Product philosophy

Tempo is not meant to become another dashboard you spend time managing. The app should help with three questions:

- **What am I doing right now?** — focus launch and timer.
- **Am I using my time roughly the way I intended?** — plans and weekly goals.
- **Where did my time actually go?** — history and lightweight analytics.

Projects are useful organization, not mandatory ceremony. If no project is selected, Tempo automatically uses a neutral `General` project.

## Stack

- Kotlin + Jetpack Compose / Material 3
- Android Gradle Plugin 9.4.0, Gradle 9.6.0
- AGP 9 built-in Kotlin + Kotlin 2.3.21 Compose Compiler Gradle plugin
- compileSdk 37, targetSdk 36, minSdk 26
- Firebase Authentication + Cloud Firestore (optional)
- Android Credential Manager for Google sign-in
- SQLiteOpenHelper + DataStore

## First run

Open the project in a current Android Studio and let Gradle sync. **Firebase is optional:** without `app/google-services.json`, Tempo remains a fully functional local-only tracker.

For cloud accounts and sync, follow [`docs/FIREBASE_SETUP.md`](docs/FIREBASE_SETUP.md).

For a release / Google Play closed-testing build, follow [`docs/PLAY_CLOSED_TESTING.md`](docs/PLAY_CLOSED_TESTING.md).

## Build

GitHub Actions installs Gradle 9.6.0 directly, so the repository CI does not depend on a preinstalled Gradle version.

```bash
gradle test lint assembleDebug
gradle bundleRelease
```

A standard `gradle/wrapper/gradle-wrapper.properties` is included. If you want the conventional `./gradlew` / `gradlew.bat` launchers in the repository too, generate them once with:

```bash
gradle wrapper --gradle-version 9.6.0
```

## Validation performed before packaging

- Core analytics/time-accounting Kotlin compiled and executed successfully.
- Work vs break totals, longest focus block, duration formatting and goal progress were exercised in the local check.
- All Kotlin files were parser/static-checked after the focus-first UI revision, including custom Compose callback call sites.
- Project tree was checked for TODO/FIXME implementation stubs.
- No Firebase config, signing key or other private credential is bundled.

A full Android APK/AAB build still needs an Android SDK + dependency download environment (Android Studio or the included GitHub Actions workflow). That is the final machine-level verification step before Play upload.

## Privacy model

Tempo is local-first. Guest data stays on-device unless the user explicitly exports it. Signed-in data is stored under that Firebase user's UID. No ads SDK, analytics SDK, location tracking, contacts, microphone, camera, or accessibility service is included.

See [`docs/PRIVACY_POLICY_TEMPLATE.md`](docs/PRIVACY_POLICY_TEMPLATE.md) and [`docs/DATA_SAFETY.md`](docs/DATA_SAFETY.md) before publishing.

## Repository hygiene

Never commit:

- `app/google-services.json`
- `keystore.properties`
- `*.jks` / `*.keystore`
- `local.properties`
- generated APK/AAB/build output

Those are already covered by `.gitignore`.
