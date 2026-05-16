import SwiftUI

struct SavedTrip: Codable {
    let name: String
    let startDate: String
    let endDate: String
    let datasetKeys: [String]
    let checkedTaxonIds: [Int]
}

struct TripReportView: View {
    let repository: PhenologyRepository

    @Environment(\.appColors) private var colors
    @Environment(\.dismiss) private var dismiss
    @Environment(AppSettings.self) private var appSettings

    @State private var startDate = Calendar.current.date(byAdding: .day, value: -7, to: Date()) ?? Date()
    @State private var endDate = Date()
    @State private var checkedSpecies: [Int: Bool] = [:]
    @State private var tripName = ""
    @State private var tripDatasetKeys: Set<String> = []
    @State private var showSavedTrips = false
    @State private var savedTrips: [(URL, SavedTrip)] = []

    private var allKeys: [String] { repository.getKeys() }

    private var effectiveStart: Date {
        startDate > endDate ? endDate : startDate
    }

    private var effectiveEnd: Date {
        startDate > endDate ? startDate : endDate
    }

    private var startWeek: Int {
        Calendar.current.component(.weekOfYear, from: effectiveStart)
    }

    private var endWeek: Int {
        Calendar.current.component(.weekOfYear, from: effectiveEnd)
    }

    private var weekRange: Set<Int> {
        if startWeek <= endWeek {
            return Set(startWeek...endWeek)
        }
        return Set(startWeek...53).union(Set(1...endWeek))
    }

    private var activeSpecies: [(Species, String)] {
        var seen: Set<Int> = []
        var list: [(Species, String)] = []
        for key in tripDatasetKeys {
            for sp in repository.getSpeciesForKey(key: key) {
                guard !seen.contains(sp.taxonId) else { continue }
                seen.insert(sp.taxonId)
                let isActive = sp.weekly.contains { weekRange.contains($0.week) && $0.n > 0 }
                if isActive { list.append((sp, key)) }
            }
        }
        return list.sorted {
            let a = $0.0.commonName.isEmpty ? $0.0.scientificName : $0.0.commonName
            let b = $1.0.commonName.isEmpty ? $1.0.scientificName : $1.0.commonName
            return a.lowercased() < b.lowercased()
        }
    }

    private var checkedCount: Int {
        checkedSpecies.values.filter { $0 }.count
    }

