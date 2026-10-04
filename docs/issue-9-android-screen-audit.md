# Issue #9 Android screen audit

Inventory of RivetIT-Mobile, started 2026-10-01 and updated 2026-10-03. The app is Kotlin/Jetpack Compose with Material 3. This table records the internal-edition routes and code-level state markers. A Pixel 7 / Android 36 emulator connected to a disposable Summit Ridge instance for the limited visual checks marked below; other routes remain unverified. API names remain `client`/`client_id` while the displayed noun is Department.

State markers: `L` loading, `E` empty, `R` retry/error, `—` no explicit marker in the screen file. The global main shell has an offline banner; it does not prove each page handles stale or denied data. `Touched` means code changed in this pass, not acceptance complete.

| Route | Screen file | Direct API calls | State markers | Code pass |
|---|---|---|---|---|
| `setup` | `app/src/main/java/com/foleyit/itflow/ui/screens/auth/ServerSetupScreen.kt` | — | — | Light/dark phone layout checked; certificate flow exercised |
| `login` | `app/src/main/java/com/foleyit/itflow/ui/screens/auth/LoginScreen.kt` | `login`, `passkeyBegin`, `passkeyComplete`, `registerFcmToken` | — | Light/dark phone layout and password sign-in checked; MFA/passkey pending |
| `dashboard` | `app/src/main/java/com/foleyit/itflow/ui/screens/dashboard/DashboardScreen.kt` | `getAlerts`, `getAppointments`, `getDashboard` | LR | Light phone and tablet layouts with data checked; dark/error pending |
| `tickets` | `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/TicketsScreen.kt` | `getSavedTicketViews`, `getTicketCategories`, `getTickets` | LER | Light phone list, retry after API recovery, and 150% text checked; filters/dark/tablet pending |
| `tickets/{id}` | `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/TicketDetailScreen.kt` | `addCharge`, `addReply`, `completeWorksheet`, `createOuttake`, `createWorksheet`, `deleteOuttake`, `deleteReply`, `deleteWorksheet`, `getTicket`, `getTicketCharges`, `getTicketOuttakes`, `getTicketStatuses`, `getTicketWorksheets`, `getWorksheetTemplates`, `updateTicketStatus` | LR | Light phone detail/reply sheet and tablet mock worksheet/outtake failure, duplicate-tap, retry, and success checked; live backend and other writes pending |
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
| `appointments` | `app/src/main/java/com/foleyit/itflow/ui/screens/appointments/AppointmentsScreen.kt` | `createAppointment`, `getAppointments`, `getTickets` | LER | Tablet mock-data, duplicate-tap, write-failure, and successful retry checked; backend permissions/lifecycle pending |
| `worksheets/{id}/fill` | `app/src/main/java/com/foleyit/itflow/ui/screens/worksheets/FillWorksheetScreen.kt` | `getWorksheet`, `saveResponses` | LR | Review and device QA pending |
| `outtakes/{id}/sign` | `app/src/main/java/com/foleyit/itflow/ui/screens/worksheets/OuttakeSignScreen.kt` | `getOuttake`, `signOuttake` | LR | Review and device QA pending |
| `tickets/create` | `app/src/main/java/com/foleyit/itflow/ui/screens/tickets/CreateTicketScreen.kt` | `createTicket`, `getClients`, `getTicketCategories` | LR | Tablet mock department search, category retry, ticket write failure and successful retry checked; live backend pending |
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
| `alerts` | `app/src/main/java/com/foleyit/itflow/ui/screens/alerts/AlertsScreen.kt` | `actOnAlert`, `getAlerts` | LER | Tablet mock-data, write-failure, retry, and status tabs checked; backend permissions/lifecycle pending |

## Emulator findings (2026-10-02)

The disposable Summit Ridge server supplied fictional users and tickets. The screenshots in [docs/qa/issue-9](qa/issue-9/) were taken with `./gradlew :app:assembleDebug -PqaScreenshots=true`; normal beta and release builds still set `FLAG_SECURE`. Setup and sign-in were checked in [light](qa/issue-9/setup-light.png) and [dark](qa/issue-9/setup-dark.png) modes. The [dashboard](qa/issue-9/dashboard-light.png), [ticket list](qa/issue-9/tickets-light.png), [ticket detail](qa/issue-9/ticket-detail-light.png), and [reply sheet](qa/issue-9/reply-sheet-light.png) loaded on a phone. The reply sheet showed separate Internal note and Public reply choices; no reply was submitted.

