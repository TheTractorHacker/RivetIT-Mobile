import SwiftUI
import RivetCore

/// Signed-in shell: five tabs (same as the Android bottom bar) plus the side menu that replaces the Android drawer.
struct MainView: View {
    @EnvironmentObject var session: Session
    @StateObject private var router = Router()

    var body: some View {
        ZStack(alignment: .leading) {
            TabView(selection: $router.tab) {
                root(.home) { DashboardView() }
                    .tabItem { Label("Home", systemImage: "square.grid.2x2") }.tag(AppTab.home)
                if session.capabilities.canView(Capabilities.support) {
                    root(.tickets) { TicketsView() }
                        .tabItem { Label("Tickets", systemImage: "ticket") }.tag(AppTab.tickets)
                }
                if session.capabilities.canView(Capabilities.client) {
                    root(.depts) { ClientsView() }
                        .tabItem { Label("Depts", systemImage: "building.2") }.tag(AppTab.depts)
                }
                if session.capabilities.canView(Capabilities.assets) {
                    root(.assets) { AssetsView() }
                        .tabItem { Label("Assets", systemImage: "laptopcomputer") }.tag(AppTab.assets)
                }
                if session.capabilities.canView(Capabilities.support) {
                    root(.appts) { AppointmentsView() }
                        .tabItem { Label("Appts", systemImage: "calendar") }.tag(AppTab.appts)
                }
            }
            .tint(Palette.brand)

            if router.menuOpen {
                Color.black.opacity(0.4).ignoresSafeArea()
                    .onTapGesture { withAnimation(.easeOut(duration: 0.25)) { router.menuOpen = false } }
                    .accessibilityHidden(true)
                SideMenu()
                    .transition(.move(edge: .leading))
                    .zIndex(1)
            }
        }
        .animation(.easeOut(duration: 0.25), value: router.menuOpen)
        .environmentObject(router)
    }

    private func root<Content: View>(_ tab: AppTab, @ViewBuilder _ content: () -> Content) -> some View {
        NavigationStack(path: router.binding(for: tab)) {
            content()
                .navigationDestination(for: AppRoute.self) { RouteView(route: $0) }
        }
    }
}

/// Maps a route to its screen.
struct RouteView: View {
    let route: AppRoute

    var body: some View {
        switch route {
        case .tickets: TicketsView()
        case .createTicket: CreateTicketView()
        case .ticket(let id): TicketDetailView(id: id)
        case .ticketChat(let id): TicketChatView(ticketId: id)
        case .clients: ClientsView()
        case .client(let id): ClientDetailView(id: id)
        case .assets: AssetsView()
        case .asset(let id): AssetDetailView(id: id)
        case .projects: ProjectsView()
        case .project(let id): ProjectDetailView(id: id)
        case .contracts: ContractsView()
        case .contract(let id): ContractDetailView(id: id)
        case .credentials: AccessGuard(module: Capabilities.credential) { CredentialsView() }
        case .notifications: NotificationsView()
        case .appointments: AppointmentsView()
        case .search: SearchView()
        case .reports: AccessGuard(module: Capabilities.reporting) { ReportsView() }
        case .scan: ScannerView()
        case .profile: ProfileView()
        case .alerts: AccessGuard(module: Capabilities.rmmAlerts) { AlertsView() }
        case .kb: AccessGuard(module: Capabilities.kb) { KnowledgeBaseView() }
        case .kbArticle(let id): AccessGuard(module: Capabilities.kb) { KbArticleView(id: id) }
        }
    }
}

/// Leading menu button + brand, trailing search / alerts / notifications: the Android app bar, in a navigation bar.
struct RootToolbar: ViewModifier {
    @EnvironmentObject var router: Router
    @EnvironmentObject var session: Session
    var hideBack = false

    func body(content: Content) -> some View {
        content
            .navigationBarTitleDisplayMode(.inline)
            .navigationBarBackButtonHidden(hideBack)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    HStack(spacing: 10) {
                        Button { withAnimation(.easeOut(duration: 0.25)) { router.menuOpen = true } } label: {
                            Image(systemName: "line.3.horizontal")
                        }.accessibilityLabel("Menu")
                        BrandMark(size: 28)
                        Text("RivetIT").font(.title3.weight(.medium)).foregroundColor(Palette.label)
                    }
                }
                ToolbarItemGroup(placement: .navigationBarTrailing) {
                    Button { router.push(.search) } label: { Image(systemName: "magnifyingglass") }
                        .accessibilityLabel("Search")
                    if session.capabilities.canView(Capabilities.rmmAlerts) {
                        Button { router.push(.alerts) } label: { Image(systemName: "exclamationmark.triangle") }
                            .accessibilityLabel("Alerts")
                    }
                    Button { router.push(.notifications) } label: {
                        Image(systemName: "bell").overlay(alignment: .topTrailing) {
                            if session.hasUnread { Circle().fill(Color.red).frame(width: 8, height: 8).offset(x: 3, y: -2) }
                        }
                    }.accessibilityLabel(session.hasUnread ? "Notifications, unread" : "Notifications")
                }
            }
            .tint(Palette.label)
    }
}

extension View {
    func rootToolbar(hideBack: Bool = false) -> some View { modifier(RootToolbar(hideBack: hideBack)) }
}
