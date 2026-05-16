import Foundation

enum GenerationPhase: String {
    case fetchingSpecies = "FETCHING_SPECIES"
    case fetchingHistograms = "FETCHING_HISTOGRAMS"
    case fetchingDetails = "FETCHING_DETAILS"
    case downloadingPhotos = "DOWNLOADING_PHOTOS"
    case saving = "SAVING"
}

struct GenerationProgress {
    let phase: GenerationPhase
    let current: Int
    let total: Int
    let message: String
}

class DatasetGenerator {

    private let apiClient: INatApiClient

    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.outputFormatting = [.sortedKeys]
        return e
    }()

    private var datasetsDir: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return docs.appendingPathComponent("datasets")
    }

    init(apiClient: INatApiClient) {
        self.apiClient = apiClient
    }

    func generate(
        placeIds: [Int],
        placeName: String,
        taxonIds: [Int?],
        taxonName: String,
        groupName: String,
        minObs: Int = 1,
        qualityGrade: String = "research",
        maxPhotos: Int = 3
    ) -> AsyncThrowingStream<GenerationProgress, Error> {
        AsyncThrowingStream { continuation in
            Task {
                do {
                    let result = try await self.performGeneration(
                        placeIds: placeIds,
                        placeName: placeName,
                        taxonIds: taxonIds,
                        taxonName: taxonName,
                        groupName: groupName,
                        minObs: minObs,
                        qualityGrade: qualityGrade,
                        maxPhotos: maxPhotos,
                        onProgress: { progress in
                            continuation.yield(progress)
                        }
                    )
                    // Final progress indicating completion
                    continuation.yield(result)
                    continuation.finish()
                } catch {
                    continuation.finish(throwing: error)
                }
            }
        }
    }

    private func performGeneration(
        placeIds: [Int],
        placeName: String,
        taxonIds: [Int?],
        taxonName: String,
        groupName: String,
        minObs: Int,
        qualityGrade: String,
        maxPhotos: Int,
        onProgress: @Sendable (GenerationProgress) -> Void
    ) async throws -> GenerationProgress {
        let slug = "\(groupName)-\(placeName)"
            .lowercased()
            .replacingOccurrences(of: "[^a-z0-9]+", with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))

        let outputDir = datasetsDir.appendingPathComponent(slug)
        let photosDir = outputDir.appendingPathComponent("photos")
        try FileManager.default.createDirectory(at: photosDir, withIntermediateDirectories: true)

        // Step 1: Fetch species list across all taxon+place combos
        onProgress(GenerationProgress(phase: .fetchingSpecies, current: 0, total: 1, message: "Fetching species list..."))

        var speciesByTaxonId: [Int: [String: Any]] = [:]
        var obsCounts: [Int: Int] = [:]

        for taxonId in taxonIds {
            for placeId in placeIds {
                var page = 1
                while true {
                    let (_, results) = try await apiClient.getSpeciesCounts(
                        taxonId: taxonId,
                        placeId: placeId,
                        qualityGrade: qualityGrade,
                        page: page
                    )
                    for r in results {
                        guard let taxon = r["taxon"] as? [String: Any],
                              let tid = taxon["id"] as? Int else { continue }
                        let count = r["count"] as? Int ?? 0
                        if speciesByTaxonId[tid] == nil {
                            speciesByTaxonId[tid] = r
                            obsCounts[tid] = count
                        } else {
                            obsCounts[tid] = (obsCounts[tid] ?? 0) + count
                        }
                    }
                    onProgress(GenerationProgress(
                        phase: .fetchingSpecies,
                        current: speciesByTaxonId.count,
                        total: speciesByTaxonId.count,
                        message: "Found \(speciesByTaxonId.count) species so far..."
                    ))
                    if results.count < 200 || results.isEmpty { break }
                    page += 1
                }
            }
        }

        // Filter by min obs (using merged counts)
        let filteredIds = obsCounts.filter { $0.value >= minObs }.keys
        let filteredRaw: [[String: Any]] = filteredIds.compactMap { tid in
            guard var obj = speciesByTaxonId[tid] else { return nil }
            obj["count"] = obsCounts[tid] ?? 0
            return obj
        }

        let speciesList = DataProcessor.buildSpeciesList(rawCounts: filteredRaw)
        guard !speciesList.isEmpty else {
            throw GenerationError.noSpeciesFound
        }

        let nSpecies = speciesList.count
        onProgress(GenerationProgress(phase: .fetchingSpecies, current: nSpecies, total: nSpecies, message: "\(nSpecies) species found"))

        // Step 2: Fetch histograms - sum across all places
        var histograms: [String: [Int: Int]] = [:]
        for (i, sp) in speciesList.enumerated() {
            onProgress(GenerationProgress(
                phase: .fetchingHistograms,
                current: i + 1,
                total: nSpecies,
                message: "Histogram: \(sp.commonName.isEmpty ? sp.scientificName : sp.commonName) (\(i + 1)/\(nSpecies))"
            ))

            var combined: [Int: Int] = [:]
            for placeId in placeIds {
                let h = try await apiClient.getHistogram(taxonId: sp.taxonId, placeId: placeId, qualityGrade: qualityGrade)
                for (week, count) in h {
                    combined[week] = (combined[week] ?? 0) + count
                }
            }
            histograms[sp.scientificName] = combined
        }

        let weekly = DataProcessor.buildWeeklyMatrix(histograms: histograms)

        // Update peak/first/last week
        for sp in speciesList {
            guard let h = histograms[sp.scientificName] else { continue }
            let active = h.filter { $0.value > 0 }
            if !active.isEmpty {
                sp.peakWeek = active.max(by: { $0.value < $1.value })!.key
                sp.firstWeek = active.keys.min()!
                sp.lastWeek = active.keys.max()!
            }
            sp.periodCount = DataProcessor.detectFlightPeriods(weeklyData: weekly, speciesName: sp.scientificName)
        }

        // Step 3: Fetch taxa details
        onProgress(GenerationProgress(phase: .fetchingDetails, current: 0, total: nSpecies, message: "Fetching species details..."))
        var taxaDetails: [Int: [String: Any]] = [:]
        let allTaxonIds = speciesList.map { $0.taxonId }
        for i in stride(from: 0, to: allTaxonIds.count, by: 30) {
            let end = min(i + 30, allTaxonIds.count)
            let batch = Array(allTaxonIds[i..<end])
            let results = try await apiClient.getTaxaDetails(taxonIds: batch)
            for t in results {
                guard let id = t["id"] as? Int else { continue }
                taxaDetails[id] = t
            }
            onProgress(GenerationProgress(
                phase: .fetchingDetails,
                current: min(i + 30, allTaxonIds.count),
                total: nSpecies,
                message: "Details: \(min(i + 30, allTaxonIds.count))/\(nSpecies)"
            ))
        }

        // Step 4: Download photos
        var photoMap: [Int: [SpeciesPhoto]] = [:]
        for (idx, sp) in speciesList.enumerated() {
            onProgress(GenerationProgress(
                phase: .downloadingPhotos,
                current: idx + 1,
                total: nSpecies,
                message: "Photos: \(sp.commonName.isEmpty ? sp.scientificName : sp.commonName) (\(idx + 1)/\(nSpecies))"
            ))

            let taxonInfo = taxaDetails[sp.taxonId]
            let photos = extractPhotos(taxonInfo: taxonInfo)
            var spPhotos: [SpeciesPhoto] = []

            // Filter to Creative Commons licensed photos only
            let ccPhotos = photos.filter { photo in
                guard let license = photo["license_code"] as? String else { return false }
                return license.hasPrefix("cc")
            }

            // Fallback to observation photos if taxon photos are insufficient
            let allCcPhotos: [[String: Any]]
            if ccPhotos.count < maxPhotos {
                let obsPhotos = (try? await apiClient.getObservationPhotos(
                    taxonId: sp.taxonId,
                    placeIds: placeIds,
                    qualityGrade: qualityGrade,
                    maxResults: maxPhotos - ccPhotos.count
                )) ?? []
                allCcPhotos = ccPhotos + obsPhotos
            } else {
                allCcPhotos = ccPhotos
            }

            for (pi, photo) in allCcPhotos.prefix(maxPhotos).enumerated() {
                let url = photo["medium_url"] as? String ?? photo["url"] as? String
                guard var photoUrl = url else { continue }
                photoUrl = photoUrl.replacingOccurrences(of: "/square.", with: "/medium.")

                let filename = "\(sp.taxonId)_\(pi).jpg"
                guard let bytes = await apiClient.downloadPhoto(url: photoUrl) else { continue }

                let fileURL = photosDir.appendingPathComponent(filename)
                try? bytes.write(to: fileURL)

                spPhotos.append(SpeciesPhoto(
                    file: filename,
                    attribution: photo["attribution"] as? String,
                    license: photo["license_code"] as? String
                ))
            }
            photoMap[sp.taxonId] = spPhotos
        }

        // Step 5: Build and save dataset
        onProgress(GenerationProgress(phase: .saving, current: 0, total: 1, message: "Saving dataset..."))

        var weeklyBySpecies: [String: [WeeklyEntry]] = [:]
        for row in weekly {
            weeklyBySpecies[row.species, default: []].append(WeeklyEntry(week: row.week, n: row.n, relAbundance: row.relAbundance))
        }

        let speciesEntries: [Species] = speciesList.map { sp in
            let taxonInfo = taxaDetails[sp.taxonId]
            let family = extractAncestorByRank(taxonInfo: taxonInfo, rank: "family")
            let familySci = extractAncestorScientificByRank(taxonInfo: taxonInfo, rank: "family")
            let order = extractAncestorByRank(taxonInfo: taxonInfo, rank: "order")
            let orderSci = extractAncestorScientificByRank(taxonInfo: taxonInfo, rank: "order")

            let conservation = taxonInfo?["conservation_status"] as? [String: Any]
            var description = taxonInfo?["wikipedia_summary"] as? String ?? ""
            description = description.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression).trimmingCharacters(in: .whitespaces)

            var spWeekly = weeklyBySpecies[sp.scientificName] ?? []
            let existingWeeks = Set(spWeekly.map { $0.week })
            for w in 1...53 {
                if !existingWeeks.contains(w) {
                    spWeekly.append(WeeklyEntry(week: w, n: 0, relAbundance: 0))
                }
            }
            spWeekly.sort { $0.week < $1.week }

            return Species(
                taxonId: sp.taxonId,
                scientificName: sp.scientificName,
                commonName: sp.commonName,
                totalObs: sp.totalObs,
                rarity: sp.rarity,
                peakWeek: sp.peakWeek,
                firstWeek: sp.firstWeek,
                lastWeek: sp.lastWeek,
                periodCount: sp.periodCount,
                description: description,
                conservationStatus: conservation?["status"] as? String,
                conservationStatusName: conservation?["status_name"] as? String,
                family: family,
                familyScientific: familySci,
                order: order,
                orderScientific: orderSci,
                photos: photoMap[sp.taxonId] ?? [],
                weekly: spWeekly
            )
        }

        let dataset = Dataset(
            metadata: DatasetMetadata(
                placeName: placeName,
                placeId: placeIds.first ?? 0,
                placeIds: placeIds,
                group: groupName,
                taxonName: taxonName,
                taxonIds: taxonIds.compactMap { $0 },
                totalObs: speciesList.reduce(0) { $0 + $1.totalObs },
                speciesCount: speciesList.count,
                generatedAt: ISO8601DateFormatter().string(from: Date()),
                minObs: minObs,
                qualityGrade: qualityGrade,
                maxPhotos: maxPhotos
            ),
            species: speciesEntries
        )

        let jsonData = try encoder.encode(dataset)
        try jsonData.write(to: outputDir.appendingPathComponent("dataset.json"))

        let finalProgress = GenerationProgress(
            phase: .saving,
            current: 1,
            total: 1,
            message: "Complete! \(speciesEntries.count) species"
        )
        return finalProgress
    }

    // MARK: - Helpers

    private func extractPhotos(taxonInfo: [String: Any]?) -> [[String: Any]] {
        guard let taxonInfo = taxonInfo else { return [] }

        if let taxonPhotos = taxonInfo["taxon_photos"] as? [[String: Any]] {
            let photos = taxonPhotos.compactMap { element -> [String: Any]? in
                return element["photo"] as? [String: Any]
            }
            if !photos.isEmpty { return photos }
        }

        if let defaultPhoto = taxonInfo["default_photo"] as? [String: Any] {
            return [defaultPhoto]
        }

        return []
    }

    private func extractAncestorByRank(taxonInfo: [String: Any]?, rank: String) -> String? {
        guard let ancestors = taxonInfo?["ancestors"] as? [[String: Any]] else { return nil }
        for a in ancestors {
            if a["rank"] as? String == rank {
                return a["preferred_common_name"] as? String ?? a["name"] as? String
            }
        }
        return nil
    }

    private func extractAncestorScientificByRank(taxonInfo: [String: Any]?, rank: String) -> String? {
        guard let ancestors = taxonInfo?["ancestors"] as? [[String: Any]] else { return nil }
        for a in ancestors {
            if a["rank"] as? String == rank {
                return a["name"] as? String
            }
        }
        return nil
    }
}

enum GenerationError: Error, LocalizedError {
    case noSpeciesFound

    var errorDescription: String? {
        switch self {
        case .noSpeciesFound:
            return "No species found matching criteria."
        }
    }
}
