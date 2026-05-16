import Foundation
import Observation

enum DatasetSource {
    case asset, `internal`
}

struct DatasetInfo {
    let key: String
    let group: String
    let placeName: String
    let speciesCount: Int
    let generatedAt: String
    let source: DatasetSource
    let sizeBytes: Int64

    init(key: String, group: String, placeName: String, speciesCount: Int, generatedAt: String, source: DatasetSource, sizeBytes: Int64 = 0) {
        self.key = key
        self.group = group
        self.placeName = placeName
        self.speciesCount = speciesCount
        self.generatedAt = generatedAt
        self.source = source
        self.sizeBytes = sizeBytes
    }

    var sizeDisplay: String {
        let mb = Double(sizeBytes) / (1024.0 * 1024.0)
        if mb >= 1.0 {
            return String(format: "%.1f MB", mb)
        } else {
            return String(format: "%.0f KB", Double(sizeBytes) / 1024.0)
        }
    }
}

@Observable
class PhenologyRepository {

    private var datasets: [String: Dataset] = [:]
    private var keyDirNames: [String: String] = [:]
    private var keySources: [String: DatasetSource] = [:]
    private var hiddenKeys: Set<String> = []
    private var bundleDatasetsBasePath: String?
    private var loaded = false

    private let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .useDefaultKeys
        return d
    }()

    private let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.outputFormatting = [.sortedKeys]
        return e
    }()

    private var prefs: UserDefaults { UserDefaults.standard }

    private func datasetKey(group: String, placeName: String) -> String {
        "\(group) — \(placeName)"
    }

    private var internalDatasetsURL: URL {
        let docs = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        return docs.appendingPathComponent("datasets")
    }

    func loadDatasets() {
        if loaded { return }
        datasets.removeAll()
        keyDirNames.removeAll()
        keySources.removeAll()
        hiddenKeys = Set(prefs.stringArray(forKey: "hidden_datasets") ?? [])

        // Load from bundle assets
        let fm = FileManager.default
        var bundleDatasetsPath: String?
        // Try folder reference path first (XcodeGen type: folder)
        if let url = Bundle.main.url(forResource: "datasets", withExtension: nil) {
            bundleDatasetsPath = url.path
            print("[PhenologyRepo] Found datasets via url(forResource): \(url.path)")
        }
        // Fallback: try resourcePath/datasets
        if bundleDatasetsPath == nil, let rp = Bundle.main.resourcePath {
            let candidate = (rp as NSString).appendingPathComponent("datasets")
            if fm.fileExists(atPath: candidate) {
                bundleDatasetsPath = candidate
                print("[PhenologyRepo] Found datasets at resourcePath: \(candidate)")
            } else {
                print("[PhenologyRepo] No datasets at resourcePath: \(candidate)")
            }
        }
        if bundleDatasetsPath == nil {
            print("[PhenologyRepo] WARNING: No bundled datasets found!")
            // List what's in the bundle to debug
            if let rp = Bundle.main.resourcePath {
                let items = (try? fm.contentsOfDirectory(atPath: rp)) ?? []
                print("[PhenologyRepo] Bundle contents: \(items)")
            }
        }
        if let datasetsPath = bundleDatasetsPath,
           let dirs = try? fm.contentsOfDirectory(atPath: datasetsPath) {
            for dir in dirs {
                let jsonPath = (datasetsPath as NSString).appendingPathComponent("\(dir)/dataset.json")
                guard let data = fm.contents(atPath: jsonPath) else { continue }
                do {
                    let dataset = try decoder.decode(Dataset.self, from: data)
                    let key = datasetKey(group: dataset.metadata.group, placeName: dataset.metadata.placeName)
                    if !hiddenKeys.contains(key) {
                        datasets[key] = dataset
                        keyDirNames[key] = dir
                        keySources[key] = .asset
                        bundleDatasetsBasePath = datasetsPath
                    }
                    print("[PhenologyRepo] Loaded asset '\(key)': \(dataset.species.count) species")
                } catch {
                    print("[PhenologyRepo] Failed to load asset \(jsonPath): \(error)")
                }
            }
        }

        // Load from internal storage (documents directory)
        let internalDir = internalDatasetsURL
        if let dirs = try? fm.contentsOfDirectory(atPath: internalDir.path) {
            for dir in dirs {
                let dirURL = internalDir.appendingPathComponent(dir)
                var isDir: ObjCBool = false
                guard fm.fileExists(atPath: dirURL.path, isDirectory: &isDir), isDir.boolValue else { continue }
                let jsonURL = dirURL.appendingPathComponent("dataset.json")
                guard let data = try? Data(contentsOf: jsonURL) else { continue }
                do {
                    let dataset = try decoder.decode(Dataset.self, from: data)
                    let key = datasetKey(group: dataset.metadata.group, placeName: dataset.metadata.placeName)
                    datasets[key] = dataset
                    keyDirNames[key] = dir
                    keySources[key] = .internal
                } catch {
                    print("[PhenologyRepo] Failed to load internal \(dir): \(error)")
                }
            }
        }

        loaded = true
    }

    func reloadDatasets() {
        loaded = false
        loadDatasets()
    }

    func loadDatasetsAsync() async {
        loadDatasets()
    }

    func reloadDatasetsAsync() async {
        reloadDatasets()
    }

    func getKeys() -> [String] {
        datasets.keys.sorted()
    }

    func getDataset(key: String) -> Dataset? {
        datasets[key]
    }

    func getGroupName(key: String) -> String {
        datasets[key]?.metadata.group ?? key
    }

    func getPlaceNameForKey(key: String) -> String {
        datasets[key]?.metadata.placeName ?? ""
    }

    func getTaxonGroup(key: String) -> String {
        guard let meta = datasets[key]?.metadata else { return "" }
        if !meta.taxonIds.isEmpty {
            return meta.taxonIds.sorted().map { String($0) }.joined(separator: ",")
        }
        return meta.taxonName
    }

    func getSpeciesForKey(key: String) -> [Species] {
        datasets[key]?.species ?? []
    }

    func getSpeciesById(taxonId: Int) -> Species? {
        datasets.values.flatMap { $0.species }.first { $0.taxonId == taxonId }
    }

    func getKeyForSpecies(taxonId: Int) -> String? {
        datasets.first { (_, ds) in ds.species.contains { $0.taxonId == taxonId } }?.key
    }

    func getPhotoURL(key: String, filename: String) -> URL? {
        let source = keySources[key] ?? .asset
        let dir = keyDirNames[key] ?? ""
        switch source {
        case .asset:
            if let basePath = bundleDatasetsBasePath {
                let path = (basePath as NSString).appendingPathComponent("\(dir)/photos/\(filename)")
                return URL(fileURLWithPath: path)
            }
            return nil
        case .internal:
            return internalDatasetsURL.appendingPathComponent("\(dir)/photos/\(filename)")
        }
    }

    func getAllDatasets() -> [DatasetInfo] {
        datasets.map { (key, ds) in
            let source = keySources[key] ?? .asset
            let dir = keyDirNames[key] ?? ""
            let size: Int64
            switch source {
            case .internal:
                size = directorySize(at: internalDatasetsURL.appendingPathComponent(dir))
            case .asset:
                size = 0
            }
            return DatasetInfo(
                key: key,
                group: ds.metadata.group,
                placeName: ds.metadata.placeName,
                speciesCount: ds.metadata.speciesCount,
                generatedAt: ds.metadata.generatedAt,
                source: source,
                sizeBytes: size
            )
        }
    }

    func deleteDataset(key: String) {
        let source = keySources[key]
        if source == .internal {
            guard let dir = keyDirNames[key] else { return }
            let dirURL = internalDatasetsURL.appendingPathComponent(dir)
            try? FileManager.default.removeItem(at: dirURL)
        } else if source == .asset {
            hiddenKeys.insert(key)
            prefs.set(Array(hiddenKeys), forKey: "hidden_datasets")
        }
        datasets.removeValue(forKey: key)
        keyDirNames.removeValue(forKey: key)
        keySources.removeValue(forKey: key)
    }

    func exportDataset(key: String) -> URL? {
        guard let source = keySources[key], source == .internal else { return nil }
        guard let dir = keyDirNames[key] else { return nil }
        let sourceDir = internalDatasetsURL.appendingPathComponent(dir)
        guard FileManager.default.fileExists(atPath: sourceDir.path) else { return nil }

        let exportsDir = FileManager.default.temporaryDirectory.appendingPathComponent("exports")
        try? FileManager.default.createDirectory(at: exportsDir, withIntermediateDirectories: true)
        let zipURL = exportsDir.appendingPathComponent("\(dir).manakin")

        // Remove old export if exists
        try? FileManager.default.removeItem(at: zipURL)

        guard createZip(at: zipURL, fromDirectory: sourceDir, baseDir: sourceDir) else { return nil }
        return zipURL
    }

    func exportBundle(keys: [String], bundleName: String) -> URL? {
        let exportsDir = FileManager.default.temporaryDirectory.appendingPathComponent("exports")
        try? FileManager.default.createDirectory(at: exportsDir, withIntermediateDirectories: true)
        let zipURL = exportsDir.appendingPathComponent("\(bundleName).manakin-bundle")
        try? FileManager.default.removeItem(at: zipURL)

        let tempDir = FileManager.default.temporaryDirectory.appendingPathComponent("bundle_\(UUID().uuidString)")
        try? FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)

        var anyContent = false
        for key in keys {
            guard let source = keySources[key], source == .internal else { continue }
            guard let dir = keyDirNames[key] else { continue }
            let sourceDir = internalDatasetsURL.appendingPathComponent(dir)
            guard FileManager.default.fileExists(atPath: sourceDir.path) else { continue }

            let destDir = tempDir.appendingPathComponent(dir)
            do {
                try FileManager.default.copyItem(at: sourceDir, to: destDir)
                anyContent = true
            } catch {
                print("[PhenologyRepo] Bundle export copy failed for \(dir): \(error)")
            }
        }

        guard anyContent else {
            try? FileManager.default.removeItem(at: tempDir)
            return nil
        }
        guard createZip(at: zipURL, fromDirectory: tempDir, baseDir: tempDir) else {
            try? FileManager.default.removeItem(at: tempDir)
            return nil
        }
        try? FileManager.default.removeItem(at: tempDir)
        return zipURL
    }

    func importDataset(from fileURL: URL) -> Bool {
        do {
            let tempDir = FileManager.default.temporaryDirectory
                .appendingPathComponent("import_temp_\(Int(Date().timeIntervalSince1970 * 1000))")
            try FileManager.default.createDirectory(at: tempDir, withIntermediateDirectories: true)

            guard unzip(fileAt: fileURL, to: tempDir) else {
                try? FileManager.default.removeItem(at: tempDir)
                return false
            }

            // Check if this is a single dataset or a bundle
            let topLevelJson = tempDir.appendingPathComponent("dataset.json")
            if FileManager.default.fileExists(atPath: topLevelJson.path) {
                // Single dataset
                let success = importSingleDataset(from: tempDir)
                if !success {
                    try? FileManager.default.removeItem(at: tempDir)
                    return false
                }
            } else {
                // Bundle - each subdirectory is a separate dataset
                var anySuccess = false
                let contents = try FileManager.default.contentsOfDirectory(atPath: tempDir.path)
                for item in contents {
                    let subDir = tempDir.appendingPathComponent(item)
                    var isDir: ObjCBool = false
                    guard FileManager.default.fileExists(atPath: subDir.path, isDirectory: &isDir),
                          isDir.boolValue else { continue }
                    let jsonFile = subDir.appendingPathComponent("dataset.json")
                    guard FileManager.default.fileExists(atPath: jsonFile.path) else { continue }
                    if importSingleDataset(from: subDir) {
                        anySuccess = true
                    }
                }
                try? FileManager.default.removeItem(at: tempDir)
                if !anySuccess { return false }
            }

            reloadDatasets()
            return true
        } catch {
            print("[PhenologyRepo] Import failed: \(error)")
            return false
        }
    }

    private func importSingleDataset(from tempDir: URL) -> Bool {
        let jsonFile = tempDir.appendingPathComponent("dataset.json")
        guard let data = try? Data(contentsOf: jsonFile),
              let dataset = try? decoder.decode(Dataset.self, from: data) else {
            return false
        }

        let slug = "\(dataset.metadata.group)-\(dataset.metadata.placeName)"
            .lowercased()
            .replacingOccurrences(of: "[^a-z0-9]+", with: "-", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: "-"))

        let destDir = internalDatasetsURL.appendingPathComponent(slug)
        try? FileManager.default.removeItem(at: destDir)
        do {
            try FileManager.default.createDirectory(at: internalDatasetsURL, withIntermediateDirectories: true)
            try FileManager.default.copyItem(at: tempDir, to: destDir)
            try? FileManager.default.removeItem(at: tempDir)
            return true
        } catch {
            print("[PhenologyRepo] Import single dataset failed: \(error)")
            return false
        }
    }

    func mergeAllDatasets() -> String? {
        var allSpecies: [Int: Species] = [:]
        var placeName = ""
        let group = "All Species"

        for key in getKeys() {
            guard let ds = datasets[key] else { continue }
            if placeName.isEmpty {
                placeName = ds.metadata.placeName
            } else if !placeName.contains(ds.metadata.placeName) {
                placeName += ", \(ds.metadata.placeName)"
            }
            for sp in ds.species {
                if allSpecies[sp.taxonId] == nil {
                    allSpecies[sp.taxonId] = sp
                }
            }
        }

        if allSpecies.isEmpty { return nil }

        let merged = Dataset(
            metadata: DatasetMetadata(
                placeName: placeName,
                placeId: datasets.values.first?.metadata.placeId ?? 0,
                group: group,
                taxonName: "Merged",
                totalObs: allSpecies.values.reduce(0) { $0 + $1.totalObs },
                speciesCount: allSpecies.count,
                generatedAt: ISO8601DateFormatter().string(from: Date())
            ),
            species: Array(allSpecies.values)
        )

        let slug = "all-species-merged"
        let outputDir = internalDatasetsURL.appendingPathComponent(slug)
        let photosDir = outputDir.appendingPathComponent("photos")
        try? FileManager.default.createDirectory(at: photosDir, withIntermediateDirectories: true)

        do {
            let jsonData = try encoder.encode(merged)
            try jsonData.write(to: outputDir.appendingPathComponent("dataset.json"))
        } catch {
            print("[PhenologyRepo] Merge save failed: \(error)")
            return nil
        }

        // Copy photos from source datasets
        for key in getKeys() {
            guard let source = keySources[key], let dir = keyDirNames[key] else { continue }
            switch source {
            case .internal:
                let srcPhotos = internalDatasetsURL.appendingPathComponent("\(dir)/photos")
                if let files = try? FileManager.default.contentsOfDirectory(atPath: srcPhotos.path) {
                    for file in files {
                        let src = srcPhotos.appendingPathComponent(file)
                        let dest = photosDir.appendingPathComponent(file)
                        try? FileManager.default.copyItem(at: src, to: dest)
                    }
                }
            case .asset:
                if let resourcePath = Bundle.main.resourcePath {
                    let srcPhotos = (resourcePath as NSString).appendingPathComponent("datasets/\(dir)/photos")
                    if let files = try? FileManager.default.contentsOfDirectory(atPath: srcPhotos) {
                        for file in files {
                            let src = URL(fileURLWithPath: (srcPhotos as NSString).appendingPathComponent(file))
                            let dest = photosDir.appendingPathComponent(file)
                            try? FileManager.default.copyItem(at: src, to: dest)
                        }
                    }
                }
            }
        }

        reloadDatasets()
        return "All Species — \(placeName)"
    }

    // MARK: - Zip Helpers

    private func createZip(at zipURL: URL, fromDirectory sourceDir: URL, baseDir: URL) -> Bool {
        guard let archive = Archive(url: zipURL, accessMode: .create) else { return false }
        let fm = FileManager.default
        guard let enumerator = fm.enumerator(at: sourceDir, includingPropertiesForKeys: [.isRegularFileKey]) else { return false }

        while let fileURL = enumerator.nextObject() as? URL {
            guard (try? fileURL.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true else { continue }
            let relativePath = fileURL.path.replacingOccurrences(of: baseDir.path + "/", with: "")
            do {
                try archive.addEntry(with: relativePath, fileURL: fileURL)
            } catch {
                print("[PhenologyRepo] Zip add entry failed: \(error)")
                return false
            }
        }
        return true
    }

    private func unzip(fileAt zipURL: URL, to destinationURL: URL) -> Bool {
        guard let archive = Archive(url: zipURL, accessMode: .read) else { return false }
        let fm = FileManager.default

        for entry in archive {
            let entryURL = destinationURL.appendingPathComponent(entry.path)
            // Guard against zip slip
            guard entryURL.standardizedFileURL.path.hasPrefix(destinationURL.standardizedFileURL.path) else {
                print("[PhenologyRepo] Zip entry outside target directory: \(entry.path)")
                return false
            }

            switch entry.type {
            case .directory:
                try? fm.createDirectory(at: entryURL, withIntermediateDirectories: true)
            case .file:
                try? fm.createDirectory(at: entryURL.deletingLastPathComponent(), withIntermediateDirectories: true)
                var fileData = Data()
                _ = try? archive.extract(entry) { data in
                    fileData.append(data)
                }
                try? fileData.write(to: entryURL)
            default:
                break
            }
        }
        return true
    }

    // MARK: - Utility

    private func directorySize(at url: URL) -> Int64 {
        let fm = FileManager.default
        guard let enumerator = fm.enumerator(at: url, includingPropertiesForKeys: [.fileSizeKey]) else { return 0 }
        var total: Int64 = 0
        while let fileURL = enumerator.nextObject() as? URL {
            if let size = try? fileURL.resourceValues(forKeys: [.fileSizeKey]).fileSize {
                total += Int64(size)
            }
        }
        return total
    }
}

