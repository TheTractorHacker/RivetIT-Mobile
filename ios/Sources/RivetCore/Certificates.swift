import Foundation
import Crypto

/// SHA-256 fingerprint in the same "AA:BB:CC:…" form the Android app shows and stores.
public enum CertificateFingerprint {
    public static func sha256(der: Data) -> String {
        SHA256.hash(data: der).map { String(format: "%02X", $0) }.joined(separator: ":")
    }
}

/// The details the "Untrusted Certificate" dialog shows. `SecCertificate` exposes no expiry on iOS, so the DER is read directly.
public struct CertificateSummary: Equatable {
    public let fingerprint: String
    public let commonName: String?
    public let notAfter: Date?

    public init(der: Data) {
        fingerprint = CertificateFingerprint.sha256(der: der)
        let parsed = X509.parse(Array(der))
        commonName = parsed?.commonName
        notAfter = parsed?.notAfter
    }
}

extension CertificateSummary: Identifiable { public var id: String { fingerprint } }

/// Minimal DER reader: just enough X.509 to pull the subject common name and the notAfter date.
enum X509 {
    struct Parsed { var commonName: String?; var notAfter: Date? }

    private struct TLV { let tag: UInt8; let start: Int; let end: Int }

    private static func read(_ d: [UInt8], _ at: Int, limit: Int) -> TLV? {
        guard at + 2 <= limit else { return nil }
        let tag = d[at]
        var len = Int(d[at + 1]); var p = at + 2
        if len & 0x80 != 0 {
            let n = len & 0x7F
            guard n > 0, n <= 4, p + n <= limit else { return nil }
            len = 0
            for _ in 0..<n { len = (len << 8) | Int(d[p]); p += 1 }
        }
        guard p + len <= limit else { return nil }
        return TLV(tag: tag, start: p, end: p + len)
    }

    private static func children(_ d: [UInt8], _ t: TLV) -> [TLV] {
        var out: [TLV] = []; var p = t.start
        while p < t.end, let c = read(d, p, limit: t.end) { out.append(c); p = c.end }
        return out
    }

    static func parse(_ d: [UInt8]) -> Parsed? {
        guard let cert = read(d, 0, limit: d.count), cert.tag == 0x30,
              let tbs = children(d, cert).first, tbs.tag == 0x30 else { return nil }
        var kids = children(d, tbs)
        if let first = kids.first, first.tag == 0xA0 { kids.removeFirst() }   // [0] version
        // serial, signature algorithm, issuer, validity, subject
        guard kids.count >= 5 else { return nil }
        let validity = children(d, kids[3]); let subject = kids[4]
        var result = Parsed()
        if validity.count == 2 { result.notAfter = time(d, validity[1]) }
        result.commonName = commonName(d, subject)
        return result
    }

    private static func time(_ d: [UInt8], _ t: TLV) -> Date? {
        guard let s = String(bytes: d[t.start..<t.end], encoding: .ascii) else { return nil }
        let f = DateFormatter()
        f.locale = Locale(identifier: "en_US_POSIX"); f.timeZone = TimeZone(identifier: "UTC")
        if t.tag == 0x17 { f.dateFormat = "yyMMddHHmmss'Z'" }       // UTCTime
        else if t.tag == 0x18 { f.dateFormat = "yyyyMMddHHmmss'Z'" } // GeneralizedTime
        else { return nil }
        return f.date(from: s)
    }

    private static func commonName(_ d: [UInt8], _ name: TLV) -> String? {
        for rdn in children(d, name) {                 // SET
            for atv in children(d, rdn) {              // SEQUENCE { OID, value }
                let parts = children(d, atv)
                guard parts.count == 2, parts[0].tag == 0x06,
                      Array(d[parts[0].start..<parts[0].end]) == [0x55, 0x04, 0x03] else { continue }
                return String(bytes: d[parts[1].start..<parts[1].end], encoding: .utf8)
            }
        }
        return nil
    }
}

public enum PEM {
    /// Strips the armour from a PEM certificate and returns the DER bytes.
    public static func der(from pem: String) -> Data? {
        let body = pem.split(separator: "\n").filter { !$0.hasPrefix("-----") }.joined()
        return Data(base64Encoded: body)
    }
}
