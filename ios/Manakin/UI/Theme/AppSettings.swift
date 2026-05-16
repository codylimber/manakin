import Foundation

@Observable
final class AppSettings {
    static let shared = AppSettings()

    var useScientificNames: Bool {
        didSet { defaults.set(useScientificNames, forKey: "useScientificNames") }
    }
    var minActivityPercent: Int {
        didSet { defaults.set(minActivityPercent, forKey: "minActivityPercent") }
    }
    var defaultSortMode: SortMode {
        didSet { defaults.set(defaultSortMode.rawValue, forKey: "defaultSortMode") }
    }
    var favorites: Set<Int> {
        didSet { defaults.set(Array(favorites), forKey: "favorites") }
    }
    var selectedDatasetKeys: Set<String> {
        didSet { defaults.set(Array(selectedDatasetKeys), forKey: "selectedDatasetKeys") }
    }
    var showActiveOnly: Bool {
        didSet { defaults.set(showActiveOnly, forKey: "showActiveOnly") }
    }
    var targetMode: String {
        didSet { defaults.set(targetMode, forKey: "targetMode") }
    }
    var weeklyDigestEnabled: Bool {
        didSet { defaults.set(weeklyDigestEnabled, forKey: "weeklyDigestEnabled") }
    }
    var digestDays: Set<Int> {
        didSet { defaults.set(Array(digestDays), forKey: "digestDays") }
    }
    var digestHour: Int {
        didSet { defaults.set(digestHour, forKey: "digestHour") }
    }
    var targetNotificationsEnabled: Bool {
        didSet { defaults.set(targetNotificationsEnabled, forKey: "targetNotificationsEnabled") }
    }
    var notificationDatasetKeys: Set<String> {
        didSet { defaults.set(Array(notificationDatasetKeys), forKey: "notificationDatasetKeys") }
    }

    private let defaults = UserDefaults.standard

    private init() {
        self.useScientificNames = defaults.bool(forKey: "useScientificNames")
        self.minActivityPercent = defaults.object(forKey: "minActivityPercent") as? Int ?? 0
        let sortRaw = defaults.string(forKey: "defaultSortMode") ?? "likelihood"
        self.defaultSortMode = SortMode(rawValue: sortRaw) ?? .likelihood
        let favArray = defaults.array(forKey: "favorites") as? [Int] ?? []
        self.favorites = Set(favArray)
        let dsKeys = defaults.array(forKey: "selectedDatasetKeys") as? [String] ?? []
        self.selectedDatasetKeys = Set(dsKeys)
        self.showActiveOnly = defaults.object(forKey: "showActiveOnly") as? Bool ?? true
        self.targetMode = defaults.string(forKey: "targetMode") ?? "STARRED"
        self.weeklyDigestEnabled = defaults.bool(forKey: "weeklyDigestEnabled")
        let daysArray = defaults.array(forKey: "digestDays") as? [Int] ?? [2]
        self.digestDays = Set(daysArray)
        self.digestHour = defaults.object(forKey: "digestHour") as? Int ?? 8
        self.targetNotificationsEnabled = defaults.bool(forKey: "targetNotificationsEnabled")
        let notifKeys = defaults.array(forKey: "notificationDatasetKeys") as? [String] ?? []
        self.notificationDatasetKeys = Set(notifKeys)
    }

    func toggleFavorite(_ taxonId: Int) {
        if favorites.contains(taxonId) {
            favorites.remove(taxonId)
        } else {
            favorites.insert(taxonId)
        }
    }

    func isFavorite(_ taxonId: Int) -> Bool {
        favorites.contains(taxonId)
    }
}

extension SortMode: RawRepresentable {
    public init?(rawValue: String) {
        switch rawValue {
        case "likelihood": self = .likelihood
        case "peakDate": self = .peakDate
        case "name": self = .name
        case "taxonomy": self = .taxonomy
        default: return nil
        }
    }

    public var rawValue: String {
        switch self {
        case .likelihood: return "likelihood"
        case .peakDate: return "peakDate"
        case .name: return "name"
        case .taxonomy: return "taxonomy"
        }
    }
}
