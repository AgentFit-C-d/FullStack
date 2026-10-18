# 백엔드 인계: 서비스 진입 기반과 API 연결 경계

> FullStack 로컬 통합 상태 (2026-10-18): Frontend `main`의 `0c39dad`를
> `apps/web`에 반영했다. `/login`은 실제 GitHub OAuth 시작 경로,
> `/app`은 세션 및 PostgreSQL 프로젝트 목록 GET,
> `/app/projects/new`는 CSRF를 거친 프로젝트 POST에 연결된다.
> 생성·목록·상세에서 `/app/live?project=<id>`로 이동하면
> A 분석·확정과 B 역할/환경·추천·미리보기·승인·ZIP의 기존 실서버 화면을 사용할 수 있다.
> `/demo`는 여전히 메모리 시연이며, `/app/projects/:projectId/analysis` 등
> 최신 단계별 서비스 화면은 아직 실데이터 폼으로 연결하지 않았다.
> 아래 내용의 "미연결" 표기는 Frontend 원본 인계 시점의 기록이다.

이 절이 현재 상태의 우선 기준이다. 아래 단계별 기록은 디자인·시연 UI·계약 확인 내용을 보존한다.

## 1. 현재 구현과 미구현

| 구분 | 현재 상태 |
| --- | --- |
| 기존 12개 화면 | `/`와 `/demo/...`에서 시연 UI·프로젝트별 메모리 초안·입력 검증·왕복 동작 구현 |
| 서비스 진입 | `/login`, `/app`, `/app/projects/...` 경로와 인증 선행 검사·공통 상태 화면 구현 |
| 서비스 로그인 | 기존 로그인/온보딩 디자인 재사용. GitHub 버튼 비활성 및 정식 이용 불가 안내. OAuth 시작/콜백 미연결 |
| 실제 서버 통신 | 기본 transport 없음. 세션·조회는 정직한 unavailable 반환. fetch·가짜 세션·fixture 응답 없음 |
| 서비스 데이터 화면 | 공통 Brand/PageHeader/Card/Button 재사용. 기존 폼은 demo 컨테이너에 종속되어 서비스 API 바인딩 미완료 |
| 서버가 응답하는 경우 | 읽기 인터페이스에서 DTO를 받을 수 있지만 ready 응답만으로 서비스 폼·추천·승인·다운로드를 활성화하지 않음 |
| 쓰기·분석 시작·승인·다운로드 | execute는 항상 unavailable. 실제 요청·완료·산출물·서버 저장 기능 미구현 |

오늘의 최소 범위는 진입·모드 경계·연결 인터페이스다. **서비스 12개 화면의 실제 데이터 CRUD가 완료된 상태가 아니다.**
폼 전체를 바꾸는 대규모 리팩터링은 하지 않았다. 확정 DTO를 연결하는 단계에서 기존 Screen/Editor와 입력 검증을 순차 재사용한다.
기존 Editor의 service 방어 분기는 직접 마운트하지 않았다. 해당 폼을 서비스에 연결할 때 내부 연동 설명을 공통 정식 오류 표시로 교체해야 한다.

## 2. 경로와 demo/service 격리

- 경로 정의: `src/app/routes.jsx`의 `createAppRoutes`, 서비스 단계 표: `src/features/service/serviceStages.js`.
- 기존 `/`와 `/demo/...`는 동일한 pathless DemoRoutes/DemoProvider 경계 안에 둬 기존 시연 이동을 유지한다.
  `/login`과 `/app/...`는 이 Provider 밖이다. 서비스에서 demoReducer나 demoProfiles를 인증/데이터 원천으로 사용하지 않는다.
  모드는 라우트 handle/고정 props로 결정한다. URL query·localStorage·DEV 플래그로 서비스를 demo로 바꾸지 않는다.
- `/login`: 서비스용 로그인 표시. 기존 `/`는 시연 진입이 가능한 기존 로그인 화면으로 보존한다.
- `/app`와 `/app/projects`: 서비스 프로젝트 목록 기반. `/app/projects/new`: 생성 기반.
- `/app/projects/:projectId`: Planning. 같은 prefix에 `/analysis`, `/review`, `/role`, `/environment`,
  `/recommendation`, `/selection`, `/preview`, `/complete`를 둔다.
- **위 경로는 임시 프론트 경로**이며 API URL이나 확정 OAuth callback 경로가 아니다.
  서비스 경로/로그인 landing 합의 후 정의 파일에서 교체한다. 배포 서버는 SPA fallback을 제공하고 `/api`는 제외해야 한다.
- Demo Provider를 떠나 서비스 모드로 전환하면 시연 Provider는 unmount된다. 서비스에 시연 세션을 이관하지 않는다.
  기존 demo 내부 이동은 값을 유지하고 새로고침·종료 시 초기화되는 정책도 유지한다.
- 각 서비스 leaf loader는 먼저 세션을 확인한다. null·만료·잘못된 공개 세션·demo 출처이면 하위 데이터를 읽지 않는다.
  알려지지 않은 `/app/*` 및 URL 직접 입력도 같은 검사를 거친다.
- 프론트 검사는 **UX용**이다. 사용자는 프론트 코드를 바꿀 수 있으므로 최종 인증·소유권·권한·승인·다운로드 검증은 매 서버 요청에서 수행해야 한다.

## 3. 실제 API 연결 위치

| 파일/인터페이스 | 연결할 작업 |
| --- | --- |
| `src/features/service/serviceApi.js` | 확정 GET 계약 registry, 기본 unavailable, 선택적 읽기 transport 인터페이스 |
| `createServiceApi({ transport })` | transport가 `{method,path,signal}`을 받아 실제 HTTP 응답의 `{status,data}`를 반환하도록 연결. 현재 transport 없음 |
| `api.read('session' / 'projects' / 'project', input, {signal})` | 공개 세션, 프로젝트 목록·상세만 읽기 가능. 다른 계약 미확정 기능은 요청하지 않고 unavailable |
| `api.execute(operation, ...)` | 연결 예정 자리만 존재. 인수와 무관하게 unavailable이며 실제 POST/PATCH/OAuth/다운로드 수행 안 함 |
| `src/features/service/serviceModel.js` | 공개 세션 정확한 필드·만료·출처 검사, 인증 선행 loader, 리소스 401에 로그인 제한 적용 |
| `src/pages/ServicePage.jsx` | 서비스용 loading/empty/unavailable/error/not-found/forbidden 표시. 원시 서버 오류는 노출하지 않음 |
| `src/app/routes.jsx` | 실제 API 객체를 서비스 loader에 주입. 현재 기본 serviceApi는 미연결 객체 |

