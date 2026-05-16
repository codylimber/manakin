import Foundation

enum AppRoute: Hashable {
    case speciesDetail(taxonId: Int)
    case addDataset
    case generating
    case compare
    case help
    case about
    case timeline
    case tripReport
}

struct GenerationParams {
    let placeIds: [Int]
    let placeName: String
    let taxonIds: [Int?]
    let taxonName: String
    let groupName: String
    let minObs: Int
    let qualityGrade: String
    let maxPhotos: Int

    init(
        placeIds: [Int],
        placeName: String,
        taxonIds: [Int?],
        taxonName: String,
        groupName: String,
        minObs: Int,
        qualityGrade: String = "research",
        maxPhotos: Int = 3
    ) {
        self.placeIds = placeIds
        self.placeName = placeName
        self.taxonIds = taxonIds
        self.taxonName = taxonName
        self.groupName = groupName
        self.minObs = minObs
        self.qualityGrade = qualityGrade
        self.maxPhotos = maxPhotos
    }

    static var current: GenerationParams?
}
