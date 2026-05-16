import SwiftUI

struct StatusBadge: View {
    let status: SpeciesStatus

    private var label: String {
        switch status {
        case .peak: return "Peak"
        case .active: return "Active"
        case .early: return "Early"
        case .late: return "Late"
        case .inactive: return "Inactive"
        }
    }

    private var color: Color {
        switch status {
        case .peak: return .statusPeak
        case .active: return .statusActive
        case .early, .late: return .statusEarlyLate
        case .inactive: return .statusInactive
        }
    }

    var body: some View {
        Text(label)
            .font(.system(size: 11, weight: .semibold))
            .foregroundColor(.white)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.85))
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }
}

struct ActivityBadge: View {
    let percent: Int

    private var color: Color {
        switch percent {
        case 80...: return .statusPeak
        case 20...: return .statusActive
        case 1...: return .statusEarlyLate
        default: return .statusInactive
        }
    }

    var body: some View {
        Text("\(percent)%")
            .font(.system(size: 11, weight: .semibold))
            .foregroundColor(.white)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(color.opacity(0.85))
            .clipShape(RoundedRectangle(cornerRadius: 4))
    }
}

struct ActivityDot: View {
    let percent: Int

    private var color: Color {
        switch percent {
        case 80...: return .statusPeak
        case 20...: return .statusActive
        case 1...: return .statusEarlyLate
        default: return .statusInactive
        }
    }

    private var symbol: String {
        switch percent {
        case 50...: return "\u{25CF}"   // filled circle
        case 1...: return "\u{25D0}"    // half circle
        default: return "\u{25CB}"      // empty circle
        }
    }

    var body: some View {
        Text(symbol)
            .font(.system(size: 14))
            .foregroundColor(color)
    }
}

struct RarityDot: View {
    let rarity: String
    @Environment(\.appColors) private var colors

    private var color: Color {
        switch rarity {
        case "Common": return .rarityCommon
        case "Uncommon": return .rarityUncommon
        case "Rare": return .rarityRare
        default: return colors.onSurfaceVariant
        }
    }

    var body: some View {
        Text("\u{25CF}")
            .font(.system(size: 12))
            .foregroundColor(color)
    }
}

struct RarityChip: View {
    let rarity: String

    private var color: Color? {
        switch rarity {
        case "Common": return .rarityCommon
        case "Uncommon": return .rarityUncommon
        case "Rare": return .rarityRare
        default: return nil
        }
    }

    var body: some View {
        if let color {
            Text(rarity)
                .font(.system(size: 10, weight: .semibold))
                .foregroundColor(.white)
                .padding(.horizontal, 5)
                .padding(.vertical, 1)
                .background(color.opacity(0.75))
                .clipShape(RoundedRectangle(cornerRadius: 4))
        }
    }
}

struct ConservationBadge: View {
    let status: String?

    private static let iucnStatuses: Set<String> = ["LC", "NT", "VU", "EN", "CR", "EW", "EX"]

    private var color: Color? {
        guard let status = status?.uppercased(),
              Self.iucnStatuses.contains(status) else { return nil }
        switch status {
        case "LC": return .conservationLC
        case "NT": return .conservationNT
        case "VU": return .conservationVU
        case "EN": return .conservationEN
        case "CR", "EW", "EX": return .conservationCR
        default: return nil
        }
    }

    var body: some View {
        if let status = status?.uppercased(),
           Self.iucnStatuses.contains(status),
           let color {
            Text(status)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(.white)
                .padding(.horizontal, 5)
                .padding(.vertical, 1)
                .background(color.opacity(0.85))
                .clipShape(RoundedRectangle(cornerRadius: 4))
        }
    }
}