// MARK: - Minimal Zip Archive Implementation

/// A minimal zip archive reader/writer using Foundation's compression.
/// For production, consider using a library like ZIPFoundation.
private class Archive: Sequence {
    enum AccessMode { case create, read }

    struct Entry {
        enum EntryType { case file, directory, symlink }
        let path: String
        let type: EntryType
    }

    private let url: URL
    private let accessMode: AccessMode
    private var entries: [Entry] = []
    private var entryData: [String: Data] = [:]

    init?(url: URL, accessMode: AccessMode) {
        self.url = url
        self.accessMode = accessMode

        switch accessMode {
        case .create:
            break
        case .read:
            guard Self.readZip(at: url, entries: &entries, entryData: &entryData) else { return nil }
        }
    }

    func addEntry(with path: String, fileURL: URL) throws {
        let data = try Data(contentsOf: fileURL)
        entryData[path] = data
        entries.append(Entry(path: path, type: .file))
        try writeZip()
    }

    func extract(_ entry: Entry, consumer: (Data) -> Void) throws {
        if let data = entryData[entry.path] {
            consumer(data)
        }
    }

    func makeIterator() -> IndexingIterator<[Entry]> {
        entries.makeIterator()
    }

    // MARK: - Zip Read/Write using shell zip/unzip as fallback

