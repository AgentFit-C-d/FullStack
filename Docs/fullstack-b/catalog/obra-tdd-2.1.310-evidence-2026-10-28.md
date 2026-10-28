# Claude Code 2.1.310 실물 Skill 검토 근거 — 2026-10-28

## 판단 범위

대상은 skills.sh의 `obra/superpowers/test-driven-development` **고정 파일 1종**이다. 현재 로컬 CLI는 `claude --version`에서 `2.1.310 (Claude Code)`을 반환했다. Windows/Claude Code `2.1.310` 이외의 OS·Client·버전은 지원 판정하지 않는다. 후보의 Skill 원문 SHA-256은 `8e8772822d25e4dfc9295cf4526c6251a10458c7417ac50c4b42e5d9fc91e0b0`이다.

2026-10-27 격리 Node.js 파일럿에서는 Claude Code가 이 Skill을 읽고 테스트를 먼저 실패시킨 뒤 구현·재실행해 테스트 2개를 통과했다. 따라서 `cap_test_authoring`만 근거가 있다. Java/Maven에서 같은 동작을 완료했다는 증거는 없으며 `cap_test_execution`을 독립 제공 기능으로 올리지 않는다. [실행 기록](obra-tdd-2.1.310-local-review-2026-10-27.md).

## 권한·위험도·조합

| 항목 | 기록한 판정 | 근거와 한계 |
| --- | --- | --- |
| 정적 출력 | `.claude/skills/test-driven-development/SKILL.md`, 보조 파일, 설치 안내, MIT 고지 | 후보의 `templates/index.json`과 12개 파일 SHA-256이 매니페스트와 모두 일치. Skill은 지시문이며 설치 프로그램이나 MCP 서버가 아님. |
| 권한 | `edit-project-files`, `run-project-tests` 각각 `ASK_EACH_TIME` 또는 `DENY` | Skill이 파일 수정·테스트 실행을 지시하므로 사용자가 작업 실행을 결정한다. Skill 설치 자체가 Claude Code의 파일·명령 권한을 부여하지 않는다. [Claude Code 권한 문서](https://code.claude.com/docs/en/permissions). |
| 외부 인증 | Skill 자체는 `NOT_REQUIRED` | 별도 서비스 자격증명을 요구하는 Skill이 아니다. Claude Code 사용자의 기존 로그인은 별개의 Client 조건이다. |
| 위험도 | `MEDIUM` | 프로젝트 파일 수정과 테스트 명령 실행을 유도할 수 있어 사용자 검토·권한 선택을 유지한다. 이 평가는 도구 내용과 [Claude Code 권한 문서](https://code.claude.com/docs/en/permissions)에 근거한다. |
| 조합 | **단일 Skill 구성만 검증** | 후보 `relations.json`에는 의존성·충돌·검증된 복수 도구 조합이 없다. 다른 Skill/MCP와의 호환성은 증명되지 않았다. |

[Claude Code Skill 문서](https://code.claude.com/docs/en/skills)는 프로젝트 `.claude/skills/` 위치와 보조 파일 구성을 설명한다. skills.sh 검색 결과는 후보 수집용이며 지원·권한·조합 판정을 대신하지 않는다. 공식 v1 API는 [skills.sh API 문서](https://www.skills.sh/docs/api)에 따라 Vercel OIDC 인증이 필요하다.

## 새 검토 후보와 검증 결과

기존 후보를 바꾸지 않고 `catalog-candidates/obra-tdd-windows-claude-2.1.310-risk-reviewed/`에 위험도·외부 인증 판단을 추가했다. 릴리스 ID는 `claude-code-windows-2026-10-28-tdd-risk-reviewed-1`, 카탈로그 해시는 `e220a4a01e8865779c78464e722dbb24dff112c7e44475c284a54c4316661527`이다. 매니페스트의 12개 파일 SHA-256이 모두 일치했다.

Java `CatalogCandidateValidatorCommand` 결과:

```text
CANDIDATE_SCHEMA_VALID releaseId=claude-code-windows-2026-10-28-tdd-risk-reviewed-1 catalogHash=e220a4a01e8865779c78464e722dbb24dff112c7e44475c284a54c4316661527 tools=1
RELEASE_NOT_READY reasons=INDEPENDENT_APPROVAL_REQUIRED
```

**운영 활성화는 아직 금지한다.** 팀의 별도 검토자가 고정 파일·실행 증거·권한/위험도·단일 구성 범위를 확인하고, 승인한 해시를 서버의 신뢰 설정으로 제공해야 한다. 이번 작업자가 작성한 `riskReview`는 독립 승인을 대신하지 않는다. 승인 후 실제 AI Capability 응답을 사용해 추천→권한 선택→Preview→ZIP과 미충족 경고를 다시 검증한다.
