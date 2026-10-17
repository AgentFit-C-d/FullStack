# AI v2 핵심 로직 통합 독립 리뷰

검토일: 2026-10-17

## 결론

AI 서비스는 HMAC으로만 호출되고 DB·권한·문서 장기 보관을 맡지 않는다. Core가 A DRAFT/CONFIRMED Profile, 사용자 권한, Audit, B 추천 상태와 Catalog 저장을 소유한다. rank는 `SkillRankPreviewService`를 통해 후보 점수만 반환하며 추천 저장 경로와 연결되지 않는다.

## 점검 결과

- HMAC: analysis/needs/rank 경로별 canonical PATH와 request context를 사용하고, Core client는 응답 context를 재검증한다.
- 원문 수명: terminal 성공·실패에서 Core planning input의 `raw_content`, `extracted_text`, `citations`를 scrub한다. AI 서비스는 요청 범위의 `Document`만 만들고 반환/저장하지 않는다.
- 실패 구분: transport, contract, provider, input 오류는 typed exception으로 유지한다. 유효한 `UNDETERMINED + question`만 `NEEDS_INFORMATION`으로 흐른다.
- 진단 예외: 기본 비활성, 활성화해도 provider 응답을 AES-GCM으로 제한 저장하며 시도당 1건·7일 TTL·주기 삭제를 적용한다. Audit에는 raw response나 secret을 넣지 않는다.
- A/B 경계: DRAFT 확인은 CONFIRMED Profile과 confirmation event를 만들지만 DRAFT를 삭제하지 않는다. B는 confirmed snapshot만 읽고 `PROFILE_NOT_CONFIRMED`를 추천 전에 거부한다.
- Capability: Core approved source path만 AI needs 입력으로 전달하며 `purpose`, `target_users`, `constraints`와 alias는 허용하지 않는다. Capability key는 기존 9개 집합으로 검증한다.
- rank 격리: rank 호출은 추천 item/status/persistence를 변경하지 않는다.

## 최종 검증

AI 서비스 `27 passed`, Core API 전체 Maven suite 통과, `git diff --check` 통과. FastAPI/httpx deprecation warning 3개만 남아 있으며 기능 실패는 아니다.
