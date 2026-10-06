# Full Stack B current status

Source: `Docs/AgentFit_AB_1차통합_개발기준.pdf` (sections 7–12) and the B PRD in `D:/capstone/Docs/team-prds/full-stack-b/PRD.md`.

The repository has no `.specify/` prerequisite script or feature `tasks.md` yet, so the Spec Kit implement command cannot consume an approved task plan. This tracker records the B work without claiming those missing artifacts exist. It does not change A or AI ownership.

## Phase 1 — B domain foundation

- [x] B01 Define the fixed nine Capability IDs and four recommendation outcomes in `services/core-api`.
- [x] B02 Reject malformed Catalog references, unsupported Capability IDs, dependency cycles, and unverified support in a pure domain validator.
- [x] B03 Implement deterministic recommendation planning for required capabilities, declared installed tool versions, environment matching, dependencies, conflicts, component overlap, and combination verification. A catalog ID alone never proves an existing usable capability.
- [x] B04 Cover the four outcomes and fail-closed edge cases with unit tests.
- [x] B04a Validate reviewed permission mappings separately from the AI decision: ASK_EACH_TIME defaults, unsupported Ask, required DENY, missing/duplicate mappings.

## Phase 2 — A and AI integration

- [ ] B05 Agree on and implement A's authenticated, owner-checked snapshot reader and B1–B4 persistence. Preserve the confirmation event and immutable version basis. A concrete [handoff proposal](integration/ab-ai-contract-proposal.md) is ready for review, not agreed.
- [ ] B06 Agree on the AI Capability request/response contract; reject unknown keys and excluded profile fields before saving anything. The [handoff proposal](integration/ab-ai-contract-proposal.md) records the missing wire and omission semantics.
- [x] B06a Add a pure AI Capability intake guard for the fixed nine IDs, required/optional/undetermined assessments, trusted A-provided source/question field paths, duplicates, and missing evidence. Wire parsing, failure envelopes, ownership, and persistence remain B06 work (see [intake boundary](recommendation/ai-capability-intake-plan.md)).
- [x] B06b Reject an empty AI Capability claim list, even if the response contains questions, so an empty assessment cannot be mistaken for `NO_ADDITIONS_NEEDED`. Partial-list semantics and the wire contract remain open (see [intake boundary](recommendation/ai-capability-intake-plan.md)).
- [ ] B07 Publish the read-only Catalog and recommendation endpoints after authentication, ownership, error envelope, and transaction boundaries exist.
- [ ] B08 Load a real versioned Claude Code Catalog bundle with manifest hashes, reviewed support records, and source evidence. Codex is a subsequent target. Keep unsupported combinations inactive; exact OS/version support remains unverified.
- [x] B08a Implement Catalog manifest/file hash and path validation before parsing release content (see [release validation](catalog/catalog-release-validation-plan.md)). The symlink rejection branch is implemented, but its filesystem test was skipped by local Windows permissions.
- [x] B08b Parse the six hash-verified Catalog files against a proposed strict schema and reject unknown references, duplicate keys, missing support/permission evidence, and unscoped combination claims (see [semantic parsing](catalog/catalog-semantic-parser-plan.md)). This is synthetic-domain validation, not real Client verification.
- [x] B08c Model verified combinations for one exact OS/Client/version target, preserve source evidence, and reject ambiguous support records and invalid combinations (see [targeted combinations](catalog/targeted-combinations-plan.md)). Real release verification is still B08 work.

## Phase 3 — permission and configuration

