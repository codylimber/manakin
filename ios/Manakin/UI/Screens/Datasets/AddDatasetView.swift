import SwiftUI

struct AddDatasetState {
    var placeQuery: String = ""
    var placeResults: [PlaceResult] = []
    var selectedPlaces: [PlaceResult] = []
    var showPlaceDropdown: Bool = false

    var taxonQuery: String = ""
    var taxonResults: [TaxonResult] = []
    var selectedTaxons: [TaxonResult] = []
    var showTaxonDropdown: Bool = false

    var showAllPlaces: Bool = false
    var groupLabel: String = ""
    var groupLabelEdited: Bool = false

    var showAdvanced: Bool = false
    var minObs: String = "10"
    var qualityGrade: String = "research"
    var maxPhotos: String = "3"

    var estimatedSpecies: Int?
    var isEstimating: Bool = false
    var isSearching: Bool = false

    var filteredPlaceResults: [PlaceResult] {
        showAllPlaces ? placeResults : placeResults.filter { $0.adminLevel != nil }
    }

    var canGenerate: Bool {
        !selectedPlaces.isEmpty && !groupLabel.trimmingCharacters(in: .whitespaces).isEmpty
    }

    var estimatedSizeMb: Double? {
        guard let count = estimatedSpecies else { return nil }
        let photosPerSpecies = Int(maxPhotos) ?? 3
        let bytes = Double(count) * (2_000.0 + Double(photosPerSpecies) * 75_000.0)
        return bytes / 1_000_000.0
    }

    var estimatedMinutes: Double? {
        guard let count = estimatedSpecies else { return nil }
        return Double(count) * 2.0 / 60.0
    }
}

@Observable
class AddDatasetViewModel {
    var state = AddDatasetState()

    private let apiClient: INatApiClient
    private var placeSearchTask: Task<Void, Never>?
    private var taxonSearchTask: Task<Void, Never>?
    private var estimateTask: Task<Void, Never>?

    init(apiClient: INatApiClient) {
        self.apiClient = apiClient
    }

    func onPlaceQueryChanged(_ query: String) {
        state.placeQuery = query
        state.showPlaceDropdown = true
        placeSearchTask?.cancel()
        guard query.count >= 2 else {
            state.placeResults = []
            return
        }
        placeSearchTask = Task {
            try? await Task.sleep(nanoseconds: 150_000_000)
            guard !Task.isCancelled else { return }
            state.isSearching = true
            do {
                let results = try await apiClient.searchPlaces(query: query)
                if !Task.isCancelled {
                    state.placeResults = results
                }
            } catch {
                if !Task.isCancelled {
                    state.placeResults = []
                }
            }
            state.isSearching = false
        }
    }

    func addPlace(_ place: PlaceResult) {
        guard !state.selectedPlaces.contains(where: { $0.id == place.id }) else { return }
        state.selectedPlaces.append(place)
        state.placeQuery = ""
        state.showPlaceDropdown = false
        state.placeResults = []
        updateAutoLabel()
        fetchEstimate()
    }

    func removePlace(_ place: PlaceResult) {
        state.selectedPlaces.removeAll { $0.id == place.id }
        updateAutoLabel()
        fetchEstimate()
    }

    func onTaxonQueryChanged(_ query: String) {
        state.taxonQuery = query
        state.showTaxonDropdown = true
        taxonSearchTask?.cancel()
        guard query.count >= 2 else {
            state.taxonResults = []
            return
        }
        taxonSearchTask = Task {
            try? await Task.sleep(nanoseconds: 150_000_000)
            guard !Task.isCancelled else { return }
            state.isSearching = true
            do {
                let results = try await apiClient.searchTaxa(query: query)
                if !Task.isCancelled {
                    state.taxonResults = results
                }
            } catch {
                if !Task.isCancelled {
                    state.taxonResults = []
                }
            }
            state.isSearching = false
        }
    }

    func addTaxon(_ taxon: TaxonResult) {
        guard !state.selectedTaxons.contains(where: { $0.id == taxon.id }) else { return }
        state.selectedTaxons.append(taxon)
        state.taxonQuery = ""
        state.showTaxonDropdown = false
        state.taxonResults = []
        updateAutoLabel()
        fetchEstimate()
    }

    func removeTaxon(_ taxon: TaxonResult) {
        state.selectedTaxons.removeAll { $0.id == taxon.id }
        updateAutoLabel()
        fetchEstimate()
    }

    func onGroupLabelChanged(_ label: String) {
        state.groupLabel = label
        state.groupLabelEdited = true
    }

    func onMinObsChanged(_ value: String) {
        state.minObs = value.filter { $0.isNumber }
    }

