# PocketDL — Phase Handoff Template

> Copy this template into `docs/handoffs/PHASE_<N>_HANDOFF.md` at the end of every major phase.
>
> The goal is to make the next AI chat independent from the previous conversation.

---

# Phase <N> — <Phase Name>

**Status:** `IN_PROGRESS | COMPLETED | BLOCKED`

**Completed date:** `YYYY-MM-DD`

**Git commit:** `<commit hash>`

**Git commit message:** `<commit message>`

---

## 1. Phase Goal

<Describe what this phase was supposed to accomplish.>

---

## 2. What Was Implemented

- <item>
- <item>
- <item>

---

## 3. What Was Intentionally NOT Implemented

- <item>
- <item>

This is important because the next AI agent must not assume unfinished work was accidentally forgotten.

---

## 4. Files / Areas Changed

```text
path/to/file
path/to/file
path/to/directory/
```

### Important implementation notes

<Short notes about significant changes.>

---

## 5. Architecture Changes

**None** / **See `08_ARCHITECTURE_DECISIONS.md`**

If architecture changed, document:

```text
Decision:
Reason:
Impact:
Alternatives considered:
```

---

## 6. Dependencies Added / Removed

### Added

- `<dependency>` — `<reason>`

### Removed

- `<dependency>` — `<reason>`

### No changes

<Use this when nothing changed.>

---

## 7. Automated Verification

Record actual commands and results.

```text
./gradlew test                  ✅ / ❌
./gradlew lint                  ✅ / ❌ / NOT RUN
./gradlew assembleDebug        ✅ / ❌
./gradlew connectedDebugAndroidTest  ✅ / ❌ / NOT RUN
```

Do not mark a check as passing unless it was actually run.

---

## 8. Manual Verification

### Device / Emulator

Device:
`<device/model/emulator>`

Android version:
`<version>`

### Verified

- [ ] App launches
- [ ] Required screen/feature works
- [ ] Back navigation works
- [ ] Configuration survives restart where expected
- [ ] No obvious crash
- [ ] Logs checked for unexpected errors

### Notes

<Manual testing notes.>

---

## 9. Known Issues

| Issue | Severity | Workaround | Follow-up |
|---|---|---|---|
| <issue> | Low/Medium/High | <workaround> | <phase/issue> |

Write `None` when there are no known issues.

---

## 10. Important Decisions Made During This Phase

- <decision>
- <decision>

If an item is architectural, copy the final decision into `08_ARCHITECTURE_DECISIONS.md` as well.

---

## 11. Next Phase

**Phase:** `<next phase number and name>`

### Expected starting point

<Describe the exact state the next phase should assume.>

### Do not change yet

- <boundary>
- <boundary>

### Suggested first verification

<What the next AI agent should inspect or run before changing code.>

---

## 12. Notes for the Next AI Chat

Paste only the necessary context here.

```text
The previous phase is complete.
Read the repository documentation and this handoff before making changes.
Do not redo completed work.
Do not start features outside the next phase scope.
```

---

# Handoff Completion Rule

A phase is not considered complete until:

1. The implementation is finished within scope.
2. Required automated checks have been run.
3. Manual verification has been performed where required.
4. Known issues are documented.
5. `07_PROJECT_STATUS.md` is updated.
6. Architecture changes are recorded when applicable.
7. This handoff file is completed.
8. A Git checkpoint commit has been created.
