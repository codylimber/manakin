import SwiftUI

struct AppColorScheme {
    let primary: Color
    let onPrimary: Color
    let secondary: Color
    let background: Color
    let surface: Color
    let surfaceVariant: Color
    let onBackground: Color
    let onSurface: Color
    let onSurfaceVariant: Color
}

extension AppColorScheme {
    static let dark = AppColorScheme(
        primary: .primary,
        onPrimary: .darkBackground,
        secondary: .primaryLight,
        background: .darkBackground,
        surface: .darkSurface,
        surfaceVariant: .darkSurfaceVariant,
        onBackground: .darkOnBackground,
        onSurface: .darkOnSurface,
        onSurfaceVariant: .darkOnSurfaceDim
    )

    static let light = AppColorScheme(
        primary: .primaryDark,
        onPrimary: .lightBackground,
        secondary: .primary,
        background: .lightBackground,
        surface: .lightSurface,
        surfaceVariant: .lightSurfaceVariant,
        onBackground: .lightOnBackground,
        onSurface: .lightOnSurface,
        onSurfaceVariant: .lightOnSurfaceDim
    )
}

@Observable
final class ThemeManager {
    static let shared = ThemeManager()

    var isDarkMode: Bool = true

    var colorScheme: ColorScheme {
        isDarkMode ? .dark : .light
    }

    var colors: AppColorScheme {
        isDarkMode ? .dark : .light
    }

    private init() {}
}

// Environment key for AppColorScheme
private struct AppColorSchemeKey: EnvironmentKey {
    static let defaultValue: AppColorScheme = .dark
}

extension EnvironmentValues {
    var appColors: AppColorScheme {
        get { self[AppColorSchemeKey.self] }
        set { self[AppColorSchemeKey.self] = newValue }
    }
}
