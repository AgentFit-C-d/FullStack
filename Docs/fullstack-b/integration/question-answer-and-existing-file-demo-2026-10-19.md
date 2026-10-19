# 2026-10-19 B 데모 연결 기록

## 추가 질문 답변

- V7 `recommendation_question_answer`는 `QUESTION` notice에 대한 사용자 답변과 작성자·시각을 저장한다. 답변은 추천 ID와 프로젝트에 묶이며, 저장은 현재 최신 `NEEDS_INFORMATION` 추천의 실제 질문에만 허용한다.
- `GET /api/projects/{projectId}/recommendations/{recommendationId}/answers`는 저장된 답변 목록을, `PUT .../answers/{noticeId}`는 `{ "answer": "..." }`을 받는다. 두 경로 모두 A의 세션·소유권 검사를 재사용하며 `Cache-Control: no-store`를 사용한다.
- 브라우저에서 설치 목록 미확인으로 `NEEDS_INFORMATION`을 생성한 뒤 답변을 저장하고 새로고침 후 복원되는 것을 확인했다. **답변 저장만으로 확정 기획·환경이나 추천 판정은 변경되지 않는다.** 다음 추천에 반영하려면 답변 대상을 A의 확정 입력/환경 버전 또는 AI facts 계약으로 연결해야 한다.

## 기존 설정 파일 비교

- `/app/live`가 우선 미리보기를 생성해 실제 생성 대상 `(targetKey, relativePath)`를 보여 준다. 사용자는 그 경로의 기존 텍스트를 직접 제공하고 `PROVIDED` Preview를 다시 요청할 수 있다.
- 실제 Core API에서 README 기존 내용을 제공했을 때 `README.md`는 `UPDATE`와 diff, 기존 내용을 제공하지 않은 `config/demo.txt`는 `PROPOSAL`로 반환됐다. 입력 내용은 화면 메모리와 재생성 요청에만 있으며 DB에는 파일 원문을 저장하지 않는다.
- 현재 ZIP은 생성된 교체 파일을 담는다. **기존 파일 내용의 자동 병합은 없다.** 실제 병합에는 파일별 보존/충돌 해결 정책과 안전한 Client별 설정 문법 검증이 필요하다.

## 로그인 진입 수정

- `/`의 GitHub 버튼은 원래 메모리 시연 버튼이어서 실제 OAuth를 시작하지 않았다. 실제 로그인 화면을 `/`에 배치하고 메모리 시연 로그인은 `/demo/login`으로 분리했다.
- OAuth 성공 후 A가 보내는 `/?login=success`를 프론트에서 `/app`으로 이동시켰다. 브라우저에서 GitHub 로그인 링크 클릭 → 성공 → `/app`의 DB 프로젝트 8개 표시를 확인했다.

## 남은 범위

- 실제 Claude Code 2.1.270 설치는 로컬에서 확인했지만 도구별 설치·호환성·권한·출력 검증 및 독립 승인 릴리스는 아직 없다. 현재 추천 Catalog는 합성 데모다. [Claude Code 공식 Skill 문서](https://code.claude.com/docs/en/skills)의 형식은 검토했으나 이를 곧바로 지원 검증으로 간주하지 않는다.
- `/demo`의 단계별 화면은 여전히 메모리 시연이고 `/app/live`가 실제 API 연결 화면이다. 이를 혼동하지 않도록 발표 동선을 분리해야 한다.
- Core API의 A 소유 `FailureDiagnosticService.purgeExpired()`가 PostgreSQL JDBC에 `Instant`를 직접 전달해 시간별 정리 작업에서 오류가 발생한다. 로그인 성공과는 무관하지만 A 담당 수정이 필요하다.
