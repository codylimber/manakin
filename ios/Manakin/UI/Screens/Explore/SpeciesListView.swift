import SwiftUI

// MARK: - View Model

@Observable
final class SpeciesListViewModel {
    struct DatasetOption: Identifiable {
        let key: String
        let group: String
        let placeName: String
        var id: String { key }
    }

    struct OrganismOfTheDay {
        let species: Species
        let key: String
        let status: SpeciesStatus
        let photoURL: URL?
    }

    struct SpeciesWithStatus: Identifiable {
        let species: Species
        let status: SpeciesStatus
        let currentAbundance: Float
        let isObserved: Bool
        let sourceKey: String
        var id: Int { species.taxonId }
    }

    var repository: PhenologyRepository?
    var datasets: [DatasetOption] = []
    var selectedKeys: Set<String> = []
    var sortMode: SortMode = AppSettings.shared.defaultSortMode
    var showAllSpecies: Bool = !AppSettings.shared.showActiveOnly
    var searchQuery: String = "" {
        didSet { debounceSearch() }
    }
    var speciesList: [SpeciesWithStatus] = []
    var currentWeek: Int = 1
    var selectedDate: Date = Date()
    var endDate: Date?
    var isCustomDate: Bool = false
    var isRangeMode: Bool = false
    var activeCount: Int = 0
    var totalCount: Int = 0
    var observedCount: Int = 0
    var hasLifeList: Bool = false
    var observedIds: Set<Int> = []
    var taxonomyHeaders: [Int: String] = [:]
    var observationFilter: ObservationFilter = .all

    private var searchDebounceTask: Task<Void, Never>?

    init() {
        let calendar = Calendar.current
        currentWeek = calendar.component(.weekOfYear, from: Date())
        loadDatasets()
    }

    func loadDatasets() {
        guard let repo = repository else { return }
        datasets = repo.getKeys().map { key in
            DatasetOption(key: key, group: repo.getGroupName(key: key), placeName: repo.getPlaceNameForKey(key: key))
        }
        let validKeys = selectedKeys.filter { k in datasets.contains(where: { $0.key == k }) }
        if validKeys.isEmpty {
            selectedKeys = Set(datasets.prefix(1).map(\.key))
        } else {
            selectedKeys = Set(validKeys)
        }
        AppSettings.shared.selectedDatasetKeys = selectedKeys
    }

    func refresh() {
        loadDatasets()
        updateSpeciesList()
    }

    func toggleDataset(_ key: String) {
        if selectedKeys.contains(key) {
            if selectedKeys.count > 1 {
                selectedKeys.remove(key)
            }
        } else {
            selectedKeys.insert(key)
        }
        AppSettings.shared.selectedDatasetKeys = selectedKeys
        updateSpeciesList()
    }

    func selectAllDatasets() {
        selectedKeys = Set(datasets.map(\.key))
        AppSettings.shared.selectedDatasetKeys = selectedKeys
        updateSpeciesList()
    }

    func selectSingleDataset(_ key: String) {
        selectedKeys = [key]
        AppSettings.shared.selectedDatasetKeys = selectedKeys
        updateSpeciesList()
    }

    func setSortMode(_ mode: SortMode) {
        sortMode = mode
        updateSpeciesList()
    }

    func setShowAllSpecies(_ showAll: Bool) {
        showAllSpecies = showAll
        AppSettings.shared.showActiveOnly = !showAll
        updateSpeciesList()
    }

    func setDate(_ date: Date) {
        selectedDate = date
        let calendar = Calendar.current
        currentWeek = calendar.component(.weekOfYear, from: date)
        endDate = nil
        isCustomDate = !calendar.isDateInToday(date)
        isRangeMode = false
        updateSpeciesList()
    }

    func setDateRange(start: Date, end: Date) {
        selectedDate = start
        endDate = end
        let calendar = Calendar.current
        currentWeek = calendar.component(.weekOfYear, from: start)
        isCustomDate = true
        isRangeMode = true
        updateSpeciesList()
    }

    func resetToToday() {
        setDate(Date())
    }

    func setObservationFilter(_ filter: ObservationFilter) {
        observationFilter = filter
        updateSpeciesList()
    }

