import SwiftUI
import LocalAuthentication
import RivetCore

// MARK: Reports

struct ReportsView: View {
    private struct Item: Identifiable { let id: String; let symbol: String; let title: String; let subtitle: String }
    private let tickets = [
        Item(id: "time", symbol: "timer", title: "Time Summary", subtitle: "Hours logged by department"),
        Item(id: "volume", symbol: "chart.line.uptrend.xyaxis", title: "Ticket Volume", subtitle: "Tickets raised per month"),
        Item(id: "byclient", symbol: "building.2", title: "Tickets by Department", subtitle: "Raised, resolved, priority breakdown"),
        Item(id: "bytech", symbol: "person", title: "Time by Technician", subtitle: "Tickets assigned/touched, time worked"),
        Item(id: "perf", symbol: "trophy", title: "Technician Performance", subtitle: "Open workload vs. resolved this year"),
        Item(id: "overview", symbol: "chart.bar", title: "Overview", subtitle: "Open tickets by priority, status, category"),
    ]
    private let support = [
        Item(id: "csat", symbol: "face.smiling", title: "Customer Satisfaction", subtitle: "CSAT ratings, trend, feedback"),
        Item(id: "rmm", symbol: "exclamationmark.triangle", title: "RMM Health", subtitle: "Alert volume, MTTA/MTTR, noisiest assets"),
        Item(id: "desk", symbol: "headphones", title: "Service Desk", subtitle: "Volume, aging, SLA, CSAT"),
        Item(id: "util", symbol: "gauge", title: "Technician Utilization", subtitle: "Capacity versus time logged"),
    ]
    private let operations = [Item(id: "expiring", symbol: "calendar.badge.exclamationmark", title: "Expiring Domains & Certs", subtitle: "Renewals in the next 30 days")]

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                section("Tickets", tickets); section("Support & Operations", support); section("Operations", operations)
            }.padding(.bottom, 20)
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle("Reports").navigationBarTitleDisplayMode(.inline)
    }

    private func section(_ title: String, _ items: [Item]) -> some View {
        VStack(spacing: 0) {
            SectionLabel(text: title, tint: true)
            ForEach(items) { i in
                NavigationLink {
                    if i.id == "overview" { OverviewReportView() } else { ReportUnavailableView(title: i.title) }
                } label: {
                    ListRowCard {
                        IconTile(systemName: i.symbol, size: 38)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(i.title).font(.subheadline.weight(.semibold))
                            Text(i.subtitle).font(.caption).foregroundColor(Palette.label2)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundColor(Palette.label3)
                    }
                }.buttonStyle(.plain)
            }
        }
    }
}

/// Reports that have not been ported yet say so plainly.
struct ReportUnavailableView: View {
    let title: String
    var body: some View {
        EmptyStateView(title: "\(title) isn't available in the iOS app yet. Open it in the web app or the Android app.", systemImage: "chart.bar")
            .navigationTitle(title).navigationBarTitleDisplayMode(.inline)
    }
}

struct OverviewReportView: View {
    @EnvironmentObject var session: Session
    @StateObject private var loader = Loader<OverviewReport>()

    var body: some View {
        LoadView(loader: loader, retry: load) { r in
            ScrollView {
                VStack(spacing: 12) {
                    if let h = r.avgResolutionHours {
                        HStack { Text("Average resolution"); Spacer(); Text(String(format: "%.1f h", h)).fontWeight(.semibold) }.card().padding(.horizontal, 16)
                    }
                    group("By priority", r.byPriority.map { ($0.priority ?? "None", $0.count) })
                    group("By status", r.byStatus.map { ($0.status ?? "None", $0.count) })
                    group("By category", r.byCategory.map { ($0.category ?? "None", $0.count) })
                }.padding(.vertical, 8)
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle("Overview").navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func group(_ title: String, _ rows: [(String, Int)]) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(title).font(.caption.weight(.semibold)).foregroundColor(Palette.label2).padding(.bottom, 4)
            ForEach(Array(rows.enumerated()), id: \.offset) { _, r in KeyValueRow(key: r.0, value: "\(r.1)") }
        }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)
    }

    private func load() async {
        guard let api = session.api else { return }
        await loader.load { try await api.overviewReport() }
    }
}

// MARK: Profile