읽기 transport도 현재는 인터페이스만 준비돼 있다. 테스트 전용 응답은 테스트 파일 안에만 존재한다.
서비스 코드에 fixture fallback·타이머 완료·브라우저 저장 세션은 없다. 요청 중단 signal은 전달하고,
React Router loader 데이터는 현재 projectId와 맞을 때만 표시하여 이전 프로젝트 결과를 재사용하지 않는다.
Project 상세 응답의 project.id가 요청한 불투명 ID와 다르면 오류 처리하며 ID prefix에서 소유권을 추론하지 않는다.
기본 검사 범위는 공개 세션/프로젝트의 필수 형태·이름/버전/날짜·상세 필수 키·demo 출처다.
Profile/Attempt 중첩 DTO 전체 스키마 검사와 실제 폼 바인딩은 연동 단계에 추가해야 한다.

## 4. 확정 계약과 미확정 계약

기준은 팀 공유 `Docs/api/openapi.phase1.json`, `Docs/api/01-project-analysis.md`다.
registry는 기존 경로만 기록하며 **새 endpoint를 만들지 않는다.**

| 기능 | 확정 계약 | 프론트 연결/남은 일 |
| --- | --- | --- |
| GitHub OAuth·세션 | GET `/api/auth/get-session`, PublicSession 또는 null | 세션 읽기 경계 준비. OAuth 시작/콜백/로그아웃 URL은 임의 생성 안 함. 팀 인증 구성 연결 필요 |
| 프로젝트 목록·생성 | GET/POST `/api/projects`, ProjectListResponse / CreateProjectRequest / CreateProjectResponse | 목록 GET 인터페이스만 준비. 생성 폼·POST 연결 미완료 |
| 프로젝트 이름 수정 | 독립 수정 endpoint 미정. Profile PATCH 응답에 project 포함 | Project.name과 ProfileData.project_name 동시 갱신·버전 계약 합의 필요 |
| Planning·분석 시작 | POST `/api/projects/{projectId}/analysis`, raw PDF/Markdown/text | 기존 파일/텍스트 검증 재사용 예정. JSON·multipart·base64로 바꾸지 않음. 실제 전송 미구현 |
| 분석 상태·결과·Profile 조회 | GET `/api/projects/{projectId}`의 project/confirmed/draft/latestAttempt | 상세 읽기 경계만 준비. confirmed/draft/latestAttempt의 null 유지. 인증 성공으로 분석 상태를 생성하지 않음 |
| Profile 확인 수정 | PATCH `/api/projects/{projectId}/profile`, SaveProfileRequest → SaveProfileResponse | expectedVersion + data 10개 전체, draftId/draftVersion 쌍, 서버 sources/evidence 미전송. 쓰기 미연결 |
| Role·Environment 저장 | phase1 미정 | UI 내부 모델과 출처/null/[] 의미만 존재. 조회/저장 endpoint·DTO·enum·버전·Client ID 합의 필요 |
| Capability 추천 | phase1 미정 | 필요성·추천 상태·근거·입력 snapshot·오류 계약 필요. 서버 점수/규칙 미발명 |
| Catalog·Selection·권한 | phase1 미정 | Catalog ID/버전·Client 호환성·manifest·권한/필수 정책·선택 저장 계약 필요 |
| Preview·최종 승인 | phase1 미정 | 파일/범위/기존 설정·Diff·충돌·fingerprint·버전·승인 무효화/소유권 계약 필요 |
| 산출물 다운로드 | phase1 미정 | 산출물 ID/형식·생성 상태·승인 일치·인증/만료·실패/재시도 계약 필요 |
| 적용 결과 보고 | phase1 미정 | status/reason enum·기준 구성·시점·조회/수정·USER_REPORTED/검증 출처 계약 필요 |

프로젝트명은 trim 후 최대 **100 코드포인트**, Role은 최대 30개·각 200·줄바꿈/구분자 포함 원문 전체 **6,000 코드포인트**다.
기존 demo 검증·Profile 확인 시 목록 이름 동기화·마지막 유효 데이터 보호는 유지한다.
현재 API ProfileData.project_name의 200 제한과 Project.name의 100 제한 차이는 서버 계약 정렬이 필요하다.
Claude Code 우선·Codex 후속 정책을 유지한다. 설정·권한·생성 파일·병합 방식을 Client 간 자동 매핑하지 않는다.

### 백엔드 인계용 구현 담당 구분

현재 프론트엔드는 12개 시연 화면과 서비스 진입·API 읽기 인터페이스를 구현한 상태다. 실제 서비스 동작을 위해서는 프론트엔드 추가 구현과 백엔드 계약·구현이 모두 필요하다.

| 영역 | 프론트엔드 추가 작업 | 백엔드·팀 확인 및 구현 |
| --- | --- | --- |
| OAuth·세션 | 로그인 버튼, 실제 transport, 인증 상태 UI 연결 | OAuth 시작/콜백/로그아웃, 세션 쿠키·만료·인증 정책 |
| 프로젝트 | 목록·생성·상세 서비스 화면에 DTO 바인딩, 쓰기·오류 처리 | 기존 GET/POST 계약 제공, 소유권 및 버전 검증 |
| Planning·Analysis | 파일/텍스트 전송, 분석 상태·결과 표시 연결 | 분석 요청 처리, 상태·실패 응답 및 보존 정책 |
| Profile | GET/PATCH 바인딩, 버전 충돌·입력 오류 처리 | 프로젝트명 동기화 원자성, 버전·409 정책 |
| Role·Environment | 기존 입력 UI를 서버 데이터 조회·저장 흐름에 연결 | endpoint·DTO·enum·Client ID·저장 정책 확정 |
| Capability·Selection | 추천 결과·Catalog·권한 선택 UI를 실제 데이터에 연결 | 추천·Catalog·호환성·권한 검증 및 저장 계약 |
| Preview·승인 | 실제 파일/Diff 표시, 승인 상태·무효화 처리 연결 | 설정 생성·충돌 검증·버전/fingerprint·승인 검증 |
| Complete·다운로드 | 실제 다운로드·보고 API 연결, 결과 상태·오류 처리 | 산출물 생성·접근 권한·만료·보고 저장 및 검증 출처 |

#### 연동 전에 확인할 주요 정책

