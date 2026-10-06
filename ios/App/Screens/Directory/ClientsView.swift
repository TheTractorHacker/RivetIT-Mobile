import SwiftUI
import RivetCore

struct ClientsView: View {
    @EnvironmentObject var session: Session
    @StateObject private var loader = PagedLoader<ClientSummary>()
    @State private var search = ""

    var body: some View {
        VStack(spacing: 0) {
            SearchField(placeholder: "Search departments…", text: $search).padding(.horizontal, 16).padding(.vertical, 8)
            let state = loader.state
            if let error = state.error, state.items.isEmpty {
                ErrorStateView(message: error, retry: reload)
            } else if state.items.isEmpty && state.isRefreshing {
                LoadingStateView()
            } else if state.items.isEmpty {
                EmptyStateView(title: "No departments found", systemImage: "building.2")
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(state.items) { c in
                            NavigationLink(value: AppRoute.client(c.id)) {
                                ListRowCard {
                                    AvatarCircle(letter: Initials.letter(c.name))
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(c.name).font(.subheadline.weight(.semibold))
                                        let place = [c.city, c.state].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")
                                        if !place.isEmpty { Text(place).font(.caption).foregroundColor(Palette.label2) }
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundColor(Palette.label3)
                                }
                            }.buttonStyle(.plain)
                            .task { await loader.loadMoreIfNeeded(current: c, fetchPage) }
                        }
                        if state.isLoadingMore { ProgressView().padding() }
                    }
                }.refreshable { await reload() }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .rootToolbar()
        .task(id: search) {
            if !search.isEmpty { try? await Task.sleep(nanoseconds: 350_000_000) }
            if Task.isCancelled { return }
            await reload()
        }
    }

    private func fetchPage(_ page: Int) async throws -> Paged<ClientSummary> {
        guard let api = session.api else { throw RivetError.invalidURL }
        return try await api.clients(search: search, page: page)
    }
    private func reload() async { await loader.reload(fetchPage) }
}

struct ClientDetailView: View {
    let id: Int
    @EnvironmentObject var session: Session
    @Environment(\.openURL) private var openURL
    @StateObject private var loader = Loader<ClientDetail>()
    @State private var tab = 0

    private var tabs: [String] {
        var t = ["Info", "Tickets", "Contacts", "Assets", "Locations"]
        if session.capabilities.canView(Capabilities.credential) { t.append("Credentials") }
        t.append("Contracts")
        return t
    }

    var body: some View {
        LoadView(loader: loader, retry: load) { client in
            VStack(spacing: 0) {
                hero(client)
                if let phone = client.phone, !phone.isEmpty {
                    Button { if let u = URL(string: "tel:" + phone.filter { $0.isNumber || $0 == "+" }) { openURL(u) } } label: {
                        Label("Call", systemImage: "phone")
                    }.buttonStyle(OutlineButtonStyle(height: 44)).padding(.horizontal, 16).padding(.top, 12)
                }
                tabBar
                ScrollView { content(client).padding(.vertical, 8) }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle(loader.value?.name ?? "Department").navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func hero(_ c: ClientDetail) -> some View {
        HStack(spacing: 14) {
            AvatarCircle(letter: Initials.letter(c.name), size: 52)
            VStack(alignment: .leading, spacing: 2) {
                Text(c.name).font(.headline)
                let place = [c.city, c.state].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")
                if !place.isEmpty { Text(place).font(.footnote).opacity(0.75) }
            }
            Spacer()
            Pill(text: "\(c.openTickets) open", tone: .cyan)
        }
        .padding(.horizontal, 20).padding(.vertical, 14).foregroundColor(Palette.onContainer)
        .frame(maxWidth: .infinity).background(Palette.container)
    }

    private var tabBar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 22) {
                ForEach(Array(tabs.enumerated()), id: \.offset) { i, title in
                    Button { tab = i } label: {
                        VStack(spacing: 8) {
                            Text(title).font(.subheadline.weight(.semibold)).foregroundColor(i == tab ? Palette.brand : Palette.label2)
                            Rectangle().fill(i == tab ? Palette.brand : Color.clear).frame(height: 3)
                        }
                    }.accessibilityAddTraits(i == tab ? .isSelected : [])
                }
            }.padding(.horizontal, 20).padding(.top, 12)
        }.overlay(alignment: .bottom) { Divider() }
    }

    @ViewBuilder private func content(_ c: ClientDetail) -> some View {
        switch tabs[tab] {
        case "Info": info(c)
        case "Tickets": ClientTickets(clientId: id)
        case "Contacts": contacts(c.contacts)
        case "Assets": ClientAssets(clientId: id)
        case "Locations": ClientLocations(clientId: id)
        case "Credentials": ClientCredentials(clientId: id)
        default: ClientContracts(clientId: id)
        }
    }

