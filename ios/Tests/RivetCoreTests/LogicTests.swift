import XCTest
@testable import RivetCore

final class ServerSetupInputTests: XCTestCase {
    func testKeepsWellFormedURLAndDropsTrailingSlash() {
        XCTAssertEqual(ServerSetupInput.normalizeServerURL("https://10.1.0.45:8444/"), "https://10.1.0.45:8444")
    }
    func testCollapsesDoubledScheme() {
        XCTAssertEqual(ServerSetupInput.normalizeServerURL("https://https://example.com"), "https://example.com")
        XCTAssertEqual(ServerSetupInput.normalizeServerURL(" HTTPS://https://example.com "), "https://example.com")
    }
    func testAddsHTTPSToBareHost() {
        XCTAssertEqual(ServerSetupInput.normalizeServerURL("example.com:8445"), "https://example.com:8445")
    }
    func testLeavesEmptyAndPrefixOnlyInputAlone() {
        XCTAssertEqual(ServerSetupInput.normalizeServerURL("  "), "")
        XCTAssertEqual(ServerSetupInput.normalizeServerURL("https://"), "https://")
    }
    func testDoesNotUpgradePlainHTTP() {
        XCTAssertEqual(ServerSetupInput.normalizeServerURL("http://example.com"), "http://example.com")
    }
    private let fp = "16:59:E5:18:FC:27:E7:AF:03:73:5B:39:05:7F:50:C6:AE:5C:4C:74:C1:FC:DE:4F:7C:BA:5C:CF:B0:61:7D:49"
    func testFingerprintSuffixAcceptsColonPlainAndSpacedForms() {
        XCTAssertTrue(ServerSetupInput.fingerprintSuffixMatches("61:7D:49", fingerprint: fp))
        XCTAssertTrue(ServerSetupInput.fingerprintSuffixMatches("617d49", fingerprint: fp))
        XCTAssertTrue(ServerSetupInput.fingerprintSuffixMatches(" 61 7D 49 ", fingerprint: fp))
    }
    func testFingerprintSuffixRejectsWrongOrEmptyInput() {
        XCTAssertFalse(ServerSetupInput.fingerprintSuffixMatches("617D4", fingerprint: fp))
        XCTAssertFalse(ServerSetupInput.fingerprintSuffixMatches("000000", fingerprint: fp))
        XCTAssertFalse(ServerSetupInput.fingerprintSuffixMatches("", fingerprint: fp))
        XCTAssertFalse(ServerSetupInput.fingerprintSuffixMatches("617D49", fingerprint: ""))
    }
    func testSuffixHintShowsTheColonForm() {
        XCTAssertEqual(ServerSetupInput.suffixHint(fingerprint: fp), "61:7D:49")
    }
}

final class CapabilitiesTests: XCTestCase {
    func testServerThatReportsNoPermissionsKeepsEverythingVisible() {
        let c = Capabilities(isAdmin: false, permissions: nil)
        XCTAssertTrue(c.canView(Capabilities.credential)); XCTAssertTrue(c.canWrite(Capabilities.support))
    }
    func testAdminSeesAndChangesEverything() {
        let c = Capabilities(isAdmin: true, permissions: [Capabilities.kb: 0])
        XCTAssertTrue(c.canView(Capabilities.kb)); XCTAssertTrue(c.canWrite(Capabilities.kb))
    }
    func testReadOnlyLevelCanViewButNotWrite() {
        let c = Capabilities(permissions: [Capabilities.support: 1])
        XCTAssertTrue(c.canView(Capabilities.support)); XCTAssertFalse(c.canWrite(Capabilities.support))
    }
    func testWriteAndFullLevelsCanChange() {
        let c = Capabilities(permissions: [Capabilities.support: 2, Capabilities.client: 3])
        XCTAssertTrue(c.canWrite(Capabilities.support)); XCTAssertTrue(c.canWrite(Capabilities.client))
    }
    func testNoneOrMissingModuleIsHidden() {
        let c = Capabilities(permissions: [Capabilities.credential: 0])
        XCTAssertFalse(c.canView(Capabilities.credential)); XCTAssertFalse(c.canView(Capabilities.kb))
    }
    func testCamelCasedKeysFromASnakeCaseDecoderStillMatch() {
        let c = Capabilities(permissions: ["moduleSupport": 1])
        XCTAssertTrue(c.canView(Capabilities.support)); XCTAssertFalse(c.canWrite(Capabilities.support))
    }
}

