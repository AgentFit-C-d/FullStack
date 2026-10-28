# 로컬 실물 Skill 연결 검증 — 2026-10-28

## 활성 경로

사용자 요청에 따라 로컬 Compose에서 고정된 실물 Skill 후보 1종을 카탈로그로 사용했다. 정확한 대상은 `WINDOWS / claude-code / 2.1.310`, Skill은 `obra/superpowers/test-driven-development`이고 지원 Capability는 `cap_test_authoring`뿐이다. `cap_test_execution`은 미충족으로 유지한다. 설정은 [로컬 Compose](../../../infra/compose.catalog-live-local.yaml)에만 두었다.

현재 활성 AI는 FullStack 안의 FastAPI `services/ai-service`다. 이 서버는 `claims` 9종과 `questions`를 기존 HMAC 계약으로 반환한다. 별도 저장소 `D:/capstone/AgentFit_AI_v2`는 Bearer·원문 참조·예산 계약이 달라 이 실행 경로에 사용하지 않았다. B가 추가한 별도 AI `needs` 클라이언트는 검색용 계약을 수용하지만 추천 API에는 연결하지 않았다.

## 실행과 확인

```powershell
docker compose -p agentfit_live_local --env-file D:\capstone\FullStack\env.docker -f infra/compose.fullstack.yaml -f infra/compose.catalog-live-local.yaml -f infra/compose.web-demo.yaml up -d --build
```

- Core `http://127.0.0.1:18080/health`, AI `http://127.0.0.1:8001/health`, 웹 `http://127.0.0.1:8080/`가 모두 HTTP 200이었다.
- `LiveAiPostgresRecommendationTest`를 실제 로컬 FastAPI와 PostgreSQL 17에 연결해 실행했다. CONFIRMED Profile → 9종 Capability → 추천 Skill 1개 → `partial_required_coverage` → 미리보기의 `unmetCapabilityKeys=[cap_test_execution]` 및 DB 저장을 확인했다. 격리 테스트 스키마는 실행 후 삭제된다.
- `SkillsShRealCatalogWorkflowTest`에서 동일한 `2.1.310` 후보로 권한 ASK 선택, Preview, 승인, ZIP 생성 및 Skill 파일·MIT 고지 포함을 확인했다.
- 후보 해시 `e220a4a01e8865779c78464e722dbb24dff112c7e44475c284a54c4316661527`의 매니페스트 12개 파일 SHA-256이 모두 일치했다. Java 후보 검증기는 `CANDIDATE_SCHEMA_VALID`를 반환했다.

이 검증은 **백엔드 로컬 기능 경로**다. 실제 사용자의 GitHub 로그인 세션으로 화면에서 프로젝트를 만들고 ZIP을 받는 조작까지 이번에 수행한 것은 아니다. `AgentFit_AI_v2`로 서버를 교체하는 작업도 완료한 것으로 표시하지 않는다. 복수 Skill/MCP 조합은 검증하지 않았고, `skills.sh` 전체 후보를 추천에 자동 등록하지 않았다.

## 상태 해석

사용자가 요청한 로컬 실행에는 이 후보를 사용한다. 후보 검증기의 `INDEPENDENT_APPROVAL_REQUIRED`는 기존 팀 릴리스 정책을 나타내며, 이번 로컬 실행이나 해시 일치를 팀의 독립 승인 기록으로 표기하지 않는다. 운영 릴리스에 같은 정책을 적용할지는 팀이 결정해야 한다.