    func getOrganismOfTheDay() -> OrganismOfTheDay? {
        // Picks a random active species based on day of year
        let candidates = speciesList.filter { $0.currentAbundance >= 0.25 }
        guard !candidates.isEmpty else { return nil }
        let dayOfYear = Calendar.current.ordinality(of: .day, in: .year, for: Date()) ?? 1
        let pick = candidates[dayOfYear % candidates.count]
        return OrganismOfTheDay(
            species: pick.species,
            key: pick.sourceKey,
            status: pick.status,
            photoURL: nil
        )
    }

    private func debounceSearch() {
        searchDebounceTask?.cancel()
        searchDebounceTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 250_000_000)
            guard !Task.isCancelled else { return }
            updateSpeciesList()
        }
    }

    func updateSpeciesList() {
        // Core filtering and sorting logic
        // This will be connected to PhenologyRepository
        let week = currentWeek
        let endWeek: Int? = if isRangeMode, let end = endDate {
            Calendar.current.component(.weekOfYear, from: end)
        } else {
            nil
        }

        let weekRange: Set<Int>
        if isRangeMode, let ew = endWeek {
            if week <= ew {
                weekRange = Set(week...ew)
            } else {
                weekRange = Set(week...53).union(Set(1...ew))
            }
        } else {
            weekRange = [week]
        }

        // Build species list from repository
        var seenIds = Set<Int>()
        var allWithStatus: [SpeciesWithStatus] = []
        let keysToSearch = searchQuery.isEmpty ? Array(selectedKeys) : repository?.getKeys() ?? []
        for key in keysToSearch {
            for sp in repository?.getSpeciesForKey(key: key) ?? [] {
                guard seenIds.insert(sp.taxonId).inserted else { continue }
                let abundance: Float
                if isRangeMode {
                    abundance = sp.weekly.filter { weekRange.contains($0.week) }.map(\.relAbundance).max() ?? 0
                } else {
                    abundance = sp.weekly.first(where: { $0.week == week })?.relAbundance ?? 0
                }
                let status: SpeciesStatus
                if isRangeMode {
                    if abundance >= 0.8 { status = .peak }
                    else if abundance >= 0.2 { status = .active }
                    else if abundance > 0 { status = .early }
                    else { status = .inactive }
                } else {
                    status = SpeciesStatus.classify(species: sp, currentWeek: week)
                }
                let isObserved = observedIds.contains(sp.taxonId)
                allWithStatus.append(SpeciesWithStatus(species: sp, status: status, currentAbundance: abundance, isObserved: isObserved, sourceKey: key))
            }
        }

        // Filter active/all
        var filtered: [SpeciesWithStatus]
        if showAllSpecies {
            filtered = allWithStatus
        } else {
            filtered = allWithStatus.filter { $0.status != .inactive }

            // Min activity threshold
            let minThreshold = Float(AppSettings.shared.minActivityPercent) / 100.0
            if minThreshold > 0 {
                filtered = filtered.filter { $0.currentAbundance >= minThreshold || $0.status == .inactive }
            }
        }

        // Observation filter
        switch observationFilter {
        case .all: break
        case .observed: filtered = filtered.filter { $0.isObserved }
        case .notObserved: filtered = filtered.filter { !$0.isObserved }
        case .favorites: filtered = filtered.filter { AppSettings.shared.isFavorite($0.species.taxonId) }
        }

        // Search filter
        if !searchQuery.isEmpty {
            let query = searchQuery.lowercased()
            filtered = filtered.filter {
                $0.species.commonName.lowercased().contains(query) ||
                $0.species.scientificName.lowercased().contains(query)
            }
        }

        // Sort
        let sorted: [SpeciesWithStatus]
        switch sortMode {
        case .likelihood:
            sorted = filtered.sorted {
                let p0 = statusPriority($0.status)
                let p1 = statusPriority($1.status)
                if p0 != p1 { return p0 < p1 }
                let log0 = $0.species.totalObs > 0 ? log(Double($0.species.totalObs)) : 0
                let log1 = $1.species.totalObs > 0 ? log(Double($1.species.totalObs)) : 0
                return Double($0.currentAbundance) * log0 > Double($1.currentAbundance) * log1
            }
        case .peakDate:
            sorted = filtered.sorted { $0.species.peakWeek < $1.species.peakWeek }
        case .name:
            sorted = filtered.sorted {
                let n0 = $0.species.commonName.isEmpty ? $0.species.scientificName : $0.species.commonName
                let n1 = $1.species.commonName.isEmpty ? $1.species.scientificName : $1.species.commonName
                return n0.lowercased() < n1.lowercased()
            }
        case .taxonomy:
            sorted = filtered.sorted {
                let o0 = $0.species.order ?? ""
                let o1 = $1.species.order ?? ""
                if o0 != o1 { return o0 < o1 }
                let f0 = $0.species.family ?? ""
                let f1 = $1.species.family ?? ""
                if f0 != f1 { return f0 < f1 }
                return $0.species.scientificName < $1.species.scientificName
            }
        }

        // Taxonomy headers
        let headers: [Int: String]
        if sortMode == .taxonomy {
            headers = buildTaxonomyHeaders(sorted)
        } else {
            headers = [:]
        }

        speciesList = sorted
        activeCount = sorted.filter { $0.status != .inactive }.count
        totalCount = sorted.count
        observedCount = sorted.filter { $0.isObserved }.count
        taxonomyHeaders = headers
    }

    private func buildTaxonomyHeaders(_ sorted: [SpeciesWithStatus]) -> [Int: String] {
        guard !sorted.isEmpty else { return [:] }

        let useSci = AppSettings.shared.useScientificNames
        let distinctOrders = Set(sorted.compactMap { $0.species.order })
        let multipleOrders = distinctOrders.count > 1

        var headers: [Int: String] = [:]
        var lastOrder = ""
        var lastFamily = ""

        for (index, item) in sorted.enumerated() {
            let order = item.species.order ?? ""
            let familyCommon = item.species.family ?? "Unknown"
            let familySci = item.species.familyScientific

            if multipleOrders && order != lastOrder {
                let orderCommon = item.species.order ?? "Unknown"
                let orderSci = item.species.orderScientific
                let orderLabel: String
                if useSci {
                    orderLabel = orderSci ?? orderCommon
                } else if let orderSci {
                    orderLabel = "\(orderCommon) (\(orderSci))"
                } else {
                    orderLabel = orderCommon
                }
                headers[index] = orderLabel
                lastOrder = order
                lastFamily = ""
            }

            let familyKey = "\(order)/\(familyCommon)"
            if familyKey != lastFamily {
                let familyLabel: String
                if useSci {
                    familyLabel = familySci ?? familyCommon
                } else if let familySci {
                    familyLabel = "\(familyCommon) (\(familySci))"
                } else {
                    familyLabel = familyCommon
                }

                if let existing = headers[index] {
                    headers[index] = existing + "\n" + familyLabel
                } else {
                    headers[index] = familyLabel
                }
                lastFamily = familyKey
            }
        }
        return headers
    }

    private func statusPriority(_ status: SpeciesStatus) -> Int {
        switch status {
        case .peak: return 0
        case .active: return 1
        case .early, .late: return 2
        case .inactive: return 3
        }
    }
}

