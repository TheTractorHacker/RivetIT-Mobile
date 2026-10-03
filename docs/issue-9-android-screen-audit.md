# Issue #9 Android screen audit

Inventory of RivetIT-Mobile, started 2026-10-01 and updated 2026-10-02. The app is Kotlin/Jetpack Compose with Material 3. This table records the internal-edition routes and code-level state markers. A Pixel 7 / Android 36 emulator connected to a disposable Summit Ridge instance for the limited visual checks marked below; other routes remain unverified. API names remain `client`/`client_id` while the displayed noun is Department.

State markers: `L` loading, `E` empty, `R` retry/error, `—` no explicit marker in the screen file. The global main shell has an offline banner; it does not prove each page handles stale or denied data. `Touched` means code changed in this pass, not acceptance complete.

| Route | Screen file | Direct API calls | State markers | Code pass |
|---|---|---|---|---|
| `setup` | `app/src/main/java/com/foleyit/itflow/ui/screens/auth/ServerSetupScreen.kt` | — | — | Light/dark phone layout checked; certificate flow exercised |
| `login` | `app/src/main/java/com/foleyit/itflow/ui/screens/auth/LoginScreen.kt` | `login`, `passkeyBegin`, `passkeyComplete`, `registerFcmToken` | — | Light/dark phone layout and password sign-in checked; MFA/passkey pending |
| `dashboard` | `app/src/main/java/com/foleyit/itflow/ui/screens/dashboard/DashboardScreen.kt` | `getAlerts`, `getAppointments`, `getDashboard` | LR | Light phone and tablet layouts with data checked; dark/error pending |
| `tickets` | `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/TicketsScreen.kt` | `getSavedTicketViews`, `getTicketCategories`, `getTickets` | LER | Light phone list, retry after API recovery, and 150% text checked; filters/dark/tablet pending |
| `tickets/{id}` | `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/TicketDetailScreen.kt` | `addCharge`, `addReply`, `completeWorksheet`, `createOuttake`, `createWorksheet`, `deleteOuttake`, `deleteReply`, `deleteWorksheet`, `getTicket`, `getTicketCharges`, `getTicketOuttakes`, `getTicketStatuses`, `getTicketWorksheets`, `getWorksheetTemplates`, `updateTicketStatus` | LR | Light phone detail/reply sheet checked; write flows/dark/tablet pending |
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
| `quotes`, `invoices`, `expenses` | Removed in this pass | Removed RivetIT API endpoints | — | Retired; no longer offered in drawer or deep links |
| `notifications` | `app/src/main/java/com/foleyit/itflow/ui/screens/notifications/NotificationsScreen.kt` | `getNotifications`, `markAllRead`, `markRead` | LER | Read actions now wait for server success, show progress/errors, and animate list removal; device QA pending |
| `appointments` | `app/src/main/java/com/foleyit/itflow/ui/screens/appointments/AppointmentsScreen.kt` | `createAppointment`, `getAppointments`, `getTickets` | LER | Review and device QA pending |
| `worksheets/{id}/fill` | `app/src/main/java/com/foleyit/itflow/ui/screens/worksheets/FillWorksheetScreen.kt` | `getWorksheet`, `saveResponses` | LR | Review and device QA pending |
| `outtakes/{id}/sign` | `app/src/main/java/com/foleyit/itflow/ui/screens/worksheets/OuttakeSignScreen.kt` | `getOuttake`, `signOuttake` | LR | Review and device QA pending |
| `tickets/create` | `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/CreateTicketScreen.kt` | `createTicket`, `getClients`, `getTicketCategories` | — | Touched; device QA pending |
| `search` | `app/src/main/java/com/foleyit/itflow/ui/screens/search/SearchScreen.kt` | `search` | LER | Touched; device QA pending |
| `reports/time` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/TimeSummaryScreen.kt` | `getTimeReport` | LR | Touched; device QA pending |
| `reports` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/ReportsHubScreen.kt` | — | — | Light phone layout checked; duplicate top inset fixed; financial links retired |
| `reports/tickets` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/TicketVolumeReportScreen.kt` | `getTicketVolumeReport` | LR | Review and device QA pending |
| `reports/tickets-by-client` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/TicketsByClientReportScreen.kt` | `getTicketsByClientReport` | LER | Touched; device QA pending |
| `reports/time-by-tech` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/TimeByTechReportScreen.kt` | `getTimeByTechReport` | LER | Review and device QA pending |
| `reports/tech-performance` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/TechPerformanceReportScreen.kt` | `getTechPerformanceReport` | LER | Review and device QA pending |
| `reports/overview` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/OverviewReportScreen.kt` | `getOverviewReport` | LR | Review and device QA pending |
| Financial reports | Removed in this pass | Removed or out-of-scope RivetIT API endpoints | — | Retired from the report hub |
| `reports/expiring` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/ExpiringReportScreen.kt` | `getExpiringReport` | LER | Review and device QA pending |
| `reports/csat` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/CsatReportScreen.kt` | `getCsatReport` | LER | Touched; device QA pending |
| `reports/rmm-health` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/RmmHealthReportScreen.kt` | `getRmmHealthReport` | LER | Touched; device QA pending |
| `reports/service-desk` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/ServiceDeskReportScreen.kt` | `getServiceDeskReport` | LR | Review and device QA pending |
| `reports/technician-utilization` | `app/src/main/java/com/foleyit/itflow/ui/screens/reports/TechnicianUtilizationReportScreen.kt` | `getTechUtilizationReport` | LER | Review and device QA pending |
| `scan/barcode` | `app/src/main/java/com/foleyit/itflow/ui/screens/scan/ScanBarcodeScreen.kt` | `getAssets` | — | Review and device QA pending |
| `profile` | `app/src/main/java/com/foleyit/itflow/ui/screens/profile/ProfileScreen.kt` | `getProfile`, `logout`, `registerFcmToken`, `updateProfile` | — | Review and device QA pending |
| `kb` | `app/src/main/java/com/foleyit/itflow/ui/screens/kb/KnowledgeBaseScreen.kt` | `getKbArticles`, `getKbCategories` | LER | Touched; device QA pending |
| `kb/{id}` | `app/src/main/java/com/foleyit/itflow/ui/screens/kb/KbArticleDetailScreen.kt` | `getKbArticle` | LR | Review and device QA pending |
| `alerts` | `app/src/main/java/com/foleyit/itflow/ui/screens/alerts/AlertsScreen.kt` | `actOnAlert`, `getAlerts` | LER | Tablet filter/error/retry UI checked; data and write-path QA pending |

## Emulator findings (2026-10-02)

The disposable Summit Ridge server supplied fictional users and tickets. The screenshots in [docs/qa/issue-9](qa/issue-9/) were taken with `./gradlew :app:assembleDebug -PqaScreenshots=true`; normal beta and release builds still set `FLAG_SECURE`. Setup and sign-in were checked in [light](qa/issue-9/setup-light.png) and [dark](qa/issue-9/setup-dark.png) modes. The [dashboard](qa/issue-9/dashboard-light.png), [ticket list](qa/issue-9/tickets-light.png), [ticket detail](qa/issue-9/ticket-detail-light.png), and [reply sheet](qa/issue-9/reply-sheet-light.png) loaded on a phone. The reply sheet showed separate Internal note and Public reply choices; no reply was submitted.

The reports screen had a doubled top safe-area inset. [Before](qa/issue-9/reports-before-top-inset.png) and [after](qa/issue-9/reports-after-top-inset.png) screenshots show the reclaimed space. The drawer and reports hub no longer offer the finance pages removed from RivetIT's API. A test server initially returned a 404 for tickets; after its route was repaired, the mobile client's cached error persisted. The API client now prevents failed GETs from being cached and clears response cache on account/server changes. The list loaded successfully after replacing that stale cache.

At 150% Android font scale, the fixed-height search field on the ticket list clipped its prompt. [Before](qa/issue-9/tickets-large-before.png) and [after](qa/issue-9/tickets-large-after.png) screenshots show the corrected field. Asset, project, contract, and knowledge-base search fields had the same fixed-height pattern and now grow with text. Their full screen audits remain pending.

The [tablet dashboard](qa/issue-9/dashboard-tablet-light.png) was checked at a 1600×2560 emulator viewport. Its two-column metrics and ticket queue remained legible without overlap. This verifies one dashboard state, not the other tablet routes.

The notification list previously allowed a swipe to disappear before the mark-read request finished and silently ignored write failures. It now keeps the card visible until success, blocks duplicate actions, reports failures with a retryable card, and updates the shell's unread dot. The notifications GET bypasses the disk cache so a subsequent visit reflects server state. No emulator was attached for a visual or network-failure check of this change.

The Alerts page also ignored failed acknowledge/resolve writes. It now shows pending actions and errors, updates an alert only after a successful response, keeps filter results fresh after writes, and lets action buttons wrap on narrow or large-text layouts. On the 1600×2560 Android 36 emulator, the [filters and network-error retry state](qa/issue-9/alerts-tablet-error.png) rendered when the earlier disposable test API was offline. No alert-data or write-path result is claimed; those still need a live disposable test server. The emulator required temporary KVM access for this account, removed after the check.

This is a partial audit. No restricted-role, comprehensive tablet, TalkBack, rotation, reduced-motion, deep-link, write-path, or representative-device performance result is claimed here.

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

- Capture baseline and after screenshots for remaining routes on a representative phone and tablet in light/dark themes.
- Run every route with a restricted account and with related modules disabled; check missing permissions and expired sessions.
- Exercise large text, TalkBack, keyboard/insets, reduced motion, rotation/process recreation, slow/offline/rate-limited network, and push deep links.
- Verify ticket creation, notes, public replies, attachments, time entry, and other writes against a test server, including duplicate taps and uncertain responses.
- Measure startup, ticket opening, scrolling, and memory on a mid-range device. Record defects and link follow-up issues before marking this issue complete.
