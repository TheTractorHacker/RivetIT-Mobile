import Foundation

// Wire models. They mirror the Android app's ApiModels.kt field for field. The JSON decoder uses
// `.convertFromSnakeCase`, so `status_color` arrives as `statusColor`, `ticket_id` as `ticketId`, and so on.

// MARK: Auth & profile

public struct LoginRequest: Encodable {
    public var username: String
    public var password: String
    public var deviceName: String
    public var totpCode: String?
    public init(username: String, password: String, deviceName: String, totpCode: String? = nil) {
        self.username = username; self.password = password; self.deviceName = deviceName; self.totpCode = totpCode
    }
}

public struct UserInfo: Decodable, Hashable {
    public let id: Int
    public let name: String
    public let email: String
    public let type: Int
}

public struct LoginResponse: Decodable {
    public let token: String?
    public let user: UserInfo?
    public let requires2fa: Bool?
}

public struct ModuleFlags: Decodable, Hashable {
    public let accountingEnabled: Bool
    public let ticketChargesEnabled: Bool
    public init(accountingEnabled: Bool = false, ticketChargesEnabled: Bool = false) {
        self.accountingEnabled = accountingEnabled; self.ticketChargesEnabled = ticketChargesEnabled
    }
    enum CodingKeys: String, CodingKey { case accountingEnabled, ticketChargesEnabled }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        accountingEnabled = try c.decodeIfPresent(Bool.self, forKey: .accountingEnabled) ?? false
        ticketChargesEnabled = try c.decodeIfPresent(Bool.self, forKey: .ticketChargesEnabled) ?? false
    }
}

public struct UserProfile: Decodable, Hashable {
    public let id: Int
    public let name: String
    public let email: String
    public let type: Int
    public let color: String?
    public let avatar: String?
    public let modules: ModuleFlags?
    public let isAdmin: Bool
    /// Module name -> level (0 none, 1 read, 2 write, 3 full). Nil on servers that do not report it.
    public let permissions: [String: Int]?

    enum CodingKeys: String, CodingKey { case id, name, email, type, color, avatar, modules, isAdmin, permissions }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        email = try c.decode(String.self, forKey: .email)
        type = try c.decodeIfPresent(Int.self, forKey: .type) ?? 1
        color = try c.decodeIfPresent(String.self, forKey: .color)
        avatar = try c.decodeIfPresent(String.self, forKey: .avatar)
        modules = try c.decodeIfPresent(ModuleFlags.self, forKey: .modules)
        isAdmin = try c.decodeIfPresent(Bool.self, forKey: .isAdmin) ?? false
        permissions = try c.decodeIfPresent([String: Int].self, forKey: .permissions)
    }
}

public struct UpdateProfileRequest: Encodable {
    public var name: String
    public var email: String
    public var currentPassword: String
    public var newPassword: String
    public init(name: String, email: String, currentPassword: String = "", newPassword: String = "") {
        self.name = name; self.email = email; self.currentPassword = currentPassword; self.newPassword = newPassword
    }
}

// MARK: Paging

public struct Paged<T: Decodable>: Decodable {
    public let data: [T]
    public let total: Int
}

// MARK: Tickets

public struct TicketSummary: Decodable, Identifiable, Hashable {
    public let id: Int
    public let number: Int
    public let subject: String
    public let priority: String?
    public let status: String?
    public let statusColor: String?
    public let client: String?
    public let assignedTo: String?
    public let createdAt: String?
    public let dueAt: String?
    public let resolvedAt: String?
}

public struct DashboardResponse: Decodable {
    public let myOpen: Int
    public let allOpen: Int
    public let overdue: Int
    public let unread: Int
    public let dueToday: Int?
    public let onsiteOpen: Int?
    public let queue: [TicketSummary]
}

public struct TicketReply: Decodable, Identifiable, Hashable {
    public let id: Int
    public let body: String
    public let type: String
    public let timeWorked: String?
    public let onsite: Bool?
    public let by: String?
    public let createdAt: String?