// MARK: - Species List View

struct SpeciesListView: View {
    @State private var viewModel = SpeciesListViewModel()
    @State private var showOotd = false
    @Environment(\.appColors) private var colors
    @Environment(AppSettings.self) private var appSettings
    @Environment(PhenologyRepository.self) private var repository

    var body: some View {
        Group {
            if viewModel.datasets.isEmpty {
                emptyState
            } else {
                speciesListContent
            }
        }
        .navigationTitle("")
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button { showOotd = true } label: {
                    HStack(spacing: 8) {
                        Image("manakin_logo")
                            .resizable()
                            .frame(width: 28, height: 28)
                        Text("Manakin")
                            .foregroundColor(.primary)
                            .fontWeight(.bold)
                            .font(.system(size: 20))
                    }
                }
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                HStack(spacing: 4) {
                    ActiveAllToggle(
                        showAll: viewModel.showAllSpecies,
                        onToggle: { viewModel.setShowAllSpecies($0) }
                    )
                    AppOverflowMenu()
                }
            }
        }
        .sheet(isPresented: $showOotd) {
            organismOfTheDaySheet
        }
        .onAppear {
            viewModel.repository = repository
            viewModel.loadDatasets()
            viewModel.updateSpeciesList()
        }
    }

    private var emptyState: some View {
        VStack(spacing: 8) {
            Spacer()
            Text("No datasets yet")
                .font(.system(size: 18, weight: .semibold))
                .foregroundColor(colors.onSurfaceVariant)
            Text("Go to the Datasets tab to download\nspecies data for your area")
                .font(.system(size: 14))
                .foregroundColor(colors.onSurfaceVariant)
                .multilineTextAlignment(.center)
            Spacer()
        }
    }

    private var speciesListContent: some View {
        ScrollView {
            LazyVStack(spacing: 8) {
                // Dataset selector
                DatasetSelector(
                    datasets: viewModel.datasets.map {
                        DatasetItem(key: $0.key, group: $0.group, placeName: $0.placeName)
                    },
                    selectedKeys: viewModel.selectedKeys,
                    onSelectSingle: { viewModel.selectSingleDataset($0) },
                    onToggle: { viewModel.toggleDataset($0) },
                    onSelectAll: { viewModel.selectAllDatasets() }
                )

                // Search bar
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(colors.onSurfaceVariant)
                    TextField("Search species...", text: Binding(
                        get: { viewModel.searchQuery },
                        set: { viewModel.searchQuery = $0 }
                    ))
                    .font(.system(size: 14))
                    if !viewModel.searchQuery.isEmpty {
                        Button {
                            viewModel.searchQuery = ""
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(colors.onSurfaceVariant)
                        }
                    }
                }
                .padding(10)
                .background(colors.surface)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(colors.surfaceVariant, lineWidth: 1)
                )
                .clipShape(RoundedRectangle(cornerRadius: 12))

                // Info row
                infoRow

                // Species list
                ForEach(Array(viewModel.speciesList.enumerated()), id: \.element.id) { index, item in
                    VStack(alignment: .leading, spacing: 0) {
                        if let header = viewModel.taxonomyHeaders[index] {
                            taxonomyHeaderView(header: header, isFirst: index == 0)
                        }
                        NavigationLink(value: AppRoute.speciesDetail(taxonId: item.species.taxonId)) {
                            SpeciesCardView(
                                species: item.species,
                                status: item.status,
                                currentWeek: viewModel.currentWeek,
                                isObserved: item.isObserved,
                                showObservedIndicator: viewModel.hasLifeList
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 4)
        }
    }

    private var infoRow: some View {
        HStack {
            let info: String = {
                var text = ""
                if viewModel.showAllSpecies {
                    text = "\(viewModel.totalCount) species"
                } else {
                    text = "\(viewModel.activeCount) active"
                }
                if viewModel.hasLifeList {
                    text += " \u{2022} \(viewModel.observedCount) seen"
                }
                return text
            }()
            Text(info)
                .font(.system(size: 12))
                .foregroundColor(colors.onSurfaceVariant)
            Spacer()
            DateChipView(
                selectedDate: viewModel.selectedDate,
                endDate: viewModel.endDate,
                isCustomDate: viewModel.isCustomDate,
                isRangeMode: viewModel.isRangeMode,
                onDateSelected: { viewModel.setDate($0) },
                onReset: { viewModel.resetToToday() }
            )
            SortDropdown(
                currentSort: viewModel.sortMode,
                onSortChange: { viewModel.setSortMode($0) }
            )
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 4)
    }

    @ViewBuilder
    private func taxonomyHeaderView(header: String, isFirst: Bool) -> some View {
        let parts = header.split(separator: "\n", maxSplits: 1)
        VStack(alignment: .leading, spacing: 2) {
            if parts.count > 1 {
                Text(parts[0])
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(.primary)
                Text(parts[1])
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.primary.opacity(0.7))
                    .padding(.leading, 8)
            } else {
                Text(header)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.primary)
            }
        }
        .padding(.top, isFirst ? 0 : 8)
        .padding(.bottom, 2)
    }

    private var organismOfTheDaySheet: some View {
        VStack(spacing: 16) {
            Text("Organism of the Day")
                .font(.headline)
                .foregroundColor(.primary)
                .fontWeight(.bold)
            if let ootd = viewModel.getOrganismOfTheDay() {
                let useSci = appSettings.useScientificNames
                let primaryName = useSci ? ootd.species.scientificName
                    : (ootd.species.commonName.isEmpty ? ootd.species.scientificName : ootd.species.commonName)
                Text(primaryName)
                    .font(.system(size: 20, weight: .bold))
                if !ootd.species.commonName.isEmpty {
                    let secondary = useSci ? ootd.species.commonName : ootd.species.scientificName
                    Text(secondary)
                        .font(.system(size: 14))
                        .italic(!useSci)
                        .foregroundColor(colors.onSurfaceVariant)
                }
                Text(ootd.key)
                    .font(.system(size: 13))
                    .foregroundColor(colors.onSurfaceVariant)
            } else {
                Text("No active species found.")
            }
            Button("Close") { showOotd = false }
                .foregroundColor(.primary)
        }
        .padding()
        .presentationDetents([.medium])
    }
}

