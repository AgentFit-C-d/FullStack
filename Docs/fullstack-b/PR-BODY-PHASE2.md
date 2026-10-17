# PR 제목

feat: 풀스택 B Phase 2 UUID 연동과 설정 최종화 API 연결

# PR 본문

## 변경 결과

A Phase 2의 UUID 스키마를 기준으로 B의 추천·Preview·승인·생성 이력 저장 경계를 맞췄습니다. 이후 최신 `develop`의 A 분석 Worker·Profile 확정 및 AI v2 내부 호출 코드를 병합했습니다. B의 읽기 전용 최신 추천, 승인, ZIP 다운로드, 사용자 적용 보고, 생성 이력, 승인 Catalog 조회 API를 연결했습니다. 프로젝트 인증·소유권은 A의 공통 경계를 재사용하고, 승인·다운로드·보고 저장 시에는 B가 필요한 상태를 다시 확인합니다. Preview 저장의 A 현재 basis 재검사는 아래 후속 항목으로 남습니다.

- 추천·Preview·승인·export·보고의 UUID 바인딩과 PostgreSQL V5 저장/조회/삭제를 정리했습니다.
- 최신 추천과 생성 이력은 현재 Project/Profile/Developer/Environment/Catalog 기준으로 `CURRENT`/`STALE`을 표시합니다.
- 사용자가 일부 필수 Capability를 선택하지 않아도 경고를 유지하며 Preview·승인·다운로드할 수 있습니다. 승인된 Preview의 지문·만료·현재 버전을 확인하고 ZIP을 재생성하며, 본문 대신 생성 메타데이터와 Audit만 저장합니다.
- 다운로드 후 `APPLIED`/`FAILED`는 사용자의 보고로 기록하며 설치 검증 결과로 표시하지 않습니다.
- Catalog 조회는 독립 승인된 릴리스만 반환하고, 승인 데이터가 없거나 해시가 맞지 않으면 `503 CATALOG_UNAVAILABLE`을 반환합니다. skills.sh 수집 후보를 자동으로 추천 대상으로 올리지 않습니다.

## 검증

- `develop` 병합 후 Core API 테스트: **511개, 실패 0, 오류 0, 건너뜀 58**. AI 서비스 테스트: **27개 통과**. Core API는 Java 21 release 대상으로 컴파일했고, 실행 JVM은 JDK 24와 Mockito용 Byte Buddy 시작 에이전트를 사용했습니다.
- B의 PostgreSQL V5/V6 최신 추천 조회·설정 최종화 테스트는 로컬 PostgreSQL 17의 별도 임시 스키마에서 통과했습니다. 환경변수가 없는 기본 실행에서는 PostgreSQL 조건부 테스트 등이 건너뛰어졌습니다.
- 실제 A 인증 세션을 포함한 추천→다운로드 HTTP 종단 검증은 아직 완료하지 못했습니다.

## 후속 연동과 제한

- 기존 A UUID 조회·GitHub 계정 생성 차단은 `develop` 병합 전 [재현·인계](https://github.com/AgentFit-C-d/FullStack/blob/feat/fullstack-b-phase2/Docs/fullstack-b/integration/phase2-a-uuid-http-blockers.md)한 내용입니다. 병합된 A 코드에는 UUID 경로 수정이 포함됐으나, 실제 OAuth 세션부터 B 추천까지의 HTTP 종단 검증은 아직 필요합니다.
- AI v2 needs/rank 호출 경로는 병합됐지만 실제 Solar 호출 smoke test는 남아 있습니다. 실제 Claude Code 도구·OS·버전·권한·조합에 대한 승인 Catalog도 없어 사용자 추천부터 ZIP 다운로드까지의 브라우저 시연 성공을 주장하지 않습니다.
- Catalog GET의 응답 필드(`key`, `support` 등)는 공통 API 초안(`D:/capstone/Docs/api/02-recommendations.draft.md`)의 예시(`id`, `verification.supportMatrix` 등)와 다릅니다. 프론트 연결 전에 계약을 맞춰야 합니다.
- Preview 저장의 동시 변경 차단은 A의 현재 basis 재조회·비교가 동일 트랜잭션에서 확정되어야 합니다.

리뷰 기준은 [Phase 2 데모 준비 상태](https://github.com/AgentFit-C-d/FullStack/blob/feat/fullstack-b-phase2/Docs/fullstack-b/integration/phase2-demo-readiness.md)와 [B API 계약 문서](https://github.com/AgentFit-C-d/FullStack/blob/feat/fullstack-b-phase2/Docs/fullstack-b/integration/phase2-finalization-api.md)입니다. 이 PR의 범위는 **B 경계의 구현 및 검증**이며, A·AI·실제 Catalog가 연결된 종단 시연 완료는 별도 확인이 필요합니다.
