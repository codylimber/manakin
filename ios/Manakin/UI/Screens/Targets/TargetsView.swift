import SwiftUI

enum TargetMode: String, CaseIterable {
    case starred = "STARRED"
    case notSeenHere = "NOT_SEEN_HERE"
    case notSeenAnywhere = "NOT_SEEN_ANYWHERE"
}

struct TargetSpecies: Identifiable {
    let species: Species
    let key: String
    let groupName: String
    let status: SpeciesStatus
    let currentAbundance: Float

    var id: Int { species.taxonId }
}

struct TargetsView: View {
    let repository: PhenologyRepository
    var lifeListService: LifeListService?
    let onSpeciesClick: (Int) -> Void
    var onTimeline: (() -> Void)?
    var onTripReport: (() -> Void)?
    var onCompare: (() -> Void)?
    var onHelp: (() -> Void)?
    var onAbout: (() -> Void)?

    @Environment(\.appColors) private var colors
    @Environment(AppSettings.self) private var appSettings

    @State private var sortMode: SortMode = AppSettings.shared.defaultSortMode

    private var currentWeek: Int {
        Calendar.current.component(.weekOfYear, from: Date())
    }

    private var mode: TargetMode {
        TargetMode(rawValue: appSettings.targetMode) ?? .starred
    }

    private var keys: [String] {
        repository.getKeys()
    }

    private var selectedKeys: Set<String> {
        let sk = appSettings.selectedDatasetKeys
        return sk.isEmpty ? Set(keys) : sk
    }

    private var hasLifeList: Bool {
        lifeListService?.hasUsername() == true
    }

    private var allSpecies: [TargetSpecies] {
        var list: [TargetSpecies] = []
        var seen: Set<Int> = []
        for key in keys {
            let groupName = repository.getGroupName(key: key)
            for sp in repository.getSpeciesForKey(key: key) {
                guard !seen.contains(sp.taxonId) else { continue }
                seen.insert(sp.taxonId)
                let status = SpeciesStatus.classify(species: sp, currentWeek: currentWeek)
                let abundance = sp.weekly.first(where: { $0.week == currentWeek })?.relAbundance ?? 0
                list.append(TargetSpecies(species: sp, key: key, groupName: groupName, status: status, currentAbundance: abundance))
            }
        }
        return list
    }

    private var observedGlobal: Set<Int> {
        guard let service = lifeListService, service.hasUsername() else { return [] }
        return Set(keys.flatMap { service.getObservedGlobal(datasetKey: $0) })
    }

    private var observedLocal: Set<Int> {
        guard let service = lifeListService, service.hasUsername() else { return [] }
        return Set(keys.flatMap { service.getObservedLocal(datasetKey: $0) })
    }

    private var displayed: [TargetSpecies] {
        var filtered: [TargetSpecies]
        switch mode {
        case .starred:
            filtered = allSpecies.filter { appSettings.isFavorite($0.species.taxonId) }
        case .notSeenHere:
            filtered = allSpecies.filter { !observedLocal.contains($0.species.taxonId) }
        case .notSeenAnywhere:
            filtered = allSpecies.filter { !observedGlobal.contains($0.species.taxonId) }
        }

        filtered = filtered.filter { selectedKeys.contains($0.key) }

        if appSettings.showActiveOnly {
            filtered = filtered.filter { $0.status != .inactive }
        }

        switch sortMode {
        case .likelihood:
            return filtered.sorted { a, b in
                let aOrder = statusOrder(a.status)
                let bOrder = statusOrder(b.status)
                if aOrder != bOrder { return aOrder < bOrder }
                let aScore = Double(a.currentAbundance) * (a.species.totalObs > 0 ? log(Double(a.species.totalObs)) : 0)
                let bScore = Double(b.currentAbundance) * (b.species.totalObs > 0 ? log(Double(b.species.totalObs)) : 0)
                return aScore > bScore
            }
        case .peakDate:
            return filtered.sorted { $0.species.peakWeek < $1.species.peakWeek }
        case .name:
            return filtered.sorted {
                let a = $0.species.commonName.isEmpty ? $0.species.scientificName : $0.species.commonName
                let b = $1.species.commonName.isEmpty ? $1.species.scientificName : $1.species.commonName
                return a.lowercased() < b.lowercased()
            }
        case .taxonomy:
            return filtered.sorted {
                let a = ($0.species.order ?? "", $0.species.family ?? "", $0.species.scientificName)
                let b = ($1.species.order ?? "", $1.species.family ?? "", $1.species.scientificName)
                return a < b
            }
        }
    }

    private func statusOrder(_ s: SpeciesStatus) -> Int {
        switch s {
        case .peak: return 0
        case .active: return 1
        case .early, .late: return 2
        case .inactive: return 3
        }
    }

    var body: some View {
        List {
            // Dataset selector
            if keys.count > 1 {
                Section {
                    DatasetSelector(
                        datasets: keys.map { DatasetItem(key: $0, group: repository.getGroupName(key: $0), placeName: repository.getPlaceNameForKey(key: $0)) },
                        selectedKeys: selectedKeys,
                        onSelectSingle: { appSettings.selectedDatasetKeys = Set([$0]) },
                        onToggle: { key in
                            var sk = selectedKeys
                            if sk.contains(key) && sk.count > 1 {
                                sk.remove(key)
                            } else {
                                sk.insert(key)
                            }
                            appSettings.selectedDatasetKeys = sk
                        },
                        onSelectAll: { appSettings.selectedDatasetKeys = Set(keys) }
                    )
                }
                .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))
                .listRowBackground(Color.clear)
            }