// MARK: - Active/All Toggle

struct ActiveAllToggle: View {
    let showAll: Bool
    let onToggle: (Bool) -> Void
    @Environment(\.appColors) private var colors

    var body: some View {
        HStack(spacing: 0) {
            toggleButton(label: "Active", isSelected: !showAll) {
                onToggle(false)
            }
            toggleButton(label: "All", isSelected: showAll) {
                onToggle(true)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: 8))
    }

    private func toggleButton(label: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(isSelected ? colors.onPrimary : colors.onSurfaceVariant)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? Color.primary : Color.clear)
        }
        .background(isSelected ? Color.clear : colors.surfaceVariant)
    }
}

// MARK: - Sort Dropdown

struct SortDropdown: View {
    let currentSort: SortMode
    let onSortChange: (SortMode) -> Void

    private var label: String {
        switch currentSort {
        case .likelihood: return "Likelihood"
        case .peakDate: return "Peak"
        case .name: return "Name"
        case .taxonomy: return "Taxonomy"
        }
    }

    var body: some View {
        Menu {
            ForEach([SortMode.likelihood, .peakDate, .name, .taxonomy], id: \.self) { mode in
                Button {
                    onSortChange(mode)
                } label: {
                    Text(sortLabel(mode))
                }
            }
        } label: {
            Text(label)
                .font(.system(size: 13))
                .foregroundColor(.primary)
        }
    }