    private func writeZip() throws {
        // Write all entries into a temp directory, then zip
        let fm = FileManager.default
        let tempDir = fm.temporaryDirectory.appendingPathComponent("zip_write_\(UUID().uuidString)")
        try fm.createDirectory(at: tempDir, withIntermediateDirectories: true)

        for (path, data) in entryData {
            let fileURL = tempDir.appendingPathComponent(path)
            try fm.createDirectory(at: fileURL.deletingLastPathComponent(), withIntermediateDirectories: true)
            try data.write(to: fileURL)
        }

        // Use NSFileCoordinator / custom zip - simplified using process
        try? fm.removeItem(at: url)

        // Use a custom minimal zip writer
        try MinimalZipWriter.createZip(at: url, fromDirectory: tempDir)
        try? fm.removeItem(at: tempDir)
    }

    private static func readZip(at url: URL, entries: inout [Entry], entryData: inout [String: Data]) -> Bool {
        guard let zipReader = MinimalZipReader(url: url) else { return false }
        for (path, data, isDirectory) in zipReader.allEntries() {
            if isDirectory {
                entries.append(Entry(path: path, type: .directory))
            } else {
                entries.append(Entry(path: path, type: .file))
                entryData[path] = data
            }
        }
        return true
    }
}

// MARK: - Minimal Zip Implementation (no external dependencies)