            // Mode chips
            Section {
                modeChips
            }
            .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))
            .listRowBackground(Color.clear)

            // Count + sort
            Section {
                HStack {
                    Text("\(displayed.count) species")
                        .font(.system(size: 12))
                        .foregroundColor(colors.onSurfaceVariant)
                    Spacer()
                    sortMenu
                }
            }
            .listRowInsets(EdgeInsets(top: 0, leading: 12, bottom: 0, trailing: 12))
            .listRowBackground(Color.clear)

            if displayed.isEmpty {
                Section {
                    emptyStateMessage
                }
                .listRowBackground(Color.clear)
            } else {
                // Group by dataset
                let grouped = Dictionary(grouping: displayed, by: { $0.groupName })
                ForEach(grouped.keys.sorted(), id: \.self) { group in
                    Section {
                        Text(group)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.appPrimary)
                            .listRowBackground(Color.clear)

                        ForEach(grouped[group] ?? [], id: \.species.taxonId) { target in
                            let photoURL = target.species.photos.first.flatMap {
                                repository.getPhotoURL(key: target.key, filename: $0.file)
                            }
                            SpeciesCardView(
                                species: target.species,
                                status: target.status,
                                currentWeek: currentWeek,
                                photoURL: photoURL
                            )
                            .onTapGesture { onSpeciesClick(target.species.taxonId) }
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                if mode == .starred {
                                    Button(role: .destructive) {
                                        appSettings.toggleFavorite(target.species.taxonId)
                                    } label: {
                                        Label("Remove", systemImage: "star.slash")
                                    }
                                }
                            }
                        }
                    }
                    .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))
                    .listRowBackground(Color.clear)
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
        .background(colors.background)
        .navigationTitle("")
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text("Targets")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.appPrimary)
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 4) {
                    ActiveAllToggle(
                        showAll: !appSettings.showActiveOnly,
                        onToggle: { showAll in appSettings.showActiveOnly = !showAll }
                    )
                    AppOverflowMenu()
                }
            }
        }
    }

    private var modeChips: some View {
        HStack(spacing: 6) {
            chipButton("Starred", isSelected: mode == .starred) {
                appSettings.targetMode = TargetMode.starred.rawValue
            }
            if hasLifeList {
                chipButton("New for Area", isSelected: mode == .notSeenHere) {
                    appSettings.targetMode = TargetMode.notSeenHere.rawValue
                }
                chipButton("Lifer Targets", isSelected: mode == .notSeenAnywhere) {
                    appSettings.targetMode = TargetMode.notSeenAnywhere.rawValue
                }
            }
        }
    }

    private func chipButton(_ label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(isSelected ? .appPrimary : colors.onSurfaceVariant)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(isSelected ? Color.appPrimary.opacity(0.2) : colors.surfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }

    private var emptyStateMessage: some View {
        let msg: String = {
            switch mode {
            case .starred:
                return "No starred species yet.\nSwipe right on a species card to star it."
            case .notSeenHere:
                if !hasLifeList {
                    return "Connect your iNaturalist account\nin Settings to use this feature."
                }
                return "You've seen every active species in this area!"
            case .notSeenAnywhere:
                if !hasLifeList {
                    return "Connect your iNaturalist account\nin Settings to use this feature."
                }
                return "You've observed every active species! Amazing."
            }
        }()
        return Text(msg)
            .font(.system(size: 14))
            .foregroundColor(colors.onSurfaceVariant)
            .multilineTextAlignment(.center)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 32)
    }

    private var activeAllToggle: some View {
        Button {
            appSettings.showActiveOnly.toggle()
        } label: {
            Text(appSettings.showActiveOnly ? "Active" : "All")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(.appPrimary)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.appPrimary.opacity(0.15))
                .clipShape(RoundedRectangle(cornerRadius: 6))
        }
    }

    private var overflowMenu: some View {
        Menu {
            if let onTimeline = onTimeline {
                Button("This Week") { onTimeline() }
            }
            if let onTripReport = onTripReport {
                Button("Trip Report") { onTripReport() }
            }
            if let onCompare = onCompare {
                Button("Compare") { onCompare() }
            }
            Divider()
            if let onHelp = onHelp {
                Button("Help") { onHelp() }
            }
            if let onAbout = onAbout {
                Button("About") { onAbout() }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .foregroundColor(.appPrimary)
        }
    }

    private var sortMenu: some View {
        Menu {
            Button { sortMode = .likelihood } label: {
                Label("Likelihood", systemImage: sortMode == .likelihood ? "checkmark" : "")
            }
            Button { sortMode = .peakDate } label: {
                Label("Peak Date", systemImage: sortMode == .peakDate ? "checkmark" : "")
            }
            Button { sortMode = .name } label: {
                Label("Name", systemImage: sortMode == .name ? "checkmark" : "")
            }
            Button { sortMode = .taxonomy } label: {
                Label("Taxonomy", systemImage: sortMode == .taxonomy ? "checkmark" : "")
            }
        } label: {
            HStack(spacing: 2) {
                Text(sortModeLabel)
                    .font(.system(size: 12))
                    .foregroundColor(.appPrimary)
                Image(systemName: "chevron.down")
                    .font(.system(size: 10))
                    .foregroundColor(.appPrimary)
            }
        }
    }

    private var sortModeLabel: String {
        switch sortMode {
        case .likelihood: return "Likelihood"
        case .peakDate: return "Peak"
        case .name: return "Name"
        case .taxonomy: return "Taxonomy"
        }
    }
}
