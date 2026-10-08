# PocketDL — Maintainable & Scalable Architecture

## 1. Architecture goals

PocketDL should be:

- easy to understand for a new Android developer
- maintainable as features grow
- testable without a physical device
- resilient to app restarts
- safe for large downloads
- independent of the browser-extension implementation
- easy to extend later without rewriting the UI

The architecture should avoid both extremes:

```text
Bad: everything in Activity/Composable
Bad: excessive enterprise-level abstraction for a small app
```

Use clear layers with practical boundaries.

---

## 2. High-level architecture

```text
┌───────────────────────────────────────────┐
│ UI Layer                                   │
│ Jetpack Compose                            │
│ Screens / Reusable Components              │
└─────────────────────┬─────────────────────┘
                      │ UI state/events
                      ▼
┌───────────────────────────────────────────┐
│ Presentation Layer                         │
│ ViewModels                                 │
│ Screen State / UI Events                  │
└─────────────────────┬─────────────────────┘
                      │ use cases
                      ▼
┌───────────────────────────────────────────┐
│ Domain Layer                               │
│ Models / Business Rules / Use Cases        │
│ Download queue rules / Capture rules       │
└─────────────────────┬─────────────────────┘
                      │ repository interfaces
                      ▼
┌───────────────────────────────────────────┐
│ Data / Platform Layer                      │
│ Repositories                                │
│ Room / DataStore / Network                 │
│ Android Services / MediaStore / Notify     │
└───────────────────────────────────────────┘
```

Use unidirectional data flow where practical:

```text
UI event
  ↓
ViewModel
  ↓
Use case
  ↓
Repository / service
  ↓
StateFlow
  ↓
UI
```

Android's current Compose guidance supports screen-level ViewModels and unidirectional data flow patterns. See the official Android architecture guidance.

---

## 3. Suggested project structure

Start with one Android application module. Do not split into many Gradle modules on day one.

```text
android-app/
├── app/
│   └── src/
│       ├── main/
│       │   ├── java/com/pocketdl/app/
│       │   │   ├── PocketDlApplication.kt
│       │   │   ├── MainActivity.kt
│       │   │   │
│       │   │   ├── core/
│       │   │   │   ├── common/
│       │   │   │   ├── dispatchers/
│       │   │   │   ├── result/
│       │   │   │   └── logging/
│       │   │   │
│       │   │   ├── data/
│       │   │   │   ├── database/
│       │   │   │   ├── datastore/
│       │   │   │   ├── network/
│       │   │   │   ├── repository/
│       │   │   │   └── storage/
│       │   │   │
│       │   │   ├── domain/
│       │   │   │   ├── model/
│       │   │   │   └── usecase/
│       │   │   │
│       │   │   ├── download/
│       │   │   │   ├── engine/
│       │   │   │   ├── service/
│       │   │   │   ├── queue/
│       │   │   │   └── model/
│       │   │   │
│       │   │   ├── capture/
│       │   │   │   ├── model/
│       │   │   │   ├── repository/
│       │   │   │   └── protocol/
│       │   │   │
│       │   │   └── ui/
│       │   │       ├── navigation/
│       │   │       ├── theme/
│       │   │       ├── components/
│       │   │       └── screens/
│       │   │           ├── home/
│       │   │           ├── captured/
│       │   │           ├── downloads/
│       │   │           ├── settings/
│       │   │           ├── media_details/
│       │   │           └── download_details/
│       │   │
│       │   └── res/
│       └── test/
│       └── androidTest/
│
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

Later, if the download subsystem becomes large enough, it can become a separate Gradle module. Do not do that preemptively.

---

## 4. Core domain models

### CapturedMedia

Represents something received from the browser extension or manual URL capture.

```text
id
createdAt
title
sourceUrl
sourceDomain
thumbnailUrl?
mediaType
variants
captureStatus
```

### MediaVariant

Represents a downloadable quality/format option.

```text
id
qualityLabel?
width?
height?
format?
mimeType?
url
estimatedBytes?
streamType
```

### DownloadTask

Represents the lifecycle of a local download.

```text
id
capturedMediaId?
variantId?
sourceUrl
fileName
localUri?
status
progress
bytesDownloaded
totalBytes?
speedBytesPerSecond?
createdAt
startedAt?
completedAt?
errorCode?
errorMessage?
```

Download status should be an enum rather than a free-form string.

Example:

```text
QUEUED
STARTING
DOWNLOADING
PAUSED
COMPLETED
FAILED
CANCELLED
```

---

## 5. Database strategy

### Room

Use Room for durable structured data:

- CapturedMedia metadata
- MediaVariant metadata
- DownloadTask metadata
- download history

Do not store large media files inside the database.

### File storage

Store downloaded files through Android's user-visible storage mechanisms, such as MediaStore or Storage Access Framework depending on the exact file type/use case.

The database stores the reference/URI/path metadata, not the file bytes.

### DataStore

Use DataStore for simple settings:

- theme
- Wi-Fi-only preference
- preferred quality
- preferred format
- simultaneous download limit
- default folder preference where applicable
- notification preferences

---

## 6. Downloader architecture

Do not put downloader code inside Compose screens.

Use a service boundary:

```text
DownloadManager
   ↓
