package com.foleyit.itflow.data.api

import com.foleyit.itflow.ui.util.PagedResponse
import com.google.gson.annotations.SerializedName

// ── Auth ────────────────────────────────────────────────────────────────────
data class LoginRequest(val username: String, val password: String, val device_name: String, val totp_code: String? = null)
data class LoginResponse(
    val token: String? = null,
    val user: UserInfo? = null,
    @SerializedName("requires_2fa") val requires2fa: Boolean? = null
)
data class UserInfo(val id: Int, val name: String, val email: String, val type: Int)

data class PasskeyBeginResponse(
    val challenge: String,
    val timeout: Long,
    @SerializedName("rpId") val rpId: String,
    @SerializedName("allowCredentials") val allowCredentials: List<Any>,
    @SerializedName("userVerification") val userVerification: String,
    @SerializedName("challengeToken") val challengeToken: String
)

data class PasskeyCompleteRequest(
    @SerializedName("challenge_token") val challengeToken: String,
    @SerializedName("passkey_response") val passkeyResponse: Map<String, Any>
)

// ── Dashboard ────────────────────────────────────────────────────────────────
data class DashboardResponse(
    @SerializedName("my_open")  val myOpen: Int,
    @SerializedName("all_open") val allOpen: Int,
    val overdue: Int,
    val unread: Int,
    @SerializedName("due_today") val dueToday: Int? = null,
    @SerializedName("onsite_open") val onsiteOpen: Int? = null,
    val queue: List<TicketSummary>
)

// ── Tickets ──────────────────────────────────────────────────────────────────
data class TicketsResponse(override val data: List<TicketSummary>, override val total: Int) : PagedResponse<TicketSummary>

data class TicketSummary(
    val id: Int,
    val number: Int,
    val subject: String,
    val priority: String?,
    val status: String?,
    @SerializedName("status_color") val statusColor: String?,
    val client: String?,
    @SerializedName("assigned_to") val assignedTo: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("due_at") val dueAt: String?,
    @SerializedName("resolved_at") val resolvedAt: String?
)

data class TicketDetail(
    val id: Int, val number: Int, val subject: String, val details: String?,
    val priority: String?, val status: String?,
    @SerializedName("status_color") val statusColor: String?,
    val client: String?,
    @SerializedName("assigned_to") val assignedTo: String?,
    @SerializedName("contact_name") val contactName: String?,
    @SerializedName("contact_email") val contactEmail: String?,
    @SerializedName("contact_phone") val contactPhone: String?,
    val billable: Boolean,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("due_at") val dueAt: String?,
    @SerializedName("resolved_at") val resolvedAt: String?,
    val replies: List<TicketReply>
)

