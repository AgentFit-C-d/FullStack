# AgentFit FullStack

AgentFit MVP 모노레포입니다. A와 B 코드는 단일 Spring Boot Core API에 병합되어 있으며, FastAPI AI Service는 독립 서비스로 유지됩니다.

## Repository Layout

```text
apps/
  web/                 # Next.js frontend, to be added by frontend team
services/
  core-api/            # Spring Boot API for A/B domain, persistence, audit, and orchestration
  ai-service/          # FastAPI service for AI request handling and structured extraction
contracts/
  openapi/             # API contracts shared by frontend, core API, and AI service
Docs/
  ab-integration-status.md # A/B 연결 상태와 다음 통합 지점
```

## Backend Services

- `services/core-api`: A의 인증·프로젝트·분석·Audit과 B의 Catalog·추천·Preview·승인·Export 내부 규칙 및 JDBC 경계를 함께 담은 단일 Spring Boot 서비스입니다.
- `services/ai-service`: Core API 내부 호출을 위한 인증·분석 경계와 health endpoint를 제공하는 독립 FastAPI 서비스입니다.
- `contracts/openapi`: Frontend/Core API 및 Core API/AI Service 간 공동 API 계약 위치입니다.

## Setup Scope

구조는 기준 문서의 흐름을 따릅니다: Next.js -> Spring Boot Core API -> FastAPI AI Service -> external LLM. PostgreSQL 읽기·쓰기는 Core API만 소유합니다.

소스가 병합되어 함께 컴파일되는 것과 인증된 사용자 흐름이 실제로 연결된 것은 다릅니다. 현재 B workflow는 A/AI의 신뢰 adapter, 승인된 Catalog, 원자 저장 port가 제공될 때만 활성화됩니다. A snapshot·AI 계약·공개 API·Flyway 통합·프로젝트 삭제 transaction은 아직 후속 통합 작업입니다. 자세한 상태는 [A/B 병합 연계 현황](Docs/ab-integration-status.md)을 확인하세요.

현재 기준 런타임은 Java 21 / Spring Boot 3.3.5 및 Python 3.11+ / FastAPI입니다. Maven wrapper는 포함하지 않습니다.

## Docker full-stack development

Prerequisite: Docker Desktop with the Linux engine running. From the repository root, copy the environment template and replace the GitHub OAuth and HMAC placeholders with your local values. `.env.docker` is local and ignored by Git; never commit it or any secrets.

```powershell
Copy-Item .env.docker.example .env.docker
# Edit .env.docker and set GITHUB_CLIENT_ID, GITHUB_CLIENT_SECRET,
# AGENTFIT_HMAC_CURRENT_KEY_ID, and AGENTFIT_HMAC_CURRENT_SECRET.
docker compose --env-file .env.docker -f infra/compose.fullstack.yaml up --build -d
```

Core API and AI service health endpoints are `http://localhost:8080/health` and `http://localhost:8001/health`. PostgreSQL is available on host port `5433` and retains its data in the named Docker volume.

```powershell
docker compose --env-file .env.docker -f infra/compose.fullstack.yaml ps
docker compose --env-file .env.docker -f infra/compose.fullstack.yaml logs -f core-api ai-service postgres
docker compose --env-file .env.docker -f infra/compose.fullstack.yaml down
```

To deliberately delete the full-stack PostgreSQL volume and all its data, run this destructive reset separately:

```powershell
docker compose --env-file .env.docker -f infra/compose.fullstack.yaml down -v
```

Run the Core API tests with Docker available, including Testcontainers tests, from `services/core-api` using `mvn -B -ntp test`.

## Local Run

Prerequisites: JDK 21, Maven, and Python 3.11+. Maven 실행 시 `JAVA_HOME`도 JDK 21을 가리켜야 합니다. 각 서비스는 의존성을 개별 관리합니다.
Build output (`target/`), Python virtual environments (`.venv/`), IDE settings, and local `.env` files are intentionally excluded from Git.
Maven downloads Java dependencies during the first build; the Python install command below installs the AI service dependencies.

```powershell
cd services/core-api
mvn spring-boot:run
```

```powershell
cd services/ai-service
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev]"
uvicorn app.main:app --reload --port 8001
```

Default ports:

- Core API: `8080`
- AI service: `8001`

## Local Verification

- Core API: run `mvn -B -ntp test` from `services/core-api`. JDK 21로 실행한 2026-10-14 기준 표준 테스트는 440개 실행, 실패·오류 0개, Docker/환경 의존 테스트 50개 skip으로 완료됐습니다. PostgreSQL opt-in 테스트는 별도 Docker와 환경 변수가 필요합니다.
- AI service: 개발 의존성을 설치한 뒤 `services/ai-service`에서 `python -m pytest -q`를 실행합니다. 2026-10-14 기준 격리 가상환경에서 10개 테스트가 통과했으며, FastAPI startup event와 Starlette TestClient의 deprecation warning 3개가 남아 있습니다.

이 검증은 실제 PostgreSQL, 승인 Catalog, 외부 LLM, 인증된 B 공개 API, 배포 환경을 검증하지 않습니다.

## Continuous Integration

The [GitHub Actions CI workflow](.github/workflows/ci.yml) runs on pushes, pull requests, and manual dispatch. It independently builds and tests `core-api` with Java 21 and `ai-service` with Python 3.11. A successful Core API job retains its tested JAR for seven days as a downloadable workflow artifact.

Deployment automation is not configured: the team has not chosen a deployment target, credentials, or environment yet. A downloadable artifact is a build result, not a deployment. Once those choices are agreed, the deploy workflow and post-deployment checks can be added under the A-owned deployment boundary.
