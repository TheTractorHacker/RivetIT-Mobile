import Foundation

/// A file attached to a ticket or reply. `url` is a server path such as `/uploads/tickets/12/x.pdf`.
public struct TicketAttachment: Decodable, Hashable, Identifiable {
    public let name: String
    public let url: String
    public var id: String { url }
}

/// The server's answer to a reply sent with files: `attachments` lists only the files it actually stored.
public struct ReplyWithFilesResult: Decodable {
    public let id: Int?
    public let attachments: [TicketAttachment]?
}

/// One file ready to upload (already read into memory).
public struct ReplyFile: Hashable {
    public let name: String
    public let mimeType: String
    public let data: Data
    public init(name: String, mimeType: String, data: Data) { self.name = name; self.mimeType = mimeType; self.data = data }
}

/// Mirrors the server's ticket upload rules (api/v1/tickets.php `api_save_ticket_attachments` + `checkFileUpload`).
public enum AttachmentRules {
    public static let maxFiles = 10
    /// The server accepts up to 500 MB per file, but this app builds the upload in memory, so a reply is capped lower.
    public static let maxBytesPerReply = 25 * 1024 * 1024
    public static let serverMaxBytes = 500 * 1024 * 1024
    public static let allowedExtensions: Set<String> = [
        "jpg", "jpeg", "gif", "png", "webp", "pdf", "txt", "md", "doc", "docx", "odt", "csv", "xls", "xlsx", "ods",
        "pptx", "odp", "zip", "tar", "gz", "xml", "msg", "json", "wav", "mp3", "ogg", "mov", "mp4", "av1", "ovpn",
    ]

    /// A user-facing reason the file cannot be sent, or nil when it passes.
    public static func problem(name: String, size: Int) -> String? {
        let ext = (name as NSString).pathExtension.lowercased()
        if ext.isEmpty || !allowedExtensions.contains(ext) {
            return "\"\(name)\" is not an allowed file type" + (ext.isEmpty ? "" : " (.\(ext))")
        }
        if size <= 0 { return "\"\(name)\" is empty" }
        if size > serverMaxBytes { return "\"\(name)\" is larger than 500 MB" }
        if size > maxBytesPerReply { return "\"\(name)\" is larger than \(humanSize(maxBytesPerReply)), the most this app can upload per reply" }
        return nil
    }

    public static func humanSize(_ bytes: Int) -> String {
        if bytes >= 1 << 30 { return String(format: "%.1f GB", Double(bytes) / Double(1 << 30)) }
        if bytes >= 1 << 20 { return String(format: "%.1f MB", Double(bytes) / Double(1 << 20)) }
        if bytes >= 1 << 10 { return String(format: "%.0f KB", Double(bytes) / Double(1 << 10)) }
        return "\(bytes) B"
    }
}

/// Attachment links come from the server as paths. Only a same-origin http(s) address under /uploads/tickets/ is ever
/// opened; any other host, scheme, userinfo or path (or a `..` segment) returns nil.
public func ticketAttachmentURL(server: URL, path: String?) -> URL? {
    guard let path, !path.isEmpty, let link = URL(string: path, relativeTo: server)?.absoluteURL else { return nil }
    guard link.scheme?.lowercased() == server.scheme?.lowercased(), link.host?.lowercased() == server.host?.lowercased(),
          link.port == server.port, link.user == nil, link.password == nil,
          link.path.hasPrefix("/uploads/tickets/"), !link.pathComponents.contains("..") else { return nil }
    return link
}

/// A `multipart/form-data` body: text fields first, then files under the `files[]` field the server expects.
public struct MultipartForm {
    public let boundary: String
    public var fields: [(String, String)] = []
    public var files: [ReplyFile] = []
    public init(boundary: String = "RivetIT-" + UUID().uuidString) { self.boundary = boundary }

    public var contentType: String { "multipart/form-data; boundary=\(boundary)" }

    public func body() -> Data {
        var out = Data()
        func add(_ s: String) { out.append(Data(s.utf8)) }
        for (name, value) in fields {
            add("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(name)\"\r\n\r\n\(value)\r\n")
        }
        for file in files {
            let safe = file.name.replacingOccurrences(of: "\"", with: "_").replacingOccurrences(of: "\r", with: "_")
                .replacingOccurrences(of: "\n", with: "_")
            add("--\(boundary)\r\nContent-Disposition: form-data; name=\"files[]\"; filename=\"\(safe)\"\r\nContent-Type: \(file.mimeType)\r\n\r\n")
            out.append(file.data)
            add("\r\n")
        }
        add("--\(boundary)--\r\n")
        return out
    }
}