    private func info(_ c: ClientDetail) -> some View {
        VStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 10) {
                Text("Contact Info").font(.caption.weight(.semibold)).foregroundColor(Palette.label2)
                if let a = c.address, !a.isEmpty { Label(a, systemImage: "mappin") }
                let cityLine = [c.city, [c.state, c.zip].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " ")]
                    .compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")
                if !cityLine.isEmpty { Label(cityLine, systemImage: "mappin") }
                if let p = c.phone, !p.isEmpty { Label(p, systemImage: "phone") }
                if let w = c.website, !w.isEmpty { Label(w, systemImage: "globe") }
            }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)
            if let n = c.notes, !n.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    Text("Notes").font(.caption.weight(.semibold)).foregroundColor(Palette.label2)
                    Text(HTMLText.plain(n))
                }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)
            }
        }
    }

    private func contacts(_ items: [Contact]) -> some View {
        Group {
            if items.isEmpty { EmptyStateView(title: "No contacts").frame(height: 200) }
            ForEach(items) { c in
                ListRowCard {
                    AvatarCircle(letter: Initials.letter(c.name), size: 38)
                    VStack(alignment: .leading, spacing: 2) {
                        Text(c.name).font(.subheadline.weight(.semibold))
                        if let t = c.title, !t.isEmpty { Text(t).font(.caption).foregroundColor(Palette.label2) }
                        if let e = c.email, !e.isEmpty { Text(e).font(.caption).foregroundColor(Palette.label2) }
                        if let p = c.phone, !p.isEmpty { Text(p).font(.caption).foregroundColor(Palette.label2) }
                    }
                    Spacer()
                }
            }
        }
    }

    private func load() async {
        guard let api = session.api else { return }
        await loader.load { try await api.client(id) }
    }
}

// MARK: Tab contents

private struct ClientTab<T, Row: View>: View {
    let load: () async throws -> [T]
    let empty: String
    @ViewBuilder var row: (T) -> Row
    @StateObject private var loader = Loader<[T]>()

    var body: some View {
        LoadView(loader: loader, retry: { await loader.load(load) }) { items in
            if items.isEmpty { EmptyStateView(title: empty).frame(height: 200) }
            else { VStack(spacing: 0) { ForEach(Array(items.enumerated()), id: \.offset) { _, item in row(item) } } }
        }
        .task { await loader.load(load) }
    }
}

private struct ClientTickets: View {
    let clientId: Int
    @EnvironmentObject var session: Session
    var body: some View {
        ClientTab(load: { try await session.requireAPI().clientTickets(clientId) }, empty: "No tickets") { (t: TicketSummary) in
            NavigationLink(value: AppRoute.ticket(t.id)) { TicketRow(ticket: t) }.buttonStyle(.plain)
        }
    }
}

private struct ClientAssets: View {
    let clientId: Int
    @EnvironmentObject var session: Session
    var body: some View {
        ClientTab(load: { try await session.requireAPI().clientAssets(clientId) }, empty: "No assets") { (a: AssetSummary) in
            NavigationLink(value: AppRoute.asset(a.id)) { AssetRow(asset: a, showClient: false) }.buttonStyle(.plain)
        }
    }
}

private struct ClientLocations: View {
    let clientId: Int
    @EnvironmentObject var session: Session
    var body: some View {
        ClientTab(load: { try await session.requireAPI().clientLocations(clientId) }, empty: "No locations") { (l: ClientLocation) in
            ListRowCard {
                IconTile(systemName: "mappin.and.ellipse")
                VStack(alignment: .leading, spacing: 2) {
                    HStack { Text(l.name ?? "Location").font(.subheadline.weight(.semibold)); if l.primary { Pill(text: "Primary", tone: .cyan, small: true) } }
                    let line = [l.address, l.city, l.state, l.zip].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")
                    if !line.isEmpty { Text(line).font(.caption).foregroundColor(Palette.label2) }
                    if let p = l.phone, !p.isEmpty { Text(p).font(.caption).foregroundColor(Palette.label2) }
                }
                Spacer()
            }
        }
    }
}

private struct ClientCredentials: View {
    let clientId: Int
    @EnvironmentObject var session: Session
    var body: some View {
        ClientTab(load: { try await session.requireAPI().clientCredentials(clientId) }, empty: "No credentials") { (c: CredentialSummary) in
            ListRowCard {
                IconTile(systemName: "key")
                VStack(alignment: .leading, spacing: 2) {
                    Text(c.name).font(.subheadline.weight(.semibold))
                    if let u = c.uri, !u.isEmpty { Text(u).font(.caption).foregroundColor(Palette.label2) }
                }
                Spacer()
            }
        }
    }
}

private struct ClientContracts: View {
    let clientId: Int
    @EnvironmentObject var session: Session
    var body: some View {
        ClientTab(load: { try await session.requireAPI().clientContracts(clientId) }, empty: "No contracts") { (c: ClientContract) in
            NavigationLink(value: AppRoute.contract(c.id)) {
                ListRowCard {
                    IconTile(systemName: "doc.text")
                    VStack(alignment: .leading, spacing: 2) {
                        Text(c.name ?? "Contract").font(.subheadline.weight(.semibold))
                        Text([c.type, c.status].compactMap { $0 }.joined(separator: " • ")).font(.caption).foregroundColor(Palette.label2)
                    }
                    Spacer()
                    Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundColor(Palette.label3)
                }
            }.buttonStyle(.plain)
        }
    }
}
