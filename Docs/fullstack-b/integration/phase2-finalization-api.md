# Phase 2 B 승인·다운로드 API 연결

## 범위

A의 세션 인증과 `ProjectOwnershipGuard`를 재사용한다. B는 저장된 Preview와 A의 현재 확정 프로필·개발자·환경 버전을 검증하고, 독립 승인된 Catalog로 승인·ZIP을 처리한다. A 소유 Java/SQL/OpenAPI 파일은 수정하지 않았다. 이 문서는 프론트 연동을 위한 현재 B 구현 계약이며, A의 `contracts/openapi/core-api.yml` 통합은 팀 계약 작업으로 남는다.

두 POST 요청에는 `AGENTFIT_SESSION` 쿠키와 `GET /api/auth/csrf`에서 받은 `X-XSRF-TOKEN` 헤더가 필요하다. 프로젝트가 본인 소유가 아니면 404로 처리된다.

## 승인

`POST /api/projects/{projectId}/config-previews/{previewId}/approval`

```json
{"fingerprint":"<Preview 응답의 64자리 SHA-256>","confirmed":true}
```

201 응답: `approvalId`, `previewId`, `fingerprint`, `approvedAt`, `expiresAt`, `unmetCapabilityKeys`. 마지막 배열은 일부 필수 Capability가 비어 있어도 승인할 수 있는 현재 정책의 경고다. 요청 본문의 확인 여부·fingerprint와 DB에 저장된 Preview, 만료 시각, 현재 A 버전, 승인 Catalog를 비교한다. 프로젝트 소유자와 현재 버전을 저장 트랜잭션에서 다시 확인한 뒤 B5 승인 정보와 Audit를 함께 커밋한다.

## 다운로드

`POST /api/projects/{projectId}/config-approvals/{approvalId}/download`

```json
{
  "previewId":"<승인된 Preview ID>",
  "fingerprint":"<승인된 Preview fingerprint>",
  "previewRequest":{
    "basis":{},"recommendationId":"<추천 ID>","target":{},
    "selectedToolKeys":[],"permissionSelections":[],
    "existingState":"UNKNOWN","providedFiles":[],"generatorVersion":"static-v1"
  }
}
```

`previewRequest`는 Preview 생성 때 보낸 전체 입력을 다시 보내야 한다. 기존 파일의 원문과 생성 콘텐츠는 DB에 저장하지 않으므로, 제공 파일이 있었으면 그 입력도 다시 필요하다. 서버는 브라우저의 basis를 신뢰하지 않고 A 현재 상태, 저장된 추천·Preview·승인, 승인 Catalog와 재생성한 fingerprint를 비교한다. 성공하면 200 `application/zip`을 반환하고 `X-Config-Export-Id`, `X-Unmet-Capability-Keys` 헤더를 포함한다. 미충족 키 헤더는 쉼표로 구분하며 없으면 빈 문자열이다. ZIP 본문은 저장하지 않고 B9 생성 메타데이터와 Audit만 같은 트랜잭션에 기록한다.

로그인/CSRF 실패는 각각 401/403, 다른 프로젝트나 없는 Preview·승인은 404, 오래된 Preview는 `VERSION_CONFLICT` 409로 처리한다. 잘못된 입력은 422다. 승인 Catalog가 서버에 설정되지 않으면 503으로 중단한다.

현재 프로젝트 잠금과 저장 직전 버전 재검사는 연결했지만, A의 프로필·환경 수정 트랜잭션과 경합하는 실제 동시성 테스트는 아직 없다. 이 경로의 완전한 경쟁 조건 차단은 A 저장 규칙을 함께 확인해야 한다.

## 검증과 시연 경계

`FinalizationApiV5PostgresTest`는 격리된 PostgreSQL V1–V5 스키마에서 부분 구성 승인, 경고 유지, ZIP 생성, 메타데이터·Audit 저장, 환경 버전 변경 후 다운로드 차단을 확인한다. HTTP 단위 테스트는 A 인증·소유권 호출과 응답 형식을 확인한다. 이 테스트의 Catalog는 합성 데이터이며 실제 Claude Code 릴리스 승인을 뜻하지 않는다. 실제 사용자 추천→Preview 시작은 AI Capability 응답과 검증·승인된 Catalog가 있어야 시연할 수 있다.
