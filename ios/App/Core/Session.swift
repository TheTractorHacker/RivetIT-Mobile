import SwiftUI
import UIKit
import Security
import RivetCore

enum ThemeMode: String, CaseIterable, Identifiable {
    case system, light, dark
    var id: String { rawValue }
    var title: String { rawValue.capitalized }
    var colorScheme: ColorScheme? { self == .light ? .light : (self == .dark ? .dark : nil) }
}

/// Minimal Keychain wrapper for the API token.
enum Keychain {
    private static let service = "com.foleyit.itflow.ios"

    static func set(_ value: String?, for key: String) {
        let base: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                   kSecAttrService as String: service, kSecAttrAccount as String: key]
        SecItemDelete(base as CFDictionary)
        guard let value, let data = value.data(using: .utf8) else { return }
        var add = base
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }

    static func get(_ key: String) -> String? {
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                    kSecAttrService as String: service, kSecAttrAccount as String: key,
                                    kSecReturnData as String: true, kSecMatchLimit as String: kSecMatchLimitOne]
        var out: AnyObject?
        guard SecItemCopyMatching(query as CFDictionary, &out) == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
}

enum ConnectResult { case connected, needsCertificate(CertificateSummary) }
enum SignInResult { case signedIn, needsTwoFactor }

/// App-wide state: which server we talk to, who is signed in, and what they may do.
@MainActor
final class Session: ObservableObject {
    enum Phase { case setup, login, main }

    @Published private(set) var phase: Phase = .setup
    @Published private(set) var profile: UserProfile?
    @Published private(set) var capabilities: Capabilities = .unrestricted
    @Published var hasUnread = false
    @Published var notice: String?
    @Published var themeMode: ThemeMode {
        didSet { UserDefaults.standard.set(themeMode.rawValue, forKey: Keys.theme) }
    }
    @Published var biometricLock: Bool {
        didSet { UserDefaults.standard.set(biometricLock, forKey: Keys.biometricLock) }
    }

    private(set) var serverURL: URL?
    private(set) var api: APIClient?
    private var trustedFingerprint: String?

    private enum Keys {
        static let server = "serverURL", fingerprint = "trustedFingerprint", theme = "themeMode"
        static let biometricLock = "biometricLock", token = "apiToken"
    }

    init() {
        let d = UserDefaults.standard
        themeMode = ThemeMode(rawValue: d.string(forKey: Keys.theme) ?? "") ?? .system
        biometricLock = d.bool(forKey: Keys.biometricLock)
        if let s = d.string(forKey: Keys.server), let url = URL(string: s) {
            serverURL = url
            trustedFingerprint = d.string(forKey: Keys.fingerprint)
            buildClient(token: Keychain.get(Keys.token))
            phase = api?.token == nil ? .login : .main
            if phase == .main { Task { await refreshProfile() } }
        }
    }

    var serverDisplay: String { serverURL?.absoluteString ?? "" }

    /// The API client, or an error if there is no server yet (never the case while signed in).
    func requireAPI() throws -> APIClient {
        guard let api else { throw RivetError.invalidURL }
        return api
    }

    // MARK: Server setup

    func connect(urlString: String, trusting fingerprint: String? = nil) async throws -> ConnectResult {
        let cleaned = ServerSetupInput.normalizeServerURL(urlString)
        guard cleaned.lowercased().hasPrefix("https://"), cleaned.count > 8, let url = URL(string: cleaned) else {
            throw RivetError.invalidURL
        }
        let client = APIClient(serverURL: url, token: nil, transport: PinnedTransport(pinnedFingerprint: fingerprint))
        do {
            let status = try await client.probe()
            guard status < 500 else { throw RivetError.http(status: status, message: nil) }
        } catch RivetError.certificate where fingerprint == nil {
            // Not signed by a trusted authority: read the certificate so the user can verify its fingerprint.
            if let summary = await CertificateProbe().fetch(serverURL: url) { return .needsCertificate(summary) }
            throw RivetError.certificate
        }
        let d = UserDefaults.standard
        d.set(cleaned, forKey: Keys.server)
        d.set(fingerprint, forKey: Keys.fingerprint)
        serverURL = url
        trustedFingerprint = fingerprint
        buildClient(token: nil)
        phase = .login
        return .connected
    }

    // MARK: Auth

    func signIn(username: String, password: String, totp: String?) async throws -> SignInResult {
        guard let api else { throw RivetError.invalidURL }
        let r = try await api.login(username: username, password: password, deviceName: UIDevice.current.name, totp: totp)
        if r.requires2fa == true, r.token == nil { return .needsTwoFactor }
        guard let token = r.token else { throw RivetError.http(status: 401, message: "Invalid username or password.") }
        Keychain.set(token, for: Keys.token)
        api.token = token
        notice = nil
        phase = .main
        await refreshProfile()
        return .signedIn
    }

    func signOut(tellServer: Bool = true) async {
        if tellServer, let api, api.token != nil { try? await api.logout() }
        Keychain.set(nil, for: Keys.token)
        api?.token = nil
        profile = nil
        capabilities = .unrestricted
        hasUnread = false
        phase = serverURL == nil ? .setup : .login
    }

    func changeServer() async {
        await signOut()
        UserDefaults.standard.removeObject(forKey: Keys.server)
        UserDefaults.standard.removeObject(forKey: Keys.fingerprint)
        serverURL = nil; trustedFingerprint = nil; api = nil
        phase = .setup
    }

    func refreshProfile() async {
        guard let api else { return }
        if let p = try? await api.profile() {
            profile = p
            capabilities = Capabilities(profile: p)
        }
        if let d = try? await api.dashboard() { hasUnread = d.unread > 0 }
    }

    private func buildClient(token: String?) {
        guard let serverURL else { return }
        let client = APIClient(serverURL: serverURL, token: token, transport: PinnedTransport(pinnedFingerprint: trustedFingerprint))
        client.onUnauthorized = { [weak self] in
            Task { @MainActor in
                self?.notice = "Your session has expired. Please sign in again."
                await self?.signOut(tellServer: false)
            }
        }
        api = client
    }
}
