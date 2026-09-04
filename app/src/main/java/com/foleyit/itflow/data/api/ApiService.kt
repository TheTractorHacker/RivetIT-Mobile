package com.foleyit.itflow.data.api

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.http.*

interface ApiService {

    // Auth — password
    @POST("auth")
    suspend fun login(@Body req: LoginRequest): LoginResponse

    @DELETE("auth")
    suspend fun logout()

    // Auth — passkey
    @GET("auth")
    suspend fun passkeyBegin(): PasskeyBeginResponse

    @POST("auth")
    suspend fun passkeyComplete(@Body req: PasskeyCompleteRequest): LoginResponse

    // Dashboard
    @GET("dashboard")
    suspend fun getDashboard(): DashboardResponse

    // Create ticket
    @POST("tickets")
    suspend fun createTicket(@Body req: CreateTicketRequest): Map<String, Int>

    // Search
    @GET("search")
    suspend fun search(@Query("q") q: String): SearchResult

    // Time report
    @GET("reports/time")
    suspend fun getTimeReport(
        @Query("period") period: String = "week",
        @Query("mine") mine: Int = 0
    ): TimeReportResponse

    // Reports (extended) — operational reports are cacheable; financial ones are not.
    @GET("reports/tickets")
    suspend fun getTicketVolumeReport(@Query("year") year: Int? = null): TicketVolumeResponse

    @GET("reports/tickets-by-client")
    suspend fun getTicketsByClientReport(
        @Query("year") year: Int? = null,
        @Query("month") month: Int? = null
    ): TicketsByClientResponse

    @GET("reports/time-by-tech")
    suspend fun getTimeByTechReport(@Query("year") year: Int? = null): TimeByTechResponse

    @GET("reports/tech-performance")
    suspend fun getTechPerformanceReport(@Query("year") year: Int? = null): TechPerformanceResponse

    @GET("reports/expiring")
    suspend fun getExpiringReport(
        @Query("type") type: String = "domains",
        @Query("days") days: Int = 30
    ): ExpiringResponse

    @GET("reports/overview")
    suspend fun getOverviewReport(@Query("year") year: Int? = null): OverviewResponse

    @GET("reports/csat")
    suspend fun getCsatReport(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): CsatReportResponse

    @GET("reports/rmm-health")
    suspend fun getRmmHealthReport(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): RmmHealthReportResponse

    @GET("reports/service-desk")
    suspend fun getServiceDeskReport(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): ServiceDeskReportResponse

    @GET("reports/technician-performance")
    suspend fun getTechUtilizationReport(
        @Query("from") from: String? = null,
        @Query("to") to: String? = null
    ): TechUtilizationReportResponse

    // Semi-public: server accepts this even without a valid token (see
    // api/v1/crash_reports.php) - the interceptor still attaches one when present,
    // but a missing/expired token must never block a crash report from going out.
    @POST("crash-reports")
    suspend fun reportCrash(@Body body: CrashReportRequest): Map<String, Boolean>

    @Headers("Cache-Control: no-store")
    @GET("reports/unbilled-tickets")
    suspend fun getUnbilledTicketsReport(@Query("year") year: Int? = null): UnbilledTicketsResponse

    @Headers("Cache-Control: no-store")
    @GET("reports/clients-with-balance")
    suspend fun getClientsWithBalanceReport(): ClientsWithBalanceResponse

    @Headers("Cache-Control: no-store")
    @GET("reports/income-summary")
    suspend fun getIncomeSummaryReport(@Query("year") year: Int? = null): FinancialSummaryResponse

    @Headers("Cache-Control: no-store")
    @GET("reports/expense-summary")
    suspend fun getExpenseSummaryReport(@Query("year") year: Int? = null): FinancialSummaryResponse

    @Headers("Cache-Control: no-store")
    @GET("reports/profit-loss")
    suspend fun getProfitLossReport(@Query("year") year: Int? = null): ProfitLossResponse

