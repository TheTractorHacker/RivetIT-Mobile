# RivetIT-Mobile

[![Build APK](https://github.com/TheTractorHacker/RivetIT-Mobile/actions/workflows/build.yml/badge.svg)](https://github.com/TheTractorHacker/RivetIT-Mobile/actions/workflows/build.yml)
![Platform](https://img.shields.io/badge/platform-Android-3ddc84)

The native Android companion for [RivetIT](https://github.com/TheTractorHacker/RivetIT), built with Kotlin, Jetpack Compose, and Material 3. This is the internal IT edition: it uses Departments in the UI and keeps the existing `client`/`client_id` API fields for server compatibility. Billing screens follow the connected server's module settings.

## Features

Tickets, public replies and internal notes, live chat, appointments, assets and barcode scanning, departments, credentials, projects, contracts, worksheets, knowledge base, alerts, notifications, search, reports, and optional billing modules.

## Install and setup

Download a beta APK from the [Actions workflow](https://github.com/TheTractorHacker/RivetIT-Mobile/actions/workflows/build.yml) or a published release. Enter your RivetIT server URL, then sign in with a technician account. Source builds require Android SDK 36 and a valid `app/google-services.json` for Firebase push notifications; the checked-in file is only a placeholder.

The application ID remains `com.foleyit.itflow.internal` (`.beta` for debug builds) so existing installations can update without losing their local settings. The legacy Java/Kotlin package namespace and API route names are also retained for compatibility.

## Development

`main` is the RivetIT-Mobile branch. Pushes run lint and unit tests and produce a beta APK. Version tags also trigger a signed release build when the repository's signing secrets are configured. Run local checks with `./gradlew :app:testDebugUnitTest :app:lintDebug`.

[Issue #9 screen audit](docs/issue-9-android-screen-audit.md) tracks UI review and device verification. The inherited `docs/screenshots` images predate this branding and should be replaced after device testing.

RivetIT-Mobile is based on ITFlow and is not the official ITFlow app.