The reports screen had a doubled top safe-area inset. [Before](qa/issue-9/reports-before-top-inset.png) and [after](qa/issue-9/reports-after-top-inset.png) screenshots show the reclaimed space. The drawer and reports hub no longer offer the finance pages removed from RivetIT's API. A test server initially returned a 404 for tickets; after its route was repaired, the mobile client's cached error persisted. The API client now prevents failed GETs from being cached and clears response cache on account/server changes. The list loaded successfully after replacing that stale cache.

At 150% Android font scale, the fixed-height search field on the ticket list clipped its prompt. [Before](qa/issue-9/tickets-large-before.png) and [after](qa/issue-9/tickets-large-after.png) screenshots show the corrected field. Asset, project, contract, and knowledge-base search fields had the same fixed-height pattern and now grow with text. Their full screen audits remain pending.

The [tablet dashboard](qa/issue-9/dashboard-tablet-light.png) was checked at a 1600×2560 emulator viewport. Its two-column metrics and ticket queue remained legible without overlap. This verifies one dashboard state, not the other tablet routes.

The notification list previously allowed a swipe to disappear before the mark-read request finished and silently ignored write failures. It now keeps the card visible until success, blocks duplicate actions, reports failures with a retryable card, and updates the shell's unread dot. The notifications GET bypasses the disk cache so a subsequent visit reflects server state. No emulator was attached for a visual or network-failure check of this change.

The Alerts page also ignored failed acknowledge/resolve writes. It now shows pending actions and errors, updates an alert only after a successful response, keeps filter results fresh after writes, and lets action buttons wrap on narrow or large-text layouts. On the 1600×2560 Android 36 emulator, the [filters and network-error retry state](qa/issue-9/alerts-tablet-error.png) rendered when the earlier disposable test API was offline. A temporary HTTPS mock using that test certificate then supplied [two fictional alerts](qa/issue-9/alerts-tablet-data.png). Its first backup Resolve returned HTTP 503: the [error appeared and the alert remained in New](qa/issue-9/alerts-tablet-write-error.png). A second Resolve returned 200, removed the alert from New, and showed it in Resolved. Acknowledge returned 200 and moved the RMM alert from New to Acked. These results verify the client interaction against a mock, not backend permissions or real alert integrations. Rotation, process recreation, back-navigation filter persistence, phone size, and live backend writes remain unverified. Temporary KVM access was removed after the check.

The appointment sheet previously closed before `createAppointment` returned, so a failed write lost the draft. It now remains open with the entered values and a useful error, disables repeat submission while a request is in flight, checks that an optional end time follows the start, and closes only after success. A network failure is treated as an uncertain outcome with a prompt to check the list before retrying. On the 1600×2560 Android 36 emulator, two quick Create taps produced one POST to a temporary HTTPS mock. Its HTTP 503 response left the ticket and draft visible with an [inline error](qa/issue-9/appointments-tablet-write-error.png). A second Create returned 201, closed the sheet, and displayed the [new appointment](qa/issue-9/appointments-tablet-created.png). These results verify the client flow against fictional data; live backend writes, restricted roles, rotation, and process recreation remain unverified.

The ticket detail page also dismissed its worksheet and outtake sheets before the create request finished. Both sheets now block repeat taps, show write failures in place, and navigate only after a returned form ID. The worksheet template picker distinguishes loading, empty, and failed requests and offers Retry. On the same tablet emulator with a fictional ticket and template, two quick taps sent one worksheet POST; HTTP 503 left the [template sheet and error visible](qa/issue-9/worksheet-tablet-write-error.png), and a 201 retry opened the fill page and showed the [new worksheet on return](qa/issue-9/worksheet-tablet-created.png). The outtake flow likewise sent one POST from two taps; HTTP 503 left its [sheet open](qa/issue-9/outtake-tablet-write-error.png), and a 201 retry opened the sign page and showed the [new form on return](qa/issue-9/outtake-tablet-created.png). These are client checks against a mock, not live backend permission or signing checks.

Ticket creation previously showed only the first 30 departments from the API, with no way to find later ones. Its department picker now searches the server by name, uses an expanded scrollable sheet, and offers Retry on loading failure. The category picker also shows loading and failed states with Retry instead of silently disappearing. In a 1600×2560 Android 36 emulator, a fictional API returned 36 departments; searching `36` found and selected [Department 36](qa/issue-9/ticket-create-department-search-tablet.png), which the original first-page picker could not reach. The mock returned HTTP 503 for categories, and the [error and Retry control](qa/issue-9/ticket-create-category-error-tablet.png) appeared; retry loaded Hardware. Live backend creation, restricted roles, and phone-size layout remain unverified.

The same emulator then checked ticket creation against a second fictional API. The ticket list initially cached an empty response. A create POST returned HTTP 503 and left the subject and error on the form. A retry returned 201 with ID 91; the app opened ticket #91 directly. Back navigation fetched the ticket list again and showed #91 instead of the cached empty list. The app now clears only cached ticket responses after a confirmed create. This verifies the client flow against a mock, not live backend permissions or notification effects.

