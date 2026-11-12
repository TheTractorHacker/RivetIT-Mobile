import XCTest
import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif
@testable import RivetCore

/// Canned-response transport that records every request.
final class MockTransport: HTTPTransport, @unchecked Sendable {
    var requests: [URLRequest] = []
    var status = 200
    var body = Data()
    var error: Error?
    var headers: [String: String] = [:]
    func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        requests.append(request)
        if let error { throw error }
        let resp = HTTPURLResponse(url: request.url!, statusCode: status, httpVersion: nil, headerFields: headers)!
        return (body, resp)
    }
}

private func decode<T: Decodable>(_ type: T.Type, _ json: String) throws -> T {
    try APIClient.decoder.decode(T.self, from: Data(json.utf8))
}

final class DecodingTests: XCTestCase {
    func testLoginAndProfile() throws {
        let login = try decode(LoginResponse.self, Fixtures.login)
        XCTAssertNotNil(login.token); XCTAssertEqual(login.user?.name, "Alex Morgan")
        let me = try decode(UserProfile.self, Fixtures.me)
        XCTAssertTrue(me.isAdmin)
        let caps = Capabilities(profile: me)
        XCTAssertTrue(caps.canWrite(Capabilities.support), "permissions map keys must survive the snake-case decoder")
    }
    func testDashboardAndTickets() throws {
        let d = try decode(DashboardResponse.self, Fixtures.dashboard)
        XCTAssertGreaterThan(d.allOpen, 0)
        let t = try decode(Paged<TicketSummary>.self, Fixtures.tickets)
        XCTAssertFalse(t.data.isEmpty); XCTAssertNotNil(t.data.first?.statusColor)
        let detail = try decode(TicketDetail.self, Fixtures.ticket)
        XCTAssertFalse(detail.subject.isEmpty); XCTAssertFalse(detail.replies.isEmpty)
        XCTAssertEqual(try decode([TicketStatus].self, Fixtures.statuses).first?.name, "New")
        XCTAssertFalse(try decode([TicketCategory].self, Fixtures.categories).isEmpty)
        _ = try decode(ChatMessagesResponse.self, Fixtures.chat)
    }
    func testDirectoryScreens() throws {
        XCTAssertFalse(try decode(Paged<ClientSummary>.self, Fixtures.clients).data.isEmpty)
        let client = try decode(ClientDetail.self, Fixtures.client)
        XCTAssertFalse(client.name.isEmpty)
        XCTAssertFalse(try decode([TicketSummary].self, Fixtures.clientTickets).isEmpty)
        XCTAssertFalse(try decode(Paged<AssetSummary>.self, Fixtures.assets).data.isEmpty)
        XCTAssertFalse(try decode(AssetDetail.self, Fixtures.asset).name.isEmpty)
    }
    func testWorkScreens() throws {
        XCTAssertFalse(try decode(Paged<ProjectSummary>.self, Fixtures.projects).data.isEmpty)
        let p = try decode(ProjectDetail.self, Fixtures.project)
        XCTAssertFalse(p.tasks.isEmpty)
        XCTAssertFalse(try decode(Paged<ContractSummary>.self, Fixtures.contracts).data.isEmpty)
        let c = try decode(ContractDetail.self, Fixtures.contract)
        XCTAssertNotNil(c.sla.high.responseTime)
        XCTAssertFalse(try decode(Paged<CredentialSummary>.self, Fixtures.credentials).data.isEmpty)
    }
    func testMonitoringAndKnowledgeBase() throws {
        XCTAssertFalse(try decode(NotificationsResponse.self, Fixtures.notifications).data.isEmpty)
        _ = try decode(AlertsResponse.self, Fixtures.alerts)
        let appts = try decode([Appointment].self, Fixtures.appointments)
        XCTAssertFalse(appts.isEmpty); XCTAssertTrue(appts[0].onsite)
        XCTAssertFalse(try decode(Paged<KbArticleSummary>.self, Fixtures.kbArticles).data.isEmpty)
        XCTAssertNotNil(try decode(KbArticleDetail.self, Fixtures.kbArticle).content)
        XCTAssertFalse(try decode([KbCategory].self, Fixtures.kbCategories).isEmpty)
        let s = try decode(SearchResult.self, Fixtures.search)
        XCTAssertFalse(s.tickets.isEmpty && s.assets.isEmpty && s.clients.isEmpty)
    }
    func testOverviewReport() throws {
        let r = try decode(OverviewReport.self, Fixtures.overview)
        XCTAssertEqual(r.year, 2026); XCTAssertEqual(r.byPriority.first?.priority, "High"); XCTAssertNotNil(r.avgResolutionHours)
    }
    func testLossyBooleansAcceptNumbers() throws {
        let r = try decode(TicketReply.self, #"{"id":1,"body":"x","type":"reply","onsite":1}"#)
        XCTAssertEqual(r.onsite, true)
        let r2 = try decode(TicketReply.self, #"{"id":2,"body":"x","type":"reply","onsite":"0"}"#)
        XCTAssertEqual(r2.onsite, false)
    }
    func testSavedViewParamsCanBeRestoredToWireKeys() throws {
        let v = try decode(SavedTicketView.self, #"{"id":1,"name":"x","icon":null,"params":{"category_id":"4","status":"open"}}"#)
        XCTAssertEqual(v.snakeParams["category_id"], "4")
    }
}

final class APIClientTests: XCTestCase {
    private let server = URL(string: "https://10.1.0.45:8444")!

    func testBuildsRequestsWithBearerTokenAndQuery() async throws {
        let t = MockTransport(); t.body = Data(Fixtures.tickets.utf8)
        let client = APIClient(serverURL: server, token: "abc", transport: t)
        _ = try await client.tickets(status: "open", mine: true, search: "wifi", page: 2)
        let req = try XCTUnwrap(t.requests.first)
        XCTAssertEqual(req.httpMethod, "GET")
        XCTAssertEqual(req.value(forHTTPHeaderField: "Authorization"), "Bearer abc")
        XCTAssertEqual(req.value(forHTTPHeaderField: "Cache-Control"), "no-store")
        let url = req.url!.absoluteString
        XCTAssertTrue(url.hasPrefix("https://10.1.0.45:8444/api/v1/tickets?"))
        XCTAssertTrue(url.contains("mine=1")); XCTAssertTrue(url.contains("page=2")); XCTAssertTrue(url.contains("search=wifi"))
    }

    func testEmptyQueryValuesAreDropped() async throws {
        let t = MockTransport(); t.body = Data(Fixtures.clients.utf8)
        let client = APIClient(serverURL: server, token: "abc", transport: t)
        _ = try await client.clients(search: "", page: 1)
        XCTAssertFalse(t.requests[0].url!.absoluteString.contains("search"))
    }

    func testLoginSendsSnakeCaseBodyWithoutToken() async throws {
        let t = MockTransport(); t.body = Data(Fixtures.login.utf8)
        let client = APIClient(serverURL: server, transport: t)
        _ = try await client.login(username: "a@b.c", password: "pw", deviceName: "iPhone")
        let req = t.requests[0]
        XCTAssertEqual(req.httpMethod, "POST"); XCTAssertNil(req.value(forHTTPHeaderField: "Authorization"))
        let body = try XCTUnwrap(req.httpBody)
        let obj = try XCTUnwrap(JSONSerialization.jsonObject(with: body) as? [String: Any])
        XCTAssertEqual(obj["device_name"] as? String, "iPhone"); XCTAssertEqual(obj["username"] as? String, "a@b.c")
    }

    func testReplyBodyUsesWireNames() async throws {
        let t = MockTransport(); t.body = Data("{}".utf8)
        let client = APIClient(serverURL: server, token: "abc", transport: t)
        try await client.addReply(ticketId: 5, AddReplyRequest(reply: "hi", type: "internal", timeWorked: "01:15:00", onsite: 0, statusId: 3))
        XCTAssertTrue(t.requests[0].url!.absoluteString.hasSuffix("/api/v1/tickets/5/reply"))
        let obj = try XCTUnwrap(JSONSerialization.jsonObject(with: try XCTUnwrap(t.requests[0].httpBody)) as? [String: Any])
        XCTAssertEqual(obj["time_worked"] as? String, "01:15:00"); XCTAssertEqual(obj["status_id"] as? Int, 3)
        XCTAssertEqual(obj["onsite"] as? Int, 0)
    }

    func testStatusChangeBody() async throws {
        let t = MockTransport(); t.body = Data("{}".utf8)
        try await APIClient(serverURL: server, token: "abc", transport: t).setStatus(ticketId: 9, statusId: 5)
        let obj = try XCTUnwrap(JSONSerialization.jsonObject(with: try XCTUnwrap(t.requests[0].httpBody)) as? [String: Any])
        XCTAssertEqual(obj["status_id"] as? Int, 5)
    }

    func testHTTPErrorCarriesServerMessage() async {
        let t = MockTransport(); t.status = 403; t.body = Data(#"{"error":"Insufficient permissions"}"#.utf8)
        do { _ = try await APIClient(serverURL: server, token: "x", transport: t).kbArticles(); XCTFail("expected failure") }
        catch { XCTAssertEqual(error as? RivetError, .http(status: 403, message: "Insufficient permissions")) }
    }

    private func sentBody(_ t: MockTransport) throws -> [String: Any] {
        try XCTUnwrap(JSONSerialization.jsonObject(with: try XCTUnwrap(t.requests[0].httpBody)) as? [String: Any])
    }

    func testKeyEnrolmentSendsPasswordAndOmitsCodeWhenNotNeeded() async throws {
        let t = MockTransport(); t.body = Data(#"{"ok":true}"#.utf8)
        try await APIClient(serverURL: server, token: "x", transport: t).registerBiometricKey(pem: "PEM", currentPassword: "hunter2")
        XCTAssertEqual(t.requests[0].httpMethod, "PUT"); XCTAssertTrue(t.requests[0].url!.absoluteString.hasSuffix("/api/v1/me"))
        let obj = try sentBody(t)
        XCTAssertEqual(obj["device_public_key_pem"] as? String, "PEM")
        XCTAssertEqual(obj["current_password"] as? String, "hunter2")
        XCTAssertNil(obj["totp_code"])
    }

    func testKeyEnrolmentSendsCodeWhenGiven() async throws {
        let t = MockTransport(); t.body = Data(#"{"ok":true}"#.utf8)
        try await APIClient(serverURL: server, token: "x", transport: t).registerBiometricKey(pem: "PEM", currentPassword: "hunter2", totpCode: "123456")
        XCTAssertEqual(try sentBody(t)["totp_code"] as? String, "123456")
    }

    func testKeyBodyNeverPrintsSecrets() {
        let body = RegisterKeyBody(devicePublicKeyPem: "PEM", currentPassword: "hunter2", totpCode: "123456")
        for text in [String(describing: body), String(reflecting: body), "\(body)"] {
            XCTAssertFalse(text.contains("hunter2")); XCTAssertFalse(text.contains("123456"))
        }
    }

    func testEnrolment401DoesNotSignOutAndMapsToPrompt() async {
        let t = MockTransport(); t.status = 401; t.body = Data(#"{"error":"Current password is required to register a biometric key"}"#.utf8)
        let client = APIClient(serverURL: server, token: "x", transport: t)
        let flag = Flag(); client.onUnauthorized = { flag.set() }
        do { try await client.registerBiometricKey(pem: "P", currentPassword: "bad"); XCTFail("expected failure") }
        catch {
            XCTAssertFalse(flag.value, "a wrong password is not an expired session")
            guard case .wrongPassword = classifyEnrolFailure(error) else { return XCTFail("got \(classifyEnrolFailure(error))") }
        }
        t.body = Data(#"{"error":"MFA code required to register a biometric key"}"#.utf8)
        do { try await client.registerBiometricKey(pem: "P", currentPassword: "ok"); XCTFail("expected failure") }
        catch { guard case .needsCode = classifyEnrolFailure(error) else { return XCTFail("got \(classifyEnrolFailure(error))") } }
    }

    func testRateLimitUsesRetryAfter() async {
        let t = MockTransport(); t.status = 429; t.headers = ["Retry-After": "600"]; t.body = Data(#"{"error":"Too many attempts. Try again later."}"#.utf8)
        let client = APIClient(serverURL: server, token: "x", transport: t)
        do { try await client.registerBiometricKey(pem: "P", currentPassword: "x"); XCTFail("expected failure") }
        catch {
            XCTAssertEqual(error as? RivetError, .rateLimited(retryAfter: 600))
            XCTAssertEqual(classifyEnrolFailure(error), .rateLimited("Too many attempts. Try again in about 10 minutes."))
        }
        t.headers = [:]
        do { _ = try await client.credential(id: 1, challengeToken: "c", signature: "s"); XCTFail("expected failure") }
        catch { XCTAssertEqual(userMessage(for: error), "Too many attempts. Try again in a few minutes.") }
    }

    func testRevealKeyMissingTriggersEnrolment() {
        XCTAssertTrue(revealNeedsKeyEnrolment(RivetError.http(status: 403, message: "No biometric key registered for this device")))
        XCTAssertTrue(revealNeedsKeyEnrolment(RivetError.http(status: 403, message: "Biometric verification failed")))
        XCTAssertFalse(revealNeedsKeyEnrolment(RivetError.http(status: 403, message: "Access denied")))
        XCTAssertFalse(revealNeedsKeyEnrolment(RivetError.rateLimited(retryAfter: nil)))
    }

    func testUnauthorizedSignsOutOnlyWhenSignedIn() async {
        let t = MockTransport(); t.status = 401; t.body = Data(#"{"error":"Unauthorized"}"#.utf8)
        let signedIn = APIClient(serverURL: server, token: "x", transport: t)
        let flag = Flag(); signedIn.onUnauthorized = { flag.set() }
        _ = try? await signedIn.dashboard()
        XCTAssertTrue(flag.value)

        let anonymous = APIClient(serverURL: server, token: nil, transport: t)
        let flag2 = Flag(); anonymous.onUnauthorized = { flag2.set() }
        _ = try? await anonymous.login(username: "a", password: "b", deviceName: "d")
        XCTAssertFalse(flag2.value, "a failed login must not look like an expired session")
    }

    func testTransportFailuresBecomeNetworkErrors() async {
        let t = MockTransport(); t.error = URLError(.notConnectedToInternet)
        do { _ = try await APIClient(serverURL: server, token: "x", transport: t).dashboard(); XCTFail() }
        catch { if case .some(.network) = error as? RivetError {} else { XCTFail("got \(error)") } }
    }

    func testUndecodableBodyIsADecodingError() async {
        let t = MockTransport(); t.body = Data("not json".utf8)
        do { _ = try await APIClient(serverURL: server, token: "x", transport: t).dashboard(); XCTFail() }
        catch { if case .some(.decoding) = error as? RivetError {} else { XCTFail("got \(error)") } }
    }

    func testCertificateFailuresSurfaceAsCertificateErrors() async {
        let t = MockTransport(); t.error = URLError(.serverCertificateUntrusted)
        let client = APIClient(serverURL: server, token: nil, transport: t)
        do { _ = try await client.probe(); XCTFail() } catch { XCTAssertEqual(error as? RivetError, .certificate) }
        do { _ = try await client.dashboard(); XCTFail() } catch { XCTAssertEqual(error as? RivetError, .certificate) }
    }

    func testCertificateErrorCodesAreRecognised() {
        XCTAssertTrue(RivetError.isCertificateProblem(URLError(.serverCertificateUntrusted)))
        XCTAssertFalse(RivetError.isCertificateProblem(URLError(.timedOut)))
    }
}

final class Flag: @unchecked Sendable {
    private(set) var value = false
    func set() { value = true }
}
