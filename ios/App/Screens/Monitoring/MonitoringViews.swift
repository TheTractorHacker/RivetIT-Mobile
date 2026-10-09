import SwiftUI
import RivetCore

// MARK: Alerts

@MainActor struct AlertsView: View {
    @EnvironmentObject var session: Session
    @StateObject private var loader = Loader<[AlertItem]>()
    @State private var statusIndex = 0
    @State private var pending: Set<String> = []
    @State private var actionError: String?
    private let statuses = [("new", "New"), ("acknowledged", "Acked"), ("resolved", "Resolved"), ("all", "All")]

    var body: some View {
        VStack(spacing: 0) {
            Picker("Status", selection: $statusIndex) {
                ForEach(Array(statuses.enumerated()), id: \.offset) { i, s in Text(s.1).tag(i) }
            }.pickerStyle(.segmented).padding(.horizontal, 16).padding(.vertical, 8)
            if let actionError { InlineError(message: actionError).padding(.horizontal, 16) }
            LoadView(loader: loader, retry: load) { items in
                if items.isEmpty { EmptyStateView(title: "No alerts", systemImage: "checkmark.shield") }
                else {
                    ScrollView {
                        LazyVStack(spacing: 10) { ForEach(items) { card($0) } }.padding(.vertical, 6)
                    }.refreshable { await load() }
                }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .rootToolbar(hideBack: true)
        .task(id: statusIndex) { await load() }
    }

    private func card(_ a: AlertItem) -> some View {
        let canAct = session.capabilities.canWrite(Capabilities.rmmAlerts)
        let busy = pending.contains(a.key)
        return VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 12) {
                Image(systemName: a.source == "backup" ? "icloud.slash" : "server.rack").frame(width: 34, height: 34)
                    .background(Palette.dangerSoft, in: Circle()).foregroundColor(Palette.danger)
                VStack(alignment: .leading, spacing: 4) {
                    Text(a.message ?? a.subject ?? "Alert").font(.subheadline.weight(.medium))
                    HStack(spacing: 6) {
                        Pill(text: a.severity.lowercased(), tone: .danger, small: true)
                        Pill(text: a.status.lowercased(), tone: .gray, small: true)
                    }
                    let line = [a.subject, a.clientName].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
                    if !line.isEmpty { Text(line).font(.caption).foregroundColor(Palette.label2) }
                }
            }
            if canAct || a.ticketLabel != nil {
                HStack(spacing: 18) {
                    if canAct && a.status == "new" { Button("Acknowledge") { act(a, "acknowledge") }.disabled(busy) }
                    if canAct && a.status != "resolved" { Button("Resolve") { act(a, "resolve") }.disabled(busy) }
                    if let t = a.ticketLabel, let id = a.ticketId {
                        NavigationLink(t, value: AppRoute.ticket(id))
                    }
                    if busy { ProgressView() }
                }.font(.subheadline.weight(.semibold)).padding(.leading, 46)
            }
        }
        .padding(14).background(Palette.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous)).padding(.horizontal, 16)
    }

    private func act(_ a: AlertItem, _ action: String) {
        guard let api = session.api, !pending.contains(a.key) else { return }
        pending.insert(a.key); actionError = nil
        Task {
            defer { pending.remove(a.key) }
            do { try await api.actOnAlert(AlertActionRequest(source: a.source, id: a.id, action: action)); await load() }
            catch { actionError = userMessage(for: error) }
        }
    }

    private func load() async {
        guard let api = session.api else { return }
        let status = statuses[statusIndex].0
        await loader.load { try await api.alerts(status: status).data }
    }
}

// MARK: Notifications