    private func sortLabel(_ mode: SortMode) -> String {
        switch mode {
        case .likelihood: return "By Likelihood"
        case .peakDate: return "By Peak Date"
        case .name: return "By Name"
        case .taxonomy: return "By Taxonomy"
        }
    }
}

// MARK: - Date Chip

struct DateChipView: View {
    let selectedDate: Date
    let endDate: Date?
    let isCustomDate: Bool
    let isRangeMode: Bool
    let onDateSelected: (Date) -> Void
    let onReset: () -> Void

    @State private var showDatePicker = false
    @Environment(\.appColors) private var colors

    private var label: String {
        let formatter = DateFormatter()
        formatter.dateFormat = "MMM d"
        if isRangeMode, let end = endDate {
            return "\(formatter.string(from: selectedDate)) - \(formatter.string(from: end))"
        } else if isCustomDate {
            return formatter.string(from: selectedDate)
        }
        return "Today"
    }

    var body: some View {
        HStack(spacing: 2) {
            Button { showDatePicker = true } label: {
                Text(label)
                    .font(.system(size: 13))
                    .foregroundColor(isCustomDate ? .primary : colors.onSurfaceVariant)
            }
            if isCustomDate {
                Button { onReset() } label: {
                    Text("\u{2715}")
                        .font(.system(size: 11))
                        .foregroundColor(colors.onSurfaceVariant)
                }
            }
        }
        .sheet(isPresented: $showDatePicker) {
            NavigationStack {
                DatePicker(
                    "Select Date",
                    selection: Binding(
                        get: { selectedDate },
                        set: { onDateSelected($0); showDatePicker = false }
                    ),
                    displayedComponents: .date
                )
                .datePickerStyle(.graphical)
                .padding()
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Cancel") { showDatePicker = false }
                    }
                }
            }
            .presentationDetents([.medium])
        }
    }
}

// MARK: - Overflow Menu

struct AppOverflowMenu: View {
    @Environment(\.appColors) private var colors

    var body: some View {
        Menu {
            NavigationLink(value: AppRoute.timeline) {
                Label("This Week", systemImage: "calendar")
            }
            NavigationLink(value: AppRoute.tripReport) {
                Label("Trip Report", systemImage: "doc.text")
            }
            NavigationLink(value: AppRoute.compare) {
                Label("Compare Locations", systemImage: "arrow.left.arrow.right")
            }
            NavigationLink(value: AppRoute.help) {
                Label("Help", systemImage: "questionmark.circle")
            }
            NavigationLink(value: AppRoute.about) {
                Label("About", systemImage: "info.circle")
            }
        } label: {
            Image(systemName: "ellipsis")
                .foregroundColor(colors.onSurfaceVariant)
        }
    }
}
