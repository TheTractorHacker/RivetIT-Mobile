import SwiftUI
import LocalAuthentication
import RivetCore

@main
struct RivetITApp: App {
    @StateObject private var session = Session()

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(session)
                .tint(Palette.brand)
        }
    }
}

/// Chooses between server setup, sign-in and the main shell, and covers the app with a lock screen when the
/// biometric lock is on (at cold start and after five minutes in the background).
struct RootView: View {
    @EnvironmentObject var session: Session
    @Environment(\.scenePhase) private var scenePhase
    @State private var locked = false
    @State private var backgroundedAt: Date?
    @State private var launchChecked = false

    var body: some View {
        ZStack {
            switch session.phase {
            case .setup: ServerSetupView()
            case .login: LoginView()
            case .main: MainView()
            }
            if locked { LockView(unlocked: { locked = false }).transition(.opacity).zIndex(2) }
        }
        .preferredColorScheme(session.themeMode.colorScheme)
        .animation(.default, value: session.phase)
        .onAppear {
            if !launchChecked {
                launchChecked = true
                if session.biometricLock && session.phase == .main { locked = true }
            }
        }
        .onChange(of: scenePhase) { phase in
            switch phase {
            case .background: backgroundedAt = Date()
            case .active:
                if session.biometricLock, session.phase == .main, let t = backgroundedAt, Date().timeIntervalSince(t) > 300 { locked = true }
                backgroundedAt = nil
            default: break
            }
        }
    }
}

struct LockView: View {
    var unlocked: () -> Void
    @State private var message: String?

    var body: some View {
        VStack(spacing: 18) {
            BrandMark(size: 72)
            Text("RivetIT is locked").font(.title2.weight(.semibold))
            Text("Verify your identity to continue").foregroundColor(Palette.label2)
            Button { authenticate() } label: { Label("Unlock", systemImage: "faceid") }
                .buttonStyle(PrimaryButtonStyle()).frame(maxWidth: 240)
            if let message { InlineError(message: message).padding(.horizontal, 32) }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity).background(Palette.background.ignoresSafeArea())
        .onAppear { authenticate() }
    }

    private func authenticate() {
        let context = LAContext()
        var err: NSError?
        // Biometrics with the device passcode as fallback, so a changed face or fingerprint never locks the user out.
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else {
            message = "Set up a passcode on this device to use the app lock."; return
        }
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Unlock RivetIT") { ok, error in
            DispatchQueue.main.async {
                if ok { unlocked() } else if let e = error as? LAError, e.code != .userCancel, e.code != .appCancel { message = e.localizedDescription }
            }
        }
    }
}
