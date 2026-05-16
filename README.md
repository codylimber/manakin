# Manakin

A field companion app for exploring species phenology — what's active near you, right now.

Available for **Android** and **iOS**.

Manakin uses community science data from [iNaturalist](https://www.inaturalist.org) to show you which species are most likely to be observed in your area this week, when they'll peak, and how their activity changes throughout the year.

Named after the White-throated Manakin because it was the only .png of an animal that I had on my computer that I had made. Also a play on Merlin (the bird ID app), but for all of nature.

## Features

- **Data packs** — download species data for any location and taxonomic group from the iNat API. Combine multiple locations and taxa in one pack.
- **Explore** — species list sorted by likelihood, with status badges (Peak, Active, Early/Late), rarity dots, mini phenology charts, and activity percentages.
- **Targets** — starred species, lifer targets, and new-for-area species. Swipe right to star, swipe left to remove.
- **iNaturalist integration** — connect your username to see which species you've observed. Blue checkmarks on seen species, filter to unseen.
- **Trip planning** — date picker and date range for future trips. Trip reports with save/load and share.
- **Activity timeline** — weekly changelog of what entered peak, became active, or went inactive.
- **Compare locations** — side-by-side view of species unique to each dataset.
- **Home screen widget** (Android) — configurable: top active species, organism of the day, weekly changes, or targets.
- **Notifications** — weekly digest of newly active/peak species on configurable days. Daily target species alerts before peak.
- **Dataset sharing** — export packs as `.manakin` files or bundles, import from friends.
- **Light/dark mode**, scientific name toggle, configurable sort and activity threshold.

## Screenshots

*TODO: Add screenshots*

## Installing the App

### Android — Install from APK

The easiest way to try Manakin on Android:

1. Download the latest APK from the [Releases](https://github.com/codylimber/manakin/releases) page (or build it yourself — see below)
2. Transfer the APK to your Android device
3. Open it and tap **Install** (you may need to enable "Install from unknown sources" in Settings)

### iOS — Build from Source

iOS requires building from source with Xcode (no sideloading like Android):

1. You need a **Mac with Xcode** installed (free from the App Store)
2. Clone the repo:
   ```bash
   git clone https://github.com/codylimber/manakin.git
   cd manakin/ios
   ```
3. Install [XcodeGen](https://github.com/yonaskolb/XcodeGen) (if not already installed):
   ```bash
   brew install xcodegen
   ```
4. Generate the Xcode project:
   ```bash
   xcodegen generate
   ```
5. Open in Xcode:
   ```bash
   open Manakin.xcodeproj
   ```
6. In Xcode:
   - Select the **Manakin** target > **Signing & Capabilities**
   - Check **Automatically manage signing**
   - Select your **Personal Team** (sign in with your Apple ID if needed)
7. Select a destination:
   - **Simulator** (e.g., iPhone 16 Pro) — works immediately, no device needed
   - **Your iPhone** — plug in via USB, trust the device
8. Press **Cmd+R** to build and run

> **Note:** Apps built with a free Apple ID expire after 7 days on a physical device. For longer-term use or sharing via TestFlight, a paid Apple Developer account ($99/year) is needed.

## Building from Source

### Android

**Prerequisites:** [Android Studio](https://developer.android.com/studio) (Ladybug or newer), JDK 21

1. Clone the repo:
   ```bash
   git clone https://github.com/codylimber/manakin.git
   cd manakin
   ```
2. Open in Android Studio: File > Open > select the `manakin` folder
3. Wait for Gradle sync, then click Run (or `Shift+F10`)

To build an APK:
```bash
./gradlew assembleDebug
```
The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

### iOS

**Prerequisites:** Mac with [Xcode](https://apps.apple.com/app/xcode/id497799835) (free), [XcodeGen](https://github.com/yonaskolb/XcodeGen) (`brew install xcodegen`)

```bash
cd ios
xcodegen generate
open Manakin.xcodeproj
```

Then set your signing team and run on a simulator or device (see install instructions above).

The app comes bundled with a Connecticut butterflies dataset so you can explore immediately. Download more packs from within the app (Datasets tab > +).

## Project Structure

```
manakin/
├── app/                              # Android app (Kotlin + Jetpack Compose)
│   └── src/main/java/com/codylimber/fieldphenology/
│       ├── MainActivity.kt
│       ├── data/                     # API client, models, repository, generator
│       ├── notifications/            # Weekly digest worker
│       ├── widget/                   # Home screen widget (Glance)
│       └── ui/                       # Screens, components, theme, navigation
│
├── ios/                              # iOS app (SwiftUI)
│   ├── project.yml                   # XcodeGen project spec
│   └── Manakin/
│       ├── ManakinApp.swift
│       ├── Data/                     # API client, models, repository, generator
│       ├── Notifications/            # Notification manager
│       └── UI/                       # Screens, components, theme, navigation
│
├── README.md
└── LICENSE
```

## Tech Stack

### Android
- **Kotlin** + **Jetpack Compose** (Material 3)
- **OkHttp** for networking
- **kotlinx.serialization** for JSON
- **Coil 3** for image loading
- **WorkManager** for background notifications
- **Glance** for home screen widgets

### iOS
- **Swift** + **SwiftUI** (iOS 17+)
- **URLSession** for networking
- **Codable** for JSON
- **Swift concurrency** (async/await, actors)
- No third-party dependencies

Both platforms use JSON files (no database) and share the same bundled dataset.

## Data & Attribution

All species data comes from [iNaturalist](https://www.inaturalist.org), a community science platform. Phenology patterns are based on research-grade observations.

Only Creative Commons licensed photos are used. Individual attributions are displayed on each photo.

Species descriptions are from Wikipedia (CC-BY-SA).

Manakin is not affiliated with or endorsed by iNaturalist.

## Contributing

1. Fork the repo
2. Create a feature branch: `git checkout -b my-feature`
3. Make your changes
4. Test on a device or emulator
5. Push and open a PR: `git push -u origin my-feature`

Please test with at least one downloaded dataset before submitting.

## License

MIT License. See [LICENSE](LICENSE) for details.
