import Foundation
import Security
import LocalAuthentication
import RivetCore

enum BiometricSignerError: LocalizedError {
    case cancelled, keyUnavailable, noPublicKey, badChallenge, signingFailed(String)

    var errorDescription: String? {
        switch self {
        case .cancelled: return "Verification was cancelled."
        case .keyUnavailable: return "The signing key is not available. Make sure a passcode is set on this device."
        case .noPublicKey: return "Could not read this device's signing key."
        case .badChallenge: return "The server sent a challenge the app could not read."
        case .signingFailed(let detail): return "Could not verify your identity: \(detail)"
        }
    }
}

/// A P-256 key kept in the Secure Enclave that only signs after Face ID, Touch ID or the passcode. Signing the server's
/// single-use challenge with it is the proof the server checks before releasing a credential's secrets (the same scheme
/// as the Android app's Keystore key).
enum BiometricSigner {
    struct Assertion { let signatureBase64: String; let publicKeyPEM: String }

    private static let tag = Data("com.foleyit.itflow.ios.credential-signing-key".utf8)

    /// Blocks while the system prompt is on screen, so call it off the main actor.
    static func sign(challenge: Data, reason: String) throws -> Assertion {
        let context = LAContext()
        context.localizedReason = reason
        let key = try existingKey(context: context) ?? createKey()
        guard let publicKey = SecKeyCopyPublicKey(key),
              let x963 = SecKeyCopyExternalRepresentation(publicKey, nil) as Data?,
              let pem = BiometricEncoding.publicKeyPEM(x963: x963) else { throw BiometricSignerError.noPublicKey }
        var error: Unmanaged<CFError>?
        // DER-encoded ECDSA over SHA-256 of the raw challenge bytes: what PHP's openssl_verify(..., SHA256) checks.
        guard let signature = SecKeyCreateSignature(key, .ecdsaSignatureMessageX962SHA256, challenge as CFData, &error) as Data? else {
            let failure: NSError? = error.map { $0.takeRetainedValue() as Error as NSError }
            if let failure, failure.code == Int(errSecUserCanceled) ||
                (failure.domain == LAError.errorDomain && failure.code == LAError.userCancel.rawValue) {
                throw BiometricSignerError.cancelled
            }
            throw BiometricSignerError.signingFailed(failure?.localizedDescription ?? "unknown error")
        }
        return Assertion(signatureBase64: signature.base64EncodedString(), publicKeyPEM: pem)
    }

    private static func existingKey(context: LAContext) throws -> SecKey? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassKey,
            kSecAttrApplicationTag as String: tag,
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecReturnRef as String: true,
            kSecUseAuthenticationContext as String: context,
        ]
        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        if status == errSecSuccess, let item { return (item as! SecKey) }
        if status == errSecItemNotFound { return nil }
        throw BiometricSignerError.keyUnavailable
    }

    private static func createKey() throws -> SecKey {
        var cfError: Unmanaged<CFError>?
        #if targetEnvironment(simulator)
        let flags: SecAccessControlCreateFlags = [.userPresence]                    // the Simulator has no Secure Enclave
        #else
        let flags: SecAccessControlCreateFlags = [.privateKeyUsage, .userPresence]
        #endif
        guard let access = SecAccessControlCreateWithFlags(nil, kSecAttrAccessibleWhenPasscodeSetThisDeviceOnly, flags, &cfError) else {
            throw BiometricSignerError.keyUnavailable
        }
        var attributes: [String: Any] = [
            kSecAttrKeyType as String: kSecAttrKeyTypeECSECPrimeRandom,
            kSecAttrKeySizeInBits as String: 256,
            kSecPrivateKeyAttrs as String: [
                kSecAttrIsPermanent as String: true,
                kSecAttrApplicationTag as String: tag,
                kSecAttrAccessControl as String: access,
            ],
        ]
        #if !targetEnvironment(simulator)
        attributes[kSecAttrTokenID as String] = kSecAttrTokenIDSecureEnclave
        #endif
        guard let key = SecKeyCreateRandomKey(attributes as CFDictionary, &cfError) else { throw BiometricSignerError.keyUnavailable }
        return key
    }
}
