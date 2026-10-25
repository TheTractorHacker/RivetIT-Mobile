import Foundation

// MARK: - Server setup input (port of ServerSetupInput.kt)

public enum ServerSetupInput {
    /// Tidies what a user typed into the server field: trims whitespace, drops a doubled "https://", and prepends
    /// it when only a host was typed. A plain "http://" address is left alone so the caller can still reject it.
    public static func normalizeServerURL(_ raw: String) -> String {
        var s = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        while s.lowercased().hasPrefix("https://"), String(s.dropFirst(8)).lowercased().hasPrefix("https://") {
            s = String(s.dropFirst(8))
        }
        if s.isEmpty || s.lowercased() == "https://" { return s }
        if !s.contains("://") { s = "https://" + s }
        while s.hasSuffix("/") { s.removeLast() }
        return s
    }

    /// True when `input` is the last six hex characters of `fingerprint`, with or without colons or spaces.
    public static func fingerprintSuffixMatches(_ input: String, fingerprint: String) -> Bool {
        func clean(_ v: String) -> String { String(v.filter { $0 != ":" && !$0.isWhitespace }) }
        let want = String(clean(fingerprint).suffix(6))
        return !want.isEmpty && clean(input).caseInsensitiveCompare(want) == .orderedSame
    }

    /// "61:7D:49" — the form the dialog asks the user to type.
    public static func suffixHint(fingerprint: String) -> String {
        let hex = fingerprint.filter { $0 != ":" }.suffix(6)
        var out: [String] = []
        var idx = hex.startIndex
        while idx < hex.endIndex {
            let next = hex.index(idx, offsetBy: 2, limitedBy: hex.endIndex) ?? hex.endIndex
            out.append(String(hex[idx..<next])); idx = next
        }
        return out.joined(separator: ":")
    }
}

// MARK: - Permissions (port of Capabilities.kt)

/// What the signed-in user may see and change, built from `/me`. Used only to avoid showing actions that would be
/// refused; the server still enforces every permission. A server that reports no permissions keeps everything visible.
public struct Capabilities: Equatable {
    public static let support = "module_support"
    public static let client = "module_client"
    public static let credential = "module_credential"
    public static let kb = "module_kb"
    public static let reporting = "module_reporting"
    public static let assets = "module_assets"
    public static let rmmAlerts = "module_rmm_alerts"
    public static let unrestricted = Capabilities()

    public var isAdmin: Bool
    public var permissions: [String: Int]?

    public init(isAdmin: Bool = false, permissions: [String: Int]? = nil) {
        self.isAdmin = isAdmin
        // Keys are normalised so `module_support` and `moduleSupport` (a snake-case decoder can rewrite map keys) match.
        self.permissions = permissions.map { Dictionary(uniqueKeysWithValues: $0.map { (Capabilities.norm($0.key), $0.value) }) }
    }

    public init(profile: UserProfile) { self.init(isAdmin: profile.isAdmin, permissions: profile.permissions) }

    public func canView(_ module: String) -> Bool { level(module) >= 1 }
    public func canWrite(_ module: String) -> Bool { level(module) >= 2 }

    private func level(_ module: String) -> Int {
        if isAdmin { return 3 }
        guard let permissions else { return 3 }
        return permissions[Capabilities.norm(module)] ?? 0
    }

    static func norm(_ key: String) -> String { key.replacingOccurrences(of: "_", with: "").lowercased() }
}

// MARK: - Deep links (port of DeepLinks.kt)

public enum AppRoute: Equatable, Hashable {
    case tickets, ticket(Int), ticketChat(Int)
    case clients, client(Int)
    case assets, asset(Int)
    case projects, project(Int)
    case contracts, contract(Int)
    case credentials, notifications, appointments, search, reports, scan, profile, alerts
    case kb, kbArticle(Int)
    /// In-app only (the new-ticket form); never produced by `DeepLinks.resolve`.
    case createTicket
}

public enum DeepLinks {
    private static let allowed = try! NSRegularExpression(
        pattern: #"^(tickets|clients|assets|projects|contracts|credentials|notifications|appointments|worksheets|outtakes|search|reports|scan|profile|kb|alerts)(/\d+(/\w+)?)?$"#)

    public static func isAllowed(_ raw: String) -> Bool {
        allowed.firstMatch(in: raw, range: NSRange(raw.startIndex..., in: raw)) != nil
    }

    /// Maps an externally supplied route (push payload, URL) to a real destination, or nil when it is not allowed or
    /// has no screen. Bare `worksheets`/`outtakes` have no list screen, only per-id ones.
    public static func resolve(_ raw: String) -> AppRoute? {
        guard isAllowed(raw) else { return nil }
        let parts = raw.split(separator: "/").map(String.init)
        let id = parts.count > 1 ? Int(parts[1]) : nil
        let sub = parts.count > 2 ? parts[2] : nil
        switch parts[0] {
        case "tickets":
            guard let id else { return .tickets }
            if sub == nil { return .ticket(id) }
            return sub == "chat" ? .ticketChat(id) : nil
        case "clients": return id.map(AppRoute.client) ?? .clients
        case "assets": return id.map(AppRoute.asset) ?? .assets
        case "projects": return id.map(AppRoute.project) ?? .projects
        case "contracts": return id.map(AppRoute.contract) ?? .contracts
        case "kb": return id.map(AppRoute.kbArticle) ?? .kb
        case "credentials": return .credentials
        case "notifications": return .notifications
        case "appointments": return .appointments
        case "search": return .search
        case "reports": return .reports
        case "scan": return .scan
        case "profile": return .profile
        case "alerts": return .alerts
        default: return nil   // worksheets / outtakes
        }
    }
}

// MARK: - Errors (port of ErrorMessages.kt)

public enum RivetError: Error, Equatable {
    case invalidURL
    case http(status: Int, message: String?)
    case network(String)
    case certificate
    case decoding(String)
    case unauthorized

