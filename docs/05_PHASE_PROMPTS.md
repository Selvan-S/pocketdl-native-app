# PocketDL — Phase Prompts for Antigravity

Use one prompt at a time. Do not paste all prompts together.

## Phase 0 — Verify environment

```text
Work on PocketDL Phase 0 only.

Read:
- docs/DEVELOPMENT_PLAN.md
- docs/ARCHITECTURE.md
- docs/START_HERE.md if available

Do not implement product features.

Verify:
- Android Studio / Gradle environment
- JDK 17
- Android SDK 36
- Build-Tools 36.x
- ADB/device availability
- Git
- Node.js
- Stitch MCP
- Stitch PocketDL project visibility

Report:
- detected versions
- what is working
- what is missing
- exact remediation steps

Do not modify application source code unless a setup correction is strictly necessary.
```

## Phase 1 — Native project foundation

```text
Implement PocketDL Phase 1 only.

Use native Kotlin + Jetpack Compose.

Create a clean Android application foundation with:
- Compose
- Navigation Compose
- ViewModel support
- Coroutines/Flow foundation
- Hilt foundation
- theme system
- test source sets
- clean package structure

Do not implement:
- downloader
- browser extension integration
- HLS
- backend

Run Gradle checks and launch the app.

At the end, report changed files, checks run, and remaining issues.
```

## Phase 2 — Stitch design system

```text
Implement PocketDL Phase 2 only.

Use Stitch MCP to inspect the PocketDL project.

Create/update docs/DESIGN.md containing the extracted design system.

Implement the Compose theme and reusable UI foundations:
- colors
- typography
- spacing
- shapes
- buttons
- cards
- progress indicators
- navigation
- common empty/loading/error components

Do not redesign the Stitch UI.
Do not implement downloader logic or extension logic.

Compare the Compose output against Stitch and fix obvious visual mismatches.
```

## Phase 3 — Six screens + two modals

```text
Implement PocketDL Phase 3 only.

Implement the six Stitch-designed screens:
- Home
- Captured
- Downloads
- Settings
- Media Details
- Download Details

Implement:
- Quality Selection modal/bottom sheet
- Capture Action modal/bottom sheet

Use mock data only.

Focus on visual fidelity and navigation.

Do not implement real networking, persistence, extension communication, or download execution.
```

## Phase 4 — UI state + fake repositories

```text
Implement PocketDL Phase 4 only.

Replace screen hardcoding with screen state + ViewModels + fake repositories.

Create realistic states for:
- empty
- loading
- captured content
- downloading
- paused
- completed
- failed
- unsupported

The UI must react to state changes rather than manually switching visual properties inside Composables.

Do not add real networking.
```

## Phase 5 — Room persistence

```text
Implement PocketDL Phase 5 only.

Add Room persistence for:
- captured media metadata
- media variants where needed
- download task metadata

Do not store media file bytes in Room.

Add entities, DAOs, repositories, and migrations strategy.

Update fake repository usage so production repository interfaces can use Room cleanly.

Test persistence across app restart.
```

## Phase 6 — Direct HTTP downloader

```text
Implement PocketDL Phase 6 only.

Build a reliable direct HTTP/HTTPS download engine for public/user-authorized files.

Support:
- enqueue
- progress
- completion
- failure
- cancel
- retry
- resume when the server supports HTTP Range

Do not implement HLS yet.
Do not integrate the browser extension yet.

Keep the downloader independent of Compose UI.

Never load the entire file into memory.

Write unit tests for queue/status behavior and important edge cases.
```

## Phase 7 — Background execution

```text
Implement PocketDL Phase 7 only.

Make active downloads continue correctly when the UI is not visible, within current Android platform rules.

Before coding, verify the current Android target-SDK requirements for long-running user-visible data-transfer work and the appropriate foreground-service declaration/permission/type.

Implement:
- execution service/worker strategy
- notifications
- progress updates
- completion/failure notifications
- cancellation
- lifecycle handling

Test on a physical device.

Do not assume unlimited background execution.
```

## Phase 8 — Queue hardening

```text
Implement PocketDL Phase 8 only.

Harden the download queue for:
- multiple downloads
- concurrency limit
- app restart recovery
- interrupted downloads
- duplicate handling
- cleanup after cancellation
- retry policy

Ensure Room state and actual download execution cannot drift silently.

Add tests for status transitions and recovery logic.
```

## Phase 9 — Extension protocol

```text
Implement PocketDL Phase 9 only.

Create a versioned capture protocol for the future browser extension.

Implement:
- payload models
- validation
- capture router
- repository integration
- duplicate capture detection

Use local/test payload injection instead of the real extension.

The extension payload must not depend on Compose UI models.
```

## Phase 10 — Real extension integration

```text
Implement PocketDL Phase 10 only.

Connect the actual PocketDL browser extension to the Android app using the agreed capture transport.

Before implementation, read docs/EXTENSION_PROTOCOL.md and verify its assumptions against the chosen transport.

Implement:
- receive capture
- validate
- persist
- show in Captured
- connection/error states

Test end-to-end.
```

## Phase 11 — HLS

```text
Implement PocketDL Phase 11 only.

Add support for permitted/public HLS streams that PocketDL can legitimately download.

Support quality selection and reliable handling of supported HLS playlists/segments.

Do not implement DRM bypass or access-control circumvention.

Document supported and unsupported HLS cases.

Test with controlled public/authorized test streams.
```

## Phase 12 — Storage/file library

```text
Implement PocketDL Phase 12 only.

Complete Android storage integration.

Support:
- file naming
- MIME types
- duplicate name handling
- open file
- share file
- cleanup
- storage information

Use Android user-visible storage APIs appropriately.

Test on a physical device.
```

## Phase 13 — Settings

```text
Implement PocketDL Phase 13 only.

Persist and apply designed settings using DataStore or appropriate Android preferences storage.

Do not create a second settings mechanism.

Test settings after process/app restart.
```

## Phase 14 — Edge cases

```text
Implement PocketDL Phase 14 only.

Audit the app for:
- invalid URLs
- unsupported schemes
- redirects
- HTTP failures
- timeouts
- connection loss
- low storage
- duplicate downloads
- malformed extension payloads
- long/unsafe filenames
- Unicode filenames
- background execution
- app restart
- notification permission denial

Every failure should have a meaningful state and preserve correct download metadata.
```

## Phase 15 — UI polish

```text
Implement PocketDL Phase 15 only.

Compare every production screen with the Stitch design.

Fix:
- spacing
- typography
- icon sizing
- component dimensions
- navigation layout
- dark/light theme details
- loading states
- animations
- accessibility issues

Do not change product behavior unless a UI issue requires it.
```

## Phase 16 — Release readiness

```text
Implement PocketDL Phase 16 only.

Prepare for release:
- unit tests
- instrumentation tests
- critical Compose UI tests
- release build
- signing configuration strategy
- R8/minification review
- permission review
- foreground-service policy review
- privacy/documentation review

Do not publish.
Report all remaining release blockers.
```
