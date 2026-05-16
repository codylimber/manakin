import Foundation
import UserNotifications
import BackgroundTasks

class ManakinNotificationManager {
    static let shared = ManakinNotificationManager()

    static let digestTaskIdentifier = "com.codylimber.manakin.weeklyDigest"
    static let digestCategoryIdentifier = "WEEKLY_DIGEST"
    static let targetCategoryIdentifier = "TARGET_ALERT"

    private init() {}

    // MARK: - Permission

    func requestPermission() async -> Bool {
        do {
            return try await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .badge, .sound])
        } catch {
            return false
        }
    }

    // MARK: - Background Task Registration

    func registerBackgroundTask() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: Self.digestTaskIdentifier, using: nil) { task in
            self.handleBackgroundTask(task as! BGAppRefreshTask)
        }
    }

    func scheduleBackgroundTask(hour: Int = 8) {
        let request = BGAppRefreshTaskRequest(identifier: Self.digestTaskIdentifier)
        // Schedule for next occurrence of the target hour
        let calendar = Calendar.current
        var components = calendar.dateComponents([.year, .month, .day], from: Date())
        components.hour = hour
        components.minute = 0
        if let targetDate = calendar.date(from: components), targetDate <= Date() {
            // Already past target hour today, schedule for tomorrow
            request.earliestBeginDate = calendar.date(byAdding: .day, value: 1, to: targetDate)
        } else {
            request.earliestBeginDate = calendar.date(from: components)
        }

        do {
            try BGTaskScheduler.shared.submit(request)
        } catch {
            print("Failed to schedule background task: \(error)")
        }
    }

    func cancelBackgroundTask() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: Self.digestTaskIdentifier)
        UNUserNotificationCenter.current().removeAllPendingNotificationRequests()
    }

    // MARK: - Background Task Handler

    private func handleBackgroundTask(_ task: BGAppRefreshTask) {
        // Schedule next occurrence
        let settings = AppSettings.shared
        scheduleBackgroundTask(hour: settings.digestHour)

        let workTask = Task {
            await processNotifications()
        }

        task.expirationHandler = {
            workTask.cancel()
        }

        Task {
            await workTask.value
            task.setTaskCompleted(success: true)
        }
    }

    // MARK: - Notification Logic

    func processNotifications() async {
        let settings = AppSettings.shared
        let repository = PhenologyRepository()
        repository.loadDatasets()

        let calendar = Calendar(identifier: .iso8601)
        let today = Date()
        let weekday = Calendar.current.component(.weekday, from: today) // 1=Sunday
        let currentWeek = calendar.component(.weekOfYear, from: today)
        let lastWeek = currentWeek > 1 ? currentWeek - 1 : 53

        let digestEnabled = settings.weeklyDigestEnabled
        let targetNotifs = settings.targetNotificationsEnabled
        let sendDigest = digestEnabled && settings.digestDays.contains(weekday)

        guard sendDigest || targetNotifs else { return }

        let notifKeys = settings.notificationDatasetKeys
        let keys = if notifKeys.isEmpty {
            repository.getKeys()
        } else {
            repository.getKeys().filter { notifKeys.contains($0) }
        }

        // Weekly digest
        if sendDigest {
            var newlyActive: [String] = []
            var peakSpecies: [String] = []
            var seenIds = Set<Int>()

            for key in keys {
                for sp in repository.getSpeciesForKey(key: key) {
                    guard seenIds.insert(sp.taxonId).inserted else { continue }
                    let thisAbundance = sp.weekly.first(where: { $0.week == currentWeek })?.relAbundance ?? 0
                    let lastAbundance = sp.weekly.first(where: { $0.week == lastWeek })?.relAbundance ?? 0
                    let name = sp.commonName.isEmpty ? sp.scientificName : sp.commonName

                    if thisAbundance > 0 && lastAbundance == 0 {
                        newlyActive.append(name)
                    }
                    if thisAbundance >= 0.8 && lastAbundance < 0.8 {
                        peakSpecies.append(name)
                    }
                }
            }

            if !newlyActive.isEmpty || !peakSpecies.isEmpty {
                var body = ""
                if !peakSpecies.isEmpty {
                    let names = peakSpecies.prefix(3).joined(separator: ", ")
                    body += "Entering peak: \(names)"
                    if peakSpecies.count > 3 { body += " +\(peakSpecies.count - 3) more" }
                }
                if !newlyActive.isEmpty {
                    if !body.isEmpty { body += "\n" }
                    let names = newlyActive.prefix(3).joined(separator: ", ")
                    body += "Newly active: \(names)"
                    if newlyActive.count > 3 { body += " +\(newlyActive.count - 3) more" }
                }

                let content = UNMutableNotificationContent()
                content.title = "Manakin Weekly Digest"
                content.body = body
                content.categoryIdentifier = Self.digestCategoryIdentifier
                if let soundURL = Bundle.main.url(forResource: "manakin_notification", withExtension: "wav") {
                    content.sound = UNNotificationSound(named: UNNotificationSoundName(soundURL.lastPathComponent))
                }

                let request = UNNotificationRequest(identifier: "weekly_digest_\(currentWeek)", content: content, trigger: nil)
                try? await UNUserNotificationCenter.current().add(request)
            }
        }

        // Target species alerts (daily)
        if targetNotifs {
            let favorites = settings.favorites
            guard !favorites.isEmpty else { return }

            var targetAtPeak: [String] = []
            var targetApproaching: [String] = []
            var seenIds = Set<Int>()

            for key in keys {
                for sp in repository.getSpeciesForKey(key: key) {
                    guard favorites.contains(sp.taxonId) else { continue }
                    guard seenIds.insert(sp.taxonId).inserted else { continue }
                    let name = sp.commonName.isEmpty ? sp.scientificName : sp.commonName

                    if sp.peakWeek == currentWeek {
                        targetAtPeak.append(name)
                    } else if sp.peakWeek == (currentWeek + 2 - 1) % 53 + 1 {
                        targetApproaching.append(name)
                    }
                }
            }

            if !targetAtPeak.isEmpty || !targetApproaching.isEmpty {
                var body = ""
                if !targetAtPeak.isEmpty {
                    body += "At peak now: \(targetAtPeak.joined(separator: ", "))"
                }
                if !targetApproaching.isEmpty {
                    if !body.isEmpty { body += "\n" }
                    body += "Peak in 2 weeks: \(targetApproaching.joined(separator: ", "))"
                }

                let content = UNMutableNotificationContent()
                content.title = "★ Target Species Alert"
                content.body = body
                content.categoryIdentifier = Self.targetCategoryIdentifier

                let request = UNNotificationRequest(identifier: "target_alert_\(currentWeek)", content: content, trigger: nil)
                try? await UNUserNotificationCenter.current().add(request)
            }
        }
    }
}
