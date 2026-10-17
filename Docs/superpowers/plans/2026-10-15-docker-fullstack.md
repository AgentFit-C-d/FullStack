# Full-stack Docker Development Environment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add a reproducible Docker Compose development environment that starts PostgreSQL, Core API, and AI service together.

**Architecture:** Keep the existing PostgreSQL-only Compose file unchanged for DB-only use. Add a separate full-stack Compose file at `infra/compose.fullstack.yaml`, two multi-stage/runtime Dockerfiles under `infra/docker/`, and an example environment file for local secrets. Compose uses service DNS (`postgres`, `ai-service`) internally, while exposing Core API on 8080, AI service on 8001, and PostgreSQL on 5433.

**Tech Stack:** Docker Compose, PostgreSQL 17 Alpine, Maven with JDK 21, Spring Boot 3.3.5, Python 3.11+, FastAPI/Uvicorn.

**Spec:** `Docs/superpowers/specs/2026-10-15-docker-fullstack-design.md`

## Global Constraints

- Do not modify B service interfaces or DTOs.
- Do not commit OAuth credentials, HMAC secrets, or local `.env` files.
- Keep the existing `infra/postgres/compose.yaml` behavior unchanged.
- Use PostgreSQL container port 5432 internally and host port 5433 externally.
- Preserve the existing Flyway V1~V5 migration sequence.
- DB volume persists by default; never include automatic volume deletion in scripts.

## Review Focus

- Missing OAuth/HMAC environment variables must be visible as configuration errors, not silently replaced with production-looking secrets; test with `docker compose config` and documented `.env.docker` inputs in Task 3.
- Core API must use `postgres:5432` and not the host-only `localhost:5433` inside Compose; pin the environment value and health dependency in Task 3.
- AI service must start only with a complete HMAC key configuration and Core API must target `ai-service:8001`; verify service health and startup logs in Task 3.
- Build contexts must exclude `target`, `.venv`, Git metadata, and temporary PDF files; verify the Docker build context rules in Task 2.
- Database initialization must be repeatable against a fresh named volume and must not destroy an existing volume; verify migration startup in Task 3.

---

### Task 1: Add Docker build contexts and ignore rules

**Files:**
- Create: `infra/docker/core-api.Dockerfile`
- Create: `infra/docker/ai-service.Dockerfile`
- Create: `.dockerignore`

**Interfaces:**
- Produces: `agentfit-core-api` image exposing port 8080 and executing the built Spring Boot JAR.
- Produces: `agentfit-ai-service` image exposing port 8001 and executing Uvicorn against `app.main:app`.

- [ ] **Step 1: Add the Core API multi-stage Dockerfile**

Use a Maven/JDK 21 builder from repository root context, run the existing Maven package command with tests skipped for image creation, and copy the generated JAR into a JRE 21 runtime image. Do not add a Maven wrapper or change the POM.

- [ ] **Step 2: Add the AI service runtime Dockerfile**

Use Python 3.11 slim, copy `services/ai-service`, install the project package without development extras, expose 8001, and run `uvicorn app.main:app --host 0.0.0.0 --port 8001`.

- [ ] **Step 3: Add root `.dockerignore`**

Exclude Git metadata, Java `target/`, Python virtual environments/caches, IDE files, local environment files, and repository temporary extraction files while retaining source, migrations, and packaging metadata.

- [ ] **Step 4: Run static checks**

Run `git diff --check` and inspect both Dockerfiles for repository-root-relative paths and the expected runtime commands.

- [ ] **Step 5: Commit**

```powershell
git add .dockerignore infra/docker/core-api.Dockerfile infra/docker/ai-service.Dockerfile
git commit -m "build: add service Docker images"
```

### Task 2: Add full-stack Compose configuration and local environment template

**Files:**
- Create: `infra/compose.fullstack.yaml`
- Create: `.env.docker.example`
- Modify: `services/core-api/src/main/resources/application.yml` only if an explicit environment/property mapping is required for the AI URL.

**Interfaces:**
- Consumes: `agentfit-core-api` and `agentfit-ai-service` images from Task 1.
- Produces: Compose services named `postgres`, `ai-service`, and `core-api`; Core API reaches `jdbc:postgresql://postgres:5432/agentfit` and AI service reaches `http://ai-service:8001/internal/v1/analysis`.

- [ ] **Step 1: Define PostgreSQL service**

Use `postgres:17-alpine`, database/user `agentfit`, password from `${AGENTFIT_LOCAL_DB_PASSWORD}`, named volume `agentfit_fullstack_postgres_data`, host binding `127.0.0.1:5433:5432`, and a `pg_isready` healthcheck.

- [ ] **Step 2: Define AI service**

Build from the repository root using `infra/docker/ai-service.Dockerfile`, publish `127.0.0.1:8001:8001`, require the current HMAC key ID and secret from the environment, and add an HTTP healthcheck for `/health`.

- [ ] **Step 3: Define Core API service**

Build from the repository root using `infra/docker/core-api.Dockerfile`, publish `127.0.0.1:8080:8080`, set the Compose-internal datasource URL and credentials, enable Flyway, set the AI service URL, pass OAuth/HMAC variables through, and depend on healthy `postgres` and `ai-service` services.

- [ ] **Step 4: Add `.env.docker.example`**

Document non-secret local defaults plus placeholders for `GITHUB_CLIENT_ID`, `GITHUB_CLIENT_SECRET`, and HMAC values. Keep the actual `.env.docker` ignored and untracked.

- [ ] **Step 5: Validate Compose syntax without starting containers**

Run `docker compose --env-file .env.docker.example -f infra/compose.fullstack.yaml config` and verify the rendered service names, internal URLs, ports, healthchecks, and volume.

- [ ] **Step 6: Commit**

```powershell
git add infra/compose.fullstack.yaml .env.docker.example
git commit -m "build: compose fullstack development services"
```

### Task 3: Run Docker integration verification and document usage

**Files:**
- Modify: `Docs/superpowers/specs/2026-10-15-docker-fullstack-design.md` only if implementation details materially differ.
- Modify: `services/core-api/README.md` or root `README.md` with the final Docker commands.

**Interfaces:**
- Consumes: Compose configuration and `.env.docker` from Task 2.
- Produces: Running `/health` endpoints and a migrated PostgreSQL database.

- [ ] **Step 1: Prepare local environment**

Copy `.env.docker.example` to `.env.docker`, replace required OAuth/HMAC placeholders with local values, and do not add the copied file to Git.

- [ ] **Step 2: Build and start the stack**

Run `docker compose --env-file .env.docker -f infra/compose.fullstack.yaml up --build -d` and wait for all healthchecks to report healthy.

- [ ] **Step 3: Verify service health and migration**

Run `Invoke-RestMethod http://localhost:8080/health` and `Invoke-RestMethod http://localhost:8001/health`; inspect Core API logs for successful Flyway startup and confirm PostgreSQL is reachable on port 5433.

- [ ] **Step 4: Run the Java test suite with Docker available**

Run `mvn -B -ntp test` from `services/core-api`; record that Testcontainers tests execute rather than skip, and treat any unrelated environment limitation separately.

- [ ] **Step 5: Document lifecycle commands**

Document start, logs, stop (`down`), and explicitly destructive volume reset (`down -v`) commands. Do not automate volume deletion.

- [ ] **Step 6: Commit**

```powershell
git add README.md services/core-api/README.md Docs/superpowers/specs/2026-10-15-docker-fullstack-design.md
git commit -m "docs: document fullstack Docker workflow"
```
