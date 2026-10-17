# 승인 카탈로그 조회 API (2026-10-16)

`GET /api/tool-catalog`

로그인한 사용자에게 서버가 독립 승인한 Catalog 릴리스를 읽기 전용으로 반환한다. 선택 query는 `clientId`, `osFamily`, `capabilityId`이며 각 항목은 한 번만 보낼 수 있다. 알 수 없는 query·중복 값·빈 값·미정의 Capability는 422다. 승인 릴리스가 없거나 파일 무결성/승인 해시가 맞지 않으면 `503 CATALOG_UNAVAILABLE`이며, 빈 후보 목록으로 위장하지 않는다.

현재 응답은 `revision`(릴리스 ID), `catalogHash`, `items`다. 항목은 B의 `CatalogReadService.Item` 읽기 모델을 사용해 `key`, 이름·종류·버전·출처, Capability/의존성/충돌/포함 ID, 실제 support 검사값과 권한 매핑 근거를 반환한다. 해당 필터에서 검증된 support가 하나도 없는 도구는 제외한다. `Cache-Control: no-store`를 사용한다.

이 DTO는 [공통 API 디자인 초안](../../../../Docs/api/02-recommendations.draft.md)의 예시 `id`·`verification.supportMatrix` 구조와 아직 다르다. 프론트 연결 전에 팀과 응답 필드 계약을 확정해야 한다. 이 API는 skills.sh 수집 후보를 승인 Catalog로 승격하지 않는다. 실제 Claude Code 릴리스가 검증·승인될 때까지 운영 환경에서는 503이 정상적인 차단 결과다.

Controller 테스트는 인증, 단일 필터, 승인 릴리스 조회, 미승인 해시, 잘못된 query를 확인한다. A GitHub OAuth·소유권 UUID 오류와 실제 Catalog 부재 때문에 사용자 브라우저 전체 시연은 아직 확인되지 않았다.