- **프로젝트명:** 현재 `Project.name`은 최대 100 코드포인트, `ProfileData.project_name`은 최대 200 코드포인트다. 사용자에게는 하나의 프로젝트명으로 보여야 하므로 서버 제약 정렬과 Profile PATCH 시 원자적 동기화·버전 충돌 처리를 합의해야 한다.
- **Role 담당 업무:** 프론트는 최대 30개, 항목당 200 코드포인트, 줄바꿈·구분자를 포함한 원문 전체 6,000 코드포인트로 검증한다. 서버의 저장 형식, 줄바꿈 정규화 및 검증 기준은 미확정이다.
- **적용 결과 보고:** 현재 시연 UI에서는 상태 선택을 필수로 요구하고, FAILED 선택 시 실패 사유도 필수다. 이는 현재 프론트 입력 정책이며 실제 API의 필수 조건·enum은 별도로 확정해야 한다.
- **Claude Code:** 우선 지원 대상이지만 실제 지원 OS·버전, 설정 파일·범위, 권한 및 산출물 정책은 아직 확정되지 않았다.
- **보안:** 클라이언트의 입력 검증과 라우트 차단은 서버 검증을 대신하지 않는다. 모든 실제 요청의 인증·소유권·권한 확인은 서버에서 수행해야 한다.

위 항목은 추가 합의가 필요한 사항이며, 현재 미확정된 endpoint나 DTO가 구현되어 있다는 의미가 아니다.

## 5. OAuth·실제 서비스 전환 작업 순서

1. 팀의 기존 인증 공급자 구성을 확인해 서비스 로그인 버튼/서버 callback/로그아웃/landing 경로를 연결한다.
   프론트가 OAuth 세션·토큰을 만들지 않는다. 공개 세션 외 Token/Account/원본 Session을 받지 않는다.
2. 실제 same-origin 또는 합의한 base URL transport를 연결한다. cookie/credentials, CORS/Origin, CSRF,
   세션 만료, no-store·민감 오류/로그 정책을 서버와 맞춘다. 현재 Vite proxy나 환경변수는 추가하지 않았다.
3. 서비스 목록/생성/Planning/Analysis/Profile부터 확정 DTO에 연결한다. 쓰기 요청 검증과 실패·409·중복 요청·취소 복구를 구현한다.
   Profile 확인 응답의 project/confirmed를 함께 반영하고 실패 시 이전 유효 데이터를 보호한다.
4. Role~Complete 계약 합의 후 해당 단계의 서버 상태·폼·승인·다운로드를 연결한다.
   default ready DTO만으로 서비스 액션을 자동 활성화하지 않는 현재 placeholder를 이때 교체한다.
5. 계정 전환·세션 만료 시 서비스 데이터와 진행 권한을 무효화한다. demo state를 복사하거나 서비스 ID로 변환하지 않는다.

## 6. 검증 범위와 연동 후 필수 테스트

현재 자동 테스트: 메모리 라우터로 모든 서비스 URL·query 우회·unknown 경로 차단, null/만료/demo 세션 거부,
서비스 로그인에서 시연 버튼·내부 설명 미노출, 원시 오류 미노출, loading/empty/error/unavailable 구분,
네트워크 실패·중단·401 처리, 프로젝트 ID 일치, 미확정 기능 요청 없음, 실제 응답 없는 완료/다운로드 없음.
기존 프로젝트별 입력 격리·100/6,000 제한·Profile 이름 동기화·Preview 확인 무효화 테스트도 유지한다.
**브라우저 자동화 및 실제 API/OAuth E2E는 이번에 수행하지 않았다.**

실제 연동 후에는 OAuth 성공/거절/만료/로그아웃, 타 계정 프로젝트 403/404, 직접 URL·새로고침,
계정/프로젝트 전환 중 늦은 응답, 409 및 마지막 유효 데이터 보호, 분석 요청 실패/응답 유실·상태 조회,
승인 뒤 구성 변경·만료·취소·다운로드 권한, 실제 산출물 일치와 사용자 보고/검증 구분을 검증해야 한다.

---

## 기존 단계별 구현·계약 기록

이하 내용은 단계별 구현 당시의 설계 결정, 시연 UI, 검증 및 API 계약 검토 기록이다.
**현재 구현 상태는 문서 맨 앞의 '백엔드 인계: 서비스 진입 기반과 API 연결 경계'를 우선 기준으로 한다.** 아래 기록 중 '미구현', 'Codex 표시', '후속 작업' 등의 표현은 작성 당시 상태를 설명할 수 있으며, 최신 상태와 충돌하면 앞부분의 인계 요약 및 이후 구현 기록을 기준으로 판단한다.
기준 자료:
- `AgentFit-team-handoff-2026-09-08-v2/Docs/api/openapi.phase1.json`
- `Docs/api/01-project-analysis.md`
아래 API 주소는 기존 계약을 요약한 것으로, 실제 서버 호출이 구현되었다는 의미가 아니다.

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

- Profile 문자열: null 또는 공백뿐이지 않은 1~200 코드포인트. 단, 사용자 확인 시 필수인 프로젝트명은 팀 확정 정책에 따라 trim 후 1~100 코드포인트로 제한한다.
  필수 프로젝트명은 빈 초안을 유지하고 확인 시 오류로 처리한다. 선택형 단일 필드(유형·도메인·Database·Deployment)는 삭제·공백 입력을 null로 처리하고 배지를 자동 갱신한다. 목록의 상태 정책은 별개로 유지한다.
- 목록: null(미확인), [](확인된 없음), 값 배열(최대 30개·항목당 200 코드포인트)을 구분한다.
- `demoProfiles[id]`의 source는 demo-manual 또는 demo-fixture. draft와 마지막 유효 profile을 분리한다.
  기존 entry는 재진입 시 초기화하지 않고, 명시적인 예시 교체만 확인 후 허용한다.
- 팀 확정 정책: 프로젝트명은 사용자에게 하나다. 생성과 Review는 100 코드포인트로 통일한다.
  Review 전체 검증을 통과해 확인할 때만 Profile.project_name과 목록 Project.name을 함께 갱신한다.
  초안 편집·오류·예시 불러오기만으로는 목록 이름을 바꾸지 않는다. 서버 계약의 중복 필드는 삭제하지 않는다.
- API 문서의 Project.name은 trim 후 1~100 code points이며 ProfileData.project_name은 현재 null 또는 최대 200 code points다.
  UI는 더 엄격한 100을 적용한다. PATCH 응답의 project/confirmed를 함께 갱신하는 서버 원자성·버전 정책은 확인이 필요하다.