/// Minimal ZIP file writer (store-only, no compression, for portability)
private enum MinimalZipWriter {
    static func createZip(at zipURL: URL, fromDirectory directory: URL) throws {
        var fileEntries: [(relativePath: String, data: Data)] = []
        let fm = FileManager.default
        let basePath = directory.path

        if let enumerator = fm.enumerator(at: directory, includingPropertiesForKeys: [.isRegularFileKey]) {
            while let fileURL = enumerator.nextObject() as? URL {
                guard (try? fileURL.resourceValues(forKeys: [.isRegularFileKey]).isRegularFile) == true else { continue }
                let relativePath = String(fileURL.path.dropFirst(basePath.count + 1))
                let data = try Data(contentsOf: fileURL)
                fileEntries.append((relativePath, data))
            }
        }

        var zipData = Data()
        var centralDirectory = Data()
        var offset: UInt32 = 0

        for entry in fileEntries {
            let fileNameData = Data(entry.relativePath.utf8)
            let fileData = entry.data
            let crc = crc32(fileData)

            // Local file header
            var localHeader = Data()
            localHeader.appendUInt32(0x04034b50) // signature
            localHeader.appendUInt16(20) // version needed
            localHeader.appendUInt16(0) // flags
            localHeader.appendUInt16(0) // compression (store)
            localHeader.appendUInt16(0) // mod time
            localHeader.appendUInt16(0) // mod date
            localHeader.appendUInt32(crc) // crc32
            localHeader.appendUInt32(UInt32(fileData.count)) // compressed size
            localHeader.appendUInt32(UInt32(fileData.count)) // uncompressed size
            localHeader.appendUInt16(UInt16(fileNameData.count)) // file name length
            localHeader.appendUInt16(0) // extra field length
            localHeader.append(fileNameData)

            zipData.append(localHeader)
            zipData.append(fileData)

            // Central directory entry
            var cdEntry = Data()
            cdEntry.appendUInt32(0x02014b50) // signature
            cdEntry.appendUInt16(20) // version made by
            cdEntry.appendUInt16(20) // version needed
            cdEntry.appendUInt16(0) // flags
            cdEntry.appendUInt16(0) // compression
            cdEntry.appendUInt16(0) // mod time
            cdEntry.appendUInt16(0) // mod date
            cdEntry.appendUInt32(crc)
            cdEntry.appendUInt32(UInt32(fileData.count))
            cdEntry.appendUInt32(UInt32(fileData.count))
            cdEntry.appendUInt16(UInt16(fileNameData.count))
            cdEntry.appendUInt16(0) // extra field length
            cdEntry.appendUInt16(0) // comment length
            cdEntry.appendUInt16(0) // disk number start
            cdEntry.appendUInt16(0) // internal attrs
            cdEntry.appendUInt32(0) // external attrs
            cdEntry.appendUInt32(offset) // local header offset
            cdEntry.append(fileNameData)

            centralDirectory.append(cdEntry)
            offset = UInt32(zipData.count)
        }

        let cdOffset = UInt32(zipData.count)
        zipData.append(centralDirectory)

        // End of central directory
        var eocd = Data()
        eocd.appendUInt32(0x06054b50) // signature
        eocd.appendUInt16(0) // disk number
        eocd.appendUInt16(0) // disk with cd
        eocd.appendUInt16(UInt16(fileEntries.count))
        eocd.appendUInt16(UInt16(fileEntries.count))
        eocd.appendUInt32(UInt32(centralDirectory.count))
        eocd.appendUInt32(cdOffset)
        eocd.appendUInt16(0) // comment length
        zipData.append(eocd)

        try zipData.write(to: zipURL)
    }

