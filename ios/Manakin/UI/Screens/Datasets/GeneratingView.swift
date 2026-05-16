import SwiftUI

@Observable
class GeneratingViewModel {
    var progress: GenerationProgress?
    var completedPhases: Set<String> = []
    var isComplete: Bool = false
    var error: String?

    private var generationTask: Task<Void, Never>?
    private let repository: PhenologyRepository
    private let generator: DatasetGenerator

    init(repository: PhenologyRepository, generator: DatasetGenerator) {
        self.repository = repository
        self.generator = generator
    }

    func startGeneration(params: GenerationParams) {
        guard generationTask == nil || isComplete || error != nil else { return }
        isComplete = false
        error = nil
        completedPhases = []
        progress = nil

        generationTask = Task {
            do {
                let stream = generator.generate(
                    placeIds: params.placeIds,
                    placeName: params.placeName,
                    taxonIds: params.taxonIds,
                    taxonName: params.taxonName,
                    groupName: params.groupName,
                    minObs: params.minObs,
                    qualityGrade: params.qualityGrade,
                    maxPhotos: params.maxPhotos
                )

                for try await prog in stream {
                    await MainActor.run {
                        // Mark completed phases
                        let allPhases: [GenerationPhase] = [.fetchingSpecies, .fetchingHistograms, .fetchingDetails, .downloadingPhotos, .saving]
                        for phase in allPhases {
                            if phase.rawValue != prog.phase.rawValue {
                                // Check if this phase comes before current
                                if phaseOrder(phase) < phaseOrder(prog.phase) {
                                    completedPhases.insert(phase.rawValue)
                                }
                            }
                        }
                        self.progress = prog
                    }
                }

                // Stream completed successfully
                await MainActor.run {
                    let allPhases: [GenerationPhase] = [.fetchingSpecies, .fetchingHistograms, .fetchingDetails, .downloadingPhotos, .saving]
                    for phase in allPhases {
                        completedPhases.insert(phase.rawValue)
                    }
                    isComplete = true
                }

                // Reload datasets
                repository.reloadDatasets()

            } catch is CancellationError {
                // Cancelled, do nothing
            } catch {
                await MainActor.run {
                    self.error = error.localizedDescription
                }
            }
        }
    }

    func cancel() {
        generationTask?.cancel()
        generationTask = nil
    }

    private func phaseOrder(_ phase: GenerationPhase) -> Int {
        switch phase {
        case .fetchingSpecies: return 0
        case .fetchingHistograms: return 1
        case .fetchingDetails: return 2
        case .downloadingPhotos: return 3
        case .saving: return 4
        }
    }
}

struct GeneratingView: View {
    let params: GenerationParams
    let generator: DatasetGenerator
    let repository: PhenologyRepository
    let onDone: () -> Void
    let onCancel: () -> Void

    @Environment(\.appColors) private var colors
    @State private var viewModel: GeneratingViewModel?

    private var vm: GeneratingViewModel {
        viewModel!
    }

    private let phaseLabels: [(GenerationPhase, String)] = [
        (.fetchingSpecies, "Fetching species list"),
        (.fetchingHistograms, "Fetching weekly histograms"),
        (.fetchingDetails, "Fetching species details"),
        (.downloadingPhotos, "Downloading photos"),
        (.saving, "Saving dataset")
    ]

    var body: some View {
        Group {
            if viewModel != nil {
                contentView
            } else {
                Color.clear.onAppear {
                    let vm = GeneratingViewModel(repository: repository, generator: generator)
                    viewModel = vm
                    vm.startGeneration(params: params)
                }
            }
        }
        .background(colors.background)
        .navigationBarBackButtonHidden(true)
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text("Generating Dataset")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.appPrimary)
            }
        }
    }

    private var contentView: some View {
        VStack(alignment: .leading, spacing: 16) {
            Text(params.groupName)
                .font(.system(size: 20, weight: .bold))
                .foregroundColor(colors.onBackground)

            Text(params.placeName)
                .font(.system(size: 14))
                .foregroundColor(colors.onSurfaceVariant)

            Spacer().frame(height: 8)

            // Phase rows
            ForEach(phaseLabels, id: \.0.rawValue) { phase, label in
                phaseRow(phase: phase, label: label)
            }

            Spacer().frame(height: 8)

            // Progress bar
            if let progress = vm.progress, !vm.isComplete, vm.error == nil {
                ProgressView(value: progress.total > 0 ? Double(progress.current) / Double(progress.total) : 0)
                    .tint(.appPrimary)

                Text(progress.message)
                    .font(.system(size: 13))
                    .foregroundColor(colors.onSurfaceVariant)
            }

            // Error card
            if let error = vm.error {
                Spacer().frame(height: 8)
                Text(error)
                    .font(.system(size: 14))
                    .foregroundColor(.red)
                    .padding(16)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Color.red.opacity(0.1))
                    .clipShape(RoundedRectangle(cornerRadius: 12))
            }

            Spacer()

            // Buttons
            if vm.isComplete {
                Button(action: onDone) {
                    Text("Done")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.appPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            } else if vm.error != nil {
                HStack(spacing: 12) {
                    Button(action: onCancel) {
                        Text("Cancel")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(colors.onSurface)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(colors.onSurfaceVariant.opacity(0.5)))
                    }
                    Button {
                        vm.startGeneration(params: params)
                    } label: {
                        Text("Retry")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .frame(height: 48)
                            .background(Color.appPrimary)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
            } else {
                Button {
                    // Continue in background - just pop the view
                    onCancel()
                } label: {
                    Text("Continue in Background")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color.appPrimary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }

                Button {
                    vm.cancel()
                    onCancel()
                } label: {
                    Text("Cancel")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(colors.onSurface)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(colors.onSurfaceVariant.opacity(0.5)))
                }
            }
        }
        .padding(24)
    }

    private func phaseRow(phase: GenerationPhase, label: String) -> some View {
        let isComplete = vm.completedPhases.contains(phase.rawValue)
        let isActive = vm.progress?.phase.rawValue == phase.rawValue && !vm.isComplete

        return HStack(spacing: 12) {
            if isComplete {
                Image(systemName: "checkmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.appPrimary)
                    .frame(width: 20, height: 20)
            } else if isActive {
                ProgressView()
                    .scaleEffect(0.7)
                    .frame(width: 20, height: 20)
            } else {
                Image(systemName: "checkmark")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(colors.onSurfaceVariant.opacity(0.3))
                    .frame(width: 20, height: 20)
            }

            Text(label)
                .font(.system(size: 14, weight: isActive ? .semibold : .regular))
                .foregroundColor(
                    isComplete ? .appPrimary :
                    isActive ? colors.onBackground :
                    colors.onSurfaceVariant
                )
        }
    }
}