    // Ticket attachment upload
    @Multipart
    @POST("tickets/{id}/attachments")
    suspend fun uploadAttachment(
        @Path("id") id: Int,
        @Part file: MultipartBody.Part
    ): Map<String, String>

    // Tickets
    @GET("tickets")
    suspend fun getTickets(
        @Query("status") status: String = "open",
        @Query("mine") mine: Int = 0,
        @Query("search") search: String = "",
        @Query("page") page: Int = 1,
        @Query("priority") priority: String? = null,
        @Query("onsite") onsite: Int? = null,
        @Query("category_id") categoryId: Int? = null,
        @Query("overdue") overdue: Int? = null,
        @Query("due_today") dueToday: Int? = null
    ): TicketsResponse

    // Ticket live chat
    @GET("tickets/{id}/chat")
    suspend fun getChatMessages(@Path("id") id: Int, @Query("since_id") sinceId: Int = 0): ChatMessagesResponse

    @POST("tickets/{id}/chat")
    suspend fun sendChatMessage(@Path("id") id: Int, @Body req: SendChatMessageRequest): Map<String, Int>

    // Knowledge base
    @GET("kb/categories")
    suspend fun getKbCategories(): List<KbCategory>

    @GET("kb/articles")
    suspend fun getKbArticles(
        @Query("category_id") categoryId: Int? = null,
        @Query("client_id") clientId: Int? = null,
        @Query("search") search: String = "",
        @Query("page") page: Int = 1
    ): KbArticlesResponse

    @GET("kb/articles/{id}")
    suspend fun getKbArticle(@Path("id") id: Int): KbArticleDetail

    // Ticket categories & saved views
    @GET("ticket-categories")
    suspend fun getTicketCategories(): List<TicketCategory>

    @GET("ticket-views")
    suspend fun getSavedTicketViews(): List<SavedTicketView>

    @GET("tickets/{id}")
    @Headers("Cache-Control: no-store")
    suspend fun getTicket(@Path("id") id: Int): TicketDetail

    @POST("tickets/{id}/reply")
    suspend fun addReply(@Path("id") id: Int, @Body req: AddReplyRequest)

    @DELETE("tickets/{id}/reply/{replyId}")
    suspend fun deleteReply(@Path("id") id: Int, @Path("replyId") replyId: Int)

    
    @POST("tickets/{id}/status")
    suspend fun updateTicketStatus(@Path("id") id: Int, @Body body: Map<String, Int>)

    @GET("statuses")
    suspend fun getTicketStatuses(): List<TicketStatus>

    @POST("tickets/{id}/time")
    suspend fun logTime(@Path("id") id: Int, @Body req: LogTimeRequest)

    // Clients
    @GET("clients")
    suspend fun getClients(
        @Query("search") search: String = "",
        @Query("page") page: Int = 1
    ): ClientsResponse

    @Headers("Cache-Control: no-store")
    @GET("clients/{id}")
    suspend fun getClient(@Path("id") id: Int): ClientDetail

    // Assets
    @GET("assets")
    suspend fun getAssets(
        @Query("search") search: String = "",
        @Query("page") page: Int = 1,
        @Query("type") type: String = ""
    ): AssetsResponse

    @GET("assets/types")
    suspend fun getAssetTypes(): List<String>

    @Headers("Cache-Control: no-store")
    @GET("assets/{id}")
    suspend fun getAsset(@Path("id") id: Int): AssetDetail

    // Credentials — never cache: responses contain decrypted passwords
    @Headers("Cache-Control: no-store")
    @GET("credentials")
    suspend fun getCredentials(
        @Query("search") search: String = "",
        @Query("page") page: Int = 1
    ): CredentialsResponse

    @Headers("Cache-Control: no-store")
    @GET("credentials/{id}")
    suspend fun getCredential(
        @Path("id") id: Int,
        @Header("X-Biometric-Challenge-Token") challengeToken: String,
        @Header("X-Biometric-Signature") signature: String
    ): CredentialDetail

