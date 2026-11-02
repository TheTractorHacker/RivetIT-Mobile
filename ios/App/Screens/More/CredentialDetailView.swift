import SwiftUI
import UniformTypeIdentifiers
import RivetCore

/// One credential. Its secrets are only fetched after Face ID / Touch ID / passcode signs a single-use server
/// challenge; leaving the app clears them from the screen, so opening it again asks again.
@MainActor struct CredentialDetailView: View {
    let id: Int
    @EnvironmentObject var session: Session
    @Environment(\.scenePhase) private var scenePhase
    @State private var detail: CredentialDetail?
    @State private var busy = false
    @State private var error: String?
    @State private var showPassword = false
    @State private var toast: String?
    @State private var asked = false

    var body: some View {
        Group {
            if let detail { content(detail) } else { gate }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle(detail?.name ?? "Credential").navigationBarTitleDisplayMode(.inline)
        .task { if !asked { asked = true; await reveal() } }          // opening a credential asks right away
        .onChange(of: scenePhase) { phase in
            if phase != .active { detail = nil; showPassword = false }      // secrets never stay on screen in the background
        }
        .overlay(alignment: .bottom) {
            if let toast {
                Text(toast).font(.subheadline.weight(.medium)).padding(.horizontal, 16).padding(.vertical, 10)
                    .background(.regularMaterial, in: Capsule()).padding(.bottom, 24).transition(.opacity)
            }
        }
        .animation(.default, value: toast)
    }

    private var gate: some View {
        VStack(spacing: 14) {
            Image(systemName: "faceid").font(.system(size: 80, weight: .light)).foregroundColor(Palette.brand)
            Text("Verify your identity").font(.headline)
            Text("Your device checks it's you before this credential is shown.").foregroundColor(Palette.label2)
                .multilineTextAlignment(.center).padding(.horizontal, 32)
            if let error { InlineError(message: error).padding(.horizontal, 32) }
            Button { Task { await reveal() } } label: {
                if busy { ProgressView().tint(Palette.onBrand) } else { Label("Verify", systemImage: "faceid") }
            }
            .buttonStyle(PrimaryButtonStyle()).frame(maxWidth: 240).padding(.top, 8).disabled(busy)
        }.frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func content(_ c: CredentialDetail) -> some View {
        ScrollView {
            VStack(spacing: 12) {
                VStack(alignment: .leading, spacing: 0) {
                    if let client = c.client, !client.isEmpty { row("Department", client); Divider() }
                    if let user = c.username, !user.isEmpty { row("Username", user, copy: ("Username", false)); Divider() }
                    passwordRow(c.password)
                }
                .background(Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))

                if hasValue(c.uri) || hasValue(c.uri2) {
                    VStack(alignment: .leading, spacing: 0) {
                        if let uri = c.uri, !uri.isEmpty { linkRow("Address", uri) }
                        if hasValue(c.uri), hasValue(c.uri2) { Divider() }
                        if let uri = c.uri2, !uri.isEmpty { linkRow("Second address", uri) }
                    }
                    .background(Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                if let note = c.note, !note.isEmpty {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("Note").font(.footnote.weight(.semibold)).foregroundColor(Palette.label2)
                        Text(HTMLText.plain(note)).font(.subheadline).textSelection(.enabled)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading).padding(14)
                    .background(Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                }
                Text("The copied password is removed from the clipboard after a minute.")
                    .font(.footnote).foregroundColor(Palette.label3).multilineTextAlignment(.center).padding(.top, 4)
            }.padding(16)
        }
    }

    private func hasValue(_ s: String?) -> Bool { !(s ?? "").isEmpty }

    private func row(_ title: String, _ value: String, copy: (label: String, sensitive: Bool)? = nil) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.caption).foregroundColor(Palette.label2)
                Text(value).font(.body).textSelection(.enabled)
            }
            Spacer()
            if let copy {
                Button { copyToClipboard(value, label: copy.label, sensitive: copy.sensitive) } label: { Image(systemName: "doc.on.doc") }
                    .accessibilityLabel("Copy \(copy.label.lowercased())")
            }
        }.padding(14)
    }

    @ViewBuilder private func passwordRow(_ password: String?) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text("Password").font(.caption).foregroundColor(Palette.label2)
                if let password, !password.isEmpty {
                    Text(showPassword ? password : String(repeating: "•", count: min(max(password.count, 8), 20)))
                        .font(.system(.body, design: .monospaced)).textSelection(.enabled)
                } else {
                    Text("No password stored").foregroundColor(Palette.label3)
                }
            }
            Spacer()
            if let password, !password.isEmpty {
                Button { showPassword.toggle() } label: { Image(systemName: showPassword ? "eye.slash" : "eye") }
                    .accessibilityLabel(showPassword ? "Hide password" : "Show password").padding(.trailing, 10)
                Button { copyToClipboard(password, label: "Password", sensitive: true) } label: { Image(systemName: "doc.on.doc") }
                    .accessibilityLabel("Copy password")
            }
        }.padding(14)
    }

    @ViewBuilder private func linkRow(_ title: String, _ value: String) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.caption).foregroundColor(Palette.label2)
                Text(value).font(.body).lineLimit(2).textSelection(.enabled)
            }
            Spacer()
            // Only web addresses open; anything else (file:, javascript:, app schemes) is shown as text only.
            if let url = URL(string: value), ["http", "https"].contains(url.scheme?.lowercased() ?? "") {
                Link(destination: url) { Image(systemName: "arrow.up.right.square") }.accessibilityLabel("Open \(title.lowercased())")
            }
        }.padding(14)
    }

    private func copyToClipboard(_ value: String, label: String, sensitive: Bool) {
        if sensitive {
            UIPasteboard.general.setItems([[UTType.plainText.identifier: value]],
                                          options: [.localOnly: true, .expirationDate: Date().addingTimeInterval(60)])
        } else {
            UIPasteboard.general.string = value
        }
        toast = "\(label) copied"
        Task { try? await Task.sleep(nanoseconds: 1_800_000_000); toast = nil }
    }

    /// Challenge -> biometric signature -> register this device's key -> fetch the credential.
    private func reveal() async {
        guard !busy, let api = session.api else { return }
        busy = true; error = nil
        defer { busy = false }
        do {
            let challenge = try await api.biometricChallenge()
            guard let bytes = BiometricEncoding.base64URLDecode(challenge.challenge) else { throw BiometricSignerError.badChallenge }
            let assertion = try await Task.detached(priority: .userInitiated) {
                try BiometricSigner.sign(challenge: bytes, reason: "Show this credential")
            }.value
            // Idempotent; covers the first use and a key recreated after a reset.
            try await api.registerBiometricKey(pem: assertion.publicKeyPEM)
            detail = try await api.credential(id: id, challengeToken: challenge.challengeToken, signature: assertion.signatureBase64)
        } catch BiometricSignerError.cancelled {
            // Stay on the gate; the Verify button tries again.
        } catch let e as BiometricSignerError {
            error = e.errorDescription
        } catch {
            self.error = userMessage(for: error)
        }
    }
}
