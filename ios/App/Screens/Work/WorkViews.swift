import SwiftUI
import RivetCore

// MARK: Projects

@MainActor struct ProjectsView: View {
    @EnvironmentObject var session: Session
    @StateObject private var loader = PagedLoader<ProjectSummary>()
    @State private var search = ""
    @State private var statusIndex = 0
    private let statuses = [("open", "Open"), ("completed", "Completed"), ("all", "All")]
    private struct Key: Equatable { var search: String; var status: Int }

    var body: some View {
        VStack(spacing: 0) {
            SearchField(placeholder: "Search projects…", text: $search).padding(.horizontal, 16).padding(.vertical, 8)
            FilterChips(items: statuses.map(\.1), selection: $statusIndex).padding(.bottom, 8)
            let state = loader.state
            if let error = state.error, state.items.isEmpty {
                // Older servers do not offer projects at all; say so instead of offering a pointless retry.
                if error == userMessage(for: RivetError.http(status: 404, message: nil)) {
                    EmptyStateView(title: "Projects aren't available on this server", systemImage: "point.3.connected.trianglepath.dotted")
                } else { ErrorStateView(message: error, retry: reload) }
            } else if state.items.isEmpty && state.isRefreshing {
                LoadingStateView()
            } else if state.items.isEmpty {
                EmptyStateView(title: "No projects found", systemImage: "point.3.connected.trianglepath.dotted")
            } else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(state.items) { p in
                            NavigationLink(value: AppRoute.project(p.id)) { card(p) }.buttonStyle(.plain)
                                .task { await loader.loadMoreIfNeeded(current: p, fetchPage) }
                        }
                        if state.isLoadingMore { ProgressView().padding() }
                    }
                }.refreshable { await reload() }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .rootToolbar(hideBack: true)
        .task(id: Key(search: search, status: statusIndex)) {
            if !search.isEmpty { try? await Task.sleep(nanoseconds: 350_000_000) }
            if Task.isCancelled { return }
            await reload()
        }
    }

    private func card(_ p: ProjectSummary) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 12) {
                IconTile(systemName: "point.3.connected.trianglepath.dotted")
                VStack(alignment: .leading, spacing: 2) {
                    Text(p.name).font(.subheadline.weight(.semibold)).multilineTextAlignment(.leading)
                    Text([ "\(p.prefix ?? "")\(p.number)", p.client ?? "" ].filter { !$0.isEmpty }.joined(separator: " • "))
                        .font(.caption).foregroundColor(Palette.label2)
                }
                Spacer()
                if let due = p.dueAt, !due.isEmpty { Text("Due \(RivetDate.dateOnly(due))").font(.caption2).foregroundColor(Palette.label2) }
            }
            ProgressView(value: Double(p.taskCompletedCount), total: Double(max(p.taskCount, 1))).tint(Palette.brand)
            Text("\(p.taskCompletedCount)/\(p.taskCount) tasks").font(.caption2).foregroundColor(Palette.label2)
        }
        .padding(14).background(Palette.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
        .padding(.horizontal, 16).padding(.vertical, 6)
        .accessibilityElement(children: .combine)
    }

    private func fetchPage(_ page: Int) async throws -> Paged<ProjectSummary> {
        try await session.requireAPI().projects(search: search, page: page, status: statuses[statusIndex].0)
    }
    private func reload() async { await loader.reload(fetchPage) }
}

@MainActor struct ProjectDetailView: View {
    let id: Int
    @EnvironmentObject var session: Session
    @StateObject private var loader = Loader<ProjectDetail>()
    @State private var toggling: Set<Int> = []
    @State private var toggleError: String?

    var body: some View {
        LoadView(loader: loader, retry: load) { p in
            ScrollView {
                VStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(p.name).font(.title2.weight(.bold))
                        Text([p.client, p.manager.map { "Managed by \($0)" }].compactMap { $0 }.joined(separator: " • "))
                            .font(.subheadline).foregroundColor(Palette.label2)
                        Pill(text: p.completedAt == nil ? "Open" : "Completed", tone: .cyan)
                    }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)

                    VStack(alignment: .leading, spacing: 0) {
                        Text("Timeline").font(.caption.weight(.semibold)).foregroundColor(Palette.label2).padding(.bottom, 4)
                        if let s = p.startAt, !s.isEmpty { KeyValueRow(key: "Start", value: RivetDate.dateOnly(s)) }
                        if let d = p.dueAt, !d.isEmpty { KeyValueRow(key: "Due", value: RivetDate.dateOnly(d)) }
                        if let c = p.createdAt { KeyValueRow(key: "Created", value: RivetDate.display(c)) }
                        if let h = p.estimatedHours { KeyValueRow(key: "Estimated Hours", value: String(format: "%.1f", h)) }
                    }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)