    // Quotes — no-store: responses contain guest_url, a bearer-token-like public link
    @Headers("Cache-Control: no-store")
    @GET("quotes")
    suspend fun getQuotes(@Query("page") page: Int = 1): QuotesResponse

    @Headers("Cache-Control: no-store")
    @GET("quotes/{id}")
    suspend fun getQuote(@Path("id") id: Int): QuoteDetail

    // Invoices — no-store: responses contain guest_url, a bearer-token-like public link
    @Headers("Cache-Control: no-store")
    @GET("invoices")
    suspend fun getInvoices(@Query("page") page: Int = 1): InvoicesResponse

    @Headers("Cache-Control: no-store")
    @GET("invoices/{id}")
    suspend fun getInvoice(@Path("id") id: Int): InvoiceDetail

    // Expenses
    @Headers("Cache-Control: no-store")
    @GET("expenses")
    suspend fun getExpenses(@Query("page") page: Int = 1): ExpensesResponse

    @Multipart
    @POST("expenses")
    suspend fun createExpense(
        @Part("description") description: RequestBody,
        @Part("amount") amount: RequestBody,
        @Part("date") date: RequestBody,
        @Part("currency") currency: RequestBody,
        @Part("reference") reference: RequestBody,
        @Part("payment_method") paymentMethod: RequestBody,
        @Part receipt: MultipartBody.Part?
    )

    // Notifications
    @GET("notifications")
    suspend fun getNotifications(@Query("page") page: Int = 1): NotificationsResponse

    // no-store: these mutate frequently (add/delete/sign) and must never be served from the
    // 5-minute disk cache, or the ticket detail screen shows stale state after an edit.
    @Headers("Cache-Control: no-store")
    @GET("tickets/{id}/charges")
    suspend fun getTicketCharges(@Path("id") id: Int): ChargesResponse

    @Headers("Cache-Control: no-store")
    @GET("tickets/{id}/worksheets")
    suspend fun getTicketWorksheets(@Path("id") id: Int): List<WorksheetSummary>

    @Headers("Cache-Control: no-store")
    @GET("tickets/{id}/outtakes")
    suspend fun getTicketOuttakes(@Path("id") id: Int): List<OuttakeSummary>

    @Headers("Cache-Control: no-store")
    @GET("outtakes/{id}")
    suspend fun getOuttake(@Path("id") id: Int): OuttakeDetail

    @POST("outtakes/{id}/sign")
    suspend fun signOuttake(@Path("id") id: Int, @Body body: SignRequest)

    @DELETE("outtakes/{id}")
    suspend fun deleteOuttake(@Path("id") id: Int)

    @DELETE("worksheets/{id}")
    suspend fun deleteWorksheet(@Path("id") id: Int)

    @Headers("Cache-Control: no-store")
    @GET("worksheets/{id}")
    suspend fun getWorksheet(@Path("id") id: Int): WorksheetDetail

    @POST("worksheets/{id}/complete")
    suspend fun completeWorksheet(@Path("id") id: Int, @Body body: CompleteWorksheetRequest)

    @GET("worksheet-templates")
    suspend fun getWorksheetTemplates(): List<WorksheetTemplate>


    @GET("products")
    suspend fun getProducts(@Query("search") search: String = ""): List<Product>

    @POST("tickets/{id}/outtake")
    suspend fun createOuttake(@Path("id") id: Int, @Body body: CreateWorksheetRequest): Map<String, Int>


    @GET("me")
    suspend fun getProfile(): UserProfile

    @PUT("me")
    suspend fun updateProfile(@Body body: UpdateProfileRequest)

    @PUT("me")
    suspend fun registerFcmToken(@Body body: FcmTokenRequest)

    @PUT("me")
    suspend fun registerBiometricKey(@Body body: BiometricKeyRequest)

    @POST("tickets/{id}/charges")
    suspend fun addCharge(@Path("id") id: Int, @Body body: AddChargeRequest)

