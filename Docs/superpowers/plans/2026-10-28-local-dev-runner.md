# Local Development Runner Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a single `npm run dev:all` command that loads `.env` and starts the frontend, Core API, and AI service locally.

**Architecture:** A root npm script calls a PowerShell runner. The runner parses root `.env`, derives Spring datasource settings, and starts three independent PowerShell windows so logs remain separate.

**Tech Stack:** npm scripts, PowerShell, Vite, Maven/Spring Boot, Python/Uvicorn.

**Spec:** `docs/superpowers/specs/2026-10-28-local-dev-runner-design.md`

## Global Constraints

- Keep ports frontend `5173`, Core API `8080`, AI service `8001`.
- Never print secret values.
- Do not change Docker Compose behavior.
- Do not commit `.env` credentials.

## Review Focus

- Missing `.env` must stop before opening service windows.
- Missing required HMAC or database values must produce a named error.
- Paths containing spaces, including the JDK path, must be quoted correctly.
- A failed child process must remain visible in its own terminal window.
- Re-running the command must not silently overwrite `.env`.

### Task 1: Root npm entry point

**Files:**
- Create: `package.json`

- [ ] Add a `dev:all` script that invokes `powershell -ExecutionPolicy Bypass -File scripts/dev-all.ps1`.
- [ ] Verify `npm run dev:all -- -Help` reaches the runner without changing files.

### Task 2: Environment-loading runner

**Files:**
- Create: `scripts/dev-all.ps1`

- [ ] Parse root `.env` lines as process-local environment variables without echoing values.
- [ ] Validate DB URL, DB username, DB password, HMAC key ID, HMAC secret, and AI service token.
- [ ] Map `AGENTFIT_DB_URL`, `AGENTFIT_DB_USERNAME`, and `AGENTFIT_DB_PASSWORD` to Spring datasource variables.
- [ ] Launch AI, Core, and web commands in separate PowerShell windows with quoted paths.
- [ ] Add a help/dry-run mode that prints commands and variable names only.

### Task 3: Local configuration and verification

**Files:**
- Modify: `.env` (local ignored file only)

- [ ] Add a local-only `AGENTFIT_AI_SERVICE_TOKEN` if it is absent; do not expose its value in output or Git.
- [ ] Run the runner help mode, then verify all three service health endpoints after launch.
- [ ] Run frontend build and the existing Core/AI test suites where dependencies are available.
