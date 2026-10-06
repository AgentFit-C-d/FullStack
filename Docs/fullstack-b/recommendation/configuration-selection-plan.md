# B09 selection guard plan

Before Preview, B must revalidate the user's selected tool set against the same immutable Catalog release and exact environment target. This is a pure domain guard while A's authenticated snapshot/persistence contract remains unsettled.

The validator takes the complete selected tool set, the exact OS/client/version target, reviewed permission mappings, and explicit permission selections. It rejects unknown or unsupported tools, missing dependencies, conflicts, overlapping included components, and multi-tool sets without verified evidence for that exact target. It then requires exactly one supported policy choice for every reviewed mapping of a selected tool. A required mapping cannot be denied. An empty tool set has no mappings and is valid only with no policy selections.

This guard does not imply installation, execution, Preview, approval, or that an optional DENY has been applied by a Client. The caller must tie the release/environment/selection to A's current immutable basis before generating a Preview. Targeted combination declarations are accepted only with reviewed support and source evidence.

- [x] Write focused tests first and observe missing validator failure.
- [x] Implement the pure selection guard, reusing the permission policy gate.
- [x] Run focused and full tests; record the partial B09 milestone.
