# Full Stack B Phase 1 기반 구현 및 A·AI 연동 경계

## 이 PR에서 리뷰할 결과

- 고정 9개 Capability와 네 추천 결과를 기준으로, AI 후보를 검증한 뒤 승인 Catalog의 **정확한 OS·Client·버전 지원**, 의존성·충돌·전체 조합을 확인해 추천을 판정합니다.
- 선택·권한 정책, 설정 Preview 비교·지문, 최종 승인 및 동일 입력 ZIP 재생성의 B 내부 경계를 구현했습니다. 선택이나 ZIP 생성은 실제 설치 성공으로 취급하지 않습니다.
- B1–B9 PostgreSQL 스키마와 호출자 소유 트랜잭션에서의 기록·조회·삭제 경계를 구현했습니다. 추천 결과의 Capability·도구·이유·환경 확인 질문을 B1–B4 메타데이터로 조립합니다.
- 최신 추천의 B1–B4 상세 정보를 복원하고 현재 버전과 비교해 `CURRENT`/`STALE`을 표시합니다. 현재 추천의 지원 근거·권한 선택지는 승인된 동일 Catalog에서만 읽습니다. 프로젝트 삭제 시에는 프로젝트 행을 잠가 늦게 들어오는 추천 저장과의 경합을 막습니다.
- Catalog 파일의 해시·스키마를 검사하고, skills.sh 후보 수집과 로컬 Playwright MCP 파일럿을 **미승인 후보 경로**에 격리했습니다. 미검증 도구를 추천 가능한 릴리스로 승격하지 않습니다.

## 리뷰 순서

1. [현재 범위와 미완료 항목](https://github.com/AgentFit-C-d/FullStack/blob/feat/fullstack-b-phase1/Docs/fullstack-b/STATUS.md), [완료 게이트](https://github.com/AgentFit-C-d/FullStack/blob/feat/fullstack-b-phase1/Docs/fullstack-b/PHASE1-CLOSEOUT.md)를 먼저 확인해 주세요.
2. [기능별 코드 길잡이](https://github.com/AgentFit-C-d/FullStack/blob/feat/fullstack-b-phase1/Docs/fullstack-b/CODE-MAP.md)를 따라 Catalog → 추천·선택 → Preview·승인·ZIP 순서로 보시면 됩니다.
3. DB 제약과 A가 넘겨야 할 트랜잭션은 [PostgreSQL 인계](https://github.com/AgentFit-C-d/FullStack/blob/feat/fullstack-b-phase1/Docs/fullstack-b/db/postgresql-schema-handoff.md), A·AI 입력/응답의 미합의 항목은 [연동 계약 제안](https://github.com/AgentFit-C-d/FullStack/blob/feat/fullstack-b-phase1/Docs/fullstack-b/integration/ab-ai-contract-proposal.md)에 있습니다.

## 검증

- Java 21 타깃, 로컬 Docker PostgreSQL 연결에서 `services/core-api`의 `mvn clean verify`: **315개, 실패·오류 0개, Windows 심볼릭 링크 제한으로 1개 건너뜀**. PostgreSQL 테스트에는 `AGENTFIT_TEST_POSTGRES_URL=jdbc:postgresql://127.0.0.1:5433/agentfit`를 사용했습니다.
- `python -m unittest discover -s tools/tests -p 'test_*.py'`: **6개 통과**. 로컬 MCP 파일럿은 재시작 전후 전체 도구 목록이 달라지면 실패합니다.
- `git diff --check`와 `git merge-tree --write-tree origin/develop HEAD`: 변경 형식 검사 통과, 현재 `develop`과 병합 충돌 없음.

## 아직 이 PR의 완료 범위가 아닌 것

- A의 인증·프로젝트 소유권·확정 스냅샷·현재 버전 재검사·Audit와 실제 DB 트랜잭션 연결
- AI HTTP 성공/실패 응답 계약, 9개 Capability의 생략·선택 의미, 공개 API·화면 연결
- 팀이 채택한 첫 Claude Code 도구·정확한 지원 범위의 실제 검증, 권한/조합/템플릿 근거와 독립 승인 해시

따라서 이 PR은 **B 독립 구현의 리뷰 단위**입니다. B01–B12 상위 목표의 현재 완료 표시는 4/12이며, 사용자 요청부터 설정 다운로드까지 연결된 Phase 1 전체 완료나 실제 Client 지원을 주장하지 않습니다. A·AI와 계약이 확정되면 연동 작업을 후속 변경으로 진행합니다.
