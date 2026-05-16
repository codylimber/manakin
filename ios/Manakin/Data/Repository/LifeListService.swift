import Foundation
import Observation

enum ObservationScope: String {
    case anywhere = "ANYWHERE"
    case here = "HERE"
}

@Observable
class LifeListService {

    private let apiClient: INatApiClient
    private let decoder = JSONDecoder()
    private let encoder = JSONEncoder()

    private var cacheDir: URL {
        let dir = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
            .appendingPathComponent("lifelist")
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    private var prefs: UserDefaults { UserDefaults.standard }

    var username: String {
        get { prefs.string(forKey: "inat_username") ?? "" }
        set { prefs.set(newValue, forKey: "inat_username") }
    }

    var observationScope: ObservationScope {
        get {
            guard let raw = prefs.string(forKey: "observation_scope"),
                  let scope = ObservationScope(rawValue: raw) else {
                return .anywhere
            }
            return scope
        }
        set { prefs.set(newValue.rawValue, forKey: "observation_scope") }
    }

    init(apiClient: INatApiClient) {
        self.apiClient = apiClient
    }

    func refreshForDataset(datasetKey: String, taxonId: Int?, placeId: Int) async {
        let user = username
        guard !user.trimmingCharacters(in: .whitespaces).isEmpty else { return }

        print("[LifeList] Refreshing for '\(datasetKey)', user='\(user)'")

        // Fetch global (seen anywhere)
        let globalIds = await apiClient.getUserSpeciesTaxonIds(username: user, taxonId: taxonId, placeId: nil)
        saveCachedIds(datasetKey: datasetKey, scope: "global", ids: globalIds)
        print("[LifeList] Global: \(globalIds.count) species observed")

        // Fetch local (seen in this place)
        let localIds = await apiClient.getUserSpeciesTaxonIds(username: user, taxonId: taxonId, placeId: placeId)
        saveCachedIds(datasetKey: datasetKey, scope: "local", ids: localIds)
        print("[LifeList] Local (\(placeId)): \(localIds.count) species observed")

        prefs.set(Int(Date().timeIntervalSince1970 * 1000), forKey: "last_sync_time")
    }

    func getObservedGlobal(datasetKey: String) -> Set<Int> {
        loadCachedIds(datasetKey: datasetKey, scope: "global")
    }

    func getObservedLocal(datasetKey: String) -> Set<Int> {
        loadCachedIds(datasetKey: datasetKey, scope: "local")
    }

    func getObservedForScope(datasetKey: String) -> Set<Int> {
        switch observationScope {
        case .anywhere:
            return getObservedGlobal(datasetKey: datasetKey)
        case .here:
            return getObservedLocal(datasetKey: datasetKey)
        }
    }

    func getLastSyncTime() -> Int {
        prefs.integer(forKey: "last_sync_time")
    }

    func hasUsername() -> Bool {
        !username.trimmingCharacters(in: .whitespaces).isEmpty
    }

    // MARK: - Cache

    private func cacheFile(datasetKey: String, scope: String) -> URL {
        let slug = datasetKey.lowercased()
            .replacingOccurrences(of: "[^a-z0-9]+", with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))
        return cacheDir.appendingPathComponent("\(slug)_\(scope).json")
    }

    private func saveCachedIds(datasetKey: String, scope: String, ids: Set<Int>) {
        let file = cacheFile(datasetKey: datasetKey, scope: scope)
        if let data = try? encoder.encode(Array(ids)) {
            try? data.write(to: file)
        }
    }

    private func loadCachedIds(datasetKey: String, scope: String) -> Set<Int> {
        let file = cacheFile(datasetKey: datasetKey, scope: scope)
        guard let data = try? Data(contentsOf: file),
              let ids = try? decoder.decode([Int].self, from: data) else {
            return []
        }
        return Set(ids)
    }
}
