import Foundation

/// What registering the credential-reveal key answered, mapped to what the password prompt should do.
public enum EnrolFailure: Equatable {
    /// Wrong or missing password: stay on the prompt.
    case wrongPassword(String)
    /// The account has MFA and no valid code was sent: show the code field.
    case needsCode(String)
    case rateLimited(String)
    /// Anything else: use the normal error UI.
    case other(String)
}

public func classifyEnrolFailure(_ error: Error) -> EnrolFailure {
    switch error as? RivetError {
    case .some(.rateLimited(let retryAfter)):
        return .rateLimited(rateLimitMessage(retryAfter: retryAfter))
    case .some(.http(401, let message)):
        let m = (message ?? "").lowercased()
        if m.hasPrefix("mfa code required") { return .needsCode("Enter your authenticator code or a recovery code.") }
        if m.hasPrefix("current password") { return .wrongPassword("That password is not correct.") }
        return .other(userMessage(for: error))
    default:
        return .other(userMessage(for: error))
    }
}

/// True when a reveal was refused because the server has no usable step-up key (never enrolled, or the device key
/// was recreated), so enrolling again can fix it.
public func revealNeedsKeyEnrolment(_ error: Error) -> Bool {
    guard case .some(.http(403, let message)) = error as? RivetError, let m = message?.lowercased() else { return false }
    return m.hasPrefix("no biometric key") || m.hasPrefix("biometric verification failed") || m.hasPrefix("invalid registered biometric key")
}
