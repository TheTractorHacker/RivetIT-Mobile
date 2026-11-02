import UIKit

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

final class AppDelegate: NSObject, UIApplicationDelegate {
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
