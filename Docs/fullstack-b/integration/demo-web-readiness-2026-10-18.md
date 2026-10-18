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

## 아직 완료로 표시할 수 없는 범위

- 합성 Catalog는 **시연 데이터**다. 실제 Claude Code 도구의 지원·권한·설정 출력은 별도 검증과 승인 전이므로 설치 가능한 결과로 설명하지 않는다.
- 공식 skills.sh API의 상시 호출은 Vercel OIDC가 없어 활성화하지 않았다. 화면은 로컬 수집 스냅샷을 읽는다.
- 최신 10단계 디자인을 각각 실제 API 폼으로 바꾸지는 않았다. 실서버 작업은 한 화면에서 이어진다.
- PDF/Markdown 업로드는 A 서버 테스트가 있지만 이번 브라우저 전체 흐름에서는 직접 입력 텍스트만 확인했다.
- Solar 실제 provider 호출, 사용자 PC 설치·인증 성공은 이 검증의 대상이 아니다. 사용자 적용 보고는 실제 설치 검증과 구분해야 한다.
- Core의 A 소유 만료 진단 삭제 작업에서 PostgreSQL `Instant` 바인딩 오류가 로그에 반복된다. 성공 경로는 동작했지만 A 측 수정이 필요하다.

## 실행·검증 기록

- 프론트: `npm test` 80개 통과, `npm run lint`, `npm run build` 통과
- Core: Java 21 `CatalogControllerTest` 4개 통과
- Docker: Core API·FastAPI·PostgreSQL·Web 재기동 후 GitHub 세션과 추천 화면 조회 확인

이 문서는 브라우저에서 직접 확인한 데모 범위를 기록한다. [기존 백엔드 범위](phase3-confirmed-profile-handoff.md)와 [B 구현 추적](../STATUS.md)을 대체하지 않는다.
