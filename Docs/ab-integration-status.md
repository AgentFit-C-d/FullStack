# A/B 병합 연계 현황

기준일: 2026-10-14. 이 문서는 `origin/develop`의 A 병합(PR #4)과 B 1차 병합(PR #3) 이후 상태를 설명한다. 팀 기준은 [1차 AB 통합 개발 기준](AgentFit_AB_1차통합_개발기준.pdf)이며, 구현 여부와 실제 런타임 연계 여부를 구분한다.

## 현재 결론

- A와 B 소스는 같은 `services/core-api` Spring Boot 애플리케이션에 병합되어 함께 컴파일·테스트된다.
- A는 인증, 프로젝트 소유권, 기획 입력, 분석, 현재 버전 검사, 공통 Audit, AI 서비스 호출 경계를 소유한다.
- B는 Catalog, 추천, 선택·권한, Preview, 승인, Export의 내부 규칙과 caller-owned JDBC reader/writer를 소유한다.
- B workflow는 필요한 신뢰 포트가 Spring bean으로 등록됐을 때만 활성화된다. 현재 A/AI의 실제 adapter와 인증된 공개 API가 없으므로, 이 workflow들이 실제 사용자 요청 경로로 연결된 상태는 아니다.

```text
브라우저 -> Core API (A 소유 인증·소유권·트랜잭션) -> AI Service -> 외부 LLM
                    |
                    +-> B Catalog / 추천 / Preview 내부 규칙
                    +-> PostgreSQL (Core API만 읽기·쓰기)
```

## 책임과 연계 수준

| 영역 | 현재 구현 | 실제 연결 상태 | 남은 연결 |
| --- | --- | --- | --- |
| A 프로젝트·인증·분석·Audit | Controller, 소유권 검사, JDBC/Flyway, AI 호출 경계 | A 단독 경로는 구현됨 | B 요청에 현재 snapshot·버전·Audit을 묶는 adapter |
| B Catalog·추천 | 검증, 결정적 추천, 메타데이터 조립, JDBC B1-B4 기록/조회 | 내부 workflow와 테스트 구현됨 | A owner-checked snapshot, AI Capability adapter, 원자 저장, 공개 API |
| B Preview·승인·Export | 선택/권한, 지문, ZIP 재생성, B5-B9 기록/조회 | 내부 workflow와 테스트 구현됨 | A 현재 basis, 인증 actor, Audit, HTTP 응답·다운로드 |
| B 이력·사용자 적용 보고 | 이력 조회·보고 규칙과 JDBC 경계 | 내부 service 구현됨 | A owner-checked reader, report store, 공개 API |
| Catalog 조회 | 승인 hash와 서버 directory가 있을 때만 reader 생성 | 조건부 bean 구현됨 | 실제 승인 Catalog와 인증된 GET API |

## B 조건부 workflow가 요구하는 포트

| Workflow | 활성화에 필요한 A/AI·운영 포트 |
| --- | --- |
| 추천 생성 | owner-checked snapshot, AI Capability analyzer, 승인 Catalog reader, 원자 recommendation store |
| Preview 생성 | trusted recommendation context, 승인 Catalog reader, 원자 preview store |
| 승인·다운로드·미충족 경고 | trusted preview/download reader, 승인 Catalog reader, approval/history store, 서버 ID 발급기, clock |
| 최신 추천 조회 | owner-checked current basis, 저장 recommendation metadata reader, 승인 Catalog reader |
| 이력·적용 보고 | owner-checked basis/generation reader, report store, report ID 발급기, clock |
| Catalog 조회 | authenticated caller, 승인 Catalog reader |

이 포트는 `services/core-api/src/main/java/com/agentfit/autoconfigure/`에 조건부 Spring bean으로 선언되어 있다. 테스트용 port를 제공한 경우에는 workflow가 활성화되지만, 이는 운영 adapter가 존재한다는 뜻이 아니다.

## 데이터베이스와 삭제 제약

B의 B1-B9 PostgreSQL DDL은 현재 [B 스키마 인계 문서](fullstack-b/db/postgresql-schema-handoff.md)와 `services/core-api/src/main/resources/db/postgresql/b-schema.sql`에 있다. A의 Flyway migration에 아직 편입하지 않았으므로, production schema migration으로 해석하면 안 된다.

B 행이 존재하는 프로젝트를 삭제하려면 A가 인증·소유권·expected version을 검사한 뒤, **같은 트랜잭션**에서 B child rows를 먼저 삭제하고 A Project를 삭제해야 한다. 현재 A Project 삭제 경로에 B deleter를 연결하지 않았으므로, 이 제약은 문서화된 후속 통합 작업이다.

## 안전한 다음 단계

1. A owner-checked snapshot/current-basis adapter와 B JDBC writer를 하나의 Spring transaction에 연결한다.
2. A의 AI client와 B Capability request/response contract를 합의하고 adapter를 구현한다.
3. B DDL의 Flyway 편입 순서와 A/B 프로젝트 삭제 transaction을 별도 통합 테스트와 함께 결정한다.
4. 위 경계가 준비된 뒤에만 인증된 Catalog/추천/Preview API를 공개한다.

세부 완료·미완료 항목은 [B 현재 상태](fullstack-b/STATUS.md), 계약 초안은 [A·AI 연동 계약 제안](fullstack-b/integration/ab-ai-contract-proposal.md)을 참조한다.
