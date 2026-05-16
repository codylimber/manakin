# Manakin iOS

SwiftUI iOS port of the Manakin field phenology companion app.

## Setup

### Option 1: Create Xcode Project Manually

1. Open Xcode
2. File > New > Project > iOS > App
3. Product Name: **Manakin**
4. Team: your team
5. Organization Identifier: `com.codylimber`
6. Interface: **SwiftUI**
7. Language: **Swift**
8. Save to: `manakin/ios/` (replace the existing Manakin folder or merge)
9. Delete the auto-generated `ContentView.swift` and `ManakinApp.swift` (we have our own)
10. Drag all files from `Manakin/` into the Xcode project navigator
11. In project settings:
    - Set **iOS Deployment Target** to **17.0**
    - Under **Build Settings**, search "Swift Language" and set to **Swift 6** or **5.10**
12. Add the `Resources/datasets/` folder as a **folder reference** (blue folder icon)
13. Add the `Resources/Sounds/` files to the target
14. Add the `Resources/Assets.xcassets` to the target
15. Build and run on a simulator (iPhone 15 Pro recommended)

### Option 2: Use XcodeGen (Recommended)

1. Install XcodeGen: `brew install xcodegen`
2. Run from this directory: `xcodegen generate`
3. Open `Manakin.xcodeproj`
4. Build and run

## Architecture

- **SwiftUI** with iOS 17+ APIs (@Observable, NavigationStack, Canvas)
- **Swift concurrency** (async/await, actors) for networking
- **URLSession** for HTTP (no third-party deps)
- **Codable** for JSON serialization
- **UserDefaults** for settings persistence
- Same bundled CT butterflies dataset as Android