                    if let d = p.description, !d.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Description").font(.caption.weight(.semibold)).foregroundColor(Palette.label2)
                            Text(HTMLText.plain(d))
                        }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)
                    }

                    VStack(alignment: .leading, spacing: 8) {
                        Text("Progress").font(.caption.weight(.semibold)).foregroundColor(Palette.label2)
                        let done = p.tasks.filter { $0.completedAt != nil }.count
                        HStack { Text("Tasks"); Spacer(); Text("\(done)/\(p.tasks.count)").foregroundColor(Palette.label2) }
                        ProgressView(value: Double(done), total: Double(max(p.tasks.count, 1))).tint(Palette.brand)
                    }.card().padding(.horizontal, 16)

                    if !p.tasks.isEmpty {
                        SectionLabel(text: "Tasks")
                        VStack(spacing: 0) {
                            ForEach(p.tasks) { t in
                                taskRow(t)
                                if t.id != p.tasks.last?.id { Divider().padding(.leading, 52) }
                            }
                        }.card(padding: 6).padding(.horizontal, 16)
                    }
                    if let toggleError { InlineError(message: toggleError).padding(.horizontal, 16) }
                    if !p.tickets.isEmpty {
                        SectionLabel(text: "Tickets")
                        ForEach(p.tickets) { t in
                            ListRowCard {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("\(t.number)  \(t.subject)").font(.subheadline.weight(.semibold))
                                    Text([t.status, t.assignedTo].compactMap { $0 }.joined(separator: " • ")).font(.caption).foregroundColor(Palette.label2)
                                }
                                Spacer()
                            }
                        }
                    }
                }.padding(.vertical, 8)
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle(loader.value.map { "\($0.prefix ?? "")\($0.number)" } ?? "Project").navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func taskRow(_ t: ProjectTask) -> some View {
        let canToggle = session.capabilities.canWrite(Capabilities.support)
        return HStack(alignment: .top, spacing: 12) {
            Button { toggle(t) } label: {
                Image(systemName: t.completedAt != nil ? "checkmark.circle.fill" : "circle").font(.title3)
                    .foregroundColor(t.completedAt != nil ? Palette.brand : Palette.label3)
            }.disabled(!canToggle || toggling.contains(t.id))
            .accessibilityLabel(t.completedAt != nil ? "Completed: \(t.name)" : "Not completed: \(t.name)")
            VStack(alignment: .leading, spacing: 2) {
                Text(t.name).strikethrough(t.completedAt != nil).foregroundColor(t.completedAt != nil ? Palette.label2 : Palette.label)
                Text([t.assignedTo, t.dueAt.map { "Due \(RivetDate.dateOnly($0))" }].compactMap { $0 }.joined(separator: " • "))
                    .font(.caption).foregroundColor(Palette.label2)
            }
            Spacer()
        }.padding(10)
    }

    /// Waits for the server's answer before changing the checkbox.
    private func toggle(_ t: ProjectTask) {
        guard let api = session.api, !toggling.contains(t.id) else { return }
        toggling.insert(t.id); toggleError = nil
        Task {
            defer { toggling.remove(t.id) }
            do { _ = try await api.toggleTask(t.id); await load() }
            catch { toggleError = userMessage(for: error) }
        }
    }

    private func load() async {
        guard let api = session.api else { return }
        await loader.load { try await api.project(id) }
    }
}

// MARK: Contracts

@MainActor struct ContractsView: View {
    @EnvironmentObject var session: Session
    @StateObject private var loader = PagedLoader<ContractSummary>()
    @State private var search = ""
    @State private var filterIndex = 0
    private struct Key: Equatable { var search: String; var filter: Int }

