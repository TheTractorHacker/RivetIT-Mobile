# Issue #9 Android screen audit

Static inventory of the `main` branch of RivetIT-Mobile on 2026-10-01. The app is Kotlin/Jetpack Compose with Material 3. This table records the internal-edition routes and code-level state markers; **none of these routes is marked visually verified** because this workspace has no Android emulator/device or test account. API names remain `client`/`client_id` while the displayed noun is Department.

State markers: `L` loading, `E` empty, `R` retry/error, `—` no explicit marker in the screen file. The global main shell has an offline banner; it does not prove each page handles stale or denied data. `Touched` means code changed in this pass, not acceptance complete.

| Route | Screen file | Direct API calls | State markers | Code pass |
|---|---|---|---|---|
| `setup` | `app/src/main/java/com/foleyit/itflow/ui/screens/auth/ServerSetupScreen.kt` | — | — | Review and device QA pending |
| `login` | `app/src/main/java/com/foleyit/itflow/ui/screens/auth/LoginScreen.kt` | `login`, `passkeyBegin`, `passkeyComplete`, `registerFcmToken` | — | Review and device QA pending |
| `dashboard` | `app/src/main/java/com/foleyit/itflow/ui/screens/dashboard/DashboardScreen.kt` | `getAlerts`, `getAppointments`, `getDashboard` | LR | Review and device QA pending |
| `tickets` | `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/TicketsScreen.kt` | `getSavedTicketViews`, `getTicketCategories`, `getTickets` | LER | Touched; device QA pending |
| `tickets/{id}` | `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/TicketDetailScreen.kt` | `addCharge`, `addReply`, `completeWorksheet`, `createOuttake`, `createWorksheet`, `deleteOuttake`, `deleteReply`, `deleteWorksheet`, `getProducts`, `getTicket`, `getTicketCharges`, `getTicketOuttakes`, `getTicketStatuses`, `getTicketWorksheets`, `getWorksheetTemplates`, `updateTicketStatus` | LR | Touched; device QA pending |
| `tickets/{id}/chat` | `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/TicketChatScreen.kt` | `getChatMessages`, `sendChatMessage` | L | Touched; device QA pending |
| `clients` | `app/src/main/java/com/foleyit/itflow/ui/screens/clients/ClientsScreen.kt` | `getClients` | LER | Touched; device QA pending |
| `clients/{id}` | `app/src/main/java/com/foleyit/itflow/ui/screens/clients/ClientDetailScreen.kt` | `getClient`, `getClientAssets`, `getClientContracts`, `getClientCredentials`, `getClientFiles`, `getClientLocations`, `getClientTickets` | LER | Touched; device QA pending |
| `assets` | `app/src/main/java/com/foleyit/itflow/ui/screens/assets/AssetsScreen.kt` | `getAssetTypes`, `getAssets` | LER | Touched; device QA pending |
| `assets/{id}` | `app/src/main/java/com/foleyit/itflow/ui/screens/assets/AssetDetailScreen.kt` | `getAsset` | LR | Touched; device QA pending |
| `projects` | `app/src/main/java/com/foleyit/itflow/ui/screens/projects/ProjectsScreen.kt` | `getProjects` | LER | Touched; device QA pending |
| `projects/{id}` | `app/src/main/java/com/foleyit/itflow/ui/screens/projects/ProjectDetailScreen.kt` | `getProject`, `toggleMilestone`, `toggleTask` | LR | Review and device QA pending |
| `contracts` | `app/src/main/java/com/foleyit/itflow/ui/screens/contracts/ContractsScreen.kt` | `getContracts` | LER | Touched; device QA pending |
| `contracts/{id}` | `app/src/main/java/com/foleyit/itflow/ui/screens/contracts/ContractDetailScreen.kt` | `getContract` | LR | Review and device QA pending |
| `credentials` | `app/src/main/java/com/foleyit/itflow/ui/screens/credentials/CredentialsScreen.kt` | `getCredentials` | LER | Touched; device QA pending |
| `credentials/{id}` | `app/src/main/java/com/foleyit/itflow/ui/screens/credentials/CredentialDetailScreen.kt` | `getCredential`, `passkeyBegin`, `registerBiometricKey` | LR | Review and device QA pending |
| `quotes` | `app/src/main/java/com/foleyit/itflow/ui/screens/quotes/QuotesScreen.kt` | `getQuotes` | LER | Touched; device QA pending |
| `quotes/{id}` | `app/src/main/java/com/foleyit/itflow/ui/screens/quotes/QuoteDetailScreen.kt` | `getQuote` | LR | Review and device QA pending |
| `invoices` | `app/src/main/java/com/foleyit/itflow/ui/screens/invoices/InvoicesScreen.kt` | `getInvoices` | LER | Touched; device QA pending |
| `invoices/{id}` | `app/src/main/java/com/foleyit/itflow/ui/screens/invoices/InvoiceDetailScreen.kt` | `getInvoice` | LR | Review and device QA pending |
| `expenses` | `app/src/main/java/com/foleyit/itflow/ui/screens/expenses/ExpensesScreen.kt` | `getExpenses` | LER | Touched; device QA pending |
| `expenses/add` | `app/src/main/java/com/foleyit/itflow/ui/screens/expenses/AddExpenseScreen.kt` | `createExpense` | — | Review and device QA pending |
| `notifications` | `app/src/main/java/com/foleyit/itflow/ui/screens/notifications/NotificationsScreen.kt` | `getNotifications`, `markAllRead`, `markRead` | LER | Review and device QA pending |
| `appointments` | `app/src/main/java/com/foleyit/itflow/ui/screens/appointments/AppointmentsScreen.kt` | `createAppointment`, `getAppointments`, `getTickets` | LER | Review and device QA pending |
| `worksheets/{id}/fill` | `app/src/main/java/com/foleyit/itflow/ui/screens/worksheets/FillWorksheetScreen.kt` | `getWorksheet`, `saveResponses` | LR | Review and device QA pending |
| `outtakes/{id}/sign` | `app/src/main/java/com/foleyit/itflow/ui/screens/worksheets/OuttakeSignScreen.kt` | `getOuttake`, `signOuttake` | LR | Review and device QA pending |
| `tickets/create` | `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/CreateTicketScreen.kt` | `createTicket`, `getClients`, `getTicketCategories` | — | Touched; device QA pending |
| `search` | `app/src/main/java/com/foleyit/itflow/ui/screens/search/SearchScreen.kt` | `search` | LER | Touched; device QA pending |
| `reports/time` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/TimeSummaryScreen.kt` | `getTimeReport` | LR | Touched; device QA pending |
| `reports` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/ReportsHubScreen.kt` | — | — | Touched; device QA pending |
| `reports/tickets` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/TicketVolumeReportScreen.kt` | `getTicketVolumeReport` | LR | Review and device QA pending |
| `reports/tickets-by-client` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/TicketsByClientReportScreen.kt` | `getTicketsByClientReport` | LER | Touched; device QA pending |
| `reports/time-by-tech` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/TimeByTechReportScreen.kt` | `getTimeByTechReport` | LER | Review and device QA pending |
| `reports/tech-performance` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/TechPerformanceReportScreen.kt` | `getTechPerformanceReport` | LER | Review and device QA pending |
| `reports/overview` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/OverviewReportScreen.kt` | `getOverviewReport` | LR | Review and device QA pending |
| `reports/unbilled-tickets` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/UnbilledTicketsReportScreen.kt` | `getUnbilledTicketsReport` | LR | Review and device QA pending |
| `reports/clients-with-balance` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/ClientsWithBalanceReportScreen.kt` | `getClientsWithBalanceReport` | LER | Touched; device QA pending |
| `reports/income-summary` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/IncomeSummaryReportScreen.kt` | `getIncomeSummaryReport` | LER | Review and device QA pending |
| `reports/expense-summary` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/ExpenseSummaryReportScreen.kt` | `getExpenseSummaryReport` | — | Review and device QA pending |
| `reports/profit-loss` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/ProfitLossReportScreen.kt` | `getProfitLossReport` | LR | Review and device QA pending |
| `reports/expiring` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/ExpiringReportScreen.kt` | `getExpiringReport` | LER | Review and device QA pending |
| `reports/csat` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/CsatReportScreen.kt` | `getCsatReport` | LER | Touched; device QA pending |
| `reports/rmm-health` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/RmmHealthReportScreen.kt` | `getRmmHealthReport` | LER | Touched; device QA pending |
| `reports/service-desk` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/ServiceDeskReportScreen.kt` | `getServiceDeskReport` | LR | Review and device QA pending |
| `reports/technician-utilization` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/TechnicianUtilizationReportScreen.kt` | `getTechUtilizationReport` | LER | Review and device QA pending |
| `scan/barcode` | `app/src/main/java/com/foleyit/itflow/ui/screens/scan/ScanBarcodeScreen.kt` | `getAssets` | — | Review and device QA pending |
| `profile` | `app/src/main/java/com/foleyit/itflow/ui/screens/profile/ProfileScreen.kt` | `getProfile`, `logout`, `registerFcmToken`, `updateProfile` | — | Review and device QA pending |
| `kb` | `app/src/main/java/com/foleyit/itflow/ui/screens/kb/KnowledgeBaseScreen.kt` | `getKbArticles`, `getKbCategories` | LER | Touched; device QA pending |
| `kb/{id}` | `app/src/main/java/com/foleyit/itflow/ui/screens/kb/KbArticleDetailScreen.kt` | `getKbArticle` | LR | Review and device QA pending |
| `alerts` | `app/src/main/java/com/foleyit/itflow/ui/screens/alerts/AlertsScreen.kt` | `actOnAlert`, `getAlerts` | LER | Review and device QA pending |

## Sheets and dialogs

These are code inventory entries, not visual verification. Names/roles should be checked on a device in light and dark modes.

| Screen file | Sheet/dialog declarations or calls |
|---|---|
| `app/src/main/java/com/foleyit/itflow/ui/screens/profile/ProfileScreen.kt` | `AlertDialog` line 193, `AlertDialog` line 207, `AlertDialog` line 239, `ModalBottomSheet` line 276 |
| `app/src/main/java/com/foleyit/itflow/ui/screens/appointments/AppointmentsScreen.kt` | `DatePickerDialog` line 293, `TimePickerDialog` line 295, `ModalBottomSheet` line 303 |
| `app/src/main/java/com/foleyit/itflow/ui/screens/scan/ScanBarcodeScreen.kt` | `AlertDialog` line 135 |
| `app/src/main/java/com/foleyit/itflow/ui/screens/auth/ServerSetupScreen.kt` | `AlertDialog` line 109 |
| `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/TicketDetailScreen.kt` | `AlertDialog` line 189, `ModalBottomSheet` line 212, `ModalBottomSheet` line 524, `AlertDialog` line 679, `AlertDialog` line 958, `AlertDialog` line 1019, `ModalBottomSheet` line 1234, `ModalBottomSheet` line 1285 |
| `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/TicketsScreen.kt` | `ModalBottomSheet` line 306 |
| `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/CreateTicketScreen.kt` | `AlertDialog` line 62, `ModalBottomSheet` line 104, `ModalBottomSheet` line 128 |

## Remaining verification

- Capture baseline and after screenshots on a representative phone and tablet in light/dark themes.
- Run every route with a restricted account and with related modules disabled; check missing permissions and expired sessions.
- Exercise large text, TalkBack, keyboard/insets, reduced motion, rotation/process recreation, slow/offline/rate-limited network, and push deep links.
- Verify ticket creation, notes, public replies, attachments, time entry, and other writes against a test server, including duplicate taps and uncertain responses.
- Measure startup, ticket opening, scrolling, and memory on a mid-range device. Record defects and link follow-up issues before marking this issue complete.
