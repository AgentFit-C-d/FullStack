# Full-stack Docker 개발 환경 설계

## 목적

로컬 Windows 환경에서 PostgreSQL, AgentFit Core API, AI service를 Docker Compose로 함께 실행한다. 애플리케이션 소스는 컨테이너 이미지에 포함해 빌드하며, 기존 호스트 설치 방식과 병행할 수 있어야 한다.

## 범위

- PostgreSQL 17 개발 컨테이너
- Java 21 기반 `core-api` 이미지
- Python 기반 `ai-service` 이미지
- 서비스 간 Compose 네트워크와 healthcheck
- Core API의 Flyway 자동 migration
- 로컬 개발용 포트와 환경 변수 기본값

다루지 않는 범위:

- 운영 배포용 이미지 최적화 및 오케스트레이션
- 외부 OAuth/LLM 자격 증명 발급
- 기존 개발 DB 데이터 마이그레이션

## 구성

`infra/compose.fullstack.yaml`이 세 서비스를 정의한다. `postgres`는 호스트 `5433`에 공개하고, Core API는 컨테이너 내부 DNS 이름 `postgres:5432`로 접속한다. Core API는 `ai-service:8001`을 AI 서비스 주소로 사용한다. 각 서비스는 healthcheck 또는 startup dependency를 사용해 DB가 준비된 뒤 시작한다.

이미지는 다음 Dockerfile로 만든다.

- `infra/docker/core-api.Dockerfile`: Maven 빌드 단계와 JRE 실행 단계의 multi-stage build
- `infra/docker/ai-service.Dockerfile`: Python 런타임에 프로젝트 의존성을 설치하고 Uvicorn 실행

루트 `.dockerignore`는 빌드 컨텍스트에서 `target`, Python 가상환경, Git 메타데이터, 로컬 임시 파일을 제외한다.

## 설정 원칙

- Compose 기본 DB는 `agentfit` / `agentfit` / `agentfit-local-only`를 사용한다.
- Core API 컨테이너의 DB URL은 `jdbc:postgresql://postgres:5432/agentfit`이다.
- 호스트에서 Core API를 직접 실행할 때는 기존처럼 `localhost:5433`을 사용한다.
- GitHub OAuth와 HMAC 비밀값은 Compose 파일에 고정하지 않고 환경 변수로 주입한다.
- DB 볼륨은 기본적으로 유지한다. 데이터 삭제는 사용자가 명시적으로 `docker compose ... down -v`를 실행할 때만 일어난다.

## 검증 기준

```powershell
docker compose -f infra/compose.fullstack.yaml config
docker compose -f infra/compose.fullstack.yaml up --build
```

검증 항목은 다음과 같다.

1. 세 이미지가 빌드된다.
2. PostgreSQL healthcheck가 통과한다.
3. Core API가 Flyway migration 후 기동한다.
4. Core API와 AI service health endpoint가 응답한다.
5. Docker가 실행 중인 환경에서 `mvn test`의 Testcontainers 테스트가 스킵되지 않는다.
