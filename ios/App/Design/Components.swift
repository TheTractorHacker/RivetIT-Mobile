import SwiftUI
import RivetCore

// MARK: Brand

@MainActor struct BrandMark: View {
    var size: CGFloat = 28
    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous).fill(Palette.brandGradient)
            Text("R").font(.system(size: size * 0.58, weight: .heavy)).foregroundColor(.white)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

// MARK: Pills, chips, avatars

enum PillTone { case gray, blue, cyan, lavender, danger, custom(Color) }

@MainActor struct Pill: View {
    let text: String
    var tone: PillTone = .gray
    var small = false

    private var colors: (Color, Color) {
        switch tone {
        case .gray: return (Palette.fill, Palette.label2)
        case .blue: return (Color.blue.opacity(0.14), Color.blue)
        case .cyan: return (Palette.container, Palette.onContainer)
        case .lavender: return (Palette.lavender, Palette.onLavender)
        case .danger: return (Palette.dangerSoft, Palette.danger)
        case .custom(let c): return (c.opacity(0.16), c)
        }
    }
    var body: some View {
        Text(text)
            .font(.system(size: small ? 11 : 12, weight: .semibold))
            .padding(.horizontal, small ? 8 : 10).padding(.vertical, small ? 3 : 4)
            .background(colors.0, in: RoundedRectangle(cornerRadius: 7, style: .continuous))
            .foregroundColor(colors.1)
    }
}

@MainActor struct AvatarCircle: View {
    let letter: String
    var size: CGFloat = 42
    var body: some View {
        Text(letter).font(.system(size: size * 0.43, weight: .semibold))
            .frame(width: size, height: size)
            .background(Palette.container, in: Circle()).foregroundColor(Palette.onContainer)
            .accessibilityHidden(true)
    }
}

@MainActor struct IconTile: View {
    let systemName: String
    var size: CGFloat = 40
    var round = false
    var body: some View {
        Image(systemName: systemName).font(.system(size: size * 0.45, weight: .regular))
            .frame(width: size, height: size).foregroundColor(Palette.brand)
            .background(Palette.tealSoft, in: RoundedRectangle(cornerRadius: round ? size / 2 : 12, style: .continuous))
            .accessibilityHidden(true)
    }
}

/// Capsule filter row with a single selection (the Android FilterChip rows).
@MainActor struct FilterChips: View {
    let items: [String]
    @Binding var selection: Int
    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(Array(items.enumerated()), id: \.offset) { idx, title in
                    Button { selection = idx } label: {
                        Text(title).font(.subheadline.weight(.medium)).lineLimit(1)
                            .padding(.horizontal, 14).padding(.vertical, 7)
                            .background(idx == selection ? Palette.brand : Palette.fill, in: Capsule())
                            .foregroundColor(idx == selection ? Palette.onBrand : Palette.label)
                    }.buttonStyle(.plain)
                    .accessibilityAddTraits(idx == selection ? .isSelected : [])
                }
            }.padding(.horizontal, 16)
        }
    }
}

@MainActor struct SearchField: View {
    let placeholder: String
    @Binding var text: String
    var body: some View {
        HStack(spacing: 7) {
            Image(systemName: "magnifyingglass").foregroundColor(Palette.label2)
            TextField(placeholder, text: $text).textInputAutocapitalization(.never).autocorrectionDisabled()
            if !text.isEmpty {
                Button { text = "" } label: { Image(systemName: "xmark.circle.fill").foregroundColor(Palette.label3) }
                    .accessibilityLabel("Clear search")
            }
        }
        .padding(.horizontal, 10).frame(height: 38)
        .background(Palette.fill, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
}

// MARK: Rows

@MainActor struct KeyValueRow: View {
    let key: String
    let value: String
    var body: some View {
        HStack(alignment: .firstTextBaseline) {
            Text(key).foregroundColor(Palette.label2)
            Spacer(minLength: 12)
            Text(value).fontWeight(.medium).multilineTextAlignment(.trailing)
        }.font(.subheadline).padding(.vertical, 6)
    }
}

@MainActor struct SectionLabel: View {
    let text: String
    var tint = false
    var body: some View {
        Text(text).font(.footnote.weight(tint ? .bold : .medium)).foregroundColor(tint ? Palette.brand : Palette.label2)
            .padding(.horizontal, 20).padding(.top, 14).padding(.bottom, 4).frame(maxWidth: .infinity, alignment: .leading)
    }
}

@MainActor struct ListRowCard<Content: View>: View {
    let content: Content
    init(@ViewBuilder content: () -> Content) { self.content = content() }
    var body: some View {
        HStack(spacing: 12) { content }
            .padding(.horizontal, 14).padding(.vertical, 12)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .padding(.horizontal, 16).padding(.vertical, 5)
    }
}

// MARK: Buttons

struct PrimaryButtonStyle: ButtonStyle {
    var height: CGFloat = 50
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.headline).frame(maxWidth: .infinity).frame(height: height)
            .background(Palette.brand, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .foregroundColor(Palette.onBrand).opacity(configuration.isPressed ? 0.85 : 1)
    }
}

struct OutlineButtonStyle: ButtonStyle {
    var height: CGFloat = 50
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.font(.headline).frame(maxWidth: .infinity).frame(height: height)
            .background(Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous).stroke(Palette.separator, lineWidth: 1))
            .foregroundColor(Palette.brand).opacity(configuration.isPressed ? 0.85 : 1)
    }
}

