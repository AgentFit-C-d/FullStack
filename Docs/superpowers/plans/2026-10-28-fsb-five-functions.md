# Fullstack B Remaining Functions Implementation Plan

> **For agentic workers:** Implement task by task with tests before code.

**Goal:** Complete the five partial B paths as far as the current A/AI contract and real Skill metadata allow.

**Architecture:** Keep verified Catalog recommendations separate from skills.sh discoveries. Extend user-selected Skill discovery and export, then expose explicit optional intent, evidence and lifecycle checks through B-owned APIs/UI. Preserve current recommendation and Preview fingerprints.

**Tech Stack:** Java 21, Spring Boot, PostgreSQL, React/Vite, skills.sh HTTP.

**Spec:** `D:/capstone/Docs/team-prds/full-stack-b/PRD.md`, FSB-01/04/05/11/12.

## Global Constraints

- Modify B-owned code only; A and AI files remain untouched.
- Remote Skill names and security scans do not prove client, OS, permissions or combinations.
- A user may explicitly choose an unverified Skill ZIP; a changed content hash blocks export.
- Keep the verified recommendation/Preview/approval contract intact.

## Review Focus

- External search unavailable: show a source error without erasing prior candidates.
- Search returns changed content: display the new hash; never export the old hash.
- Anonymous search/export: reject before external network access.
- Unsupported optional declaration: do not infer OPTIONAL from NOT_REQUESTED.
- Catalog version or permission drift: old recommendations must remain readable as STALE, not reusable for approval.

---

### Task 1: Live Skill discovery and pinned export

- [x] Test authenticated bounded search, anonymous rejection and remote failure.
- [x] Implement B search API using existing collector and preserve source/hash in response.
- [x] Test direct pinned export and changed hash rejection.
- [x] Connect a search form and selected ZIP in the service UI.
- [x] Run targeted Java and frontend tests/build.

### Task 2: Optional capability intent

- [x] Test explicit B user optional selection against stored AI claims; no implicit promotion.
- [x] Expose project-scoped optional intent and feed it to B recommendation evaluation.
- [x] Verify saved optional recommendation and Preview path.

### Task 3: Recommendation evidence

- [x] Test clear capability, source, support, risk and permission evidence per item.
- [x] Show the evidence in the service UI and saved response.

### Task 4: Installation and recovery

- [x] Test ZIP instructions for location, authentication, harmless check and recovery.
- [x] Show the same guidance in the UI; never assert automatic installation.

### Task 5: Re-evaluation

- [x] Test candidate hash drift and Catalog version/permission drift.
- [x] Block stale Preview/approval and expose a fresh recommendation action.
- [x] Run focused regression and, if available, PostgreSQL/API smoke tests.