struct ProfileView: View {
    @EnvironmentObject var session: Session
    @State private var name = ""
    @State private var email = ""
    @State private var saving = false
    @State private var message: String?
    @State private var failed = false
    @State private var showPassword = false
    @Environment(\.openURL) private var openURL

    var body: some View {
        ScrollView {
            VStack(spacing: 12) {
                VStack(spacing: 4) {
                    Text(Initials.letter(name)).font(.largeTitle.weight(.semibold)).frame(width: 64, height: 64)
                        .background(Palette.brand, in: Circle()).foregroundColor(Palette.onBrand)
                    Text(name).font(.title3.weight(.semibold)).padding(.top, 6)
                    Text(email).font(.footnote).opacity(0.75)
                }.frame(maxWidth: .infinity).padding(.vertical, 20).background(Palette.container).foregroundColor(Palette.onContainer)

                VStack(alignment: .leading, spacing: 10) {
                    Text("Account").font(.caption.weight(.semibold)).foregroundColor(Palette.label2)
                    TextField("Full Name", text: $name).padding(12).background(Palette.fill, in: RoundedRectangle(cornerRadius: 10))
                    TextField("Email", text: $email).keyboardType(.emailAddress).textInputAutocapitalization(.never)
                        .padding(12).background(Palette.fill, in: RoundedRectangle(cornerRadius: 10))
                    if let message { if failed { InlineError(message: message) } else { Text(message).font(.footnote).foregroundColor(Palette.brand) } }
                    Button { save() } label: { if saving { ProgressView().tint(Palette.onBrand) } else { Text("Save Profile") } }
                        .buttonStyle(PrimaryButtonStyle()).disabled(saving || name.isEmpty || email.isEmpty)
                }.card().padding(.horizontal, 16)

                VStack(alignment: .leading, spacing: 0) {
                    Text("Security & Server").font(.caption.weight(.semibold)).foregroundColor(Palette.label2).padding(16)
                    settingRow("touchid", "Biometric Lock", "Lock after 5 min in background") {
                        Toggle("", isOn: Binding(get: { session.biometricLock }, set: { setLock($0) })).labelsHidden().tint(Palette.brand)
                    }
                    settingRow("lock", "Change Password", "Update your account password") {
                        Button { showPassword = true } label: { Image(systemName: "chevron.right").foregroundColor(Palette.label3) }
                    }
                    settingRow("server.rack", "Server", session.serverDisplay) {
                        Button("Change") { Task { await session.changeServer() } }.font(.subheadline)
                    }
                    settingRow("bell.badge", "Notifications", "Manage push notification settings") {
                        Button { if let u = URL(string: UIApplication.openSettingsURLString) { openURL(u) } } label: {
                            Image(systemName: "arrow.up.forward.square").foregroundColor(Palette.label3)
                        }
                    }
                }.background(Palette.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous)).padding(.horizontal, 16)

                VStack(alignment: .leading, spacing: 10) {
                    Text("Appearance").font(.caption.weight(.semibold)).foregroundColor(Palette.label2)
                    Picker("Theme", selection: $session.themeMode) { ForEach(ThemeMode.allCases) { Text($0.title).tag($0) } }
                        .pickerStyle(.segmented)
                }.card().padding(.horizontal, 16)

                Button(role: .destructive) { Task { await session.signOut() } } label: {
                    Label("Sign Out", systemImage: "rectangle.portrait.and.arrow.right").frame(maxWidth: .infinity)
                }.buttonStyle(.bordered).padding(.horizontal, 16)

                Text(appVersion).font(.footnote).foregroundColor(Palette.label3).padding(.top, 8)
            }.padding(.bottom, 20)
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle("Profile").navigationBarTitleDisplayMode(.inline)
        .sheet(isPresented: $showPassword) { ChangePasswordSheet(name: name, email: email).presentationDetents([.medium]) }
        .onAppear { if let p = session.profile { name = p.name; email = p.email } }
    }

