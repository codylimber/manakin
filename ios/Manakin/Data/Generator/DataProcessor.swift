import Foundation

class RawSpecies {
    let scientificName: String
    let commonName: String
    let taxonId: Int
    var totalObs: Int
    var rarity: String = ""
    var peakWeek: Int = 0
    var firstWeek: Int = 0
    var lastWeek: Int = 0
    var periodCount: Int = 1

    init(scientificName: String, commonName: String, taxonId: Int, totalObs: Int) {
        self.scientificName = scientificName
        self.commonName = commonName
        self.taxonId = taxonId
        self.totalObs = totalObs
    }
}

struct WeeklyRow {
    let species: String
    let week: Int
    let n: Int
    let relAbundance: Float
}

enum DataProcessor {

    static func buildSpeciesList(rawCounts: [[String: Any]]) -> [RawSpecies] {
        let species = rawCounts.compactMap { item -> RawSpecies? in
            guard let taxon = item["taxon"] as? [String: Any],
                  let id = taxon["id"] as? Int else {
                return nil
            }
            let scientificName = taxon["name"] as? String ?? "Unknown"
            let commonName = taxon["preferred_common_name"] as? String ?? ""
            let totalObs = item["count"] as? Int ?? 0
            return RawSpecies(scientificName: scientificName, commonName: commonName, taxonId: id, totalObs: totalObs)
        }
        computeRarity(species: species)
        return species
    }

    static func computeRarity(species: [RawSpecies]) {
        if species.count <= 3 {
            for sp in species {
                sp.rarity = "Uncommon"
            }
            return
        }
        let counts = species.map { $0.totalObs }.sorted()
        let n = counts.count
        let q25 = counts[n / 4]
        let q75 = counts[3 * n / 4]
        for sp in species {
            if sp.totalObs >= q75 {
                sp.rarity = "Common"
            } else if sp.totalObs >= q25 {
                sp.rarity = "Uncommon"
            } else {
                sp.rarity = "Rare"
            }
        }
    }

    static func buildWeeklyMatrix(histograms: [String: [Int: Int]]) -> [WeeklyRow] {
        var rows: [WeeklyRow] = []
        for (speciesName, weekCounts) in histograms {
            let maxCount = max(weekCounts.values.max() ?? 1, 1)
            for (week, count) in weekCounts {
                if week >= 1 && week <= 53 {
                    let relAbundance = Float(Int(Float(count) / Float(maxCount) * 10000)) / 10000.0
                    rows.append(WeeklyRow(
                        species: speciesName,
                        week: week,
                        n: count,
                        relAbundance: relAbundance
                    ))
                }
            }
        }
        return rows
    }

    static func detectFlightPeriods(weeklyData: [WeeklyRow], speciesName: String, gapWeeks: Int = 3) -> Int {
        let activeWeeks = weeklyData
            .filter { $0.species == speciesName && $0.n > 0 }
            .map { $0.week }
            .sorted()

        if activeWeeks.isEmpty { return 0 }

        var periods = 1
        for i in 1..<activeWeeks.count {
            if activeWeeks[i] - activeWeeks[i - 1] > gapWeeks {
                periods += 1
            }
        }
        // Check for year-wrapping: if first and last active weeks connect across the boundary
        if periods > 1 && activeWeeks.count >= 2 {
            let wrapGap = (53 - activeWeeks.last!) + activeWeeks.first!
            if wrapGap <= gapWeeks {
                periods -= 1 // First and last periods are actually one continuous period
            }
        }
        return periods
    }
}
