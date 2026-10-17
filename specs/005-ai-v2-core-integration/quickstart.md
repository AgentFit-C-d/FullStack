# 통합 검증 빠른 안내

## 사전 조건

1. Core API와 AI 서비스에 같은 HMAC current key ID/secret을 설정한다.
2. 테스트는 유료 공급자 대신 deterministic provider fixture를 사용한다.
3. 승인 Catalog, 사용자 소유 프로젝트, A 사용자 확인 흐름을 준비한다.
4. 구현의 `contracts/openapi/ai-service.yml`는 [계획 계약](contracts/ai-service-v2-internal.yml)과 동기화한다.
5. 실제 Solar 호출을 할 때만 `AGENTFIT_AI_V2_PROVIDER=solar`와 `UPSTAGE_API_KEY`를 설정한다. 기본값은 deterministic provider다.

## 검증 시나리오

### A 분석

1. Core가 유효 HMAC, context, deadline, 임시 문서 입력으로 `/internal/v1/analysis`를 호출한다.
2. AI 결과를 받은 Core는 request context와 프로젝트 상태를 재검증하고 구조화 A 결과만 저장한다.
3. 성공·공급자 실패·예외 각각에서 PDF·Markdown·직접 텍스트 원문과 전체 추출문이 terminal 상태 뒤 DB·일반 로그·AI 응답에 없는지 확인한다. 실제 수신한 최종 분석 실패 응답만 암호화 진단 1건/시도·최대 7일 예외인지도 확인한다.

**기대 결과**: 기존 분석 경로와 HMAC 방식은 바뀌지 않으며 실패는 성공 분석으로 저장되지 않는다.

### 사용자 확인과 B 추천

1. 확인 전 A 결과로 `POST /api/projects/{projectId}/recommendations`를 호출한다.
2. 분석 terminal 상태 뒤에도 남은 A DRAFT를 사용자가 확인·수정해 confirmation snapshot을 만든다. DRAFT가 자동 삭제되지 않고 원문을 재조회하지 않는지 확인한다.
3. 같은 추천 요청을 다시 호출하고 Core가 `/internal/v1/skills/needs`를 호출하게 한다.

**기대 결과**: 1은 거부되고 3만 B 추천을 생성한다. 알 수 없는·중복 Capability key와 허용되지 않은 source field는 `FAILED`; 유효 `UNDETERMINED`는 `NEEDS_INFORMATION`; Catalog 조합 부재는 `NO_COMPATIBLE_TOOLS`다.

### rank 격리

1. HMAC `/internal/v1/skills/rank`에 최대 6 need와 최대 15 candidate를 보낸다.
2. 후보별 score/reason을 받는다.
3. B 추천 DB 상태·항목·정상 상태가 바뀌지 않았는지 확인한다.

**기대 결과**: B 담당자 승인 전 rank는 최종 추천에 영향을 주지 않는다.

AI Service는 세 경로에서 DB를 조회하거나 원문을 저장하지 않는다. Core가 소유한 HMAC context와 transient 입력을 받고, 분석 결과·need·후보 점수만 응답한다.

## 실행

```powershell
.\mvnw.cmd -f services\core-api\pom.xml test
python -m pytest services\ai-service\tests -q
```

실행 전 AI v2 이식 후의 Python 3.12 환경과 lock file을 준비한다. HMAC tamper/replay, 계약 fixture, 임시 원문 폐기, B 상태 분기 테스트를 같은 변경에서 실행한다.

## 2026-10-17 검증 기록

- `uv run --project . --extra dev pytest tests -q` → **27 passed** (경고 3개는 FastAPI/httpx deprecation warning).
- 선택 Java 테스트 → **통과**: HMAC client, typed AI failure, failure diagnostic, terminal cleanup, A DRAFT persistence, profile confirmation, A→확인→B HMAC fixture, rank isolation, OpenAPI context.
- `mvn -o -q -f services/core-api/pom.xml -DskipTests compile` → **통과**.
- `mvn -o -q -f services/core-api/pom.xml test` → **통과**.
