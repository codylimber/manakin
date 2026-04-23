# Manakin

A field companion app for exploring species phenology — what's active near you, right now.

Manakin uses community science data from [iNaturalist](https://www.inaturalist.org) to show you which species are most likely to be observed in your area this week, when they'll peak, and how their activity changes throughout the year.

Named after the White-throated Manakin because it was the only .png of any animal that I had on my computer that I had made. Also a play on Merlin (the bird ID app), but for all of nature.

## Features

- **Data packs** — download species data for any location and taxonomic group from the iNat API. Combine multiple locations and taxa in one pack.
- **Explore** — species list sorted by likelihood, with status badges (Peak, Active, Early/Late), rarity dots, mini phenology charts, and activity percentages.
- **Targets** — starred species, lifer targets, and new-for-area species. Swipe right to star, swipe left to remove.
- **iNaturalist integration** — connect your username to see which species you've observed. Blue checkmarks on seen species, filter to unseen.
- **Trip planning** — date picker and date range for future trips. Trip reports with save/load and share.
- **Activity timeline** — weekly changelog of what entered peak, became active, or went inactive.
- **Compare locations** — side-by-side view of species unique to each dataset.
- **Home screen widget** — configurable: top active species, organism of the day, weekly changes, or targets.
- **Notifications** — weekly digest of newly active/peak species, target species alerts before peak.
- **Dataset sharing** — export packs as `.manakin` files, import from friends.
- **Light/dark mode**, scientific name toggle, configurable sort and activity threshold.

## Screenshots

*TODO: Add screenshots*

## Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (Ladybug or newer)
- JDK 21 (bundled with Android Studio)
- An Android device or emulator (API 26+, Android 8.0+)

### Setup

1. Clone the repo:
   ```bash
   git clone https://github.com/codylimber/manakin.git
   cd manakin
   ```

2. Open in Android Studio:
   - File > Open > select the `manakin` folder
   - Wait for Gradle sync to complete

3. Run:
   - Select a device/emulator from the toolbar
   - Click the green play button (or `Shift+F10`)

The app comes bundled with a Connecticut butterflies dataset so you can explore immediately. Download more packs from within the app (Datasets tab > +).

### Building the APK

```bash
./gradlew assembleDebug
```

The APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Project Structure

```
app/src/main/java/com/codylimber/fieldphenology/
├── MainActivity.kt
├── data/
│   ├── api/
│   │   ├── INatApiClient.kt      # Rate-limited iNat API client
│   │   ├── ApiModels.kt          # Search result models
│   │   └── LifeListService.kt    # Observation tracking + caching
│   ├── generator/
│   │   ├── DatasetGenerator.kt   # Downloads species data from iNat
│   │   └── DataProcessor.kt      # Rarity, weekly matrix, flight periods
│   ├── model/
│   │   └── Dataset.kt            # @Serializable data classes
│   └── repository/
│       └── PhenologyRepository.kt # Loads datasets from assets + storage
├── notifications/
│   └── WeeklyDigestWorker.kt     # Background notification worker
├── widget/
│   └── ManakinWidget.kt          # Home screen widget
└── ui/
    ├── theme/                    # Colors, theme, AppSettings
    ├── components/               # StatusBadge, RarityDot, etc.
    ├── navigation/               # Routes, GenerationParams
    └── screens/
        ├── main/                 # Bottom nav shell
        ├── specieslist/          # Explore tab + SpeciesCard
        ├── speciesdetail/        # Species detail + phenology chart
        ├── targets/              # Targets tab
        ├── compare/              # Compare locations
        ├── timeline/             # Activity timeline
        ├── tripreport/           # Trip reports
        ├── adddataset/           # Dataset creation form
        ├── generating/           # Generation progress
        ├── managedatasets/        # Dataset management
        ├── settings/             # App settings
        ├── onboarding/           # First-launch walkthrough
        ├── help/                 # Help page
        └── about/                # About + attribution
```

## Tech Stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Navigation Compose** for routing
- **OkHttp** for API calls
- **kotlinx.serialization** for JSON
- **Coil 3** for image loading
- **WorkManager** for background notifications
- No database — JSON files in assets and internal storage

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

*TODO: Choose a license*
