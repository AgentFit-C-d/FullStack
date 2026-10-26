# 실물 Skill 추천 샘플 점검 (2026-10-26)

## 실행 범위

- 합성 카탈로그 Compose를 중지하고 `infra/compose.catalog-review.yaml`로 교체했다. 기존 데모 DB 볼륨은 삭제하지 않았다.
- 카탈로그 1건은 skills.sh의 `obra/superpowers/test-driven-development`에서 내려받은 실물 Skill이다. Windows + Claude Code 2.1.270에서 `cap_test_authoring`만 검증했다. 별도 팀 승인 전이므로 운영 승인 카탈로그가 아니라 로컬 검토 대상이다.
- 프론트의 `LIVE DEMO`, `demo-claude-code`, 합성 설정 안내는 현재 실행 설정과 맞지 않는다.

## 실제 저장·응답

샘플 프로젝트 `e53c4ace-5b90-481b-9ead-6010414068d3`에 다음 기획을 입력했다.

```text
project_name: TestFlow
project_type: developer tool
domain: software development
features: Write tests
backend: Spring Boot
```

Core API → FastAPI Solar 분석은 `SUCCEEDED`를 반환했고, PostgreSQL의 `project_profile`에는 `DRAFT v1`과 사용자 확인 후 `CONFIRMED v1`이 각각 저장됐다. AI의 구조화 응답에는 `schemaVersion: 1.0`, `profile.project_name: TestFlow`, `profile.features: ["Write tests"]`, 필드별 근거 위치, 검토 질문, `modelMetadata.provider: solar`가 포함됐다.

실제 FastAPI needs 모듈에 확정 기능 `Write tests`를 넣으면 다음 두 판정이 나온다. 나머지 7개 Capability는 `NOT_REQUESTED`이다.

```json
{
  "needs": [{"id":"n1","name":"Write tests","query":"feature Write tests","sourceFields":["profile.item.features.0"]}],
  "claims": [
    {"capabilityKey":"cap_test_authoring","need":"REQUIRED","sourceFields":["profile.item.features.0"]},
    {"capabilityKey":"cap_test_execution","need":"REQUIRED","sourceFields":["profile.item.features.0"]}
  ],
  "questions": []
}
```

환경 `WINDOWS / claude-code / 2.1.270`, 설치 목록 확인됨·빈 목록, 역할 `FULLSTACK` 및 업무 `Write tests`를 저장한 후 추천 생성 API가 `NO_COMPATIBLE_TOOLS`를 반환했다. PostgreSQL `recommendation`에는 결과와 실물 카탈로그 릴리스 ID가, `recommendation_capability`에는 9개 판정이 저장됐고 `recommendation_item`은 0건이다. 현재 검증된 Skill이 테스트 작성만 보장하며 AI가 테스트 실행도 필수로 판정했기 때문이다. 테스트 코드에서 `cap_test_authoring`만 수동 입력했을 때 `RECOMMENDED`가 되는 것은 실제 AI 응답의 종단 성공을 증명하지 않는다.

## 기획 대비 판단과 남은 계약

- 원 PRD의 첫 카탈로그는 팀이 검증한 약 15~20개 도구다. skills.sh는 후보 출처이며 외부 검색 결과에 OS·Client 버전·권한·의존/충돌·설정 산출물 검증 정보가 없다. 후보 전체를 곧바로 추천 대상으로 저장하는 방식은 이 기준과 충돌한다.
- skills.sh 공식 `/api/v1/skills`는 Vercel OIDC 인증, 페이지네이션(최대 500건/페이지), 전역 all-time 목록 10만 행 제공 범위를 가진다. `1.2M` 같은 사이트 표시를 수집 가능한 Skill 개수로 해석하지 않는다. 이 로컬 환경에는 공식 API용 OIDC가 없으며 공개 실험 경로로 검색한 7건만 후보 스냅샷에 있다.
- AI 담당과 `Write tests`가 테스트 실행까지 필수인지 논의해야 한다. 필요하지 않다면 `cap_test_execution` 판정 기준을 구체적인 실행 표현으로 좁혀야 한다. 필요한 작업이라면 테스트 실행을 검증한 실물 도구를 카탈로그에 추가해야 한다.
- `D:/capstone/AgentFit_AI_v2`는 Bearer 인증과 `/internal/v1/analyze` 및 다른 needs 스키마를 사용한다. 현재 Core의 HMAC `/internal/v1/analysis` 계약과 직접 연결되지 않는다. URL만 바꾸는 것으로는 해결되지 않으며 A·AI 계약 합의가 필요하다.
- 긴 한글 샘플은 Solar 분석에서 HTTP 400으로 실패했으나 짧은 명시적 영문 샘플은 성공했다. AI의 오류 코드를 Core가 `AI_ANALYSIS_FAILED`로 뭉뚱그려 표시해 원인 진단이 어렵다.

재현 명령은 저장소 루트에서 다음과 같다. 개인 `env.docker` 값은 저장소에 복사하거나 출력하지 않는다.

```powershell
docker compose -p agentfit_real_review --env-file D:\capstone\FullStack\env.docker -f infra/compose.fullstack.yaml -f infra/compose.catalog-review.yaml -f infra/compose.web-demo.yaml up -d --build
```