final class DeepLinksTests: XCTestCase {
    func testResolvesTopLevelAndIdRoutes() {
        XCTAssertEqual(DeepLinks.resolve("tickets"), .tickets)
        XCTAssertEqual(DeepLinks.resolve("tickets/157"), .ticket(157))
        XCTAssertEqual(DeepLinks.resolve("tickets/157/chat"), .ticketChat(157))
        XCTAssertEqual(DeepLinks.resolve("kb/1"), .kbArticle(1))
        XCTAssertEqual(DeepLinks.resolve("scan"), .scan)
        XCTAssertEqual(DeepLinks.resolve("clients/22"), .client(22))
    }
    func testRejectsUnknownListlessAndTraversalRoutes() {
        XCTAssertNil(DeepLinks.resolve("worksheets"))
        XCTAssertNil(DeepLinks.resolve("outtakes"))
        XCTAssertNil(DeepLinks.resolve("admin/settings"))
        XCTAssertNil(DeepLinks.resolve("tickets/../profile"))
        XCTAssertNil(DeepLinks.resolve("tickets/12/bogus"))
    }
}

final class FormattingTests: XCTestCase {
    func testDateTimeAndDateOnly() {
        XCTAssertEqual(RivetDate.display("2026-10-04 10:26:00"), "10/04/2026 10:26 AM")
        XCTAssertEqual(RivetDate.display("2026-10-18"), "10/18/2026")
        XCTAssertEqual(RivetDate.dateOnly("2026-10-04 15:00:00"), "10/04/2026")
        XCTAssertEqual(RivetDate.display(nil), "")
        XCTAssertEqual(RivetDate.display("not a date"), "not a date")
    }
    func testWireFormatCarriesTheLocalClockReading() {
        var c = DateComponents(); c.year = 2026; c.month = 10; c.day = 4; c.hour = 15; c.minute = 30
        let d = Calendar.current.date(from: c)!
        XCTAssertEqual(RivetDate.wire(d), "2026-10-04 15:30:00", "the wall-clock time, whatever the device time zone")
        XCTAssertEqual(RivetDate.parse("2026-10-04 15:30:00"), d)
    }
    func testTimeWorked() {
        XCTAssertNil(TimeWorked.wire(hours: 0, minutes: 0))
        XCTAssertEqual(TimeWorked.wire(hours: 1, minutes: 15), "01:15:00")
        XCTAssertEqual(TimeWorked.display("01:15:00"), "1h 15m")
        XCTAssertEqual(TimeWorked.display("00:45:00"), "45m")
        XCTAssertNil(TimeWorked.display("00:00:00"))
    }
    func testSnakeCasedAndInitials() {
        XCTAssertEqual("categoryId".snakeCased, "category_id")
        XCTAssertEqual(Initials.letter("  alex morgan"), "A")
        XCTAssertEqual(Initials.letter(""), "?")
    }
    func testPriorityTone() {
        XCTAssertEqual(PriorityTone("Critical"), .critical)
        XCTAssertEqual(PriorityTone(nil), .none)
    }
}

