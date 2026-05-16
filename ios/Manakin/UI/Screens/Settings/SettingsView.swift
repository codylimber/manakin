import SwiftUI

struct SettingsView: View {
    let lifeListService: LifeListService
    let repository: PhenologyRepository
    var onTimeline: (() -> Void)?
    var onTripReport: (() -> Void)?
    var onCompare: (() -> Void)?
    var onHelp: (() -> Void)?
    var onAbout: (() -> Void)?

    @Environment(\.appColors) private var colors
    @Environment(AppSettings.self) private var appSettings
    @Environment(ThemeManager.self) private var themeManager

    @State private var username: String = ""
    @State private var isSyncing = false
    @State private var syncMessage: String?
    private var lastSyncText: String {
        let lastSync = lifeListService.getLastSyncTime()
        if lastSync > 0 {
            let date = Date(timeIntervalSince1970: Double(lastSync) / 1000.0)
            let formatter = DateFormatter()
            formatter.dateFormat = "MMM d, yyyy h:mm a"
            return formatter.string(from: date)
        }
        return "Never"
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 20) {
                // Profile card
                if !username.trimmingCharacters(in: .whitespaces).isEmpty {
                    profileCard
                }

                // iNaturalist Account
                sectionHeader(icon: "person.fill", title: "iNaturalist Account")

                Text("Enter your iNaturalist username to see which species you've already observed. Your observation data is public on iNaturalist \u{2014} no login is required.")
                    .font(.system(size: 13))
                    .foregroundColor(colors.onSurfaceVariant)

                TextField("iNaturalist Username", text: $username)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                    .autocorrectionDisabled()
                    .onChange(of: username) { _, newValue in
                        lifeListService.username = newValue.trimmingCharacters(in: .whitespaces)
                    }

                // Sync
                sectionHeader(icon: "arrow.clockwise", title: "Sync Observations")
                Text("Last synced: \(lastSyncText)")
                    .font(.system(size: 13))
                    .foregroundColor(colors.onSurfaceVariant)

                Button {
                    syncObservations()
                } label: {
                    HStack {
                        if isSyncing {
                            ProgressView()
                                .scaleEffect(0.8)
                                .tint(.white)
                            Text("Syncing...")
                                .font(.system(size: 16, weight: .semibold))
                        } else {
                            Text("Sync Observations")
                                .font(.system(size: 16, weight: .semibold))
                        }
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background((!isSyncing && !username.trimmingCharacters(in: .whitespaces).isEmpty) ? Color.appPrimary : Color.gray)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .disabled(isSyncing || username.trimmingCharacters(in: .whitespaces).isEmpty)

                if let msg = syncMessage {
                    Text(msg)
                        .font(.system(size: 13))
                        .foregroundColor(msg.hasPrefix("Sync failed") ? .red : .appPrimary)
                }

                // Appearance
                sectionHeader(icon: "paintbrush.fill", title: "Appearance")

                Toggle("Dark Mode", isOn: Binding(
                    get: { themeManager.isDarkMode },
                    set: { themeManager.isDarkMode = $0 }
                ))
                .tint(.appPrimary)

                VStack(alignment: .leading, spacing: 2) {
                    Toggle(isOn: Binding(
                        get: { appSettings.useScientificNames },
                        set: { appSettings.useScientificNames = $0 }
                    )) {
                        VStack(alignment: .leading) {
                            Text("Scientific Names")
                                .font(.system(size: 15))
                            Text("Show scientific names as the primary name")
                                .font(.system(size: 12))
                                .foregroundColor(colors.onSurfaceVariant)
                        }
                    }
                    .tint(.appPrimary)
                }

                // Min activity
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Minimum Activity")
                            .font(.system(size: 15))
                            .foregroundColor(colors.onSurface)
                        Spacer()
                        Text("\(appSettings.minActivityPercent)%")
                            .font(.system(size: 15))
                            .foregroundColor(.appPrimary)
                    }
                    Text("Hide species below this activity threshold")
                        .font(.system(size: 12))
                        .foregroundColor(colors.onSurfaceVariant)
                    Slider(
                        value: Binding(
                            get: { Double(appSettings.minActivityPercent) },
                            set: { appSettings.minActivityPercent = Int($0) }
                        ),
                        in: 0...50,
                        step: 5
                    )
                    .tint(.appPrimary)
                }

                // Default sort
                VStack(alignment: .leading, spacing: 4) {
                    Text("Default Sort")
                        .font(.system(size: 15))
                        .foregroundColor(colors.onSurface)
                    Text("Sort order when opening the app")
                        .font(.system(size: 12))
                        .foregroundColor(colors.onSurfaceVariant)

                    HStack(spacing: 6) {
                        sortChip("Likelihood", mode: .likelihood)
                        sortChip("Peak", mode: .peakDate)
                        sortChip("Name", mode: .name)
                        sortChip("Taxonomy", mode: .taxonomy)
                    }
                }

                // Notifications
                sectionHeader(icon: "bell.fill", title: "Notifications")

                VStack(alignment: .leading, spacing: 2) {
                    Toggle(isOn: Binding(
                        get: { appSettings.weeklyDigestEnabled },
                        set: { appSettings.weeklyDigestEnabled = $0 }
                    )) {
                        VStack(alignment: .leading) {
                            Text("Weekly Digest")
                                .font(.system(size: 15))
                            Text("Get notified about newly active and peak species")
                                .font(.system(size: 12))
                                .foregroundColor(colors.onSurfaceVariant)
                        }
                    }
                    .tint(.appPrimary)
                }

                if appSettings.weeklyDigestEnabled {
                    digestDaysSection
                }

                VStack(alignment: .leading, spacing: 2) {
                    Toggle(isOn: Binding(
                        get: { appSettings.targetNotificationsEnabled },
                        set: { appSettings.targetNotificationsEnabled = $0 }
                    )) {
                        VStack(alignment: .leading) {
                            Text("Target Species Alerts")
                                .font(.system(size: 15))
                            Text("Get notified when favorited species approach peak (2 weeks before and at peak)")
                                .font(.system(size: 12))
                                .foregroundColor(colors.onSurfaceVariant)
                        }
                    }
                    .tint(.appPrimary)
                }

                // Notification dataset filter
                if appSettings.weeklyDigestEnabled || appSettings.targetNotificationsEnabled {
                    notificationDatasetFilter
                }

                Spacer().frame(height: 32)
            }
            .padding(16)
        }
        .background(colors.background)
        .navigationTitle("")
        .toolbar {
            ToolbarItem(placement: .principal) {
                Text("Settings")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundColor(.appPrimary)
            }
            ToolbarItem(placement: .navigationBarTrailing) {
                AppOverflowMenu()
            }
        }
        .onAppear {
            username = lifeListService.username
        }
    }

    private var profileCard: some View {
        HStack(spacing: 12) {
            Image(systemName: "person.fill")
                .font(.system(size: 24))
                .foregroundColor(.appPrimary)
            VStack(alignment: .leading) {
                Text(username)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundColor(colors.onSurface)
                Text("iNaturalist")
                    .font(.system(size: 12))
                    .foregroundColor(colors.onSurfaceVariant)
            }
            Spacer()
        }
        .padding(16)
        .background(colors.surface)
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func sectionHeader(icon: String, title: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(.appPrimary)
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(colors.onBackground)
        }
    }

    private func sortChip(_ label: String, mode: SortMode) -> some View {
        Button {
            appSettings.defaultSortMode = mode
        } label: {
            Text(label)
                .font(.system(size: 12))
                .foregroundColor(appSettings.defaultSortMode == mode ? .appPrimary : colors.onSurfaceVariant)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(appSettings.defaultSortMode == mode ? Color.appPrimary.opacity(0.2) : colors.surfaceVariant)
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .buttonStyle(.plain)
    }

    private var digestDaysSection: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("Digest Days")
                .font(.system(size: 14))
                .foregroundColor(colors.onSurface)
            Text("Select one or more days to receive your digest")
                .font(.system(size: 12))
                .foregroundColor(colors.onSurfaceVariant)

            HStack(spacing: 4) {
                let days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"]
                ForEach(Array(days.enumerated()), id: \.offset) { idx, day in
                    let calDay = idx + 1
                    let isSelected = appSettings.digestDays.contains(calDay)
                    Button {
                        if isSelected {
                            if appSettings.digestDays.count > 1 {
                                appSettings.digestDays.remove(calDay)
                            }
                        } else {
                            appSettings.digestDays.insert(calDay)
                        }
                    } label: {
                        Text(day)
                            .font(.system(size: 11))
                            .foregroundColor(isSelected ? .appPrimary : colors.onSurfaceVariant)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 4)
                            .background(isSelected ? Color.appPrimary.opacity(0.2) : colors.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 6))
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    @ViewBuilder
    private var notificationDatasetFilter: some View {
        let allKeys = repository.getKeys()
        if allKeys.count > 1 {
            VStack(alignment: .leading, spacing: 4) {
                Text("Notification Datasets")
                    .font(.system(size: 14))
                    .foregroundColor(colors.onSurface)
                Text(appSettings.notificationDatasetKeys.isEmpty
                     ? "Receiving notifications for all datasets"
                     : "Receiving notifications for \(appSettings.notificationDatasetKeys.count) of \(allKeys.count) datasets")
                    .font(.system(size: 12))
                    .foregroundColor(colors.onSurfaceVariant)

                FlowLayout(spacing: 6) {
                    ForEach(allKeys, id: \.self) { key in
                        let isSelected = appSettings.notificationDatasetKeys.isEmpty || appSettings.notificationDatasetKeys.contains(key)
                        Button {
                            let current = appSettings.notificationDatasetKeys
                            if current.isEmpty {
                                appSettings.notificationDatasetKeys = Set(allKeys).subtracting([key])
                            } else if current.contains(key) {
                                let removed = current.subtracting([key])
                                appSettings.notificationDatasetKeys = removed.isEmpty ? [] : removed
                            } else {
                                let added = current.union([key])
                                appSettings.notificationDatasetKeys = added.count == allKeys.count ? [] : added
                            }
                        } label: {
                            VStack(alignment: .leading, spacing: 0) {
                                Text(repository.getGroupName(key: key))
                                    .font(.system(size: 12))
                                Text(repository.getPlaceNameForKey(key: key))
                                    .font(.system(size: 10))
                                    .foregroundColor(colors.onSurfaceVariant)
                            }
                            .foregroundColor(isSelected ? .appPrimary : colors.onSurfaceVariant)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(isSelected ? Color.appPrimary.opacity(0.2) : colors.surfaceVariant)
                            .clipShape(RoundedRectangle(cornerRadius: 8))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func syncObservations() {
        guard !username.trimmingCharacters(in: .whitespaces).isEmpty else {
            syncMessage = "Please enter a username first."
            return
        }
        isSyncing = true
        syncMessage = nil
        Task {
            let keys = repository.getKeys()
            for key in keys {
                guard let dataset = repository.getDataset(key: key) else { continue }
                let meta = dataset.metadata
                await lifeListService.refreshForDataset(
                    datasetKey: key,
                    taxonId: nil,
                    placeId: meta.placeId
                )
            }
            await MainActor.run {
                syncMessage = "Synced \(keys.count) dataset(s) successfully!"
                isSyncing = false
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
                .foregroundColor(.appPrimary)
        }
    }
}

// MARK: - FlowLayout helper

struct FlowLayout: Layout {
    var spacing: CGFloat = 6

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let result = arrangeSubviews(proposal: proposal, subviews: subviews)
        return result.size
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        let result = arrangeSubviews(proposal: proposal, subviews: subviews)
        for (index, position) in result.positions.enumerated() {
            subviews[index].place(at: CGPoint(x: bounds.minX + position.x, y: bounds.minY + position.y), proposal: .unspecified)
        }
    }

    private func arrangeSubviews(proposal: ProposedViewSize, subviews: Subviews) -> (size: CGSize, positions: [CGPoint]) {
        let maxWidth = proposal.width ?? .infinity
        var positions: [CGPoint] = []
        var x: CGFloat = 0
        var y: CGFloat = 0
        var rowHeight: CGFloat = 0
        var maxX: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > maxWidth && x > 0 {
                x = 0
                y += rowHeight + spacing
                rowHeight = 0
            }
            positions.append(CGPoint(x: x, y: y))
            rowHeight = max(rowHeight, size.height)
            x += size.width + spacing
            maxX = max(maxX, x)
        }

        return (CGSize(width: maxX, height: y + rowHeight), positions)
    }
}
