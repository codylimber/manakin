import SwiftUI

struct SpeciesDetailView: View {
    let taxonId: Int
    let repository: PhenologyRepository
    var lifeListService: LifeListService?

    @Environment(\.appColors) private var colors
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSettings.self) private var appSettings

    private var species: Species? {
        repository.getSpeciesById(taxonId: taxonId)
    }

    private var key: String? {
        repository.getKeyForSpecies(taxonId: taxonId)
    }

    private var currentWeek: Int {
        Calendar.current.component(.weekOfYear, from: Date())
    }

    var body: some View {
        if let species = species, let key = key {
            speciesContent(species: species, key: key)
        } else {
            errorState
        }
    }

    private var errorState: some View {
        VStack {
            Spacer()
            Text("This species is no longer available.\nThe dataset may have been removed.")
                .font(.system(size: 16))
                .foregroundColor(colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
            Spacer()
        }
        .navigationTitle("Species Not Found")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .foregroundColor(.appPrimary)
                }
            }
        }
    }

    private func speciesContent(species: Species, key: String) -> some View {
        let status = SpeciesStatus.classify(species: species, currentWeek: currentWeek)
        let isObserved: Bool = {
            guard let service = lifeListService, service.hasUsername() else { return false }
            return service.getObservedForScope(datasetKey: key).contains(taxonId)
        }()

        return ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                // Photo carousel
                if !species.photos.isEmpty {
                    PhotoCarouselView(photos: species.photos, key: key, repository: repository)
                        .frame(height: 280)
                        .clipped()
                }

                VStack(alignment: .leading, spacing: 0) {
                    // Header
                    headerSection(species: species, isObserved: isObserved)

                    // Taxonomy
                    taxonomyLine(species: species)

                    // Badges + target button
                    badgesRow(species: species, status: status)

                    Spacer().frame(height: 20)

                    // Phenology chart
                    Text("Phenology")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(colors.onBackground)

                    Spacer().frame(height: 8)

                    PhenologyChart(
                        weekly: species.weekly,
                        currentWeek: currentWeek,
                        peakWeek: species.peakWeek
                    )

                    Spacer().frame(height: 20)

                    // Key facts
                    keyFactsCard(species: species)

                    // Description
                    if !species.description.isEmpty {
                        Spacer().frame(height: 20)
                        Text("About")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(colors.onBackground)
                        Spacer().frame(height: 8)
                        Text(species.description)
                            .font(.system(size: 14))
                            .foregroundColor(colors.onSurface)
                            .lineSpacing(4)
                    }

                    // Links
                    Spacer().frame(height: 20)
                    linkButtons(species: species, key: key)

                    Spacer().frame(height: 32)
                }
                .padding(16)
            }
        }
        .navigationBarTitleDisplayMode(.inline)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .foregroundColor(.appPrimary)
                }
            }
        }
    }

    private func headerSection(species: Species, isObserved: Bool) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            HStack(spacing: 6) {
                if isObserved {
                    Image(systemName: "checkmark")
                        .font(.system(size: 18))
                        .foregroundColor(.appPrimary)
                }
                let useSci = appSettings.useScientificNames
                let headerName = useSci ? species.scientificName
                    : (species.commonName.isEmpty ? species.scientificName : species.commonName)
                Text(headerName)
                    .font(.system(size: 24, weight: .bold))
                    .italic(useSci)
                    .foregroundColor(colors.onBackground)
            }

            // Sub name
            let useSci = appSettings.useScientificNames
            let subName: String? = {
                if useSci && !species.commonName.isEmpty {
                    return species.commonName
                } else if !useSci {
                    return species.scientificName
                }
                return nil
            }()
            if let subName = subName {
                Text(subName)
                    .font(.system(size: 16))
                    .italic(!useSci)
                    .foregroundColor(.appPrimary)
            }
        }
    }

    private func taxonomyLine(species: Species) -> some View {
        let useSci = appSettings.useScientificNames
        let parts: [String]
        if useSci {
            parts = [
                species.familyScientific ?? species.family,
                species.orderScientific ?? species.order
            ].compactMap { $0 }
        } else {
            parts = [
                species.family.flatMap { common in
                    species.familyScientific.map { "\(common) (\($0))" } ?? common
                },
                species.order.flatMap { common in
                    species.orderScientific.map { "\(common) (\($0))" } ?? common
                }
            ].compactMap { $0 }
        }
        let taxonomy = parts.joined(separator: " \u{2022} ")

        return Group {
            if !taxonomy.isEmpty {
                Text(taxonomy)
                    .font(.system(size: 14))
                    .foregroundColor(colors.onSurfaceVariant)
                    .padding(.top, 2)
            }
        }
    }

    private func badgesRow(species: Species, status: SpeciesStatus) -> some View {
        HStack {
            HStack(spacing: 8) {
                StatusBadge(status: status)
                ConservationBadge(status: species.conservationStatus)
            }
            Spacer()
            Button {
                appSettings.toggleFavorite(species.taxonId)
            } label: {
                let isFav = appSettings.isFavorite(species.taxonId)
                HStack(spacing: 4) {
                    Image(systemName: "star.fill")
                        .font(.system(size: 14))
                        .foregroundColor(isFav ? .favoriteGold : colors.onSurfaceVariant)
                    Text(isFav ? "Target" : "Add Target")
                        .font(.system(size: 13))
                        .foregroundColor(isFav ? .favoriteGold : colors.onSurfaceVariant)
                }
            }
        }
        .padding(.top, 8)
    }

    private func keyFactsCard(species: Species) -> some View {
        VStack(spacing: 8) {
            factRow(label: "Observations", value: formatNumber(species.totalObs))
            factRow(label: "Rarity", value: species.rarity, showRarityDot: true)
            factRow(label: "Active Season", value: "\(weekToDate(species.firstWeek)) \u{2013} \(weekToDate(species.lastWeek))")
            factRow(label: "Peak", value: weekToDate(species.peakWeek))
            if species.periodCount > 1 {
                factRow(label: "Active Periods", value: "\(species.periodCount)")
            }
        }
        .padding(16)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func factRow(label: String, value: String, showRarityDot: Bool = false) -> some View {
        HStack {
            Text(label)
                .font(.system(size: 14))
                .foregroundColor(colors.onSurfaceVariant)
            Spacer()
            HStack(spacing: 4) {
                if showRarityDot {
                    RarityDot(rarity: value)
                }
                Text(value)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(colors.onSurface)
            }
        }
    }

    private func linkButtons(species: Species, key: String) -> some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                Button {
                    let url = "https://www.inaturalist.org/taxa/\(species.taxonId)"
                    if let u = URL(string: url) { UIApplication.shared.open(u) }
                } label: {
                    Text("View on iNat")
                        .font(.system(size: 13))
                        .foregroundColor(.appPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.appPrimary.opacity(0.5), lineWidth: 1)
                        )
                }

                Button {
                    let dataset = repository.getDataset(key: key)
                    var mapUrl = "https://www.inaturalist.org/observations?taxon_id=\(species.taxonId)"
                    if let placeId = dataset?.metadata.placeId {
                        mapUrl += "&place_id=\(placeId)"
                    }
                    mapUrl += "&subview=map"
                    if let u = URL(string: mapUrl) { UIApplication.shared.open(u) }
                } label: {
                    Text("Observation Map")
                        .font(.system(size: 13))
                        .foregroundColor(.appPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.appPrimary.opacity(0.5), lineWidth: 1)
                        )
                }
            }

            // My observations link
            if let service = lifeListService, !service.username.trimmingCharacters(in: .whitespaces).isEmpty {
                Button {
                    let url = "https://www.inaturalist.org/observations?user_id=\(service.username)&taxon_id=\(species.taxonId)"
                    if let u = URL(string: url) { UIApplication.shared.open(u) }
                } label: {
                    Text("My Observations")
                        .font(.system(size: 13))
                        .foregroundColor(.appPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 8)
                                .stroke(Color.appPrimary.opacity(0.5), lineWidth: 1)
                        )
                }
            }
        }
    }

    // MARK: - Helpers

    private func weekToDate(_ week: Int) -> String {
        guard week >= 1, week <= 53 else { return "Week \(week)" }
        let year = Calendar.current.component(.year, from: Date())
        var components = DateComponents()
        components.yearForWeekOfYear = year
        components.weekOfYear = week
        components.weekday = 2 // Monday
        guard let date = Calendar.current.date(from: components) else { return "Week \(week)" }
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        return formatter.string(from: date)
    }

    private func formatNumber(_ n: Int) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        return formatter.string(from: NSNumber(value: n)) ?? "\(n)"
    }
}
