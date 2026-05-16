import Foundation

struct Dataset: Codable {
    let metadata: DatasetMetadata
    let species: [Species]
}

struct DatasetMetadata: Codable {
    let placeName: String
    let placeId: Int
    let placeIds: [Int]
    let group: String
    let taxonName: String
    let taxonIds: [Int]
    let totalObs: Int
    let speciesCount: Int
    let generatedAt: String
    let minObs: Int
    let qualityGrade: String
    let maxPhotos: Int

    init(
        placeName: String,
        placeId: Int,
        placeIds: [Int] = [],
        group: String,
        taxonName: String,
        taxonIds: [Int] = [],
        totalObs: Int,
        speciesCount: Int,
        generatedAt: String,
        minObs: Int = 10,
        qualityGrade: String = "research",
        maxPhotos: Int = 3
    ) {
        self.placeName = placeName
        self.placeId = placeId
        self.placeIds = placeIds
        self.group = group
        self.taxonName = taxonName
        self.taxonIds = taxonIds
        self.totalObs = totalObs
        self.speciesCount = speciesCount
        self.generatedAt = generatedAt
        self.minObs = minObs
        self.qualityGrade = qualityGrade
        self.maxPhotos = maxPhotos
    }
}

struct Species: Codable {
    let taxonId: Int
    let scientificName: String
    let commonName: String
    let totalObs: Int
    let rarity: String
    let peakWeek: Int
    let firstWeek: Int
    let lastWeek: Int
    let periodCount: Int
    let description: String
    let conservationStatus: String?
    let conservationStatusName: String?
    let family: String?
    let familyScientific: String?
    let order: String?
    let orderScientific: String?
    let photos: [SpeciesPhoto]
    let weekly: [WeeklyEntry]

    init(
        taxonId: Int,
        scientificName: String,
        commonName: String = "",
        totalObs: Int,
        rarity: String,
        peakWeek: Int,
        firstWeek: Int,
        lastWeek: Int,
        periodCount: Int,
        description: String = "",
        conservationStatus: String? = nil,
        conservationStatusName: String? = nil,
        family: String? = nil,
        familyScientific: String? = nil,
        order: String? = nil,
        orderScientific: String? = nil,
        photos: [SpeciesPhoto] = [],
        weekly: [WeeklyEntry] = []
    ) {
        self.taxonId = taxonId
        self.scientificName = scientificName
        self.commonName = commonName
        self.totalObs = totalObs
        self.rarity = rarity
        self.peakWeek = peakWeek
        self.firstWeek = firstWeek
        self.lastWeek = lastWeek
        self.periodCount = periodCount
        self.description = description
        self.conservationStatus = conservationStatus
        self.conservationStatusName = conservationStatusName
        self.family = family
        self.familyScientific = familyScientific
        self.order = order
        self.orderScientific = orderScientific
        self.photos = photos
        self.weekly = weekly
    }
}

struct SpeciesPhoto: Codable {
    let file: String
    let attribution: String?
    let license: String?

    init(file: String, attribution: String? = nil, license: String? = nil) {
        self.file = file
        self.attribution = attribution
        self.license = license
    }
}

struct WeeklyEntry: Codable {
    let week: Int
    let n: Int
    let relAbundance: Float
}

enum SpeciesStatus {
    case peak, active, early, late, inactive

    static func classify(species: Species, currentWeek: Int) -> SpeciesStatus {
        guard let entry = species.weekly.first(where: { $0.week == currentWeek }),
              entry.n > 0 else {
            return .inactive
        }
        if entry.relAbundance >= 0.8 {
            return .peak
        } else if entry.relAbundance >= 0.2 {
            return .active
        } else {
            // Handle year-wrapping: compute shortest distance to peak in either direction
            let forwardToPeak = (species.peakWeek - currentWeek + 53) % 53
            let backFromPeak = (currentWeek - species.peakWeek + 53) % 53
            return forwardToPeak <= backFromPeak ? .early : .late
        }
    }
}

enum SortMode {
    case likelihood, peakDate, name, taxonomy
}

enum ObservationFilter {
    case all, observed, notObserved, favorites
}