    @POST("tickets/{id}/worksheets")
    suspend fun createWorksheet(@Path("id") id: Int, @Body body: CreateWorksheetRequest): Map<String, Int>

    @POST("worksheets/{id}/responses")
    suspend fun saveResponses(@Path("id") id: Int, @Body body: SaveResponsesRequest)


    @POST("appointments")
    suspend fun createAppointment(@Body body: CreateAppointmentRequest): Map<String, Int>

    @Headers("Cache-Control: no-store")
    @GET("appointments")
    suspend fun getAppointments(
        @Query("when") when_: String = "future",
        @Query("mine") mine: Int = 0
    ): List<Appointment>

    @Headers("Cache-Control: no-store")
    @GET("clients/{id}/tickets")
    suspend fun getClientTickets(@Path("id") id: Int): List<TicketSummary>

    @Headers("Cache-Control: no-store")
    @GET("clients/{id}/assets")
    suspend fun getClientAssets(@Path("id") id: Int): List<AssetSummary>

    @Headers("Cache-Control: no-store")
    @GET("clients/{id}/locations")
    suspend fun getClientLocations(@Path("id") id: Int): List<ClientLocation>

    @Headers("Cache-Control: no-store")
    @GET("clients/{id}/credentials")
    suspend fun getClientCredentials(@Path("id") id: Int): List<CredentialSummary>

    @Headers("Cache-Control: no-store")
    @GET("clients/{id}/contracts")
    suspend fun getClientContracts(@Path("id") id: Int): List<ClientContract>

    @Headers("Cache-Control: no-store")
    @GET("clients/{id}/files")
    suspend fun getClientFiles(@Path("id") id: Int): List<ClientFile>


    @POST("notifications/{id}/read")
    suspend fun markRead(@Path("id") id: Int)

    @POST("notifications/read-all")
    suspend fun markAllRead()

    @GET("alerts")
    suspend fun getAlerts(
        @Query("status") status: String = "new",
        @Query("severity") severity: String? = null,
        @Query("source") source: String? = null,
        @Query("client_id") clientId: Int? = null
    ): AlertsResponse

    @POST("alerts")
    suspend fun actOnAlert(@Body body: AlertActionRequest)
}

// Extension helpers
suspend fun ApiService.addReply(id: Int, reply: String, type: String = "reply", timeWorked: String? = null, onsite: Boolean = false, statusId: Int? = null) =
    addReply(id, AddReplyRequest(reply, type, timeWorked, if (onsite) 1 else 0, statusId))


// ── Appointments ─────────────────────────────────────────────────────────────
data class Appointment(
    val id: Int,
    @com.google.gson.annotations.SerializedName("ticket_id") val ticketId: Int,
    val number: Int, val subject: String,
    val schedule: String?, @com.google.gson.annotations.SerializedName("schedule_end") val scheduleEnd: String?,
    val onsite: Boolean, val notes: String?,
    val priority: String?, val status: String?,
    @com.google.gson.annotations.SerializedName("status_color") val statusColor: String?,
    val client: String?, @com.google.gson.annotations.SerializedName("assigned_to") val assignedTo: String?,
    // On-site contact + the client's primary location — may all be null (no contact/location
    // on file), which is why the "Drive"/"Call" appointment actions must be conditional.
    @com.google.gson.annotations.SerializedName("contact_name") val contactName: String? = null,
    @com.google.gson.annotations.SerializedName("contact_phone") val contactPhone: String? = null,
    val address: String? = null, val city: String? = null, val state: String? = null,
)

data class CreateAppointmentRequest(
    @com.google.gson.annotations.SerializedName("ticket_id") val ticketId: Int,
    @com.google.gson.annotations.SerializedName("schedule_start") val scheduleStart: String,
    @com.google.gson.annotations.SerializedName("schedule_end") val scheduleEnd: String? = null,
    val onsite: Boolean = false,
    val notes: String = ""
)
