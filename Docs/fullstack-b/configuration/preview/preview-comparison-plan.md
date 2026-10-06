# B10 Preview comparison implementation plan

> **For agentic workers:** Use Superpowers test-driven-development and verification-before-completion. Work only in B-owned code in the shared `FullStack` checkout.

**Goal:** Compare reviewed generated file candidates with only the existing files a user explicitly provided, without storing their contents or claiming to inspect the user's PC.

**Architecture:** A pure `configuration` package accepts in-memory generated and provided file values. It validates target/path consistency, relative path safety, and existing-state semantics before returning immutable Preview file projections with SHA-256 hashes and a deterministic unified diff. No repository, logger, cache, or endpoint is involved. A future Catalog-backed template renderer must supply the generated candidates; this task does not create real Client configuration files.

**Tech Stack:** Java 21 target, JUnit 5, UTF-8, SHA-256.

**Spec:** `Docs/AgentFit_AB_1차통합_개발기준.pdf` section 12 and `D:/capstone/Docs/api/03-configuration.draft.md` sections 3–5. The draft's exact DTO/limits are proposals; this task enforces only the comparison semantics and conservative path rules.

## Constraints and review focus

- `UNKNOWN` and `USER_SAYS_EMPTY` require no provided files; their outputs are `PROPOSAL` with null `beforeHash`/diff and `EXISTING_UNKNOWN` scope.
- `PROVIDED` requires at least one file. Only an exact `(targetKey, relativePath)` match may become `UPDATE` with `PROVIDED_FILE` scope.
- A path cannot be absolute, traverse a parent, contain backslash/NUL, or have empty, `.` or `..` segments.
- The same target key cannot map to two paths and the same path cannot map to two target keys. Duplicates and unmatched provided files are rejected.
- Hashes cover exact UTF-8 bytes; no newline or BOM normalization. Reject malformed UTF-16 strings rather than replacing characters during encoding.
- Diff is transient and deterministic. The output does not imply that downloaded bytes have been installed or that a local file still matches.

### Task 1: Comparison and path validation

**Files:** `configuration/preview/PreviewFileComparator.java`, `configuration/preview/PreviewFile.java`, `configuration/preview/PreviewInputFile.java`, `configuration/preview/ExistingState.java`, and `configuration/preview/PreviewFileComparatorTest.java`.

- [x] Write tests for unknown/provided states, exact hashes, path conflicts/traversal, unchanged and changed contents.
- [x] Run focused tests and observe the missing comparator failure.
- [x] Implement immutable value types, strict validation, SHA-256, and deterministic full-file unified diff.
- [x] Run focused tests; add edge tests for BOM/newline, empty originals, case-insensitive path collision, and malformed surrogate.
- [x] Run full Maven package and update B tracker with this partial B10 milestone. Result: 45 tests discovered, 44 passed, one Windows symlink test skipped; `git diff --check` passed. Compiled for Java 21 using JDK 24.

The comparator alone does **not** complete B10. Actual reviewed templates, allowed arguments, sensitive-input rejection, Preview fingerprint, approval, export, and A-owned persistence/access control remain separate work.
