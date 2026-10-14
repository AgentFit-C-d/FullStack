# B10c Preview sensitive-content guard

**Goal:** Refuse clearly identifiable credentials before transient Preview comparison, fingerprinting, or export can include them.

**Scope:** Inspect generated candidates and explicitly provided originals inside the existing in-memory comparator, before hashes and diffs are constructed. Reject private-key blocks, selected recognizable provider token prefixes, bearer literals, URL userinfo, and nonempty literal values assigned to credential-like keys, including prefixed names such as `TEAM_API_KEY`, `TEAM_TOKEN`, and `AWS_SECRET_ACCESS_KEY` and shell `export` assignments. Allow exact environment-variable references and empty/placeholder values. Return a generic error without echoing content. This is a bounded pattern screen, not a general secret scanner or a guarantee that no secrets remain.

**Security boundary:** The same comparator is used by static-template validation, fingerprinting, and ZIP regeneration; HTTP ingress must still enforce body limits and run this check before logging, persistence, or forwarding. The current package has no public HTTP endpoint.

- [x] Write tests that prove rejection of generated/provided literals and no secret in the error.
- [x] Write tests that preserve environment references and ordinary configuration values.
- [x] Add the scanner and invoke it before comparison artifacts are built.
- [x] Run targeted tests, full package, and whitespace validation; update the B tracker. Full package: 62 tests, 0 failures, 0 errors, 1 Windows symlink test skipped.
