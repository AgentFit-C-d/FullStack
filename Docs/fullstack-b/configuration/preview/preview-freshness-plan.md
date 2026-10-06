# B11c Preview freshness gate

**Goal:** A stored Preview metadata record may be approved or reused only while its version basis, expiration, and server-computed fingerprint still match a fresh regeneration.

**Design:** A pure B guard accepts minimal stored metadata (`previewId`, `PreviewBasis`, fingerprint, expiration), the A-supplied current owner-checked basis, the client-submitted fingerprint, a freshly recomputed B fingerprint result, and server time. It fails closed on missing inputs, changed basis, malformed/mismatched fingerprints, and `now >= expiresAt`. Approval additionally requires explicit `confirmation=true`. The guard neither reads nor writes DB and never stores original file content or Diff.

**Boundary:** The current basis and regenerated result must come from trusted server work, never from the browser alone. The caller still checks authentication/ownership, current recommendation membership, selected permissions, and (for export) an existing unexpired approval in a transaction. This class does not claim an approval was persisted.

- [x] Add failing tests for exact valid boundary, stale versions, expired Preview, changed fingerprint, and missing confirmation.
- [x] Add minimal metadata record and pure guard with distinct stale/expired/invalid errors.
- [x] Run targeted and full Maven tests; update B status and handoff docs. Full package: 76 tests discovered, 75 passed, one Windows symlink test skipped; `git diff --check` passed.