- 실제 OAuth 공급자 연결·세션 만료, 소유권/Origin 검증, 버전 충돌(409), 분석 오류/중단 복구,
  네트워크 재시도 및 중복 시작 방지는 미구현이다. 서비스 라우트 기반은 문서 앞부분의 최신 인계 내용에 따라 제공하되 실제 인증·데이터 폼 연결은 미구현이다.

## 커밋 전 모드 검증

- 서비스/시연 Analysis·Profile 컴포넌트를 정적 렌더링하여 시연 버튼·문구 노출을 검사한다.
- 서비스 표시 모드에 demo-manual/demo-fixture를 주입하면 편집 화면 대신 데이터 오류 안내를 표시한다.
- 서비스 라우트는 인증 차단·상태 표시 기반만 제공한다. 운영 가능한 서비스 폼은 아직 없으며 정적 렌더링은 인증·API E2E 검증이 아니다.
- 상태 문구 5종, 진행 중 원본 제목, 신규 Profile의 예시 미주입, 명시적 예시 교체,
  Profile 확인과 분석 완료의 독립성은 자동 테스트로 확인한다.
- 실제 세션 만료·소유권·AI 응답/오류·서버 버전 충돌 및 상태 전환은 백엔드 연동 후 검증한다.

## Role 05/10 (시연 구현)

- HTML·DOCX 기준: 역할(프론트엔드/백엔드/풀스택/AI/디자이너) 단일 선택,
  담당 업무 여러 줄(최대 30개·각 200 코드포인트), 경험 수준(초보/중급/숙련) 단일 선택.
  모두 선택 입력이며 미입력도 유효하다. 공백 줄은 업무 항목에서 제외하고 원문 초안은 보존한다.
- 팀 확정 정책으로 textarea 원문 전체를 줄바꿈·공백·구분자 포함 최대 6,000 코드포인트로 검증한다.
  항목별 200·최대 30개 조건도 함께 유지한다. 원문을 trim하거나 자동 절단하지 않으며 오류 초안과 마지막 유효 value를 분리한다.
  UI에서는 한 줄에 한 업무를 입력한다. LF는 1, 원문에 CRLF가 있으면 2 코드포인트로 계산한다.
  API에 배열만 보낼지, 원문도 보낼지, 줄바꿈 정규화·배열 join 구분자·길이 산정 시점은 계약 확인이 필요하다.
- `/demo/projects/:demoId/role`은 확인 완료·유효한 시연 Profile만 허용한다.
  이는 AI 분석 성공을 의미하지 않는다. Review 수정으로 확인 상태가 해제되면 재확인이 필요하다.
- `roleDrafts[id]`의 source는 demo-user. draft(역할/원문 업무/경험)와 마지막 유효 value를
  분리하고, API 모델로 간주하지 않는다. 신규 Role fixture는 없으며 현재 세션에서만 보존한다.
- 실제 서비스 모드 Role UI는 인증/역할 API 미연동 안내로 차단한다.
  OAuth 성공만으로 접근이나 분석 상태를 바꾸지 않는다.
- 공유 OpenAPI phase1에는 Role 경로/스키마가 없다. 저장·조회 방법, 실제 enum 코드,
  미선택 값의 null/문자열/빈 배열 표현, 버전·소유권·Profile 연결 정책은 팀 합의가 필요하다.
- 다음 버튼은 유효 입력을 시연 메모리에 확인한 후 06단계로 이동한다.

## Environment 06/10 (시연 UI 모델)

- 모든 입력은 선택이다. OS 종류는 미정/Windows/macOS/Linux/기타, OS 버전은 자유 텍스트다.
  ‘기타’ 전용 추가 입력은 원본에 없으므로 만들지 않았다. OS·버전도 추정하거나 PC에서 검사하지 않는다.
- 현재 설정 상태는 UNKNOWN/USER_SAYS_EMPTY/HAS_EXISTING. 실행 환경과 설치 구성요소는
  UNKNOWN(null)과 KNOWN(빈 배열 또는 이름·버전 행)을 구분한다. 정보 상태 전환 시 행 초안은 보존하고,
  확인 value에는 UNKNOWN이면 null, KNOWN이면 [] 또는 실제 행을 넣는다.
- AI Client는 확정 MVP인 Claude Code의 읽기 전용 표시다. 설치 여부를 탐지하거나 인증을 확인하지 않는다.
  Codex는 후속 지원이며 선택지를 추가하지 않았다. 아래 구현 기록과 전환 영향 분석을 참고한다.
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
- 다음 버튼은 유효 입력을 시연 메모리에서 확인하고 07단계 시연 화면으로 이동한다.
  서버 저장·환경 검사·추천 생성은 실행하지 않는다.


## 02~06단계 모드 점검 범위

Planning은 `/demo` 전용 컨테이너이며 서비스용 Planning 화면은 아직 없다.
Analysis의 시연 진입·안내와 Review의 예시 불러오기는 demo 표시에서만 제공한다.
서비스 표시 모드에서 Review는 demo-manual/demo-fixture 입력을 거부하고,
Role·Environment는 실제 서비스 기능 자체를 미연동 안내로 차단한다.
서비스/시연 문구·데이터 보호는 정적 렌더링 및 모델 테스트이며 실제 인증·API E2E 검증은 아니다.
서비스 라우트의 OAuth 세션/소유권, 실제 API 응답·저장·상태 전환은 연동 후 구현·검증해야 한다.

## Capability 추천 07/10 (미실행 + 명시적 시연 미리보기)

- HTML·DOCX의 Capability 9개와 필요성 REQUIRED/OPTIONAL/UNDETERMINED,
  정상 추천 표현 4종(RECOMMENDED/NO_ADDITIONS_NEEDED/NEEDS_INFORMATION/NO_COMPATIBLE_TOOLS)을 재현한다.
  PRD는 역할명 하나로 필수 능력을 확정하지 않고 네 결과와 검증된 이유·호환 조건을 구분하도록 요구한다.
- 기본 실제 결과 뷰 모델은 NOT_RUN, capabilities:null, tools:null이다. 이는 서버 DTO가 아니다.
  추천 미실행, 응답 데이터 null, 확인된 빈 목록 [], 실제 값 배열을 동일하게 취급하지 않는다.
  오류/이용 불가도 정상 추천 결과로 대체하지 않는다.
