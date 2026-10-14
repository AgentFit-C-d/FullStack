# 검증 가이드: 프로젝트 생성과 분석 시작 MVP

## 사전 조건

1. PostgreSQL에서 `agentfit` 데이터베이스가 실행 중이고 `AGENTFIT_DB_URL`, `AGENTFIT_DB_USERNAME`, `AGENTFIT_DB_PASSWORD`가 설정되어 있다.
2. GitHub OAuth 환경 변수와 `AGENTFIT_COOKIE_SECURE=false`(로컬 HTTP 테스트)가 설정되어 있다.
3. Core API가 `http://localhost:8080`에서 실행 중이다.
4. 브라우저에서 GitHub 로그인 뒤 `GET /api/auth/get-session`이 사용자 정보를 반환한다.
5. 쓰기 요청 전 `GET /api/auth/csrf`로 `XSRF-TOKEN` 쿠키를 받고, 같은 값을 `X-XSRF-TOKEN` 헤더로 보낸다.

## 자동 테스트

Core API 디렉터리에서 실행한다.

```powershell
cd C:\dev_source\agentFit\services\core-api
$env:JAVA_HOME='C:\Program Files\Eclipse Adoptium\jdk-21.0.7.6-hotspot'
mvn -q test
```

PostgreSQL 동시성 통합 테스트는 Testcontainers를 사용하므로 Docker가 실행 중이면 함께 실행된다. Docker가 없으면 해당 테스트는 `disabledWithoutDocker=true`로 자동 건너뛰며, 나머지 단위·웹 테스트 결과와 통합 테스트 미실행 사실을 별도로 기록한다.

기대 결과:

- 프로젝트 생성·조회·수정·소유권·입력 한도·멱등성 테스트 통과
- 프로젝트 1건, 사용자 1건, 서버 전체 2건 분석 동시성 테스트 통과
- 최대 10초 간격의 600초 만료 정리에서 `FAILED/TIMEOUT`, 원문 삭제, admission 회수 테스트 통과
- OpenAPI·DTO·오류 코드 계약 테스트 통과

## 수동 핵심 흐름

### 1. 직접 입력 프로젝트 생성

`POST /api/projects`에 multipart 요청을 보낸다.

- `metadata`: `name`, `role`, `runtimeInput`
- `inputText`: UTF-8 `text/plain`
- 헤더: `Idempotency-Key`, `X-XSRF-TOKEN`

기대 결과: `201`, 역할과 중복 제거된 runtime, `TEXT` 입력 메타데이터가 응답에 있다. 원문은 응답에 없다.

### 2. 동일 키 재시도

완전히 같은 생성 요청을 동일 `Idempotency-Key`로 순차·동시에 다시 보낸다.

기대 결과: 프로젝트가 하나만 존재하고 최초 프로젝트 응답이 돌아온다.

### 3. 프로젝트 정보 재조회·수정

`GET /api/projects/{projectId}`로 메타데이터를 확인한 뒤 `PATCH /api/projects/{projectId}`에 현재 `expectedVersion`과 변경할 이름·역할·runtime 목록을 보낸다.

기대 결과: `200`, version 증가, 새로고침/재조회 후 수정값 유지. 예전 version으로 다시 수정하면 `409 VERSION_CONFLICT`다.

### 4. 분석 시작

`POST /api/projects/{projectId}/analysis-attempts`에 현재 `expectedVersion`을 보낸다.

기대 결과: `202`, 상태 `PROCESSING`, `deadlineAt`은 시작 시각에서 600초 뒤다.

### 5. 동시성 오류

1. 같은 프로젝트에서 다시 분석을 시작한다.
2. 같은 사용자의 다른 프로젝트에서 분석을 시작한다.
3. 서로 다른 사용자로 두 개의 활성 분석을 만든 뒤 세 번째를 시작한다.

기대 결과:

| 상황 | 결과 |
|---|---|
| 같은 프로젝트 | `409 ANALYSIS_ALREADY_PROCESSING` 및 지정 안내 문구 |
| 같은 사용자의 다른 프로젝트 | `429 ANALYSIS_USER_LIMIT_REACHED` |
| 서버 세 번째 활성 분석 | `429 ANALYSIS_SERVER_LIMIT_REACHED` |

### 6. 원문 삭제와 만료

테스트용 Clock 또는 만료 정리 실행으로 Attempt 기한을 넘긴다.

기대 결과: Attempt가 `FAILED`, 오류 코드가 `TIMEOUT`, 입력 원문·추출문이 삭제되고 메타데이터·Attempt 이력은 남는다. 이후 활성 슬롯이 회수되어 새 분석을 시작할 수 있다.

### 7. 활성 분석 중 프로젝트 삭제

`PROCESSING` Attempt가 있는 프로젝트에 기존 `DELETE /api/projects/{projectId}` 요청을 보낸다.

기대 결과: `409 ANALYSIS_ACTIVE_PROJECT_DELETE_FORBIDDEN`이며 Attempt·입력 원문·admission 슬롯은 그대로 남는다. timeout으로 terminal 상태가 된 뒤에는 기존 삭제 흐름을 수행할 수 있다.

상세 요청·응답 모양은 [계약 문서](contracts/project-analysis-mvp.md), 저장 구조는 [데이터 모델](data-model.md)을 따른다.
