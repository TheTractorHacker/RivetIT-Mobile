import SwiftUI
import RivetCore

/// Reply / internal note / time entry / status change in one sheet. Waits for the server before closing, blocks repeat
/// taps while saving, and keeps everything typed if the write fails.
@MainActor struct ReplySheet: View {
    let ticketId: Int
    let statuses: [TicketStatus]
    var initialMinutes: Int = 0
    var onSent: () -> Void

    @EnvironmentObject var session: Session
    @Environment(\.dismiss) private var dismiss
    @State private var isPublic = false
    @State private var onsite = false
    @State private var text = ""
    @State private var hours = 0
    @State private var minutes = 0
    @State private var statusIndex = 0
    @State private var sending = false
    @State private var error: String?
    @FocusState private var editorFocused: Bool

    private var primaryTitle: String { isPublic ? "Send public reply" : "Add internal note" }
    private var canSubmit: Bool { !sending && !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("Visibility").font(.footnote.weight(.semibold)).foregroundColor(Palette.label2)
                    Picker("Visibility", selection: $isPublic) {
                        Label("Internal note", systemImage: "lock").tag(false)
                        Label("Public reply", systemImage: "globe").tag(true)
                    }.pickerStyle(.segmented)
                    Text(isPublic ? "Department contacts can see this reply." : "Only technicians can see this note.")
                        .font(.footnote).foregroundColor(Palette.label2)

                    Picker("Visit type", selection: $onsite) {
                        Label("Remote", systemImage: "wifi").tag(false)
                        Label("On-Site", systemImage: "mappin.and.ellipse").tag(true)
                    }.pickerStyle(.segmented)

                    ZStack(alignment: .topLeading) {
                        TextEditor(text: $text).focused($editorFocused).frame(minHeight: 120)
                            .scrollContentBackground(.hidden)
                        if text.isEmpty { Text(isPublic ? "Public reply" : "Internal note").foregroundColor(Palette.label3).padding(.top, 8).padding(.leading, 5).allowsHitTesting(false) }
                    }
                    .padding(8).background(Palette.card, in: RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).stroke(Palette.brand, lineWidth: 1.5))

                    Text("Time worked").font(.footnote.weight(.semibold)).foregroundColor(Palette.label2)
                    HStack(spacing: 12) {
                        stepper("hr", value: $hours, step: 1, max: 99)
                        stepper("min", value: $minutes, step: 5, max: 55)
                    }

                    if !statuses.isEmpty {
                        Text("Set status (optional)").font(.footnote.weight(.semibold)).foregroundColor(Palette.label2)
                        FilterChips(items: ["No change"] + statuses.map(\.name), selection: $statusIndex).padding(.horizontal, -16)
                    }
                    if let error { InlineError(message: error) }
                }.padding(20)
            }
            .navigationTitle("Reply to ticket").navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() }.disabled(sending) }
            }
            .safeAreaInset(edge: .bottom) {
                Button { submit() } label: {
                    if sending { ProgressView().tint(Palette.onBrand) } else { Text(primaryTitle) }
                }
                .buttonStyle(PrimaryButtonStyle(height: 48)).disabled(!canSubmit)
                .padding(.horizontal, 20).padding(.vertical, 10).background(.bar)
            }
            .background(Palette.background.ignoresSafeArea())
        }
        .interactiveDismissDisabled(sending)
        .onAppear {
            minutes = ((initialMinutes % 60) / 5) * 5; hours = initialMinutes / 60
        }
    }

    private func stepper(_ unit: String, value: Binding<Int>, step: Int, max: Int) -> some View {
        HStack {
            Button { value.wrappedValue = Swift.max(0, value.wrappedValue - step) } label: { Image(systemName: "minus") }
                .accessibilityLabel("Decrease \(unit)")
            Spacer()
            Text("\(value.wrappedValue) \(unit)").font(.body.monospacedDigit())
            Spacer()
            Button { value.wrappedValue = Swift.min(max, value.wrappedValue + step) } label: { Image(systemName: "plus") }
                .accessibilityLabel("Increase \(unit)")
        }
        .padding(.horizontal, 14).frame(height: 44).frame(maxWidth: .infinity)
        .background(Palette.fill, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }

    private func submit() {
        guard let api = session.api, canSubmit else { return }
        sending = true; error = nil
        let statusId = statusIndex > 0 ? statuses[statusIndex - 1].id : nil
        let body = AddReplyRequest(reply: text.trimmingCharacters(in: .whitespacesAndNewlines),
                                   type: isPublic ? "reply" : "note",   // the values the server (and the Android app) use
                                   timeWorked: TimeWorked.wire(hours: hours, minutes: minutes),
                                   onsite: onsite ? 1 : 0, statusId: statusId)
        Task {
            defer { sending = false }
            do {
                try await api.addReply(ticketId: ticketId, body)
                onSent(); dismiss()
            } catch let e as RivetError {
                if case .network = e {
                    // The request may or may not have reached the server: never retry blindly.
                    error = "Could not confirm the save. Check the ticket history before trying again: " + userMessage(for: e)
                } else { error = userMessage(for: e) }
            } catch { self.error = userMessage(for: error) }
        }
    }
}
