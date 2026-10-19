# 로컬 웹 데모 동작 범위 — 2026-10-18

## 시연 가능한 경로

`http://localhost:8080/app`에서 GitHub 로그인 후 프로젝트를 선택한다. 현재 실서버 작업 화면은 `/app/live?project=<UUID>`이다. 단계별 `/app/projects/:id/...` 주소도 같은 프로젝트의 이 화면으로 이동한다. `/demo`는 별도의 메모리 시연이다.

브라우저에서 신규 프로젝트 `신규 분석 통합 검증 20261018` (`556bcf3f-fdf6-4552-9e17-5545fa77da88`)을 사용해 다음 흐름을 확인했다.

1. 프로젝트 생성과 직접 입력 기획 자료의 PostgreSQL 저장
2. FastAPI 분석 작업 `PROCESSING → SUCCEEDED`, DRAFT 항목 16개 생성
3. 사용자 DRAFT 확인과 CONFIRMED Profile v1 저장
4. 개발자 역할·환경 저장 및 재조회, 설치 도구 목록의 선택적 버전 입력과 새로고침 복원
5. AI Capability → Core의 합성 승인 Catalog 판정 → `RECOMMENDED` 저장
6. 권한 선택 → 미리보기(README.md와 설정 예시 파일) → 승인 → ZIP 다운로드
7. 생성 이력의 `GENERATED`/`CURRENT` 및 파일 수 조회

환경만 바꿨을 때 기존 추천이 `STALE`이 되고 새 추천을 만들면 다시 `RECOMMENDED`가 되는 흐름도 확인했다. 변경 없는 역할·환경 저장은 버전을 올리지 않고, 환경만 수정하면 개발자 프로필 버전은 유지된다. `GET /api/tool-catalog`는 현재 저장된 OS·Client·Client 버전으로 필터링할 수 있다. skills.sh 후보는 `UNVERIFIED`로 별도 조회되며 추천에 포함되지 않는다.

Core·Web 재배포 후에도 같은 프로젝트의 현재 추천으로 미리보기→승인→ZIP 다운로드를 다시 실행했다. 최신 생성 이력은 `GENERATED`/`CURRENT`와 파일 2개를 반환했고, 환경 변경 전 이력은 `STALE`로 남았다.

## 아직 완료로 표시할 수 없는 범위

- 합성 Catalog는 **시연 데이터**다. 실제 Claude Code 도구의 지원·권한·설정 출력은 별도 검증과 승인 전이므로 설치 가능한 결과로 설명하지 않는다.
- 공식 skills.sh API의 상시 호출은 Vercel OIDC가 없어 활성화하지 않았다. 화면은 로컬 수집 스냅샷을 읽는다. 이 스냅샷은 추천 가능한 승인 Catalog와 별개다.
- 최신 10단계 디자인을 각각 실제 API 폼으로 바꾸지는 않았다. 실서버 작업은 한 화면에서 이어진다.
- PDF/Markdown 업로드와 분석은 2026-10-18 브라우저 및 PostgreSQL에서 확인했다. 다만 모델이 원문에 없는 근거 문구를 반환하면 분석 전체가 실패한다. 이 경우 원문 근거 검증이 정상적으로 거부한 것이며 사용자에게 재시도 가능 상태로 남는다.
- AI 컨테이너의 provider 설정은 `solar`이고 Upstage 키가 주입돼 있다. 실제 분석 호출과 정상 응답을 확인했으며 코드상 Solar HTTP 호출 실패를 결정론적 응답으로 대체하지 않는다. 외부 제공자의 개별 요청 ID는 저장하지 않는다. 사용자 PC 설치·인증 성공은 아직 확인하지 않았다.
- Core의 A 소유 만료 진단 삭제 작업에서 PostgreSQL `Instant` 바인딩 오류가 로그에 반복된다. 성공 경로는 동작했지만 A 측 수정이 필요하다.

## 실행·검증 기록

- 프론트: `npm test` 80개 통과, `npm run lint`, `npm run build` 통과
- Core: Java 21 `CatalogControllerTest` 4개 통과
- Docker: Core API·FastAPI·PostgreSQL·Web 재기동 후 GitHub 세션과 추천 화면 조회 확인
- 2026-10-18 skills.sh 실서버: `GET /api/v1/skills/search?q=react&limit=2` 토큰 없이 401, 공개 실험 경로 `GET /api/search?q=react&limit=2`는 200 및 2건. 후보 갱신 명령은 `changed=0; CATALOG_NOT_APPROVED; EXPERIMENTAL_PUBLIC_API`를 반환했다. 로컬 스냅샷과 실행 중인 Core 컨테이너의 파일 SHA-256이 같았다.
- 2026-10-18 로컬 DB: 최근 분석 작업에 `SUCCEEDED` 기록이 있으며, 실행 중인 AI 컨테이너는 `solar` provider 및 Upstage 키 존재 상태였다. 비밀값은 출력하거나 문서화하지 않았다.
- 2026-10-18 Markdown 브라우저 검증: 파일 업로드 후 DB에 `MARKDOWN`, 파일명, 193바이트, 95자, revision 2가 저장됐다. 분석은 `SUCCEEDED`로 끝나고 DRAFT v1이 화면에 표시됐다. 처음 파일은 제목의 `AgentFit`을 Solar가 프로젝트명으로 오인해 존재하지 않는 근거를 반환했고 `INVALID_PROVIDER_OUTPUT`으로 거부됐다. 충돌하는 제목을 제거한 파일로 재시도했다.
- 2026-10-18 PDF 브라우저 검증: 한국어 1페이지 PDF 업로드 후 DB에 `PDF`, 25,288바이트, 추출 86자, 1페이지, revision 2가 저장됐다. 분석은 `SUCCEEDED`로 끝나고 DRAFT v1이 화면에 표시됐다. 앞선 영문 PDF에서는 Solar가 원문 영어 라벨을 한국어로 바꿔 인용해 `INVALID_PROVIDER_OUTPUT`으로 거부됐다. 따라서 영어 PDF 일반 성공은 아직 검증되지 않았다.

이 문서는 브라우저에서 직접 확인한 데모 범위를 기록한다. [기존 백엔드 범위](phase3-confirmed-profile-handoff.md)와 [B 구현 추적](../STATUS.md)을 대체하지 않는다.
