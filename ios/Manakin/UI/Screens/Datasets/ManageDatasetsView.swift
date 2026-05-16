import SwiftUI

struct ManageDatasetsView: View {
    let repository: PhenologyRepository
    var onAddDataset: () -> Void = {}
    var onUpdateDataset: ((DatasetMetadata) -> Void)?
    var onTimeline: (() -> Void)?
    var onTripReport: (() -> Void)?
    var onCompare: (() -> Void)?
    var onHelp: (() -> Void)?
    var onAbout: (() -> Void)?

    @Environment(\.appColors) private var colors
    @Environment(\.dismiss) private var dismiss

    @State private var datasets: [DatasetInfo] = []
    @State private var deleteTarget: DatasetInfo?
    @State private var showBundleExport = false
    @State private var isExporting = false
    @State private var bundleName = "my-datasets"
    @State private var bundleSelectedKeys: Set<String> = []

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            if datasets.isEmpty {
                VStack {
                    Spacer()
                    Text("No datasets installed")
                        .font(.system(size: 16))
                        .foregroundColor(colors.onSurfaceVariant)
                    Spacer()
                }
                .frame(maxWidth: .infinity)
            } else {
                List {
                    let exportable = datasets.filter { $0.source == .internal }
                    if exportable.count > 1 {
                        Section {
                            Button {
                                bundleSelectedKeys = Set(exportable.map { $0.key })
                                bundleName = "my-datasets"
                                showBundleExport = true
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: "square.and.arrow.up")
                                        .font(.system(size: 14))
                                        .foregroundColor(.primary)
                                    Text("Export Bundle")
                                        .font(.system(size: 13))
                                        .foregroundColor(.primary)
                                }
                            }
                        }
                        .listRowBackground(Color.clear)
                    }

