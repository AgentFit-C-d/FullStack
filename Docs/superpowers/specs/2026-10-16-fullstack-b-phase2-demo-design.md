# Full Stack B Phase 2 demo integration design

**Date:** 2026-10-16

**Status:** Review

**Owner:** Full Stack B

**Baseline:** `origin/main` A Phase 2 UUID schema and existing B services

## Goal and constraints

By next week's demo, make B's real persistence and recommendation/Preview path work against the A Phase 2 schema without changing A-owned Java, SQL, API, or AI service files. A retains authentication, ownership, confirmed snapshot, current version, transaction orchestration, and public API ownership. B owns Catalog validation, combination decisions, selection and permissions, Preview/ZIP rules, and B9 persistence helpers. A real user flow is only called complete when the approved Catalog, actual AI Capability response, and authenticated A data are present; synthetic fixtures remain test evidence, not product behavior.

The current `feat/fullstack-b-phase2` branch descends from `develop` (`66a7d6a`). A Phase 2 is in `main` (`1046f46`) and not `develop`; merging main into this branch reports many add/add conflicts. Before implementation, create an A-compatible B work branch from `origin/main` and carry over the B audit document commit (`df1619b`) without modifying A-owned files. Keep the existing branch reference recoverable. This is a branch-base change, not a change to A's implementation. The untracked `Docs/fullstack-b/presentation/` must remain untouched.

## Approach considered

1. **Keep B on develop and add dual VARCHAR/UUID persistence.** This allows old tests to keep passing but creates a temporary production compatibility mode, leaves A Phase 2 adapters absent, and cannot validate the demo path. Reject for the demo.
2. **Merge main into the develop-based B branch.** This preserves develop ancestry but requires resolving A-auth/project/Audit, docs, and OpenAPI conflicts in A files. Reject because A code is out of scope.
3. **Base B work on main and change B-owned files only.** This uses A's UUID contract as-is, isolates B changes, and permits focused PostgreSQL tests. Choose this route. A-owned defects discovered during verification are recorded for A rather than patched in B's branch.

## B implementation slices

### 1. UUID persistence compatibility

Change B JDBC boundaries to use `UUID` for the `project.id` and B9 `project_id` columns in A Phase 2 V5, and UUID for `approved_by`/`reported_by`. B's service-facing project IDs may remain `String` to avoid altering A adapter contracts; parse and validate once at the persistence edge. Update recommendation writer/reader, Preview reader and metadata guard, history reader, project B deletion, approval and report writers. Preserve UUID values read from DB in the public String representation. Reject malformed IDs before SQL. Remove the old assumption that any nonblank VARCHAR(36) key is valid. Do not add a silent fallback to the old V1–V4 schema.

### 2. Recommendation and Preview connection

Use the existing A Phase 2 integration adapters and controller without editing them. B must accept A's UUID snapshot basis and store/reload a recommendation with all nine validated Capability claims, Catalog release/hash, supported items, notices, and success Audit in the A-owned transaction. Preview must restore the exact stored recommendation and selected items, compare server-owned current basis and Environment with request values, validate approved Catalog and permission choices, then persist only B5–B8 metadata. It must allow a valid partial selection and return unmet required Capability keys. A's Preview store adapter currently appears to omit a save-time A snapshot recheck; do not claim that race is solved by B unless a B-owned guard can prove the full current basis in the same transaction. Record A-side changes separately.

### 3. Remaining finalization path

After recommendation and Preview pass on PostgreSQL, connect B approval, ZIP generation, content-free history, and USER report helpers to UUID actor/project IDs. A must still supply authenticated owner-checked current state, atomic saves, Audit, and HTTP responses. No browser-provided basis or Catalog hash becomes trusted state. Approval and download may proceed for a valid partial configuration while keeping unmet required Capability warnings visible. B code must continue to avoid storing raw existing files, generated content, Diff, or secrets.

## External dependencies and demo claims

- The present AI service `POST /internal/v1/analysis` returns `result: {kind: CONTRACT_TEST_DOUBLE}`. A's AI Capability adapter expects `claims` and `questions`; until the AI owner implements an agreed 9-Capability contract, end-to-end recommendation creation cannot be claimed. B may use deterministic test fixtures in its own tests only.
- Candidate discovery from skills.sh does not make a Catalog release approved. A concrete Claude Code OS/version, support evidence, templates, permissions, combination verification, and independently configured approved hash are required before a real recommendation demo. Unapproved candidates stay inactive.
- A Phase 2 spec/tasks in main are still Draft/partly unchecked. The B branch will report A-owned blockers rather than silently changing A files.

## Verification and acceptance

1. Fresh PostgreSQL applies the intended A Phase 2 migration. UUID USER/PROJECT and all B9 FK types are checked from the actual database; never rely on H2 alone for UUID binding semantics.
2. B-focused tests first fail against the current String/long JDBC binds, then pass after B changes. Cover recommendation write/read; Preview write/read; approval; ZIP history and USER report; B child deletion; malformed/foreign IDs; and rollback after an injected failure. A-owned files must remain unchanged in the diff.
3. Run the JDK 21 Maven suite and relevant PostgreSQL integration tests. If this machine has no JDK 21, obtain/use an approved runtime or report that exact limitation; do not mark those checks passed based on JDK 17/24 alone.
4. A demo completion report distinguishes: B persistence/decision verified; A-authenticated API verified; AI Capability verified; approved real Catalog verified; final download verified. Each is marked only by observed tests or a live run.

## Out of scope

Changing A's authentication, ownership, project/profile schema or adapters, changing FastAPI's response, approving unverified Catalog candidates, adding a second DB or message queue, and declaring the entire Phase 2 complete from synthetic unit tests.