QueueManager
   ↓
DownloadEngine
   ├── HttpDownloadEngine
   └── HlsDownloadEngine (when implemented)
```

The engine should expose progress through a predictable abstraction.

Example conceptual API:

```text
enqueue(request)
pause(downloadId)
resume(downloadId)
cancel(downloadId)
retry(downloadId)
observe(downloadId)
observeAll()
```

The UI should not know how bytes are copied from network to disk.

---

## 7. Background download architecture

Long-running user-visible downloads are an Android platform concern.

Do not assume a normal coroutine in a ViewModel can safely continue after the app goes to background.

Possible responsibilities:

```text
ViewModel
  = user intent / UI state

DownloadRepository
  = durable download metadata

DownloadManager
  = queue orchestration

ForegroundService
  = user-visible long-running download execution

Room
  = durable state

NotificationManager
  = progress/completion/failure notification
```

Android requires appropriate foreground-service declarations/permissions and has restrictions on starting/using foreground services. Data-transfer download work is associated with the `dataSync` foreground-service type, and modern Android versions impose additional limits. Always verify the exact requirements for the target SDK before release.

Do not design the product around an assumption that an unlimited background service can run forever.

---

## 8. Extension boundary

The extension should be treated as an external client of PocketDL.

Recommended architecture:

```text
Browser Extension
      ↓
Capture Protocol
      ↓
Android app entry point
      ↓
CaptureReceiver / CaptureRouter
      ↓
CaptureRepository
      ↓
Room
      ↓
Captured screen
```

The extension must send structured data rather than UI-specific instructions.

Never design the protocol around Compose views.

---

## 9. Capture protocol

Keep the first protocol small and versioned.

Example:

```json
{
  "version": 1,
  "type": "MEDIA_CAPTURE",
  "id": "capture-123",
  "title": "Example video",
  "sourceUrl": "https://example.com/video",
  "sourceDomain": "example.com",
  "thumbnailUrl": "https://example.com/thumb.jpg",
  "mediaType": "VIDEO",
  "variants": [
    {
      "quality": "1080p",
      "format": "mp4",
      "mimeType": "video/mp4",
      "url": "https://example.com/media.mp4",
      "size": 245000000
    }
  ]
}
```

This is a conceptual contract. The exact transport mechanism should be designed and tested before the extension implementation is finalized.

---

## 10. Security principles

The app may receive URLs from an external extension, so treat all received data as untrusted input.

Validate:

- payload version
- required fields
- URL structure
- supported schemes
- maximum lengths
- media variant count
- file name safety

Never blindly execute a command based on extension payload.

Avoid shell commands for downloads.

Do not store secrets in the capture payload.

---

## 11. Dependency principles

Before adding a library, ask:

1. Is AndroidX already able to solve this?
2. Is the library maintained?
3. Does it support the target Android SDK?
4. Does it introduce native binaries or large APK size?
5. Do we really need it?

Use the smallest number of dependencies that solves the problem.

---

## 12. Testing strategy

### Unit tests

Test:

- download queue rules
- status transitions
- duplicate detection
- file-name sanitization
- capture payload validation
- settings logic

### Instrumentation tests

Test:

- navigation
- Room integration
- notification behavior
- storage access
- service integration where practical

### Manual device tests

Test:

- screen locked
- app minimized
- app removed from recent tasks
- network interruption
- Wi-Fi ↔ mobile data
- large file
- low storage
- duplicate file
- failed download
- pause/resume
- multiple queued downloads

---

## 13. Architecture rules for AI agents

The agent must not:

- put networking inside Composables
- put database operations directly inside Composables
- put download logic inside Activity
- use global mutable singleton state for everything
- duplicate UI components across screens
- introduce a backend without a documented product requirement
- introduce a second state-management framework without justification
- create many modules just for appearance
- mix extension transport code with download engine code

The agent should:

- preserve boundaries
- prefer small interfaces at real boundaries
- keep data flow understandable
- use immutable UI state models where appropriate
- keep ViewModels screen-scoped
- keep platform-specific code inside platform/data boundaries
- document architectural decisions in `docs/DECISIONS.md`
