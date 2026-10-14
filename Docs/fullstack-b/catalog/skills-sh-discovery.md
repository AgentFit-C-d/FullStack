# skills.sh Skill 후보 수집 경계

## 목적과 현재 범위

skills.sh의 [공식 API](https://www.skills.sh/docs/api)에서 Skill을 검색하고 상세 응답의 해시를 확인하여 **검토 후보**로 저장한다. 후보는 검증된 Catalog 릴리스가 아니다. 현재 추천·Preview는 승인된 Catalog 파일만 읽는다. MCP 디렉터리 연동은 보류한다.

## 처리 흐름

1. `SkillsShCandidateCollector.production(tokenSupplier)`가 토큰을 사용해 검색 API와 각 Skill 상세 API를 호출한다. 쿼리 길이, 결과 수(최대 20), 응답 크기(2 MiB), 연결·요청 시간을 제한한다. 중복 표시 항목을 건너뛰고 검색 ID·source·URL과 상세 ID·source가 일치하는지 확인한 뒤 SHA-256 해시를 받는다.
2. `SkillsShCandidateRefresh.refresh(query, limit, snapshotPath)`가 수집 성공 후에만 별도의 JSON 스냅샷을 갱신한다. 새 후보 또는 이름·URL·해시가 바뀐 후보를 반환한다. 검색 결과에 다시 나타나지 않은 후보는 삭제하지 않는다.
3. API의 인증·제한·장애 응답 또는 잘못된 JSON은 오류로 처리한다. 기존 스냅샷을 빈 목록으로 덮어쓰지 않는다. 스냅샷도 엄격하게 검증하고 임시 파일에서 원자적으로 교체한다.
4. 담당자가 후보의 출처, 실제 파일, Claude Code 버전/OS 지원, 의존성·충돌, 권한, 조합과 설정 방법을 따로 검증한다. 검증 후 별도 Catalog 릴리스를 작성·승인해야 추천에 들어간다. 외부 API 데이터만으로 자동 설치하거나 추천하지 않는다.

`SkillsShReviewQueue.classify`는 후보 스냅샷과 별도 검토 결정 목록을 받아 `PENDING`, `SHORTLISTED`, `REJECTED`, `CHANGED`를 계산한다. 결정에는 후보 ID와 검토 당시 해시·이름·검토자·시각을 묶는다. 해시나 이름이 바뀌면 과거 SHORTLISTED/REJECTED 판단은 `CHANGED`가 된다. 관측 시각만 바뀐 경우에는 기존 판단을 유지한다. SHORTLISTED는 Catalog 검증 완료나 사용자 추천 가능을 의미하지 않는다. 검토 결정의 DB 저장·관리자 화면은 A 계약 전까지 없다.

`SkillsShReviewDecisionStore`는 이 검토 결정을 후보 스냅샷과 **별도 JSON 파일**에 원자적으로 기록한다. 현재 후보에서 ID·이름·해시를 직접 읽어 결정에 묶고, 알 수 없는 후보·손상된 기존 결정 파일은 거부한다. `SkillsShTriageCommand`는 `list`, `shortlist`, `reject`를 제공하며, 후보 파일이 없으면 빈 목록으로 성공하지 않는다. 여기의 `reviewer-id`는 로컬 운영자가 입력한 표식일 뿐 인증된 사용자 ID나 독립 Catalog 승인자가 아니다.

## 로컬 후보 갱신 명령

`SkillsShCandidateRefreshCommand`는 기존 수집기와 스냅샷 갱신기를 연결하는 **관리자 로컬 실행 진입점**이다. `services/core-api`에서 실행하며, 연동된 Vercel 프로젝트가 발급한 유효한 `VERCEL_OIDC_TOKEN`을 프로세스 환경에서 제공해야 한다. 토큰을 명령 인자나 저장 파일에 넣지 않는다.

```powershell
mvn '-Dspring-boot.run.main-class=com.agentfit.coreapi.catalog.discovery.SkillsShCandidateRefreshCommand' '-Dspring-boot.run.arguments=react 20 ../../catalog-candidates/skills-sh-candidates.json' spring-boot:run
```

성공 시 변경·신규 후보 수와 `CATALOG_NOT_APPROVED`를 출력한다. 인증·API·스냅샷 오류면 실패하고 이전 파일을 유지한다. 이 명령은 제품 Catalog를 생성·승인하거나 추천에 연결하지 않는다. 실제 OIDC 토큰을 이용한 실서버 호출은 아직 검증하지 않았다.

후보를 확인하고 로컬 선별 결과를 기록할 때는 같은 디렉터리에서 아래 명령을 사용한다. `shortlist`와 `reject`의 `<external-id>`는 `list`가 출력한 ID를 그대로 사용한다. 생성된 두 JSON 파일은 Git에서 제외한다.

```powershell
mvn '-Dspring-boot.run.main-class=com.agentfit.coreapi.catalog.discovery.SkillsShTriageCommand' '-Dspring-boot.run.arguments=list ../../catalog-candidates/skills-sh-candidates.json ../../catalog-candidates/skills-sh-decisions.json' spring-boot:run
mvn '-Dspring-boot.run.main-class=com.agentfit.coreapi.catalog.discovery.SkillsShTriageCommand' '-Dspring-boot.run.arguments=shortlist ../../catalog-candidates/skills-sh-candidates.json ../../catalog-candidates/skills-sh-decisions.json owner/repo/skill reviewer-1' spring-boot:run
```

이 결과는 조사 우선순위만 정한다. 출시 도구가 되려면 실제 Skill 파일·설치 방법·대상 OS/Client 버전·권한·의존성·조합·설정 출력을 별도로 검증하고 Catalog 릴리스를 독립 승인해야 한다.

## 아직 필요한 연결

- 2026-10-10 [skills.sh 공식 API 문서](https://www.skills.sh/docs/api)를 재확인했다. 검색·상세 경로, `Authorization: Bearer` 형식, 검색 `data`와 상세 `id`/`source`/`hash`는 현재 수집 코드의 가정과 맞는다. 상세 `hash`는 스냅샷이 없으면 `null`일 수 있으므로 현재 코드는 해당 후보를 거부한다. 이 확인은 문서 계약 점검이며 실서버 호출 성공이나 Catalog 검증은 아니다.
- 공식 인증 방식은 **Vercel 프로젝트 OIDC 토큰**이다. 배포 대상이 아직 정해지지 않아 Spring Boot 서버에서 이 토큰을 안전하게 발급·전달·갱신할 방법은 미확정이다. 일반 사용자 로그인 토큰이나 임의의 고정 API 키로 대체하지 않는다. 이 작업에서는 실서버 호출을 하지 않았다.
- 로컬 관리용 갱신·선별 명령과 별도 결정 파일은 추가됐지만, 서버의 보호된 수집 API·배치, 인증된 검토자/관리 UI·DB 승인 기록, 검증된 Catalog 릴리스 생성 절차는 미구현이다.
- API 응답에는 AgentFit의 OS·Client 버전 호환성, 권한, 의존성·충돌 검증 결과가 없다. 해시 변경은 재검토 신호이지 품질·보안 검증이 아니다.

## 검증

네트워크를 쓰지 않는 전송 경계 테스트에서 정상 검색, 누락 토큰·503 오류, 상세 ID·해시 오류, 변경 감지, 스냅샷 보존과 손상 거부를 확인한다.