@MainActor struct NotificationsView: View {
    @EnvironmentObject var session: Session
    @EnvironmentObject var router: Router
    @StateObject private var loader = Loader<[AppNotification]>()
    @State private var pending: Set<Int> = []
    @State private var actionError: String?

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text("Notifications").font(.title2.weight(.bold))
                Spacer()
                Button("Mark all read") { markAll() }.font(.subheadline)
            }.padding(.horizontal, 20).padding(.vertical, 10)
            if let actionError { InlineError(message: actionError).padding(.horizontal, 20) }
            LoadView(loader: loader, retry: load) { items in
                if items.isEmpty { EmptyStateView(title: "You're all caught up", systemImage: "bell") }
                else {
                    ScrollView {
                        LazyVStack(spacing: 0) {
                            ForEach(items) { n in
                                ListRowCard {
                                    IconTile(systemName: "bell", size: 40, round: true)
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(n.message).font(.subheadline).multilineTextAlignment(.leading)
                                        Text(n.timestamp ?? "").font(.caption).foregroundColor(Palette.label2)
                                    }
                                    Spacer()
                                    if pending.contains(n.id) { ProgressView() }
                                    else {
                                        Button { read(n) } label: { Image(systemName: "checkmark.circle").font(.title3) }
                                            .accessibilityLabel("Mark as read")
                                    }
                                }.contentShape(Rectangle()).onTapGesture { open(n) }
                            }
                        }
                    }.refreshable { await load() }
                }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .rootToolbar(hideBack: true)
        .task { await load() }
    }

    private func open(_ n: AppNotification) {
        if let action = n.action, let route = DeepLinks.resolve(action) { router.open(route) }
    }

    /// The row only disappears once the server confirms the read.
    private func read(_ n: AppNotification) {
        guard let api = session.api, !pending.contains(n.id) else { return }
        pending.insert(n.id); actionError = nil
        Task {
            defer { pending.remove(n.id) }
            do { try await api.markRead(n.id); await load(); await session.refreshProfile() }
            catch { actionError = userMessage(for: error) }
        }
    }

    private func markAll() {
        guard let api = session.api else { return }
        actionError = nil
        Task {
            do { try await api.markAllRead(); await load(); await session.refreshProfile() }
            catch { actionError = userMessage(for: error) }
        }
    }

    private func load() async {
        guard let api = session.api else { return }
        await loader.load { try await api.notifications().data }
    }
}

// MARK: Appointments

@MainActor struct AppointmentsView: View {
    @EnvironmentObject var session: Session
    @EnvironmentObject var router: Router
    @Environment(\.openURL) private var openURL
    @StateObject private var loader = Loader<[Appointment]>()
    @State private var whenIndex = 2
    @State private var mine = false
    @State private var showCreate = false
    private let whens = [("past", "Past"), ("today", "Today"), ("future", "Upcoming")]
    private struct Key: Equatable { var when: Int; var mine: Bool }

    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            VStack(spacing: 0) {
                HStack(spacing: 8) {
                    Picker("When", selection: $whenIndex) {
                        ForEach(Array(whens.enumerated()), id: \.offset) { i, w in Text(w.1).tag(i) }
                    }.pickerStyle(.segmented)
                    Button { mine.toggle() } label: {
                        Text("Mine").font(.footnote.weight(.medium)).padding(.horizontal, 12).padding(.vertical, 7)
                            .background(mine ? Palette.brand : Palette.fill, in: Capsule())
                            .foregroundColor(mine ? Palette.onBrand : Palette.label)
                    }
                }.padding(.horizontal, 16).padding(.vertical, 8)
                LoadView(loader: loader, retry: load) { items in
                    if items.isEmpty { EmptyStateView(title: "No appointments", systemImage: "calendar") }
                    else { ScrollView { LazyVStack(spacing: 10) { ForEach(items) { card($0) } }.padding(.vertical, 6).padding(.bottom, 80) }
                        .refreshable { await load() } }
                }
            }
            if session.capabilities.canWrite(Capabilities.support) {
                FloatingButton(systemName: "plus", label: "New appointment") { showCreate = true }
            }
        }
        .background(Palette.background.ignoresSafeArea())
        .rootToolbar()
        .sheet(isPresented: $showCreate) { CreateAppointmentSheet { Task { await load() } }.presentationDetents([.large]) }
        .task(id: Key(when: whenIndex, mine: mine)) { await load() }
    }

    private func card(_ a: Appointment) -> some View {
        HStack(alignment: .top, spacing: 12) {
            RoundedRectangle(cornerRadius: 2).fill(Palette.priority(a.priority)).frame(width: 4)
            VStack(alignment: .leading, spacing: 5) {
                HStack {
                    Label(timeRange(a), systemImage: a.onsite ? "mappin.and.ellipse" : "video").font(.caption.weight(.semibold)).foregroundColor(Palette.brand)
                    Spacer()
                    if let s = a.status { Pill(text: s, small: true) }
                }
                Button { router.push(.ticket(a.ticketId)) } label: {
                    Text(a.subject).font(.subheadline.weight(.semibold)).multilineTextAlignment(.leading)
                }.buttonStyle(.plain)
                HStack(spacing: 10) {
                    if let c = a.client { Label(c, systemImage: "building.2") }
                    if let t = a.assignedTo { Label(t, systemImage: "person") }
                }.font(.caption).foregroundColor(Palette.label2)
                if let n = a.notes, !n.isEmpty { Text(n).font(.footnote).foregroundColor(Palette.label2) }
                HStack(spacing: 8) {
                    if let p = a.contactPhone, !p.isEmpty {
                        Button { if let u = URL(string: "tel:" + p.filter { $0.isNumber || $0 == "+" }) { openURL(u) } } label: {
                            Label("Call", systemImage: "phone").font(.footnote).padding(.horizontal, 12).padding(.vertical, 6)
                                .background(Palette.fill, in: Capsule())
                        }
                    }
                    if a.onsite, let map = mapURL(a) {
                        Button { openURL(map) } label: {
                            Label("Drive", systemImage: "car").font(.footnote).padding(.horizontal, 12).padding(.vertical, 6)
                                .background(Palette.fill, in: Capsule())
                        }
                    }
                    Text("#\(a.number)").font(.footnote).padding(.horizontal, 12).padding(.vertical, 6).background(Palette.fill, in: Capsule())
                }.padding(.top, 4)
            }
        }
        .padding(14).background(Palette.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous)).padding(.horizontal, 16)
    }

    private func timeRange(_ a: Appointment) -> String {
        let start = RivetDate.display(a.schedule)
        guard let end = a.scheduleEnd, !end.isEmpty else { return start }
        return "\(start) – \(RivetDate.display(end))"
    }

    private func mapURL(_ a: Appointment) -> URL? {
        let q = [a.address, a.city, a.state].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: ", ")
        guard !q.isEmpty, let enc = q.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else { return nil }
        return URL(string: "https://maps.apple.com/?daddr=\(enc)")
    }

    private func load() async {
        guard let api = session.api else { return }
        let when = whens[whenIndex].0, only = mine
        await loader.load { try await api.appointments(when: when, mine: only) }
    }
}

