# B11c Preview freshness gate

**Goal:** A stored READY Preview may be approved while its basis, expiry, and submitted fingerprint match current state; export additionally requires a fresh regeneration from resubmitted input to match the stored fingerprint. The draft approval request has no original file content.

**Design:** A pure B guard accepts minimal stored metadata (`previewId`, `PreviewBasis`, fingerprint, expiration), the A-supplied current owner-checked basis, the client-submitted fingerprint, and server time. `requireStoredCurrent` supports approval without discarded original bytes; `requireCurrent` also accepts a freshly recomputed B result for export. Both fail closed on missing inputs, changed basis, malformed/mismatched fingerprints, and `now >= expiresAt`. `PreviewApprovalIssuer.issueStoredReady` separately requires explicit `confirmation=true`. Neither guard nor issuer reads/writes DB or stores original file content or Diff.

**Boundary:** The current basis and, for export, regenerated result must come from trusted server work, never from the browser alone. A must load an owner-checked READY Preview for approval and check authentication/ownership, current recommendation membership, selected permissions, and (for export) an existing unexpired approval in a transaction. This class does not claim an approval was persisted.

- [x] Add failing tests for exact valid boundary, stale versions, expired Preview, changed fingerprint, and missing confirmation.
- [x] Add minimal metadata record and pure guard with distinct stale/expired/invalid errors.
- [x] Run targeted and full Maven tests; update B status and handoff docs. Full package: 76 tests discovered, 75 passed, one Windows symlink test skipped; `git diff --check` passed.