    enum CodingKeys: String, CodingKey { case id, body, type, timeWorked, onsite, by, createdAt }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        body = try c.decodeIfPresent(String.self, forKey: .body) ?? ""
        type = try c.decodeIfPresent(String.self, forKey: .type) ?? ""
        timeWorked = try c.decodeIfPresent(String.self, forKey: .timeWorked)
        onsite = LossyBool.decode(c, .onsite)
        by = try c.decodeIfPresent(String.self, forKey: .by)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
    }
}

public struct TicketDetail: Decodable, Hashable {
    public let id: Int
    public let number: Int
    public let subject: String
    public let details: String?
    public let priority: String?
    public let status: String?
    public let statusColor: String?
    public let client: String?
    public let assignedTo: String?
    public let contactName: String?
    public let contactEmail: String?
    public let contactPhone: String?
    public let billable: Bool
    public let createdAt: String?
    public let dueAt: String?
    public let resolvedAt: String?
    public let replies: [TicketReply]

    enum CodingKeys: String, CodingKey {
        case id, number, subject, details, priority, status, statusColor, client, assignedTo, contactName,
             contactEmail, contactPhone, billable, createdAt, dueAt, resolvedAt, replies
    }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        number = try c.decode(Int.self, forKey: .number)
        subject = try c.decode(String.self, forKey: .subject)
        details = try c.decodeIfPresent(String.self, forKey: .details)
        priority = try c.decodeIfPresent(String.self, forKey: .priority)
        status = try c.decodeIfPresent(String.self, forKey: .status)
        statusColor = try c.decodeIfPresent(String.self, forKey: .statusColor)
        client = try c.decodeIfPresent(String.self, forKey: .client)
        assignedTo = try c.decodeIfPresent(String.self, forKey: .assignedTo)
        contactName = try c.decodeIfPresent(String.self, forKey: .contactName)
        contactEmail = try c.decodeIfPresent(String.self, forKey: .contactEmail)
        contactPhone = try c.decodeIfPresent(String.self, forKey: .contactPhone)
        billable = LossyBool.decode(c, .billable) ?? false
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        dueAt = try c.decodeIfPresent(String.self, forKey: .dueAt)
        resolvedAt = try c.decodeIfPresent(String.self, forKey: .resolvedAt)
        replies = try c.decodeIfPresent([TicketReply].self, forKey: .replies) ?? []
    }
}

public struct AddReplyRequest: Encodable {
    public var reply: String
    public var type: String          // "reply" (public) | "note" (internal)
    public var timeWorked: String?   // "HH:MM:SS"
    public var onsite: Int
    public var statusId: Int?
    public init(reply: String, type: String, timeWorked: String?, onsite: Int, statusId: Int?) {
        self.reply = reply; self.type = type; self.timeWorked = timeWorked; self.onsite = onsite; self.statusId = statusId
    }
}

public struct TicketStatus: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let color: String
}

public struct TicketCategory: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let color: String?
}

public struct SavedTicketView: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let icon: String?
    public let params: [String: String]
}

public struct CreateTicketRequest: Encodable {
    public var subject: String
    public var details: String
    public var clientId: Int?
    public var priority: String
    public var assignedTo: Int?
    public var categoryId: Int?
    public init(subject: String, details: String = "", clientId: Int? = nil, priority: String = "low",
                assignedTo: Int? = nil, categoryId: Int? = nil) {
        self.subject = subject; self.details = details; self.clientId = clientId; self.priority = priority
        self.assignedTo = assignedTo; self.categoryId = categoryId
    }
}

public struct IdResponse: Decodable { public let id: Int? }

public struct ChatMessage: Decodable, Identifiable, Hashable {
    public let id: Int
    public let senderType: String
    public let senderId: Int
    public let senderName: String?
    public let message: String
    public let createdAt: String?
}

public struct ChatMessagesResponse: Decodable { public let data: [ChatMessage] }
public struct SendChatMessageRequest: Encodable { public var message: String; public init(message: String) { self.message = message } }

// MARK: Worksheets & outtake forms (list payloads only)

