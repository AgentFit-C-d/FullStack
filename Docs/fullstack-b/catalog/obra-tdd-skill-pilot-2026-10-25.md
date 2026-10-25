# 실물 Skill 카탈로그 조사: Superpowers TDD

## 출처와 범위

- skills.sh ID: `obra/superpowers/test-driven-development`
- skills.sh 응답 해시: `8e8772822d25e4dfc9295cf4526c6251a10458c7417ac50c4b42e5d9fc91e0b0`
- 검증 대상: Windows, Claude Code 2.1.270, `cap_test_authoring`
- 정적 출력: `.claude/skills/test-driven-development/SKILL.md`와 `writing-good-tests.md`, 설치 안내와 MIT 고지
- 후보 위치: `catalog-candidates/obra-tdd-windows-claude-2.1.270/`

## 2026-10-25 확인 결과

1. Java 수집기의 공개 실험 모드로 skills.sh 검색·상세 API를 호출했다. `test-driven-development` 검색 결과에 위 ID와 동일한 해시가 포함됐다. 공식 v1 API는 Vercel OIDC가 필요해 이 로컬 검증에는 사용하지 않았다.
2. skills.sh 다운로드 응답의 `SKILL.md`와 `writing-good-tests.md`를 고정해 후보에 담았다. 원 저장소의 MIT 라이선스 고지를 함께 담았다.
3. Claude Code 2.1.270에서 격리 디렉터리의 `.claude/skills`에 대해 `claude plugin validate`가 `Validation passed`를 반환했다.
4. `CatalogCandidateValidatorCommand`가 후보 무결성·의미·정적 출력 형식을 통과했다. `RELEASE_NOT_READY` 사유는 `SUPPORT_NOT_RUN,INDEPENDENT_APPROVAL_REQUIRED`다.
5. Claude Code 모델 호출로 Skill 실제 실행을 시도했으나 `OAuth session expired and could not be refreshed`로 인증 단계에서 멈췄다. 모델 토큰·비용은 0으로 기록됐다. 실제 Skill 호출 성공으로 해석하지 않는다.
6. 사용자가 CLI 로그인을 갱신한 뒤 동일한 격리 폴더에서 `/test-driven-development`를 호출했다. 첫 시도는 0.05달러 한도에 도달해 응답이 없었다. 다음 시도는 Claude Haiku 지정으로 종료 코드 0과 Skill의 핵심 원칙을 반환했다. 별도 호출에서 보조 파일 `writing-good-tests.md`의 첫 제목 `Writing Good Tests`도 읽어 반환했다. 해당 작업 중 파일 수정이나 명령 실행은 요청·수행하지 않았다.
7. 검증 결과를 기존 후보를 덮어쓰지 않고 `obra-tdd-windows-claude-2.1.270-verified`에 반영했다. 새 해시는 `9baf88eda910d9fc96a35163aeacb9ae0c08edd04a3bef9f5ed46c47da3ddf90`이다. 후보 검증 결과 남은 사유는 `INDEPENDENT_APPROVAL_REQUIRED` 하나다.
8. B의 실제 추천→권한 선택→미리보기→승인→ZIP 테스트에서 위 후보의 `.claude/skills/test-driven-development/` 파일과 MIT 고지가 산출물에 들어가는 것을 확인했다. 미검증 원본 후보는 추천되지 않는다.

## 로컬 검토 실행

`infra/compose.catalog-review.yaml`은 이 고정 후보를 로컬 Core API에 읽기 전용으로 연결한다. 이 구성은 승인 전 검토용이며 운영 배포 파일이 아니다. 저장소 루트에서 개인 환경 파일을 지정해 실행한다.

```powershell
docker compose -p agentfit_catalog_review --env-file <로컬 환경 파일> -f infra/compose.fullstack.yaml -f infra/compose.catalog-review.yaml up -d --build
```

이 구성이 띄워지면 인증된 `GET /api/tool-catalog?osFamily=WINDOWS&clientId=claude-code&clientVersion=2.1.270&capabilityId=cap_test_authoring`으로 검토 후보를 읽을 수 있다. 다른 Client/버전이나 미충족 Capability에는 지원한다고 표시하지 않는다.

## 활성화 조건

- 별도 검토자가 고정된 Skill 파일, 출력 경로, MIT 고지, `edit-project-files`·`run-project-tests`의 ASK/DENY 권한 안내와 위 검증 근거를 확인한다.
- 검토자가 승인한 해시를 서버 설정 `AGENTFIT_CATALOG_DIRECTORY`와 `AGENTFIT_CATALOG_APPROVED_HASH`에 지정한다. 이 설정이 바뀌기 전까지 실제 추천은 해당 Skill을 사용하지 않는다.
- 정확한 대상 Windows·Claude Code 2.1.270에서 `cap_test_authoring`이 필요한 프로젝트의 추천→권한 선택→Preview→ZIP을 HTTP·PostgreSQL 경로로 재검증한다.

skills.sh는 후보를 공급하며, [공식 API](https://www.skills.sh/docs/api)는 OIDC 인증이 필요하다. [Claude Code Skill 문서](https://code.claude.com/docs/en/skills)는 프로젝트 `.claude/skills/` 경로와 보조 파일 구성을 설명한다.
