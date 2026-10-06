import SwiftUI
import RivetCore

/// Live chat for a ticket: polls for new messages while the screen is visible.
struct TicketChatView: View {
    let ticketId: Int
    @EnvironmentObject var session: Session
    @State private var messages: [ChatMessage] = []
    @State private var loaded = false
    @State private var draft = ""
    @State private var sending = false
    @State private var error: String?

    var body: some View {
        VStack(spacing: 0) {
            Group {
                if loaded && messages.isEmpty {
                    EmptyStateView(title: "No messages yet", systemImage: "bubble.left")
                } else {
                    ScrollViewReader { proxy in
                        ScrollView {
                            LazyVStack(spacing: 8) {
                                ForEach(messages) { m in bubble(m).id(m.id) }
                            }.padding(16)
                        }
                        .onChange(of: messages.count) { _ in
                            if let last = messages.last { withAnimation { proxy.scrollTo(last.id, anchor: .bottom) } }
                        }
                    }
                }
            }
            if let error { InlineError(message: error).padding(.horizontal, 16) }
            HStack(spacing: 10) {
                TextField("Message", text: $draft, axis: .vertical).lineLimit(1...4)
                    .padding(.horizontal, 14).padding(.vertical, 9)
                    .background(Palette.card, in: Capsule()).overlay(Capsule().stroke(Palette.separator))
                Button { send() } label: {
                    Image(systemName: "paperplane.fill").frame(width: 38, height: 38)
                        .background(canSend ? Palette.brand : Palette.fill, in: Circle())
                        .foregroundColor(canSend ? Palette.onBrand : Palette.label3)
                }.disabled(!canSend).accessibilityLabel("Send message")
            }.padding(.horizontal, 14).padding(.vertical, 10).background(.bar)
        }
        .background(Palette.background.ignoresSafeArea())
        .navigationTitle("Live Chat").navigationBarTitleDisplayMode(.inline)
        .task {
            // Poll every few seconds; the task is cancelled automatically when the screen goes away.
            while !Task.isCancelled {
                await fetch()
                try? await Task.sleep(nanoseconds: 4_000_000_000)
            }
        }
    }

    private var canSend: Bool { !sending && !draft.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    private func bubble(_ m: ChatMessage) -> some View {
        let mine = m.senderType.lowercased() != "client" && m.senderType.lowercased() != "contact"
        return VStack(alignment: mine ? .trailing : .leading, spacing: 2) {
            Text(m.message).padding(.horizontal, 12).padding(.vertical, 8)
                .background(mine ? Palette.brand : Palette.card, in: RoundedRectangle(cornerRadius: 16, style: .continuous))
                .foregroundColor(mine ? Palette.onBrand : Palette.label)
            Text("\(m.senderName ?? "") \(RivetDate.display(m.createdAt))").font(.caption2).foregroundColor(Palette.label2)
        }.frame(maxWidth: .infinity, alignment: mine ? .trailing : .leading)
    }

    private func fetch() async {
        guard let api = session.api else { return }
        do {
            let r = try await api.chat(ticketId: ticketId, sinceId: messages.last?.id ?? 0)
            let known = Set(messages.map(\.id))
            messages += r.data.filter { !known.contains($0.id) }
            loaded = true
        } catch { if !loaded { self.error = userMessage(for: error) } }
    }

    private func send() {
        guard let api = session.api, canSend else { return }
        sending = true; error = nil
        let text = draft.trimmingCharacters(in: .whitespacesAndNewlines)
        Task {
            defer { sending = false }
            do { try await api.sendChat(ticketId: ticketId, message: text); draft = ""; await fetch() }
            catch { self.error = userMessage(for: error) }
        }
    }
}
