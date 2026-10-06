# RivetIT for iOS

Native iOS companion for [RivetIT](https://github.com/TheTractorHacker/RivetIT), mirroring the Android app screen for
screen. The design reference is [`docs/ios-mockup/index.html`](../docs/ios-mockup/index.html) (28 screens, light and dark).

- **UI:** SwiftUI, iOS 16+, iPhone and iPad.
- **Logic:** `RivetCore`, a Swift package with no UI dependencies (models, API client, certificate trust, permissions,
  deep links, formatting). It builds on Linux and macOS.
- **Project:** generated with [XcodeGen](https://github.com/yonaskolb/XcodeGen) from `project.yml`.

## Build

```sh
brew install xcodegen
cd ios
xcodegen generate          # creates RivetIT.xcodeproj (git-ignored)
open RivetIT.xcodeproj     # run on a simulator or device
swift test                 # RivetCore unit tests (also runs on Linux: docker run --rm -v "$PWD":/pkg -w /pkg swift:5.10-jammy swift test)
```

Set your own development team in Xcode to run on a device. The app talks to `<server>/api/v1/`; point it at your
RivetIT server (a self-signed certificate is supported, see below).

## What has and has not been verified

| Part | Status |
|---|---|
| `RivetCore` (models, API client, errors, permissions, deep links, dates, certificate reader) | **Built and unit-tested on Linux (44 tests).** Models are decoded from real responses of a RivetIT demo server. |
| SwiftUI app (`App/`) | **Syntax-checked only.** It has never been compiled or run: it needs Xcode, and the machine it was written on is Linux. The macOS job in `.github/workflows/ios.yml` is the first real compile; expect to fix some compile errors and visual details on first run. |
| Certificate pinning / trust flow, Face ID lock, barcode scanner | Written against Apple APIs; untested on a device. |

## Screens (parity with Android)

Done: server setup with certificate confirmation, sign in (incl. authentication code), dashboard, side menu, tickets
(search, filters, saved views, open/closed), ticket detail, reply / internal note / time / status sheet, resolve,
create ticket, live chat, departments and department detail, assets and asset detail, barcode scanner, projects
(with task toggle), contracts, alerts, notifications, appointments (with create), reports hub + Overview report,
profile (account, password, app lock, theme), knowledge base, credentials list behind Face ID, global search.

Behaviour carried over from the Android app: permission-based hiding of menu items and write controls (`/me`
permissions), writes only close or change the screen after the server confirms, repeat taps are blocked while a
write is in flight, a failed write keeps what the user typed, an unconfirmed network write tells the user to check
before retrying, deep links are allow-listed, and `rivetit://tickets/157` style links wait until sign-in.

## Not in this build yet

- Passkey sign-in (the button is hidden).
- Revealing a credential's secret (needs the signed biometric-challenge flow the Android app uses).
- Worksheet and outtake create / fill / sign (the ticket shows their lists only).
- Report screens other than Overview (the rows say so).
- Push notifications (APNs) and ticket attachments.
- Rich rendering of knowledge-base HTML (shown as text).
- Expiring-asset and per-report details, and iPad-specific split layouts.

## Layout

```
ios/
  Package.swift          RivetCore package
  Sources/RivetCore/     models, APIClient, transport + certificate trust, logic
  Tests/RivetCoreTests/  unit tests (+ Fixtures.swift from real API responses)
  project.yml            XcodeGen definition of the app target
  App/                   SwiftUI app: Core/ (session, loaders), Design/, Navigation/, Screens/
```
