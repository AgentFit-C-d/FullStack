# Phase 2 시작 기준 및 A·B·AI 연결 점검 (2026-10-16)

## 현재 기준

- B 작업 브랜치 `feat/fullstack-b-phase2`는 A Phase 2가 병합된 `origin/main`의 `1046f46`을 기준으로 한다. 이전 develop 기반 작업은 `feat/fullstack-b-phase2-develop-base`에 보존했다. `origin/develop`의 최신 병합 커밋 `66a7d6a`는 이 main의 조상이 아니다. 두 원격 브랜치의 공통 조상은 `11056d6`이며, develop의 후속 정리 커밋을 이 B 브랜치에 일괄 반영하지 않았다.
- A 소유 인증·프로젝트·연동 adapter·OpenAPI·V5 migration과 FastAPI 파일은 이 B 브랜치에서 수정하지 않았다. 기존 미추적 발표 자료 `Docs/fullstack-b/presentation/`도 그대로 두었다.
- B의 프로젝트 ID JDBC 경계는 UUID를 검증해 PostgreSQL UUID 컬럼에 바인딩한다. 승인자·적용 보고자도 UUID로 기록한다. B 단독 DDL의 Phase 1 타입 가정 역시 UUID로 맞췄다.
- B Preview가 반환하는 생성·만료 시각은 PostgreSQL의 마이크로초 정밀도로 정규화한다. 그렇지 않으면 저장 직후 승인 시 DB에서 복원한 시각과 반환값이 달라져 승인 검증이 실패했다.

## 확인한 범위

| 경계 | 2026-10-16 확인 결과 |
| --- | --- |
| B1–B9와 A V5 스키마 | 빈 임시 PostgreSQL의 격리 스키마에 Flyway V1–V5를 적용하고 UUID 부모를 생성했다. 추천·Preview·승인·B9 메타데이터/USER 보고·B 데이터 삭제와 실패 시 트랜잭션 rollback 통합 테스트가 통과했다. |
| Java 회귀 | JDK 24로 Java 21 release를 컴파일하고 Byte Buddy 시작 에이전트를 지정한 Maven 전체 456개 테스트가 실패·오류 0건, 건너뜀 3건으로 통과했다. 호스트에 JDK 21은 없어 해당 런타임의 결과로 주장하지 않는다. |
| A 인증 경로 | 기존 Spring 애플리케이션 구성 및 A의 프로젝트/분석 WebMvc 테스트는 통과했다. 추천/Preview 공개 경로 자체의 테스트는 저장소에 없어 실제 HTTP 왕복 검증은 아직 없다. |
| AI Capability | `ai-service`는 아직 `CONTRACT_TEST_DOUBLE`의 `result.kind`만 반환한다. A adapter는 `result.claims`와 `result.questions`를 요구한다. 실제 AI 기반 추천 성공은 미검증이다. |
| Catalog | skills.sh 수집은 후보 연구 경로이며, 지원 OS·Client·버전·권한·조합을 실제 검증하고 독립 승인한 릴리스는 아직 확인되지 않았다. 합성 Catalog 테스트를 제품 추천 성공으로 해석하지 않는다. |
| Preview 신선도 | A의 `JdbcPreviewStoreAdapter`는 B 저장과 Audit을 하나의 트랜잭션에서 실행하지만 저장 직전 A의 현재 project/profile/developer/environment 버전을 다시 확인하는 코드는 보이지 않는다. 동시 수정 차단은 A 연동 과제다. |
| 승인·다운로드 API | B 내부 승인·B9 기록 경로는 있으나 A의 인증된 최종 승인/ZIP 다운로드 HTTP 경로는 아직 확인되지 않았다. |
| 최신 추천 조회 | B `OwnerCheckedCurrentReader`·`StoredMetadataReader` 런타임 구현을 추가했다. V5 DB 및 Spring bean 활성화는 검증했지만, 인증된 GET HTTP 왕복은 아직 검증하지 않았다. |

세부 검증 명령, 미구현 경계와 담당자는 [데모 준비 상태](phase2-demo-readiness.md)에 기록했다.
