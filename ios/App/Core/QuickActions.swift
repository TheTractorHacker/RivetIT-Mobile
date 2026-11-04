import UIKit
import UserNotifications

/// Hands a Home Screen quick action (touch and hold the app icon) from UIKit to the SwiftUI app. The action waits here
/// until the signed-in main screen is showing, then `MainView` follows it.
final class QuickActionInbox {
    static let shared = QuickActionInbox()
    static let received = Notification.Name("RivetITQuickActionReceived")
    private var pending: String?

    func set(_ type: String) {
        pending = type
        NotificationCenter.default.post(name: Self.received, object: nil)
    }
    func take() -> String? { defer { pending = nil }; return pending }
}

final class AppDelegate: NSObject, UIApplicationDelegate, UNUserNotificationCenterDelegate {
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        NotificationAlerts.registerBackgroundTask()
        UNUserNotificationCenter.current().delegate = self
        return true
    }

    /// The user tapped an alert: remember where it points; the SwiftUI app follows it once it is showing.
    func userNotificationCenter(_ center: UNUserNotificationCenter, didReceive response: UNNotificationResponse,
                                withCompletionHandler completionHandler: @escaping () -> Void) {
        if let route = response.notification.request.content.userInfo["route"] as? String { NotificationAlerts.setTapped(route: route) }
        completionHandler()
    }

    func userNotificationCenter(_ center: UNUserNotificationCenter, willPresent notification: UNNotification,
                                withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void) {
        completionHandler([.banner, .list, .sound])
    }

    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        if let item = options.shortcutItem { QuickActionInbox.shared.set(item.type) }       // cold launch from an action
        let configuration = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        configuration.delegateClass = SceneDelegate.self
        return configuration
    }
}

final class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem,
                     completionHandler: @escaping (Bool) -> Void) {
        QuickActionInbox.shared.set(shortcutItem.type)                                        // app was already running
        completionHandler(true)
    }
}