final class ErrorMessageTests: XCTestCase {
    func testBackendMessageWins() {
        XCTAssertEqual(userMessage(for: RivetError.http(status: 403, message: "Insufficient permissions")), "Insufficient permissions")
    }
    func testStatusFallbacks() {
        XCTAssertEqual(userMessage(for: RivetError.http(status: 401, message: nil)), "Your session has expired. Please sign in again.")
        XCTAssertEqual(userMessage(for: RivetError.http(status: 403, message: nil)), "You don't have permission to do this.")
        XCTAssertEqual(userMessage(for: RivetError.http(status: 404, message: nil)), "Not found.")
        XCTAssertEqual(userMessage(for: RivetError.http(status: 503, message: nil)), "The server ran into a problem. Please try again.")
        XCTAssertEqual(userMessage(for: RivetError.network("x")), "Network error — check your connection and try again.")
    }
}

final class CertificateTests: XCTestCase {
    func testDemoCertificateSummary() throws {
        let der = try XCTUnwrap(PEM.der(from: Fixtures.demoCertPEM))
        let s = CertificateSummary(der: der)
        XCTAssertEqual(s.commonName, "rivet-demo")
        XCTAssertEqual(s.fingerprint, "16:59:E5:18:FC:27:E7:AF:03:73:5B:39:05:7F:50:C6:AE:5C:4C:74:C1:FC:DE:4F:7C:BA:5C:CF:B0:61:7D:49")
        let year = Calendar(identifier: .gregorian).dateComponents(in: TimeZone(identifier: "UTC")!, from: try XCTUnwrap(s.notAfter)).year
        XCTAssertEqual(year, 2027)
    }
    func testGarbageDERDoesNotCrash() {
        let s = CertificateSummary(der: Data([0x01, 0x02, 0x03]))
        XCTAssertNil(s.commonName); XCTAssertNil(s.notAfter)
        XCTAssertEqual(s.fingerprint.count, 95)
    }
}

final class CompanyLogoTests: XCTestCase {
    private let server = URL(string: "https://rivet.example.com")!

