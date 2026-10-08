# PocketDL — Phase-by-Phase Development Plan

## Principle

Build the application vertically in small, testable slices.

Do not build the UI, downloader, extension, persistence, and Android background execution all at once.

Each phase has:

- goal
- scope
- output
- exit criteria

Only move to the next phase after the exit criteria pass.

---

# Phase 0 — Environment & workspace

### Goal

Make sure all development tools work before AI implementation starts.

### Tasks

- Install Android Studio stable.
- Install SDK 36 / Build-Tools 36.x.
- Verify JDK 17.
- Verify ADB.
- Connect physical device or create emulator.
- Install Git.
- Install Node 18+.
- Install Antigravity.
- Create Stitch project/API key.
- Connect Stitch MCP in Antigravity.
- Verify `List my Stitch projects.`
- Create Git repository.

### Exit criteria

A blank/test Android app runs on a real device or emulator and Stitch is visible to Antigravity.

---

# Phase 1 — Native Android foundation

### Goal

Create a clean Kotlin/Compose project.

### Build

- Kotlin Android project
- Compose
- Navigation
- Theme system
- Dependency injection foundation
- test structure
- baseline logging

### Important

Do not build downloader functionality.

Do not connect extension.

### Exit criteria

- Gradle sync succeeds.
- Debug build succeeds.
- App launches.
- Theme works.
- Navigation framework is ready.
- Git commit created.

---

# Phase 2 — Stitch design system extraction

### Goal

Turn Stitch's visual design into explicit implementation rules.

### Build

- `docs/DESIGN.md`
- Compose theme tokens
- typography
- spacing
- shapes
- common buttons/cards
- icons
- bottom navigation

### Exit criteria

The design tokens are documented and the Compose theme uses those tokens rather than scattered values.

---

# Phase 3 — Screen shell

### Goal

Implement all six screens as UI-only screens using mock data.

### Screens

- Home
- Captured
- Downloads
- Settings
- Media Details
- Download Details

### Modals

- Quality Selection
- Capture Action

### Exit criteria

All screens are navigable and visually close to Stitch.

No networking.

No real downloader.

No extension.

---

# Phase 4 — State and fake data

### Goal

Make the UI behave like a real app without network dependencies.

### Build

- screen state models
- ViewModels
- fake repositories
- loading/empty/error/success states
- mock captured media
- mock download queue

### Exit criteria

You can demonstrate:

```text
Home → Captured → Media Details → Quality Modal
Home → Downloads → Download Details
```

with realistic state changes.

---

# Phase 5 — Room persistence

### Goal

Make captures/download metadata survive process death.

### Build

Room database:

- CapturedMediaEntity
- MediaVariantEntity if required
- DownloadTaskEntity
- DAOs
- repositories

### Exit criteria

Close/reopen the application and metadata remains.

Downloaded files are not stored inside Room.

---

# Phase 6 — Direct HTTP download engine

### Goal

Implement the simplest real download path first.

### Scope

- direct HTTP/HTTPS downloadable file
- queue
- progress
- pause if technically supported for the chosen engine
- resume using HTTP Range where supported
- cancel
- retry
- completion
- failure
- file output

Do not implement HLS first.

### Exit criteria

A user can download a real public/authorized direct media file to device storage and see correct progress/status.

---

# Phase 7 — Background execution & notifications

### Goal

Make downloads behave correctly when the app UI is not visible.

### Build

- download execution service/worker strategy
- foreground-service integration if required for active user-visible downloads
- notification channel
- progress notification
- completion notification
- failure notification

### Important

Verify the chosen foreground-service type, permission, launch restrictions, and current Play policy before release.

### Exit criteria

Start a download, background the app, and observe stable progress/notification behavior on a physical device.

---

# Phase 8 — Download manager hardening

### Goal

Make multiple downloads reliable.

### Build

