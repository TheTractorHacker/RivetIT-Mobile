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

/// A captioned, filled input row used by the sign-in form.
@MainActor private struct FormRow<Field: View>: View {
    let title: String
    let systemImage: String
    let field: Field
    init(_ title: String, systemImage: String, @ViewBuilder field: () -> Field) {
        self.title = title; self.systemImage = systemImage; self.field = field()
    }
    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(title).font(.footnote.weight(.semibold)).foregroundColor(Palette.label2)
            HStack(spacing: 12) {
                Image(systemName: systemImage).foregroundColor(Palette.label2).frame(width: 22)
                field
            }
            .padding(.horizontal, 14).frame(minHeight: 50)
            .background(Palette.fill, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
    }
}

/// Sign-in is two steps: email + password, then (only for accounts with 2FA) a separate verification screen.
@MainActor struct LoginView: View {
    @EnvironmentObject var session: Session
    @State private var username = ""
    @State private var password = ""
    @State private var code = ""
    @State private var showPassword = false
    @State private var needsCode = false
    @State private var loading = false
    @State private var error: String?
    @FocusState private var focus: Field?
    private enum Field { case username, password, code }

    private var canSubmit: Bool {
        if loading { return false }
        if needsCode { return !code.trimmingCharacters(in: .whitespaces).isEmpty }
        return !username.isEmpty && !password.isEmpty
    }

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                if needsCode { twoFactorStep.transition(.move(edge: .trailing).combined(with: .opacity)) }
                else { passwordStep.transition(.move(edge: .leading).combined(with: .opacity)) }
            }
            .frame(maxWidth: 440)
            .padding(.horizontal, 20).padding(.bottom, 30)
            .frame(maxWidth: .infinity)
        }
        .background(Palette.background.ignoresSafeArea())
        .scrollDismissesKeyboard(.interactively)
        .animation(.easeInOut(duration: 0.25), value: needsCode)
    }

    // MARK: Step 1 - email and password

    private var passwordStep: some View {
        VStack(spacing: 0) {
            VStack(spacing: 12) {
                BrandMark(size: 84)
                Text(session.branding.name).font(.title2.weight(.bold)).multilineTextAlignment(.center)
                Text("Sign in to continue").font(.subheadline).foregroundColor(Palette.label2)
                Label(session.serverDisplay, systemImage: "lock.shield").font(.caption.monospaced())
                    .foregroundColor(Palette.label2).lineLimit(1).truncationMode(.middle)
                    .padding(.horizontal, 12).padding(.vertical, 6)
                    .background(Palette.fill, in: Capsule())
                    .accessibilityLabel("Server \(session.serverDisplay)")
            }.padding(.top, 48).padding(.bottom, 28)

            if let notice = session.notice { InlineError(message: notice).padding(.bottom, 12) }

            VStack(spacing: 16) {
                FormRow("Username or email", systemImage: "person") {
                    TextField("name@company.com", text: $username)
                        .textContentType(.username).keyboardType(.emailAddress)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                        .focused($focus, equals: .username).submitLabel(.next)
                        .onSubmit { focus = .password }
                }
                FormRow("Password", systemImage: "lock") {
                    Group {
                        if showPassword { TextField("Password", text: $password) } else { SecureField("Password", text: $password) }
                    }
                    .textContentType(.password).textInputAutocapitalization(.never).autocorrectionDisabled()
                    .focused($focus, equals: .password).submitLabel(.go)
                    .onSubmit { signIn() }
                    Button { showPassword.toggle() } label: {
                        Image(systemName: showPassword ? "eye.slash" : "eye").foregroundColor(Palette.label2)
                    }.accessibilityLabel(showPassword ? "Hide password" : "Show password")
                }
                if let error { InlineError(message: error) }
                Button { signIn() } label: {
                    if loading { ProgressView().tint(Palette.onBrand) } else { Text("Sign in") }
                }
                .buttonStyle(PrimaryButtonStyle()).disabled(!canSubmit).opacity(canSubmit ? 1 : 0.55)
            }
            .padding(20)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))

            Button("Use a different server") { Task { await session.changeServer() } }
                .font(.subheadline.weight(.medium)).padding(.top, 22)
        }
    }

    // MARK: Step 2 - two-step verification

    private var twoFactorStep: some View {
        VStack(spacing: 0) {
            VStack(spacing: 12) {
                Image(systemName: "lock.shield.fill").font(.system(size: 38)).foregroundColor(Palette.onBrand)
                    .frame(width: 84, height: 84)
                    .background(Palette.brandGradient, in: RoundedRectangle(cornerRadius: 24, style: .continuous))
                    .accessibilityHidden(true)
                Text("Two-step verification").font(.title2.weight(.bold)).multilineTextAlignment(.center)
                Text("Your password was accepted. Enter the code from your authenticator app to finish signing in as \(username).")
                    .font(.subheadline).foregroundColor(Palette.label2).multilineTextAlignment(.center)
            }.padding(.top, 56).padding(.bottom, 28)

            VStack(spacing: 14) {
                TextField("000000", text: $code)
                    .font(.system(size: 30, weight: .semibold, design: .monospaced)).multilineTextAlignment(.center)
                    .keyboardType(.asciiCapable).textContentType(.oneTimeCode)
                    .textInputAutocapitalization(.never).autocorrectionDisabled()
                    .focused($focus, equals: .code).submitLabel(.go).onSubmit { signIn() }
                    .padding(.vertical, 14)
                    .background(Palette.fill, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .accessibilityLabel("Authentication code")
                    .onAppear { focus = .code }
                    .onChange(of: code) { value in
                        // A full six-digit code submits itself; recovery codes (letters and a dash) use the button.
                        if value.count == 6, value.allSatisfy(\.isNumber) { signIn() }
                    }
                Text("Lost your phone? Enter one of your recovery codes instead.")
                    .font(.footnote).foregroundColor(Palette.label2).multilineTextAlignment(.center)
                if let error { InlineError(message: error) }
                Button { signIn() } label: {
                    if loading { ProgressView().tint(Palette.onBrand) } else { Text("Verify and sign in") }
                }
                .buttonStyle(PrimaryButtonStyle()).disabled(!canSubmit).opacity(canSubmit ? 1 : 0.55)
            }
            .padding(20)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 22, style: .continuous))

            Button { needsCode = false; code = ""; error = nil } label: { Label("Back to sign in", systemImage: "chevron.left") }
                .font(.subheadline.weight(.medium)).disabled(loading).padding(.top, 22)
        }
    }

    private func signIn() {
        guard canSubmit else { return }
        loading = true; error = nil
        Task {
            defer { loading = false }
            do {
                switch try await session.signIn(username: username.trimmingCharacters(in: .whitespaces), password: password,
                                                totp: needsCode ? code.trimmingCharacters(in: .whitespaces) : nil) {
                case .signedIn: break
                case .needsTwoFactor: needsCode = true; error = nil
                }
            } catch {
                self.error = userMessage(for: error)
                if needsCode { code = "" }        // a wrong code is cleared so the next try starts clean
            }
        }
    }
}
