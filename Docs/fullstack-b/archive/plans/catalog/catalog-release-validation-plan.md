# Catalog Release Validation Implementation Plan

> **For agentic workers:** Use Superpowers test-driven-development and verification-before-completion for each task. This plan runs in the user-selected shared `FullStack` checkout; it does not move A's work.

**Goal:** Load an immutable Catalog release only when its declared files and release hash match the bytes on disk.

**Architecture:** A B-owned loader reads one release directory into immutable in-memory text. It rejects missing, changed, duplicate, extra, or unsafe paths before any file is made available to a Catalog parser. No real tool is marked supported by this task.

**Tech Stack:** Java 21 target, Spring Boot's Jackson dependency, JUnit 5, SHA-256.

**Spec:** `Docs/AgentFit_AB_1차통합_개발기준.pdf` section 8, with the B PRD FSB-01/02. The exact manifest wire shape and hash preimage below are this implementation's v1 proposal for team review.

## Global Constraints

- The reader must never infer support from a directory or filename. Verified support records are a later task.
- Only six required JSON paths and explicit `templates/` or `guides/` text paths may be listed.
- `manifest.json` is never listed as one of its own files.
- File bytes are UTF-8; no raw file or temporary copy is persisted by the loader.
- Every path is relative, normalized, inside the release directory, and free of symlinks.
- `catalogHash` is computed without itself: SHA-256 of UTF-8 `agentfit-catalog-v1\n{releaseId}\n` followed by sorted `{path}\t{sha256}\n` lines.
- Manifest `schemaVersion` must equal `1`; file hash and catalog hash are lowercase SHA-256 hex.
- Maximum 100 listed files, maximum 1 MiB per file, maximum 8 MiB combined. Oversize is an unavailable release, never a normal no-candidates recommendation.

## Review Focus

- A file changed after the manifest was authored must be rejected.
- A missing or extra file must be rejected.
- Traversal, absolute paths, and symlinks must be rejected.
- Duplicate paths or noncanonical hash strings must be rejected.
- Reordering manifest entries must not change the computed release hash.

### Task 1: Manifest and file integrity

**Files:** `services/core-api/src/main/java/com/agentfit/coreapi/catalog/CatalogBundleLoader.java`, `VerifiedCatalogBundle.java`, and `services/core-api/src/test/java/com/agentfit/coreapi/catalog/CatalogBundleLoaderTest.java`.

**Interfaces:** `CatalogBundleLoader.load(Path releaseDirectory): VerifiedCatalogBundle`; `VerifiedCatalogBundle` exposes `releaseId()`, `catalogHash()`, and an immutable map of verified UTF-8 file text. Invalid input throws `CatalogUnavailableException`.

- [x] Write a synthetic six-file fixture and assert the hand-checked v1 hash `38e92a97ab9126a133c5f3bb85ebfdd2d55e43c82515d887b6ded60b6698d3a5`.
- [x] Run the focused test and observe it fail before implementing the reader.
- [x] Implement strict manifest parsing, path and size validation, SHA-256 file checks, release hash calculation, and immutable return value.
- [ ] Verify valid, changed, missing, extra, duplicate, traversal, and symlink cases. All except the symlink test ran; Windows denied test symlink creation, so that case remains unverified on this workstation.
- [x] Run the focused test, full Maven test suite, and package build; confirm Java 21 class-file target and record the result. Maven package succeeded on JDK 24, with 18 tests discovered: 17 passed, 1 symlink test skipped; class-file major version 65.

The release reader alone does not populate the real Catalog. The next B task parses each verified file against a team-reviewed schema and loads verified support evidence before enabling any recommendation endpoint.
