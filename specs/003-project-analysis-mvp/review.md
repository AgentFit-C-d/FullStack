# 독립 구현 리뷰: 프로젝트 생성·분석 시작 MVP

## 범위

`spec.md`, `plan.md`, `tasks.md`, OpenAPI 계약과 현재 Core API 구현을 대조했다. 검토 기준은 원자성·원문 비보관·소유권·버전 충돌·동시성·계약 정합성이다.

## 결과

| 검토 항목 | 결과 | 확인 내용 |
|---|---|---|
| 생성 원자성 | PASS | 입력 검증과 프로젝트/입력/runtime 저장이 한 서비스 트랜잭션 경계에 있다. |
| 원문 비노출·비보관 | PASS | 응답 DTO와 Audit 차단 규칙에 raw/extracted/citation을 포함하지 않으며 terminal cleanup에서 scrub한다. |
| 소유권 | PASS | 프로젝트 조회·수정·입력 교체·분석 시작은 owner 조회를 먼저 수행한다. |
| 버전 충돌 | PASS | 수정·입력 교체·분석 시작에서 `expectedVersion`을 잠금 행과 비교한다. |
| 409/429 구분 | PASS | 같은 프로젝트는 409, 사용자/서버 capacity는 429 전용 코드로 분리했다. |
| PostgreSQL 동시성 | PASS | admission row lock, partial unique index, idempotency `ON CONFLICT DO NOTHING` 경계를 추가했다. Docker가 없으면 Testcontainers 테스트는 자동 skip된다. |
| OpenAPI/DTO/테스트 | PASS | role/runtime, 입력 메타데이터, Idempotency-Key, 안전한 오류 응답과 MockMvc/단위 계약 테스트를 맞췄다. |

## 잔여 운영 확인

- 실제 AI 분석 워커와 성공/일반 실패 결과 생성은 이번 MVP 범위 밖이다.
- 운영 배포 전에는 `AGENTFIT_COOKIE_SECURE=true`, OAuth secret 관리, PostgreSQL Flyway 적용 로그를 확인한다.
- Docker가 실행되지 않은 CI에서는 Testcontainers 통합 테스트가 skip되므로 PostgreSQL 전용 파이프라인에서 별도 실행한다.
