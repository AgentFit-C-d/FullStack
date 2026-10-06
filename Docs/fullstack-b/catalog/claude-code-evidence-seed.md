# Claude Code Catalog 근거 조사 시작점

> 2026-10-06 공식 문서 확인. **실제 Catalog 릴리스 아님.** 아래는 구성 종류별 조사 출처이며 특정 도구·버전·OS·권한 매핑의 `PASS` 판정이나 실행 시험 결과가 아니다. 1차 실제 Catalog는 Claude Code부터 만들고 Codex는 후속으로 둔다.

| 조사 대상 | 공식 문서에서 확인한 범위 | Catalog에 넣기 전 필요한 검증 |
| --- | --- | --- |
| 설치/버전 | [`claude --version`, `claude doctor` 확인 절차](https://code.claude.com/docs/en/setup#verify-your-installation) | 팀이 고를 정확한 Claude Code 버전과 OS별 실행 환경·재현 기록. 사용자 선언 버전과 실제 검사 결과는 구분 |
| 공유 프로젝트 설정 | [`.claude/settings.json`과 개인/관리 설정의 범위·우선순위](https://code.claude.com/docs/en/settings) | 고정 템플릿의 키·경로·적용 범위·상위 정책 충돌·예상 효과를 해당 버전에서 시험. 개인 설정을 팀 파일로 무단 복사하지 않음 |
| 권한 규칙 | [`deny` → `ask` → `allow` 우선순위와 실제 집행 주체](https://code.claude.com/docs/en/permissions) | B의 `DENY`/`ASK_EACH_TIME`/`ALWAYS_ALLOW` 각각을 **구체적 도구·작업·scope**에 어떻게 변환할지 검토하고 실제 Client에서 확인. 상위 설정·관리 정책 때문에 최종 유효 권한을 단정하지 않음 |
| MCP | [프로젝트 `.mcp.json`, 범위와 프로젝트 서버 승인](https://code.claude.com/docs/en/mcp) | 선정한 MCP 서버별 배포 버전·명령/주소·인증 요구·Secret 배제·OS별 연결/도구 실행 확인. `.mcp.json` 존재만으로 연결 성공이라 하지 않음 |
| Skills | [Skill 위치와 `SKILL.md` 형식](https://code.claude.com/docs/en/skills) | 후보 Skill별 자연어 내용·파일 위치·호출 확인. 외부 출처/버전/의존성 및 Hook·명령 포함 여부 별도 심사 |
| Plugins | [manifest와 포함 가능한 Skill/Agent/Hook/MCP 컴포넌트](https://code.claude.com/docs/en/plugins) | Plugin 배포 단위와 포함 컴포넌트 목록 확인, 독립 도구와 중복 집계 금지, 정확한 설치·충돌 조합 시험 |
| Hooks | [Hook 실행 지점과 설정 참조](https://code.claude.com/docs/en/hooks) | 실행 코드는 LLM 생성물이 아닌 검토된 고정 템플릿만 사용. 사용자 선택·권한·OS/버전·실패·복구를 시험하기 전에는 활성화하지 않음 |

## Catalog 등록 전 증거 묶음

각 후보 도구마다 `toolKey`·고정 버전·출처 URL·확인 시각/담당, 배포 단위와 포함 컴포넌트, Capability 근거, 필요한 런타임·인증·Secret 처리, 설치/복구 안내를 정리한다. 이후 **정확한 OS + Client ID + Client 버전**마다 문서 확인(`documentation`), 생성 파일 형식 확인(`format`), 단독 동작 확인(`standalone`)을 별도로 기록한다. 현재 세 검사는 모두 `NOT_RUN`이다. 문서 URL이 있다는 사실만으로 `format` 또는 `standalone`을 `PASS`로 만들지 않는다.

두 도구 이상을 추천·선택하려면 단독 성공과 별도로 **그 정확한 전체 집합**의 의존성·포함관계·충돌·권한 결과를 시험하고 조합 근거를 남긴다. 권한 매핑도 도구별로 실제 Claude Code 규칙과 외부 인증 범위를 확인한다. 검증 결과로 고정 파일·manifest SHA-256을 만들고, 팀이 승인한 hash를 독립적으로 제공한 다음에만 릴리스를 활성화한다. 서버의 해시 검증은 이 인간 검토·실행 검증을 대체하지 않는다.

현재 남은 입력: 첫 Claude Code 시험 버전과 OS, 팀이 검증할 후보 도구 목록, 시험 담당/환경, Catalog 승인 담당. 이 정보가 없으므로 실제 지원 레코드와 설정 템플릿은 아직 작성하지 않는다.