- [ ] B09 Validate selected tools, supported permission mappings, required DENY, dependencies, and conflict rules.
- [x] B09a Add an in-memory selection guard for exact environment support, dependencies, conflicts, component overlap, and selected-tool permission policies. It is now called by the transient Preview assembler; A's authenticated current-basis lookup remains open.
- [x] B09b Allow multi-tool selection only when the exact complete set and environment target have a reviewed combination record. No real combinations are active yet.
- [x] B09c Require an A-supplied current recommendation snapshot before Preview assembly; reject a non-RECOMMENDED result, changed basis, different recommendation ID, or tool outside its items (see [Preview recommendation gate](recommendation/preview-recommendation-gate-plan.md)). A's authenticated lookup and persistence remain open.
- [ ] B10 Generate Preview only from reviewed templates; compare provided files without retaining content, originals, or diff.
- [x] B10a Add a pure in-memory comparator for reviewed file candidates and explicitly provided originals. It validates relative paths/target conflicts, computes exact UTF-8 SHA-256 hashes, and labels unprovided files as proposals (see [comparison](configuration/preview/preview-comparison-plan.md)).
- [x] B10b Copy manifest-verified static Catalog templates into Preview candidates only when an independently approved Catalog hash matches. Reject unindexed sources, unknown tools, malformed mappings, and unsafe/conflicting output paths. Real templates, approval provisioning, and API integration remain open (see [static templates](configuration/assembly/catalog-static-template-plan.md)).
- [x] B10c Screen identifiable credential literals in both generated and user-provided Preview files before hashes/diffs are built; allow exact environment references and return a generic rejection. This pattern screen is not a complete secret detector, and HTTP ingress still needs size and logging controls (see [sensitive input](configuration/preview/preview-sensitive-input-plan.md)).
- [x] B10d Assemble a transient Preview through one Catalog load, approved hash/current basis match, exact-target selection and permission validation, static template rendering, sensitive-input comparison, and fingerprinting. A's owner-checked current snapshot and recommendation membership are still external prerequisites (see [assembly](configuration/assembly/catalog-preview-assembly-plan.md)).
- [x] B10e Enforce the proposed Preview assembly count/code-point/UTF-8-content budgets before request processing and fingerprinting; exact HTTP JSON body enforcement remains future API work (see [limits](configuration/assembly/preview-budget-plan.md)).
- [x] B10f Match the request's OS/Client/version to a separately supplied, owner-checked current Environment target before Catalog loading; reject missing or spoofed targets (see [assembly](configuration/assembly/catalog-preview-assembly-plan.md)). A must still obtain that target through an authenticated read.
- [x] B10g Require every selected tool to have at least one reviewed indexed template or guide before Preview rendering; one tool's output cannot silently stand in for another (see [static templates](configuration/assembly/catalog-static-template-plan.md)).
- [ ] B11 Bind approval to an immutable Preview fingerprint and current basis; regenerate exact bytes for ZIP export.
- [x] B11a Compute transient selection/content/final fingerprints from an A-shaped version basis, sorted choices, exact file hashes, and generator version. A's current-basis lookup, approval persistence, and expiry remain open (see [fingerprint](configuration/preview/preview-fingerprint-plan.md)).
- [x] B11b Regenerate a bounded in-memory ZIP only when fresh Preview inputs reproduce an expected fingerprint; tests inspect decompressed paths and exact bytes. A's approval/current-basis checks, persistence, audit, and HTTP export remain open (see [ZIP export](configuration/export/preview-zip-export-plan.md)).
- [x] B11c Reject stale/expired Preview metadata and mismatched submitted/regenerated fingerprints before approval or reuse; require explicit confirmation for approval. A's trusted current snapshot, ownership, recommendation status, persisted approval, and transaction remain open (see [freshness](configuration/preview/preview-freshness-plan.md)).
- [x] B11d Guard ZIP generation with server-stored approval metadata bound to the same Preview ID, fingerprint, current basis, and expiration; raw ZIP helper is package-private. A's owner-checked approval lookup, transaction, audit, and HTTP response remain open (see [approved export](configuration/export/approved-export-plan.md)).
- [x] B11e Return approved ZIP bytes and content-free GENERATED history metadata from one B entry point after successful regeneration; defensively copy the ZIP bytes (see [approved generation](configuration/history/approved-generation-plan.md)). A's trusted lookup, atomic DB save, and HTTP transfer remain open.
- [ ] B12 Persist only B5–B9 metadata, audit, generation history, and user reports; verify deletion and stale-state handling.
- [x] B12a Validate a user's APPLIED/FAILED report and its allowed failure reason; assign USER source and report time on the server without treating the statement as verified installation (see [report boundary](configuration/report/user-application-report-plan.md)). Persistence, ownership, audit, and deletion remain B12 work.
- [x] B12b Project a regenerated Preview into immutable generation-history metadata containing IDs, basis, fingerprint, and file path/action/hash only; mark changed basis STALE and verification NOT_RUN/NONE (see [history plan](configuration/history/generation-history-plan.md)). The public B generator now enforces ZIP-before-history order; storage, audit, and deletion remain open.

Detailed milestone results and test counts: [HISTORY.md](HISTORY.md).