    func testResolvesUploadedLogoOnTheSameServer() {
        XCTAssertEqual(CompanyLogo.url(server: server, path: "/uploads/settings/logo.png")?.absoluteString,
                       "https://rivet.example.com/uploads/settings/logo.png")
    }
    func testNoLogoMeansNoURL() {
        XCTAssertNil(CompanyLogo.url(server: server, path: nil))
        XCTAssertNil(CompanyLogo.url(server: server, path: ""))
    }
    func testRefusesAnotherHost() {
        XCTAssertNil(CompanyLogo.url(server: server, path: "https://evil.example.net/uploads/settings/logo.png"))
        XCTAssertNil(CompanyLogo.url(server: server, path: "//evil.example.net/uploads/settings/logo.png"))
    }
    func testRefusesOtherPortsAndPathsOutsideTheSettingsFolder() {
        XCTAssertNil(CompanyLogo.url(server: server, path: "https://rivet.example.com:8443/uploads/settings/logo.png"))
        XCTAssertNil(CompanyLogo.url(server: server, path: "/api/v1/me"))
        XCTAssertNil(CompanyLogo.url(server: server, path: "/uploads/settings/../../api/v1/me"))
    }
    func testDecodesTheBrandingResponse() throws {
        let b = try APIClient.decoder.decode(CompanyBranding.self,
            from: Data(#"{"name":"Summit Ridge Manufacturing","logo_path":"\/uploads\/settings\/a.png"}"#.utf8))
        XCTAssertEqual(b.name, "Summit Ridge Manufacturing")
        XCTAssertEqual(b.logoPath, "/uploads/settings/a.png")
        let none = try APIClient.decoder.decode(CompanyBranding.self, from: Data(#"{"name":"X","logo_path":null}"#.utf8))
        XCTAssertNil(none.logoPath)
    }
}

final class TicketAttachmentTests: XCTestCase {
    func testAllowedTypePasses() { XCTAssertNil(AttachmentRules.problem(name: "Report.PDF", size: 1024)) }
    func testDisallowedTypeIsRejected() { XCTAssertNotNil(AttachmentRules.problem(name: "run.exe", size: 1024)) }
    func testNoExtensionIsRejected() { XCTAssertNotNil(AttachmentRules.problem(name: "README", size: 10)) }
    func testEmptyIsRejected() { XCTAssertNotNil(AttachmentRules.problem(name: "a.txt", size: 0)) }
    func testOverServerLimitIsRejected() { XCTAssertNotNil(AttachmentRules.problem(name: "a.zip", size: AttachmentRules.serverMaxBytes + 1)) }
    func testOverAppLimitIsRejectedWithExplanation() {
        XCTAssertTrue(AttachmentRules.problem(name: "a.zip", size: AttachmentRules.maxBytesPerReply + 1)?.contains("per reply") == true)
    }
    func testSizeFormatting() {
        XCTAssertEqual(AttachmentRules.humanSize(512), "512 B")
        XCTAssertEqual(AttachmentRules.humanSize(2 << 20), "2.0 MB")
    }

    private let server = URL(string: "https://x.example")!
    func testSameOriginUploadPathIsAccepted() {
        XCTAssertEqual(ticketAttachmentURL(server: server, path: "/uploads/tickets/12/a.pdf")?.absoluteString, "https://x.example/uploads/tickets/12/a.pdf")
    }
    func testOtherHostSchemePathAndTraversalAreRejected() {
        XCTAssertNil(ticketAttachmentURL(server: server, path: "https://evil.example/uploads/tickets/12/a.pdf"))
        XCTAssertNil(ticketAttachmentURL(server: server, path: "javascript:alert(1)"))
        XCTAssertNil(ticketAttachmentURL(server: server, path: "/admin/login.php"))
        XCTAssertNil(ticketAttachmentURL(server: server, path: "/uploads/tickets/../../etc/passwd"))
        XCTAssertNil(ticketAttachmentURL(server: server, path: "https://u:p@x.example/uploads/tickets/1/a.pdf"))
    }

    func testMultipartBodyHasFieldsFilesAndClosingBoundary() {
        var form = MultipartForm(boundary: "B")
        form.fields = [("reply", "hi"), ("type", "note")]
        form.files = [ReplyFile(name: "a\".txt", mimeType: "text/plain", data: Data("DATA".utf8))]
        let body = String(decoding: form.body(), as: UTF8.self)
        XCTAssertTrue(body.contains("name=\"reply\"\r\n\r\nhi\r\n"))
        XCTAssertTrue(body.contains("name=\"files[]\"; filename=\"a_.txt\"\r\nContent-Type: text/plain\r\n\r\nDATA\r\n"))
        XCTAssertTrue(body.hasSuffix("--B--\r\n"))
        XCTAssertEqual(form.contentType, "multipart/form-data; boundary=B")
    }

    func testTicketDecodesAttachmentsOnTicketAndReply() throws {
        let json = """
        {"id":1,"number":1,"subject":"s","billable":false,
         "attachments":[{"name":"a.pdf","url":"/uploads/tickets/1/a.pdf"}],
         "replies":[{"id":2,"body":"b","type":"Internal","attachments":[{"name":"b.txt","url":"/uploads/tickets/1/b.txt"}]},
                    {"id":3,"body":"c","type":"reply"}]}
        """
        let t = try APIClient.decoder.decode(TicketDetail.self, from: Data(json.utf8))
        XCTAssertEqual(t.attachments.map(\.name), ["a.pdf"])
        XCTAssertEqual(t.replies[0].attachments.map(\.name), ["b.txt"])
        XCTAssertTrue(t.replies[1].attachments.isEmpty)
    }
}

final class LoginResponseTests: XCTestCase {
    private func decode(_ json: String) throws -> LoginResponse { try APIClient.decoder.decode(LoginResponse.self, from: Data(json.utf8)) }

    func testTwoFactorRequiredIsRecognised() throws {
        let r = try decode(#"{"requires_2fa":true}"#)
        XCTAssertEqual(r.requires2fa, true)
        XCTAssertNil(r.token)
    }
    func testTwoFactorFlagSurvivesOtherSpellings() throws {
        XCTAssertEqual(try decode(#"{"requires2fa":true}"#).requires2fa, true)
        XCTAssertEqual(try decode(#"{"requires2Fa":true}"#).requires2fa, true)
    }
    func testTokenAndUserStillDecode() throws {
        let r = try decode(#"{"token":"abc","user":{"id":9,"name":"Alex","email":"a@x.example","type":1}}"#)
        XCTAssertEqual(r.token, "abc")
        XCTAssertEqual(r.user?.name, "Alex")
        XCTAssertNil(r.requires2fa)
    }
}

final class QuickActionAndCredentialTests: XCTestCase {
    func testQuickActionTypesMapToScreens() {
        XCTAssertEqual(QuickAction(type: "com.foleyit.itflow.ios.newTicket")?.route, .createTicket)
        XCTAssertEqual(QuickAction(type: "com.foleyit.itflow.ios.scan")?.route, .scan)
        XCTAssertEqual(QuickAction(type: "com.foleyit.itflow.ios.myTickets")?.route, .tickets)
        XCTAssertEqual(QuickAction(type: "com.foleyit.itflow.ios.search")?.route, .search)
    }
    func testUnknownQuickActionIsIgnored() {
        XCTAssertNil(QuickAction(type: "com.evil.app.wipe"))
        XCTAssertNil(QuickAction(type: ""))
    }
    func testCredentialDeepLinks() {
        XCTAssertEqual(DeepLinks.resolve("credentials"), .credentials)
        XCTAssertEqual(DeepLinks.resolve("credentials/42"), .credential(42))
    }

    func testBase64URLDecodesWithoutPadding() {
        XCTAssertEqual(BiometricEncoding.base64URLDecode("aGVsbG8"), Data("hello".utf8))
        XCTAssertEqual(BiometricEncoding.base64URLDecode("_-8"), Data([0xFF, 0xEF]))
        XCTAssertNil(BiometricEncoding.base64URLDecode("%%%"))
    }
    func testPublicKeyPEMWrapsAnX963PointAsSPKI() throws {
        var point = Data([0x04]); point.append(Data(repeating: 0xAB, count: 64))
        let pem = try XCTUnwrap(BiometricEncoding.publicKeyPEM(x963: point))
        XCTAssertTrue(pem.hasPrefix("-----BEGIN PUBLIC KEY-----\n"))
        XCTAssertTrue(pem.hasSuffix("\n-----END PUBLIC KEY-----\n"))
        let b64 = pem.split(separator: "\n").dropFirst().dropLast().joined()
        let der = try XCTUnwrap(Data(base64Encoded: b64))
        XCTAssertEqual(der.count, 91)                         // 26-byte header + 65-byte point
        XCTAssertEqual(Array(der.prefix(4)), [0x30, 0x59, 0x30, 0x13])
        XCTAssertEqual(der.suffix(65), point)
        XCTAssertTrue(pem.split(separator: "\n").allSatisfy { $0.count <= 64 || $0.hasPrefix("-----") })
    }
    func testPublicKeyPEMRejectsOtherShapes() {
        XCTAssertNil(BiometricEncoding.publicKeyPEM(x963: Data(repeating: 4, count: 33)))
        XCTAssertNil(BiometricEncoding.publicKeyPEM(x963: Data(repeating: 1, count: 65)))
    }
    func testCredentialDetailDecodes() throws {
        let json = #"{"id":3,"name":"Router","username":"admin","password":"s3cret","uri":"https://10.0.0.1","uri2":null,"note":"n","client":"HR"}"#
        let c = try APIClient.decoder.decode(CredentialDetail.self, from: Data(json.utf8))
        XCTAssertEqual(c.password, "s3cret"); XCTAssertEqual(c.client, "HR"); XCTAssertNil(c.uri2)
    }
    func testBiometricChallengeDecodes() throws {
        let c = try APIClient.decoder.decode(BiometricChallenge.self, from: Data(#"{"challenge":"abc","challengeToken":"tok","timeout":60000}"#.utf8))
        XCTAssertEqual(c.challenge, "abc"); XCTAssertEqual(c.challengeToken, "tok")
    }
}

final class NotificationAlertPlanTests: XCTestCase {
    private func n(_ id: Int, action: String? = nil, message: String = "m") -> AppNotification {
        let a = action.map { "\"\($0)\"" } ?? "null"
        let json = #"{"id":\#(id),"type":"ticket","message":"\#(message)","action":\#(a),"timestamp":null}"#
        return try! APIClient.decoder.decode(AppNotification.self, from: Data(json.utf8))
    }

    func testFirstRunOnlyRecordsABaseline() {
        let r = NotificationAlertPlan.plan(items: [n(3), n(9), n(5)], lastSeenID: nil)
        XCTAssertTrue(r.toAlert.isEmpty); XCTAssertEqual(r.overflow, 0); XCTAssertEqual(r.lastSeenID, 9)
    }
    func testFirstRunWithNothingBaselinesAtZeroSoTheNextOneAlerts() {
        XCTAssertEqual(NotificationAlertPlan.plan(items: [], lastSeenID: nil).lastSeenID, 0)
        XCTAssertEqual(NotificationAlertPlan.plan(items: [n(1)], lastSeenID: 0).toAlert.map(\.id), [1])
    }
    func testOnlyNewerThanLastSeenAlertOldestFirst() {
        let r = NotificationAlertPlan.plan(items: [n(12), n(10), n(11), n(7)], lastSeenID: 10)
        XCTAssertEqual(r.toAlert.map(\.id), [11, 12]); XCTAssertEqual(r.lastSeenID, 12)
    }
    func testNothingNewKeepsTheMarkAndNeverGoesBackwards() {
        let r = NotificationAlertPlan.plan(items: [n(4)], lastSeenID: 10)
        XCTAssertTrue(r.toAlert.isEmpty); XCTAssertEqual(r.lastSeenID, 10)
        XCTAssertEqual(NotificationAlertPlan.plan(items: [], lastSeenID: 10).lastSeenID, 10)
    }
    func testAFloodIsCappedToTheNewestAndCountsTheRest() {
        let r = NotificationAlertPlan.plan(items: (1...9).map { n($0) }, lastSeenID: 0, maxAlerts: 4)
        XCTAssertEqual(r.toAlert.map(\.id), [6, 7, 8, 9]); XCTAssertEqual(r.overflow, 5); XCTAssertEqual(r.lastSeenID, 9)
    }
    func testBodyCollapsesWhitespaceCapsLengthAndNeverEmpty() {
        XCTAssertEqual(NotificationAlertPlan.body("  Ticket \n #5   updated "), "Ticket #5 updated")
        XCTAssertEqual(NotificationAlertPlan.body(String(repeating: "a", count: 300)).count, 160)
        XCTAssertTrue(NotificationAlertPlan.body(String(repeating: "a", count: 300)).hasSuffix("…"))
        XCTAssertEqual(NotificationAlertPlan.body("   "), "You have a new notification")
    }
    func testRouteFollowsOnlyRealScreensElseTheList() {
        XCTAssertEqual(NotificationAlertPlan.route(for: n(1, action: "tickets/157")), "tickets/157")
        XCTAssertEqual(NotificationAlertPlan.route(for: n(1, action: "worksheets/5")), "notifications")   // allowed but no screen
        XCTAssertEqual(NotificationAlertPlan.route(for: n(1, action: "https://evil.example/x")), "notifications")
        XCTAssertEqual(NotificationAlertPlan.route(for: n(1, action: nil)), "notifications")
    }
}
