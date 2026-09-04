# ITFlow Internal IT — Android App

[![Latest release](https://img.shields.io/github/v/release/TheTractorHacker/itflow-internal-it-app?include_prereleases&label=release)](https://github.com/TheTractorHacker/itflow-internal-it-app/releases/latest)
[![Build APK](https://github.com/TheTractorHacker/itflow-internal-it-app/actions/workflows/build.yml/badge.svg)](https://github.com/TheTractorHacker/itflow-internal-it-app/actions/workflows/build.yml)
![Platform](https://img.shields.io/badge/platform-Android-3ddc84)

> **By [TractorHacker](https://github.com/TheTractorHacker)** — the native Android companion for [ITFlow — Internal IT Edition](https://github.com/TheTractorHacker/ITFlow-Internal-IT), a fork of ITFlow repurposed for internal IT teams (one organization, many departments, no client billing) rather than MSPs.
> This is a fork of [ITFlow MSP Edition's Android app](https://github.com/TheTractorHacker/itflow-msp-app). Not the official ITFlow app and not affiliated with or endorsed by ITFlow LLC.

Built with Kotlin + Jetpack Compose + Material 3.

---

## Screenshots

`docs/screenshots/*.png` are inherited from the MSP app fork and still show its old branding/copy ("ITFlow MSP", "Client") — regenerating them needs a live Internal IT server to capture against, which doesn't exist yet. Treat them as stale until replaced.

---

## Features

- **Dashboard** — open ticket counts, recent activity, and system alerts at a glance
- **Tickets** — view, reply, change status, assign technicians, and add time charges (when the ticket-charges module is enabled)
- **Assets** — browse department assets with full detail view; scan barcodes and QR codes to look up assets instantly
- **Departments** — full department list with contacts, locations, credentials, and contracts
- **Worksheets** — view and fill out worksheet responses from your phone
- **Global Search** — search across tickets, departments, and assets in one place
- **Push Notifications** — get notified on new tickets and assignments via Firebase
- **Appointments** — view and manage scheduled on-site and remote appointments
- **Billing UI** (Add Charge, Quotes, Invoices, Expenses) — hidden automatically when the server has these modules disabled, matching the web edition's billing-off-by-default convention

---

## Requirements

- Android Studio Hedgehog or newer
- Android SDK 35
- **ITFlow — Internal IT Edition** server ([TheTractorHacker/ITFlow-Internal-IT](https://github.com/TheTractorHacker/ITFlow-Internal-IT)) — no live server exists yet at the time of this app's first commits; this app has been built and verified to compile against that expectation but not yet tested end-to-end against a real deployment
- `google-services.json` from your Firebase project for push notifications — the checked-in file is currently a **placeholder** (push/crash-reporting no-op) until a real Firebase project is registered for `com.foleyit.itflow.internal` / `com.foleyit.itflow.internal.beta` and the real file substituted

---

## Setup

1. Clone the repo and open in Android Studio
2. Replace `app/google-services.json` with your real Firebase project's file (see Requirements above)
3. Run the app — enter your ITFlow Internal IT server URL on first launch
4. Log in with your technician credentials

---

## Branches

| Branch | Purpose |
|--------|---------|
| `beta` | Active development — new features land here first |

No `release` branch/tag has been cut yet — this app hasn't been verified against a live server. See the web edition's own branch convention for what to expect once one exists.

---

## Related

- Web app (this app's backend): [TheTractorHacker/ITFlow-Internal-IT](https://github.com/TheTractorHacker/ITFlow-Internal-IT)
- Forked from: [TheTractorHacker/itflow-msp-app](https://github.com/TheTractorHacker/itflow-msp-app) (the MSP edition's Android app)
- Upstream ITFlow: [itflow-org/itflow](https://github.com/itflow-org/itflow)