    private var appVersion: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
        return "RivetIT \(v) (\(b))"
    }

    private func settingRow<Trailing: View>(_ symbol: String, _ title: String, _ subtitle: String, @ViewBuilder trailing: () -> Trailing) -> some View {
        HStack(spacing: 12) {
            IconTile(systemName: symbol, size: 36)
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.subheadline.weight(.semibold))
                Text(subtitle).font(.caption).foregroundColor(Palette.label2).lineLimit(1)
            }
            Spacer()
            trailing()
        }.padding(.horizontal, 16).padding(.vertical, 8)
    }

    /// Turning the lock on proves the device can authenticate first, so the user cannot lock themselves out.
    private func setLock(_ on: Bool) {
        guard on else { session.biometricLock = false; return }
        let ctx = LAContext(); var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else {
            failed = true; message = "Set up a passcode, Face ID or Touch ID on this device first."; return
        }
        ctx.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Turn on the RivetIT app lock") { ok, _ in
            DispatchQueue.main.async { if ok { session.biometricLock = true } }
        }
    }

    private func save() {
        guard let api = session.api, !saving else { return }
        saving = true; message = nil
        Task {
            defer { saving = false }
            do {
                try await api.updateProfile(UpdateProfileRequest(name: name.trimmingCharacters(in: .whitespaces), email: email.trimmingCharacters(in: .whitespaces)))
                await session.refreshProfile(); failed = false; message = "Profile saved."
            } catch { failed = true; message = userMessage(for: error) }
        }
    }
}

struct ChangePasswordSheet: View {
    let name: String
    let email: String
    @EnvironmentObject var session: Session
    @Environment(\.dismiss) private var dismiss
    @State private var current = ""
    @State private var new = ""
    @State private var confirm = ""
    @State private var saving = false
    @State private var error: String?

    private var valid: Bool { !current.isEmpty && new.count >= 8 && new == confirm }

    var body: some View {
        NavigationStack {
            Form {
                SecureField("Current password", text: $current).textContentType(.password)
                SecureField("New password (8+ characters)", text: $new).textContentType(.newPassword)
                SecureField("Confirm new password", text: $confirm).textContentType(.newPassword)
                if !confirm.isEmpty && new != confirm { InlineError(message: "The passwords do not match.") }
                if let error { InlineError(message: error) }
            }
            .navigationTitle("Change Password").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.disabled(saving) }
                ToolbarItem(placement: .confirmationAction) {
                    Button { save() } label: { if saving { ProgressView() } else { Text("Save") } }.disabled(!valid || saving)
                }
            }
        }
    }

    private func save() {
        guard let api = session.api else { return }
        saving = true; error = nil
        Task {
            defer { saving = false }
            do { try await api.updateProfile(UpdateProfileRequest(name: name, email: email, currentPassword: current, newPassword: new)); dismiss() }
            catch { self.error = userMessage(for: error) }
        }
    }
}

// MARK: Knowledge base

struct KnowledgeBaseView: View {
    @EnvironmentObject var session: Session
    @StateObject private var loader = PagedLoader<KbArticleSummary>()
    @State private var search = ""
    @State private var categories: [KbCategory] = []
    @State private var categoryIndex = 0
    private struct Key: Equatable { var search: String; var category: Int }

    var body: some View {
        VStack(spacing: 0) {
            SearchField(placeholder: "Search articles…", text: $search).padding(.horizontal, 16).padding(.vertical, 8)
            FilterChips(items: ["All"] + categories.map(\.name), selection: $categoryIndex).padding(.bottom, 8)
            let state = loader.state
            if let error = state.error, state.items.isEmpty { ErrorStateView(message: error, retry: reload) }
            else if state.items.isEmpty && state.isRefreshing { LoadingStateView() }
            else if state.items.isEmpty { EmptyStateView(title: "No articles found", systemImage: "book") }
            else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(state.items) { a in
                            NavigationLink(value: AppRoute.kbArticle(a.id)) {
                                ListRowCard {
                                    VStack(alignment: .leading, spacing: 4) {
                                        Text(a.title).font(.subheadline.weight(.semibold)).multilineTextAlignment(.leading)
                                        HStack(spacing: 8) {
                                            if let c = a.categoryName { Pill(text: c, small: true) }
                                            Text(RivetDate.display(a.updatedAt)).font(.caption).foregroundColor(Palette.label2)
                                        }
                                    }
                                    Spacer()
                                }
                            }.buttonStyle(.plain)
                            .task { await loader.loadMoreIfNeeded(current: a, fetchPage) }
                        }
                        if state.isLoadingMore { ProgressView().padding() }
                    }
                }.refreshable { await reload() }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle("Knowledge Base").navigationBarTitleDisplayMode(.inline)
        .task { categories = (try? await session.api?.kbCategories()) ?? [] }
        .task(id: Key(search: search, category: categoryIndex)) {
            if !search.isEmpty { try? await Task.sleep(nanoseconds: 350_000_000) }
            if Task.isCancelled { return }
            await reload()
        }
    }

    private func fetchPage(_ page: Int) async throws -> Paged<KbArticleSummary> {
        let cat = categoryIndex > 0 && categoryIndex <= categories.count ? categories[categoryIndex - 1].id : nil
        return try await session.requireAPI().kbArticles(categoryId: cat, search: search, page: page)
    }
    private func reload() async { await loader.reload(fetchPage) }
}