@MainActor struct FloatingButton: View {
    let systemName: String
    let label: String
    let action: () -> Void
    var body: some View {
        Button(action: action) {
            Image(systemName: systemName).font(.system(size: 22, weight: .semibold))
                .frame(width: 56, height: 56)
                .background(Palette.container, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
                .foregroundColor(Palette.onContainer)
                .shadow(color: .black.opacity(0.18), radius: 8, y: 4)
        }
        .padding(.trailing, 18).padding(.bottom, 14)
        .accessibilityLabel(label)
    }
}

// MARK: States

@MainActor struct ErrorStateView: View {
    let message: String
    var retry: (() async -> Void)?
    var body: some View {
        VStack(spacing: 10) {
            Text("Something went wrong").font(.headline)
            Text(message).font(.subheadline).foregroundColor(Palette.label2).multilineTextAlignment(.center)
            if let retry {
                Button("Retry") { Task { await retry() } }.buttonStyle(.borderedProminent).tint(Palette.brand)
            }
        }.padding(32).frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

@MainActor struct EmptyStateView: View {
    let title: String
    var systemImage = "tray"
    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: systemImage).font(.system(size: 44)).foregroundColor(Palette.label3)
            Text(title).font(.subheadline).foregroundColor(Palette.label2).multilineTextAlignment(.center)
        }.padding(32).frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

@MainActor struct LoadingStateView: View {
    var body: some View { ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity) }
}

/// Renders a `Loader` as loading / error-with-retry / content.
@MainActor struct LoadView<T, Content: View>: View {
    @ObservedObject var loader: Loader<T>
    var retry: () async -> Void
    @ViewBuilder var content: (T) -> Content

    var body: some View {
        switch loader.state {
        case .idle, .loading: LoadingStateView()
        case .failed(let message): ErrorStateView(message: message, retry: retry)
        case .loaded(let value): content(value)
        }
    }
}

/// Shown instead of a screen the signed-in user's role cannot use (reachable by deep link even though menus hide it).
@MainActor struct AccessDeniedView: View {
    var body: some View { EmptyStateView(title: "You don't have access to this section", systemImage: "lock") }
}

@MainActor struct AccessGuard<Content: View>: View {
    @EnvironmentObject var session: Session
    let module: String
    let content: Content
    init(module: String, @ViewBuilder content: () -> Content) { self.module = module; self.content = content() }
    var body: some View {
        if session.capabilities.canView(module) { content } else { AccessDeniedView() }
    }
}

/// Inline banner for a failed write that keeps the user's input in place.
@MainActor struct InlineError: View {
    let message: String
    var body: some View {
        Text(message).font(.footnote).foregroundColor(Palette.danger)
            .frame(maxWidth: .infinity, alignment: .leading).accessibilityAddTraits(.isStaticText)
    }
}

// MARK: Ticket rows

@MainActor struct TicketRow: View {
    let ticket: TicketSummary
    var showAssignee = true

    var body: some View {
        HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 2).fill(Palette.priority(ticket.priority)).frame(width: 4)
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 8) {
                    Text("#\(ticket.number)").font(.caption.weight(.bold)).foregroundColor(Palette.brand)
                    if showAssignee, let who = ticket.assignedTo, !who.isEmpty {
                        Label(who, systemImage: "person").labelStyle(.titleAndIcon)
                            .font(.caption).foregroundColor(Palette.label2)
                    }
                }
                Text(ticket.subject).font(.subheadline.weight(.semibold)).multilineTextAlignment(.leading)
                if let client = ticket.client, !client.isEmpty {
                    Label(client, systemImage: "building.2").font(.caption).foregroundColor(Palette.label2)
                }
            }
            Spacer(minLength: 4)
            Image(systemName: "chevron.right").font(.footnote.weight(.semibold)).foregroundColor(Palette.label3)
        }
        .padding(.horizontal, 12).padding(.vertical, 11)
        .background(Palette.card, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .padding(.horizontal, 16).padding(.vertical, 5)
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Ticket \(ticket.number), \(ticket.subject), priority \(ticket.priority ?? "none"), \(ticket.status ?? "")")
    }
}
