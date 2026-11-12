import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// Abstraction over the network so the client can be unit-tested with canned responses.
public protocol HTTPTransport: Sendable {
    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse)
}

struct AnyEncodable: Encodable {
    let encodeTo: (Encoder) throws -> Void
    init<T: Encodable>(_ value: T) { encodeTo = { try value.encode(to: $0) } }
    func encode(to encoder: Encoder) throws { try encodeTo(encoder) }
}

/// REST client for `<server>/api/v1/`. Bearer-token auth, snake_case JSON, and the same error semantics as the
/// Android client: a 401 while signed in calls `onUnauthorized`, other failures surface as `RivetError`.
public final class APIClient: @unchecked Sendable {
    public let serverURL: URL
    public var token: String?
    public var onUnauthorized: (@Sendable () -> Void)?
    let transport: HTTPTransport

    public init(serverURL: URL, token: String? = nil, transport: HTTPTransport) {
        self.serverURL = serverURL
        self.token = token
        self.transport = transport
    }

    // MARK: Coding

    static let decoder: JSONDecoder = {
        let d = JSONDecoder()
        d.keyDecodingStrategy = .convertFromSnakeCase
        return d
    }()
    static let encoder: JSONEncoder = {
        let e = JSONEncoder()
        e.keyEncodingStrategy = .convertToSnakeCase
        return e
    }()

    // MARK: Requests

