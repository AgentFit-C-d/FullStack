# AI Analysis Input Reference Pipeline Design

## Intent

Complete the first product-level vertical slice from planning-input storage to real AI analysis completion. A browser uploads a PDF, Markdown file, or text to Core API. Core API owns authentication, project authorization, input persistence, analysis-attempt state, and result persistence. AI Service receives only an internally authenticated analysis request, retrieves the original input through a short-lived `inputRef`, runs the existing analysis pipeline, and returns a validated result.

## Goals

- Persist and reuse the exact `PlanningInput` revision selected for an analysis attempt.
- Create a short-lived, attempt-bound `inputRef` and lease when analysis starts.
- Let AI Service retrieve the original PDF/Markdown bytes without browser cookies or GitHub credentials.
- Execute the existing Python analysis pipeline instead of returning the contract test double.
- Persist a safe analysis result and transition the attempt to `SUCCEEDED` or `FAILED`.
- Preserve the current asynchronous UI contract: start returns `202 PROCESSING`, and the frontend polls the latest attempt.
- Keep raw input and provider secrets out of API responses, logs, and analysis-result metadata.

## Non-goals

- Recommendation or skill-matching functionality.
- Replacing GitHub OAuth or browser session authentication.
- Sending browser requests directly to AI Service.
- Introducing a second project/user authorization model in AI Service.
- Building a general-purpose object-storage abstraction in this slice.

## Chosen approach

Use an asynchronous Core-owned worker with an attempt-bound input lease. `POST /analysis-attempts` only validates the project and current input, creates a `PROCESSING` attempt, and returns `202`. A worker claims the attempt, creates or uses the lease, loads the current `PlanningInput`, and calls AI Service through the existing HMAC client. AI Service uses the `inputRef` to call Core API's internal input endpoint, extracts the document, and invokes the existing provider pipeline. Core validates response context before persisting the result and transitions the attempt to a terminal state.

## End-to-end flow

```text
Browser
  └─ PUT /api/projects/{projectId}/planning-input
       └─ Core validates and stores PlanningInput(revision N)

Browser
  └─ POST /api/projects/{projectId}/analysis-attempts {expectedVersion}
       └─ Core verifies owner/version/input and stores PROCESSING attempt
       └─ Core returns 202 with safe attempt metadata

Core worker
  └─ claims PROCESSING attempt
  └─ creates attempt-bound lease + inputRef
  └─ HMAC POST /internal/v1/analysis to AI Service

AI Service
  └─ verifies Core HMAC request
  └─ GETs Core internal input endpoint with attemptId + leaseId
  └─ receives original bytes and metadata
  └─ runs extract_document → provider → grounding/review
  └─ returns request context + validated result

Core worker
  └─ validates requestId/generation/contractVersion and lease freshness
  └─ persists safe result
  └─ transitions attempt to SUCCEEDED or FAILED
  └─ scrubs raw input only according to the existing retention policy

Browser
  └─ polls latest attempt until terminal state
```

## Internal contracts

### Analysis request from Core to AI

The existing HMAC envelope remains the outer contract:

```json
{
  "requestId": "opaque-attempt-request-id",
  "generation": 1,
  "contractVersion": "1.0",
  "documentId": "opaque-document-id",
  "attemptId": "opaque-attempt-id",
  "leaseId": "opaque-lease-id",
  "inputKind": "pdf",
  "inputRef": {
    "uploadId": "opaque-upload-id",
    "storage": "core-api",
    "objectKey": "project/p1/revision/1",
    "expiresAt": "2026-10-27T18:00:00Z"
  },
  "provider": "upstage",
  "deadline": "2026-10-27T17:50:00Z"
}
```

The exact request body is HMAC-signed. Core never forwards browser cookies, GitHub tokens, or provider keys.

### Internal input retrieval

AI Service calls a Core-internal endpoint using the attempt and lease context:

```text
GET /internal/v1/inputs/{uploadId}/metadata?attemptId=...&leaseId=...
GET /internal/v1/inputs/{uploadId}?attemptId=...&leaseId=...
```

Core returns the input only when the internal caller is authenticated, the lease is valid and unexpired, the attempt is still eligible, and the upload/revision belongs to that attempt. Metadata and bytes are bounded by the existing planning-input limits.

### Analysis response

The response must echo `requestId`, `generation`, and `contractVersion`. Core rejects mismatched or late responses before persistence. Provider output is validated in AI Service; Core stores only the validated result envelope and safe metadata.

## State and failure handling

- `PROCESSING`: attempt accepted and owned by a valid lease.
- `SUCCEEDED`: AI response passed context and result validation and was persisted.
- `FAILED`: provider, input, contract, or persistence failure was recorded with a safe error code.
- `TIMEOUT`: existing scheduler marks expired attempts and releases admission capacity.
- A duplicate worker delivery must not create a second terminal transition.
- A stale lease or expired attempt must not return input bytes or persist a result.
- Retriable AI admission failures may be retried once with the same request context; uncertain processing state is not blindly retried.

## Persistence changes

- Extend analysis-attempt persistence for lease ID/expiry and the selected input reference or revision as needed.
- Add an analysis-result persistence boundary keyed by attempt ID/request ID. The result must not expose raw PDF bytes or provider secrets.
- Reuse the existing `PlanningInput` revision and scrub behavior; do not duplicate the document into the analysis result.

## Security and privacy

- Browser authentication remains Core API's responsibility.
- AI Service authenticates Core-to-AI and AI-to-Core internal calls, not end users.
- `uploadId`, `leaseId`, and request IDs are opaque and short-lived; do not use project names or filenames as secrets.
- Do not log raw request bodies, PDF bytes, extracted text, HMAC secrets, or provider API keys.
- Validate content type, byte size, page count, and extracted-text limits again at the internal boundary.

## Verification targets

- Uploading a PDF stores both raw bytes and extracted text under one revision.
- Starting analysis without input, with a stale version, or with another user's project is rejected.
- A valid attempt produces an input reference bound to that attempt and revision.
- Invalid/expired leases cannot retrieve metadata or bytes.
- AI request signatures reject tampering and replay.
- A successful provider response becomes `SUCCEEDED` and is visible through the polling API.
- Provider failure, timeout, response mismatch, and duplicate completion become safe terminal states without leaking document content.
