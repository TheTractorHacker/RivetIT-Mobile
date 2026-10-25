import SwiftUI
import RivetCore

private struct TicketQuery: Equatable {
    var search = ""
    var mine = false
    var closed = false
    var priority: String?
    var categoryId: Int?
    var overdue = false
    var dueToday = false
    var onsite = false
}

@MainActor struct TicketsView: View {
    @EnvironmentObject var session: Session
    @EnvironmentObject var router: Router
    @StateObject private var loader = PagedLoader<TicketSummary>()
    @State private var query = TicketQuery()
    @State private var savedViews: [SavedTicketView] = []
    @State private var categories: [TicketCategory] = []
    @State private var showFilters = false
    @State private var activeViewName: String?

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                controls
                list
            }
            if session.capabilities.canWrite(Capabilities.support) {
                FloatingButton(systemName: "plus", label: "New ticket") { router.push(.createTicket) }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .rootToolbar()
        .sheet(isPresented: $showFilters) {
            TicketFilterSheet(priority: $query.priority, categoryId: $query.categoryId, categories: categories)
                .presentationDetents([.medium])
        }
        .task(id: query) {
            // Debounce typing in the search field; filter and tab changes load immediately.
            if !query.search.isEmpty { try? await Task.sleep(nanoseconds: 350_000_000) }
            if Task.isCancelled { return }
            await reload()
        }
        .task { await loadMeta() }
        .onAppear { applyRouterFilter() }
        .onChange(of: router.ticketFilter) { _ in applyRouterFilter() }
    }

    // MARK: Controls

    private var controls: some View {
        VStack(spacing: 8) {
            HStack(spacing: 8) {
                SearchField(placeholder: "Search tickets…", text: $query.search)
                Button { query.mine.toggle() } label: {
                    Text("Mine").font(.footnote.weight(.medium)).padding(.horizontal, 12).padding(.vertical, 7)
                        .background(query.mine ? Palette.brand : Palette.fill, in: Capsule())
                        .foregroundColor(query.mine ? Palette.onBrand : Palette.label)
                }.accessibilityAddTraits(query.mine ? .isSelected : [])
                Button { showFilters = true } label: {
                    Image(systemName: filtersActive ? "line.3.horizontal.decrease.circle.fill" : "slider.horizontal.3")
                }.accessibilityLabel(filtersActive ? "Filters, active" : "Filters")
                if !savedViews.isEmpty {
                    Menu {
                        ForEach(savedViews) { v in Button(v.name) { apply(view: v) } }
                        if activeViewName != nil { Button("Clear saved view", role: .destructive) { query = TicketQuery(); activeViewName = nil } }
                    } label: { Image(systemName: "bookmark") }.accessibilityLabel("Saved views")
                }
            }
            Picker("Status", selection: $query.closed) {
                Text("Open").tag(false)
                Text("Closed").tag(true)
            }.pickerStyle(.segmented)
            if let name = activeViewName {
                HStack { Label(name, systemImage: "bookmark.fill").font(.footnote); Spacer()
                    Button("Clear") { query = TicketQuery(); activeViewName = nil }.font(.footnote) }
                    .foregroundColor(Palette.brand)
            }
        }.padding(.horizontal, 16).padding(.vertical, 8)
    }

    private var filtersActive: Bool {
        query.priority != nil || query.categoryId != nil || query.overdue || query.dueToday || query.onsite
    }

    // MARK: List

    private var list: some View {
        let state = loader.state
        return Group {
            if let error = state.error, state.items.isEmpty {
                ErrorStateView(message: error, retry: reload)
            } else if state.items.isEmpty && state.isRefreshing {
                LoadingStateView()
            } else if state.items.isEmpty {
                EmptyStateView(title: "No tickets found", systemImage: "ticket")
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(groups(state.items), id: \.name) { group in
                            HStack(spacing: 8) {
                                Circle().fill(Color(serverHex: group.color)).frame(width: 9, height: 9)
                                Text(group.name).font(.subheadline.weight(.bold))
                                Text("· \(group.tickets.count)").font(.subheadline).foregroundColor(Palette.label2)
                                Spacer()
                            }.padding(.horizontal, 20).padding(.top, 12).padding(.bottom, 4)
                            ForEach(group.tickets) { t in
                                NavigationLink(value: AppRoute.ticket(t.id)) { TicketRow(ticket: t) }.buttonStyle(.plain)
                                    .task { await loader.loadMoreIfNeeded(current: t, fetchPage) }
                            }
                        }
                        if state.isLoadingMore { ProgressView().padding() }
                        Color.clear.frame(height: 80)
                    }
                }.refreshable { await reload() }
            }
        }
    }

    private struct Group_ { let name: String; let color: String?; let tickets: [TicketSummary] }

    private func groups(_ items: [TicketSummary]) -> [Group_] {
        var order: [String] = []
        var map: [String: [TicketSummary]] = [:]
        var colors: [String: String?] = [:]
        for t in items {
            let key = t.status ?? "Other"
            if map[key] == nil { order.append(key); colors[key] = t.statusColor }
            map[key, default: []].append(t)
        }
        return order.map { Group_(name: $0, color: colors[$0] ?? nil, tickets: map[$0] ?? []) }
    }

    // MARK: Data

    private func fetchPage(_ page: Int) async throws -> Paged<TicketSummary> {
        guard let api = session.api else { throw RivetError.invalidURL }
        let q = query
        return try await api.tickets(status: q.closed ? "closed" : "open", mine: q.mine, search: q.search, page: page,
                                     priority: q.priority, onsite: q.onsite ? true : nil, categoryId: q.categoryId,
                                     overdue: q.overdue ? true : nil, dueToday: q.dueToday ? true : nil)
    }

    private func reload() async { await loader.reload(fetchPage) }

    private func loadMeta() async {
        guard let api = session.api else { return }
        savedViews = (try? await api.savedViews()) ?? []
        categories = (try? await api.categories()) ?? []
    }

    private func applyRouterFilter() {
        let f = router.ticketFilter
        guard !f.isEmpty else { return }
        query = TicketQuery(mine: f.mine, overdue: f.overdue, dueToday: f.dueToday, onsite: f.onsite)
        activeViewName = nil
        router.ticketFilter = TicketFilter()
    }

    private func apply(view: SavedTicketView) {
        let p = view.snakeParams
        var q = TicketQuery()
        q.closed = p["status"] == "closed"
        q.mine = p["mine"] == "1"
        q.priority = p["priority"]
        q.categoryId = p["category_id"].flatMap(Int.init)
        q.overdue = p["overdue"] == "1"; q.dueToday = p["due_today"] == "1"; q.onsite = p["onsite"] == "1"
        query = q; activeViewName = view.name
    }
}

@MainActor struct TicketFilterSheet: View {
    @Binding var priority: String?
    @Binding var categoryId: Int?
    let categories: [TicketCategory]
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("Priority") {
                    Picker("Priority", selection: $priority) {
                        Text("Any").tag(String?.none)
                        ForEach(["low", "medium", "high", "critical"], id: \.self) { Text($0.capitalized).tag(String?.some($0)) }
                    }.pickerStyle(.segmented)
                }
                Section("Category") {
                    Picker("Category", selection: $categoryId) {
                        Text("Any").tag(Int?.none)
                        ForEach(categories) { Text($0.name).tag(Int?.some($0.id)) }
                    }
                }
                Section { Button("Reset filters", role: .destructive) { priority = nil; categoryId = nil } }
            }
            .navigationTitle("Filters").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Done") { dismiss() } } }
        }
    }
}
