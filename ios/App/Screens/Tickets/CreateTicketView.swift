import SwiftUI
import RivetCore

@MainActor struct CreateTicketView: View {
    @EnvironmentObject var session: Session
    @EnvironmentObject var router: Router
    @State private var subject = ""
    @State private var details = ""
    @State private var priority = "low"
    @State private var department: ClientSummary?
    @State private var category: TicketCategory?
    @State private var showDepartments = false
    @State private var showCategories = false
    @State private var saving = false
    @State private var error: String?

    private var canCreate: Bool { !saving && !subject.trimmingCharacters(in: .whitespaces).isEmpty }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 12) {
                    Image(systemName: "ticket").font(.title3).frame(width: 44, height: 44)
                        .background(Palette.brand, in: RoundedRectangle(cornerRadius: 13, style: .continuous)).foregroundColor(Palette.onBrand)
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Log a new ticket").font(.title2.weight(.bold))
                        Text("Capture the details so your team can jump on it").font(.caption).foregroundColor(Palette.label2)
                    }
                }.padding(.horizontal, 20).padding(.top, 8)

                SectionLabel(text: "Details")
                VStack(spacing: 0) {
                    TextField("Subject *", text: $subject).padding(16)
                    Divider()
                    TextField("Description", text: $details, axis: .vertical).lineLimit(4...8).padding(16)
                }.background(Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous)).padding(.horizontal, 16)

                SectionLabel(text: "Department · Category")
                VStack(spacing: 0) {
                    pickRow("building.2", department?.name ?? "Select department (optional)", set: department != nil) { showDepartments = true }
                    Divider().padding(.leading, 46)
                    pickRow("tag", category?.name ?? "Select category (optional)", set: category != nil) { showCategories = true }
                }.background(Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous)).padding(.horizontal, 16)

                SectionLabel(text: "Priority")
                Picker("Priority", selection: $priority) {
                    ForEach(["low", "medium", "high", "critical"], id: \.self) { Text($0.capitalized).tag($0) }
                }.pickerStyle(.segmented).padding(.horizontal, 16)

                if let error { InlineError(message: error).padding(16) }
            }.padding(.bottom, 30)
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationBarTitleDisplayMode(.inline)
        .safeAreaInset(edge: .bottom) {
            Button { create() } label: {
                if saving { ProgressView().tint(Palette.onBrand) } else { Label("Create Ticket", systemImage: "plus") }
            }.buttonStyle(PrimaryButtonStyle()).disabled(!canCreate).padding(.horizontal, 16).padding(.vertical, 10).background(.bar)
        }
        .sheet(isPresented: $showDepartments) {
            DepartmentPicker { department = $0; showDepartments = false }.presentationDetents([.large])
        }
        .sheet(isPresented: $showCategories) {
            CategoryPicker(selected: category) { category = $0; showCategories = false }.presentationDetents([.medium, .large])
        }
    }

    private func pickRow(_ symbol: String, _ title: String, set: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: symbol).foregroundColor(Palette.label2).frame(width: 22)
                Text(title).foregroundColor(set ? Palette.label : Palette.label2)
                Spacer()
                Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundColor(Palette.label3)
            }.padding(16)
        }.buttonStyle(.plain)
    }

    /// Creates the ticket, then opens it. The form stays put (with the error) if the server does not confirm.
    private func create() {
        guard let api = session.api, canCreate else { return }
        saving = true; error = nil
        let body = CreateTicketRequest(subject: subject.trimmingCharacters(in: .whitespaces), details: details,
                                       clientId: department?.id, priority: priority, categoryId: category?.id)
        Task {
            defer { saving = false }
            do {
                let created = try await api.createTicket(body)
                router.pop()
                if let id = created.id { router.push(.ticket(id)) } else { router.openTickets(filter: TicketFilter()) }
            } catch { self.error = userMessage(for: error) }
        }
    }
}

/// Searches the server by name so departments beyond the first page are reachable.
@MainActor struct DepartmentPicker: View {
    var onPick: (ClientSummary) -> Void
    @EnvironmentObject var session: Session
    @Environment(\.dismiss) private var dismiss
    @State private var search = ""
    @State private var results: [ClientSummary] = []
    @State private var loading = true
    @State private var error: String?

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                SearchField(placeholder: "Search departments…", text: $search).padding(16)
                if let error { ErrorStateView(message: error, retry: { await load() }) }
                else if loading && results.isEmpty { LoadingStateView() }
                else if results.isEmpty { EmptyStateView(title: "No departments found", systemImage: "building.2") }
                else {
                    List(results) { c in
                        Button { onPick(c) } label: {
                            VStack(alignment: .leading) { Text(c.name); if let city = c.city { Text(city).font(.caption).foregroundColor(Palette.label2) } }
                        }.buttonStyle(.plain)
                    }.listStyle(.plain)
                }
            }
            .navigationTitle("Department").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
            .task(id: search) {
                if !search.isEmpty { try? await Task.sleep(nanoseconds: 300_000_000) }
                if Task.isCancelled { return }
                await load()
            }
        }
    }

    private func load() async {
        guard let api = session.api else { return }
        loading = true; error = nil
        do { results = try await api.clients(search: search, page: 1).data }
        catch { self.error = userMessage(for: error) }
        loading = false
    }
}

@MainActor struct CategoryPicker: View {
    let selected: TicketCategory?
    var onPick: (TicketCategory?) -> Void
    @EnvironmentObject var session: Session
    @Environment(\.dismiss) private var dismiss
    @StateObject private var loader = Loader<[TicketCategory]>()

    var body: some View {
        NavigationStack {
            LoadView(loader: loader, retry: load) { items in
                List {
                    Button("No category") { onPick(nil) }.foregroundColor(Palette.label2)
                    ForEach(items) { c in
                        Button { onPick(c) } label: {
                            HStack { Text(c.name); Spacer(); if c.id == selected?.id { Image(systemName: "checkmark") } }
                        }.buttonStyle(.plain)
                    }
                }
            }
            .navigationTitle("Category").navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
            .task { await load() }
        }
    }

    private func load() async {
        guard let api = session.api else { return }
        await loader.load { try await api.categories() }
    }
}