- `recommendationPreviews[id]`의 source는 demo-ui이며 별도 미리보기 조작값만 보관한다.
  사용자가 명시적으로 열기 전에는 상태 버튼이 숨겨지고 필요성 select는 비활성이다.
  고정 예시는 디자이너 항목·상태 문구뿐이며 추천 도구 fixture·점수·규칙·자동 분석은 없다.
  미리보기 선택을 실제 결과 저장소에 쓰지 않는다. 닫기·재진입은 시연 선택을 보존한다.
- 유효 확인된 Profile·Role·Environment만 demo 화면 이동을 허용한다.
  수정으로 Environment 확인이 해제되면 재확인이 필요하다. 이전 단계/목록 이동 중 입력은 유지한다.
  실제 결과 기반 08단계 진입은 제공하지 않고, 별도 시연 버튼으로만 Selection을 확인한다.
- service 표시는 인증·추천 API 미연동 오류로 차단하고 시안 항목·예시를 표시하지 않는다.
  서비스 경로는 준비됐지만 실제 API·데이터 폼 연결은 없다. OAuth 성공은 추천 실행/분석 완료의 근거가 아니다.
- 현재 프론트 표현 가능: 9개 Capability 표시, 필요성 시안 선택, 네 결과의 문구 미리보기,
  미실행/결과 없음/실패/이용 불가 안내. 실제 도구 목록은 어떤 상태에서도 생성하지 않는다.
- 공유 phase1 OpenAPI에는 추천/Capability 경로·스키마가 없다. 백엔드 합의 필요:
  시작·조회 방식, 결과 버전/입력 snapshot 식별자, 상태·오류 코드, Capability 분류와 출처,
  필요성·근거·불확실성, Catalog 도구 ID/버전·호환 조건·권한·위험도, 결과 null/[] 의미,
  재분석 시 이전 결과 무효화 및 동시 수정 정책.
- 팀 확정 MVP는 Claude Code 우선, Codex 후속이다. 06단계 Environment의 Client 표시 및 입력 안내는 Claude Code 기준으로 전환 완료했다.
  다만 07단계 Capability 추천은 아직 특정 Client의 실제 호환성을 검증하거나 서버 추천 결과를 조회하지 않는다.
  추천·호환성 판정은 확정된 Client/Catalog 계약과 검증된 서버 응답을 연결한 뒤 제공해야 한다.

## Selection 08/10 (명시적 시연 진입)

- 07단계 정상 결과 CTA는 실제 결과 없이 진행하지 않는다. 별도 ‘시연용 도구·권한 화면 미리보기’만
  유효한 시연 입력과 함께 진입한다. 이 이동은 추천 성공이나 권한 승인이 아니다.
- 기본 실제 데이터는 tools:null, permissions:null(미조회)이다. 조회 결과 [] 및 값 배열과 구분한다.
  시연 상태는 `selectionPreviews[id]`, source: demo-ui에만 저장하고 실제 결과 저장소와 합치지 않는다.
- 원본에는 실제 도구/고정 Catalog 목록이 없었다. 선택 동작 QA를 위해 ‘필수 카드 UI 예시’와
  ‘선택 카드 UI 예시’라는 추상 카드를 별도 demo fixture로 둔다. 명시적으로 예시를 열어야 나타나며
  실제 도구명·지원 버전·추천 이유·호환성·필수 권한을 주장하지 않는다.
- 원본의 필수 카드 고정/선택 카드 토글을 재현한다. 예시의 필수 표시는 UI 동작만 의미한다.
  권한 조작값은 ALWAYS_ALLOW/ASK_EACH_TIME/DENY, 기본 ASK_EACH_TIME이지만 실제 정책은 미정이다.
  예시가 닫혀 있으면 실제 정책 미조회 표시와 함께 조작을 비활성화한다.
- 카드 선택, 권한 조작값, 권한 설명 펼침을 분리한다. 선택 카드 해제로 권한 값을 바꾸지 않는다.
  필수 예시 카드 + DENY 경고는 가상 조합 검증일 뿐 실제 Capability/manifest 판정이 아니다.
- 실제 서비스 모드는 인증·검증된 추천/Catalog 미연동 안내로 차단하고 시연 데이터를 표시하지 않는다.
  Secret 입력, 설치, 토큰 저장, 실제 승인, 설정 파일 생성은 없다.
- 실제 Preview는 미준비다. 기본 다음 버튼은 시연 조합 검증만 하고, 별도의 시연용 설정 확인·승인 화면 미리보기로만 09단계에 진입한다.
- 백엔드 합의 필요: Catalog 도구 ID/버전·출처, 필수/선택 여부, 추천 snapshot/버전, Client 지원 조합,
  도구별 manifest 권한 범위·정책 기본값·거부 시 처리, 선택 저장/조회 계약·소유권·동시 수정 정책.
  실제 계약 없이 경로·응답이나 호환 규칙을 추가하지 않았다. 원본에도 도구 상세 화면은 정의되어 있지 않아 만들지 않았다.


## 팀 확정 정책 및 Claude Code MVP 전환 영향 분석 (2026-10-18)

이 절은 Claude Code MVP 지원 정책 확정 당시의 영향 분석 및 변경 결정 기록이다.
이후 06단계 Environment의 Claude Code 대응, 09단계 Preview 시연 UI, 10단계 Complete 시연 UI가 구현되었다. 따라서 아래 Client 전환 범위 표는 최초 변경 계획과 후속 연동 필요사항을 함께 설명한다.
**현재 완료된 것은 시연 UI와 서비스 진입 기반이며, 실제 Claude Code 설정 생성·병합·권한 승인·다운로드 및 OAuth/API 연동은 미완료다.**

### 프로젝트명: 중복 필드와 하나의 사용자 개념

- 프론트에 별도 이름 필드가 **있다**: `projects[].name`, `demoProfiles[id].profile.project_name`,
  `draft.project_name`. draft는 편집 버퍼, profile은 마지막 유효 확인값이다.
  API에도 `Project.name`과 `ProfileData.project_name`이 별도로 정의되어 있다.
- 사용자 관점의 이름은 하나다. Review의 유효한 전체 확인을 통해 두 확인값을 동기화한다.
  저장 전 초안·오류는 목록 이름을 덮어쓰지 않으며 다른 프로젝트에는 영향을 주지 않는다.
  예시 Profile 불러오기는 현재 프로젝트명을 유지한다. 예시 프로젝트 카드의 이름 수정도 세션 중 유지한다.
