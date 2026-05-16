import Foundation
import CryptoKit

actor INatApiClient {

    static let baseURL = "https://api.inaturalist.org/v1"
    static let dataIntervalNs: UInt64 = 2_000_000_000 // 2s
    static let interactiveIntervalNs: UInt64 = 500_000_000 // 500ms
    static let maxRetries = 5
    static let retryStatuses: Set<Int> = [429, 500, 502, 503, 504]
    private static let maxCacheSize = 500

    private let session: URLSession
    private var lastRequestTime: Date = .distantPast
    private var cache: OrderedDictionary<String, [String: Any]> = OrderedDictionary()

    init() {
        let config = URLSessionConfiguration.default
        config.httpAdditionalHeaders = [
            "User-Agent": "Manakin-iOS/1.0"
        ]
        self.session = URLSession(configuration: config)
    }

    // MARK: - Throttle & Cache

    private func throttle(intervalNs: UInt64) async {
        let elapsed = Date().timeIntervalSince(lastRequestTime)
        let intervalSec = Double(intervalNs) / 1_000_000_000.0
        if elapsed < intervalSec {
            let waitNs = UInt64((intervalSec - elapsed) * 1_000_000_000)
            try? await Task.sleep(nanoseconds: waitNs)
        }
        lastRequestTime = Date()
    }

    private func cacheKey(endpoint: String, params: [String: String]) -> String {
        let raw = endpoint + "|" + params.sorted(by: { $0.key < $1.key }).map { "\($0.key)=\($0.value)" }.joined(separator: ",")
        let digest = Insecure.MD5.hash(data: Data(raw.utf8))
        return digest.map { String(format: "%02x", $0) }.joined()
    }

    // MARK: - Core GET

    private func get(
        endpoint: String,
        params: [String: String] = [:],
        intervalNs: UInt64 = dataIntervalNs,
        useCache: Bool = true
    ) async throws -> [String: Any] {
        if useCache {
            let key = cacheKey(endpoint: endpoint, params: params)
            if let cached = cache[key] {
                return cached
            }
        }

        var urlString = "\(Self.baseURL)/\(endpoint)"
        if !params.isEmpty {
            let queryItems = params.map { key, value in
                "\(key.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? key)=\(value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value)"
            }
            urlString += "?" + queryItems.joined(separator: "&")
        }

        guard let url = URL(string: urlString) else {
            throw INatError.invalidURL(urlString)
        }

        var lastStatus = 0
        for attempt in 0..<Self.maxRetries {
            await throttle(intervalNs: intervalNs)

            do {
                let (data, response) = try await session.data(from: url)
                let httpResponse = response as? HTTPURLResponse
                lastStatus = httpResponse?.statusCode ?? 0

                if lastStatus >= 200 && lastStatus < 300 {
                    guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
                        throw INatError.invalidResponse
                    }
                    if useCache {
                        let key = cacheKey(endpoint: endpoint, params: params)
                        cache[key] = json
                        while cache.count > Self.maxCacheSize {
                            cache.removeFirst()
                        }
                    }
                    return json
                } else if Self.retryStatuses.contains(lastStatus) {
                    let backoff = 5000 * (1 << attempt)
                    try await Task.sleep(nanoseconds: UInt64(backoff) * 1_000_000)
                    continue
                } else {
                    throw INatError.httpError(lastStatus)
                }
            } catch let error as INatError {
                throw error
            } catch is CancellationError {
                throw CancellationError()
            } catch {
                if attempt < Self.maxRetries - 1 {
                    let backoff = 5000 * (1 << attempt)
                    try await Task.sleep(nanoseconds: UInt64(backoff) * 1_000_000)
                    continue
                }
                throw INatError.networkError("Network error after \(Self.maxRetries) retries: \(error.localizedDescription)")
            }
        }
        throw INatError.networkError("Failed after \(Self.maxRetries) retries (last status: \(lastStatus))")
    }

    // MARK: - Interactive Endpoints

    func searchPlaces(query: String) async throws -> [PlaceResult] {
        let data = try await get(
            endpoint: "places/autocomplete",
            params: ["q": query, "per_page": "10"],
            intervalNs: Self.interactiveIntervalNs,
            useCache: false
        )
        guard let results = data["results"] as? [[String: Any]] else { return [] }
        return results.compactMap { obj in
            guard let id = obj["id"] as? Int,
                  let name = obj["display_name"] as? String else { return nil }
            let adminLevel = obj["admin_level"] as? Int
            return PlaceResult(id: id, name: name, adminLevel: adminLevel)
        }
    }

    func searchTaxa(query: String) async throws -> [TaxonResult] {
        let data = try await get(
            endpoint: "taxa/autocomplete",
            params: ["q": query, "per_page": "10"],
            intervalNs: Self.interactiveIntervalNs,
            useCache: false
        )
        guard let results = data["results"] as? [[String: Any]] else { return [] }
        return results.compactMap { obj in
            guard let id = obj["id"] as? Int else { return nil }
            let sci = obj["name"] as? String ?? ""
            let common = obj["preferred_common_name"] as? String ?? ""
            let rank = obj["rank"] as? String ?? ""
            let display: String
            if !common.isEmpty && common != sci {
                display = "\(common) (\(sci))"
            } else {
                display = sci
            }
            let displayWithRank = rank.isEmpty ? display : "\(display) [\(rank)]"
            return TaxonResult(id: id, displayName: displayWithRank, scientificName: sci, commonName: common, rank: rank)
        }
    }

    func getObservationPhotos(
        taxonId: Int,
        placeIds: [Int],
        qualityGrade: String = "research",
        maxResults: Int = 5
    ) async throws -> [[String: Any]] {
        var params: [String: String] = [
            "taxon_id": String(taxonId),
            "quality_grade": qualityGrade,
            "photos": "true",
            "photo_licensed": "true",
            "per_page": String(maxResults),
            "order_by": "votes"
        ]
        if !placeIds.isEmpty {
            params["place_id"] = placeIds.map { String($0) }.joined(separator: ",")
        }
        let data = try await get(endpoint: "observations", params: params, intervalNs: Self.interactiveIntervalNs)
        guard let results = data["results"] as? [[String: Any]] else { return [] }
        var photos: [[String: Any]] = []
        for obs in results {
            guard let obsPhotos = obs["photos"] as? [[String: Any]] else { continue }
            for photo in obsPhotos {
                guard let license = photo["license_code"] as? String,
                      license.hasPrefix("cc") else { continue }
                photos.append(photo)
            }
        }
        return photos
    }

    func getSpeciesCountEstimate(
        taxonId: Int?,
        placeIds: [Int],
        qualityGrade: String = "research"
    ) async throws -> Int {
        var total = 0
        for placeId in placeIds {
            let (count, _) = try await getSpeciesCounts(taxonId: taxonId, placeId: placeId, qualityGrade: qualityGrade, page: 1, perPage: 1)
            total += count
        }
        return total
    }

    // MARK: - Data Endpoints

    func getSpeciesCounts(
        taxonId: Int?,
        placeId: Int,
        qualityGrade: String = "research",
        page: Int = 1,
        perPage: Int = 200
    ) async throws -> (totalResults: Int, results: [[String: Any]]) {
        var params: [String: String] = [
            "quality_grade": qualityGrade,
            "place_id": String(placeId),
            "page": String(page),
            "per_page": String(perPage)
        ]
        if let taxonId = taxonId {
            params["taxon_id"] = String(taxonId)
        }

        let data = try await get(endpoint: "observations/species_counts", params: params)
        let totalResults = data["total_results"] as? Int ?? 0
        let results = data["results"] as? [[String: Any]] ?? []
        return (totalResults, results)
    }

    func getHistogram(
        taxonId: Int,
        placeId: Int,
        qualityGrade: String = "research"
    ) async throws -> [Int: Int] {
        let data = try await get(endpoint: "observations/histogram", params: [
            "taxon_id": String(taxonId),
            "place_id": String(placeId),
            "quality_grade": qualityGrade,
            "date_field": "observed",
            "interval": "week_of_year"
        ])
        guard let results = data["results"] as? [String: Any],
              let weekMap = results["week_of_year"] as? [String: Any] else {
            return [:]
        }
        var histogram: [Int: Int] = [:]
        for (key, value) in weekMap {
            guard let week = Int(key), week >= 1, week <= 53 else { continue }
            let count: Int
            if let intVal = value as? Int {
                count = intVal
            } else if let doubleVal = value as? Double {
                count = Int(doubleVal)
            } else {
                count = 0
            }
            histogram[week] = count
        }
        return histogram
    }

    func getTaxaDetails(taxonIds: [Int]) async throws -> [[String: Any]] {
        let idsStr = taxonIds.prefix(30).map { String($0) }.joined(separator: ",")
        let data = try await get(endpoint: "taxa/\(idsStr)")
        return data["results"] as? [[String: Any]] ?? []
    }

    func downloadPhoto(url: String) async -> Data? {
        guard let photoURL = URL(string: url) else { return nil }
        do {
            let (data, response) = try await session.data(from: photoURL)
            let httpResponse = response as? HTTPURLResponse
            guard let status = httpResponse?.statusCode, status >= 200, status < 300 else { return nil }
            return data
        } catch {
            return nil
        }
    }

    func getUserSpeciesTaxonIds(
        username: String,
        taxonId: Int?,
        placeId: Int?
    ) async -> Set<Int> {
        var allIds: Set<Int> = []
        var page = 1
        while true {
            var params: [String: String] = [
                "user_id": username,
                "quality_grade": "research",
                "page": String(page),
                "per_page": "200"
            ]
            if let taxonId = taxonId {
                params["taxon_id"] = String(taxonId)
            }
            if let placeId = placeId {
                params["place_id"] = String(placeId)
            }

            guard let data = try? await get(endpoint: "observations/species_counts", params: params) else { break }
            let totalResults = data["total_results"] as? Int ?? 0
            guard let results = data["results"] as? [[String: Any]] else { break }

            for r in results {
                if let taxon = r["taxon"] as? [String: Any],
                   let tid = taxon["id"] as? Int {
                    allIds.insert(tid)
                }
            }
            if allIds.count >= totalResults || results.isEmpty { break }
            page += 1
        }
        return allIds
    }
}

// MARK: - Error Type

enum INatError: Error {
    case invalidURL(String)
    case invalidResponse
    case httpError(Int)
    case networkError(String)
}

// MARK: - OrderedDictionary (LRU Cache)

class OrderedDictionary<Key: Hashable, Value> {
    private var keys: [Key] = []
    private var dict: [Key: Value] = [:]

    var count: Int { keys.count }

    subscript(key: Key) -> Value? {
        get {
            guard let value = dict[key] else { return nil }
            // Move to end (most recently used)
            if let index = keys.firstIndex(of: key) {
                keys.remove(at: index)
                keys.append(key)
            }
            return value
        }
        set {
            if let newValue = newValue {
                if dict[key] == nil {
                    keys.append(key)
                } else {
                    // Move to end
                    if let index = keys.firstIndex(of: key) {
                        keys.remove(at: index)
                        keys.append(key)
                    }
                }
                dict[key] = newValue
            } else {
                dict.removeValue(forKey: key)
                keys.removeAll { $0 == key }
            }
        }
    }

    func removeFirst() {
        guard let first = keys.first else { return }
        keys.removeFirst()
        dict.removeValue(forKey: first)
    }
}