    private static func crc32(_ data: Data) -> UInt32 {
        var crc: UInt32 = 0xFFFFFFFF
        let table = makeCRC32Table()
        for byte in data {
            let index = Int((crc ^ UInt32(byte)) & 0xFF)
            crc = table[index] ^ (crc >> 8)
        }
        return crc ^ 0xFFFFFFFF
    }

    private static func makeCRC32Table() -> [UInt32] {
        var table = [UInt32](repeating: 0, count: 256)
        for i in 0..<256 {
            var crc = UInt32(i)
            for _ in 0..<8 {
                if crc & 1 == 1 {
                    crc = 0xEDB88320 ^ (crc >> 1)
                } else {
                    crc = crc >> 1
                }
            }
            table[i] = crc
        }
        return table
    }
}

/// Minimal ZIP reader (supports store and deflate)
private class MinimalZipReader {
    private let data: Data

    init?(url: URL) {
        guard let data = try? Data(contentsOf: url) else { return nil }
        self.data = data
    }

    func allEntries() -> [(path: String, data: Data, isDirectory: Bool)] {
        var results: [(String, Data, Bool)] = []
        guard data.count > 22 else { return results }

        // Find End of Central Directory
        var eocdOffset = -1
        for i in stride(from: data.count - 22, through: max(0, data.count - 65557), by: -1) {
            if data.readUInt32(at: i) == 0x06054b50 {
                eocdOffset = i
                break
            }
        }
        guard eocdOffset >= 0 else { return results }

        let cdOffset = Int(data.readUInt32(at: eocdOffset + 16))
        let entryCount = Int(data.readUInt16(at: eocdOffset + 10))

        var offset = cdOffset
        for _ in 0..<entryCount {
            guard offset + 46 <= data.count else { break }
            guard data.readUInt32(at: offset) == 0x02014b50 else { break }

            let compression = data.readUInt16(at: offset + 10)
            let compressedSize = Int(data.readUInt32(at: offset + 20))
            let _ = Int(data.readUInt32(at: offset + 24)) // uncompressedSize
            let fileNameLength = Int(data.readUInt16(at: offset + 28))
            let extraLength = Int(data.readUInt16(at: offset + 30))
            let commentLength = Int(data.readUInt16(at: offset + 32))
            let localHeaderOffset = Int(data.readUInt32(at: offset + 42))

            let fileNameStart = offset + 46
            let fileNameData = data.subdata(in: fileNameStart..<(fileNameStart + fileNameLength))
            let path = String(data: fileNameData, encoding: .utf8) ?? ""

            let isDirectory = path.hasSuffix("/")

            // Read file data from local header
            var fileData = Data()
            if !isDirectory && localHeaderOffset + 30 <= data.count {
                let localFileNameLength = Int(data.readUInt16(at: localHeaderOffset + 26))
                let localExtraLength = Int(data.readUInt16(at: localHeaderOffset + 28))
                let dataStart = localHeaderOffset + 30 + localFileNameLength + localExtraLength
                if dataStart + compressedSize <= data.count {
                    let rawData = data.subdata(in: dataStart..<(dataStart + compressedSize))
                    if compression == 0 {
                        fileData = rawData
                    } else if compression == 8 {
                        // Deflate - use built-in decompression
                        if let decompressed = try? (rawData as NSData).decompressed(using: .zlib) as Data {
                            fileData = decompressed
                        } else {
                            // Try raw inflate without zlib header
                            fileData = rawData
                        }
                    }
                }
            }

            results.append((path, fileData, isDirectory))
            offset += 46 + fileNameLength + extraLength + commentLength
        }

        return results
    }
}

// MARK: - Data Extensions for Zip

private extension Data {
    mutating func appendUInt16(_ value: UInt16) {
        var v = value.littleEndian
        append(Data(bytes: &v, count: 2))
    }

    mutating func appendUInt32(_ value: UInt32) {
        var v = value.littleEndian
        append(Data(bytes: &v, count: 4))
    }

    func readUInt16(at offset: Int) -> UInt16 {
        guard offset + 2 <= count else { return 0 }
        return subdata(in: offset..<(offset + 2)).withUnsafeBytes { $0.load(as: UInt16.self).littleEndian }
    }

    func readUInt32(at offset: Int) -> UInt32 {
        guard offset + 4 <= count else { return 0 }
        return subdata(in: offset..<(offset + 4)).withUnsafeBytes { $0.load(as: UInt32.self).littleEndian }
    }
}
