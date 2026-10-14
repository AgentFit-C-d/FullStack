# B11 ZIP byte regeneration implementation plan

> **For agentic workers:** Use Superpowers test-driven-development and verification-before-completion. This is a B-owned pure domain slice in the shared `FullStack` checkout.

**Goal:** Regenerate a ZIP only when the supplied Preview inputs reproduce an expected fingerprint, with decompressed paths and UTF-8 bytes matching the Preview files exactly.

**Architecture:** Recompute the transient Preview with `PreviewFingerprint.compute(input)`; compare the expected fingerprint before writing anything; stream only its sorted file rows into an in-memory ZIP. Recheck each `afterHash` against the written bytes and never include user-provided originals or Diff. The caller must separately enforce A's owner/current-basis/approval/expiry checks before invoking this helper.

**Tech Stack:** Java 21 target, JDK `ZipOutputStream`, JUnit 5, SHA-256.

**Spec:** `Docs/AgentFit_AB_1차통합_개발기준.pdf` section 12 and `D:/capstone/Docs/api/03-configuration.draft.md` sections 5–7.

## Constraints

- The expected fingerprint is a lowercase 64-character SHA-256 hex string. Any mismatch is a stale Preview error.
- The exporter gets exact file contents by recomputing; it never accepts a client-supplied file list or digest as proof.
- ZIP entries contain only Preview paths, once each, with exact UTF-8 content bytes. ZIP container metadata need not be byte-identical.
- No originals or diffs are written to ZIP. This helper performs no file-system write, DB write, audit, or external execution.
- Limit output to 20 files and 1 MiB uncompressed bytes until the public API size contract is finalized.

### Task 1: Reproduction gate and archive bytes

**Files:** `configuration/export/PreviewZipExporter.java`, `configuration/export/PreviewZipExporterTest.java`, B tracker, core-api README.

- [x] Write tests for exact decompressed path/bytes, changed input/basis rejection, no original leakage, malformed fingerprint, and oversized output.
- [x] Run focused tests and observe the missing exporter failure.
- [x] Implement recomputation, constant-time fingerprint comparison, bounded in-memory ZIP generation.
- [x] Run full package tests, inspect counts, and run `git diff --check`. Result: 54 tests discovered, 53 passed, one Windows symlink test skipped; Java 21 class-file target (major 65) confirmed on JDK 24.

This does not complete B11: a real export endpoint also needs authenticated owner checks, current basis, valid unexpired approval, transaction/audit rules, and final response handling.
