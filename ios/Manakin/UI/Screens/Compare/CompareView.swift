import SwiftUI

enum CompareViewMode: String, CaseIterable {
    case all, onlyA, shared, onlyB
}

struct CompareView: View {
    let repository: PhenologyRepository
    var lifeListService: LifeListService?
    let onSpeciesClick: (Int) -> Void

    @Environment(\.appColors) private var colors
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSettings.self) private var appSettings

    @State private var keyA: String = ""
    @State private var keyB: String = ""
    @State private var viewMode: CompareViewMode = .all
    @State private var sortMode: SortMode = AppSettings.shared.defaultSortMode
    @State private var filterMode: String = "ALL"

    private var currentWeek: Int {
        Calendar.current.component(.weekOfYear, from: Date())
    }

    private var keys: [String] {
        repository.getKeys()
    }

    private var hasLifeList: Bool {
        lifeListService?.hasUsername() == true
    }

    private var compatibleKeysForB: [String] {
        keys.filter { repository.getTaxonGroup(key: $0) == repository.getTaxonGroup(key: keyA) && $0 != keyA }
    }

    private var observedIds: Set<Int> {
        guard let service = lifeListService, hasLifeList else { return [] }
        return Set(keys.flatMap { service.getObservedForScope(datasetKey: $0) })
    }

    private var speciesA: [Int: Species] {
        Dictionary(uniqueKeysWithValues: repository.getSpeciesForKey(key: keyA).map { ($0.taxonId, $0) })
    }

    private var speciesB: [Int: Species] {
        Dictionary(uniqueKeysWithValues: repository.getSpeciesForKey(key: keyB).map { ($0.taxonId, $0) })
    }

    private var onlyA: [Int: Species] {
        speciesA.filter { !speciesB.keys.contains($0.key) }
    }

    private var onlyB: [Int: Species] {
        speciesB.filter { !speciesA.keys.contains($0.key) }
    }

    private var shared: [Int: Species] {
        speciesA.filter { speciesB.keys.contains($0.key) }
    }

    var body: some View {
        Group {
            if keys.count < 2 {
                insufficientDatasetsView
            } else {
                compareContent
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
                Text("Compare")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.primary)
            }
        }
        .onAppear {
            if keyA.isEmpty { keyA = keys.first ?? "" }
            if keyB.isEmpty { keyB = compatibleKeysForB.first ?? "" }
        }
        .onChange(of: keyA) { _, _ in
            if !compatibleKeysForB.contains(keyB) {
                keyB = compatibleKeysForB.first ?? ""
            }
        }
    }

    private var insufficientDatasetsView: some View {
        VStack {
            Spacer()
            Text("You need at least 2 datasets to compare.\nDownload more from the Datasets tab.")
                .font(.system(size: 16))
                .foregroundColor(colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
            Spacer()
        }
    }

    private var compareContent: some View {
        List {
            // Dataset selectors
            Section {
                datasetSelectors
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))

            // Summary chips
            Section {
                summaryChips
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))

            // Filter + sort
            Section {
                filterAndSort
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 0, leading: 12, bottom: 0, trailing: 12))

            // Species sections
            if viewMode == .all || viewMode == .onlyA {
                let filteredA = filterSpecies(Array(onlyA.values))
                if !filteredA.isEmpty {
                    speciesSection(
                        title: "Only in \(repository.getGroupName(key: keyA)) \u{2014} \(repository.getPlaceNameForKey(key: keyA)) (\(filteredA.count))",
                        species: filteredA,
                        key: keyA,
                        titleColor: .primary
                    )
                }
            }

            if viewMode == .all || viewMode == .shared {
                let filteredShared = filterSpecies(Array(shared.values))
                if !filteredShared.isEmpty {
                    speciesSection(
                        title: "In both (\(filteredShared.count))",
                        species: filteredShared,
                        key: keyA,
                        titleColor: colors.onSurfaceVariant
                    )
                }
            }

            if viewMode == .all || viewMode == .onlyB {
                let filteredB = filterSpecies(Array(onlyB.values))
                if !filteredB.isEmpty {
                    speciesSection(
                        title: "Only in \(repository.getGroupName(key: keyB)) \u{2014} \(repository.getPlaceNameForKey(key: keyB)) (\(filteredB.count))",
                        species: filteredB,
                        key: keyB,
                        titleColor: .primary
                    )
                }
            }
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
    }

    private var datasetSelectors: some View {
        HStack(spacing: 8) {
            Menu {
                ForEach(keys, id: \.self) { key in
                    Button {
                        keyA = key
                    } label: {
                        VStack(alignment: .leading) {
                            Text(repository.getGroupName(key: key))
                            Text(repository.getPlaceNameForKey(key: key))
                                .font(.caption)
                        }
                    }
                }
            } label: {
                Text(repository.getPlaceNameForKey(key: keyA))
                    .font(.system(size: 13))
                    .lineLimit(1)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.primary.opacity(0.3)))
            }
            .frame(maxWidth: .infinity)

            Text("vs")
                .foregroundColor(colors.onSurfaceVariant)

            Menu {
                ForEach(compatibleKeysForB, id: \.self) { key in
                    Button {
                        keyB = key
                    } label: {
                        VStack(alignment: .leading) {
                            Text(repository.getGroupName(key: key))
                            Text(repository.getPlaceNameForKey(key: key))
                                .font(.caption)
                        }
                    }
                }
            } label: {
                Text(keyB.isEmpty ? "\u{2014}" : repository.getPlaceNameForKey(key: keyB))
                    .font(.system(size: 13))
                    .lineLimit(1)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.primary.opacity(0.3)))
            }
            .frame(maxWidth: .infinity)
            .disabled(compatibleKeysForB.isEmpty)
        }
    }

    private var summaryChips: some View {
        HStack(spacing: 0) {
            Spacer()
            summaryChip(
                label: "Only \(repository.getPlaceNameForKey(key: keyA))",
                count: onlyA.count,
                color: .primary,
                selected: viewMode == .onlyA
            ) {
                viewMode = viewMode == .onlyA ? .all : .onlyA
            }
            Spacer()
            summaryChip(
                label: "Shared",
                count: shared.count,
                color: colors.onSurfaceVariant,
                selected: viewMode == .shared
            ) {
                viewMode = viewMode == .shared ? .all : .shared
            }
            Spacer()
            summaryChip(
                label: "Only \(repository.getPlaceNameForKey(key: keyB))",
                count: onlyB.count,
                color: .primary,
                selected: viewMode == .onlyB
            ) {
                viewMode = viewMode == .onlyB ? .all : .onlyB
            }
            Spacer()
        }
        .padding(.vertical, 8)
    }

    private func summaryChip(label: String, count: Int, color: Color, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            VStack(spacing: 2) {
                Text("\(count)")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundColor(color)
                Text(label)
                    .font(.system(size: 11))
                    .foregroundColor(colors.onSurfaceVariant)
                    .lineLimit(1)
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(selected ? Color.primary.opacity(0.15) : Color.clear)
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }

    private var filterAndSort: some View {
        HStack {
            HStack(spacing: 6) {
                filterChip("All", value: "ALL")
                if hasLifeList {
                    filterChip("Unseen", value: "UNSEEN")
                }
                filterChip("Starred", value: "STARRED")
            }
            Spacer()
            sortMenu
        }
    }

    private func filterChip(_ label: String, value: String) -> some View {
        Button {
            filterMode = value
        } label: {
            Text(label)
                .font(.system(size: 12))
                .foregroundColor(filterMode == value ? .primary : colors.onSurfaceVariant)
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(filterMode == value ? Color.primary.opacity(0.2) : colors.surfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 6))
        }
        .buttonStyle(.plain)
    }

    private var sortMenu: some View {
        Menu {
            Button { sortMode = .likelihood } label: { Label("Likelihood", systemImage: sortMode == .likelihood ? "checkmark" : "") }
            Button { sortMode = .peakDate } label: { Label("Peak Date", systemImage: sortMode == .peakDate ? "checkmark" : "") }
            Button { sortMode = .name } label: { Label("Name", systemImage: sortMode == .name ? "checkmark" : "") }
            Button { sortMode = .taxonomy } label: { Label("Taxonomy", systemImage: sortMode == .taxonomy ? "checkmark" : "") }
        } label: {
            HStack(spacing: 2) {
                Image(systemName: "arrow.up.arrow.down")
                    .font(.system(size: 10))
                    .foregroundColor(.primary)
            }
        }
    }

    private func filterSpecies(_ species: [Species]) -> [Species] {
        var list = species
        if filterMode == "UNSEEN" && hasLifeList {
            list = list.filter { !observedIds.contains($0.taxonId) }
        }
        if filterMode == "STARRED" {
            list = list.filter { appSettings.isFavorite($0.taxonId) }
        }
        return sortSpecies(list)
    }

    private func sortSpecies(_ list: [Species]) -> [Species] {
        switch sortMode {
        case .name:
            return list.sorted { ($0.commonName.isEmpty ? $0.scientificName : $0.commonName).lowercased() < ($1.commonName.isEmpty ? $1.scientificName : $1.commonName).lowercased() }
        case .peakDate:
            return list.sorted { $0.peakWeek < $1.peakWeek }
        case .taxonomy:
            return list.sorted {
                let a = ($0.order ?? "", $0.family ?? "", $0.scientificName)
                let b = ($1.order ?? "", $1.family ?? "", $1.scientificName)
                return a < b
            }
        case .likelihood:
            return list.sorted { a, b in
                let wA = a.weekly.first(where: { $0.week == currentWeek })?.relAbundance ?? 0
                let wB = b.weekly.first(where: { $0.week == currentWeek })?.relAbundance ?? 0
                let scoreA = Double(wA) * (a.totalObs > 0 ? log(Double(a.totalObs)) : 0)
                let scoreB = Double(wB) * (b.totalObs > 0 ? log(Double(b.totalObs)) : 0)
                return scoreA > scoreB
            }
        }
    }

    @ViewBuilder
    private func speciesSection(title: String, species: [Species], key: String, titleColor: Color) -> some View {
        Section {
            ForEach(species, id: \.taxonId) { sp in
                let photoURL = sp.photos.first.flatMap { repository.getPhotoURL(key: key, filename: $0.file) }
                SpeciesCardView(
                    species: sp,
                    status: SpeciesStatus.classify(species: sp, currentWeek: currentWeek),
                    currentWeek: currentWeek,
                    photoURL: photoURL,
                    isObserved: observedIds.contains(sp.taxonId),
                    showObservedIndicator: hasLifeList
                )
                .onTapGesture { onSpeciesClick(sp.taxonId) }
            }
        } header: {
            Text(title)
                .font(.system(size: 15, weight: .semibold))
                .foregroundColor(titleColor)
                .textCase(nil)
        }
        .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))
        .listRowBackground(Color.clear)
    }
}
