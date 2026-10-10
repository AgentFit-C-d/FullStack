# Playwright MCP 조사 후보 — 2026-10-10

> **후보 조사 / AgentFit Catalog 승인 아님.** 팀의 첫 도구 목록과 지원 OS·Client 버전은 미확정이다. 이 파일은 단일 후보의 실행 가능성을 확인한 기록이며 `PASS` 지원 행, 권한 매핑, 설정 템플릿, 조합 검증의 근거가 아니다.

## 고정 조사 대상

| 항목 | 관찰값 |
| --- | --- |
| 도구 | Microsoft [`playwright-mcp`](https://github.com/microsoft/playwright-mcp) |
| npm 패키지 | `@playwright/mcp@0.0.83` (`npm view` 확인) |
| 출처 | `https://github.com/microsoft/playwright-mcp` (`package.json`의 repository) |
| 런타임 | 이 PC Node.js `v24.16.0`; 패키지 요구사항 `node >=18` |
| 종속 버전 | `playwright`/`playwright-core` `1.64.0-alpha-1790635538000` |
| 후보 Capability | `cap_browser_verification` — 실제 업무 수행 검증 전의 분류 후보 |
| Client 파일럿 | Windows의 Claude Code `2.1.270`; Claude Code 설정에는 등록하지 않음 |

[공식 README](https://github.com/microsoft/playwright-mcp#readme)는 Claude Code에 MCP 서버를 추가하는 방법과 Node.js 요구사항을 제시한다. 다만 README의 `@latest` 예시는 제품 Catalog에 그대로 쓰지 않고 고정 버전의 파일·명령·의존성을 검토해야 한다.

## 로컬 관찰

`npm install --prefix <격리 폴더> --ignore-scripts --no-audit --no-fund @playwright/mcp@0.0.83`으로 전역 설치·사용자 설정 변경 없이 패키지를 받았다. `node <격리 폴더>/node_modules/@playwright/mcp/cli.js --help`가 완료됐다. 같은 CLI의 stdio에 MCP `initialize` 요청을 보내 `serverInfo.name=Playwright`, `serverInfo.version=1.64.0-alpha-1790635538000` 응답을 받았고, `tools/list`에서 25개 도구를 확인했다. 목록에는 `browser_navigate`, `browser_snapshot`, `browser_click`과 `browser_run_code_unsafe`가 포함됐다.

이어 설치된 Microsoft Edge를 `--browser msedge --headless --isolated`로 실행했다. 제한된 샌드박스에서는 Edge의 임시 프로필 파일 쓰기가 실패했으나, 일반 실행 환경에서 MCP `browser_navigate`가 `data:text/html,...<h1>AgentFit Pilot</h1>`을 열었고 생성된 접근성 스냅샷에 `heading "AgentFit Pilot"`이 있었다. 외부 사이트나 사용자 파일은 열지 않았다.

Claude Code `2.1.270`에서도 사용자·프로젝트 MCP 설정을 수정하지 않고 임시 `--mcp-config`와 `--strict-mcp-config`로 같은 서버를 연결했다. 첫 호출은 Client의 도구 권한에서 거부됐다. 다음 호출에서 `browser_navigate`만 `--allowedTools`로 허용하자 Client가 페이지 제목을 반환했고, 같은 격리 폴더에 접근성 스냅샷이 생성됐다. 별도 `browser_snapshot` 호출은 허용하지 않아 거부됐다. 이 결과는 **해당 로컬 페이지에 한정된 Claude Code → MCP → Edge 탐색 파일럿 성공**이다. 테스트 파일과 설치 폴더는 조사 후 제거했다.

이 파일럿으로 패키지 시작·MCP 핸드셰이크·로컬 페이지 탐색을 확인했다. 실제 업무 페이지의 검증, 쓰기·외부 연결 권한, 인증/Secret, 제품 설정 템플릿, 실패 복구, 다른 도구와의 조합은 시험하지 않았다. 특히 `browser_run_code_unsafe`가 포함되므로 사용자에게 제시할 권한 범위와 허용 작업을 정하기 전에는 설정을 생성하거나 추천하지 않는다. 현재 이 후보의 **제품 Catalog 지원 행**은 `documentation`·`format`·`standalone` 모두 `NOT_RUN`으로 취급한다. 개별 파일럿 관찰을 제품 지원 범위 전체의 `PASS`로 승격하지 않는다.

## 실제 릴리스에 앞서 필요한 결정·시험

1. 팀이 이 도구와 정확한 npm 버전, Windows/Claude Code 버전을 1차 검증 대상으로 채택한다.
2. 설치된 패키지의 출처·배포물 해시·알파 의존성 허용 여부를 검토한다.
3. 격리된 프로젝트에서 Claude Code의 MCP 연결, 무해한 로컬 페이지 탐색·결과 확인, 실패/재시작을 기록한다.
4. 도구별 작업·resource 범위, 외부 연결·실행 권한, 상위 Client 정책과 Secret 처리 방식을 검토한다.
5. 설정·적용·복구 가이드를 고정 템플릿으로 작성하고 단독 및 전체 조합을 별도 검증한다. 독립 검토자가 릴리스 해시를 승인하기 전에는 활성화하지 않는다.
