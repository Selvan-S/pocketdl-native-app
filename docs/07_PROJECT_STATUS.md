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

**Current Phase:** Phase 2 — Stitch Design System Extraction / COMPLETED

**Status:** PHASE 2 COMPLETED. READY FOR PHASE 3.

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
- The Android app and browser extension should communicate through a clearly defined, versioned capture protocol.

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
│   │       ├── data/ (repository)
│   │       ├── domain/ (model)
│   │       ├── download/
│   │       ├── capture/
│   │       └── ui/
│   │           ├── components/ (PocketDLTopAppBar, PocketDLBottomBar, MetricBadge, MediaItemCard, DownloadProgressCard, QueueItemRow, ExtensionStatusCard, StorageBreakdownBar, QualitySelectionBottomSheet, UrlInputField)
│   │           ├── mock/ (MockData.kt)
│   │           ├── navigation/ (Screen.kt, AppNavHost.kt)
│   │           ├── screens/ (home, captured, downloads, queue, media_details, analysis, extension, settings, storage)
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
        └── PHASE_2_HANDOFF.md
```

---

## Design / Stitch Status

**Stitch project:** Accessible through Stitch MCP.

**Project:** `PocketDL Companion App Design` (ID: `502327910935149531`)

**Design system:** `PocketDL Dark Utility`

**Extracted Design Tokens & Components:** Documented in `docs/DESIGN.md`.

**Implemented Screen Shells:**

1. `HomeScreen` (Screen 1: PocketDL - Home Dashboard)
2. `CapturedScreen` (Screen 2: PocketDL - Captured Media)
3. `DownloadsScreen` (Screen 3: PocketDL - Downloads Library)
4. `QueueScreen` (Screen 4: PocketDL - Download Queue)
5. `MediaDetailsScreen` (Screen 5: PocketDL - Media Detail & Quality)
6. `MediaSnifferScreen` (Screen 6: PocketDL - Media Sniffer Analysis)
7. `ExtensionConnectionScreen` (Screen 7: PocketDL - Extension Connection)
8. `SettingsScreen` (Screen 8: PocketDL - Settings & Engine Preferences)
9. `StorageCleanupScreen` (Screen 9: PocketDL - Batch Edit & Storage Cleanup)

*Screen 10 (Browser Extension Popup) is intentionally separated and NOT built in the Android Compose application.*

---

## Tooling / Environment Status

### Git

- Git branch: `phase-2`
- Git commit baseline: `7b06169`

### Android Studio / Gradle

- Installed/configured: **VERIFIED**
- Gradle build (`assembleDebug`): **VERIFIED SUCCESSFUL**
- Automated tests (`./gradlew test`): **PASSING**

---

## Completed Phases

| Phase | Name | Status |
|---|---|---|
| 0 | Environment & Workspace | COMPLETED |
| 1 | Native Android Foundation | COMPLETED |
| 2 | Stitch Design System Extraction | COMPLETED |
| 3 | Screen Shell | NOT STARTED |
| 4 | State + Fake Data | NOT STARTED |
| 5 | Room Persistence | NOT STARTED |
| 6 | Direct HTTP Download Engine | NOT STARTED |
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

Proceed to **Phase 3 — Screen Shell**.