    var body: some View {
        VStack(spacing: 0) {
            SearchField(placeholder: "Search contracts…", text: $search).padding(.horizontal, 16).padding(.vertical, 8)
            FilterChips(items: ["All", "Expiring / Expired"], selection: $filterIndex).padding(.bottom, 8)
            let state = loader.state
            if let error = state.error, state.items.isEmpty { ErrorStateView(message: error, retry: reload) }
            else if state.items.isEmpty && state.isRefreshing { LoadingStateView() }
            else if state.items.isEmpty { EmptyStateView(title: "No contracts found", systemImage: "doc.text") }
            else {
                ScrollView {
                    LazyVStack(spacing: 0) {
                        ForEach(state.items) { c in
                            NavigationLink(value: AppRoute.contract(c.id)) {
                                ListRowCard {
                                    IconTile(systemName: "doc.text")
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(c.name).font(.subheadline.weight(.semibold)).multilineTextAlignment(.leading)
                                        Text([c.type, c.client].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " • "))
                                            .font(.caption).foregroundColor(Palette.label2).multilineTextAlignment(.leading)
                                    }
                                    Spacer()
                                    if c.isExpired { Pill(text: "Expired", tone: .danger, small: true) }
                                    else if c.isDueSoon { Pill(text: "Due Soon", tone: .lavender, small: true) }
                                    else if let r = c.renewalDate, !r.isEmpty {
                                        Text("Renews \(RivetDate.dateOnly(r))").font(.caption2).foregroundColor(Palette.label2)
                                    }
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
        .rootToolbar(hideBack: true)
        .task(id: Key(search: search, filter: filterIndex)) {
            if !search.isEmpty { try? await Task.sleep(nanoseconds: 350_000_000) }
            if Task.isCancelled { return }
            await reload()
        }
    }

    private func fetchPage(_ page: Int) async throws -> Paged<ContractSummary> {
        try await session.requireAPI().contracts(search: search, page: page, expiring: filterIndex == 1)
    }
    private func reload() async { await loader.reload(fetchPage) }
}

@MainActor struct ContractDetailView: View {
    let id: Int
    @EnvironmentObject var session: Session
    @Environment(\.openURL) private var openURL
    @StateObject private var loader = Loader<ContractDetail>()

    var body: some View {
        LoadView(loader: loader, retry: load) { c in
            ScrollView {
                VStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(c.name).font(.title2.weight(.bold))
                        Text([c.type, c.client].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " • ")).foregroundColor(Palette.label2)
                        HStack(spacing: 8) {
                            if let s = c.status { Pill(text: s, tone: .cyan) }
                            if c.isExpired { Pill(text: "Expired", tone: .danger) } else if c.isDueSoon { Pill(text: "Due Soon", tone: .lavender) }
                        }
                    }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)

                    VStack(alignment: .leading, spacing: 0) {
                        Text("Details").font(.caption.weight(.semibold)).foregroundColor(Palette.label2).padding(.bottom, 4)
                        if let s = c.startDate { KeyValueRow(key: "Start", value: RivetDate.dateOnly(s)) }
                        if let e = c.endDate { KeyValueRow(key: "End", value: RivetDate.dateOnly(e)) }
                        if let r = c.renewalDate { KeyValueRow(key: "Renewal Date", value: RivetDate.dateOnly(r)) }
                        if let f = c.renewalFrequency, !f.isEmpty { KeyValueRow(key: "Renewal", value: f) }
                    }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)

                    if let d = c.details, !d.isEmpty {
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Notes").font(.caption.weight(.semibold)).foregroundColor(Palette.label2)
                            Text(HTMLText.plain(d))
                        }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)
                    }

                    VStack(alignment: .leading, spacing: 0) {
                        Text("SLA").font(.caption.weight(.semibold)).foregroundColor(Palette.label2).padding(.bottom, 4)
                        KeyValueRow(key: "High", value: sla(c.sla.high)); KeyValueRow(key: "Medium", value: sla(c.sla.medium))
                        KeyValueRow(key: "Low", value: sla(c.sla.low))
                    }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)

                    if !c.documents.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Documents").font(.caption.weight(.semibold)).foregroundColor(Palette.label2)
                            ForEach(c.documents) { d in
                                Button { open(d.url) } label: { Label(d.name, systemImage: "doc") }
                            }
                        }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)
                    }
                }.padding(.vertical, 8)
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle("Contract").navigationBarTitleDisplayMode(.inline)
        .task { await load() }
    }

    private func sla(_ t: ContractSlaTier) -> String {
        switch (t.responseTime, t.resolutionTime) {
        case (let r?, let s?): return "\(r)h response / \(s)h resolution"
        case (let r?, nil): return "\(r)h response"
        case (nil, let s?): return "\(s)h resolution"
        default: return "Not set"
        }
    }

    /// Document links are only followed when they are http(s), the same rule the Android app applies.
    private func open(_ raw: String) {
        guard let base = session.serverURL, let url = URL(string: raw, relativeTo: base)?.absoluteURL,
              ["http", "https"].contains(url.scheme?.lowercased() ?? "") else { return }
        openURL(url)
    }

    private func load() async {
        guard let api = session.api else { return }
        await loader.load { try await api.contract(id) }
    }
}
