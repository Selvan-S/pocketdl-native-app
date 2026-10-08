# PocketDL — Architecture Decisions

> **Purpose:** This document records important technical decisions that should remain stable across AI chats and development phases.
>
> **Rule:** Do not change an architectural decision silently. If a later phase reveals a strong reason to change one, document the reason, impact, and replacement decision before changing the implementation.

---

## ADR-001 — Native Android

**Status:** Accepted

**Decision:** Build PocketDL as a native Android application using Kotlin and Jetpack Compose.

**Reason:**

PocketDL is Android-first and depends heavily on Android platform capabilities such as:

- background/long-running downloads
- foreground services where applicable
- Android notifications
- user-visible file storage
- MediaStore / Storage Access Framework
- app lifecycle handling
- Android intents/deep links or another appropriate extension-to-app transport
- native performance and platform behavior

A web runtime or cross-platform abstraction is not required for v1.

**Alternatives considered:**

- React + Capacitor
- React Native

These remain technically viable but were not selected for the Android-first v1 architecture.

---

## ADR-002 — Jetpack Compose for UI

**Status:** Accepted

**Decision:** Use Jetpack Compose for all new PocketDL UI.

**Reason:**

Compose provides a modern declarative UI model and works naturally with Kotlin, ViewModels, StateFlow, and Android's current application architecture guidance.

Do not introduce XML layouts unless a platform/component requirement makes XML genuinely necessary.

---

## ADR-003 — Local-first v1

**Status:** Accepted

**Decision:** PocketDL v1 does not require a backend or cloud database.

**Reason:**

The core product is a local Android download manager. A backend would add deployment, authentication, security, and operational complexity without being necessary for the initial product.

A backend may be reconsidered later for optional features such as cloud sync, but that would be a new architectural decision.

---

## ADR-004 — Browser Extension Is a Separate Product Boundary

**Status:** Accepted

**Decision:** Media detection is performed by a separate browser extension. The Android app receives capture data.

**Core flow:**

```text
Browser
  ↓
PocketDL Extension
  ↓
Media Capture Payload
  ↓
PocketDL Android App
  ↓
Captured Inbox
  ↓
Download Manager
```

**Reason:**

The app should not implement an in-app browser merely to detect media. This keeps the Android app focused on capture management, downloading, storage, and file management.

---

## ADR-005 — No In-App Browser

**Status:** Accepted

**Decision:** Do not add an embedded browser to PocketDL v1.

**Reason:**

The browser extension is the intended detection mechanism. An in-app browser would duplicate browser functionality and create a larger security, maintenance, and compatibility surface.

---

## ADR-006 — UI Must Not Own Download Logic

**Status:** Accepted

**Decision:** Compose screens must not contain network/download engine implementation.

**Allowed:**

```text
Screen
  ↓
ViewModel
  ↓
Use Case
  ↓
Repository / DownloadManager
```

**Not allowed:**

```text
Compose Screen
  ↓
OkHttp request
  ↓
FileOutputStream
```

**Reason:**

This makes the download engine independently testable and prevents UI classes from becoming unmaintainable.

---

## ADR-007 — Room for Durable Structured Metadata

**Status:** Accepted

**Decision:** Use Room for persistent application metadata.

**Store in Room:**

- captured media metadata
- media variants
- download task metadata
- download history
- durable download state required for recovery

**Do not store:**

- actual media file bytes
- large binary downloads

The filesystem/storage layer owns the files; Room stores metadata and references.

---

## ADR-008 — DataStore for Preferences

**Status:** Accepted

**Decision:** Use DataStore for small user preferences.

Examples:

- theme mode
- Wi-Fi-only setting
- preferred quality
- preferred format
- concurrency limit
- notification preferences

Do not use Room for simple key-value preferences unless there is a clear relational need.

---

## ADR-009 — Coroutines + Flow for Async State

**Status:** Accepted

**Decision:** Use Kotlin Coroutines and Flow/StateFlow for asynchronous operations and observable state.

Use structured concurrency. Avoid manually managed threads where coroutines or platform-managed execution are more appropriate.

---

## ADR-010 — Versioned Capture Protocol

**Status:** Accepted

**Decision:** The extension-to-app payload must be versioned.

Example concept:

```json
{
  "protocolVersion": 1,
  "type": "MEDIA_CAPTURE",
  "media": {}
}
```

The exact schema will be finalized in Phase 9 and documented in `EXTENSION_PROTOCOL.md`.

**Reason:**

The extension and Android app may evolve independently. Versioning prevents future changes from becoming breaking, undocumented behavior.

---

## ADR-011 — Direct HTTP Before HLS

**Status:** Accepted

**Decision:** Implement stable direct HTTP/HTTPS file downloads before HLS support.

**Reason:**

Direct downloads provide the simplest path for validating queue, persistence, progress, storage, pause/resume, retry, notifications, and failure handling before adding stream-specific complexity.

---

## ADR-012 — Android Background Execution Must Follow Current Platform Rules

**Status:** Accepted

**Decision:** Background download execution must be designed around the target Android SDK and current Android foreground-service/background execution rules.

Do not assume that a foreground service can run indefinitely without platform restrictions.

The final implementation must verify:

- foreground service type
- required permissions
- when background starts are allowed
- notification requirements
- lifecycle recovery
- Play policy implications

These details are to be finalized during the downloader/background execution phases using current official Android documentation.

---

## ADR-013 — One App Module Initially

**Status:** Accepted

**Decision:** Start with one Android application Gradle module.

**Reason:**

PocketDL is small enough initially that multiple Gradle modules would add cognitive and build complexity without a clear benefit.

Create additional modules only when there is a demonstrated boundary that improves build time, ownership, testability, or reuse.

---

## ADR-014 — AI Agent Must Work Phase-by-Phase

**Status:** Accepted

**Decision:** Antigravity must not implement the entire roadmap in one pass.

Each phase must have:

- explicit scope
- exit criteria
- tests/build verification
- Git checkpoint
- project-status update
- phase handoff

A new AI chat may be used for each major phase.

---

## ADR-015 — Stitch Is the Visual Source of Truth

**Status:** Accepted

**Decision:** The Stitch project is the primary reference for PocketDL visual design.

Antigravity should use Stitch MCP when implementing or reviewing UI.

Do not redesign the product simply because a generated implementation looks easier.

If the design must change, update the design/source-of-truth and document the reason.

---

## ADR-016 — Current Design Scope

**Status:** Accepted

**Decision:** The current Stitch project contains ten designs:

- 9 mobile-oriented PocketDL app designs
- 1 browser extension popup design

The Android app roadmap must use the mobile designs relevant to the current phase. The browser extension popup is not an Android screen and must not be implemented inside the Android app.

---
