# B10d Catalog-backed Preview assembly

**Goal:** A single pure B entry point must apply release, selection, template, and Preview-fingerprint checks in that order for a transient Preview.

**Contract:** `CatalogPreviewAssembler.assemble(Path, approvedCatalogHash, StoredRecommendationState, currentBasis, CatalogPreviewRequest)` loads one local release bundle after checking the stored recommendation. The approved hash is an independently provisioned server value. The request carries an A-shaped basis, recommendation ID, environment target, selected tool keys, permission choices, existing-file state and bytes, and generator version; it has no caller-supplied generated-file field. The assembler rejects a recommendation/basis/membership mismatch, release/hash mismatch, duplicate or empty selections, unsupported tool/target/policy combinations, unavailable static template files, and unsafe or sensitive file contents. It returns only transient Preview files and digests.

**Limits:** The caller must still obtain the current basis and stored recommendation through A's authenticated, owner-checked read. B now checks recommendation status, ID, basis, and selected-tool membership from those supplied values; it cannot authenticate or fetch them. No HTTP endpoint, DB write, approval, or export authorization is introduced. Real Catalog release content and its independently approved hash remain unavailable.

- [x] Test success through a synthetic manifest-verified release and rejected stale basis, invalid selection, and hash mismatch.
- [x] Add a request record and assembler that reuses the existing validators and one loaded bundle.
- [x] Run targeted tests and full Maven package; update the B tracker and README. Full package: 66 tests discovered, 65 passed, one Windows symlink test skipped; `git diff --check` passed.