- `01-project-analysis.md`는 Project.name을 trim 후 1~100 **code points**로 명시한다.
  UTF-16 length/바이트/화면상 글자 묶음이 아닌 `Array.from(value.trim()).length`를 사용한다.
  이모지 한 코드포인트는 1, 결합 문자·ZWJ 조합은 구성 코드포인트 수로 계산한다.
  생성 입력의 기존 자동 제한 동작은 100으로만 줄였다. Review 오류 초안은 자르지 않는다.
- 서버 확인 필요: Profile 확인 PATCH에서 Project.name도 같은 트랜잭션으로 갱신하는지,
  project/confirmed version과 409 처리, 분석 추출 이름은 사용자 확인 전 목록에 미반영하는지,
  Profile 이름의 현 200 제한을 100으로 정렬할지. 기존 OpenAPI/서버 원본은 수정하지 않았다.

### Client 전환 범위

| 영역 | 반드시 변경할 부분 / 디자인 수정 대상 | 재사용 가능 | 팀·백엔드 추가 확인 |
| --- | --- | --- | --- |
| 06 Environment | 현재 Codex ‘MVP 지원 대상’ 배지·다른 Client 미지원 설명·Codex 버전 라벨·하단 문구를 Claude Code 기준으로 수정. 현재 설정 상태가 어느 Client/범위인지 표시 | OS·runtime·설치 구성요소·외부 준비 상태, 선택/미확인 UI 및 초안 보호 | 명시적 Client 식별자, 지원 버전·OS·설정 범위, 기존 설정 입력 방식. `clientVersion`만으로 대상 식별 불가; 기존 Codex 버전 입력을 Claude 버전으로 자동 이관 금지 |
| 07 Capability | 실제 Client별 지원·호환 결과/근거를 서버 응답으로 표시. 9개 항목을 Claude 지원 확정 목록으로 간주하지 않음 | Capability 목록/필요성/네 결과 카드, 명시적 demo 미리보기 | Catalog 분류·Client/버전 호환성·근거/불확실성·snapshot 계약. 추천 규칙/점수 미구현 유지 |
| 08 Selection | 검증된 Claude 대상 도구·manifest 권한을 표시하고 승인 범위 문구 재설계. 현재 3개 권한값은 추상 시연 조작값 | 선택 카드·필수/선택 표시·상세 펼침·미조회/null/[]·프로젝트별 상태 | 실제 Catalog ID/버전, 필수 판정, 도구별 권한 범위, 거부 동작, 저장·조회·동시 수정 계약. ALWAYS_ALLOW/ASK_EACH_TIME/DENY를 Client 설정 키/모드에 일대일 대응 금지 |
| 09 Preview·설정 생성 | 시연용 Preview 및 확인 체크 UI 구현 완료. 실제 Claude Code 설정 생성·Diff·충돌 검증·서버 승인 기능은 미구현 | 선택 요약·권한 요약·파일 상태·확인 UI 구조 | 생성 파일 종류/경로/범위·기존 설정 병합·검증·기준 버전/fingerprint·승인 무효화 계약 |
| 10 Complete·다운로드 | 시연용 다운로드 안내 및 적용 결과 보고 UI 구현 완료. 실제 산출물 생성·다운로드·적용 검증은 미구현 | 다운로드/보고·실패 사유·입력 검증 UI 구조 | 산출물 ID/형식·다운로드 인증/만료·승인 일치·적용 보고/검증 출처 계약 |

### 설정·권한은 Client마다 별도 계약

공식 문서를 확인한 기술적 배경이며 AgentFit의 확정 지원 파일 목록은 아니다:

