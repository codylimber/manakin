import SwiftUI

enum TimelineEventType: Int, CaseIterable {
    case lastChance = 0
    case enteredPeak = 1
    case rareAndActive = 2
    case newlyActive = 3
    case comingSoon = 4
    case approachingPeak = 5
    case peakThisWeek = 6
    case leftPeak = 7
    case becameInactive = 8
}

struct TimelineEvent: Identifiable {
    let species: Species
    let key: String
    let type: TimelineEventType

    var id: String { "\(species.taxonId)_\(type.rawValue)" }
}

struct TimelineView: View {
    let repository: PhenologyRepository
    let onSpeciesClick: (Int) -> Void

    @Environment(\.appColors) private var colors
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSettings.self) private var appSettings

    private var currentWeek: Int {
        Calendar.current.component(.weekOfYear, from: Date())
    }

    private var lastWeek: Int {
        currentWeek > 1 ? currentWeek - 1 : 53
    }

    private var nextWeek: Int {
        currentWeek < 53 ? currentWeek + 1 : 1
    }

    private var selectedKeys: Set<String> {
        let sk = appSettings.selectedDatasetKeys
        return sk.isEmpty ? Set(repository.getKeys()) : sk
    }

    private var events: [TimelineEvent] {
        var list: [TimelineEvent] = []
        var enteredPeakIds: Set<Int> = []
        var seenByType: [TimelineEventType: Set<Int>] = [:]

        func addEvent(_ sp: Species, _ key: String, _ type: TimelineEventType) {
            var seen = seenByType[type] ?? []
            if seen.insert(sp.taxonId).inserted {
                seenByType[type] = seen
                list.append(TimelineEvent(species: sp, key: key, type: type))
            }
        }

        for key in selectedKeys {
            for sp in repository.getSpeciesForKey(key: key) {
                let thisAbundance = sp.weekly.first(where: { $0.week == currentWeek })?.relAbundance ?? 0
                let lastAbundance = sp.weekly.first(where: { $0.week == lastWeek })?.relAbundance ?? 0
                let nextAbundance = sp.weekly.first(where: { $0.week == nextWeek })?.relAbundance ?? 0

                // Transition events
                if thisAbundance >= 0.8 && lastAbundance < 0.8 {
                    addEvent(sp, key, .enteredPeak)
                    enteredPeakIds.insert(sp.taxonId)
                } else if thisAbundance < 0.8 && lastAbundance >= 0.8 {
                    addEvent(sp, key, .leftPeak)
                } else if thisAbundance > 0 && lastAbundance == 0 {
                    addEvent(sp, key, .newlyActive)
                } else if thisAbundance == 0 && lastAbundance > 0 {
                    addEvent(sp, key, .becameInactive)
                } else if nextAbundance >= 0.8 && thisAbundance < 0.8 && thisAbundance > 0 {
                    addEvent(sp, key, .approachingPeak)
                }

                // Forward-looking
                if thisAbundance > 0 && nextAbundance == 0 {
                    addEvent(sp, key, .lastChance)
                }
                if thisAbundance == 0 && nextAbundance > 0 {
                    addEvent(sp, key, .comingSoon)
                }

                // Highlights
                if sp.rarity == "Rare" && thisAbundance > 0 {
                    addEvent(sp, key, .rareAndActive)
                }
                if thisAbundance >= 0.8 && !enteredPeakIds.contains(sp.taxonId) {
                    addEvent(sp, key, .peakThisWeek)
                }
            }
        }

        return list.sorted { $0.type.rawValue < $1.type.rawValue }
    }

    var body: some View {
        Group {
            if events.isEmpty {
                VStack {
                    Spacer()
                    Text("No changes this week")
                        .font(.system(size: 16))
                        .foregroundColor(colors.onSurfaceVariant)
                    Spacer()
                }
            } else {
                List {
                    let grouped = Dictionary(grouping: events, by: { $0.type })

                    eventSection(type: .lastChance, title: "\u{23F3} Last Chance", events: grouped)
                    eventSection(type: .enteredPeak, title: "\u{1F525} Entering Peak", events: grouped)
                    eventSection(type: .rareAndActive, title: "\u{1F48E} Rare + Active", events: grouped)
                    eventSection(type: .newlyActive, title: "\u{1F331} Newly Active", events: grouped)
                    eventSection(type: .comingSoon, title: "\u{1F440} Coming Soon", events: grouped)
                    eventSection(type: .approachingPeak, title: "\u{2B06}\u{FE0F} Approaching Peak Next Week", events: grouped)
                    eventSection(type: .peakThisWeek, title: "\u{2B50} At Peak", events: grouped)
                    eventSection(type: .leftPeak, title: "\u{2B07}\u{FE0F} Left Peak", events: grouped)
                    eventSection(type: .becameInactive, title: "\u{1F4A4} Became Inactive", events: grouped)
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }
        }
        .background(colors.background)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .foregroundColor(.primary)
                }
            }
            ToolbarItem(placement: .principal) {
                VStack(spacing: 0) {
                    Text("This Week")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(.primary)
                    let label = selectedKeys.count == repository.getKeys().count
                        ? "All Datasets"
                        : selectedKeys.map { repository.getGroupName(key: $0) }.joined(separator: ", ")
                    Text(label)
                        .font(.system(size: 12))
                        .foregroundColor(colors.onSurfaceVariant)
                }
            }
        }
    }

    @ViewBuilder
    private func eventSection(type: TimelineEventType, title: String, events: [TimelineEventType: [TimelineEvent]]) -> some View {
        if let items = events[type], !items.isEmpty {
            Section {
                ForEach(items) { event in
                    eventCard(event: event)
                        .onTapGesture { onSpeciesClick(event.species.taxonId) }
                }
            } header: {
                Text(title)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(colors.onBackground)
                    .textCase(nil)
            }
            .listRowInsets(EdgeInsets(top: 3, leading: 12, bottom: 3, trailing: 12))
            .listRowBackground(Color.clear)
        }
    }

    private func eventCard(event: TimelineEvent) -> some View {
        let sp = event.species
        let isFav = appSettings.isFavorite(sp.taxonId)
        let photoURL = sp.photos.first.flatMap { repository.getPhotoURL(key: event.key, filename: $0.file) }

        return HStack(spacing: 10) {
            if let url = photoURL {
                AsyncImage(url: url) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().aspectRatio(contentMode: .fill)
                    default:
                        Color.gray.opacity(0.3)
                    }
                }
                .frame(width: 44, height: 44)
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }

            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 3) {
                    if isFav {
                        Image(systemName: "star.fill")
                            .font(.system(size: 12))
                            .foregroundColor(.favoriteGold)
                    }
                    let useSci = appSettings.useScientificNames
                    let primaryName = useSci ? sp.scientificName : (sp.commonName.isEmpty ? sp.scientificName : sp.commonName)
                    Text(primaryName)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundColor(colors.onSurface)
                        .lineLimit(1)
                }
                if !sp.commonName.isEmpty {
                    let useSci = appSettings.useScientificNames
                    let secondaryName = useSci ? sp.commonName : sp.scientificName
                    Text(secondaryName)
                        .font(.system(size: 12))
                        .italic(!useSci)
                        .foregroundColor(colors.onSurfaceVariant)
                        .lineLimit(1)
                }
            }

            Spacer()

            let (label, color) = eventLabelAndColor(event.type)
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(color)
        }
        .padding(12)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }

    private func eventLabelAndColor(_ type: TimelineEventType) -> (String, Color) {
        switch type {
        case .enteredPeak: return ("Peak", .statusPeak)
        case .newlyActive: return ("Active", .statusActive)
        case .approachingPeak: return ("Rising", .primary)
        case .leftPeak: return ("Declining", .statusEarlyLate)
        case .becameInactive: return ("Inactive", .statusInactive)
        case .lastChance: return ("Last Chance", .rarityRare)
        case .comingSoon: return ("Soon", .statusActive)
        case .rareAndActive: return ("Rare", .rarityRare)
        case .peakThisWeek: return ("Peak", .statusPeak)
        }
    }
}
