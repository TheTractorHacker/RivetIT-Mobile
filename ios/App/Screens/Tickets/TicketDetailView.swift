import SwiftUI
import RivetCore

@MainActor struct TicketDetailView: View {
    let id: Int
    @EnvironmentObject var session: Session
    @EnvironmentObject var router: Router
    @StateObject private var loader = Loader<TicketDetail>()
    @State private var statuses: [TicketStatus] = []
    @State private var showReply = false
    @State private var showStatusPicker = false
    @State private var confirmResolve = false
    @State private var actionError: String?
    @State private var resolving = false

    // Timer (the Android timer icon): counts up and pre-fills the time worked when the reply sheet opens.
    @State private var timerRunning = false
    @State private var elapsed = 0
    private let tick = Timer.publish(every: 1, on: .main, in: .common).autoconnect()

    private var canWrite: Bool { session.capabilities.canWrite(Capabilities.support) }
    private var closedStatus: TicketStatus? { statuses.first { $0.name.lowercased() == "closed" } }

    var body: some View {
        VStack(spacing: 0) {
            LoadView(loader: loader, retry: load) { ticket in
                ScrollView {
                    VStack(spacing: 12) {
                        header(ticket)
                        if timerRunning || elapsed > 0 { timerBar }
                        if let d = ticket.details, !d.isEmpty { description(d) }
                        formsCard
                        if !ticket.replies.isEmpty { replies(ticket.replies) }
                        if let actionError { InlineError(message: actionError).padding(.horizontal, 16) }
                    }.padding(.vertical, 8)
                }.refreshable { await load() }
            }
            // The action bar only exists once the ticket has loaded and the role may write.
            if let ticket = loader.value, canWrite { actionBar(ticket) }
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle(loader.value.map { "#\($0.number)" } ?? "Ticket")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItemGroup(placement: .navigationBarTrailing) {
                Button { router.push(.ticketChat(id)) } label: { Image(systemName: "bubble.left.and.bubble.right") }
                    .accessibilityLabel("Live chat")
                if canWrite {
                    Button { timerRunning.toggle() } label: { Image(systemName: timerRunning ? "pause.circle" : "play.circle") }
                        .accessibilityLabel(timerRunning ? "Pause timer" : "Start timer")
                    if !statuses.isEmpty {
                        Button { showStatusPicker = true } label: { Image(systemName: "arrow.up.arrow.down") }
                            .accessibilityLabel("Change status")
                    }
                }
            }
        }
        .sheet(isPresented: $showReply) {
            if let ticket = loader.value {
                ReplySheet(ticketId: ticket.id, statuses: statuses, initialMinutes: max(0, elapsed / 60)) {
                    elapsed = 0; timerRunning = false
                    Task { await load() }
                }.presentationDetents([.large])
            }
        }
        .confirmationDialog("Change status", isPresented: $showStatusPicker, titleVisibility: .visible) {
            ForEach(statuses) { s in Button(s.name) { setStatus(s) } }
        }
        .alert("Resolve Ticket?", isPresented: $confirmResolve) {
            Button("Cancel", role: .cancel) {}
            Button("Resolve") { if let s = closedStatus { setStatus(s) } }
        } message: { Text("This will mark the ticket as Closed.") }
        .task { await load(); await loadStatuses() }
        .onReceive(tick) { _ in if timerRunning { elapsed += 1 } }
    }

    // MARK: Sections

    private func header(_ t: TicketDetail) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                if let p = t.priority { Pill(text: p.capitalized, tone: .custom(Palette.priority(p))) }
                if let s = t.status { Pill(text: s, tone: .custom(Color(serverHex: t.statusColor))) }
            }
            Text(t.subject).font(.title2.weight(.bold)).fixedSize(horizontal: false, vertical: true)
            if let c = t.client { meta("building.2", c) }
            if let a = t.assignedTo, !a.isEmpty { meta("person", "Assigned: \(a)") }
            if let n = t.contactName, !n.isEmpty { meta("person.text.rectangle", n) }
            if let p = t.contactPhone, !p.isEmpty { meta("phone", p) }
            meta("calendar", "Opened: \(RivetDate.display(t.createdAt))")
            if let due = t.dueAt, !due.isEmpty { meta("clock", "Due: \(RivetDate.display(due))") }
        }
        .frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)
    }

    private func meta(_ symbol: String, _ text: String) -> some View {
        Label { Text(text).font(.subheadline) } icon: { Image(systemName: symbol).foregroundColor(Palette.label2).frame(width: 18) }
    }

    private func description(_ html: String) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("Description").font(.caption.weight(.semibold)).foregroundColor(Palette.label2)
            Text(HTMLText.plain(html)).font(.body)
        }.frame(maxWidth: .infinity, alignment: .leading).card().padding(.horizontal, 16)
    }

    /// Worksheets and outtake forms: the lists the Android card shows. Creating and filling them is not in the iOS app yet.
    private var formsCard: some View {
        FormsCard(ticketId: id).padding(.horizontal, 16)
    }

    private func replies(_ replies: [TicketReply]) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("\(replies.count) \(replies.count == 1 ? "reply" : "replies")").font(.footnote.weight(.semibold))
                .foregroundColor(Palette.label2).padding(.horizontal, 20)
            ForEach(replies) { r in ReplyCard(reply: r) }
        }
    }

    private var timerBar: some View {
        HStack {
            Image(systemName: "timer")
            Text(String(format: "%02d:%02d:%02d", elapsed / 3600, (elapsed % 3600) / 60, elapsed % 60))
                .font(.system(.title3, design: .monospaced).weight(.bold))
            Spacer()
            Button("Log Time") { timerRunning = false; showReply = true }
        }
        .foregroundColor(Palette.onContainer).card()
        .background(Palette.container, in: RoundedRectangle(cornerRadius: 16, style: .continuous)).padding(.horizontal, 16)
    }

    private func actionBar(_ t: TicketDetail) -> some View {
        let resolved = ["closed", "resolved"].contains((t.status ?? "").lowercased())
        return HStack(spacing: 12) {
            if !resolved, closedStatus != nil {
                Button { confirmResolve = true } label: { Label("Resolve", systemImage: "checkmark.circle") }
                    .buttonStyle(OutlineButtonStyle(height: 48)).disabled(resolving)
            }
            Button { showReply = true } label: { Label("Reply / note", systemImage: "square.and.pencil") }
                .buttonStyle(PrimaryButtonStyle(height: 48))
        }
        .padding(.horizontal, 16).padding(.vertical, 10)
        .background(.bar)
    }

    // MARK: Data

    private func load() async {
        guard let api = session.api else { return }
        await loader.load { try await api.ticket(id) }
    }

    private func loadStatuses() async {
        guard let api = session.api else { return }
        statuses = (try? await api.statuses()) ?? []
    }

    /// Waits for the server before changing anything on screen, and ignores repeat taps while a change is in flight.
    private func setStatus(_ status: TicketStatus) {
        guard let api = session.api, !resolving else { return }
        resolving = true; actionError = nil
        Task {
            defer { resolving = false }
            do { try await api.setStatus(ticketId: id, statusId: status.id); await load() }
            catch { actionError = userMessage(for: error) }
        }
    }
}

