# Phase 2 사용자 적용 보고 API

`POST /api/projects/{projectId}/configuration-history/{configurationId}/reports`

사용자가 내려받은 설정을 적용해 본 결과를 기록한다. 요청에는 로그인 세션과 `X-XSRF-TOKEN`이 필요하다. A의 프로젝트 소유권 검사 후, B가 같은 PostgreSQL 트랜잭션에서 프로젝트 행을 다시 잠그고 해당 생성 이력이 프로젝트에 속하는지 확인한다. 보고와 Audit는 함께 저장한다.

```json
{"reportedState":"APPLIED","reasonCode":null}
```

`reportedState`는 `APPLIED` 또는 `FAILED`다. `FAILED`에는 `COPY_FAILED`, `AUTH_REQUIRED`, `DEPENDENCY_MISSING`, `UNKNOWN` 중 하나의 `reasonCode`가 필수다. `APPLIED`의 `reasonCode`는 `null`이어야 한다. 두 필드 외의 값은 422로 거부한다. 서버가 보고 ID·`USER` 출처·시각을 지정한다.

성공은 201 `{ "report": { "id", "configurationId", "reportedState", "reasonCode", "source": "USER", "reportedAt" } }`와 `Cache-Control: no-store`다. 보고는 사용자 진술이며 설치·인증·도구 동작이 검증됐다는 의미가 아니다. 기존 B9 생성 이력에서 최신 진술만 갱신하고 ZIP·설정 본문은 저장하지 않는다. 없는 프로젝트/생성 이력 또는 다른 사용자의 프로젝트는 404다.

V5 PostgreSQL 통합 테스트는 승인·다운로드 후 적용 진술과 Audit 기록, 타 사용자 거부를 검증한다. Controller 테스트는 소유권 호출, 응답의 USER 출처, 추가 필드 거부와 404를 검증한다. 이 테스트는 실제 GitHub 세션 HTTP 왕복이 아니며, A의 UUID 소유권 조회 오류가 해결되기 전까지 브라우저 시연 완료로 표시하지 않는다. [A 차단 사항](phase2-a-uuid-http-blockers.md).