public struct WorksheetSummary: Decodable, Identifiable, Hashable {
    public let id: Int
    public let templateName: String?
    public let createdBy: String?
    public let createdAt: String?
    public let completedAt: String?
    public let signedName: String?
    public let signedAt: String?
    public let isOuttake: Bool
    public let signed: Bool
    enum CodingKeys: String, CodingKey { case id, templateName, createdBy, createdAt, completedAt, signedName, signedAt, isOuttake, signed }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        templateName = try c.decodeIfPresent(String.self, forKey: .templateName)
        createdBy = try c.decodeIfPresent(String.self, forKey: .createdBy)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        completedAt = try c.decodeIfPresent(String.self, forKey: .completedAt)
        signedName = try c.decodeIfPresent(String.self, forKey: .signedName)
        signedAt = try c.decodeIfPresent(String.self, forKey: .signedAt)
        isOuttake = LossyBool.decode(c, .isOuttake) ?? false
        signed = LossyBool.decode(c, .signed) ?? false
    }
}

public struct OuttakeSummary: Decodable, Identifiable, Hashable {
    public let id: Int
    public let notes: String?
    public let createdBy: String?
    public let createdAt: String?
    public let signedName: String?
    public let signedAt: String?
    public let signed: Bool
    enum CodingKeys: String, CodingKey { case id, notes, createdBy, createdAt, signedName, signedAt, signed }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        notes = try c.decodeIfPresent(String.self, forKey: .notes)
        createdBy = try c.decodeIfPresent(String.self, forKey: .createdBy)
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        signedName = try c.decodeIfPresent(String.self, forKey: .signedName)
        signedAt = try c.decodeIfPresent(String.self, forKey: .signedAt)
        signed = LossyBool.decode(c, .signed) ?? false
    }
}

// MARK: Departments (API name: clients)

public struct Contact: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let title: String?
    public let email: String?
    public let phone: String?
    public let `extension`: String?
    public let client: String?
    public let clientId: Int?
}

public struct ClientSummary: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let phone: String?
    public let city: String?
    public let state: String?
    public let website: String?
}

public struct ClientDetail: Decodable, Hashable {
    public let id: Int
    public let name: String
    public let phone: String?
    public let address: String?
    public let city: String?
    public let state: String?
    public let zip: String?
    public let website: String?
    public let notes: String?
    public let openTickets: Int
    public let contacts: [Contact]

    enum CodingKeys: String, CodingKey { case id, name, phone, address, city, state, zip, website, notes, openTickets, contacts }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        phone = try c.decodeIfPresent(String.self, forKey: .phone)
        address = try c.decodeIfPresent(String.self, forKey: .address)
        city = try c.decodeIfPresent(String.self, forKey: .city)
        state = try c.decodeIfPresent(String.self, forKey: .state)
        zip = try c.decodeIfPresent(String.self, forKey: .zip)
        website = try c.decodeIfPresent(String.self, forKey: .website)
        notes = try c.decodeIfPresent(String.self, forKey: .notes)
        openTickets = try c.decodeIfPresent(Int.self, forKey: .openTickets) ?? 0
        contacts = try c.decodeIfPresent([Contact].self, forKey: .contacts) ?? []
    }
}

public struct ClientLocation: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String?
    public let address: String?
    public let city: String?
    public let state: String?
    public let zip: String?
    public let phone: String?
    public let primary: Bool
    enum CodingKeys: String, CodingKey { case id, name, address, city, state, zip, phone, primary }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        name = try c.decodeIfPresent(String.self, forKey: .name)
        address = try c.decodeIfPresent(String.self, forKey: .address)
        city = try c.decodeIfPresent(String.self, forKey: .city)
        state = try c.decodeIfPresent(String.self, forKey: .state)
        zip = try c.decodeIfPresent(String.self, forKey: .zip)
        phone = try c.decodeIfPresent(String.self, forKey: .phone)
        primary = LossyBool.decode(c, .primary) ?? false
    }
}

public struct ClientContract: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String?
    public let status: String?
    public let type: String?
}

// MARK: Assets

public struct AssetSummary: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let tag: String?
    public let type: String?
    public let make: String?
    public let model: String?
    public let serial: String?
    public let os: String?
    public let status: String?
    public let client: String?
}