data class TicketReply(
    val id: Int, val body: String, val type: String,
    @SerializedName("time_worked") val timeWorked: String?,
    val onsite: Boolean?,
    val by: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class AddReplyRequest(val reply: String, val type: String, val time_worked: String?, val onsite: Int = 0, val status_id: Int? = null)
data class TicketStatus(val id: Int, val name: String, val color: String)

data class LogTimeRequest(val time_worked: String, val note: String)

// ── Clients ──────────────────────────────────────────────────────────────────
data class ClientsResponse(override val data: List<ClientSummary>, override val total: Int) : PagedResponse<ClientSummary>

data class ClientSummary(
    val id: Int, val name: String, val phone: String?,
    val city: String?, val state: String?, val website: String?
)

data class ClientDetail(
    val id: Int, val name: String, val phone: String?, val address: String?,
    val city: String?, val state: String?, val zip: String?, val website: String?,
    val notes: String?,
    @SerializedName("open_tickets") val openTickets: Int,
    val contacts: List<Contact>
)

data class Contact(
    val id: Int, val name: String, val title: String?,
    val email: String?, val phone: String?, val extension: String?,
    val client: String?
)

// ── Assets ───────────────────────────────────────────────────────────────────
data class AssetsResponse(override val data: List<AssetSummary>, override val total: Int) : PagedResponse<AssetSummary>

data class AssetSummary(
    val id: Int, val name: String, val tag: String?, val type: String?,
    val make: String?, val model: String?, val serial: String?,
    val os: String?, val status: String?, val client: String?
)

data class AssetDetail(
    val id: Int, val name: String, val tag: String?, val type: String?,
    val make: String?, val model: String?, val serial: String?,
    val os: String?, val status: String?, val description: String?,
    @SerializedName("physical_location") val physicalLocation: String?,
    @SerializedName("location_name") val locationName: String?,
    @SerializedName("location_city") val locationCity: String?,
    @SerializedName("location_state") val locationState: String?,
    @SerializedName("contact_name") val contactName: String?,
    @SerializedName("contact_phone") val contactPhone: String?,
    val client: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("purchase_date") val purchaseDate: String?,
    @SerializedName("warranty_expire") val warrantyExpire: String?,
    val notes: String?
)

// ── Credentials ──────────────────────────────────────────────────────────────
data class CredentialsResponse(override val data: List<CredentialSummary>, override val total: Int) : PagedResponse<CredentialSummary>

data class CredentialSummary(
    val id: Int, val name: String,
    val uri: String?, val client: String?
)

data class CredentialDetail(
    val id: Int, val name: String, val username: String?, val password: String?,
    val uri: String?, val uri2: String?,
    val note: String?, val client: String?
)

// ── Quotes ───────────────────────────────────────────────────────────────────
data class QuotesResponse(override val data: List<QuoteSummary>, override val total: Int) : PagedResponse<QuoteSummary>

data class QuoteSummary(
    val id: Int, val number: String?, val subject: String?,
    val status: String?, val date: String?, val total: Double?, val client: String?,
    @SerializedName("guest_url") val guestUrl: String?
)

data class QuoteDetail(
    val id: Int, val number: String?, val subject: String?,
    val status: String?, val date: String?,
    val subtotal: Double?, val tax: Double?, val total: Double?,
    val client: String?,
    @SerializedName("contact_name") val contactName: String?,
    val notes: String?,
    @SerializedName("guest_url") val guestUrl: String?,
    val items: List<LineItem>
)

// ── Invoices ─────────────────────────────────────────────────────────────────
data class InvoicesResponse(override val data: List<InvoiceSummary>, override val total: Int) : PagedResponse<InvoiceSummary>

data class InvoiceSummary(
    val id: Int, val number: String?, val date: String?,
    @SerializedName("due_date") val dueDate: String?,
    val status: String?, val total: Double?, val client: String?,
    @SerializedName("guest_url") val guestUrl: String?
)

data class InvoiceDetail(
    val id: Int, val number: String?, val date: String?,
    @SerializedName("due_date") val dueDate: String?,
    val status: String?,
    val subtotal: Double?, val tax: Double?, val total: Double?, val balance: Double?,
    val client: String?,
    @SerializedName("contact_name") val contactName: String?,
    val notes: String?,
    @SerializedName("guest_url") val guestUrl: String?,
    val items: List<LineItem>
)

data class LineItem(
    val description: String?, val quantity: Double?,
    @SerializedName("unit_price") val unitPrice: Double?,
    val total: Double?, val taxable: Boolean
)

// ── Expenses ─────────────────────────────────────────────────────────────────
data class ExpensesResponse(override val data: List<ExpenseSummary>, override val total: Int) : PagedResponse<ExpenseSummary>

data class ExpenseSummary(
    val id: Int, val description: String?, val amount: Double?,
    val currency: String?, val date: String?,
    val reference: String?,
    @SerializedName("payment_method") val paymentMethod: String?,
    @SerializedName("has_receipt") val hasReceipt: Boolean,
    val client: String?
)

// ── Notifications ─────────────────────────────────────────────────────────────
data class NotificationsResponse(val data: List<Notification>, val total: Int)

data class Notification(
    val id: Int, val type: String, val message: String,
    val action: String?, val timestamp: String?
)

// ── Client Tabs ──────────────────────────────────────────────────────────────
data class ClientLocation(
    val id: Int, val name: String?, val address: String?,
    val city: String?, val state: String?, val zip: String?,
    val phone: String?, val primary: Boolean
)

data class ClientContract(
    val id: Int, val name: String?, val status: String?, val type: String?
)

data class ClientFile(
    val id: Int,
    @SerializedName("ticket_id") val ticketId: Int,
    @SerializedName("ticket_number") val ticketNumber: Int,
    @SerializedName("ticket_subject") val ticketSubject: String?,
    @SerializedName("signed_name") val signedName: String?,
    @SerializedName("signed_at") val signedAt: String?
)

// ── Charges & Worksheets ─────────────────────────────────────────────────────
data class TicketCharge(
    val id: Int, val name: String, val description: String?,
    val quantity: Double, @SerializedName("unit_price") val unitPrice: Double,
    val total: Double, val invoiced: Boolean,
    @SerializedName("created_by") val createdBy: String?,
    @SerializedName("created_at") val createdAt: String?
)

data class ChargesResponse(val charges: List<TicketCharge>, val total: Double)

data class WorksheetSummary(
    val id: Int, @SerializedName("template_name") val templateName: String?,
    @SerializedName("created_by") val createdBy: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("completed_at") val completedAt: String?,
    @SerializedName("signed_name") val signedName: String?,
    @SerializedName("signed_at") val signedAt: String?,
    @SerializedName("is_outtake") val isOuttake: Boolean,
    val signed: Boolean
)

data class WorksheetField(
    val id: Int, val name: String, val type: String,
    val options: String?, val required: Boolean, val value: String?
)

data class WorksheetDetail(
    val id: Int, @SerializedName("template_name") val templateName: String?,
    @SerializedName("signed_name") val signedName: String?,
    @SerializedName("signed_at") val signedAt: String?,
    @SerializedName("completed_at") val completedAt: String?,
    val signed: Boolean, val fields: List<WorksheetField>
)

data class WorksheetTemplate(val id: Int, val name: String, val description: String?)

data class SignRequest(
    @SerializedName("signed_name") val signedName: String,
    val signature: String
)

data class CompleteWorksheetRequest(val completed: Boolean)

// ── Profile ──────────────────────────────────────────────────────────────────
data class UserProfile(
    val id: Int, val name: String, val email: String,
    val type: Int, val color: String?, val avatar: String?
)

data class AddChargeRequest(
    val name: String, val description: String,
    val quantity: Double, @com.google.gson.annotations.SerializedName("unit_price") val unitPrice: Double
)

data class CreateWorksheetRequest(
    @com.google.gson.annotations.SerializedName("template_id") val templateId: Int = 0,
    @com.google.gson.annotations.SerializedName("is_outtake") val isOuttake: Int = 0
)

data class SaveResponsesRequest(val responses: List<FieldResponse>)
data class FieldResponse(
    @com.google.gson.annotations.SerializedName("field_id") val fieldId: Int,
    val value: String
)

data class UpdateProfileRequest(
    val name: String, val email: String,
    @com.google.gson.annotations.SerializedName("current_password") val currentPassword: String = "",
    @com.google.gson.annotations.SerializedName("new_password") val newPassword: String = ""
)

data class FcmTokenRequest(
    @com.google.gson.annotations.SerializedName("fcm_token") val fcmToken: String
)

data class BiometricKeyRequest(
    @com.google.gson.annotations.SerializedName("device_public_key_pem") val devicePublicKeyPem: String
)

// ── Create Ticket ────────────────────────────────────────────────────────────
data class CreateTicketRequest(
    val subject: String,
    val details: String = "",
    @SerializedName("client_id") val clientId: Int? = null,
    val priority: String = "low",
    @SerializedName("assigned_to") val assignedTo: Int? = null,
    @SerializedName("category_id") val categoryId: Int? = null
)

// ── Search ───────────────────────────────────────────────────────────────────
data class SearchResult(
    val tickets: List<TicketSummary>,
    val clients: List<ClientSummary>,
    val assets: List<AssetSummary>
)

data class SearchTicket(
    val id: Int, val number: Int, val subject: String,
    val status: String?, val client: String?, val priority: String?
)

// ── Time Report ──────────────────────────────────────────────────────────────
data class TimeReportResponse(
    val period: String,
    @SerializedName("total_hours") val totalHours: Double,
    val entries: List<TimeReportEntry>
)

data class TimeReportEntry(
    val client: String,
    @SerializedName("client_id") val clientId: Int,
    val hours: Double,
    @SerializedName("ticket_count") val ticketCount: Int
)

// ── Reports (extended) ────────────────────────────────────────────────────────
data class MonthCount(val month: Int, val count: Int)
data class TicketVolumeResponse(val year: Int, val months: List<MonthCount>)

data class TicketsByClientResponse(val year: Int, val month: Int?, val clients: List<ClientTicketStats>)
data class ClientTicketStats(
    @SerializedName("client_id") val clientId: Int,
    val client: String,
    val raised: Int,
    val resolved: Int,
    @SerializedName("priority_low") val priorityLow: Int,
    @SerializedName("priority_medium") val priorityMedium: Int,
    @SerializedName("priority_high") val priorityHigh: Int,
    @SerializedName("seconds_worked") val secondsWorked: Long,
    @SerializedName("avg_response_seconds") val avgResponseSeconds: Long?,
    @SerializedName("avg_resolve_seconds") val avgResolveSeconds: Long?
)

data class TimeByTechResponse(val year: Int, val technicians: List<TechTimeStats>)
data class TechTimeStats(
    @SerializedName("user_id") val userId: Int,
    val name: String,
    @SerializedName("tickets_assigned") val ticketsAssigned: Int,
    @SerializedName("tickets_touched") val ticketsTouched: Int,
    @SerializedName("seconds_worked") val secondsWorked: Long
)

data class TechPerformanceResponse(val year: Int, val technicians: List<TechPerformanceStats>)
data class TechPerformanceStats(
    val name: String,
    @SerializedName("open_tickets") val openTickets: Int,
    @SerializedName("resolved_this_year") val resolvedThisYear: Int
)

data class UnbilledTicketsResponse(val year: Int, val clients: List<UnbilledClientStats>)
data class UnbilledClientStats(
    @SerializedName("client_id") val clientId: Int,
    val client: String,
    val raised: Int,
    @SerializedName("billable_closed") val billableClosed: Int,
    val unbilled: Int
)

data class ClientsWithBalanceResponse(val clients: List<ClientBalance>)
data class ClientBalance(
    @SerializedName("client_id") val clientId: Int,
    @SerializedName("client_name") val clientName: String,
    val balance: Double
)

data class FinancialSummaryResponse(
    val year: Int,
    val categories: List<CategoryMonths>,
    @SerializedName("grand_total") val grandTotal: Double
)
data class CategoryMonths(
    @SerializedName("category_id") val categoryId: Int,
    @SerializedName("category_name") val categoryName: String,
    val months: List<Double>,
    val total: Double
)

data class ProfitLossResponse(val year: Int, val months: List<ProfitLossMonth>)
data class ProfitLossMonth(val month: Int, val income: Double, val expense: Double, val profit: Double)

data class ExpiringResponse(val type: String, val days: Int, val items: List<ExpiringItem>)
data class ExpiringItem(
    val id: Int, val name: String,
    @SerializedName("expire_date") val expireDate: String?,
    @SerializedName("client_name") val clientName: String?
)

data class OverviewResponse(
    val year: Int,
    @SerializedName("by_priority") val byPriority: List<PriorityCount>,
    @SerializedName("by_status") val byStatus: List<StatusCount>,
    @SerializedName("by_category") val byCategory: List<CategoryCount>,
    @SerializedName("avg_resolution_hours") val avgResolutionHours: Double?
)
data class PriorityCount(val priority: String, val count: Int)
data class StatusCount(val status: String, val color: String, val count: Int)
data class CategoryCount(val category: String, val color: String, val count: Int)

// ── Reports: Support & Operations (CSAT / RMM Health / Service Desk / Tech Utilization) ────────
data class CsatReportResponse(
    @SerializedName("date_from") val dateFrom: String,
    @SerializedName("date_to") val dateTo: String,
    val summary: CsatSummary,
    val distribution: Map<String, Int>,
    val trend: List<CsatTrendPoint>,
    @SerializedName("by_technician") val byTechnician: List<CsatTechStats>,
    @SerializedName("by_client") val byClient: List<CsatClientStats>,
    val feedback: List<CsatFeedbackItem>
)
data class CsatSummary(
    @SerializedName("total_closed") val totalClosed: Int,
    val rated: Int,
    @SerializedName("response_rate_pct") val responseRatePct: Double?,
    @SerializedName("avg_rating") val avgRating: Double?,
    @SerializedName("satisfied_pct") val satisfiedPct: Double?,
    @SerializedName("needs_followup_count") val needsFollowupCount: Int
)
data class CsatTrendPoint(val label: String, val count: Int, @SerializedName("avg_rating") val avgRating: Double?)
data class CsatTechStats(
    @SerializedName("user_id") val userId: Int,
    val name: String,
    @SerializedName("rated_count") val ratedCount: Int,
    @SerializedName("avg_rating") val avgRating: Double?,
    @SerializedName("satisfied_pct") val satisfiedPct: Double?
)
data class CsatClientStats(
    @SerializedName("client_id") val clientId: Int,
    val name: String,
    @SerializedName("rated_count") val ratedCount: Int,
    @SerializedName("avg_rating") val avgRating: Double?,
    @SerializedName("satisfied_pct") val satisfiedPct: Double?
)
data class CsatFeedbackItem(
    @SerializedName("ticket_id") val ticketId: Int,
    @SerializedName("ticket_number") val ticketNumber: Int,
    @SerializedName("client_name") val clientName: String?,
    @SerializedName("tech_name") val techName: String?,
    val rating: Int,
    val comment: String?,
    @SerializedName("rated_at") val ratedAt: String?
)

data class RmmHealthReportResponse(
    @SerializedName("date_from") val dateFrom: String,
    @SerializedName("date_to") val dateTo: String,
    @SerializedName("have_data") val haveData: Boolean,
    val totals: RmmHealthTotals,
    @SerializedName("by_severity") val bySeverity: List<RmmCountByKey>,
    @SerializedName("by_status") val byStatus: List<RmmCountByKey>,
    val trend: RmmHealthTrend,
    @SerializedName("noisiest_assets") val noisiestAssets: List<RmmNoisyAsset>,
    @SerializedName("noisiest_clients") val noisiestClients: List<RmmNoisyClient>
)
data class RmmHealthTotals(
    val total: Int,
    val resolved: Int,
    val acknowledged: Int,
    val open: Int,
    @SerializedName("with_ticket") val withTicket: Int,
    @SerializedName("conversion_pct") val conversionPct: Double?,
    @SerializedName("mtta_seconds") val mttaSeconds: Long?,
    @SerializedName("mttr_seconds") val mttrSeconds: Long?
)
// Shared shape for both by_severity ({"severity":...}) and by_status ({"status":...}) list items.
data class RmmCountByKey(
    @SerializedName(value = "severity", alternate = ["status"]) val key: String,
    val count: Int
)
data class RmmHealthTrend(val labels: List<String>, val counts: List<Int>)
data class RmmNoisyAsset(
    @SerializedName("asset_id") val assetId: Int,
    @SerializedName("asset_name") val assetName: String,
    val alerts: Int,
    @SerializedName("mttr_seconds") val mttrSeconds: Long?
)
data class RmmNoisyClient(
    @SerializedName("client_id") val clientId: Int,
    @SerializedName("client_name") val clientName: String,
    val alerts: Int,
    @SerializedName("with_ticket") val withTicket: Int
)

data class ServiceDeskReportResponse(
    @SerializedName("date_from") val dateFrom: String,
    @SerializedName("date_to") val dateTo: String,
    val totals: ServiceDeskTotals,
    val volume: ServiceDeskVolume,
    val aging: List<ServiceDeskAgingBucket>,
    val sla: ServiceDeskSla,
    val csat: ServiceDeskCsat,
    @SerializedName("by_priority") val byPriority: List<ServiceDeskPriorityStats>,
    @SerializedName("by_technician") val byTechnician: List<ServiceDeskTechStats>
)
data class ServiceDeskTotals(val opened: Int, val resolved: Int, @SerializedName("open_now") val openNow: Int)
data class ServiceDeskVolume(
    val labels: List<String>,
    val opened: List<Int>,
    val resolved: List<Int>,
    val backlog: List<Int>
)
data class ServiceDeskAgingBucket(val label: String, val count: Int)
data class ServiceDeskSla(
    @SerializedName("response_total") val responseTotal: Int,
    @SerializedName("response_met") val responseMet: Int,
    @SerializedName("response_pct") val responsePct: Double?,
    @SerializedName("resolution_total") val resolutionTotal: Int,
    @SerializedName("resolution_met") val resolutionMet: Int,
    @SerializedName("resolution_pct") val resolutionPct: Double?
)
data class ServiceDeskCsat(
    val rated: Int,
    @SerializedName("avg_rating") val avgRating: Double?,
    val satisfied: Int,
    @SerializedName("satisfied_pct") val satisfiedPct: Double?
)
data class ServiceDeskPriorityStats(
    val priority: String,
    val total: Int,
    @SerializedName("avg_response_seconds") val avgResponseSeconds: Long?,
    @SerializedName("avg_resolve_seconds") val avgResolveSeconds: Long?
)
data class ServiceDeskTechStats(
    @SerializedName("user_id") val userId: Int,
    val name: String,
    val open: Int,
    val resolved: Int,
    @SerializedName("avg_resolve_seconds") val avgResolveSeconds: Long?
)

data class TechUtilizationReportResponse(
    @SerializedName("date_from") val dateFrom: String,
    @SerializedName("date_to") val dateTo: String,
    val capacity: TechUtilizationCapacity,
    val totals: TechUtilizationTotals,
    val technicians: List<TechUtilizationStats>
)
data class TechUtilizationCapacity(
    @SerializedName("hours_per_day") val hoursPerDay: Int,
    @SerializedName("days_per_week") val daysPerWeek: Int,
    @SerializedName("business_days") val businessDays: Int,
    @SerializedName("capacity_seconds") val capacitySeconds: Long,
    @SerializedName("capacity_hours_per_tech") val capacityHoursPerTech: Int
)
data class TechUtilizationTotals(
    @SerializedName("billable_seconds") val billableSeconds: Long,
    @SerializedName("nonbillable_seconds") val nonbillableSeconds: Long,
    @SerializedName("total_seconds") val totalSeconds: Long,
    @SerializedName("billable_value") val billableValue: Double,
    @SerializedName("tickets_closed") val ticketsClosed: Int,
    @SerializedName("csat_rated_count") val csatRatedCount: Int,
    @SerializedName("team_utilization_pct") val teamUtilizationPct: Double?,
    @SerializedName("csat_avg_rating") val csatAvgRating: Double?,
    @SerializedName("csat_satisfied_pct") val csatSatisfiedPct: Double?
)
data class TechUtilizationStats(
    @SerializedName("user_id") val userId: Int,
    val name: String,
    @SerializedName("billable_seconds") val billableSeconds: Long,
    @SerializedName("nonbillable_seconds") val nonbillableSeconds: Long,
    @SerializedName("total_seconds") val totalSeconds: Long,
    @SerializedName("billable_value") val billableValue: Double,
    @SerializedName("tickets_closed") val ticketsClosed: Int,
    @SerializedName("avg_handle_seconds") val avgHandleSeconds: Long?,
    @SerializedName("csat_rated_count") val csatRatedCount: Int,
    @SerializedName("csat_avg_rating") val csatAvgRating: Double?,
    @SerializedName("csat_satisfied_pct") val csatSatisfiedPct: Double?,
    @SerializedName("csat_comment_count") val csatCommentCount: Int,
    @SerializedName("utilization_pct") val utilizationPct: Double?
)

// ── Crash Reporting ──────────────────────────────────────────────────────────
data class CrashReportRequest(
    val platform: String = "android",
    @SerializedName("app_version_name") val appVersionName: String,
    @SerializedName("app_version_code") val appVersionCode: Int,
    @SerializedName("device_model") val deviceModel: String,
    @SerializedName("os_version") val osVersion: String,
    @SerializedName("sdk_int") val sdkInt: Int,
    @SerializedName("thread_name") val threadName: String,
    @SerializedName("is_fatal") val isFatal: Boolean = true,
    @SerializedName("occurred_at") val occurredAt: String,
    @SerializedName("stack_trace") val stackTrace: String
)

// ── Outtake Forms ────────────────────────────────────────────────────────────
data class OuttakeSummary(
    val id: Int,
    @SerializedName("sign_token") val signToken: String?,
    val notes: String?,
    @SerializedName("created_by") val createdBy: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("signed_name") val signedName: String?,
    @SerializedName("signed_at") val signedAt: String?,
    val signed: Boolean
)

data class OuttakeDetail(
    val id: Int,
    @SerializedName("sign_token") val signToken: String?,
    val notes: String?,
    @SerializedName("created_by") val createdBy: String?,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("signed_name") val signedName: String?,
    @SerializedName("signed_at") val signedAt: String?,
    val signed: Boolean,
    @SerializedName("ticket_subject") val ticketSubject: String?,
    val client: String?,
    @SerializedName("contact_name") val contactName: String?
)

// ── Products ─────────────────────────────────────────────────────────────────
data class Product(
    val id: Int, val name: String, val type: String?,
    val description: String?, val price: Double,
    val currency: String?, val code: String?
)

// ── Ticket Live Chat ─────────────────────────────────────────────────────────
data class ChatMessagesResponse(val data: List<ChatMessage>)

data class ChatMessage(
    val id: Int,
    @SerializedName("sender_type") val senderType: String,
    @SerializedName("sender_id") val senderId: Int,
    @SerializedName("sender_name") val senderName: String?,
    val message: String,
    @SerializedName("created_at") val createdAt: String?
)

data class SendChatMessageRequest(val message: String)

// ── Knowledge Base ───────────────────────────────────────────────────────────
data class KbCategory(
    val id: Int, val name: String,
    @SerializedName("parent_id") val parentId: Int,
    @SerializedName("client_id") val clientId: Int
)

data class KbArticlesResponse(override val data: List<KbArticleSummary>, override val total: Int) : PagedResponse<KbArticleSummary>

data class KbArticleSummary(
    val id: Int, val title: String,
    @SerializedName("category_id") val categoryId: Int,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("client_id") val clientId: Int,
    @SerializedName("client_name") val clientName: String?,
    @SerializedName("client_visible") val clientVisible: Int,
    @SerializedName("updated_at") val updatedAt: String?
)

data class KbArticleAttachment(val id: Int, val name: String, val url: String)

data class KbArticleDetail(
    val id: Int, val title: String, val content: String?,
    @SerializedName("category_id") val categoryId: Int,
    @SerializedName("category_name") val categoryName: String?,
    @SerializedName("client_id") val clientId: Int,
    @SerializedName("client_name") val clientName: String?,
    @SerializedName("client_visible") val clientVisible: Int,
    @SerializedName("updated_at") val updatedAt: String?,
    val attachments: List<KbArticleAttachment>?
)

// ── Ticket categories & saved views ─────────────────────────────────────────
data class TicketCategory(val id: Int, val name: String, val color: String?)

data class SavedTicketView(
    val id: Int, val name: String, val icon: String?,
    val params: Map<String, String>
)

// ── Alerts (RMM + backup) ───────────────────────────────────────────────────
data class AlertItem(
    val source: String, // "rmm" | "backup"
    val id: Int,
    val severity: String,
    val message: String?,
    val subject: String?,
    @SerializedName("client_id") val clientId: Int?,
    @SerializedName("client_name") val clientName: String?,
    val status: String,
    @SerializedName("created_at") val createdAt: String?,
    @SerializedName("ticket_id") val ticketId: Int?,
    @SerializedName("ticket_label") val ticketLabel: String?
)

data class AlertsResponse(val data: List<AlertItem>, val total: Int)

data class AlertActionRequest(val source: String, val id: Int, val action: String)