    private var tripsDir: URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
            .appendingPathComponent("trips")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private let dateFormatter: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "MMM d, yyyy"
        return f
    }()

    var body: some View {
        List {
            // Dataset selector
            Section {
                DatasetSelector(
                    datasets: allKeys.map { DatasetItem(key: $0, group: repository.getGroupName(key: $0), placeName: repository.getPlaceNameForKey(key: $0)) },
                    selectedKeys: tripDatasetKeys,
                    onSelectSingle: { tripDatasetKeys = Set([$0]) },
                    onToggle: { key in
                        if tripDatasetKeys.contains(key) && tripDatasetKeys.count > 1 {
                            tripDatasetKeys.remove(key)
                        } else {
                            tripDatasetKeys.insert(key)
                        }
                    },
                    onSelectAll: { tripDatasetKeys = Set(allKeys) }
                )
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))

            // Trip name + save/load
            Section {
                HStack(spacing: 8) {
                    TextField("Trip name...", text: $tripName)
                        .font(.system(size: 13))
                        .textFieldStyle(.roundedBorder)

                    Button("Save") { saveTrip() }
                        .font(.system(size: 13))
                        .foregroundColor(.appPrimary)

                    Button("Load") { showSavedTrips = true }
                        .font(.system(size: 13))
                        .foregroundColor(.appPrimary)
                }
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))

            // Date range
            Section {
                HStack(spacing: 8) {
                    DatePicker("", selection: $startDate, displayedComponents: .date)
                        .labelsHidden()
                        .frame(maxWidth: .infinity)
                    Text("to")
                        .foregroundColor(colors.onSurfaceVariant)
                    DatePicker("", selection: $endDate, displayedComponents: .date)
                        .labelsHidden()
                        .frame(maxWidth: .infinity)
                }
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))

            // Summary card
            Section {
                summaryCard
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))

            // Instructions
            Section {
                Text("Tap to check off species you found:")
                    .font(.system(size: 13))
                    .foregroundColor(colors.onSurfaceVariant)
            }
            .listRowBackground(Color.clear)
            .listRowInsets(EdgeInsets(top: 0, leading: 12, bottom: 0, trailing: 12))

            // Species checklist
            Section {
                ForEach(activeSpecies, id: \.0.taxonId) { sp, _ in
                    speciesCheckRow(species: sp)
                }
            }
            .listRowInsets(EdgeInsets(top: 2, leading: 12, bottom: 2, trailing: 12))
            .listRowBackground(Color.clear)
        }
        .listStyle(.plain)
        .scrollContentBackground(.hidden)
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
                Text("Trip Report")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.appPrimary)
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                Button { shareReport() } label: {
                    Image(systemName: "square.and.arrow.up")
                        .foregroundColor(.appPrimary)
                }
            }
        }
        .onAppear {
            let sk = appSettings.selectedDatasetKeys
            tripDatasetKeys = sk.isEmpty ? Set(allKeys) : sk
            loadTripsFromDisk()
        }
        .sheet(isPresented: $showSavedTrips) {
            savedTripsSheet
        }
    }

    private var summaryCard: some View {
        HStack {
            Spacer()
            VStack {
                Text("\(checkedCount)")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.appPrimary)
                Text("Found")
                    .font(.system(size: 12))
                    .foregroundColor(colors.onSurfaceVariant)
            }
            Spacer()
            VStack {
                Text("\(activeSpecies.count)")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(colors.onSurfaceVariant)
                Text("Active")
                    .font(.system(size: 12))
                    .foregroundColor(colors.onSurfaceVariant)
            }
            Spacer()
            VStack {
                let pct = activeSpecies.isEmpty ? 0 : (checkedCount * 100 / activeSpecies.count)
                Text("\(pct)%")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundColor(.appPrimary)
                Text("Rate")
                    .font(.system(size: 12))
                    .foregroundColor(colors.onSurfaceVariant)
            }
            Spacer()
        }
        .padding(16)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func speciesCheckRow(species: Species) -> some View {
        let isChecked = checkedSpecies[species.taxonId] == true
        return Button {
            checkedSpecies[species.taxonId] = !isChecked
        } label: {
            HStack(spacing: 8) {
                Image(systemName: isChecked ? "checkmark.square.fill" : "square")
                    .foregroundColor(isChecked ? .appPrimary : colors.onSurfaceVariant)

                VStack(alignment: .leading, spacing: 1) {
                    let useSci = appSettings.useScientificNames
                    let primaryName = useSci ? species.scientificName : (species.commonName.isEmpty ? species.scientificName : species.commonName)
                    Text(primaryName)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundColor(colors.onSurface)
                    if !species.commonName.isEmpty {
                        let secondaryName = useSci ? species.commonName : species.scientificName
                        Text(secondaryName)
                            .font(.system(size: 12))
                            .italic(!useSci)
                            .foregroundColor(colors.onSurfaceVariant)
                    }
                }
                Spacer()
                if isChecked {
                    Image(systemName: "checkmark")
                        .font(.system(size: 16))
                        .foregroundColor(.appPrimary)
                }
            }
            .padding(12)
            .background(isChecked ? Color.appPrimary.opacity(0.1) : colors.surface)
            .clipShape(RoundedRectangle(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }

    private func shareReport() {
        let report = buildReportText()
        let av = UIActivityViewController(activityItems: [report], applicationActivities: nil)
        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
           let root = scene.windows.first?.rootViewController {
            root.present(av, animated: true)
        }
    }

    private func buildReportText() -> String {
        var text = "Manakin Trip Report\n"
        text += "\(dateFormatter.string(from: startDate)) \u{2014} \(dateFormatter.string(from: endDate))\n\n"
        text += "\(checkedCount) of \(activeSpecies.count) active species found\n\n"

        let found = activeSpecies.filter { checkedSpecies[$0.0.taxonId] == true }
        if !found.isEmpty {
            text += "Found:\n"
            for (sp, _) in found {
                text += "  \u{2713} \(sp.commonName.isEmpty ? sp.scientificName : sp.commonName)\n"
            }
        }
        let missed = activeSpecies.filter { checkedSpecies[$0.0.taxonId] != true }
        if !missed.isEmpty {
            text += "\nMissed:\n"
            for (sp, _) in missed {
                text += "  \u{2717} \(sp.commonName.isEmpty ? sp.scientificName : sp.commonName)\n"
            }
        }
        return text
    }

    private func saveTrip() {
        guard !tripName.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        let trip = SavedTrip(
            name: tripName,
            startDate: ISO8601DateFormatter().string(from: startDate),
            endDate: ISO8601DateFormatter().string(from: endDate),
            datasetKeys: Array(tripDatasetKeys),
            checkedTaxonIds: checkedSpecies.filter { $0.value }.map { $0.key }
        )
        let slug = tripName.replacingOccurrences(of: "[^a-zA-Z0-9]", with: "_", options: .regularExpression)
        let dir = tripsDir
        Task {
            let file = dir.appendingPathComponent("\(slug).json")
            if let data = try? JSONEncoder().encode(trip) {
                try? data.write(to: file)
            }
            loadTripsFromDisk()
        }
    }

    private func loadTripsFromDisk() {
        let dir = tripsDir
        Task {
            let fm = FileManager.default
            let files = (try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: [.contentModificationDateKey])) ?? []
            let decoder = JSONDecoder()
            var loaded: [(URL, SavedTrip)] = []
            for file in files where file.pathExtension == "json" {
                guard let data = try? Data(contentsOf: file),
                      let trip = try? decoder.decode(SavedTrip.self, from: data) else { continue }
                loaded.append((file, trip))
            }
            loaded.sort { a, b in
                let dateA = (try? a.0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                let dateB = (try? b.0.resourceValues(forKeys: [.contentModificationDateKey]).contentModificationDate) ?? .distantPast
                return dateA > dateB
            }
            savedTrips = loaded
        }
    }

    private var savedTripsSheet: some View {
        NavigationView {
            VStack {
                if savedTrips.isEmpty {
                    Spacer()
                    Text("No saved trips yet")
                        .foregroundColor(colors.onSurfaceVariant)
                    Spacer()
                } else {
                    List {
                        ForEach(savedTrips, id: \.0) { file, trip in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(trip.name)
                                        .font(.system(size: 15, weight: .semibold))
                                    Text("\(trip.startDate) \u{2014} \(trip.endDate)")
                                        .font(.system(size: 12))
                                        .foregroundColor(colors.onSurfaceVariant)
                                }
                                Spacer()
                                Button {
                                    deleteTrip(file: file)
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.system(size: 14))
                                        .foregroundColor(.red)
                                }
                                .buttonStyle(.plain)
                            }
                            .contentShape(Rectangle())
                            .onTapGesture {
                                loadTrip(trip)
                                showSavedTrips = false
                            }
                        }
                    }
                }
            }
            .navigationTitle("Saved Trips")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { showSavedTrips = false }
                }
            }
        }
    }

    private func loadTrip(_ trip: SavedTrip) {
        tripName = trip.name
        let iso = ISO8601DateFormatter()
        if let s = iso.date(from: trip.startDate) { startDate = s }
        if let e = iso.date(from: trip.endDate) { endDate = e }
        tripDatasetKeys = Set(trip.datasetKeys)
        checkedSpecies.removeAll()
        for id in trip.checkedTaxonIds {
            checkedSpecies[id] = true
        }
    }

    private func deleteTrip(file: URL) {
        Task.detached {
            try? FileManager.default.removeItem(at: file)
            await MainActor.run { loadTripsFromDisk() }
        }
    }
}
