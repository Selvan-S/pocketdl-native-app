# PocketDL — Start Here

A beginner-friendly setup and development guide for building PocketDL as a native Android application.

## 1. What we are building

PocketDL is an Android-first download manager that works with a browser extension.

### Core product flow

```text
Browser
  ↓
PocketDL Extension detects public/user-authorized media
  ↓
Extension sends capture information to PocketDL
  ↓
PocketDL Captured inbox
  ↓
User reviews media / chooses quality
  ↓
Download Manager
  ↓
Android storage
  ↓
Open / Share downloaded file
```

There is **no in-app browser**.

PocketDL must not bypass DRM, authentication, paywalls, private-content restrictions, or other access controls. Detection and downloading are intended for public or user-authorized content.

---

## 2. Recommended technology

### Application

- Kotlin
- Jetpack Compose
- AndroidX
- Navigation Compose
- ViewModel
- Kotlin Coroutines + Flow
- Repository pattern
- Room for persistent structured data
- DataStore for simple preferences
- Dependency Injection (Hilt)
- OkHttp for HTTP networking where appropriate
- Kotlin serialization for local/domain payloads where useful
- WorkManager for appropriate deferrable/persistent work
- Foreground Service for user-visible long-running downloads when Android policy/platform rules require it
- MediaStore / Storage Access Framework for user-visible file storage and access
- Android Notifications

### Design-to-code/tooling

- Google Stitch — design source of truth
- Stitch MCP — gives Antigravity access to Stitch design context
- Google Antigravity — AI development environment/agent
- Android Studio — official Android IDE and final Android build/debug environment
- Git + GitHub — source control

### Important version baseline

Use the current stable Android tooling compatible with the project template at creation time. As of this guide's verification date (2026-10-08), official sources show:

- Android Studio Rabbit 1 (2026.2.1) — stable
- Android 16 / SDK 36
- Android Gradle Plugin 9.4.0
- Gradle 9.6.0 with AGP 9.4.0
- JDK 17 with AGP 9.4.0
- Kotlin 2.4.20 — current stable Kotlin line
- Compose BOM 2026.08.00 shown in the current Android documentation

Do not blindly force these versions into an existing project. For a new project, let Android Studio create the compatible Gradle/plugin setup, then verify the versions.

---

## 3. What you need before coding

### Accounts

- Google account
- Google Stitch access
- Google Cloud project for Stitch API access if Stitch asks for it
- GitHub account/repository
- Optional: Google Play Console account later, when publishing

### Software

Install:

1. Android Studio stable
2. Git
3. Chrome
4. Node.js 18+ for Stitch/Antigravity MCP tooling
5. Antigravity IDE

Node.js is primarily a **tooling requirement for the Stitch/Antigravity workflow**. PocketDL itself is native Kotlin and does not need Node.js at runtime.

---

## 4. Android Studio setup

Open Android Studio and use the SDK Manager to verify the required SDK packages.

At minimum, prepare:

- Android SDK Platform 36
- Android SDK Build-Tools 36.x
- Android SDK Platform-Tools
- Android Emulator (optional if you will mainly use a physical phone)
- Android SDK Command-line Tools

Use a physical Android phone for serious download testing. The emulator is useful for normal UI/navigation tests but should not be your only test device.

### Enable developer testing on a physical phone

On your Android phone:

1. Enable Developer Options.
2. Enable USB debugging.
3. Connect the phone by USB.
4. Accept the RSA/debugging prompt.
5. Verify the device in Android Studio.

From a terminal, verify:

```bash
adb devices
```

Expected shape:

```text
<device-id>    device
```

If it says `unauthorized`, unlock the phone and accept the debugging prompt.

---

## 5. Mandatory preflight checks

Before asking Antigravity to build anything, run these checks.

### Java

```bash
java -version
```

The Android Gradle Plugin baseline uses JDK 17.

### Git

```bash
git --version
```

### Node

```bash
node --version
npm --version
```

Node 18+ is sufficient for the Stitch/Antigravity codelab workflow.

### ADB

```bash
adb version
adb devices
```

### Android SDK

In Android Studio:

```text
Tools → SDK Manager
```

Verify SDK 36 and Build-Tools 36.x are installed.

---

## 6. Create the repository first

Recommended repository:

```text
PocketDL/
```

Initial files:

```text
PocketDL/
├── README.md
├── LICENSE
├── .gitignore
├── docs/
│   ├── DESIGN.md
│   ├── ARCHITECTURE.md
│   ├── DEVELOPMENT_PLAN.md
│   ├── EXTENSION_PROTOCOL.md
│   └── DECISIONS.md
└── android-app/
```

Keep the Android application in its own folder so a future browser extension can live beside it:

```text
PocketDL/
├── android-app/
├── browser-extension/        # later
└── docs/
```

Do not build the extension in the first Android UI phase.

---

## 7. Git baseline

Before AI development:

```bash
git init
git add .
git commit -m "chore: initialize PocketDL project"
```

Create small commits at the end of every successful phase.

Example:

```text
feat: implement home screen
feat: implement captured inbox
feat: add download domain model
feat: add download engine
feat: integrate extension capture
```

This makes AI-generated mistakes easy to revert.

---

## 8. Definition of done for setup

Do not start Phase 1 until all of these are true:

- Android Studio opens normally.
- A test Android app can run.
- Physical device appears in Android Studio or emulator works.
- `adb devices` reports a usable device.
- JDK 17 is available to Gradle.
- Git works.
- Node 18+ works.
- Antigravity opens a dedicated PocketDL workspace.
- Stitch project exists and is named.
- Stitch API key can be created.
- Stitch MCP can be connected to Antigravity.
- Antigravity can list the Stitch project.
- A Git commit exists before major AI implementation begins.

---

## 9. Important beginner rule

Do not ask the AI agent to build the complete application in one request.

Use this loop:

```text
Plan
 ↓
One phase
 ↓
Build
 ↓
Run
 ↓
Test
 ↓
Review
 ↓
Commit
 ↓
Next phase
```

This is the safest way to learn mobile development while keeping the project maintainable.

---

## 10. Official references

- Android Studio install: https://developer.android.com/studio/install
- Android Studio releases: https://developer.android.com/studio/releases/
- Android 16 SDK setup: https://developer.android.com/about/versions/16/setup-sdk
- Android architecture recommendations: https://developer.android.com/topic/architecture/recommendations
- Foreground services: https://developer.android.com/develop/background-work/services/fgs
- Kotlin releases: https://kotlinlang.org/docs/releases.html
- Stitch + Antigravity MCP codelab: https://codelabs.developers.google.com/design-to-code-with-antigravity-stitch
