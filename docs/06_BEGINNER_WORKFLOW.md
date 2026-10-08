# PocketDL — Beginner's Day-to-Day Workflow

## When starting work

1. Open Git repository.
2. Open Antigravity in the PocketDL workspace.
3. Open Android Studio for Android-specific build/device/logging work.
4. Make sure the working tree is clean or understand your current changes.
5. Decide which phase/task you are working on.

Useful commands:

```bash
git status
git log --oneline -5
```

## Before asking the agent

Give it a specific task.

Bad:

```text
Build PocketDL completely.
```

Good:

```text
Implement Phase 3 Captured screen only.
Read docs/DEVELOPMENT_PLAN.md and docs/DESIGN.md first.
```

## After the agent changes code

Run:

```bash
./gradlew test
```

On Windows PowerShell you can also use:

```powershell
.\gradlew.bat test
```

For Android builds, use the project Gradle tasks appropriate to the generated project, for example:

```powershell
.\gradlew.bat assembleDebug
```

Then run the app on your device from Android Studio.

## When something breaks

Do not immediately ask the agent to rewrite everything.

Give it the exact error.

Example:

```text
Gradle sync fails with this exact error:
<error>

Do not rewrite unrelated code.
Identify the root cause, make the smallest safe change, then rerun the failing check.
```

## Learn while developing

You do not need to learn all Android development before starting.

Learn these concepts as they appear:

```text
Activity
 ↓
Compose
 ↓
Navigation
 ↓
ViewModel
 ↓
StateFlow
 ↓
Repository
 ↓
Room
 ↓
Service
 ↓
Android storage
```

For PocketDL, understanding **state + lifecycle + background execution + storage** is more valuable than memorizing Compose syntax.

## Recommended first milestone

Your first meaningful milestone is NOT a downloader.

It is:

```text
PocketDL launches
 ↓
Home works
 ↓
Captured works
 ↓
Downloads works
 ↓
Settings works
 ↓
Mock media details works
 ↓
Two modals work
```

Then start the real downloader.

## Keep one written source of truth

Maintain:

```text
README.md

docs/
├── DESIGN.md
├── ARCHITECTURE.md
├── DEVELOPMENT_PLAN.md
├── EXTENSION_PROTOCOL.md
└── DECISIONS.md
```

Whenever an important architectural decision changes, document it.

## Beginner rule for AI

AI is your implementation assistant, not the owner of the architecture.

You should always know:

- what phase you are in
- what the phase should produce
- how you will test it
- what files changed
- what remains before moving on