public struct AssetDetail: Decodable, Hashable {
    public let id: Int
    public let name: String
    public let tag: String?
    public let type: String?
    public let make: String?
    public let model: String?
    public let serial: String?
    public let os: String?
    public let status: String?
    public let description: String?
    public let physicalLocation: String?
    public let locationName: String?
    public let locationCity: String?
    public let locationState: String?
    public let contactName: String?
    public let contactPhone: String?
    public let client: String?
    public let createdAt: String?
    public let purchaseDate: String?
    public let warrantyExpire: String?
    public let notes: String?
}

// MARK: Projects

public struct ProjectSummary: Decodable, Identifiable, Hashable {
    public let id: Int
    public let prefix: String?
    public let number: Int
    public let name: String
    public let dueAt: String?
    public let createdAt: String?
    public let completedAt: String?
    public let archivedAt: String?
    public let client: String?
    public let manager: String?
    public let ticketCount: Int
    public let ticketClosedCount: Int
    public let taskCount: Int
    public let taskCompletedCount: Int
}

public struct ProjectMilestone: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let description: String?
    public let dueAt: String?
    public let order: Int
    public let status: String?
    public let completedAt: String?
}

public struct ProjectTask: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let status: String?
    public let progress: Int
    public let dueAt: String?
    public let startAt: String?
    public let milestoneId: Int?
    public let completedAt: String?
    public let assignedTo: String?
    public let ticketNumber: String?
}

public struct ProjectTicket: Decodable, Identifiable, Hashable {
    public let id: Int
    public let number: String
    public let subject: String
    public let status: String?
    public let statusColor: String?
    public let dueAt: String?
    public let closedAt: String?
    public let assignedTo: String?
}

public struct ProjectDetail: Decodable, Hashable {
    public let id: Int
    public let prefix: String?
    public let number: Int
    public let name: String
    public let description: String?
    public let dueAt: String?
    public let startAt: String?
    public let createdAt: String?
    public let updatedAt: String?
    public let completedAt: String?
    public let archivedAt: String?
    public let client: String?
    public let manager: String?
    public let estimatedHours: Double?
    public let budgetAmount: Double?
    public let hourlyRate: Double?
    public let milestones: [ProjectMilestone]
    public let tasks: [ProjectTask]
    public let tickets: [ProjectTicket]
}

// MARK: Contracts

public struct ContractSummary: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let type: String?
    public let status: String?
    public let client: String?
    public let value: Double?
    public let renewalFrequency: String?
    public let startDate: String?
    public let endDate: String?
    public let renewalDate: String?
    public let isExpired: Bool
    public let isDueSoon: Bool
    public let hasSla: Bool
    public let hasAllowance: Bool
}

public struct ContractSlaTier: Decodable, Hashable {
    public let responseTime: Int?
    public let resolutionTime: Int?
}

public struct ContractSla: Decodable, Hashable {
    public let high: ContractSlaTier
    public let medium: ContractSlaTier
    public let low: ContractSlaTier
}

public struct ContractAllowancePeriod: Decodable, Hashable {
    public let included: Double?
    public let used: Double
    public let remaining: Double?
    public let pct: Double?
}

public struct ContractAllowance: Decodable, Hashable {
    public let month: Int
    public let year: Int
    public let remote: ContractAllowancePeriod
    public let onsite: ContractAllowancePeriod
}

public struct ContractDocument: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let mimeType: String?
    public let size: Int
    public let uploadedAt: String?
    public let url: String
}

public struct ContractDetail: Decodable, Hashable {
    public let id: Int
    public let name: String
    public let type: String?
    public let status: String?
    public let client: String?
    public let value: Double?
    public let renewalFrequency: String?
    public let startDate: String?
    public let endDate: String?
    public let renewalDate: String?
    public let isExpired: Bool
    public let isDueSoon: Bool
    public let details: String?
    public let sla: ContractSla
    public let allowance: ContractAllowance?
    public let documents: [ContractDocument]
    public let createdAt: String?
    public let updatedAt: String?

