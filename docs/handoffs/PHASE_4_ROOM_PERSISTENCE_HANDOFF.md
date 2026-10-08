# PocketDL — Phase Handoff: Room Persistence

> Handoff document for Room Persistence phase.
> The goal is to make any subsequent AI conversation or developer immediately understand the persistence architecture and current state.

---

# Phase 4 / 5 — Room Persistence

**Status:** `COMPLETED`

**Completed date:** `2026-10-08`

**Git branch:** `room-persistence`

---

## 1. Phase Goal

Transition the PocketDL native Android application from volatile in-memory repositories to a durable SQLite persistence layer powered by AndroidX Room, fulfilling:
1. Captured media items and associated quality/codec stream variants persist across process death.
2. Download task lifecycle states and queue order persist across process death.
3. Existing repository interfaces (`CapturedMediaRepository`, `DownloadRepository`) are preserved so ViewModels and Compose UI remain untouched.
4. Download binary files/bytes are strictly kept out of Room (reserved for filesystem/MediaStore).
5. User preferences/settings are kept out of Room (reserved for Jetpack DataStore per ADR-008).
6. Initial mock data seeds once on clean install without faking persistence on subsequent launches.
7. Database schema starts at initial version 1 with a clean, extensible migration registry ready for future version increments.

---

## 2. What Was Implemented

- **Room 2.8.5 + KSP 2.2.10-2.0.2:** Added to `libs.versions.toml`, configured in root `build.gradle.kts`, `app/build.gradle.kts`, and `gradle.properties` (`android.disallowKotlinSourceSets=false`).
- **Entity Schema:**
  - `captured_media`: Stores captured metadata (`id`, `title`, `source_url`, `source_domain`, `thumbnail_url`, `duration_seconds`, `duration_text`, `primary_resolution`, `primary_codec`, `total_size_bytes`, `total_size_text`, `media_type`, `created_at`, `extracted_at_text`).
  - `media_variants`: Stores extracted stream qualities (`id`, `captured_media_id`, `label`, `resolution`, `codec`, `container`, `bitrate_kbps`, `bitrate_text`, `size_bytes`, `size_text`, `direct_url`, `is_selected`). Foreign key references `captured_media(id)` with `CASCADE` delete and an explicit index on `captured_media_id`.
  - `download_tasks`: Stores download tasks and queue (`id`, `captured_media_id`, `title`, `source_url`, `source_domain`, `resolution_badge`, `codec_badge`, `status`, `status_text`, `progress`, `downloaded_bytes`, `total_bytes`, `downloaded_size_text`, `total_size_text`, `speed_bps`, `speed_text`, `eta_seconds`, `eta_text`, `local_file_path`, `destination_directory`, `error_message`, `created_at`, `completed_at`). Indexed on `status`.
- **DAOs (`CapturedMediaDao`, `DownloadTaskDao`):**
  - Reactive `Flow` queries for all lists and single items (`observeAll()`, `observeByMediaType()`, `observeByStatus()`, `getById()`).
  - Transactional operations: `insertWithVariants`, `deleteById`, `updateStatus`, `retryTask`, `reorderQueue`.
- **Database & Migrations (`PocketDlDatabase`, `DatabaseMigrations`):**
  - Database name: `"pocketdl.db"`, version 1.
  - Forward migration structure `DatabaseMigrations.ALL_MIGRATIONS` cleanly established; empty at version 1 without premature no-op migrations, and destructive fallback disabled (`fallbackToDestructiveMigration(false)`).
- **Repository Implementations:**
  - `RoomCapturedMediaRepository`: Implements `CapturedMediaRepository` with first-run seeding and Room DAO queries/mutations.
  - `RoomDownloadRepository`: Implements `DownloadRepository` with first-run seeding, reactive state transitions, and enqueuing directly from captured media.
- **Dependency Injection & App Wiring:**
  - `DefaultAppContainer` instantiates `PocketDlDatabase` and provides `RoomCapturedMediaRepository` and `RoomDownloadRepository`.
  - `PocketDlApplication.onCreate()` wires the Room repositories into `InMemoryRepositoryProvider`, seamlessly powering all existing 10 ViewModels and Compose screens without rewriting UI contracts.
- **Automated & Device Tests:**
  - `RoomPersistenceUnitTest` added to test suite covering entity mapping, foreign key integrity, and migration structure.
  - Device verification performed on physical Samsung Galaxy S22 Ultra (Android 14) via ADB.

---

## 3. What Was Intentionally NOT Implemented

- **Real HTTP Downloader Engine:** Kept strictly for Phase 6. No `OkHttp` streaming download loops or chunked file writing were added.
- **Foreground Services / Notifications:** Kept for Phase 7.
- **HLS Segment Downloading / FFmpeg Muxing:** Kept for Phase 11.
- **Real Browser Extension Socket Server:** Kept for Phase 9 & 10.
- **Settings DataStore:** Kept for Phase 13 per ADR-008. `SettingsRepository` remains in-memory until the dedicated DataStore phase.
- **Large binary file storage:** Kept strictly out of SQLite per ADR-007.

---

## 4. Files / Areas Changed

