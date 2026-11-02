import Foundation

/// Encodings for the biometric step-up that reveals credentials. They mirror what the server verifies in
/// api/v1/credentials.php: a base64url challenge, a standard-base64 DER ECDSA signature, and a PEM public key.
public enum BiometricEncoding {
    /// Decodes base64url (no padding needed) to raw bytes; nil when the text is not valid base64url.
    public static func base64URLDecode(_ text: String) -> Data? {
        var s = text.replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        s += String(repeating: "=", count: (4 - s.count % 4) % 4)
        return Data(base64Encoded: s)
    }

    /// DER prefix of a P-256 SubjectPublicKeyInfo; followed by the 65-byte X9.63 point (04 || X || Y).
    private static let p256SPKIHeader: [UInt8] = [
        0x30, 0x59, 0x30, 0x13, 0x06, 0x07, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x02, 0x01,
        0x06, 0x08, 0x2A, 0x86, 0x48, 0xCE, 0x3D, 0x03, 0x01, 0x07, 0x03, 0x42, 0x00,
    ]

    /// Wraps an uncompressed P-256 public key (what `SecKeyCopyExternalRepresentation` returns) as a PEM
    /// "PUBLIC KEY", the format PHP's `openssl_pkey_get_public` reads. Nil when the key is not a 65-byte point.
    public static func publicKeyPEM(x963: Data) -> String? {
        guard x963.count == 65, x963.first == 0x04 else { return nil }
        let der = Data(p256SPKIHeader) + x963
        let body = der.base64EncodedString()
        var lines: [String] = []
        var index = body.startIndex
        while index < body.endIndex {
            let next = body.index(index, offsetBy: 64, limitedBy: body.endIndex) ?? body.endIndex
            lines.append(String(body[index..<next]))
            index = next
        }
        return "-----BEGIN PUBLIC KEY-----\n" + lines.joined(separator: "\n") + "\n-----END PUBLIC KEY-----\n"
    }
}
