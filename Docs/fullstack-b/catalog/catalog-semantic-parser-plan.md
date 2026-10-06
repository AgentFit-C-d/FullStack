# Catalog semantic parser plan (proposed v1 contract)

The hashed bundle is only an envelope. B must reject structurally valid files whose tool references, support checks, combination checks, or permission mappings are incomplete. This file shape is a **proposal for A/B/AI review**, not a claim that a real Client tool has been verified.

Each of the six JSON files has exactly `schemaVersion: 1` and an `items` array, except `relations.json`, which has `schemaVersion`, `dependencies`, `conflicts`, and `verifiedCombinations` arrays. Unknown fields fail closed.

- `capabilities.json`: nine `{key}` items, exactly the agreed nine keys.
- `tools.json`: `{key, version, capabilityKeys, includedComponentKeys}` items. Tool keys are unique and each Capability reference must exist. A dependency-only tool may have no direct Capability.
- `support-matrix.json`: `{key, toolKey, osFamily, clientId, clientVersion, documentation, format, standalone, evidenceUrl, checkedAt}` items. The three checks are `PASS`, `FAIL`, or `NOT_RUN`. A `PASS` record requires a nonempty HTTPS source and a parseable date. Exact OS/client/version matching remains required.
- `relations.json`: `{toolKey, targetKey}` dependency/conflict items. A `verifiedCombinations` item has `{toolKeys, osFamily, clientId, clientVersion, evidenceUrl, checkedAt}`. It is eligible only for that exact target and only if every member has reviewed standalone support there, all dependencies are included, and no conflict or component overlap exists. This target-specific extension remains a team-review proposal.
- `permissions.json`: `{toolKey, mappingKey, required, supportedPolicies, evidenceUrl, checkedAt}` items. Policies use the existing B enum. A reviewed mapping requires `ASK_EACH_TIME` and a source; a tool can have no mappings only if it has no permissions to configure.
- `client-capabilities.json`: empty `items` only for now. The built-in capability contract is not agreed, so a nonempty declaration fails rather than silently claiming support.

The public `load(Path)` method chains hash verification and semantic parsing before returning an immutable `CatalogRelease` and reviewed permission mappings, tagged with the verified release hash and source metadata. An empty tool set is valid but cannot recommend any tools. The package-private `parse(VerifiedCatalogBundle)` method exists for isolated tests; no public endpoint accepts a caller-supplied bundle.

## Execution

- [x] Write tests for a valid synthetic one-tool release and rejection of unknown capability, dangling reference, absent support evidence, duplicate JSON keys, environment-agnostic combination, and unknown client capability. Target-specific combination tests were added in `targeted-combinations-plan.md`.
- [x] Observe test compilation fail before implementation and two behavior tests fail before hardening.
- [x] Implement strict parser and reuse `CatalogValidator` for graph validation.
- [x] Add an end-to-end test proving changed bytes fail before semantic projection.
- [x] Run full Maven package and update the B tracker and README with remaining production limits. JDK 24 compiled for Java 21 target; a Java 21 runtime was not available locally.