```text
android-app/
├── gradle/libs.versions.toml
├── build.gradle.kts
├── gradle.properties
└── app/
    ├── build.gradle.kts
    ├── src/main/java/com/pocketdl/app/
    │   ├── PocketDlApplication.kt
    │   ├── core/di/AppContainer.kt
    │   ├── data/
    │   │   ├── database/
    │   │   │   ├── PocketDlDatabase.kt
    │   │   │   ├── RoomMappers.kt
    │   │   │   ├── dao/
    │   │   │   │   ├── CapturedMediaDao.kt
    │   │   │   │   └── DownloadTaskDao.kt
    │   │   │   ├── entity/
    │   │   │   │   ├── CapturedMediaEntity.kt
    │   │   │   │   ├── MediaVariantEntity.kt
    │   │   │   │   └── DownloadTaskEntity.kt
    │   │   │   └── migrations/
    │   │   │       └── DatabaseMigrations.kt
    │   │   └── repository/
    │   │       ├── InMemoryRepositories.kt
    │   │       ├── RoomCapturedMediaRepository.kt
    │   │       └── RoomDownloadRepository.kt
    └── src/test/java/com/pocketdl/app/
        └── RoomPersistenceUnitTest.kt
```

### Important Implementation Notes

1. **Room 2.8.5 with KSP2:** AGP 9.4.1 utilizes built-in Kotlin and KSP2. Room 2.6.1 triggered an `unexpected jvm signature V` compiler error under KSP2 on suspend functions returning `Unit`. Upgrading to Room 2.8.5 (which has native KSP2 Analysis API support) completely resolved this issue without requiring experimental workarounds.
2. **AGP DisallowKotlinSourceSets:** AGP 9.4.1 enforces `android.disallowKotlinSourceSets=true` by default. Adding `android.disallowKotlinSourceSets=false` to `gradle.properties` enabled KSP source sets to integrate smoothly into the compile task graph.
3. **Repository Interoperability:** By updating `InMemoryRepositoryProvider` references at runtime in `PocketDlApplication.onCreate()`, every existing ViewModel default argument (`InMemoryRepositoryProvider.capturedMediaRepository`, `InMemoryRepositoryProvider.downloadRepository`) seamlessly binds to the live Room-backed database.

---

## 5. Architecture Changes

**See `08_ARCHITECTURE_DECISIONS.md` (ADR-007 & ADR-009)**

- Persistent metadata now flows reactively:
  `SQLite (pocketdl.db) → Room DAOs (Flow) → Room Repositories (Flow) → ViewModels (StateFlow) → Compose UI`.
- Media and task mutations now execute asynchronously on `Dispatchers.IO` and automatically emit updated snapshots to all active screens through Room's built-in table change tracking.

---

## 6. Dependencies Added / Removed

### Added

- `libs.room.runtime` (`androidx.room:room-runtime:2.8.5`) — SQLite object mapping.
- `libs.room.ktx` (`androidx.room:room-ktx:2.8.5`) — Coroutines and Flow integration for Room.
- `libs.room.compiler` (`androidx.room:room-compiler:2.8.5`) via `ksp` — Annotation processor for DAOs and entities.
- `libs.plugins.ksp` (`com.google.devtools.ksp:2.2.10-2.0.2`) — Kotlin Symbol Processing plugin.

### Removed

- None.

---

## 7. Automated Verification

```text
./gradlew.bat test                  ✅ PASS (100% pass rate: Phase3ViewModelStateUnitTest, RoomPersistenceUnitTest)
./gradlew.bat assembleDebug         ✅ PASS (Clean debug APK generation)
```

---

## 8. Physical Device Verification (Samsung Galaxy S22 Ultra)

- **Device:** Samsung Galaxy S22 Ultra (SM-S908E, Android 14, OneUI 6.1).
- **Connection:** ADB via TLS / TCP.
- **Verification Scenarios Conducted:**
  1. **Clean Installation & Launch:**
     - `adb install -r app/build/outputs/apk/debug/app-debug.apk` succeeded.
     - App launched via `am start -n com.pocketdl.app/.MainActivity`.
     - Initial seed data populated `pocketdl.db` (`pocketdl.db-wal` created in app database directory).
  2. **Captured Deletion Persistence Across Process Death:**
     - Navigated to Captured tab (3 initial items).
     - Deleted `"Keynote 2026"`; count updated to `All (2)`.
     - Force-stopped app process via `adb shell am force-stop com.pocketdl.app`.
     - Relaunched app.
     - Inspected UI hierarchy: `"Keynote 2026"` remained deleted and count remained `All (2)`.
  3. **New Detection Persistence Across Process Death:**
     - Navigated to Home tab; entered `https://example.com/testvideo.mp4` and triggered media sniffing.
     - New media item `"Extracted Stream from direct-media.com"` was captured and saved to Room.
     - Force-stopped app and relaunched.
     - Navigated to Captured tab: `"Extracted Stream from direct-media.com"` was intact and visible.
  4. **Download Enqueue & Status Persistence Across Process Death:**
     - Opened Quality bottom sheet for the newly captured media.
     - Selected `"2160p 60fps Ultra HD"` and tapped `"Start Download"`.
     - Task was enqueued into `download_tasks` in Room with status `QUEUED`.
     - Verified Downloads screen showed `"Extracted Stream from direct-media.com (Queued)"`.
     - Force-stopped app process via `adb shell am force-stop com.pocketdl.app`.
     - Relaunched app and navigated directly to Downloads screen.
     - Confirmed enqueued download task and counts (`All (7)`, `Queued (4)`) remained intact in Room.

---

## 9. Known Limitations / Deferred Work

1. `StorageRepository` and `SettingsRepository` currently retain in-memory implementations. `SettingsRepository` will be migrated to Jetpack DataStore in Phase 13, and `StorageRepository` will read physical filesystem quotas in Phase 12.
2. Download tasks are in the `QUEUED` / `DOWNLOADING` state representation, awaiting the real HTTP execution engine in Phase 6.

---

## 10. Next Phase Readiness

- **Ready for Phase 6:** Direct HTTP Download Engine.
- The queue, database schema, entity models, and repository contracts are fully prepared to support real HTTP byte-streaming tasks.
