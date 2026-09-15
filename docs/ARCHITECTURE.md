# Architecture

Tempo is deliberately local-first.

## Source of truth

SQLite is the timing source of truth. A session contains alternating `WORK` and `BREAK` intervals. The UI never increments persisted counters once per second; it derives duration from stored timestamps. This makes timers resistant to process death, background throttling and temporary network loss.

## Storage model

- `projects`: identity, color, symbol, weekly goal, archive/tombstone state
- `sessions`: project, intention, note, state, plan/outcome/quality, start/end timestamps
- `intervals`: exact work/break spans

Rows include `owner_id`, `updated_at`, and soft-delete tombstones. Guest rows use the local `guest` namespace. On first sign-in, guest data migrates only when the remote account has no local data, avoiding accidental account mixing.

## Sync

Firebase is optional. When configured, each authenticated user's objects live below `/users/{uid}`. Reconciliation compares `updatedAt` timestamps per object and keeps soft-deletion tombstones so deletions can propagate. If multiple devices produce active sessions, reconciliation keeps the most recently updated one active and safely closes older ones.

## UI

Jetpack Compose screens are intentionally shallow:

1. Today — active timer + instant project start
2. Projects — identities and targets
3. History — detailed, editable record
4. Insights — descriptive patterns
5. Settings — customization, sync, backup/privacy

## Battery model

No continuously ticking background service is required for correctness. The optional notification presents actions and elapsed-time UI; persisted timing remains timestamp-based.
