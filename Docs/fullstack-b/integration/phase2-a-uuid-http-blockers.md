# Phase 2 A/B HTTP 연결 차단 사항 (2026-10-16)

## 실제 재현 결과

A의 UUID `USER`·`project`와 B의 저장된 추천을 Flyway V1–V5 PostgreSQL 격리 스키마에 넣고, Spring Boot `MockMvc`로 `GET /api/projects/{projectId}/recommendations/latest`를 호출했다. 인증 주체 UUID를 `SecurityContext`에 주입하고 A의 `SessionPrincipalResolver`와 `ProjectOwnershipGuard`를 실제 Bean으로 통과시켰다. **소유자와 다른 사용자 모두 500**을 받았고 B 조회 서비스에는 도달하지 못했다. 테스트가 재현 중 실패했으므로 회귀 테스트로 커밋하지 않았다. 테스트용 스키마는 실행 후 삭제했다.

직접 원인은 A의 `JdbcProjectRepository.findByIdAndOwnerUuid`가 `project.id`(PostgreSQL `UUID`)에 Java `String`을 바인딩하는 것이다. PostgreSQL 오류는 `operator does not exist: uuid = character varying`이며 호출 경로는 `RecommendationController.latest` → `ProjectOwnershipGuard.requireOwned` → 위 저장소 메서드다. A 소유 코드에서 프로젝트 ID를 검증한 정규 UUID로 변환해 바인딩한 후, 소유자 200·타 사용자 404를 PostgreSQL HTTP 테스트로 확인해야 한다. 이 문제는 추천 생성·Preview·B 승인·다운로드처럼 같은 A 소유권 검사를 쓰는 경로에도 영향을 준다.

## 로그인 경로의 별도 확인 사항

A의 `JdbcUserAccountRepository`에는 V5 UUID 스키마와 맞지 않는 과거 BIGINT 처리도 남아 있다.

- `findByGithubUserId`는 UUID `id`를 `getLong`으로 읽는다.
- `createFromGithub`는 UUID 기본키를 지정하지 않고 삽입하며 `RETURNING id`를 `Long`으로 읽는다. V5의 `"USER".id`는 기본값이 없는 `UUID NOT NULL`이다.
- `AgentFitOAuth2User.getName()`은 UUID 계정이면 UUID를 세션 주체 이름으로 사용하므로, 계정 조회·생성 경로부터 UUID 기준으로 정리해야 실제 GitHub 로그인이 연결된다.

이 항목은 코드 정적 검토에서 확인했다. 실제 GitHub OAuth 왕복은 아직 실행하지 않았으므로 로그인 응답 상태까지 검증됐다고 보지 않는다.

## 인계 및 B 재검증 순서

1. A 담당자가 위 두 저장소의 UUID 바인딩·계정 생성/조회 경로를 수정한다.
2. A의 인증 필터와 세션을 포함해 로그인 → 소유 프로젝트 조회, 타 사용자 차단을 검증한다.
3. 같은 실제 A 데이터로 B 추천 생성 → 최신 조회 → Preview → 승인 → 다운로드를 HTTP 왕복한다.
4. AI의 실제 Capability 계약과 독립 승인 Catalog가 준비되기 전에는 합성 Catalog·테스트 응답을 사용자 기능 성공으로 표시하지 않는다.

A 소유 파일은 이번 B 브랜치에서 수정하지 않았다.