- queue ordering
- maximum concurrent downloads
- persistent status recovery
- app restart recovery
- duplicate handling
- interrupted downloads
- retry policy
- cancellation cleanup
- invalid/corrupt output handling

### Exit criteria

The queue remains consistent after app restart and common failures.

---

# Phase 9 — Extension capture protocol

### Goal

Define and validate the extension-to-app contract.

### Build

- versioned payload
- validation
- capture router
- repository integration
- duplicate capture handling
- source information

### Exit criteria

A test payload can be sent to the app and appears correctly in Captured.

Do not depend on a specific extension implementation yet.

---

# Phase 10 — Real browser extension integration

### Goal

Connect the actual browser extension.

### Build

- extension transport
- Android app entry point
- connection state
- capture receipt
- failure handling

### Exit criteria

```text
Browser → Extension → PocketDL → Captured
```

works reliably.

---

# Phase 11 — HLS support

### Goal

Add supported HLS download handling after direct-file downloads are stable.

### Build

- detect HLS variant metadata
- select quality
- download supported HLS content
- handle segment failures
- assemble output where appropriate
- define supported formats

Do not assume every `.m3u8` is downloadable or permitted.

Do not attempt to bypass DRM/encryption/access controls.

### Exit criteria

A supported, public/authorized HLS test stream can be captured, selected, downloaded, and opened successfully.

---

# Phase 12 — Storage & file experience

### Goal

Make the local file library production quality.

### Build

- MediaStore/SAF strategy
- file naming
- MIME types
- open/share
- duplicate names
- cleanup
- storage calculations

### Exit criteria

Files appear correctly to Android and can be opened/shared by normal apps.

---

# Phase 13 — Settings & preferences

### Goal

Implement the designed settings using DataStore and platform APIs.

### Build

- theme
- preferred quality
- preferred format
- Wi-Fi-only
- concurrency
- notifications
- storage options

### Exit criteria

Settings survive restart and affect relevant behavior.

---

# Phase 14 — Error/edge-case pass

### Test

- invalid URL
- unsupported scheme
- unsupported media
- server error
- HTTP 403/404/416
- redirect
- timeout
- connection lost
- low disk space
- duplicate download
- app backgrounded
- app killed
- device reboot
- notification denied
- malformed extension payload
- extremely long filename
- Unicode filename
- very large file

### Exit criteria

Every important failure has a meaningful user-visible state and does not silently corrupt download state.

---

# Phase 15 — UI polish

### Goal

Final visual fidelity against Stitch.

### Tasks

- compare every screen
- spacing
- typography
- icon size
- card radius
- navigation dimensions
- animation timing
- loading skeletons where appropriate
- dark mode
- light mode
- accessibility

### Exit criteria

The app looks intentionally designed rather than merely functional.

---

# Phase 16 — Testing & release preparation

### Automated

- unit tests
- repository tests
- ViewModel tests
- instrumentation tests
- Compose UI tests for critical flows

### Manual

Test on multiple Android versions/devices.

### Release

- release signing
- R8/minification review
- privacy policy
- app permissions review
- foreground-service policy review
- Play Console declaration requirements
- crash reporting if added

Never publish a build containing test credentials or development endpoints.

---

# Phase 17 — v1.0 release

The first release should prioritize reliability over feature count.

Recommended v1 feature set:

```text
✅ Captured inbox
✅ Direct HTTP downloads
✅ Queue management
✅ Pause/resume where supported
✅ Retry
✅ Background execution
✅ Notifications
✅ Local file library
✅ Extension capture
✅ Settings
✅ Dark/light theme
```

HLS can be part of v1 if it is stable enough; otherwise release it as a follow-up after the direct-download path is proven.

---

# AI development rule

At the end of every phase, the agent must report:

1. What changed
2. Files created/modified
3. Tests/checks run
4. What passed
5. What failed
6. Known technical debt
7. Suggested next phase

Never continue silently through multiple phases.
