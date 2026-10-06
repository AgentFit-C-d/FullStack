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
