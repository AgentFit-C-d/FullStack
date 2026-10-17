# 검증 가이드: A 분석 → 확인 → B 추천

## 1. 사전 준비

저장소 루트에서 환경 파일을 준비한다. 실제 Solar key는 Git에 올리지 않는다.

```powershell
Copy-Item .env.docker.example .env.docker
# .env.docker에 기존 DB/OAuth/HMAC 값과 아래 두 값을 설정한다.
# AGENTFIT_AI_V2_PROVIDER=solar
# UPSTAGE_API_KEY=<Solar key>
```

Compose 구현 후에는 Core가 세 AI URL을, AI service가 provider와 Solar key를 받는지 확인한다.

```powershell
docker compose --env-file .env.docker -f infra/compose.fullstack.yaml config --no-interpolate
docker compose --env-file .env.docker -f infra/compose.fullstack.yaml up --build -d
docker compose --env-file .env.docker -f infra/compose.fullstack.yaml ps
```

`config --no-interpolate` 출력에는 `${UPSTAGE_API_KEY}`·HMAC 변수 이름만 보여야 하며 실제 값이
나오면 안 된다. `postgres`, `ai-service`, `core-api`가 healthy 또는 running이어야 한다. 실제
Solar key 전달은 출력·inspect가 아니라 3단계의 Solar smoke 성공으로만 확인한다.

## 2. 자동 검증

구현 단계에서 추가되는 Worker·PostgreSQL 통합 테스트와 기존 회귀 테스트를 실행한다.

```powershell
mvn -o -q -f services/core-api/pom.xml test
uv run --project services/ai-service --extra dev pytest services/ai-service/tests -q
```

PostgreSQL 통합 테스트는 fresh Flyway migration에서 UUID/status analysis attempt, Worker claim,
성공 DRAFT, 실패 scrub을 확인해야 한다. H2 통과만으로는 이 조건을 충족하지 않는다.

Compose smoke는 공개 API를 추가하지 않고 Core의 기존 AI client/service를 통해 analysis, needs,
rank 세 내부 경로를 각각 한 번 호출한다. 각 호출은 2xx, request context 일치, HMAC 검증 통과를
확인한다. `rank` 응답은 이 smoke에서만 검증하며 최종 추천 상태나 저장 항목을 바꾸지 않는다.

## 3. Docker 데모

1. 기존 인증된 사용자로 프로젝트와 PlanningInput을 만든다. 새 분석 요청은 202/`PROCESSING`을
   반환해야 한다.
2. Worker가 작은 텍스트 입력을 Solar에 한 번 전달해 A DRAFT와 `SUCCEEDED`를 만든다. Compose
   smoke는 Core를 통해 analysis, needs, rank 세 내부 경로가 각각 HMAC을 통과하는지도 확인한다.
3. DB 또는 owner-checked project 조회로 raw content·extracted text·citations가 scrub되었고
   DRAFT만 남았는지 확인한다.
4. 기존 confirm API로 사용자가 DRAFT를 확정한다. DRAFT가 자동 삭제되지 않고 CONFIRMED
   Profile·확인 이벤트가 만들어져야 한다.
5. 기존 recommendation API를 호출한다. `RECOMMENDED`, `NEEDS_INFORMATION`,
   `NO_COMPATIBLE_TOOLS` 중 하나면 정상 B 결과이며, AI/계약/저장 실패와 구분되어야 한다.

공개 API 데모에는 기존 인증을 사용하며, 이 기능을 위해 인증 우회 endpoint를 추가하지 않는다.
프론트엔드 없이 재현이 필요하면 같은 흐름을 Core service-level integration test에서 인증된
test principal로 실행하되, AI 호출은 Compose의 실제 AI service를 향하게 한다.

## 4. 완료 판정

다음 세 증거가 모두 있어야 풀스택 B 담당자에게 완료본으로 넘길 수 있다.

1. Worker가 `PROCESSING` attempt를 한 번 처리해 DRAFT를 저장한 테스트 또는 로그.
2. fresh PostgreSQL migration과 UUID/status Repository 통합 테스트 통과 기록.
3. Docker Compose에서 Solar 실제 1회 분석, 원문 scrub, DRAFT 확정, 정상 B 결과까지의 기록.
