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
