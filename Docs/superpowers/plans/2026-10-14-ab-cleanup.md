# A/B 최소 변경 정리 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A/B 병합본의 Maven 빌드를 복구하고, 기존 구현을 변경하지 않은 채 현재 연계 상태와 책임 경계를 정확히 문서화한다.

**Architecture:** A의 인증·소유권·트랜잭션·Audit 책임과 B의 Catalog·추천·Preview 내부 워크플로우 책임을 유지한다. B의 conditional auto-configuration이 필요한 A/AI 포트가 제공되지 않으면 비활성인 현재 동작을 그대로 두고, 한 곳의 현황 문서와 기존 입구 문서에서 이를 안내한다.

**Tech Stack:** Java 21, Spring Boot 3.3.5, Maven, Python 3.11+, FastAPI, Markdown.

**Spec:** `Docs/superpowers/specs/2026-10-14-ab-cleanup-design.md`

## Global Constraints

- 기존 프로덕션 Java 파일은 변경하지 않는다.
- `services/core-api/pom.xml`의 PostgreSQL 런타임 의존성은 정확히 한 번만 선언한다.
- A snapshot reader, AI HTTP contract, 공개 API, B DDL/Flyway 통합, 패키지 대이동은 구현하지 않는다.
- B 조건부 서비스에 가짜 A/AI adapter를 추가하지 않는다.
- PostgreSQL Docker 테스트의 opt-in 정책을 바꾸지 않는다.

## Review Focus

- Maven이 중복 좌표를 다시 허용하지 않고 전체 테스트 단계까지 진행하는지 검증한다.
- README가 B 내부 구현을 인증된 A/B 런타임 연계로 잘못 주장하지 않는지 확인한다.
- 상태 문서가 B 서비스의 비활성 조건(A owner-checked port, AI port, 승인 Catalog)을 빠뜨리지 않는지 확인한다.
- 프로젝트 삭제가 B FK와 단일 트랜잭션 연결을 필요로 하지만 아직 미구현임을 분명히 한다.
- Python 테스트는 개발 의존성이 설치된 환경에서만 실행된다는 조건을 명시하고, 누락된 의존성을 테스트 성공으로 오인하지 않는다.

---

### Task 1: Maven 의존성 중복 제거

**Files:**
- Modify: `services/core-api/pom.xml:24-94`

**Interfaces:**
- Consumes: Maven의 `groupId:artifactId:type:classifier` 고유성 규칙
- Produces: PostgreSQL JDBC driver 하나를 사용하는 유효한 Core API Maven 모델

- [ ] **Step 1: 현재 POM 검증 실패를 재현한다**

Run: `mvn -B -ntp test` from `services/core-api`
Expected: `org.postgresql:postgresql` duplicate declaration 오류로 실패

- [ ] **Step 2: PostgreSQL JDBC runtime 의존성 하나를 제거한다**

`services/core-api/pom.xml`에서 동일한 `<groupId>org.postgresql</groupId>`와 `<artifactId>postgresql</artifactId>` 조합 중 한 블록만 남긴다. scope는 `runtime`으로 유지한다.

- [ ] **Step 3: POM 검증과 전체 Core API 테스트를 통과시킨다**

Run: `mvn -B -ntp test` from `services/core-api`
Expected: Maven 모델 오류 없이 테스트 실행 완료

- [ ] **Step 4: 변경 사항을 커밋한다**

```powershell
git add services/core-api/pom.xml
git commit -m "fix: remove duplicate PostgreSQL driver dependency"
```

### Task 2: 단일 A/B 연계 현황 문서 추가

**Files:**
- Create: `Docs/ab-integration-status.md`
- Modify: `Docs/README.md:1-9`
- Modify: `README.md:1-61`

**Interfaces:**
- Consumes: `com.agentfit.autoconfigure`의 `@ConditionalOnBean` 요구 포트와 `Docs/fullstack-b/STATUS.md`의 B05-B12 미완료 항목
- Produces: 개발자가 A/B 런타임 연결 여부와 다음 연결 지점을 빠르게 확인할 수 있는 문서 입구

- [ ] **Step 1: 문서에 넣을 사실을 코드와 B 상태 문서에서 교차 확인한다**

Check: A는 인증·소유권·현재 version·Audit·트랜잭션을 소유하고, B는 caller-owned JDBC 구성요소와 조건부 workflow를 제공하며, 실제 A/AI adapters·공개 API·Flyway 통합은 없음을 확인한다.

- [ ] **Step 2: `Docs/ab-integration-status.md`를 작성한다**

다음 표를 포함한다: A 책임, B 책임, 현재 실제 연결, 아직 없는 연결, 각 B 조건부 workflow가 요구하는 포트. 프로젝트 삭제에서 A가 B child 삭제를 같은 트랜잭션에 연결해야 한다는 제약과 현재 미연결 상태를 명시한다.

