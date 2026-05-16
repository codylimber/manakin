import Foundation

struct PlaceResult: Identifiable, Equatable, Hashable {
    let id: Int
    let name: String
    let adminLevel: Int?

    init(id: Int, name: String, adminLevel: Int? = nil) {
        self.id = id
        self.name = name
        self.adminLevel = adminLevel
    }
}

struct TaxonResult: Identifiable, Equatable, Hashable {
    let id: Int
    let displayName: String
    let scientificName: String
    let commonName: String
    let rank: String
}
