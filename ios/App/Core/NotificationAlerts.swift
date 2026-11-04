import UIKit
import UserNotifications
import BackgroundTasks
import RivetCore

/// Free alerts for new notifications. Real push (APNs) needs a paid Apple Developer account, so instead iOS wakes the
/// app now and then (Background App Refresh), the app asks the server what is new, and raises a local notification.
///
/// Honest limits: iOS decides when the wake-ups happen (often tens of minutes, less if the app is rarely opened, never
/// if Background App Refresh or Low Power Mode is off), so this is not instant.
enum NotificationAlerts {
    static let taskID = "com.foleyit.itflow.ios.refresh"
    private static let enabledKey = "alertsEnabled", lastSeenKey = "alertsLastSeen", askedKey = "alertsAsked"
    private static var tappedRoute: String?
    static let tapped = Notification.Name("RivetITAlertTapped")

    static var enabled: Bool {
        get { UserDefaults.standard.object(forKey: enabledKey) as? Bool ?? true }
        set {
            UserDefaults.standard.set(newValue, forKey: enabledKey)
            if newValue { scheduleRefresh() } else { BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskID) }
        }
    }
    private static var lastSeen: Int? {
        get { UserDefaults.standard.object(forKey: lastSeenKey) as? Int }
        set { UserDefaults.standard.set(newValue, forKey: lastSeenKey) }
    }

    // MARK: Background refresh

    /// Must run before the app finishes launching.
    static func registerBackgroundTask() {
        BGTaskScheduler.shared.register(forTaskWithIdentifier: taskID, using: nil) { task in
            guard let task = task as? BGAppRefreshTask else { task.setTaskCompleted(success: false); return }
            scheduleRefresh()                                       // always line up the next wake-up first
            let work = Task { task.setTaskCompleted(success: await poll()) }
            task.expirationHandler = { work.cancel() }
        }
    }

    static func scheduleRefresh() {
        guard enabled else { return }
        let request = BGAppRefreshTaskRequest(identifier: taskID)
        request.earliestBeginDate = Date(timeIntervalSinceNow: 15 * 60)
        try? BGTaskScheduler.shared.submit(request)
    }

    /// Asks the server what is new and alerts for it. Returns false only when nothing could be checked.
    static func poll() async -> Bool {
        guard enabled, let api = Session.backgroundClient(),
              let items = try? await api.notifications().data else { return false }
        let plan = NotificationAlertPlan.plan(items: items, lastSeenID: lastSeen)
        lastSeen = plan.lastSeenID
        guard !plan.toAlert.isEmpty, await isAuthorized() else { return true }
        for n in plan.toAlert { await post(title: Session.cachedBrandName(), body: NotificationAlertPlan.body(n.message),
                                           route: NotificationAlertPlan.route(for: n), id: "rivetit.notification.\(n.id)") }
        if plan.overflow > 0 {
            await post(title: Session.cachedBrandName(), body: "and \(plan.overflow) more new notifications",
                       route: "notifications", id: "rivetit.notification.more")
        }
        return true
    }

    // MARK: Foreground

    /// Called when the app is in front: anything the user can now see in the app must not alert later, and the first
    /// time through we ask permission.
    static func activate(api: APIClient?) async {
        guard enabled, let api else { return }
        if let items = try? await api.notifications().data {
            lastSeen = NotificationAlertPlan.plan(items: items, lastSeenID: lastSeen).lastSeenID
        }
        if !UserDefaults.standard.bool(forKey: askedKey) {
            UserDefaults.standard.set(true, forKey: askedKey)
            _ = await requestAuthorization()
        }
    }

    /// Sign-out / server change: forget the mark so another account never inherits it.
    static func reset() {
        UserDefaults.standard.removeObject(forKey: lastSeenKey)
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskID)
        UNUserNotificationCenter.current().removeAllDeliveredNotifications()
    }

    // MARK: Permission and delivery

    static func requestAuthorization() async -> Bool {
        (try? await UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge])) ?? false
    }

    static func isAuthorized() async -> Bool {
        switch await UNUserNotificationCenter.current().notificationSettings().authorizationStatus {
        case .authorized, .provisional, .ephemeral: return true
        default: return false
        }
    }

    static func isDenied() async -> Bool {
        await UNUserNotificationCenter.current().notificationSettings().authorizationStatus == .denied
    }

    private static func post(title: String, body: String, route: String, id: String) async {
        let content = UNMutableNotificationContent()
        content.title = title
        content.body = body
        content.sound = .default
        content.threadIdentifier = "rivetit.notifications"
        content.userInfo = ["route": route]
        try? await UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: id, content: content, trigger: nil))
    }

    // MARK: Tap handling

    static func setTapped(route: String) {
        tappedRoute = route
        NotificationCenter.default.post(name: tapped, object: nil)
    }
    /// The route of the alert the user tapped; the main screen validates it against `DeepLinks` before following it.
    static func takeTappedRoute() -> String? { defer { tappedRoute = nil }; return tappedRoute }
}