- [Claude Code 설정](https://code.claude.com/docs/en/settings)은 JSON 설정 파일과 사용자/프로젝트/로컬/관리 범위를 구분한다.
  `.claude/settings.json`과 `.claude/settings.local.json`을 같은 배포 대상으로 보지 않는다.
- [Claude Code 권한](https://code.claude.com/docs/en/permissions)은 allow/ask/deny 규칙·모드·관리 정책을 구분한다.
  UI의 ‘항상 허용’ 선택만으로 모든 실행을 허용하거나 상위 정책을 덮어쓴다고 주장하면 안 된다.
- [Codex 설정](https://developers.openai.com/codex/config-basic/)은 `config.toml` 기반이며 별도 범위·우선순위가 있다.
  JSON↔TOML 치환만으로 승인/샌드박스/manifest 의미가 같아지지 않는다.

Claude Code 우선 정책은 기존 PRD §4의 Claude 중심 정책과 일치한다. Codex 중심의 HTML·DOCX 시안을 참고하되, 06단계 Environment와 10단계 Complete의 표시·안내는 Claude Code 기준으로 수정했다. 07~09단계의 시연 UI는 실제 Client 호환성·설정 생성·승인을 수행하지 않는다.

Codex 후속 지원을 위해 향후 데이터에 Client 식별자·지원 버전·Catalog 호환 대상·권한 모델 버전·
템플릿/산출물 유형을 명시하고, 공통 입력/카드와 Client별 설정 생성·검증·적용 안내를 분리할 필요가 있다.
지금은 어댑터나 신규 API를 구현하지 않는다. 서버·디자인 합의 후 전환하고 이전 시연 값을 자동 변환하지 않는다.

### 최종 Selection UI QA

1280px에서 원본과 카드 너비(442.45px)·안쪽 여백(26px)·좌우 정렬이 같다.
원본에 없는 명시적 예시 진입 버튼은 남겨두므로 카드 시작 위치는 원본보다 60px 아래다.
상단 하늘색 시연 박스와 미조회 pill은 제거된 상태이며 본문과 비활성 select로 미조회를 전달한다.
권한 정보 펼침 간격은 12px, 닫을 때 정보 박스를 제거한다. 390px에서 scrollWidth=390,
하단 버튼은 좌우 18px 안에 있고 높이 49px로 정상 표시된다.
실제 추천·권한 승인이 아닌 시연 설명은 카드/상단 공통 모드 표시/하단 안내로 유지된다.


## Environment Claude Code MVP 대응 구현

- 기준: 최종 HTML/DOCX의 Codex 표시와 PRD의 Claude Code 중심 정책을 대조했다.
  Client 이름·버전 라벨·설정 대상 설명·지원 안내만 정책에 맞게 변경했다.
  06 카드 구조·진행 표시·OS/실행 환경/설치 구성요소/외부 인증 준비 상태·버튼·CSS는 유지한다.
- `clientDefinition.js`에 현재 MVP 식별자와 Client별 표시 문구를 분리했다.
  `CLAUDE_CODE`는 UI 내부 식별자이며 서버 enum을 확정한 것이 아니다.
  `clientVersion`은 사용자가 알고 있는 버전 문자열로 기존 선택 입력·200 코드포인트 검증을 유지한다.
  버전 자동 조회·지원 범위 판정·설치 확인·설정 내용 수집은 없다.
- `configurationKnowledge`의 UNKNOWN/USER_SAYS_EMPTY/HAS_EXISTING는 사용자가 알고 있는
  **Claude Code 설정 존재 여부**다. 특정 JSON 파일·권한 모드·모든 설정 범위의 실효 상태에 매핑하지 않는다.
  [공식 설정 문서](https://code.claude.com/docs/en/settings)는 사용자/프로젝트/로컬/관리 범위와 우선순위를 구분하므로
  ‘설정 있음’만으로 실제 적용·상위 정책·도구 권한을 판정할 수 없다.
  [공식 설치 문서](https://code.claude.com/docs/en/setup)의 설치 지원과 AgentFit 검증 지원 범위도 동일시하지 않는다.
- 신규 시연 entry와 마지막 확인 value에 Client 식별자를 함께 기록한다. 서버 데이터는 여전히 별도이며
  service 모드 입력 UI는 인증·API 미연동 안내로 차단한다. null/[]/목록 의미는 변경하지 않는다.
- 구버전 메모리 entry에는 Client 식별자가 없었다. 기존 Codex 버전/설정 선언을 `legacyClientInput`에
  별도 보존하고 Claude Code 초안은 빈 버전/UNKNOWN으로 시작한다. OS·공통 목록·초안은 그대로다.
  마지막 유효 value는 확인 전 덮어쓰지 않고 confirmed를 해제한다. Claude 값을 재확인해야 추천 시연으로 이동한다.
  이 처리는 구버전 demo 메모리 전용이며 서버 데이터 마이그레이션·영구 저장이 아니다.
  명시적인 다른 Client 식별자는 자동 변환하지 않고 입력 화면을 차단한다.
- Codex 후속 지원은 정의 파일의 표시 문구와 별도 Client 입력/계약을 확장해 연결한다.
  현재 Client 전환 selector·설정 파서·권한 어댑터를 만들지 않는다.

### 남은 계약·디자인 결정

1. Claude Code의 AgentFit 지원 OS/버전, 서버 Client 식별자, 버전 형식 및 저장 계약.
2. 설정 존재 여부의 범위와 출처, 사용자/프로젝트/관리 설정 확인 방식, Secret 정제·충돌 검증.
3. Selection(08)은 여전히 추상 시연이다. 실제 manifest 권한·승인 정책을 Claude 규칙/모드와
   단순 매핑하지 않는다. Catalog/추천 snapshot/호환성/도구별 권한 계약과 디자인 확정 필요.
4. Preview(09)·Complete(10)의 시연 UI는 구현 완료했다. 그러나 실제 Claude Code 설정 생성·병합·Diff·충돌 검증·서버 승인·산출물 다운로드·적용 검증은 미구현이다. 지원 파일 종류/경로/범위, 기준 버전, 승인 fingerprint, 다운로드 권한 및 적용 보고 계약을 합의해야 한다.
5. 06단계 Claude Code 입력 안내와 09~10단계 시연 UI는 반영되었지만, 07~10단계의 실제 추천·권한·설정·다운로드 API 연동은 후속 과제다.


## Preview 09/10 (시연 확인과 실제 승인 분리)

- HTML/DOCX의 선택 요약·파일 Preview·최종 확인·체크 전 CTA 비활성을 재현했다.
  `/demo/projects/:demoId/preview`는 유효한 선행 입력과 Selection 시연 entry 및 명시적 preview-enter가 있어야 한다.
  없는 프로젝트는 기존 목록 안내, 미진입·선행 입력 오류는 Selection 및 기존 선행 단계 정책으로 처리한다.
- 실제 상태 모델은 NOT_PREPARED, files:null, approval:null, download:null이다. API 요청/응답은 없다.
  null은 미준비/미조회이며 조회된 빈 파일 목록 []와 동일하지 않다. UI 예시가 실제 files를 채우지 않는다.
  service 렌더링은 인증·검증된 Preview/승인 API 미연동 안내로 차단하며 서비스 경로에는 공통 인증 차단 화면만 연결돼 있다.
- `previewDrafts[id]`는 source:demo-ui, visible, acknowledged, selectionSignature만 보관한다.
  acknowledged는 화면 확인 체크일 뿐 승인 상태가 아니다. 서버 승인 ID·산출물·다운로드 URL은 생성하지 않는다.
- 기본은 실제 검증 도구 없음·권한 미확인·파일 경로/변경 유형/기존 파일 상태 미확인이다.
  명시적으로 예시를 열면 Selection의 추상 카드/권한 조작값 요약과 별도 fixture의 설명문만 표시한다.
  실제 Claude Code JSON·파일 경로·Diff·권한 규칙을 예시로 발명하지 않았다.
- 예시 열기/닫기는 체크를 해제한다. 이전 단계·뒤로/앞으로·동일 내용 재진입은 체크를 보존한다.
  Selection의 selectedIds/permission 변경 시 체크를 해제하고 현재 시연 내용으로 재확인을 요구한다.
  정보 펼침/닫기만으로는 내용 확인을 해제하지 않는다. 이 signature는 UI 비교값이며 서버 승인 fingerprint가 아니다.
  실제 파일 내용 변경은 현재 발생하지 않는다. 향후 서버 Preview 버전·내용·권한·Client·입력 snapshot 변경 시
  서버 근거로 승인을 무효화하는 계약이 필요하다.
- 시연 next는 체크 후 활성화되며 명시적인 사용자 동작으로 10단계 시연 안내 화면에만 진입한다. 실제 서버 승인을 기록하지 않는다.
  체크 및 시연 흐름은 실제 권한 승인/설정 생성·병합/설치/다운로드/서버 저장을 의미하지 않는다.
  원본은 체크 후 완료 화면으로 이동하므로 이 부분은 승인 범위에 맞춘 의도적 차이다.
- 기존 Selection DENY+필수 예시 경고는 UI 조합 확인용이다. 명시적 09 미리보기는 이를 실제 제약 통과로
  취급하지 않으며 파일의 유효성/충돌/도구 호환성/필수 권한을 정상 판정하지 않는다.

### 실제 연동 전 합의할 계약

phase1 OpenAPI에는 Preview/승인/Config/다운로드 계약이 없다. 새 endpoint나 DTO는 만들지 않았다.
Claude Code의 사용자/프로젝트/로컬/관리 설정 범위는 [공식 설정 문서](https://code.claude.com/docs/en/settings),
규칙·모드·관리 정책은 [공식 권한 문서](https://code.claude.com/docs/en/permissions)를 참고해야 하며
Codex 설정 형식이나 현재 시연 3값과 동일하게 취급하지 않는다.

- 대상 Client/버전·지원 manifest·Catalog/추천/Selection snapshot과 Preview의 연결.
- 생성/조회 상태·오류·충돌·미확인 표현, 파일 목록 null/[] 의미, 지원 파일 종류/경로/범위.
- 기존 파일 제출·Secret 정제·보관 정책, 신규안 vs 실제 CREATE/UPDATE 판정, 병합/Diff·검증 주체.
- 승인 주체·소유권·기준 버전/내용 fingerprint·유효 기간·중복 요청·409·취소/재승인·감사 기록.
- Preview와 승인·최종 산출물 일치, 변경 시 승인 무효화, 다운로드 권한·만료·실패/재시도.
- 10단계 적용/인증/검증 안내와 사용자 보고·실제 검사 구분. 다운로드만으로 설치 완료 표시 금지.


## Complete 10/10 (다운로드 미연동 + 시연 사용자 보고)

### 시안·문서 대조와 구현 범위

- HTML/DOCX: 다운로드 경고·4개 적용 순서·다운로드 CTA, 적용 상태 단일 선택(APPLIED/FAILED),
  FAILED일 때만 실패 사유 선택(파일 복사 실패/인증 필요/의존성 누락/알 수 없음), 보고 저장·기록,
  NOT_RUN 검증 상태·내 프로젝트 이동을 구현했다. 미보고는 빈 상태이며 별도 NOT_STARTED enum을 만들지 않는다.
- 시연 UI에서는 적용 결과 상태 선택을 필수로 검증하며, FAILED를 선택한 경우에만 실패 사유를 필수로 검증한다.
  이는 현재 프론트 입력 정책이며, 실제 보고 API의 필수 조건과 enum은 팀 합의가 필요하다.
  원본 toast 대신 해당 필드의 inline 오류와 focus를 제공한다.
  실패 사유는 자유 텍스트가 아니라 원본의 고정 select다.
  상태를 바꿨다가 돌아오면 사유 초안은 보존하고, APPLIED로 확인한 report에는 reason을 빈 문자열로 둔다.
  오류 시 마지막 유효 report를 덮어쓰지 않는다.
- 원본의 Codex 적용 문구를 팀 확정 정책에 따라 Claude Code로 바꿨고 `clientDefinition.js`의 안내 배열로 분리했다.
  파일 종류·경로·범위·병합·실제 인증 방법은 확정하지 않는다. 실제 적용을 수행하라는 실행 기능은 없다.
- PRD-27/28/38의 사용자 보고와 실제 검사 구분을 따른다. ‘적용 완료’는 사용자가 고른 **시연 사용자 보고**이며,
  실제 설치 성공이나 시스템 검증 결과가 아니다. 10/10 진행률은 시연 화면 위치다.
- 원본에 없는 이전 버튼은 요구된 왕복 검증을 위해 추가했다. 시연/미연동/초기화 안내와 필수 표시도 보강했다.

### 접근·데이터 보호

- 실제 기본 데이터는 artifact:null / approval:null / download:null / verification:NOT_RUN이다.
  가짜 파일·ZIP·Blob·download 링크를 생성하지 않는다. 다운로드 버튼은 ‘실제 파일 없음’ 안내만 표시한다.
- Complete 진입은 현재 유효한 demo Profile/Role/Environment/Selection/Preview와 예시 열림·체크·
  현재 선택 signature 일치가 필요하며, complete-enter를 명시적으로 수행해야 한다.
- Preview 체크 해제·예시 닫기·Selection 도구/권한 변경은 completeDrafts[id].entered를 취소한다.
  재체크만으로 예전 접근을 복구하지 않고 다음 버튼을 다시 눌러야 한다. 직접 URL/뒤로/앞으로 진입도 같은 guard를 따른다.
- completeDrafts[id]의 source는 demo-ui. draft와 마지막 유효 report는 분리하고 프로젝트별 메모리에만 보관한다.
  report.source는 demo-user-report, verification은 NOT_RUN이다. 실제 서버 승인/적용 보고 저장소와 합치지 않는다.
  원본과 달리 보고 시각·설치/다운로드 완료 시각을 임의로 만들지 않는다.
- 접근이 취소돼도 초안/보고는 삭제하지 않는다. 선택 signature가 바뀐 뒤 다시 진입하면 이전 보고에
  ‘이전 시연 선택에 대한 보고’ 표시를 붙여 현재 구성 결과로 오인하지 않게 한다.
- 서비스 렌더링은 인증·승인·설정 산출물·다운로드 API 미연동 안내로 차단하고 demo 입력/보고/가짜 완료를 노출하지 않는다.
  서비스 경로는 준비됐으며 실제 OAuth·다운로드 API는 없다. 새로고침·시연 종료 시 메모리 상태는 초기화된다.

### 백엔드 연동 전에 필요한 계약

phase1 OpenAPI에 다운로드·적용 보고 경로/스키마가 없어 신규 endpoint/응답을 발명하지 않았다.

1. Claude Code 지원 OS/버전·설정 파일/경로/범위·manifest 및 기존 파일 병합/충돌/보존 정책.
2. Preview/Selection snapshot·최종 승인 fingerprint·Config 산출물 일치, 변경·취소·만료 시 접근 무효화.
3. 산출물 생성/실패/미생성 상태와 파일 목록 null/[] 의미, 다운로드 소유권·인증·만료·재시도·파일명/형식.
4. 적용 보고 상태/실패 사유 enum 코드, 원문/정제 규칙, 저장/조회·수정·시점·버전·소유권·보고의 기준 구성 연결.
5. USER_REPORTED와 팀 조합 검사/실제 환경 검사의 구분, verification 상태·검증 주체·시간과 근거.
6. 실제 승인·다운로드·설치 성공은 API 응답 및 검증 근거가 있어야 표시한다. 다운로드만으로 verified 처리 금지.
