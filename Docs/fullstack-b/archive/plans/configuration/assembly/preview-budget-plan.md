# B10e Preview assembly size budget

**Goal:** Reject inputs and generated outputs that cannot fit the proposed Preview/export limits before constructing a Preview.

**Design:** The B-only assembly path limits selected tools to 20, permission choices to 200, and provided existing files to 20. Every provided/generated file has at most 100,000 Unicode code points; each group has at most 1,048,576 UTF-8 content bytes and at most 20 files. The generated limit matches the existing ZIP export count/byte ceiling. Inputs are rejected without truncation or storing content. The HTTP layer must later enforce the exact serialized JSON body size of 1 MiB; this pure guard only counts file content bytes.

The regeneration path now enforces those same limits inside `PreviewFingerprint.compute` before comparison, Diff, hashing, or ZIP generation. This closes the direct B export entry point when it receives a large generated/provided file list, even before the public HTTP body limit exists. `PreviewAssemblyLimits` and the fingerprint guard currently duplicate the provisional numeric limits; keep them aligned when the team fixes the contract.

The direct regeneration path also bounds project/profile/confirmation/Catalog/recommendation/tool/generator IDs to 128 Unicode code points and permission mapping/file target keys to 200. This follows the configuration API draft and prevents oversized metadata from reaching canonical JSON hashing. The request body and all public DTO fields still need separate HTTP validation.

The Catalog Preview assembly entry now applies the same ID/key limits to request metadata before loading a release, in addition to its earlier file-content limits. These are still two internal guards with duplicated provisional constants; keep the values synchronized until the public contract is approved and consolidated.

These numbers come from the configuration API draft and are provisional until the team approves that contract. No public API or A-owned persistence is introduced.

- [x] Write failing boundary tests for exact limit, overflow, Unicode byte count, and too many choices/files.
- [x] Add the budget check before Catalog loading for requests and before fingerprinting for generated files.
- [x] Run targeted and full Maven tests; update B status and file map. Full package: 71 tests discovered, 70 passed, one Windows symlink test skipped; `git diff --check` passed.
