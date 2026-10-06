import SwiftUI
import RivetCore

private struct DashboardData {
    var summary: DashboardResponse
    var todayAppointments: [Appointment]
    var activeAlerts: Int
}

struct DashboardView: View {
    @EnvironmentObject var session: Session
    @EnvironmentObject var router: Router
    @StateObject private var loader = Loader<DashboardData>()

    var body: some View {
        LoadView(loader: loader, retry: load) { data in
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    header(data.summary)
                    actions
                    tiles(first: tile("Today's Appts", data.todayAppointments.count, "calendar", .lavender) { router.open(.appointments) },
                          second: tile("Active Alerts", data.activeAlerts, "bell", .gray) { router.push(.alerts) })
                    schedule(data.todayAppointments)
                    myOpen(data.summary)
                    tiles(first: tile("All Open", data.summary.allOpen, "ticket", .teal) { router.openTickets(filter: TicketFilter()) },
                          second: tile("Overdue", data.summary.overdue, "exclamationmark.triangle", .danger) {
                              router.openTickets(filter: TicketFilter(overdue: true)) })
                    tiles(first: tile("Due Today", data.summary.dueToday ?? 0, "clock", .lavender) {
                              router.openTickets(filter: TicketFilter(dueToday: true)) },
                          second: tile("On-Site", data.summary.onsiteOpen ?? 0, "mappin.and.ellipse", .gray) {
                              router.openTickets(filter: TicketFilter(onsite: true)) })
                    queue(data.summary.queue)
                }.padding(.bottom, 20)
            }
            .refreshable { await load() }
        }
        .background(Palette.background.ignoresSafeArea())
        .rootToolbar()
        .task { await load() }
    }

    private func load() async {
        guard let api = session.api else { return }
        await loader.load {
            let summary = try await api.dashboard()
            // The schedule and alert count are secondary: a failure there must not blank the whole dashboard.
            let appts = (try? await api.appointments(when: "today")) ?? []
            let alerts = (try? await api.alerts(status: "new"))?.total ?? 0
            session.hasUnread = summary.unread > 0
            return DashboardData(summary: summary, todayAppointments: appts, activeAlerts: alerts)
        }
    }

    // MARK: Sections

    private func header(_ s: DashboardResponse) -> some View {
        let hour = Calendar.current.component(.hour, from: Date())
        let greeting = hour < 12 ? "Good morning" : (hour < 18 ? "Good afternoon" : "Good evening")
        return VStack(alignment: .leading, spacing: 2) {
            Text(greeting).font(.largeTitle.weight(.bold))
            Text(s.unread > 0 ? "\(s.unread) unread notifications" : "All caught up").font(.subheadline).foregroundColor(Palette.label2)
        }.padding(.horizontal, 20).padding(.top, 10)
    }

    private var actions: some View {
        HStack(spacing: 10) {
            if session.capabilities.canWrite(Capabilities.support) {
                Button { router.push(.createTicket) } label: { Label("Ticket", systemImage: "plus") }
                    .buttonStyle(PrimaryButtonStyle(height: 42))
            }
            Button { router.push(.scan) } label: { Label("Scan", systemImage: "qrcode.viewfinder") }
                .buttonStyle(OutlineButtonStyle(height: 42))
            Button { router.push(.search) } label: { Label("Search", systemImage: "magnifyingglass") }
                .buttonStyle(OutlineButtonStyle(height: 42))
        }.font(.subheadline).padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 6)
    }

    private enum Tone { case lavender, gray, teal, danger }

    private func tile(_ title: String, _ value: Int, _ symbol: String, _ tone: Tone, action: @escaping () -> Void) -> some View {
        let bg: Color, fg: Color
        switch tone {
        case .lavender: bg = Palette.lavender; fg = Palette.onLavender
        case .gray: bg = Palette.graySoft; fg = Palette.label
        case .teal: bg = Palette.tealSoft; fg = Palette.label
        case .danger: bg = Palette.dangerSoft; fg = Palette.danger
        }
        return Button(action: action) {
            VStack(alignment: .leading, spacing: 4) {
                Image(systemName: symbol).opacity(0.75)
                Spacer(minLength: 8)
                Text("\(value)").font(.system(size: 30, weight: .bold))
                Text(title).font(.caption).opacity(0.8)
            }
            .padding(14).frame(maxWidth: .infinity, minHeight: 96, alignment: .leading)
            .background(bg, in: RoundedRectangle(cornerRadius: 18, style: .continuous)).foregroundColor(fg)
        }
        .buttonStyle(.plain)
        .accessibilityElement(children: .combine).accessibilityLabel("\(value) \(title)")
    }

    private func tiles<A: View, B: View>(first: A, second: B) -> some View {
        HStack(spacing: 10) { first; second }.padding(.horizontal, 16).padding(.vertical, 5)
    }

    private func schedule(_ appts: [Appointment]) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Today's Schedule").font(.title3.weight(.bold)).padding(.horizontal, 16).padding(.top, 14).padding(.bottom, 6)
            if appts.isEmpty {
                Text("Nothing scheduled for today.").font(.subheadline).foregroundColor(Palette.label2).padding(.horizontal, 16)
            }
            ForEach(appts.prefix(3)) { a in
                Button { router.push(.ticket(a.ticketId)) } label: {
                    ListRowCard {
                        Image(systemName: a.onsite ? "mappin.and.ellipse" : "video").foregroundColor(Palette.label2)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(RivetDate.display(a.schedule)).font(.caption).foregroundColor(Palette.label2)
                            Text(a.subject).font(.subheadline.weight(.semibold)).multilineTextAlignment(.leading)
                            if let c = a.client { Text(c).font(.caption).foregroundColor(Palette.label2) }
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundColor(Palette.label3)
                    }
                }.buttonStyle(.plain)
            }
        }
    }

    private func myOpen(_ s: DashboardResponse) -> some View {
        Button { router.openTickets(filter: TicketFilter(mine: true)) } label: {
            HStack(spacing: 14) {
                Image(systemName: "person").font(.title3).frame(width: 52, height: 52)
                    .background(Palette.brand, in: Circle()).foregroundColor(Palette.onBrand)
                VStack(alignment: .leading) {
                    Text("\(s.myOpen)").font(.system(size: 40, weight: .bold))
                    Text("tickets in My Open")
                }
                Spacer()
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold))
            }
            .padding(18).foregroundColor(Palette.label)
            .background(LinearGradient(colors: [Palette.container, Palette.lavender], startPoint: .leading, endPoint: .trailing),
                        in: RoundedRectangle(cornerRadius: 22, style: .continuous))
            .padding(.horizontal, 16).padding(.vertical, 8)
        }.buttonStyle(.plain)
    }

    private func queue(_ tickets: [TicketSummary]) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                VStack(alignment: .leading) {
                    Text("My Queue").font(.title3.weight(.bold))
                    Text("\(tickets.count) tickets").font(.caption).foregroundColor(Palette.label2)
                }
                Spacer()
                Button("See all") { router.openTickets(filter: TicketFilter(mine: true)) }.font(.subheadline.weight(.semibold))
            }.padding(.horizontal, 16).padding(.top, 16).padding(.bottom, 6)
            ForEach(tickets.prefix(5)) { t in
                NavigationLink(value: AppRoute.ticket(t.id)) { TicketRow(ticket: t, showAssignee: false) }.buttonStyle(.plain)
            }
        }
    }
}
