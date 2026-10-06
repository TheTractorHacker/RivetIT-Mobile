import SwiftUI
import RivetCore

enum AppTab: Hashable { case home, tickets, depts, assets, appts }

/// Optional pre-set filters when opening the ticket list from a dashboard tile.
struct TicketFilter: Equatable {
    var mine = false
    var overdue = false
    var dueToday = false
    var onsite = false
    var isEmpty: Bool { !mine && !overdue && !dueToday && !onsite }
}

/// Owns tab selection, one navigation stack per tab, the side menu, and deep-link handling.
@MainActor
final class Router: ObservableObject {
    @Published var tab: AppTab = .home
    @Published var menuOpen = false
    @Published var ticketFilter = TicketFilter()
    @Published private var paths: [AppTab: NavigationPath] = [:]

    func binding(for tab: AppTab) -> Binding<NavigationPath> {
        Binding(get: { self.paths[tab] ?? NavigationPath() }, set: { self.paths[tab] = $0 })
    }

    /// Opens a destination the way the Android app does: list screens that are bottom tabs switch tab, everything else
    /// is pushed on the current tab.
    func open(_ route: AppRoute) {
        menuOpen = false
        switch route {
        case .tickets: select(.tickets)
        case .clients: select(.depts)
        case .assets: select(.assets)
        case .appointments: select(.appts)
        default: push(route)
        }
    }

    func openTickets(filter: TicketFilter) {
        ticketFilter = filter
        select(.tickets)
    }

    func push(_ route: AppRoute) {
        menuOpen = false
        var p = paths[tab] ?? NavigationPath()
        p.append(route)
        paths[tab] = p
    }

    func popToRoot() { paths[tab] = NavigationPath() }

    func pop() {
        var p = paths[tab] ?? NavigationPath()
        if !p.isEmpty { p.removeLast() }
        paths[tab] = p
    }

    private func select(_ t: AppTab) {
        if tab == t { paths[t] = NavigationPath() } else { tab = t }
    }

    /// Entry for push notifications and URLs: only allow-listed routes are followed.
    func handleDeepLink(_ raw: String) {
        guard let route = DeepLinks.resolve(raw) else { return }
        open(route)
    }
}
