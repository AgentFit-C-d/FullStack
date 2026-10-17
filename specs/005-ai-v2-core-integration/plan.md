# 구현 계획: AI v2 핵심 로직 통합

**브랜치**: `005-ai-v2-core-integration` | **날짜**: 2026-10-17 | **명세**: [spec.md](spec.md)

## 요약

기존 FullStack의 HMAC 분석 계약과 Core API의 권한·저장 경계를 유지한다. Python AI 서비스에는 AgentFit_AI_v2의 문서 추출, 공급자 실행, 근거 검증, 필요 역량 도출 핵심만 이식한다. A는 구조화 결과를 저장하고 사용자의 명시적 확인을 받는다. B는 확인된 snapshot으로 AI의 9개 Capability 판단을 받아 기존 승인 Catalog 추천을 생성·저장한다. `rank`는 HMAC 내부 API로 제공하되 B 담당자가 매핑 규칙을 승인하기 전에는 최종 추천에 사용하지 않는다.

## 기술 환경

**언어/버전**: Java 21 / Spring Boot 3.3.5, Python 3.12 목표 (현 AI 서비스는 Python 3.11 이상)
**주요 의존성**: Spring Web·Validation·JDBC·Flyway·Jackson, FastAPI·Uvicorn·pytest, AI v2의 Upstage Solar/Information Extract 및 문서 추출 의존성
**저장소**: PostgreSQL은 Core API만 접근. PDF·Markdown·직접 입력 원문과 전체 추출문은 분석 terminal 상태까지의 임시 입력이며 영구 저장하지 않는다. 구조화 A DRAFT는 남고, 실패 진단 저장은 기본 비활성이다. 명시적으로 활성화한 환경에서만 실제 수신한 최종 분석 실패 응답을 암호화 진단 1건/시도·최대 7일로 예외 보관한다.
**테스트**: JUnit/MockMvc·Testcontainers·H2, pytest/TestClient, OpenAPI fixture와 HMAC 계약 테스트
**대상**: Core API와 FastAPI AI 서비스의 사설 HTTPS HTTP 통신
**성능**: Core deadline을 전달하고 AI 실행은 최대 178초로 제한한다.
**제약**: HMAC 필수, AI의 DB 직접 접근 금지, 원문·전체 추출문·Secret·정상 원시 공급자 응답의 로그·DTO·영구 저장 금지. 실패 진단 예외는 기본 비활성이고, 명시적 설정·암호화·1건/시도·최대 7일·일반 로그/Audit 배제를 모두 충족해야 한다.
**범위**: 기존 `/internal/v1/analysis` 교체, HMAC `skills/needs`·`skills/rank` 추가, A 확인 후 B Capability 연결. 외부 Skill 검색·자동 승인·`rank` 기반 최종 추천은 제외.

## 헌장 점검

| 점검 | 대응 | 결과 |
|---|---|---|
| Core API 단일 DB 소유 | AI는 결과만 반환, Core가 검증·저장 | 통과 |
| 사용자 확인 | B는 CONFIRMED snapshot 없이는 추천 불가 | 통과 |
| 권한·버전 | Core가 AI 호출 전후로 검증 | 통과 |
| 실패 보존 | 새 실패가 기존 성공 추천을 덮지 않음 | 통과 |
| 계약 우선 | OpenAPI·DTO·fixture·테스트를 같은 변경에서 갱신 | 통과 |
| 최소 변경 | 기존 AI client·B adapter·Catalog workflow를 확장 | 통과 |

## 소스 구조

```text
contracts/openapi/ai-service.yml                 # 실제 공유 계약
services/ai-service/app/
├── main.py                                      # HMAC 라우트 조립
├── internal_auth.py                             # 기존 HMAC·replay 방지
├── internal_analysis.py                         # 분석 인증·입력 검증
└── ai_v2/                                       # 새 무상태 분석·needs·rank 코어
services/core-api/src/main/java/com/agentfit/coreapi/
├── domain/analysis/                             # HMAC 서명·AI HTTP client·A 저장
├── integration/ab/                              # A snapshot → AI Capability 어댑터
├── recommendation/ai/                           # 9개 Capability 검증
└── catalog/                                     # 승인 Catalog 최종 추천·저장
specs/005-ai-v2-core-integration/
├── research.md
├── data-model.md
├── contracts/ai-service-v2-internal.yml
└── quickstart.md
```

**구조 결정**: A와 B는 동일 Core API 모듈로 남긴다. FastAPI는 계산만 수행하며 DB, 사용자 권한, 추천 상태를 소유하지 않는다.

## AI 입력 투영 규칙

Core는 `project_name`, `project_type`, `domain`을 각각 `profile.projectName`,
`profile.projectType`, `profile.domain`으로, 나머지 7개 planning field를
`profile.item.<field_key>.<ordinal>`로 투영한다. 별도로 신뢰하는 runtime
context는 `developer.version`, `environment.osFamily`, `environment.clientId`,
`environment.clientVersion`뿐이다. `purpose`, `target_users`, `constraints`,
별칭 및 나머지 field key는 snapshot에 있더라도 Core에서 거부한다.

A 확인은 Core의 트랜잭션 안에서 CONFIRMED Profile,
`profile_confirmation_event`, 그리고 확인 Audit을 함께 만들며 DRAFT를 삭제하지 않는다.
셋 중 하나라도 실패하면 모두 롤백한다.
B의 공개 추천 API는 CONFIRMED Profile이 없으면 HTTP 409
`PROFILE_NOT_CONFIRMED`로 응답한다.

## 구현 순서

1. AI v2 의존성·코어 모듈을 감사해 현 AI 서비스의 무상태 패키지로 이식하고 Bearer·Express·Vercel 코드는 제외한다.
2. 기존 분석 HMAC 인증·replay 검사를 재사용해 실제 문서 분석과 계약 검증을 연결한다.
3. Core AI HTTP client를 경로별 HMAC 호출로 확장하고 OpenAPI·fixture를 동기화한다.
4. A가 구조화 DRAFT만 저장하고 PDF·Markdown·직접 입력 원문과 전체 추출문을 terminal 상태에서 폐기한다. DRAFT는 사용자가 나중에 CONFIRMED Profile·확인 이벤트를 만들 때까지 남으며, 확인이 DRAFT를 삭제하지 않는다. 기존 `document_summary`가 새 경로에 원문 또는 재조회 위치를 남기지 않는지 감사한다.
5. 기본 비활성인 실패 진단을 명시적 설정 환경에서만 실제 수신한 최종 분석 실패 응답 1건/시도로 최대 7일 보관하고, 정상 응답·미수신·원문·일반 로그·Audit에는 남기지 않는다.
6. B 어댑터가 확인된 snapshot의 허용 필드만 AI에 전달하고 9개 `CapabilityKey` claim/question을 검증한다.
7. 정상 상태·실패 상태·이전 성공 추천 보존을 통합 테스트로 고정한다.
8. `rank`는 독립 HMAC 계약과 테스트까지만 구현한다. B 최종 추천 반영은 B 담당자의 별도 승인 뒤 작업한다.

## 복잡성 추적

| 위반 | 필요 이유 | 더 단순한 대안을 거절한 이유 |
|---|---|---|
| 없음 | - | - |
