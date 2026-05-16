import SwiftUI

extension Color {
    // Core palette - natural green scheme
    static let appPrimary = Color(hex: 0x4CAF50)
    static let primaryLight = Color(hex: 0x66BB6A)
    static let primaryDark = Color(hex: 0x388E3C)
    static let accent = Color(hex: 0x81C784)

    // Dark theme
    static let darkBackground = Color(hex: 0x1A1A1A)
    static let darkSurface = Color(hex: 0x222222)
    static let darkSurfaceVariant = Color(hex: 0x2A2A2A)
    static let darkOnBackground = Color(hex: 0xE0E0E0)
    static let darkOnSurface = Color(hex: 0xCCCCCC)
    static let darkOnSurfaceDim = Color(hex: 0x888888)

    // Light theme
    static let lightBackground = Color(hex: 0xF0F0F0)
    static let lightSurface = Color(hex: 0xFFFFFF)
    static let lightSurfaceVariant = Color(hex: 0xE0E0E0)
    static let lightOnBackground = Color(hex: 0x1A1A1A)
    static let lightOnSurface = Color(hex: 0x333333)
    static let lightOnSurfaceDim = Color(hex: 0x777777)

    // Accent colors
    static let favoriteGold = Color(hex: 0xFFD700)
    static let observedBlue = Color(hex: 0x4A90D9)

    // Status colors
    static let statusPeak = Color(hex: 0xFFB300)
    static let statusActive = Color(hex: 0x4CAF50)
    static let statusEarlyLate = Color(hex: 0x5B8FB9)
    static let statusInactive = Color(hex: 0x555555)

    // Rarity colors
    static let rarityCommon = Color(hex: 0x4CAF50)
    static let rarityUncommon = Color(hex: 0xF39C12)
    static let rarityRare = Color(hex: 0xE74C3C)

    // Conservation status colors
    static let conservationLC = Color(hex: 0x4CAF50)
    static let conservationNT = Color(hex: 0xF39C12)
    static let conservationVU = Color(hex: 0xE67E22)
    static let conservationEN = Color(hex: 0xE74C3C)
    static let conservationCR = Color(hex: 0xC0392B)

    init(hex: UInt, alpha: Double = 1.0) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255.0,
            green: Double((hex >> 8) & 0xFF) / 255.0,
            blue: Double(hex & 0xFF) / 255.0,
            opacity: alpha
        )
    }
}
