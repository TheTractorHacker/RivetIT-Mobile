import SwiftUI
import UniformTypeIdentifiers
import RivetCore

/// Reply / internal note / time entry / status change in one sheet. Waits for the server before closing, blocks repeat
/// taps while saving, and keeps everything typed if the write fails.
@MainActor struct ReplySheet: View {
    let ticketId: Int
    let statuses: [TicketStatus]
    var initialMinutes: Int = 0
    var onSent: () -> Void
    /// Called when the reply was saved but the server stored fewer files than were sent.
    var onPartial: ((String) -> Void)? = nil

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
    @State private var files: [ReplyFile] = []
    @State private var showImporter = false
    @State private var attachProblem: String?
    @FocusState private var editorFocused: Bool

    private var primaryTitle: String { isPublic ? "Send public reply" : "Add internal note" }
    private var canAttach: Bool { session.capabilities.canWrite(Capabilities.support) }
    private var attachedBytes: Int { files.reduce(0) { $0 + $1.data.count } }
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
                    if canAttach { attachments }
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

    private var attachments: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Attachments (optional)").font(.footnote.weight(.semibold)).foregroundColor(Palette.label2)
            ForEach(files, id: \.self) { f in
                HStack {
                    Image(systemName: "paperclip").foregroundColor(Palette.label2)
                    Text("\(f.name) · \(AttachmentRules.humanSize(f.data.count))").font(.subheadline).lineLimit(1)
                    Spacer()
                    Button { files.removeAll { $0 == f } } label: { Image(systemName: "xmark.circle.fill").foregroundColor(Palette.label3) }
                        .disabled(sending).accessibilityLabel("Remove \(f.name)")
                }
            }
            Button { showImporter = true } label: { Label(files.isEmpty ? "Attach files" : "Add more", systemImage: "paperclip") }
                .disabled(sending)
            if let attachProblem { Text(attachProblem).font(.footnote).foregroundColor(.red) }
            if sending && !files.isEmpty {
                HStack(spacing: 8) { ProgressView(); Text("Uploading…").font(.footnote).foregroundColor(Palette.label2) }
            }
        }
        .fileImporter(isPresented: $showImporter, allowedContentTypes: [.item], allowsMultipleSelection: true) { result in
            guard case .success(let urls) = result else { return }
            var problems: [String] = []
            for url in urls {
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                let name = url.lastPathComponent
                let size = (try? url.resourceValues(forKeys: [.fileSizeKey]).fileSize) ?? -1
                if files.contains(where: { $0.name == name }) { continue }          // a repeat pick of the same file
                if files.count >= AttachmentRules.maxFiles { problems.append("At most \(AttachmentRules.maxFiles) files per reply"); break }
                if let p = AttachmentRules.problem(name: name, size: size) { problems.append(p); continue }
                if attachedBytes + size > AttachmentRules.maxBytesPerReply {
                    problems.append("Attachments are limited to \(AttachmentRules.humanSize(AttachmentRules.maxBytesPerReply)) per reply"); continue
                }
                guard let data = try? Data(contentsOf: url) else { problems.append("Could not read \"\(name)\""); continue }
                let mime = UTType(filenameExtension: url.pathExtension)?.preferredMIMEType ?? "application/octet-stream"
                files.append(ReplyFile(name: name, mimeType: mime, data: data))
            }
            attachProblem = problems.first
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
                if files.isEmpty {
                    try await api.addReply(ticketId: ticketId, body)
                } else {
                    let result = try await api.addReply(ticketId: ticketId, body, files: files)
                    let stored = result.attachments?.count ?? 0
                    if stored < files.count {
                        // The reply is saved, so the sheet closes; the detail view then reloads. Say which part failed.
                        onPartial?("Reply posted, but \(files.count - stored) of \(files.count) attachment(s) were rejected by the server.")
                    }
                }
                onSent(); dismiss()
            } catch let e as RivetError {
                if case .network = e {
                    // The request may or may not have reached the server: never retry blindly.
                    error = "Could not confirm the save. Check the ticket history before trying again" + (files.isEmpty ? "" : " (your draft and files are kept)") + ": " + userMessage(for: e)
                } else { error = userMessage(for: e) }
            } catch { self.error = userMessage(for: error) }
        }
    }
}
