# Claude Code 로컬 조사 기준 — 2026-10-07

> 상태: **조사 후보 / Catalog PASS 아님**. 팀의 첫 릴리스 대상 OS·Client 버전과 후보 도구 목록은 아직 결정되지 않았다. 개인 PC 상태를 모든 사용자 지원 범위로 일반화하지 않는다.

| 확인 항목 | 이 PC에서 관찰한 값 | 확인 방법·한계 |
| --- | --- | --- |
| OS | Windows 커널 `10.0.26200.9457`, Claude 진단 `win32-x64` | `cmd /c ver`, `claude doctor`; 제품 표기와 팀 지원 OS 범위는 별도 결정 |
| Claude Code | `2.1.270 (Claude Code)` | `claude --version` 성공. 설치·실행 가능하다는 로컬 관찰만 의미 |
| 설치 형태 | npm-global, 패키지 `@anthropic-ai/claude-code@2.1.270`, 진단 commit `97ecbf7abeb4` | `npm list -g --depth=0`, `claude doctor`; 팀 설치 방식은 미정 |
| 진단 | bundled search `OK`; 자동 업데이트 설정 `latest` | `claude doctor`의 읽기 전용 진단. 후보 MCP/Plugin/Skill의 동작 시험이 아님 |
| 진단 경고 | npm 글로벌 폴더에 쓰기 권한이 없어 자동 업데이트 불가 | 로컬 설치 유지관리 문제. 이번 조사에서 설치 방식이나 권한은 변경하지 않음 |

[Claude Code 공식 설치·검증 문서](https://code.claude.com/docs/en/setup#verify-your-installation)는 `claude --version`과 읽기 전용 `claude doctor`를 설치 확인 절차로 설명한다. [설정 범위 문서](https://code.claude.com/docs/en/settings)는 프로젝트 공유 `.claude/settings.json`과 개인·관리 설정을 구분한다. [MCP 문서](https://code.claude.com/docs/en/mcp)는 프로젝트 `.mcp.json`의 별도 승인과 연결 상태 확인을 설명한다. 이 문서들은 조사 출처이며 이 PC의 특정 후보 도구가 동작한다는 증거가 아니다.

이번 조사에서는 사용자·프로젝트의 설정 파일 내용, 인증 정보, MCP 서버 목록을 읽거나 수정하지 않았고 외부 연결·명령 실행 시험도 하지 않았다. 후보 도구가 정해지기 전에는 `documentation`, `format`, `standalone`, `combination`, 권한 변환·집행을 모두 `NOT_RUN`으로 둔다. 실제 릴리스에는 도구별 고정 버전·공식 출처·시험 단계와 결과·검증 담당·시각·독립 승인 해시가 필요하다.

## 2026-10-10 로컬 Skill 호출 파일럿

팀의 첫 지원 대상이 정해지기 전, 이 PC의 Claude Code `2.1.270`에서 Skill 호출 자체를 확인하려고 격리된 임시 프로젝트에 `.claude/skills/agentfit-pilot/SKILL.md`를 만들었다. Skill 본문에만 고유 응답 문자열을 넣고 `claude -p --tools '' --setting-sources project --strict-mcp-config '/agentfit-pilot'`를 실행했다. 약 65초간 표준 출력·오류 출력이 없어 호출을 중단했고, 임시 파일은 제거했다. 별도 `claude auth status --json` 조회에서 `loggedIn: true`가 반환됐지만, API 연결·요청 성공을 뜻하지 않는다. 네트워크·Skill 로딩 중 어느 단계에서 대기했는지는 확인하지 못했다.

따라서 이번 시도는 **Skill 형식 또는 단독 동작의 PASS 근거가 아니다.** Catalog 지원 행·설정 템플릿을 활성화하지 않고, `format`과 `standalone`은 계속 `NOT_RUN`으로 둔다. 다음 재시험은 실행 가능한 Claude Code 연결 환경에서 Skill 호출 결과와 종료 코드, 정확한 Client 버전, OS, 실행 시각을 함께 기록해야 한다. 실제 도구 후보 선정과 조합·권한 검증은 별도다.

## 2026-10-10 격리된 로컬 Skill 호출 재시험

같은 PC의 Claude Code `2.1.270`에서 임시 디렉터리의 `.claude/skills/agentfit-pilot/SKILL.md`에 무해한 고유 응답 문자열을 넣고, 프로젝트 설정만 읽도록 제한한 `claude -p '/agentfit-pilot' --model haiku --setting-sources project --strict-mcp-config --tools Skill --no-session-persistence`를 실행했다. 응답 JSON은 `is_error: false`, `terminal_reason: completed`, `result: AGENTFIT_PILOT_OK_20261010`을 반환했다. 별도 일반 텍스트 호출도 `PONG`으로 완료됐다. 이 환경에서는 Claude Code의 로컬 Skill 발견과 직접 호출이 가능하다는 **파일럿 결과**다. 이전의 무응답 시도와 달리 실행 제한이 없는 네트워크 환경에서 성공했으며, 그 시도의 정확한 대기 원인은 확정하지 않았다.

이 임시 Skill은 실제 Catalog 후보가 아니고 업무 Capability를 검증하지 않았다. 외부 Skill의 고정 버전·공식 출처·설정 산출물·권한·단독 업무 수행·다중 도구 조합 시험은 여전히 필요하다. 따라서 실제 도구의 `documentation`, `format`, `standalone` 및 조합 결과는 `PASS`로 바꾸지 않고, 릴리스도 활성화하지 않는다.
