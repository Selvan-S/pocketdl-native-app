# PocketDL — Project Status

> **Purpose:** This file is the current snapshot of the PocketDL repository. It should be updated at the end of every development phase so a new AI chat can understand the current state without relying on previous conversation history.
>
> **Rule:** Keep this file factual and concise. Do not use it as a detailed changelog. Use Git history for historical changes.

---

## Current Project State

**Project:** PocketDL

**Repository:** `pocketdl-native`

**Application:** Native Android app

**Primary stack:** Kotlin + Jetpack Compose

**Current Phase:** Phase 4 / 5 — Room Persistence / COMPLETED

**Status:** ROOM PERSISTENCE COMPLETED. READY FOR PHASE 6 (DIRECT HTTP DOWNLOAD ENGINE).

**Last updated:** 2026-10-08

---

## Confirmed Product Decisions

- PocketDL is an **Android-first native application**.
- Use **Kotlin + Jetpack Compose**.
- There is **no in-app browser**.
- Media detection is handled by a **separate browser extension**.
- The extension sends captured media information to PocketDL.
- PocketDL provides the **Captured inbox + Download Manager + Download Library**.
- PocketDL supports **public or user-authorized content only**.
- PocketDL must not bypass DRM, authentication, paywalls, private-content restrictions, or other access controls.
- No backend is required for the initial local-first version.
- Downloader code must be isolated from Compose UI code.
- Unidirectional state flow (UDF) is established: UI Event → ViewModel → UI State → Compose UI.
- All screen business logic resides in ViewModels and in-memory repositories rather than Composables.
- The Android app and browser extension communicate through a clearly defined capture protocol.

---

## Repository Structure

Current structure:

```text
pocketdl-native/
├── android-app/
│   ├── app/
│   │   └── src/main/java/com/pocketdl/app/
│   │       ├── PocketDlApplication.kt
│   │       ├── MainActivity.kt
│   │       ├── core/ (di, dispatchers, logging, result)
│   │       ├── data/
│   │       │   └── repository/ (CapturedMediaRepository, DownloadRepository, ExtensionRepository, SettingsRepository, StorageRepository, InMemoryRepositories)
│   │       ├── domain/ (model)
│   │       ├── download/
│   │       ├── capture/
│   │       └── ui/
│   │           ├── components/ (PocketDLTopAppBar, PocketDLBottomBar, MetricBadge, MediaItemCard, DownloadProgressCard, QueueItemRow, ExtensionStatusCard, StorageBreakdownBar, QualitySelectionBottomSheet, UrlInputField)
│   │           ├── mock/ (MockData.kt)
│   │           ├── navigation/ (Screen.kt, AppNavHost.kt)
│   │           ├── screens/
│   │           │   ├── home/ (HomeScreen, HomeViewModel, HomeUiState)
│   │           │   ├── captured/ (CapturedScreen, CapturedViewModel, CapturedUiState)
│   │           │   ├── downloads/ (DownloadsScreen, DownloadsViewModel, DownloadsUiState)
│   │           │   ├── queue/ (QueueScreen, QueueViewModel, QueueUiState)
│   │           │   ├── media_details/ (MediaDetailsScreen, MediaDetailsViewModel, MediaDetailsUiState)
│   │           │   ├── analysis/ (MediaSnifferScreen, MediaSnifferViewModel, MediaSnifferUiState)
│   │           │   ├── extension/ (ExtensionConnectionScreen, ExtensionViewModel, ExtensionUiState)
│   │           │   ├── settings/ (SettingsScreen, SettingsViewModel, SettingsUiState)
│   │           │   ├── storage/ (StorageCleanupScreen, StorageCleanupViewModel, StorageCleanupUiState)
│   │           │   └── download_details/ (DownloadDetailsScreen, DownloadDetailsViewModel, DownloadDetailsUiState)
│   │           └── theme/ (Color.kt, Dimensions.kt, Shapes.kt, Theme.kt, Type.kt)
│   ├── build.gradle.kts
│   ├── settings.gradle.kts
│   └── ...
│
└── docs/
    ├── 00_START_HERE.md
    ├── 01_STITCH_ANTIGRAVITY_CONNECTION.md
    ├── 02_ARCHITECTURE.md
    ├── 03_DEVELOPMENT_PLAN.md
    ├── 04_ANTIGRAVITY_MASTER_PROMPT.md
    ├── 05_PHASE_PROMPTS.md
    ├── 06_BEGINNER_WORKFLOW.md
    ├── 07_PROJECT_STATUS.md
    ├── 08_ARCHITECTURE_DECISIONS.md
    ├── 09_PHASE_HANDOFF_TEMPLATE.md
    ├── DESIGN.md
    └── handoffs/
        ├── PHASE_1_HANDOFF.md
        ├── PHASE_2_HANDOFF.md
        └── PHASE_3_HANDOFF.md
```

