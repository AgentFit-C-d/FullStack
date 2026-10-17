# 설정 생성 이력 조회 API (2026-10-16)

`GET /api/projects/{projectId}/configuration-history?limit=20&cursor=...`

- A 세션 인증과 프로젝트 소유권 검사를 먼저 통과한다. 타 사용자 프로젝트는 404다.
- `limit` 기본값은 20, 허용 범위는 1–100이다. `cursor`는 서버가 발급한 불투명 문자열이며 최대 512자다. 잘못된 값은 422다.
- 최신 생성 순서로 페이지를 반환한다. 응답은 `items`, `nextCursor`로 구성되며 마지막 페이지에서는 `nextCursor`가 `null`이다.
- 각 항목은 생성 ID·시각, 추천/Preview/승인 ID, 생성 당시 basis·fingerprint, 현재 기준 `CURRENT/STALE`, 파일 경로·action·afterHash, `GENERATED`, 검증 `NOT_RUN/NONE`, 최신 USER 적용 보고만 포함한다. ZIP 바이트·파일 본문·Diff는 포함하지 않는다.
- 현재 basis와 저장된 basis는 같은 반복 읽기 트랜잭션에서 비교한다. 승인 Catalog가 없으면 현재 여부를 판정할 수 없어 503이다.

로컬 PostgreSQL V5 통합 테스트에서 생성→적용 보고→이력 조회가 이어지고 환경 버전 변경 후 `STALE`로 바뀌는 것을 확인했다. Controller 테스트는 소유권 차단과 잘못된 cursor/limit을 확인한다. 실제 로그인 HTTP 경로는 A의 UUID 조회 오류가 남아 있어 아직 성공으로 판정하지 않는다.
