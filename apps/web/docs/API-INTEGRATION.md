# Analysis / Profile 연동 준비 메모

기준: 팀 공유 `AgentFit-team-handoff-2026-09-08-v2/Docs/api/openapi.phase1.json`,
`Docs/api/01-project-analysis.md`. 아래 주소는 기존 계약을 요약한 것이며 이번 작업에서 호출하거나 새로 구현하지 않았다.

| 기능 | 기존 계약 / 프론트 연결 지점 |
| --- | --- |
| 인증 세션 | GET `/api/auth/get-session`: `{user:{id,name},session:{expiresAt}}` 또는 null. 토큰을 프론트에 노출하지 않는다. OAuth 성공은 분석 상태 전환 신호가 아니다. |
| 분석 시작 | POST `/api/projects/{projectId}/analysis`: raw PDF bytes / Markdown / 직접 텍스트. MIME application/pdf, text/markdown, text/plain. 10 MiB·100페이지·100,000 코드포인트 제한. 클라이언트 파일 검사는 페이지/내용 검증을 대체하지 않는다. |
| 분석 응답 | 성공 시 `{draft,attempt}`. Profile은 data/sources/evidence/unknownFields/version, Attempt는 id/status/errorCode/시간·문서 요약 포함. 실패해도 이전 유효 Profile을 삭제하지 않는다. |
| 상태·Profile 조회 | GET `/api/projects/{projectId}`: project/confirmed/draft/latestAttempt 키는 항상 존재하며 미존재 값은 null. 별도 상태 조회 엔드포인트를 만들지 않는다. |
| Profile 확인 저장 | PATCH `/api/projects/{projectId}/profile`: expectedVersion + data 10개 필드 전체. draftId/draftVersion은 함께 전송하거나 함께 생략. 응답 `{project,confirmed}`를 기준으로 상태 갱신. sources/evidence 등 서버 계산 필드는 제출하지 않는다. |

## 상태 및 표시 정책

- UI 상태는 unavailable / queued / processing / completed / failed로 구분한다.
- 계약의 PROCESSING / SUCCEEDED / FAILED를 각각 진행 / 완료 / 실패에 대응시킨다.
- 현재 계약은 별도 작업 큐나 취소 endpoint를 정의하지 않는다. queued는 UI 표현만 준비했으며 실제 대기 신호·조회 주기·재시도·취소 UX는 팀과 확인한다.
- `getAnalysisPresentation(status, mode)`와 AnalysisScreen에 명시적으로 상태를 전달한다.
  현재 `/demo` 라우트는 항상 demo + unavailable이며 타이머·요청·가짜 결과가 없다.
- `getProfilePresentation(mode)`는 서비스와 시연 문구를 분리한다.
  실제 서비스 연결 시 별도 인증/서버 데이터 컨테이너를 연결하고 demoProfiles를 재사용하지 않는다.
  실제 이용 불가 상태에서도 서비스 오류 안내를 표시한다.

## 데이터 및 미확정 사항

- Profile 문자열: null 또는 공백뿐이지 않은 1~200 코드포인트. 프로젝트명은 사용자 확인 시 필수.
  필수 프로젝트명은 빈 초안을 유지하고 확인 시 오류로 처리한다. 선택형 단일 필드(유형·도메인·Database·Deployment)는 삭제·공백 입력을 null로 처리하고 배지를 자동 갱신한다. 목록의 상태 정책은 별개로 유지한다.
- 목록: null(미확인), [](확인된 없음), 값 배열(최대 30개·항목당 200 코드포인트)을 구분한다.
- `demoProfiles[id]`의 source는 demo-manual 또는 demo-fixture. draft와 마지막 유효 profile을 분리한다.
  기존 entry는 재진입 시 초기화하지 않고, 명시적인 예시 교체만 확인 후 허용한다.
- 프로젝트 생성 이름과 Profile.project_name 동기화는 확인한 계약에 명시적으로 규정되어 있지 않다.
  PATCH 응답에 project가 포함되지만 이름 갱신 여부는 별도 합의가 필요하다. 현 시연은 자동 동기화하지 않는다.
- 기존 생성 UI는 이름 200 코드포인트지만 공유 API 문서의 Project.name은 100이다.
  이번 범위에서는 변경하지 않았으며 실제 프로젝트 생성 연동 전 팀 확인이 필요하다.
- 실제 OAuth 공급자 연결·세션 만료, 소유권/Origin 검증, 버전 충돌(409), 분석 오류/중단 복구,
  네트워크 재시도 및 중복 시작 방지는 미구현이다. 서비스 모드 라우트도 아직 제공하지 않는다.

## 커밋 전 모드 검증

- 서비스/시연 Analysis·Profile 컴포넌트를 정적 렌더링하여 시연 버튼·문구 노출을 검사한다.
- 서비스 표시 모드에 demo-manual/demo-fixture를 주입하면 편집 화면 대신 데이터 오류 안내를 표시한다.
- 현재 운영 가능한 서비스 라우트는 없다. 정적 서비스 렌더링은 인증·API 연결 검증이 아니다.
- 상태 문구 5종, 진행 중 원본 제목, 신규 Profile의 예시 미주입, 명시적 예시 교체,
  Profile 확인과 분석 완료의 독립성은 자동 테스트로 확인한다.
- 실제 세션 만료·소유권·AI 응답/오류·서버 버전 충돌 및 상태 전환은 백엔드 연동 후 검증한다.

