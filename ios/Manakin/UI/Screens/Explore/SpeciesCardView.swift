import SwiftUI

struct SpeciesCardView: View {
    let species: Species
    let status: SpeciesStatus
    let currentWeek: Int
    var photoURL: URL?
    var isObserved: Bool = false
    var showObservedIndicator: Bool = false

    @Environment(\.appColors) private var colors
    @Environment(AppSettings.self) private var appSettings

    @State private var swipeOffset: CGFloat = 0
    private let swipeThreshold: CGFloat = 120

    private var useSci: Bool { appSettings.useScientificNames }

    private var primaryName: String {
        if useSci {
            return species.scientificName
        }
        return species.commonName.isEmpty ? species.scientificName : species.commonName
    }

    private var secondaryName: String? {
        if useSci && !species.commonName.isEmpty {
            return species.commonName
        } else if !useSci && !species.commonName.isEmpty {
            return species.scientificName
        }
        return nil
    }

    private var activityPercent: Int {
        let entry = species.weekly.first(where: { $0.week == currentWeek })
        return Int((entry?.relAbundance ?? 0) * 100)
    }

    private var isFavorite: Bool {
        appSettings.isFavorite(species.taxonId)
    }

    private var pastThreshold: Bool {
        swipeOffset > swipeThreshold
    }

    var body: some View {
        ZStack(alignment: .leading) {
            // Swipe background
            if swipeOffset > 10 {
                swipeBackground
            }

            // Card content
            cardContent
                .offset(x: swipeOffset)
                .gesture(
                    DragGesture()
                        .onChanged { value in
                            let newOffset = value.translation.width
                            swipeOffset = max(0, min(newOffset, swipeThreshold * 1.5))
                        }
                        .onEnded { _ in
                            if pastThreshold {
                                appSettings.toggleFavorite(species.taxonId)
                            }
                            withAnimation(.spring(response: 0.3)) {
                                swipeOffset = 0
                            }
                        }
                )
        }
    }

    private var swipeBackground: some View {
        HStack(spacing: 4) {
            Image(systemName: "star.fill")
                .font(.system(size: 20))
                .foregroundColor(swipeIconColor)
            Text(swipeText)
                .font(.system(size: 13))
                .fontWeight(pastThreshold ? .bold : .regular)
                .foregroundColor(swipeIconColor)
            Spacer()
        }
        .padding(.leading, 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(swipeBackgroundColor)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var swipeBackgroundColor: Color {
        if pastThreshold && !isFavorite {
            return Color.favoriteGold.opacity(0.3)
        } else if pastThreshold && isFavorite {
            return Color.red.opacity(0.2)
        } else if swipeOffset > 10 && !isFavorite {
            return Color.primary.opacity(0.1)
        } else if swipeOffset > 10 && isFavorite {
            return Color.red.opacity(0.1)
        }
        return Color.clear
    }

    private var swipeIconColor: Color {
        if pastThreshold {
            return isFavorite ? .red : .favoriteGold
        }
        return colors.onSurfaceVariant
    }

    private var swipeText: String {
        if pastThreshold {
            return isFavorite ? "Release to Remove" : "Release to Add"
        }
        return isFavorite ? "Swipe to Remove" : "Swipe to Add"
    }

    private var cardContent: some View {
        HStack(spacing: 12) {
            // Thumbnail
            thumbnailView

            // Info column
            VStack(alignment: .leading, spacing: 6) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 2) {
                        // Primary name row
                        HStack(spacing: 3) {
                            if isFavorite {
                                Image(systemName: "star.fill")
                                    .font(.system(size: 14))
                                    .foregroundColor(.favoriteGold)
                            }
                            Text(primaryName)
                                .font(.system(size: 15, weight: .semibold))
                                .italic(useSci)
                                .foregroundColor(colors.onSurface)
                                .lineLimit(1)
                            if showObservedIndicator && isObserved {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 14))
                                    .foregroundColor(.observedBlue)
                            }
                            RarityDot(rarity: species.rarity)
                        }

                        // Secondary name
                        if let secondary = secondaryName {
                            Text(secondary)
                                .font(.system(size: 12))
                                .italic(!useSci)
                                .foregroundColor(colors.onSurfaceVariant)
                                .lineLimit(1)
                        }
                    }

                    Spacer()

                    // Status badge and activity
                    VStack(alignment: .trailing, spacing: 2) {
                        StatusBadge(status: status)
                        if activityPercent > 0 {
                            Text("\(activityPercent)%")
                                .font(.system(size: 10))
                                .foregroundColor(colors.onSurfaceVariant)
                        }
                    }
                }

                // Mini bar chart
                MiniBarChart(
                    weekly: species.weekly,
                    currentWeek: currentWeek
                )
            }
        }
        .padding(12)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private var thumbnailView: some View {
        Group {
            if let url = photoURL {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                    default:
                        Color.gray.opacity(0.3)
                    }
                }
            } else {
                Color.gray.opacity(0.2)
            }
        }
        .frame(width: 56, height: 56)
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }
}