                    ForEach(datasets, id: \.key) { info in
                        datasetCard(info: info)
                            .listRowInsets(EdgeInsets(top: 4, leading: 12, bottom: 4, trailing: 12))
                            .listRowBackground(Color.clear)
                    }
                }
                .listStyle(.plain)
                .scrollContentBackground(.hidden)
            }

            // FAB
            Button(action: onAddDataset) {
                Image(systemName: "plus")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(.white)
                    .frame(width: 56, height: 56)
                    .background(Color.primary)
                    .clipShape(Circle())
                    .shadow(radius: 4)
            }
            .padding(.trailing, 16)
            .padding(.bottom, 16)
        }
        .background(colors.background)
        .navigationTitle("")
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text("Datasets")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.primary)
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                overflowMenu
            }
        }
        .onAppear {
            datasets = repository.getAllDatasets()
        }
        .alert("Remove \(deleteTarget?.group ?? "")?", isPresented: Binding(
            get: { deleteTarget != nil },
            set: { if !$0 { deleteTarget = nil } }
        )) {
            Button("Cancel", role: .cancel) { deleteTarget = nil }
            Button("Remove", role: .destructive) {
                if let info = deleteTarget {
                    repository.deleteDataset(key: info.key)
                    datasets = repository.getAllDatasets()
                    deleteTarget = nil
                }
            }
        } message: {
            if let info = deleteTarget {
                Text(info.source == .asset
                     ? "This will hide the bundled dataset. You can reinstall the app to restore it."
                     : "This will remove the dataset and all downloaded photos.")
            }
        }
        .sheet(isPresented: $showBundleExport) {
            bundleExportSheet
        }
    }

    private func datasetCard(info: DatasetInfo) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(info.group)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(colors.onBackground)
                Text(info.placeName)
                    .font(.system(size: 13))
                    .foregroundColor(colors.onSurfaceVariant)
                let details = buildDetails(info: info)
                Text(details)
                    .font(.system(size: 12))
                    .foregroundColor(colors.onSurfaceVariant)
            }
            Spacer()

            if info.source == .internal, let onUpdate = onUpdateDataset {
                Button {
                    if let ds = repository.getDataset(key: info.key) {
                        onUpdate(ds.metadata)
                    }
                } label: {
                    Image(systemName: "arrow.clockwise")
                        .font(.system(size: 16))
                        .foregroundColor(.primary)
                }
                .buttonStyle(.plain)
            }

            if info.source == .internal {
                Button {
                    exportDataset(info: info)
                } label: {
                    Image(systemName: "square.and.arrow.up")
                        .font(.system(size: 16))
                        .foregroundColor(.primary)
                }
                .buttonStyle(.plain)
                .disabled(isExporting)
            }

            Button {
                deleteTarget = info
            } label: {
                Image(systemName: "trash")
                    .font(.system(size: 16))
                    .foregroundColor(.red)
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func buildDetails(info: DatasetInfo) -> String {
        var parts: [String] = ["\(info.speciesCount) species"]
        if info.source == .internal && info.sizeBytes > 0 {
            parts.append(info.sizeDisplay)
        }
        if info.source == .asset {
            parts.append("Bundled")
        }
        return parts.joined(separator: "  \u{2022}  ")
    }

    private func exportDataset(info: DatasetInfo) {
        Task {
            isExporting = true
            guard let zipURL = repository.exportDataset(key: info.key) else {
                isExporting = false
                return
            }
            isExporting = false
            let av = UIActivityViewController(activityItems: [zipURL], applicationActivities: nil)
            if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
               let root = scene.windows.first?.rootViewController {
                root.present(av, animated: true)
            }
        }
    }

    private var bundleExportSheet: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Select datasets to include:")
                    .font(.system(size: 14))
                    .foregroundColor(colors.onSurfaceVariant)

                let exportable = datasets.filter { $0.source == .internal }
                ForEach(exportable, id: \.key) { info in
                    HStack {
                        Button {
                            if bundleSelectedKeys.contains(info.key) {
                                bundleSelectedKeys.remove(info.key)
                            } else {
                                bundleSelectedKeys.insert(info.key)
                            }
                        } label: {
                            Image(systemName: bundleSelectedKeys.contains(info.key) ? "checkmark.square.fill" : "square")
                                .foregroundColor(.primary)
                        }
                        VStack(alignment: .leading) {
                            Text(info.group)
                                .font(.system(size: 14))
                                .foregroundColor(colors.onSurface)
                            Text(info.placeName)
                                .font(.system(size: 12))
                                .foregroundColor(colors.onSurfaceVariant)
                        }
                    }
                }

                TextField("Bundle name", text: $bundleName)
                    .textFieldStyle(.roundedBorder)

                Spacer()

                Button {
                    Task {
                        isExporting = true
                        guard let zipURL = repository.exportBundle(keys: Array(bundleSelectedKeys), bundleName: bundleName) else {
                            isExporting = false
                            showBundleExport = false
                            return
                        }
                        isExporting = false
                        showBundleExport = false
                        let av = UIActivityViewController(activityItems: [zipURL], applicationActivities: nil)
                        if let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                           let root = scene.windows.first?.rootViewController {
                            root.present(av, animated: true)
                        }
                    }
                } label: {
                    Text(isExporting ? "Exporting..." : "Export")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(bundleSelectedKeys.isEmpty || isExporting ? Color.gray : Color.primary)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .disabled(bundleSelectedKeys.isEmpty || isExporting)
            }
            .padding(16)
            .navigationTitle("Export Bundle")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { showBundleExport = false }
                }
            }
        }
    }

    private var overflowMenu: some View {
        Menu {
            if let onTimeline = onTimeline {
                Button("This Week") { onTimeline() }
            }
            if let onTripReport = onTripReport {
                Button("Trip Report") { onTripReport() }
            }
            if let onCompare = onCompare {
                Button("Compare") { onCompare() }
            }
            Divider()
            if let onHelp = onHelp {
                Button("Help") { onHelp() }
            }
            if let onAbout = onAbout {
                Button("About") { onAbout() }
            }
        } label: {
            Image(systemName: "ellipsis.circle")
                .foregroundColor(.primary)
        }
    }
}