@MainActor struct ReplyCard: View {
    let reply: TicketReply
    var body: some View {
        let isNote = ["internal", "note"].contains(reply.type.lowercased())
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                AvatarCircle(letter: Initials.letter(reply.by ?? "?"), size: 30)
                VStack(alignment: .leading, spacing: 0) {
                    Text(reply.by ?? "Customer").font(.footnote.weight(.semibold))
                    Text(RivetDate.display(reply.createdAt)).font(.caption).foregroundColor(Palette.label2)
                }
                Spacer()
                Pill(text: isNote ? "Note" : (reply.type.lowercased() == "reply" ? "Public reply" : reply.type.capitalized),
                     tone: isNote ? .lavender : .cyan, small: true)
                if reply.onsite == true { Pill(text: "On-site", small: true) } else if reply.onsite == false { Pill(text: "Remote", small: true) }
                if let t = TimeWorked.display(reply.timeWorked) { Pill(text: t, tone: .cyan, small: true) }
            }
            Text(HTMLText.plain(reply.body)).font(.subheadline)
        }
        .frame(maxWidth: .infinity, alignment: .leading).card(padding: 14)
        .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).stroke(isNote ? Palette.brand.opacity(0.25) : Color.clear, lineWidth: 1))
        .padding(.horizontal, 16)
        .accessibilityElement(children: .combine)
    }
}

/// Worksheets / outtake forms attached to a ticket (read-only lists, like the Android card without the add buttons).
@MainActor struct FormsCard: View {
    let ticketId: Int
    @EnvironmentObject var session: Session
    @State private var worksheets: [WorksheetSummary] = []
    @State private var outtakes: [OuttakeSummary] = []

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            VStack(alignment: .leading, spacing: 4) {
                Text("Worksheets").font(.subheadline.weight(.semibold))
                if worksheets.isEmpty { Text("No worksheets.").font(.caption).foregroundColor(Palette.label2) }
                ForEach(worksheets) { w in
                    HStack {
                        Text(w.templateName ?? "Worksheet #\(w.id)").font(.subheadline); Spacer()
                        Pill(text: w.completedAt != nil ? "Completed" : "In progress", tone: w.completedAt != nil ? .cyan : .gray, small: true)
                    }
                }
            }
            Divider()
            VStack(alignment: .leading, spacing: 4) {
                Text("Outtake Forms").font(.subheadline.weight(.semibold))
                Text("Contact signs on pickup").font(.caption).foregroundColor(Palette.label2)
                if outtakes.isEmpty { Text("No outtake forms.").font(.caption).foregroundColor(Palette.label2) }
                ForEach(outtakes) { o in
                    HStack {
                        Text("Outtake #\(o.id)").font(.subheadline); Spacer()
                        Pill(text: o.signed ? "Signed" : "Awaiting signature", tone: o.signed ? .cyan : .gray, small: true)
                    }
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading).card()
        .task {
            guard let api = session.api else { return }
            worksheets = (try? await api.worksheets(ticketId: ticketId)) ?? []
            outtakes = (try? await api.outtakes(ticketId: ticketId)) ?? []
        }
    }
}

/// Server HTML (descriptions, replies, KB) shown as plain text for lists and cards.
enum HTMLText {
    static func plain(_ html: String) -> String {
        var s = html
        for (tag, replacement) in [("<br>", "\n"), ("<br/>", "\n"), ("<br />", "\n"), ("</p>", "\n"), ("</div>", "\n"), ("</li>", "\n")] {
            s = s.replacingOccurrences(of: tag, with: replacement, options: .caseInsensitive)
        }
        s = s.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        for (entity, char) in [("&nbsp;", " "), ("&amp;", "&"), ("&lt;", "<"), ("&gt;", ">"), ("&quot;", "\""), ("&#39;", "'")] {
            s = s.replacingOccurrences(of: entity, with: char)
        }
        return s.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