    func makeRequest(_ method: String, _ path: String, query: [String: String] = [:], body: AnyEncodable? = nil) throws -> URLRequest {
        var comps = URLComponents(url: serverURL.appendingPathComponent("api/v1/\(path)"), resolvingAgainstBaseURL: false)
        let items = query.filter { !$0.value.isEmpty }.sorted { $0.key < $1.key }.map { URLQueryItem(name: $0.key, value: $0.value) }
        if !items.isEmpty { comps?.queryItems = items }
        guard let url = comps?.url else { throw RivetError.invalidURL }
        var req = URLRequest(url: url, cachePolicy: .reloadIgnoringLocalCacheData, timeoutInterval: 30)
        req.httpMethod = method
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        req.setValue("no-store", forHTTPHeaderField: "Cache-Control")
        if let token { req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization") }
        if let body {
            req.httpBody = try APIClient.encoder.encode(body)
            req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        return req
    }

    @discardableResult
    func perform(_ method: String, _ path: String, query: [String: String] = [:], body: AnyEncodable? = nil) async throws -> Data {
        try await perform(request: try makeRequest(method, path, query: query, body: body))
    }

    @discardableResult
    func perform(request: URLRequest, signOutOn401: Bool = true) async throws -> Data {
        let data: Data, response: HTTPURLResponse
        do { (data, response) = try await transport.send(request) }
        catch let e as RivetError { throw e }
        catch let e as URLError { throw RivetError.isCertificateProblem(e) ? RivetError.certificate : RivetError.network(e.localizedDescription) }
        catch { throw RivetError.network(error.localizedDescription) }

        guard (200..<300).contains(response.statusCode) else {
            if response.statusCode == 401, token != nil, signOutOn401 { onUnauthorized?() }
            if response.statusCode == 429 {
                let retry = response.value(forHTTPHeaderField: "Retry-After").flatMap { Int($0.trimmingCharacters(in: .whitespaces)) }
                throw RivetError.rateLimited(retryAfter: retry)
            }
            let message = (try? JSONSerialization.jsonObject(with: data) as? [String: Any])?["error"] as? String
            throw RivetError.http(status: response.statusCode, message: message)
        }
        return data
    }

    func get<T: Decodable>(_ path: String, query: [String: String] = [:]) async throws -> T {
        let data = try await perform("GET", path, query: query)
        do { return try APIClient.decoder.decode(T.self, from: data) }
        catch { throw RivetError.decoding(String(describing: error)) }
    }

    func send<T: Decodable, B: Encodable>(_ method: String, _ path: String, body: B) async throws -> T {
        let data = try await perform(method, path, body: AnyEncodable(body))
        do { return try APIClient.decoder.decode(T.self, from: data) }
        catch { throw RivetError.decoding(String(describing: error)) }
    }

    func sendVoid<B: Encodable>(_ method: String, _ path: String, body: B) async throws {
        try await perform(method, path, body: AnyEncodable(body))
    }

    func sendVoid(_ method: String, _ path: String) async throws {
        try await perform(method, path)
    }

    /// Reachability check used by server setup: any answer below 500 means "this is a server that answered".
    public func probe() async throws -> Int {
        do {
            let request = try makeRequest("GET", "auth")
            let (_, response) = try await transport.send(request)
            return response.statusCode
        } catch let e as URLError {
            throw RivetError.isCertificateProblem(e) ? RivetError.certificate : RivetError.network(e.localizedDescription)
        }
    }
}

// MARK: - Endpoints (one method per call the Android app makes)

private struct Empty: Encodable {}
private struct StatusBody: Encodable { var statusId: Int }
/// `totpCode` is nil (and left out of the JSON) unless the server asked for it. Never logged or stored.
struct RegisterKeyBody: Encodable, CustomStringConvertible, CustomDebugStringConvertible {
    var devicePublicKeyPem: String
    var currentPassword: String
    var totpCode: String?
    var description: String { "RegisterKeyBody(<redacted>)" }
    var debugDescription: String { description }
}

public extension APIClient {
    // Auth & profile
    func login(username: String, password: String, deviceName: String, totp: String? = nil) async throws -> LoginResponse {
        try await send("POST", "auth", body: LoginRequest(username: username, password: password, deviceName: deviceName, totpCode: totp))
    }
    func logout() async throws { try await sendVoid("DELETE", "auth") }
    func profile() async throws -> UserProfile { try await get("me") }
    func updateProfile(_ body: UpdateProfileRequest) async throws { try await sendVoid("PUT", "me", body: body) }

    // Dashboard & search
    func dashboard() async throws -> DashboardResponse { try await get("dashboard") }
    func search(_ q: String) async throws -> SearchResult { try await get("search", query: ["q": q]) }

    // Tickets
    func tickets(status: String = "open", mine: Bool = false, search: String = "", page: Int = 1,
                 priority: String? = nil, onsite: Bool? = nil, categoryId: Int? = nil,
                 overdue: Bool? = nil, dueToday: Bool? = nil) async throws -> Paged<TicketSummary> {
        var q: [String: String] = ["status": status, "page": String(page)]
        if mine { q["mine"] = "1" }
        if !search.isEmpty { q["search"] = search }
        if let priority { q["priority"] = priority }
        if let onsite { q["onsite"] = onsite ? "1" : "0" }
        if let categoryId { q["category_id"] = String(categoryId) }
        if let overdue { q["overdue"] = overdue ? "1" : "0" }
        if let dueToday { q["due_today"] = dueToday ? "1" : "0" }
        return try await get("tickets", query: q)
    }
    func ticket(_ id: Int) async throws -> TicketDetail { try await get("tickets/\(id)") }
    func addReply(ticketId: Int, _ body: AddReplyRequest) async throws { try await sendVoid("POST", "tickets/\(ticketId)/reply", body: body) }

    /// A reply with files in one request, so the reply and its attachments are confirmed together.
    func addReply(ticketId: Int, _ body: AddReplyRequest, files: [ReplyFile]) async throws -> ReplyWithFilesResult {
        var form = MultipartForm()
        form.fields = [("reply", body.reply), ("type", body.type), ("onsite", String(body.onsite))]
        if let t = body.timeWorked { form.fields.append(("time_worked", t)) }
        if let s = body.statusId { form.fields.append(("status_id", String(s))) }
        form.files = files
        var request = try makeRequest("POST", "tickets/\(ticketId)/reply")
        request.httpBody = form.body()
        request.setValue(form.contentType, forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 300
        let data = try await perform(request: request)
        do { return try APIClient.decoder.decode(ReplyWithFilesResult.self, from: data) }
        catch { throw RivetError.decoding(String(describing: error)) }
    }
    func deleteReply(ticketId: Int, replyId: Int) async throws { try await sendVoid("DELETE", "tickets/\(ticketId)/reply/\(replyId)") }
    func setStatus(ticketId: Int, statusId: Int) async throws { try await sendVoid("POST", "tickets/\(ticketId)/status", body: StatusBody(statusId: statusId)) }
    func statuses() async throws -> [TicketStatus] { try await get("statuses") }
    func categories() async throws -> [TicketCategory] { try await get("ticket-categories") }
    func savedViews() async throws -> [SavedTicketView] { try await get("ticket-views") }
    func createTicket(_ body: CreateTicketRequest) async throws -> IdResponse { try await send("POST", "tickets", body: body) }
    func worksheets(ticketId: Int) async throws -> [WorksheetSummary] { try await get("tickets/\(ticketId)/worksheets") }
    func outtakes(ticketId: Int) async throws -> [OuttakeSummary] { try await get("tickets/\(ticketId)/outtakes") }
    func chat(ticketId: Int, sinceId: Int = 0) async throws -> ChatMessagesResponse {
        try await get("tickets/\(ticketId)/chat", query: sinceId > 0 ? ["since_id": String(sinceId)] : [:])
    }
    func sendChat(ticketId: Int, message: String) async throws { try await sendVoid("POST", "tickets/\(ticketId)/chat", body: SendChatMessageRequest(message: message)) }

    // Contacts (searchable, optionally limited to one department)
    func contacts(clientId: Int? = nil, search: String = "") async throws -> Paged<Contact> {
        var query = ["search": search, "limit": "50"]
        if let clientId { query["client_id"] = String(clientId) }
        return try await get("contacts", query: query)
    }

    // Departments (API name: clients)
    func clients(search: String = "", page: Int = 1) async throws -> Paged<ClientSummary> {
        try await get("clients", query: ["search": search, "page": String(page)])
    }
    func client(_ id: Int) async throws -> ClientDetail { try await get("clients/\(id)") }
    func clientTickets(_ id: Int) async throws -> [TicketSummary] { try await get("clients/\(id)/tickets") }
    func clientAssets(_ id: Int) async throws -> [AssetSummary] { try await get("clients/\(id)/assets") }
    func clientLocations(_ id: Int) async throws -> [ClientLocation] { try await get("clients/\(id)/locations") }
    func clientCredentials(_ id: Int) async throws -> [CredentialSummary] { try await get("clients/\(id)/credentials") }
    func clientContracts(_ id: Int) async throws -> [ClientContract] { try await get("clients/\(id)/contracts") }

    // Assets
    func assets(search: String = "", page: Int = 1, type: String = "") async throws -> Paged<AssetSummary> {
        try await get("assets", query: ["search": search, "page": String(page), "type": type])
    }
    func assetTypes() async throws -> [String] { try await get("assets/types") }
    func asset(_ id: Int) async throws -> AssetDetail { try await get("assets/\(id)") }

    // Projects
    func projects(search: String = "", page: Int = 1, status: String = "open") async throws -> Paged<ProjectSummary> {
        try await get("projects", query: ["search": search, "page": String(page), "status": status])
    }
    func project(_ id: Int) async throws -> ProjectDetail { try await get("projects/\(id)") }
    func toggleTask(_ id: Int) async throws -> ProjectTask { try await send("POST", "tasks/\(id)/toggle", body: Empty()) }

    // Contracts
    func contracts(search: String = "", page: Int = 1, expiring: Bool = false) async throws -> Paged<ContractSummary> {
        try await get("contracts", query: ["search": search, "page": String(page), "expiring": expiring ? "1" : ""])
    }
    func contract(_ id: Int) async throws -> ContractDetail { try await get("contracts/\(id)") }

    // Credentials (list only; secrets need the biometric challenge flow)
    /// Step 1 of revealing a credential: a fresh challenge from the server.
    func biometricChallenge() async throws -> BiometricChallenge { try await get("auth") }

    /// Registers this device's signing key. The server now needs the account password (and the TOTP / recovery code
    /// on MFA accounts); a wrong or missing value is a 401 that does NOT sign the user out, and the key stays unaccepted.
    func registerBiometricKey(pem: String, currentPassword: String, totpCode: String? = nil) async throws {
        let body = RegisterKeyBody(devicePublicKeyPem: pem, currentPassword: currentPassword, totpCode: totpCode)
        let request = try makeRequest("PUT", "me", body: AnyEncodable(body))
        try await perform(request: request, signOutOn401: false)
    }

    /// Step 3: the credential with its secrets, proven by the signature over the challenge.
    func credential(id: Int, challengeToken: String, signature: String) async throws -> CredentialDetail {
        var request = try makeRequest("GET", "credentials/\(id)")
        request.setValue(challengeToken, forHTTPHeaderField: "X-Biometric-Challenge-Token")
        request.setValue(signature, forHTTPHeaderField: "X-Biometric-Signature")
        let data = try await perform(request: request)
        do { return try APIClient.decoder.decode(CredentialDetail.self, from: data) }
        catch { throw RivetError.decoding(String(describing: error)) }
    }

    func credentials(search: String = "", page: Int = 1) async throws -> Paged<CredentialSummary> {
        try await get("credentials", query: ["search": search, "page": String(page)])
    }

    // Notifications, alerts, appointments
    func notifications(page: Int = 1) async throws -> NotificationsResponse { try await get("notifications", query: ["page": String(page)]) }
    func markRead(_ id: Int) async throws { try await sendVoid("POST", "notifications/\(id)/read") }
    func markAllRead() async throws { try await sendVoid("POST", "notifications/read-all") }
    func alerts(status: String = "new") async throws -> AlertsResponse { try await get("alerts", query: ["status": status]) }
    func actOnAlert(_ body: AlertActionRequest) async throws { try await sendVoid("POST", "alerts", body: body) }
    func appointments(when: String = "future", mine: Bool = false) async throws -> [Appointment] {
        try await get("appointments", query: ["when": when, "mine": mine ? "1" : ""])
    }
    func createAppointment(_ body: CreateAppointmentRequest) async throws -> IdResponse { try await send("POST", "appointments", body: body) }

    // Reports
    func overviewReport(year: Int? = nil) async throws -> OverviewReport {
        try await get("reports/overview", query: year.map { ["year": String($0)] } ?? [:])
    }

    // Knowledge base
    func kbCategories() async throws -> [KbCategory] { try await get("kb/categories") }
    func kbArticles(categoryId: Int? = nil, search: String = "", page: Int = 1) async throws -> Paged<KbArticleSummary> {
        var q: [String: String] = ["search": search, "page": String(page)]
        if let categoryId { q["category_id"] = String(categoryId) }
        return try await get("kb/articles", query: q)
    }
    func kbArticle(_ id: Int) async throws -> KbArticleDetail { try await get("kb/articles/\(id)") }
}