This is a partial audit. No restricted-role, comprehensive tablet, TalkBack, rotation, reduced-motion, deep-link, write-path, or representative-device performance result is claimed here.

## Live-backend pass (2026-10-04)

First pass against a live fictional backend (a Summit Ridge demo server running the RivetIT API) rather than a mock, on a Pixel 7 / Android 36 emulator. A QA build (`-PqaScreenshots=true`) was used for captures. Representative screenshots are in [qa/issue-9/live](qa/issue-9/live/).

**Routes walked:** tickets, ticket detail, ticket chat, departments, department detail, assets, asset detail, projects, project detail, contracts, contract detail, credentials, notifications, appointments, search, reports hub, profile, knowledge base, KB article and alerts, in light and dark phone layouts as an administrator. None showed an error or empty state with data present.

| Finding | Status |
|---|---|
| Rotating the screen (or any activity recreation) re-applied the launching intent's `deep_link_route`, so a user opened from a notification was thrown back to that destination and lost an unsent draft. `MainActivity` now applies the extra only on a first launch and clears it once consumed. | Fixed. Verified: a create-ticket draft survives portrait -> landscape -> portrait ([landscape capture](qa/issue-9/live/create-ticket-landscape-draft.png)) and process death (background, `am kill`, reopen from recents). |
| Credentials stacked its own header under the main app bar (it was listed in `ROOT_ROUTES`). | Fixed and verified. |
| The credentials gate did nothing on a device with no strong biometric enrolled. It now says what is missing and offers the system enrollment screen; the check stays `BIOMETRIC_STRONG`. | Fixed and verified. |
| The `scan` deep link was in the allowlist but has no destination (the route is `scan/barcode`). `DeepLinks.resolve` maps it; bare `worksheets`/`outtakes` resolve to nothing. | Fixed and verified (opens the scanner and its camera-permission prompt). |
| The app ignored `permissions` and `is_admin` from `/me`: a read-only, single-department user saw every drawer item, New Ticket, the reply bar, timer, status picker and add buttons, and met 403s. A `Capabilities` model now hides unusable items; credentials, KB and alerts routes explain themselves when reached by deep link. Servers that report no permissions keep the old behaviour. | Fixed. Verified on device with a read-only, one-department account; the server independently returns 403 for that account's reply and ticket-create calls and hides out-of-scope departments. |
| At 200% text: the tickets search placeholder wrapped letter by letter, bottom-nav labels clipped and ran together, the profile email overflowed, and the Alerts "Resolved" tab was cut off. | Fixed and verified on device ([tickets](qa/issue-9/live/tickets-200pct-text.png), [profile](qa/issue-9/live/profile-200pct-text.png)). |
| A knowledge-base article image showed its alt text with a broken-image box. | Seed content points at a file that does not exist; not an app defect. |
| Ticket-detail error state: a failed load showed the raw `HTTP 500 Internal Server Error` text and still showed the reply bar. | Fixed and verified (backend stopped, then retried): it now shows the shared friendly message with Retry and hides the reply bar until the ticket loads. |
| Tablet (1600x2560): nothing clips, but phone layouts simply stretch to full width (single-column lists, very wide forms). | Open design follow-up: constrain content width and consider list/detail. |

**Write flows verified live:** an internal note and a public reply were each stored with the right visibility ([internal](qa/issue-9/live/reply-sheet-internal-note.png), [public](qa/issue-9/live/reply-sheet-public-reply.png)); the submit label and helper text change with the choice. Two near-simultaneous taps on submit created one note. With the backend stopped the sheet stayed open with the draft and said the outcome could not be confirmed ([capture](qa/issue-9/live/reply-failed-draft-kept.png)); after the backend returned, one retry created exactly one note. The "Mine" ticket filter survived opening a ticket and going back. Opening a deep link while signed out, then signing in, landed on the linked ticket.

**Baseline performance (emulator only, software rendering):** cold start 2.7-2.8 s over five runs; about 141 MB total PSS after a few screens; scrolling the ticket list rendered 69 frames with a 65 ms median frame time, which reflects the software renderer, not a device. These are not a mid-range-device comparison.

**Still not covered:** a mid-range physical device for performance, TalkBack (a `uiautomator` scan of unlabeled controls is not usable here because it cannot see merged Compose semantics), reduced-motion behaviour, time-entry and status-change writes, restricted-role write behaviour beyond the server refusal above, attachment upload, push notification delivery, and the tablet/landscape layouts of every route. This issue should stay open for those.

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