    func onQualityGradeChanged(_ grade: String) {
        state.qualityGrade = grade
        fetchEstimate()
    }

    func onMaxPhotosChanged(_ value: String) {
        state.maxPhotos = value.filter { $0.isNumber }
    }

    func toggleShowAllPlaces() {
        state.showAllPlaces.toggle()
    }

    func toggleAdvanced() {
        state.showAdvanced.toggle()
    }

    func dismissDropdowns() {
        state.showPlaceDropdown = false
        state.showTaxonDropdown = false
    }

    private func updateAutoLabel() {
        guard !state.groupLabelEdited else { return }
        let taxonPart = state.selectedTaxons.isEmpty
            ? "All Species"
            : state.selectedTaxons.map { $0.commonName.isEmpty ? $0.scientificName : $0.commonName }.joined(separator: ", ")
        let placePart = state.selectedPlaces.map { $0.name.components(separatedBy: ",").first ?? $0.name }.joined(separator: ", ")
        state.groupLabel = placePart.isEmpty ? taxonPart : "\(placePart) \(taxonPart)"
    }

    private func fetchEstimate() {
        estimateTask?.cancel()
        guard !state.selectedPlaces.isEmpty else {
            state.estimatedSpecies = nil
            state.isEstimating = false
            return
        }
        estimateTask = Task {
            state.isEstimating = true
            do {
                let taxonIds: [Int?] = state.selectedTaxons.isEmpty ? [nil] : state.selectedTaxons.map { $0.id }
                var total = 0
                for taxonId in taxonIds {
                    let count = try await apiClient.getSpeciesCountEstimate(
                        taxonId: taxonId,
                        placeIds: state.selectedPlaces.map { $0.id },
                        qualityGrade: state.qualityGrade
                    )
                    total += count
                }
                if !Task.isCancelled {
                    state.estimatedSpecies = total
                }
            } catch {
                if !Task.isCancelled {
                    state.estimatedSpecies = nil
                }
            }
            state.isEstimating = false
        }
    }
}

struct AddDatasetView: View {
    let apiClient: INatApiClient
    var onGenerate: () -> Void = {}
    var repository: PhenologyRepository?

    @Environment(\.appColors) private var colors
    @Environment(\.dismiss) private var dismiss

    @State private var viewModel: AddDatasetViewModel?
    @State private var importSuccess: Bool?
    @State private var isGenerating = false
    @State private var generationProgress: GenerationProgress?
    @State private var generationError: String?
    @State private var generationComplete = false

    private var vm: AddDatasetViewModel {
        viewModel!
    }