- [ ] **Step 3: 루트 README를 현재 병합 상태에 맞춘다**

스캐폴드·health-only라는 과거 설명을 A/B 병합본의 실제 구현 수준으로 교체한다. 내부 구현과 공개 런타임 API의 미연결 상태를 구분하고 새 연계 현황 문서로 링크한다.

- [ ] **Step 4: `Docs/README.md`에 연계 현황 문서 링크를 추가한다**

공통 기준 PDF와 B 문서 사이에 A/B 통합 현황의 공식 입구를 추가한다.

- [ ] **Step 5: 링크와 주장 범위를 검증한다**

Run: `git diff --check`
Expected: 공백 오류 없음. 모든 상대 Markdown 링크가 기존 파일 또는 새 문서를 가리킴.

- [ ] **Step 6: 변경 사항을 커밋한다**

```powershell
git add README.md Docs/README.md Docs/ab-integration-status.md
git commit -m "docs: clarify A/B integration boundaries"
```

### Task 3: B 문서의 병합·검증 상태 정합화

**Files:**
- Modify: `Docs/fullstack-b/README.md:1-93`
- Modify: `Docs/fullstack-b/STATUS.md:1-8`
- Modify: `Docs/fullstack-b/NEXT-STEPS.md:1-36`

**Interfaces:**
- Consumes: 최신 `origin/develop`의 A/B merge commit 및 Task 2의 연계 상태 문서
- Produces: B 구현 현황, 병합 완료, 런타임 연계 미완료를 구분한 팀용 재개 안내

- [ ] **Step 1: 병합 전 branch-only 표현을 찾는다**

Check: `feat/fullstack-b-phase1`에만 코드가 있다는 표현, source merge rehearsal을 현재 런타임 연계 완료로 읽을 수 있는 표현, 오래된 테스트 수치를 찾는다.

- [ ] **Step 2: B README의 상태 설명을 갱신한다**

B Phase 1 코드가 `develop`에 병합됐다는 사실과, 내부 구현·JDBC 경계가 존재하지만 A/AI adapter, 공개 API, 통합 migration은 미연결이라는 사실을 함께 적는다. Task 2 문서로 링크한다.

- [ ] **Step 3: STATUS와 NEXT-STEPS의 병합 기준점을 갱신한다**

맨 위에 현재 기준 branch와 병합 commit을 기록하고, 남은 일을 계약/adapter/DB migration/API로 분류한다. 완료되지 않은 B05-B12 항목의 체크 상태와 의미는 바꾸지 않는다.

- [ ] **Step 4: 문서 일관성을 검증한다**

Run: `rg -n "branches remain separate|feat/fullstack-b-phase1.*managed|health endpoint exist|not connected" README.md Docs`
Expected: 병합 후 사실과 모순되는 안내가 남지 않음.

- [ ] **Step 5: 변경 사항을 커밋한다**

```powershell
git add Docs/fullstack-b/README.md Docs/fullstack-b/STATUS.md Docs/fullstack-b/NEXT-STEPS.md
git commit -m "docs: align B status with merged develop"
```

### Task 4: 전체 검증과 변경 범위 확인

**Files:**
- Verify only: `services/core-api/`, `services/ai-service/`, Markdown documents

**Interfaces:**
- Consumes: Tasks 1-3의 POM·문서 변경
- Produces: 실행 가능한 병합 기준선과 명확한 제한 사항

- [ ] **Step 1: Core API 전체 테스트를 실행한다**

Run: `mvn -B -ntp test` from `services/core-api`
Expected: Java 21에서 Maven 모델 오류 없이 완료

- [ ] **Step 2: AI Service 개발 의존성을 준비한 뒤 테스트를 실행한다**

Run: `python -m pip install -e ".[dev]"` then `python -m pytest -q` from `services/ai-service`
Expected: AI health 및 내부 계약 테스트 완료. 패키지 설치에 네트워크 승인이 필요하면 요청하고, 승인 전에는 미검증으로 보고한다.

- [ ] **Step 3: 최종 변경 범위를 확인한다**

Run: `git diff origin/develop...HEAD --check` and `git status --short`
Expected: 공백 오류 없음. 설계·계획·POM·문서 외 프로덕션 Java 파일 변경 없음.

- [ ] **Step 4: 최종 검증 결과를 기록한다**

문서의 검증 수치는 실행 결과가 있을 때만 갱신한다. 환경/권한/외부 Docker 조건 때문에 실행하지 못한 항목은 성공으로 표시하지 않는다.

- [ ] **Step 5: 검증 기록을 커밋한다**

```powershell
git add Docs README.md
git commit -m "docs: record A/B cleanup verification"
```
