# Mobile features: approvals, requests, tasks, attachments

Added on branch `mobile/approvals-requests`. All four need a RivetIT server that ships `api/v1/approvals.php`, `service_catalog.php`, `workflow_tasks.php` and `ticket_attachments.php` (see the server's `docs/MOBILE_API.md`). Against an older server the entries appear but show an error with Retry; the ticket Attachments section simply stays hidden when the endpoint is missing (404).

## Screens

| Screen | Route | What it does |
|---|---|---|
| Approvals | `approvals` | Approvals waiting on you (service-request steps and workflow approval tasks). Pull to refresh, empty state "Nothing waiting on you", last result kept in memory, error banner with Retry. Tap an item for the detail sheet: requester, risk chip, step, the answers, "Open ticket", comment field, Approve / Reject (reject needs a comment). Buttons disable while the decision is sent; a snackbar and confirm/reject haptic report the result, the list and the drawer badge update. |
| Approval deep link | `approvals/{kind}/{id}` | Opens Approvals with that item's sheet showing; says so if it is no longer waiting. |
| New request | `requests` | Service catalog: search, "Popular this month" and "Recently used" shelves, all requests. A request opens a form built from the item's fields (text, multi-line, select, checkbox, date picker, number). `show_if` is evaluated live by `ShowIfEvaluator` (same rules as the server and web form: `equals`, `in`, `not_empty`, chained, hidden fields are cleared, not required, not sent). Result screen: "Request created" or "Waiting for approval" with Open ticket. Also reachable from the Tickets create button. |
| My tasks | `tasks` | Onboarding/offboarding tasks assigned to you, grouped by run/person, with Overdue / Due soon chips. Blocked tasks are dimmed and disabled with "Waiting for: ...". Manual tasks: Complete, or Skip with a required reason. Approval tasks show "Needs approval" and open Approvals. Action tasks are read-only (a failed one explains itself). Unknown statuses render read-only. |
| Ticket attachments | ticket detail | "Attachments" card (icon by type, name, size). Tap downloads through the authenticated endpoint into the app cache and opens it with the system viewer through a FileProvider (type taken from the allowlisted extension, never from the server). The reply sheet has Attach: Camera, Photo picker, Files; chips show name and size with Remove; files upload after the reply is stored with per-file progress, Retry on failure, and no re-send of finished files. |

## Permissions and visibility

- Approvals, My tasks: any login that is not `limited` (from `/me`). Module-only logins see none of these. The server answers 404 to a non-approver.
- New request: not limited and Tickets write access (it creates a ticket).
- Attachments list: Tickets view; Attach button: Tickets write.
- Android: no storage permission. Photo picker and document picker are system UIs. The camera needs the CAMERA runtime permission (the manifest already declares it for barcode scanning); denial is reported, not thrown.

## Push and notifications

- Push data `{"type":"approval","kind":"catalog_request"|"workflow_task","id":N}` opens `approvals/<kind>/<id>`; malformed values open the Approvals list. Other pushes keep using the allowlisted `action` route.
- Notification-list entries with `type: "approval"` (plus `kind`, `ref_id`) open the same screen when tapped.

## Attachment rules (client side)

Extension allowlist is the web agent list (jpg jpeg gif png webp pdf txt md doc docx odt csv xls xlsx ods pptx odp zip tar gz xml msg json wav mp3 ogg mov mp4 av1 ovpn); middle segments such as `.php.` or `.exe.` are refused like the server does; up to 10 files; 10 MB each (the API does not expose a limit yet). The server re-checks everything.

## Security notes

Approvals, tasks and attachments are requested `no-store`, so they never reach the HTTP disk cache. The "cached last result" is process memory only and is cleared on sign-out or account/server change, which also deletes downloaded and camera temp files (they are also wiped at process start and aged out when the ticket screen closes). The FileProvider exposes only `cache/attachments/` and `cache/camera/`.

## Known gaps

- Requests are filed for no department (`client_id` null), which the server treats as an internal request; there is no department picker.
- Queued attachments and the reply draft attachments do not survive rotation or process death.
- No in-app image viewer; images open in the system viewer.
- Upload limit is a client constant (10 MB) until the API exposes it.
- Not verified on a physical device: push delivery, TalkBack announcements, performance, landscape.
