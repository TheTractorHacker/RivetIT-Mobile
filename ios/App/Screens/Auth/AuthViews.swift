import SwiftUI
import RivetCore

@MainActor private struct LabeledField<Field: View>: View {
    let systemImage: String
    let field: Field
    init(systemImage: String, @ViewBuilder field: () -> Field) { self.systemImage = systemImage; self.field = field() }
    var body: some View {
        HStack(spacing: 12) {
            Image(systemName: systemImage).foregroundColor(Palette.label2).frame(width: 22)
            field
        }
        .padding(.horizontal, 14).frame(minHeight: 54)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
}

@MainActor struct ServerSetupView: View {
    @EnvironmentObject var session: Session
    @State private var url = "https://"
    @State private var loading = false
    @State private var error: String?
    @State private var pendingCertificate: CertificateSummary?

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                BrandMark(size: 76).padding(.top, 70)
                Text("Connect to RivetIT").font(.largeTitle.weight(.bold)).padding(.top, 24)
                Text("Enter your RivetIT server address.").foregroundColor(Palette.label2).padding(.top, 4)

                LabeledField(systemImage: "link") {
                    TextField("https://rivetit.example.com", text: $url)
                        .keyboardType(.URL).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .submitLabel(.go).onSubmit { connect() }
                }.padding(.top, 34)

                if let error { InlineError(message: error).padding(.top, 8) }

                Button { connect() } label: {
                    if loading { ProgressView().tint(Palette.onBrand) } else { Text("Connect") }
                }
                .buttonStyle(PrimaryButtonStyle()).disabled(loading).padding(.top, 20)

                Text("Not affiliated with ITFlow LLC.").font(.footnote).foregroundColor(Palette.label3).padding(.top, 40)
            }.padding(.horizontal, 24)
        }
        .background(Palette.background.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
        .sheet(item: $pendingCertificate) { cert in
            CertificateTrustSheet(certificate: cert,
                                  trust: { connect(trusting: cert.fingerprint) },
                                  cancel: { pendingCertificate = nil })
                .presentationDetents([.large])
        }
    }

    private func connect(trusting fingerprint: String? = nil) {
        guard !loading else { return }
        loading = true; error = nil; pendingCertificate = nil
        Task {
            defer { loading = false }
            do {
                switch try await session.connect(urlString: url, trusting: fingerprint) {
                case .connected: break
                case .needsCertificate(let cert): pendingCertificate = cert
                }
            } catch RivetError.invalidURL {
                error = "URL must start with https://"
            } catch RivetError.certificate {
                error = "SSL error and could not retrieve the server certificate."
            } catch let e {
                error = "Cannot reach server. Check the URL.\n" + userMessage(for: e)
            }
        }
    }
}

extension CertificateSummary: Identifiable { var id: String { fingerprint } }

/// The user must type the last six characters of the fingerprint before a self-signed certificate is trusted.
@MainActor struct CertificateTrustSheet: View {
    let certificate: CertificateSummary
    var trust: () -> Void
    var cancel: () -> Void
    @State private var confirmation = ""

    private var confirmed: Bool { ServerSetupInput.fingerprintSuffixMatches(confirmation, fingerprint: certificate.fingerprint) }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Image(systemName: "exclamationmark.triangle.fill").font(.system(size: 36)).foregroundColor(Palette.danger)
                    .frame(maxWidth: .infinity)
                Text("Untrusted Certificate").font(.title2.weight(.bold)).frame(maxWidth: .infinity)
                Text("This server's certificate is not signed by a trusted authority. If you did not set up this server yourself, or you're on a network you don't fully trust (e.g. public Wi-Fi), someone could be intercepting your connection. Only continue if you personally recognize this server.")
                    .foregroundColor(Palette.danger)
                Text("Issued to: \(certificate.commonName ?? "unknown")")
                if let end = certificate.notAfter {
                    Text("Expires: \(end.formatted(date: .abbreviated, time: .shortened))")
                }
                Text("SHA-256 Fingerprint:").font(.caption.weight(.semibold)).foregroundColor(Palette.label2)
                Text(certificate.fingerprint).font(.system(.footnote, design: .monospaced)).padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Palette.fill, in: RoundedRectangle(cornerRadius: 8)).textSelection(.enabled)
                Text("To confirm you've verified this fingerprint (e.g. against your server admin), type its last 6 characters: \(ServerSetupInput.suffixHint(fingerprint: certificate.fingerprint))")
                    .font(.footnote).foregroundColor(Palette.label2)
                TextField("", text: $confirmation).textFieldStyle(.roundedBorder).font(.system(.body, design: .monospaced))
                    .textInputAutocapitalization(.characters).autocorrectionDisabled()
                HStack {
                    Button("Cancel", action: cancel).frame(maxWidth: .infinity)
                    Button { trust() } label: { Label("Trust & Connect", systemImage: "checkmark.shield") }
                        .buttonStyle(.borderedProminent).tint(Palette.danger).disabled(!confirmed).frame(maxWidth: .infinity)
                }.padding(.top, 8)
            }.padding(24)
        }
    }
}

@MainActor struct LoginView: View {
    @EnvironmentObject var session: Session
    @State private var username = ""
    @State private var password = ""
    @State private var code = ""
    @State private var showPassword = false
    @State private var needsCode = false
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                BrandMark(size: 76).padding(.top, 70)
                Text("Sign in").font(.largeTitle.weight(.bold)).padding(.top, 24)
                Text(session.serverDisplay).font(.footnote.monospaced()).foregroundColor(Palette.label2).padding(.top, 4)

                if let notice = session.notice { InlineError(message: notice).padding(.top, 14) }

                LabeledField(systemImage: "person") {
                    TextField("Username or email", text: $username)
                        .textContentType(.username).keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                }.padding(.top, 30)

                LabeledField(systemImage: "lock") {
                    Group {
                        if showPassword { TextField("Password", text: $password) } else { SecureField("Password", text: $password) }
                    }.textContentType(.password).textInputAutocapitalization(.never).autocorrectionDisabled()
                        .submitLabel(.go).onSubmit { signIn() }
                    Button { showPassword.toggle() } label: { Image(systemName: showPassword ? "eye.slash" : "eye").foregroundColor(Palette.label2) }
                        .accessibilityLabel(showPassword ? "Hide password" : "Show password")
                }.padding(.top, 12)

                if needsCode {
                    LabeledField(systemImage: "number") {
                        TextField("Authentication code", text: $code).keyboardType(.numberPad).textContentType(.oneTimeCode)
                    }.padding(.top, 12)
                }

                if let error { InlineError(message: error).padding(.top, 10) }

                Button { signIn() } label: {
                    if loading { ProgressView().tint(Palette.onBrand) } else { Text("Sign in") }
                }
                .buttonStyle(PrimaryButtonStyle()).disabled(loading || username.isEmpty || password.isEmpty).padding(.top, 20)

                Button("Change server") { Task { await session.changeServer() } }.padding(.top, 20)
            }.padding(.horizontal, 24)
        }
        .background(Palette.background.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
    }

    private func signIn() {
        guard !loading else { return }
        loading = true; error = nil
        Task {
            defer { loading = false }
            do {
                switch try await session.signIn(username: username.trimmingCharacters(in: .whitespaces), password: password,
                                                totp: needsCode ? code : nil) {
                case .signedIn: break
                case .needsTwoFactor: needsCode = true; error = "Enter your authentication code."
                }
            } catch { self.error = userMessage(for: error) }
        }
    }
}