struct KbArticleView: View {
    let id: Int
    @EnvironmentObject var session: Session
    @Environment(\.openURL) private var openURL
    @StateObject private var loader = Loader<KbArticleDetail>()

    var body: some View {
        LoadView(loader: loader, retry: load) { a in
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    if let c = a.categoryName { Pill(text: c, small: true) }
                    Text(a.title).font(.title2.weight(.bold))
                    // Article HTML is shown as text; embedded images and rich formatting are not rendered yet.
                    Text(HTMLText.plain(a.content ?? "")).font(.body)
                    if let files = a.attachments, !files.isEmpty {
                        Divider()
                        Text("Attachments").font(.caption.weight(.semibold)).foregroundColor(Palette.label2)
                        ForEach(files) { f in Button { open(f.url) } label: { Label(f.name, systemImage: "paperclip") } }
                    }
                }.padding(20).frame(maxWidth: .infinity, alignment: .leading)
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle(loader.value?.title ?? "Article").navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    /// Only http(s) links are opened, the same rule the Android app applies to KB attachments.
    private func open(_ raw: String) {
        guard let base = session.serverURL, let url = URL(string: raw, relativeTo: base)?.absoluteURL,
              ["http", "https"].contains(url.scheme?.lowercased() ?? "") else { return }
        openURL(url)
    }

    private func load() async {
        guard let api = session.api else { return }
        await loader.load { try await api.kbArticle(id) }
    }
}

// MARK: Credentials

/// Credentials sit behind Face ID / Touch ID / passcode. Names and URLs are listed; revealing a secret is not in this
/// build (the Android app uses a signed biometric challenge for that).
struct CredentialsView: View {
    @EnvironmentObject var session: Session
    @StateObject private var loader = PagedLoader<CredentialSummary>()
    @State private var unlocked = false
    @State private var gateMessage: String?
    @State private var search = ""

    var body: some View {
        Group {
            if unlocked { list } else { gate }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle("Credentials").navigationBarTitleDisplayMode(.inline)
    }

    private var gate: some View {
        VStack(spacing: 14) {
            Image(systemName: "faceid").font(.system(size: 80, weight: .light)).foregroundColor(Palette.brand)
            Text("Authentication Required").font(.headline)
            Text("Verify your identity to view credentials").foregroundColor(Palette.label2)
            if let gateMessage { InlineError(message: gateMessage).padding(.horizontal, 32) }
            Button { authenticate() } label: { Label("Authenticate", systemImage: "faceid") }
                .buttonStyle(PrimaryButtonStyle()).frame(maxWidth: 240).padding(.top, 8)
        }.frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private var list: some View {
        VStack(spacing: 0) {
            SearchField(placeholder: "Search credentials…", text: $search).padding(.horizontal, 16).padding(.vertical, 8)
            let state = loader.state
            if let error = state.error, state.items.isEmpty { ErrorStateView(message: error, retry: reload) }
            else if state.items.isEmpty && state.isRefreshing { LoadingStateView() }
            else if state.items.isEmpty { EmptyStateView(title: "No credentials found", systemImage: "key") }
            else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(state.items) { c in
                            ListRowCard {
                                IconTile(systemName: "key")
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(c.name).font(.subheadline.weight(.semibold))
                                    Text([c.client, c.uri].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " • "))
                                        .font(.caption).foregroundColor(Palette.label2).lineLimit(1)
                                }
                                Spacer()
                            }.task { await loader.loadMoreIfNeeded(current: c, fetchPage) }
                        }
                    }
                }.refreshable { await reload() }
            }
        }
        .task(id: search) {
            if !search.isEmpty { try? await Task.sleep(nanoseconds: 350_000_000) }
            if Task.isCancelled { return }
            await reload()
        }
    }

    private func fetchPage(_ page: Int) async throws -> Paged<CredentialSummary> {
        try await session.requireAPI().credentials(search: search, page: page)
    }
    private func reload() async { await loader.reload(fetchPage) }

    private func authenticate() {
        let ctx = LAContext(); var err: NSError?
        guard ctx.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else {
            gateMessage = "No Face ID, Touch ID or passcode is set up on this device. Set one up in Settings to view credentials."; return
        }
        gateMessage = nil
        ctx.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "Access credentials") { ok, error in
            DispatchQueue.main.async {
                if ok { unlocked = true }
                else if let e = error as? LAError, e.code != .userCancel, e.code != .appCancel { gateMessage = e.localizedDescription }
            }
        }
    }
}