    enum CodingKeys: String, CodingKey {
        case id, name, type, status, client, value, renewalFrequency, startDate, endDate, renewalDate, isExpired,
             isDueSoon, details, sla, allowance, documents, createdAt, updatedAt
    }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        name = try c.decode(String.self, forKey: .name)
        type = try c.decodeIfPresent(String.self, forKey: .type)
        status = try c.decodeIfPresent(String.self, forKey: .status)
        client = try c.decodeIfPresent(String.self, forKey: .client)
        value = try c.decodeIfPresent(Double.self, forKey: .value)
        renewalFrequency = try c.decodeIfPresent(String.self, forKey: .renewalFrequency)
        startDate = try c.decodeIfPresent(String.self, forKey: .startDate)
        endDate = try c.decodeIfPresent(String.self, forKey: .endDate)
        renewalDate = try c.decodeIfPresent(String.self, forKey: .renewalDate)
        isExpired = LossyBool.decode(c, .isExpired) ?? false
        isDueSoon = LossyBool.decode(c, .isDueSoon) ?? false
        details = try c.decodeIfPresent(String.self, forKey: .details)
        sla = try c.decode(ContractSla.self, forKey: .sla)
        allowance = try c.decodeIfPresent(ContractAllowance.self, forKey: .allowance)
        documents = try c.decodeIfPresent([ContractDocument].self, forKey: .documents) ?? []
        createdAt = try c.decodeIfPresent(String.self, forKey: .createdAt)
        updatedAt = try c.decodeIfPresent(String.self, forKey: .updatedAt)
    }
}

// MARK: Credentials (list only; secrets are never in the list payload)

public struct CredentialSummary: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let uri: String?
    public let client: String?
}

// MARK: Notifications, alerts, appointments

public struct AppNotification: Decodable, Identifiable, Hashable {
    public let id: Int
    public let type: String
    public let message: String
    public let action: String?
    public let timestamp: String?
}

public struct NotificationsResponse: Decodable { public let data: [AppNotification]; public let total: Int }

public struct AlertItem: Decodable, Identifiable, Hashable {
    public let source: String       // "rmm" | "backup"
    public let id: Int
    public let severity: String
    public let message: String?
    public let subject: String?
    public let clientId: Int?
    public let clientName: String?
    public let status: String
    public let createdAt: String?
    public let ticketId: Int?
    public let ticketLabel: String?
    /// Source and id together are unique; ids alone can collide between RMM and backup alerts.
    public var key: String { "\(source)-\(id)" }
}

public struct AlertsResponse: Decodable { public let data: [AlertItem]; public let total: Int }
public struct AlertActionRequest: Encodable {
    public var source: String; public var id: Int; public var action: String
    public init(source: String, id: Int, action: String) { self.source = source; self.id = id; self.action = action }
}

public struct Appointment: Decodable, Identifiable, Hashable {
    public let id: Int
    public let ticketId: Int
    public let number: Int
    public let subject: String
    public let schedule: String?
    public let scheduleEnd: String?
    public let onsite: Bool
    public let notes: String?
    public let priority: String?
    public let status: String?
    public let statusColor: String?
    public let client: String?
    public let assignedTo: String?
    public let contactName: String?
    public let contactPhone: String?
    public let address: String?
    public let city: String?
    public let state: String?

    enum CodingKeys: String, CodingKey {
        case id, ticketId, number, subject, schedule, scheduleEnd, onsite, notes, priority, status, statusColor,
             client, assignedTo, contactName, contactPhone, address, city, state
    }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(Int.self, forKey: .id)
        ticketId = try c.decode(Int.self, forKey: .ticketId)
        number = try c.decode(Int.self, forKey: .number)
        subject = try c.decode(String.self, forKey: .subject)
        schedule = try c.decodeIfPresent(String.self, forKey: .schedule)
        scheduleEnd = try c.decodeIfPresent(String.self, forKey: .scheduleEnd)
        onsite = LossyBool.decode(c, .onsite) ?? false
        notes = try c.decodeIfPresent(String.self, forKey: .notes)
        priority = try c.decodeIfPresent(String.self, forKey: .priority)
        status = try c.decodeIfPresent(String.self, forKey: .status)
        statusColor = try c.decodeIfPresent(String.self, forKey: .statusColor)
        client = try c.decodeIfPresent(String.self, forKey: .client)
        assignedTo = try c.decodeIfPresent(String.self, forKey: .assignedTo)
        contactName = try c.decodeIfPresent(String.self, forKey: .contactName)
        contactPhone = try c.decodeIfPresent(String.self, forKey: .contactPhone)
        address = try c.decodeIfPresent(String.self, forKey: .address)
        city = try c.decodeIfPresent(String.self, forKey: .city)
        state = try c.decodeIfPresent(String.self, forKey: .state)
    }
}

