import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

public extension RivetError {
    /// URLSession reports an untrusted/invalid server certificate with one of these codes; the setup flow reacts by
    /// probing the certificate and asking the user to confirm its fingerprint, as the Android app does.
    static func isCertificateProblem(_ error: Error) -> Bool {
        guard let e = error as? URLError else { return false }
        switch e.code {
        case .serverCertificateUntrusted, .serverCertificateHasUnknownRoot, .serverCertificateHasBadDate,
             .serverCertificateNotYetValid, .secureConnectionFailed, .clientCertificateRejected:
            return true
        default:
            return false
        }
    }
}

#if canImport(Security)
import Security

/// URLSession transport that trusts the system store and, additionally, ONE user-confirmed certificate identified by
/// its SHA-256 fingerprint (the iOS equivalent of the Android `FingerprintTrustManager`).
public final class PinnedTransport: NSObject, HTTPTransport, URLSessionDelegate, @unchecked Sendable {
    private let pinned: String?
    private lazy var session: URLSession = {
        let cfg = URLSessionConfiguration.ephemeral
        cfg.requestCachePolicy = .reloadIgnoringLocalCacheData
        cfg.urlCache = nil
        cfg.timeoutIntervalForRequest = 30
        return URLSession(configuration: cfg, delegate: self, delegateQueue: nil)
    }()

    public init(pinnedFingerprint: String?) { self.pinned = pinnedFingerprint?.uppercased() }

    public func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw RivetError.network("No HTTP response") }
        return (data, http)
    }

    public func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge,
                           completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        guard challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
              let trust = challenge.protectionSpace.serverTrust else {
            completionHandler(.performDefaultHandling, nil); return
        }
        if SecTrustEvaluateWithError(trust, nil) { completionHandler(.useCredential, URLCredential(trust: trust)); return }
        if let pinned, let leaf = PinnedTransport.leafDER(trust),
           CertificateFingerprint.sha256(der: leaf) == pinned {
            completionHandler(.useCredential, URLCredential(trust: trust)); return
        }
        completionHandler(.cancelAuthenticationChallenge, nil)
    }

    static func leafDER(_ trust: SecTrust) -> Data? {
        guard let chain = SecTrustCopyCertificateChain(trust) as? [SecCertificate], let leaf = chain.first else { return nil }
        return SecCertificateCopyData(leaf) as Data
    }
}

/// Connects once, trusting anything, only to read the server's certificate so the user can verify its fingerprint.
/// It never sends credentials and the response is discarded.
public final class CertificateProbe: NSObject, URLSessionDelegate, @unchecked Sendable {
    private var captured: Data?
    public override init() { super.init() }

    public func fetch(serverURL: URL) async -> CertificateSummary? {
        let session = URLSession(configuration: .ephemeral, delegate: self, delegateQueue: nil)
        var req = URLRequest(url: serverURL.appendingPathComponent("api/v1/auth"), timeoutInterval: 10)
        req.httpMethod = "GET"
        _ = try? await session.data(for: req)
        session.invalidateAndCancel()
        return captured.map { CertificateSummary(der: $0) }
    }

    public func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge,
                           completionHandler: @escaping (URLSession.AuthChallengeDisposition, URLCredential?) -> Void) {
        if let trust = challenge.protectionSpace.serverTrust { captured = PinnedTransport.leafDER(trust) }
        completionHandler(.cancelAuthenticationChallenge, nil)   // never complete the handshake: inspection only
    }
}
#else

/// Linux build (used for tests in Docker): plain URLSession, no certificate pinning.
public final class PinnedTransport: HTTPTransport, @unchecked Sendable {
    private let session = URLSession(configuration: .ephemeral)
    public init(pinnedFingerprint: String?) {}
    public func send(_ request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        // swift-corelibs-foundation has no async data(for:), so bridge the completion-handler API.
        try await withCheckedThrowingContinuation { cont in
            session.dataTask(with: request) { data, response, error in
                if let error { cont.resume(throwing: error); return }
                guard let http = response as? HTTPURLResponse else { cont.resume(throwing: RivetError.network("No HTTP response")); return }
                cont.resume(returning: (data ?? Data(), http))
            }.resume()
        }
    }
}
#endif