// MARK: Search

struct SearchView: View {
    @EnvironmentObject var session: Session
    @State private var query = ""
    @State private var result: SearchResult?
    @State private var loading = false
    @State private var error: String?
    @FocusState private var focused: Bool

    var body: some View {
        VStack(spacing: 0) {
            SearchField(placeholder: "Search…", text: $query).focused($focused).padding(.horizontal, 16).padding(.vertical, 8)
            if query.trimmingCharacters(in: .whitespaces).count < 2 {
                EmptyStateView(title: "Type at least 2 characters to search", systemImage: "magnifyingglass")
            } else if let error { ErrorStateView(message: error, retry: nil) }
            else if loading && result == nil { LoadingStateView() }
            else if let r = result {
                if isEmpty(r) { EmptyStateView(title: "No results", systemImage: "magnifyingglass") }
                else {
                    ScrollView { LazyVStack(alignment: .leading, spacing: 0) { results(r) }.padding(.bottom, 20) }
                }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle("Search").navigationBarTitleDisplayMode(.inline)
        .onAppear { focused = true }
        .task(id: query) {
            let q = query.trimmingCharacters(in: .whitespaces)
            guard q.count >= 2 else { result = nil; error = nil; return }
            try? await Task.sleep(nanoseconds: 400_000_000)
            if Task.isCancelled { return }
            loading = true; error = nil
            do { result = try await session.requireAPI().search(q) } catch { self.error = userMessage(for: error) }
            loading = false
        }
    }

    private func isEmpty(_ r: SearchResult) -> Bool {
        r.tickets.isEmpty && r.clients.isEmpty && r.assets.isEmpty && r.contacts.isEmpty && r.credentials.isEmpty && r.articles.isEmpty
    }

    @ViewBuilder private func results(_ r: SearchResult) -> some View {
        if !r.tickets.isEmpty {
            SectionLabel(text: "Tickets")
            ForEach(r.tickets) { t in NavigationLink(value: AppRoute.ticket(t.id)) { TicketRow(ticket: t) }.buttonStyle(.plain) }
        }
        if !r.clients.isEmpty {
            SectionLabel(text: "Departments")
            ForEach(r.clients) { c in
                NavigationLink(value: AppRoute.client(c.id)) { ListRowCard { AvatarCircle(letter: Initials.letter(c.name), size: 38); Text(c.name).font(.subheadline.weight(.semibold)); Spacer() } }.buttonStyle(.plain)
            }
        }
        if !r.assets.isEmpty {
            SectionLabel(text: "Assets")
            ForEach(r.assets) { a in NavigationLink(value: AppRoute.asset(a.id)) { AssetRow(asset: a) }.buttonStyle(.plain) }
        }
        if !r.contacts.isEmpty {
            SectionLabel(text: "Contacts")
            ForEach(r.contacts) { c in
                ListRowCard { AvatarCircle(letter: Initials.letter(c.name), size: 38)
                    VStack(alignment: .leading) { Text(c.name).font(.subheadline.weight(.semibold)); if let e = c.email { Text(e).font(.caption).foregroundColor(Palette.label2) } }; Spacer() }
            }
        }
        if !r.articles.isEmpty {
            SectionLabel(text: "Knowledge base")
            ForEach(r.articles) { a in NavigationLink(value: AppRoute.kbArticle(a.id)) { ListRowCard { Image(systemName: "book"); Text(a.title).font(.subheadline); Spacer() } }.buttonStyle(.plain) }
        }
    }
}