public struct CreateAppointmentRequest: Encodable {
    public var ticketId: Int
    public var scheduleStart: String
    public var scheduleEnd: String?
    public var onsite: Bool
    public var notes: String
    public init(ticketId: Int, scheduleStart: String, scheduleEnd: String? = nil, onsite: Bool = false, notes: String = "") {
        self.ticketId = ticketId; self.scheduleStart = scheduleStart; self.scheduleEnd = scheduleEnd
        self.onsite = onsite; self.notes = notes
    }
}

// MARK: Knowledge base

public struct KbCategory: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let parentId: Int?
    public let clientId: Int?
}

public struct KbArticleSummary: Decodable, Identifiable, Hashable {
    public let id: Int
    public let title: String
    public let categoryId: Int?
    public let categoryName: String?
    public let clientId: Int?
    public let clientName: String?
    public let updatedAt: String?
}

public struct KbArticleAttachment: Decodable, Identifiable, Hashable {
    public let id: Int
    public let name: String
    public let url: String
}

public struct KbArticleDetail: Decodable, Hashable {
    public let id: Int
    public let title: String
    public let content: String?
    public let categoryId: Int?
    public let categoryName: String?
    public let clientId: Int?
    public let clientName: String?
    public let updatedAt: String?
    public let attachments: [KbArticleAttachment]?
}

// MARK: Search

public struct KbArticleSearchResult: Decodable, Identifiable, Hashable { public let id: Int; public let title: String }

public struct SearchResult: Decodable {
    public let tickets: [TicketSummary]
    public let clients: [ClientSummary]
    public let assets: [AssetSummary]
    public let contacts: [Contact]
    public let credentials: [CredentialSummary]
    public let articles: [KbArticleSearchResult]

    enum CodingKeys: String, CodingKey { case tickets, clients, assets, contacts, credentials, articles }
    public init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        tickets = try c.decodeIfPresent([TicketSummary].self, forKey: .tickets) ?? []
        clients = try c.decodeIfPresent([ClientSummary].self, forKey: .clients) ?? []
        assets = try c.decodeIfPresent([AssetSummary].self, forKey: .assets) ?? []
        contacts = try c.decodeIfPresent([Contact].self, forKey: .contacts) ?? []
        credentials = try c.decodeIfPresent([CredentialSummary].self, forKey: .credentials) ?? []
        articles = try c.decodeIfPresent([KbArticleSearchResult].self, forKey: .articles) ?? []
    }
}

// MARK: Reports

public struct PriorityCount: Decodable, Hashable { public let priority: String?; public let count: Int }
public struct StatusCount: Decodable, Hashable { public let status: String?; public let color: String?; public let count: Int }
public struct CategoryCount: Decodable, Hashable { public let category: String?; public let color: String?; public let count: Int }

public struct OverviewReport: Decodable {
    public let year: Int
    public let byPriority: [PriorityCount]
    public let byStatus: [StatusCount]
    public let byCategory: [CategoryCount]
    public let avgResolutionHours: Double?
}

// MARK: Helpers

/// The server sends booleans as true/false, 0/1 or "0"/"1" depending on the endpoint; accept all of them.
enum LossyBool {
    static func decode<K: CodingKey>(_ c: KeyedDecodingContainer<K>, _ key: K) -> Bool? {
        if let b = try? c.decodeIfPresent(Bool.self, forKey: key) { return b }
        if let i = try? c.decodeIfPresent(Int.self, forKey: key) { return i != 0 }
        if let s = try? c.decodeIfPresent(String.self, forKey: key) { return s == "1" || s.lowercased() == "true" }
        return nil
    }
}
