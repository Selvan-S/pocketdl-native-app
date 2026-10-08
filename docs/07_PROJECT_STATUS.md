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

**Current Phase:** Phase 0 — Environment & Workspace / Ready for Phase 1

**Status:** READY TO START PHASE 1

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
    └── 09_PHASE_HANDOFF_TEMPLATE.md
```

> Note: a `browser-extension/` directory can be introduced later when extension development begins. Do not create it merely for the initial Android foundation unless the current phase requires it.

---

## Design / Stitch Status

**Stitch project:** Accessible through Stitch MCP.

**Project:** `PocketDL Companion App Design`

**Design system:** `PocketDL Dark Utility`

**Known design direction:**

- Dark-mode-first utility application
- Electric Cyan `#06B6D4` primary accent
- Emerald Green `#10B981` secondary accent
- Inter / JetBrains Mono typography

**Known Stitch designs:**

1. PocketDL - Home Dashboard
2. PocketDL - Captured Media
3. PocketDL - Downloads Library
4. PocketDL - Download Queue
5. PocketDL - Media Detail & Quality
6. PocketDL - Media Sniffer Analysis
7. PocketDL - Extension Connection
8. PocketDL - Settings & Engine Preferences
9. PocketDL - Batch Edit & Storage Cleanup
10. PocketDL - Browser Extension Popup

The first nine are mobile-oriented app designs; the browser extension popup is a separate desktop-oriented design.

Stitch MCP has been verified successfully in Antigravity.

---

## Tooling / Environment Status

### Git

- Git initialized: **YES**
- Initial project commit: **DONE**
- Working tree should be checked before every phase starts and ends.

### Android Studio

- Installed/configured: **CHECK LOCALLY**
- Physical Android device: **CHECK LOCALLY**
- Emulator: optional

### Android SDK

- SDK 36: **CHECK LOCALLY**
- Build Tools 36.x: **CHECK LOCALLY**
- Platform Tools / ADB: **CHECK LOCALLY**

### Java

- JDK 17: **CHECK LOCALLY**

### Stitch MCP

- Connected to Antigravity: **YES**
- Stitch project accessible: **YES**
- Stitch screens accessible: **YES**

---

## Completed Phases

| Phase | Name | Status |
|---|---|---|
| 0 | Environment & Workspace | READY / verified tooling and repository setup |
| 1 | Native Android Foundation | NOT STARTED |
| 2 | Stitch Design System Extraction | NOT STARTED |
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

## Current Phase Goal

### Phase 1 — Native Android Foundation

Create a clean, maintainable native Android foundation without implementing real downloader or browser-extension functionality.

### Phase 1 must establish

- Kotlin project baseline
- Jetpack Compose baseline
- Application theme foundation
- Navigation foundation
- Dependency injection foundation if required by the agreed architecture
- Test structure
- Package structure
- Basic logging approach
- Build/debug verification
- Physical-device or emulator launch verification

### Phase 1 must NOT implement

- Real download engine
- HLS downloading
- Browser extension integration
- Extension transport protocol implementation
- Room schema for the complete product
- Complex background services
- Authentication/backend

---

## Current Known Issues

- None known at the time of the initial project setup.

If an issue is discovered, record it here in one or two lines and link to the relevant GitHub issue if one exists.

---

## Current Architecture Changes

None yet.

Major architectural changes must also be recorded in `08_ARCHITECTURE_DECISIONS.md`.

---

## Latest Git Checkpoint

The repository has an initial commit before feature implementation.

At the end of each phase, create a clearly named commit, for example:

```text
phase 1: native android foundation
phase 2: stitch compose design system
phase 3: screen shell
```

Do not squash away useful phase checkpoints while the project is under active development.

---

## Next Action

Start a **new Antigravity chat for Phase 1**.

Before modifying code, the agent must read:

```text
00_START_HERE.md
01_STITCH_ANTIGRAVITY_CONNECTION.md
02_ARCHITECTURE.md
03_DEVELOPMENT_PLAN.md
04_ANTIGRAVITY_MASTER_PROMPT.md
05_PHASE_PROMPTS.md
06_BEGINNER_WORKFLOW.md
07_PROJECT_STATUS.md
08_ARCHITECTURE_DECISIONS.md
09_PHASE_HANDOFF_TEMPLATE.md
```

Then implement **Phase 1 only**.