@MainActor struct CreateAppointmentSheet: View {
    var onCreated: () -> Void
    @EnvironmentObject var session: Session
    @Environment(\.dismiss) private var dismiss
    @State private var search = ""
    @State private var tickets: [TicketSummary] = []
    @State private var ticket: TicketSummary?
    @State private var start = Date().addingTimeInterval(3600)
    @State private var hasEnd = false
    @State private var end = Date().addingTimeInterval(7200)
    @State private var onsite = false
    @State private var notes = ""
    @State private var saving = false
    @State private var error: String?

    private var endIsValid: Bool { !hasEnd || end > start }
    private var canSave: Bool { ticket != nil && endIsValid && !saving }

    var body: some View {
        NavigationStack {
            Form {
                Section("Ticket") {
                    if let ticket {
                        HStack { Text("#\(ticket.number) \(ticket.subject)").font(.subheadline); Spacer()
                            Button("Change") { self.ticket = nil }.font(.footnote) }
                    } else {
                        SearchField(placeholder: "Search tickets…", text: $search)
                        ForEach(tickets.prefix(8)) { t in
                            Button { ticket = t } label: { Text("#\(t.number) \(t.subject)").font(.subheadline).foregroundColor(Palette.label) }
                        }
                    }
                }
                Section("When") {
                    DatePicker("Start", selection: $start)
                    Toggle("End time", isOn: $hasEnd)
                    if hasEnd { DatePicker("End", selection: $end) }
                    if !endIsValid { InlineError(message: "The end time must be after the start.") }
                }
                Section { Toggle("On-site visit", isOn: $onsite); TextField("Notes", text: $notes, axis: .vertical).lineLimit(2...5) }
                if let error { Section { InlineError(message: error) } }
            }
            .navigationTitle("New appointment").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.disabled(saving) }
                ToolbarItem(placement: .confirmationAction) {
                    Button { save() } label: { if saving { ProgressView() } else { Text("Create") } }.disabled(!canSave)
                }
            }
            .task(id: search) {
                if !search.isEmpty { try? await Task.sleep(nanoseconds: 300_000_000) }
                if Task.isCancelled { return }
                tickets = (try? await session.api?.tickets(status: "open", search: search).data) ?? []
            }
        }
        .interactiveDismissDisabled(saving)
    }

    /// Closes only after the server confirms; a failure keeps the draft and the ticket choice.
    private func save() {
        guard let api = session.api, let ticket, canSave else { return }
        saving = true; error = nil
        let body = CreateAppointmentRequest(ticketId: ticket.id, scheduleStart: RivetDate.wire(start),
                                            scheduleEnd: hasEnd ? RivetDate.wire(end) : nil, onsite: onsite, notes: notes)
        Task {
            defer { saving = false }
            do { _ = try await api.createAppointment(body); onCreated(); dismiss() }
            catch { self.error = userMessage(for: error) }
        }
    }
}
