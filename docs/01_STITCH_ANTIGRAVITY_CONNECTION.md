# PocketDL — Stitch ↔ Antigravity ↔ Android Studio Connection Guide

This explains what each tool does and how they fit together.

## 1. Understand the roles

```text
Google Stitch
    = design tool

Stitch MCP
    = bridge that lets Antigravity read Stitch project/design context

Antigravity
    = AI coding/development agent

Android Studio
    = native Android IDE, Gradle/build/debug/device tooling

Git/GitHub
    = source control and rollback
```

Android Studio is **not replaced by Antigravity**. You can use Antigravity as the AI development environment and Android Studio as the authoritative Android project/build/debug environment.

---

## 2. Recommended workflow

```text
Stitch
  ↓
Stitch MCP
  ↓
Antigravity
  ↓
Kotlin + Jetpack Compose code
  ↓
Git repository
  ↓
Android Studio
  ↓
Physical Android device / Emulator
```

The official Google Stitch/Antigravity codelab demonstrates the MCP connection and a Design DNA workflow. That codelab uses React/Tailwind for its example; PocketDL should adapt the same **design-context bridge** to a native Kotlin/Compose implementation rather than copying its web stack.

---

## 3. Prepare Stitch

You already have the PocketDL design. Before connecting it:

1. Open the PocketDL Stitch project.
2. Give the project a clear name: `PocketDL`.
3. Make sure the six screens and two modals are finalized enough to serve as the visual source of truth.
4. Ensure screen naming is clear.
5. Keep the same design language across screens.

Recommended screen names:

```text
Home
Captured
Downloads
Settings
MediaDetails
DownloadDetails
```

Recommended modal/sheet names:

```text
QualitySelection
CaptureAction
```

---

## 4. Create Stitch API key

According to the current Google codelab workflow:

1. Open Google Stitch.
2. Open your profile/settings area.
3. Go to the API key section.
4. Create a Stitch API key.
5. Store it securely.

Never commit the API key to Git.

Do not paste it into source files.

Treat it like a password/API credential.

---

## 5. Configure Stitch MCP in Antigravity

In Antigravity:

1. Open the Agent Manager.
2. Open MCP Servers.
3. Open the MCP store/options.
4. Search for `Stitch`.
5. Install the Stitch MCP server.
6. Enter the Stitch API key when prompted.
7. Open the PocketDL workspace.

The exact UI labels may change as Antigravity evolves, so use the current Antigravity MCP UI rather than relying on an old screenshot.

---

## 6. Verify the connection before writing code

In the Antigravity Agent chat, run a harmless verification request such as:

```text
List my Stitch projects.
```

Expected result:

```text
PocketDL
```

If Antigravity cannot see PocketDL, stop here and fix MCP configuration before implementation.

---

## 7. Create a DESIGN.md from Stitch

Do this before implementing the UI.

Prompt Antigravity:

```text
Use the Stitch MCP to inspect the PocketDL project.

Extract the design system and create docs/DESIGN.md.

Capture:
- screen names
- navigation structure
- colors
- typography
- spacing scale
- corner radii
- component styles
- button styles
- cards
- progress indicators
- bottom navigation
- modals/bottom sheets
- empty states
- loading states
- success states
- error states
- light/dark theme rules
- image/icon treatment

Do not redesign anything.
Treat Stitch as the visual source of truth.

This document is a reference for implementing PocketDL in native Kotlin + Jetpack Compose.
Do not generate React, Tailwind, Vite, or Capacitor code.
```

Review the generated `DESIGN.md` manually.

The goal is not blindly trusting AI extraction. The goal is to make the visual rules explicit.

---

## 8. Important point about Stitch and native Android

The current official Stitch/Antigravity codelab demonstrates a web implementation using React + Tailwind.

That does **not** mean PocketDL must use React.

For PocketDL:

```text
Stitch design metadata
        ↓
Antigravity understands the design
        ↓
Antigravity implements the design in
Kotlin + Jetpack Compose
```

The MCP is being used for **design context**, not as a command that dictates the runtime technology.

---

## 9. Android Studio relationship

You have two good working patterns.

### Pattern A — Antigravity is primary coding IDE

```text
Antigravity
  ↓ edit Kotlin/Gradle files
Git
  ↓
Android Studio
  ↓
Build / Logcat / Device / Profiler
```

Use this if you prefer Antigravity's agent experience.

### Pattern B — Android Studio is primary IDE

```text
Android Studio
  ↓
Open same Git repository
  ↓
Antigravity edits the same repository
```

Use whichever editor is more comfortable, but keep Android Studio installed because it is the official Android development environment and provides Android-specific tooling.

---

## 10. First connection test

After creating the native Android project, ask Antigravity:

```text
Verify the PocketDL Android project.

Do not modify application code yet.

Check:
- Gradle configuration
- Android plugin configuration
- Kotlin configuration
- compileSdk
- targetSdk
- minSdk
- Compose configuration
- JDK used by Gradle
- Android SDK availability

Report any compatibility issue and the smallest safe fix.
```

Then open the same project in Android Studio and confirm it syncs successfully.

---

## 11. Visual implementation loop

For each screen:

```text
Stitch screen
   ↓
Antigravity implements one screen
   ↓
Build/run
   ↓
Compare to Stitch
   ↓
Fix spacing/typography/components
   ↓
Commit
```

Do not implement all six screens first and visually compare them only at the end.

---

## 12. Safety rules for AI-generated code

Always ask the agent to:

- make the smallest change needed
- reuse existing components
- avoid duplicate components
- avoid unnecessary dependencies
- avoid generated magic constants where a design token is appropriate
- keep networking/download logic outside UI composables
- keep Android service code outside screen code
- write tests for non-trivial business logic
- run Gradle checks after structural changes
- never commit secrets

---

## 13. Official verification source

Google's current Stitch/Antigravity codelab explicitly documents:

- Stitch project setup
- Stitch API key generation
- Antigravity MCP installation
- verifying the connection by listing Stitch projects
- fetching design context into a `DESIGN.md`

https://codelabs.developers.google.com/design-to-code-with-antigravity-stitch
