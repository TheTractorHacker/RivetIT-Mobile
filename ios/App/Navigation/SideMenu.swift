import SwiftUI
import RivetCore

/// The Android navigation drawer: profile header, destinations (hidden when the role cannot use them), theme, sign out.
struct SideMenu: View {
    @EnvironmentObject var session: Session
    @EnvironmentObject var router: Router

    private var caps: Capabilities { session.capabilities }

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            header
            ScrollView {
                VStack(spacing: 2) {
                    item("Home", "square.grid.2x2") { router.menuOpen = false; router.tab = .home; router.popToRoot() }
                    item("Search", "magnifyingglass") { router.open(.search) }
                    item("Notifications", "bell", dot: session.hasUnread) { router.open(.notifications) }
                    if caps.canView(Capabilities.rmmAlerts) { item("Alerts", "exclamationmark.triangle") { router.open(.alerts) } }
                    Divider().padding(.vertical, 8).padding(.horizontal, 14)
                    if caps.canView(Capabilities.support) { item("Projects", "point.3.connected.trianglepath.dotted") { router.open(.projects) } }
                    if caps.canView(Capabilities.reporting) { item("Reports", "chart.bar") { router.open(.reports) } }
                    if caps.canView(Capabilities.kb) { item("Knowledge Base", "book") { router.open(.kb) } }
                    if caps.canView(Capabilities.credential) { item("Credentials", "lock") { router.open(.credentials) } }
                    if caps.canView(Capabilities.client) { item("Contracts", "doc.text") { router.open(.contracts) } }
                }.padding(.horizontal, 12).padding(.top, 10)
            }
            VStack(spacing: 2) {
                Divider().padding(.bottom, 8).padding(.horizontal, 14)
                item("Profile", "person") { router.open(.profile) }
                HStack(spacing: 16) {
                    Image(systemName: session.themeMode == .dark ? "moon" : "sun.max").frame(width: 24)
                    Toggle("Dark mode", isOn: Binding(get: { session.themeMode == .dark },
                                                      set: { session.themeMode = $0 ? .dark : .light }))
                        .tint(Palette.brand)
                }.padding(.horizontal, 14).frame(height: 50)
                Button { Task { await session.signOut() } } label: {
                    HStack(spacing: 16) { Image(systemName: "rectangle.portrait.and.arrow.right").frame(width: 24); Text("Sign out"); Spacer() }
                        .foregroundColor(Palette.danger).padding(.horizontal, 14).frame(height: 50)
                }
            }.padding(.horizontal, 12).padding(.bottom, 12)
        }
        .frame(width: 312).frame(maxHeight: .infinity, alignment: .top)
        .background(Palette.background.ignoresSafeArea())
        .clipShape(RoundedRectangle(cornerRadius: 28, style: .continuous))
        .shadow(color: .black.opacity(0.25), radius: 14, x: 4)
        .accessibilityAddTraits(.isModal)
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(Initials.letter(session.profile?.name ?? "")).font(.title.weight(.semibold))
                .frame(width: 60, height: 60).background(Palette.brand, in: Circle()).foregroundColor(Palette.onBrand)
            Text(session.profile?.name ?? "Account").font(.headline).padding(.top, 6)
            Text(session.profile?.email ?? "").font(.footnote).opacity(0.75)
        }
        .padding(.horizontal, 20).padding(.top, 64).padding(.bottom, 18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Palette.container).foregroundColor(Palette.onContainer)
    }

    private func item(_ title: String, _ symbol: String, dot: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                Image(systemName: symbol).frame(width: 24)
                Text(title)
                if dot { Circle().fill(Color.red).frame(width: 8, height: 8) }
                Spacer()
            }.foregroundColor(Palette.label).padding(.horizontal, 14).frame(height: 50).contentShape(Rectangle())
        }
    }
}