    var body: some View {
        Group {
            if viewModel != nil {
                content
            } else {
                Color.clear.onAppear {
                    viewModel = AddDatasetViewModel(apiClient: apiClient)
                }
            }
        }
        .overlay {
            if isGenerating {
                ZStack {
                    Color.black.opacity(0.7).ignoresSafeArea()
                    VStack(spacing: 16) {
                        if generationComplete {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 48))
                                .foregroundColor(.appPrimary)
                            Text("Dataset Generated!")
                                .font(.headline)
                                .foregroundColor(.white)
                            Button("Done") {
                                dismiss()
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.appPrimary)
                        } else if let error = generationError {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .font(.system(size: 48))
                                .foregroundColor(.red)
                            Text("Error: \(error)")
                                .font(.subheadline)
                                .foregroundColor(.white)
                                .multilineTextAlignment(.center)
                            Button("Dismiss") {
                                isGenerating = false
                            }
                            .buttonStyle(.borderedProminent)
                            .tint(.red)
                        } else {
                            ProgressView()
                                .scaleEffect(1.5)
                                .tint(.appPrimary)
                            if let progress = generationProgress {
                                Text(progress.message)
                                    .font(.subheadline)
                                    .foregroundColor(.white)
                                    .multilineTextAlignment(.center)
                                Text("\(progress.current)/\(progress.total)")
                                    .font(.caption)
                                    .foregroundColor(.white.opacity(0.7))
                            } else {
                                Text("Starting...")
                                    .font(.subheadline)
                                    .foregroundColor(.white)
                            }
                        }
                    }
                    .padding(32)
                }
            }
        }
        .background(colors.background)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .navigationBarLeading) {
                Button { dismiss() } label: {
                    Image(systemName: "chevron.left")
                        .foregroundColor(.appPrimary)
                }
            }
            ToolbarItem(placement: .principal) {
                Text("Add Dataset")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.appPrimary)
            }
        }
    }

    private var content: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                // Place search
                HStack {
                    Text("Locations")
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundColor(colors.onSurfaceVariant)
                    Spacer()
                    Button {
                        vm.toggleShowAllPlaces()
                    } label: {
                        Text(vm.state.showAllPlaces ? "All Places" : "Regions Only")
                            .font(.system(size: 12))
                            .foregroundColor(vm.state.showAllPlaces ? .appPrimary : colors.onSurfaceVariant)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(vm.state.showAllPlaces ? Color.appPrimary.opacity(0.15) : colors.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                    }
                }

                VStack(spacing: 0) {
                    TextField("Search for a place...", text: Binding(
                        get: { vm.state.placeQuery },
                        set: { vm.onPlaceQueryChanged($0) }
                    ))
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)

                    if vm.state.showPlaceDropdown && !vm.state.filteredPlaceResults.isEmpty {
                        VStack(spacing: 0) {
                            ForEach(vm.state.filteredPlaceResults) { place in
                                Button {
                                    vm.addPlace(place)
                                } label: {
                                    Text(place.name)
                                        .font(.system(size: 14))
                                        .foregroundColor(colors.onSurface)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                }
                                Divider()
                            }
                        }
                        .background(colors.surface)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .shadow(radius: 2)
                    }
                }

                // Selected place chips
                if !vm.state.selectedPlaces.isEmpty {
                    FlowLayout(spacing: 6) {
                        ForEach(vm.state.selectedPlaces) { place in
                            HStack(spacing: 4) {
                                Text(place.name)
                                    .font(.system(size: 13))
                                Button { vm.removePlace(place) } label: {
                                    Image(systemName: "xmark")
                                        .font(.system(size: 10))
                                }
                            }
                            .foregroundColor(.appPrimary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.appPrimary.opacity(0.15))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                    }
                }

                Spacer().frame(height: 4)

                // Taxon search
                Text("Taxa (optional)")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(colors.onSurfaceVariant)

                VStack(spacing: 0) {
                    TextField("Search for a taxon...", text: Binding(
                        get: { vm.state.taxonQuery },
                        set: { vm.onTaxonQueryChanged($0) }
                    ))
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)

                    if vm.state.showTaxonDropdown && !vm.state.taxonResults.isEmpty {
                        VStack(spacing: 0) {
                            ForEach(vm.state.taxonResults) { taxon in
                                Button {
                                    vm.addTaxon(taxon)
                                } label: {
                                    Text(taxon.displayName)
                                        .font(.system(size: 14))
                                        .foregroundColor(colors.onSurface)
                                        .frame(maxWidth: .infinity, alignment: .leading)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 8)
                                }
                                Divider()
                            }
                        }
                        .background(colors.surface)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                        .shadow(radius: 2)
                    }
                }

                // Selected taxon chips
                if !vm.state.selectedTaxons.isEmpty {
                    FlowLayout(spacing: 6) {
                        ForEach(vm.state.selectedTaxons) { taxon in
                            HStack(spacing: 4) {
                                Text(taxon.commonName.isEmpty ? taxon.scientificName : taxon.commonName)
                                    .font(.system(size: 13))
                                Button { vm.removeTaxon(taxon) } label: {
                                    Image(systemName: "xmark")
                                        .font(.system(size: 10))
                                }
                            }
                            .foregroundColor(.appPrimary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color.appPrimary.opacity(0.15))
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                    }
                }

                Spacer().frame(height: 4)

                // Tab label
                Text("Tab Label")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(colors.onSurfaceVariant)

                TextField("e.g., CT Butterflies", text: Binding(
                    get: { vm.state.groupLabel },
                    set: { vm.onGroupLabelChanged($0) }
                ))
                .textFieldStyle(.roundedBorder)

                Spacer().frame(height: 4)

                // Advanced options
                Button {
                    vm.toggleAdvanced()
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: vm.state.showAdvanced ? "chevron.up" : "chevron.down")
                            .font(.system(size: 12))
                        Text("Advanced Options")
                            .font(.system(size: 13, weight: .semibold))
                    }
                    .foregroundColor(.appPrimary)
                }

                if vm.state.showAdvanced {
                    advancedOptions
                }

                Spacer().frame(height: 4)

                // Estimate card
                if vm.state.isEstimating {
                    HStack(spacing: 12) {
                        ProgressView()
                            .scaleEffect(0.8)
                            .tint(.appPrimary)
                        Text("Estimating...")
                            .font(.system(size: 13))
                            .foregroundColor(colors.onSurfaceVariant)
                    }
                    .padding(16)
                    .background(colors.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                } else if let count = vm.state.estimatedSpecies {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Estimate")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(.appPrimary)
                        let sizeStr: String = {
                            guard let mb = vm.state.estimatedSizeMb else { return "" }
                            return mb < 1 ? "< 1 MB" : "~\(Int(mb)) MB"
                        }()
                        let timeStr: String = {
                            guard let min = vm.state.estimatedMinutes else { return "" }
                            return min < 1 ? "< 1 min" : "~\(Int(min)) min"
                        }()
                        Text("~\(count) species  \u{2022}  \(sizeStr)  \u{2022}  \(timeStr)")
                            .font(.system(size: 13))
                            .foregroundColor(colors.onSurfaceVariant)
                    }
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(colors.surfaceVariant)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                Spacer().frame(height: 4)

                // Generate button
                Button {
                    let places = vm.state.selectedPlaces
                    let taxons = vm.state.selectedTaxons
                    let placeName = places.map { $0.name }.joined(separator: ", ")
                    let taxonName = taxons.isEmpty ? "All Species"
                        : taxons.map { $0.commonName.isEmpty ? $0.scientificName : $0.commonName }.joined(separator: ", ")
                    let taxonIds: [Int?] = taxons.isEmpty ? [nil] : taxons.map { $0.id }

                    GenerationParams.current = GenerationParams(
                        placeIds: places.map { $0.id },
                        placeName: placeName,
                        taxonIds: taxonIds,
                        taxonName: taxonName,
                        groupName: vm.state.groupLabel,
                        minObs: Int(vm.state.minObs) ?? 1,
                        qualityGrade: vm.state.qualityGrade,
                        maxPhotos: Int(vm.state.maxPhotos) ?? 3
                    )
                    print("[AddDataset] Generate tapped, starting generation")
                    isGenerating = true
                    generationError = nil
                    generationComplete = false
                    let generator = DatasetGenerator(apiClient: apiClient)
                    let params = GenerationParams.current!
                    Task {
                        do {
                            for try await progress in generator.generate(
                                placeIds: params.placeIds,
                                placeName: params.placeName,
                                taxonIds: params.taxonIds,
                                taxonName: params.taxonName,
                                groupName: params.groupName,
                                minObs: params.minObs,
                                qualityGrade: params.qualityGrade,
                                maxPhotos: params.maxPhotos
                            ) {
                                generationProgress = progress
                            }
                            generationComplete = true
                            repository?.reloadDatasets()
                        } catch {
                            generationError = error.localizedDescription
                        }
                    }
                } label: {
                    Text("Generate Dataset")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                }
                .background(vm.state.canGenerate ? Color.appPrimary : Color.gray)
                .clipShape(RoundedRectangle(cornerRadius: 12))
                .disabled(!vm.state.canGenerate)
            }
            .padding(16)
        }
    }

    private var advancedOptions: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Minimum Observations")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(colors.onSurfaceVariant)
            Text("Species with fewer observations will be excluded. Higher = smaller, more reliable list.")
                .font(.system(size: 12))
                .foregroundColor(colors.onSurfaceVariant)
            TextField("10", text: Binding(
                get: { vm.state.minObs },
                set: { vm.onMinObsChanged($0) }
            ))
            .textFieldStyle(.roundedBorder)
            .frame(width: 120)
            .keyboardType(.numberPad)

            Text("Quality Grade")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(colors.onSurfaceVariant)
            HStack(spacing: 8) {
                qualityChip("Research Grade", value: "research")
                qualityChip("+ Needs ID", value: "research,needs_id")
            }

            Text("Photos per Species")
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(colors.onSurfaceVariant)
            TextField("3", text: Binding(
                get: { vm.state.maxPhotos },
                set: { vm.onMaxPhotosChanged($0) }
            ))
            .textFieldStyle(.roundedBorder)
            .frame(width: 120)
            .keyboardType(.numberPad)

            // Import dataset
            if repository != nil {
                Divider().padding(.vertical, 4)
                Text("Import Dataset")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(colors.onSurfaceVariant)
                Text("Import a .manakin file shared by another user.")
                    .font(.system(size: 12))
                    .foregroundColor(colors.onSurfaceVariant)

                Button {
                    // File import would use a document picker
                    // For now, placeholder
                } label: {
                    Text("Choose File")
                        .font(.system(size: 13))
                        .foregroundColor(.appPrimary)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color.appPrimary.opacity(0.5)))
                }

                if let success = importSuccess {
                    Text(success ? "Dataset imported successfully!" : "Import failed. Check the file format.")
                        .font(.system(size: 12))
                        .foregroundColor(success ? .appPrimary : .red)
                }
            }
        }
    }

    private func qualityChip(_ label: String, value: String) -> some View {
        Button {
            vm.onQualityGradeChanged(value)
        } label: {
            Text(label)
                .font(.system(size: 12))
                .foregroundColor(vm.state.qualityGrade == value ? .appPrimary : colors.onSurfaceVariant)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(vm.state.qualityGrade == value ? Color.appPrimary.opacity(0.15) : colors.surfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }
}
