# AI v2 Core Integration Design

## Goal

Reuse the document-analysis core from `AgentFit_AI_v2` inside the existing FullStack AI service while preserving the FullStack HTTP contract, HMAC authentication, and Spring-owned orchestration and persistence boundaries.

## Scope

### In scope

- Import the reusable Python analysis components from `AgentFit_AI_v2/apps/ai`.
- Keep FullStack's `POST /internal/v1/analysis` endpoint and HMAC request authentication.
- Add an adapter from the FullStack analysis request to the AI v2 analysis input.
- Add an adapter from AI v2 output to the existing FullStack analysis response contract.
- Preserve timeout, request-context, failure, and no-secret/raw-input logging rules.
- Add unit/contract tests for authentication, request mapping, response mapping, and provider failures.

### Out of scope

- Importing the AI v2 Express routes, Bearer-token middleware, Vercel deployment files, or its web/API server.
- Connecting `skills/needs` and `skills/rank` in the first slice.
- Moving project ownership, source-document storage, lease state, budget reservation, or persistence into Python.
- Committing provider keys or changing the public Core API.

## Architecture

```text
Spring Boot core-api
  -> existing HMAC-signed POST /internal/v1/analysis
  -> services/ai-service adapter
  -> AI v2 document extraction / provider / evidence validation core
  -> FullStack response contract
```

The adapter is the boundary. FullStack remains the source of truth for authentication, project authorization, input references, leases, deadlines, budget decisions, and persistence. The AI service receives only the approved request projection and returns a validated, contract-shaped result.

## Contract decisions

- FullStack HMAC headers remain mandatory; Bearer authentication from AI v2 is not copied.
- TLS remains required in deployed environments because HMAC authenticates integrity and origin but does not encrypt payloads.
- Existing `requestId`, `generation`, and `contractVersion` context is preserved in the mapped request and response.
- Raw documents, secrets, provider credentials, and raw provider responses are not logged or persisted.
- Provider/model failures, malformed outputs, deadline expiry, and evidence-validation failures map to the existing failure behavior and do not create a successful analysis snapshot.

## Testing strategy

Tests are written before production changes. The first red tests cover:

1. valid HMAC request reaches the adapter;
2. invalid, stale, or replayed HMAC requests are rejected;
3. FullStack request fields map deterministically to AI v2 input;
4. AI v2 output maps to the existing response contract while preserving request context;
5. malformed/provider failure results are rejected without a successful result;
6. no raw input, secrets, or provider response is emitted to ordinary logs.

The existing Core API and AI service suites remain required verification gates.

## Acceptance criteria

- Existing FullStack API paths and HMAC behavior remain compatible.
- A synthetic analysis request can pass through the adapter without a paid model call.
- AI v2 extraction/provider/evidence logic is covered by the AI service test suite.
- Core API and AI service tests pass, or any environment-only blocker is reported explicitly.
- The branch contains no provider secrets and no unrelated route or deployment migration.
