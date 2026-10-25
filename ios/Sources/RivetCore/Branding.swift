import Foundation
#if canImport(FoundationNetworking)
import FoundationNetworking
#endif

/// The company identity a server publishes without authentication (`GET /api/v1/branding`): the same name and
/// uploaded logo its public login page shows. Never contains credentials or settings.
public struct CompanyBranding: Decodable, Equatable, Sendable {
    public var name: String
    public var logoPath: String?
    public init(name: String = "RivetIT", logoPath: String? = nil) {
        self.name = name
        self.logoPath = logoPath
    }
}

public enum CompanyLogo {
    public static let maxBytes = 5 * 1024 * 1024

    /// The logo's absolute URL, or nil unless it is on the same origin as the server and inside the settings
    /// upload folder. Branding requests carry no credentials, so they must never be steered to another host or to
    /// an API path.
    public static func url(server: URL, path: String?) -> URL? {
        guard let path, !path.isEmpty,
              let logo = URL(string: path, relativeTo: server)?.absoluteURL.standardized else { return nil }
        func port(_ u: URL) -> Int { u.port ?? (u.scheme?.lowercased() == "http" ? 80 : 443) }
        guard logo.scheme?.lowercased() == server.scheme?.lowercased(),
              logo.host?.lowercased() == server.host?.lowercased(),
              port(logo) == port(server),
              logo.user == nil, logo.password == nil,
              logo.path.hasPrefix("/uploads/settings/"), !logo.path.contains("..") else { return nil }
        return logo
    }
}

public extension APIClient {
    /// Public company identity of the server this client points at. Works without a token.
    func branding() async throws -> CompanyBranding { try await get("branding") }

    /// The uploaded logo's bytes, or nil if there is none, it is off-origin, too large, or the request fails.
    func companyLogoData(path: String?, server: URL) async -> Data? {
        guard let url = CompanyLogo.url(server: server, path: path) else { return nil }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        guard let (data, response) = try? await transport.send(request),
              (200..<300).contains(response.statusCode), data.count <= CompanyLogo.maxBytes else { return nil }
        return data
    }
}