## Role 05/10 (시연 구현)

- HTML·DOCX 기준: 역할(프론트엔드/백엔드/풀스택/AI/디자이너) 단일 선택,
  담당 업무 여러 줄(최대 30개·각 200 코드포인트), 경험 수준(초보/중급/숙련) 단일 선택.
  모두 선택 입력이며 미입력도 유효하다. 공백 줄은 업무 항목에서 제외하고 원문 초안은 보존한다.
- HTML에는 textarea 전체 6,000 코드포인트 제한도 있다. 30개×200자에 줄바꿈이 더해지면
  6,000을 넘으므로 본 구현은 DOCX의 항목별 제한을 검증하고 전체를 자동 절단하지 않는다.
  실제 계약에서 전체 제한 및 줄바꿈 계산 기준을 확정해야 한다.
- `/demo/projects/:demoId/role`은 확인 완료·유효한 시연 Profile만 허용한다.
  이는 AI 분석 성공을 의미하지 않는다. Review 수정으로 확인 상태가 해제되면 재확인이 필요하다.
- `roleDrafts[id]`의 source는 demo-user. draft(역할/원문 업무/경험)와 마지막 유효 value를
  분리하고, API 모델로 간주하지 않는다. 신규 Role fixture는 없으며 현재 세션에서만 보존한다.
- 실제 서비스 모드 Role UI는 인증/역할 API 미연동 안내로 차단한다.
  OAuth 성공만으로 접근이나 분석 상태를 바꾸지 않는다.
- 공유 OpenAPI phase1에는 Role 경로/스키마가 없다. 저장·조회 방법, 실제 enum 코드,
  미선택 값의 null/문자열/빈 배열 표현, 버전·소유권·Profile 연결 정책은 팀 합의가 필요하다.
- 06단계는 미구현이다. 다음 버튼은 검증 후 시연 메모리 값만 확인하고 현재 화면에 안내한다.

## Environment 06/10 (시연 UI 모델)

- 모든 입력은 선택이다. OS 종류는 미정/Windows/macOS/Linux/기타, OS 버전은 자유 텍스트다.
  ‘기타’ 전용 추가 입력은 원본에 없으므로 만들지 않았다. OS·버전도 추정하거나 PC에서 검사하지 않는다.
- 현재 설정 상태는 UNKNOWN/USER_SAYS_EMPTY/HAS_EXISTING. 실행 환경과 설치 구성요소는
  UNKNOWN(null)과 KNOWN(빈 배열 또는 이름·버전 행)을 구분한다. 정보 상태 전환 시 행 초안은 보존하고,
  확인 value에는 UNKNOWN이면 null, KNOWN이면 [] 또는 실제 행을 넣는다.
- AI Client는 시각 시안의 읽기 전용 Codex 표시다. 설치 여부나 사용자 환경 값으로 자동 저장하지 않는다.
  PRD는 초기 Claude Code 중심이므로 실제 지원 Client/버전은 팀 합의가 필요하다. 다른 Client 선택지는 추가하지 않았다.
- 외부 서비스 준비 상태는 서비스명 + UNKNOWN/READY/NOT_READY로 입력한다.
  실제 로그인 상태나 토큰이 아니며 사용자 선언이다. Secret·인증 토큰 입력을 요구하지 않는다.
- 시안과 DOCX의 30행·200 코드포인트를 적용했다. 전부 미입력은 유효하다.
  사용자가 추가한 빈 행은 이름을 채우거나 삭제하도록 안내한다. 버전은 선택이다.
  서비스명에도 200 코드포인트·30행 보호를 적용했으며 이는 UI 임시 검증 정책이다.
  행별 필수 조건·서비스명 제한은 원본의 상세 검증이 없으므로 API 계약에서 확정해야 한다.
- `environmentDrafts[id]`는 source: demo-user, 편집 draft와 마지막 유효 value를 분리한다.
  Role의 유효 확인 후 진입하며 재진입·목록 이동 중 보존하고 새로고침·시연 종료 시 초기화한다.
- 공유 phase1 OpenAPI에 Environment 경로/스키마가 없다. 저장/조회 endpoint, enum 값,
  USER_DECLARED 출처 표현, null/빈 배열, 버전·권한·부분 수정·Profile/Role 연결 정책은 미확정이다.
  현 UI 모델을 서버 계약으로 확정하지 않는다. 실제 서비스 모드는 미연동 안내로 차단한다.
- 다음 버튼은 유효 입력을 시연 메모리에서 확인하고 07단계 미구현을 안내한다.
  서버 저장·환경 검사·추천 생성은 실행하지 않는다.


## 02~06단계 모드 점검 범위

Planning은 `/demo` 전용 컨테이너이며 서비스용 Planning 화면은 아직 없다.
Analysis의 시연 진입·안내와 Review의 예시 불러오기는 demo 표시에서만 제공한다.
서비스 표시 모드에서 Review는 demo-manual/demo-fixture 입력을 거부하고,
Role·Environment는 실제 서비스 기능 자체를 미연동 안내로 차단한다.
서비스/시연 문구·데이터 보호는 정적 렌더링 및 모델 테스트이며 실제 인증·API E2E 검증은 아니다.
실제 서비스 라우트, OAuth 세션/소유권, API 응답·저장·상태 전환은 연동 후 구현·검증해야 한다.
