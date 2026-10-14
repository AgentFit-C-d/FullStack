# B11d Approved ZIP export boundary

**Goal:** Only a stored approval bound to the same current, unexpired Preview may reach the ZIP byte exporter.

**Design:** Minimal server-stored approval metadata includes approval ID, Preview ID, fingerprint, approved time, and expiration. A pure export boundary regenerates the Preview fingerprint from transient input, checks the stored Preview against A's current owner-checked basis and submitted fingerprint, then checks approval/Preview IDs, approval fingerprint, and approval lifetime. It invokes the existing bounded ZIP exporter only after those conditions pass. The approval expiration must not exceed Preview expiration. Neither input files nor Diff are persisted here.

**Boundary:** The caller must still fetch approval and Preview under authentication/ownership in the final short transaction, verify recommendation/permission state, and record generation/audit. Constructing approval metadata from client input would bypass this boundary. No HTTP endpoint or DB implementation is introduced.

- [x] Write failing tests for valid exact ZIP bytes, wrong approval/Preview IDs, changed fingerprint/basis, expired approval, and invalid lifetime.
- [x] Add stored approval metadata and a guarded ZIP entry point.
- [x] Run targeted and full tests, update status, then calculate remaining B01–B12 top-level goals. `mvn package` found 80 tests: 79 passed, one Windows symlink test skipped; `git diff --check` passed. Top-level completion is 4/12 (33.3%), with eight goals remaining.