---

## Design / Stitch Status

**Stitch project:** Accessible through Stitch MCP.

**Project:** `PocketDL Companion App Design` (ID: `502327910935149531`)

**Design system:** `PocketDL Dark Utility`

**Extracted Design Tokens & Components:** Documented in `docs/DESIGN.md`.

**State-Driven Screen Implementations:**

1. `HomeScreen` (`HomeViewModel` + `HomeUiState`)
2. `CapturedScreen` (`CapturedViewModel` + `CapturedUiState`)
3. `DownloadsScreen` (`DownloadsViewModel` + `DownloadsUiState`)
4. `QueueScreen` (`QueueViewModel` + `QueueUiState`)
5. `MediaDetailsScreen` (`MediaDetailsViewModel` + `MediaDetailsUiState`)
6. `MediaSnifferScreen` (`MediaSnifferViewModel` + `MediaSnifferUiState`)
7. `ExtensionConnectionScreen` (`ExtensionViewModel` + `ExtensionUiState`)
8. `SettingsScreen` (`SettingsViewModel` + `SettingsUiState`)
9. `StorageCleanupScreen` (`StorageCleanupViewModel` + `StorageCleanupUiState`)
10. `DownloadDetailsScreen` (`DownloadDetailsViewModel` + `DownloadDetailsUiState`)

*Screen 10 (Browser Extension Popup) is intentionally separated and NOT built in the Android Compose application.*

---

## Tooling / Environment Status

### Git

- Git branch: `room-persistence`

### Android Studio / Gradle

- Installed/configured: **VERIFIED**
- Gradle build (`assembleDebug`): **VERIFIED SUCCESSFUL**
- Automated tests (`./gradlew test`): **PASSING (100% pass rate: Phase3ViewModelStateUnitTest, RoomPersistenceUnitTest)**
- Device Run/Verification: **VERIFIED on physical connected Samsung Galaxy S22 Ultra via ADB (persistence across app kill confirmed)**

---

## Completed Phases

| Phase | Name | Status |
|---|---|---|
| 0 | Environment & Workspace | COMPLETED |
| 1 | Native Android Foundation | COMPLETED |
| 2 | Stitch Design System Extraction | COMPLETED |
| 3 | Screen State & Interactions | COMPLETED |
| 4/5 | Room Persistence | COMPLETED |
| 6 | Direct HTTP Download Engine | NEXT UP |
| 7 | Background Execution + Notifications | NOT STARTED |
| 8 | Download Manager Hardening | NOT STARTED |
| 9 | Extension Capture Protocol | NOT STARTED |
| 10 | Real Browser Extension Integration | NOT STARTED |
| 11 | HLS Support | NOT STARTED |
| 12 | Android Storage + File Library | NOT STARTED |
| 13 | Settings + Preferences | NOT STARTED |
| 14 | Edge Cases + Reliability | NOT STARTED |
| 15 | UI Polish + Accessibility | NOT STARTED |
| 16 | Release Testing | NOT STARTED |
| 17 | v1.0 Release Preparation | NOT STARTED |

---

## Next Action

Proceed to **Phase 6 — Direct HTTP Download Engine** (implementing standalone HTTP file download engine with streaming byte progress, resume with HTTP Range headers, and direct storage output).
