# 프로젝트 소유권 계약 문서

이 폴더는 구현 전 소유권·오류·fixture의 **의미**를 기록한다. 공유 기준은 `contracts/openapi/core-api.yml`과 `contracts/openapi/ai-service.yml`이며, 이 문서는 두 OpenAPI가 표현하기 어려운 소유권·세션 수명·결과 저장 경계를 보완한다.

실제 URL, HTTP 요청·응답 schema, 오류 본문, 이름 길이, 삭제 확인·OAuth·CSRF의 wire 형식은 OpenAPI와 서버 검증·테스트에 같은 변경으로 반영한다. 프로젝트 자체 수정 endpoint는 이 기능 범위에 없으며, 409 충돌은 이후 하위 업무 보호 쓰기에서 사용한다.

- [project-ownership.md](./project-ownership.md): URL 비종속 계약 제약과 정상·실패 fixture

AI 호출 경계도 구현 전 계약 게이트다. Spring Boot는 `POST /internal/v1/analysis` body에 A6 `request_id`·`generation`·`contract_version`을 각각 `requestId`·`generation`·`contractVersion`으로 매핑해 보내고, `X-AgentFit-Key-Id`·`X-AgentFit-Timestamp`·`X-AgentFit-Signature`로 HMAC을 증명한다. FastAPI는 DB·SESSION에 접근하지 않으며, canonical 문자열·header·timestamp 허용 범위·keyId·키 교체·오류 의미와 `CONTRACT_TEST_DOUBLE` 응답은 `contracts/openapi/ai-service.yml`과 fixture를 함께 기준으로 삼는다.

