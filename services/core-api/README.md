# AgentFit Core API

Spring Boot service for the AgentFit core domain.

## Responsibilities

- Own project, profile, recommendation, configuration, and audit data.
- Expose frontend-facing APIs.
- Call `ai-service` over HTTP for AI extraction and recommendation support.

The B-owned `catalog`, `recommendation`, and `configuration` packages currently provide pure release-integrity, strict semantic parsing, recommendation, selection/permission validation, Catalog-hash-pinned static template candidates, in-memory Preview comparison with identifiable-secret screening, deterministic fingerprints, Preview freshness checks, stored-approval-guarded bounded ZIP byte regeneration, and a transient Catalog-backed Preview assembly path with size budgets. Their `model`, `selection`, `preview`, and `export` subpackages group related classes and tests. The Catalog v1 file schema and fingerprint encoding are team-review proposals. Multi-tool combinations are scoped to an exact OS/Client/version with reviewed source evidence; no real combination records or reviewed configuration templates have been added. These are not public APIs or verified real Codex support data yet. See `../../Docs/fullstack-b/README.md` for the B file map, current status, and pending work.

## Run

```powershell
mvn spring-boot:run
```

## Test

```powershell
mvn test
```