    public var statusCode: Int? { if case .http(let s, _) = self { return s }; return nil }
}

public func userMessage(for error: Error) -> String {
    switch error as? RivetError {
    case .some(.http(let status, let message)):
        if let message, !message.trimmingCharacters(in: .whitespaces).isEmpty { return message }
        switch status {
        case 401: return "Your session has expired. Please sign in again."
        case 403: return "You don't have permission to do this."
        case 404: return "Not found."
        case 500...599: return "The server ran into a problem. Please try again."
        default: return "Something went wrong. Please try again."
        }
    case .some(.unauthorized): return "Your session has expired. Please sign in again."
    case .some(.network): return "Network error — check your connection and try again."
    case .some(.certificate): return "The server's certificate could not be verified."
    case .some(.invalidURL): return "That server address isn't valid."
    case .some(.decoding): return "The server sent a response the app could not read."
    case .none: return "Something went wrong. Please try again."
    }
}

// MARK: - Formatting (port of DateFormat.kt and friends)

public enum RivetDate {
    private static func formatter(_ pattern: String) -> DateFormatter {
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX")
        f.timeZone = TimeZone(identifier: "UTC")
        f.dateFormat = pattern
        return f
    }

    /// Server timestamps are "yyyy-MM-dd HH:mm:ss" (or a bare date); shown as "MM/dd/yyyy h:mm a" / "MM/dd/yyyy".
    public static func display(_ raw: String?) -> String {
        guard let raw, !raw.isEmpty else { return "" }
        if raw.count > 10 {
            if let d = formatter("yyyy-MM-dd HH:mm:ss").date(from: raw) { return formatter("MM/dd/yyyy h:mm a").string(from: d) }
        } else if let d = formatter("yyyy-MM-dd").date(from: raw) {
            return formatter("MM/dd/yyyy").string(from: d)
        }
        return raw
    }

    public static func dateOnly(_ raw: String?) -> String {
        guard let raw, raw.count >= 10 else { return raw ?? "" }
        return display(String(raw.prefix(10)))
    }

    /// The server stores wall-clock time with no zone, so requests carry the picker's local clock reading
    /// (not UTC), and `parse` reads a server string back as local wall-clock time.
    private static func localFormatter(_ pattern: String) -> DateFormatter {
        let f = formatter(pattern); f.timeZone = TimeZone.current; return f
    }
    /// "yyyy-MM-dd HH:mm:ss" for requests.
    public static func wire(_ date: Date) -> String { localFormatter("yyyy-MM-dd HH:mm:ss").string(from: date) }
    public static func parse(_ raw: String?) -> Date? {
        guard let raw else { return nil }
        return localFormatter("yyyy-MM-dd HH:mm:ss").date(from: raw) ?? localFormatter("yyyy-MM-dd").date(from: raw)
    }
}

public enum TimeWorked {
    /// "HH:MM:SS" the API expects, or nil when no time was entered.
    public static func wire(hours: Int, minutes: Int) -> String? {
        guard hours > 0 || minutes > 0 else { return nil }
        return String(format: "%02d:%02d:00", hours, minutes)
    }
    /// "1h 15m" for display from the server's "01:15:00".
    public static func display(_ raw: String?) -> String? {
        guard let raw else { return nil }
        let p = raw.split(separator: ":").compactMap { Int($0) }
        guard p.count >= 2, p[0] > 0 || p[1] > 0 else { return nil }
        return p[0] > 0 ? "\(p[0])h \(p[1])m" : "\(p[1])m"
    }
}

public enum Initials {
    public static func letter(_ name: String) -> String {
        name.trimmingCharacters(in: .whitespaces).first.map { String($0).uppercased() } ?? "?"
    }
}

public enum PriorityTone: String { case low, medium, high, critical, none
    public init(_ raw: String?) {
        switch raw?.lowercased() {
        case "low": self = .low
        case "medium": self = .medium
        case "high": self = .high
        case "critical": self = .critical
        default: self = .none
        }
    }
}

public extension String {
    /// "categoryId" -> "category_id" (a snake-case decoder rewrites dictionary keys; saved ticket views need the originals).
    var snakeCased: String {
        var out = ""
        for ch in self {
            if ch.isUppercase { out.append("_"); out.append(contentsOf: String(ch).lowercased()) } else { out.append(ch) }
        }
        return out
    }
}

public extension SavedTicketView {
    var snakeParams: [String: String] { Dictionary(uniqueKeysWithValues: params.map { ($0.key.snakeCased, $0.value) }) }
}

// MARK: - Paging state

public struct PagedState<T> {
    public var items: [T] = []
    public var total: Int = 0
    public var page: Int = 0
    public var isRefreshing: Bool = false
    public var isLoadingMore: Bool = false
    public var error: String?
    public init() {}
    public var hasMore: Bool { items.count < total }
}
