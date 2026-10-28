# Claude Code 2.1.310용 실물 Skill 로컬 검토 — 2026-10-27

## 검토 범위와 결과

- 대상: Windows, Claude Code CLI `2.1.310`, skills.sh의 `obra/superpowers/test-driven-development` 고정 파일 (`SHA-256 8e8772822d25e4dfc9295cf4526c6251a10458c7417ac50c4b42e5d9fc91e0b0`).
- 격리된 임시 Node.js 프로젝트에서 Claude Code가 Skill을 읽고 테스트를 먼저 실패시킨 후 구현·재실행하여 2개 테스트가 통과했다. 기존 2.1.270 검토와 파일 해시는 같다.
- 이 증거로 `cap_test_authoring`만 로컬 검토했다. AI가 `Write tests`에서 함께 요구하는 `cap_test_execution`은 이 Skill의 독립 제공 기능으로 인정하지 않는다. 따라서 추천·Preview에는 미충족 경고가 남는다.
- 별도 Java/Maven 파일럿은 기초 테스트 1개 통과까지 확인했다. 이후 Claude API 재시도가 반복되어 Skill 사용 호출을 중단했다. Java에서 Skill이 동작했다는 근거로 사용하지 않는다.
- 다른 Skill 후보(`addyosmani/agent-skills/test-driven-development`)의 Node.js 파일럿도 성공했으나 Java 범위와 권한 검토가 끝나지 않아 카탈로그에 등록하지 않았다.

## 새 검토 후보

`catalog-candidates/obra-tdd-windows-claude-2.1.310-reviewed`는 기존 2.1.270 릴리스를 덮어쓰지 않는 별도 검토 후보이다. 릴리스 해시는 `9789c48f125593b80b0c3d3b0f005e781312222149996e815c68a522de56964f`이다. 지원 행은 정확히 `WINDOWS / claude-code / 2.1.310` 하나이다. 지원 버전이나 파일 내용이 달라지면 새 검토가 필요하다.

이 후보를 운영 카탈로그로 지정하지 않았다. 팀의 별도 검토자가 고정 파일, MIT 고지, 편집·테스트 실행 권한 안내, 버전별 실행 증거 및 해시를 확인해야 한다. 승인 후 서버의 카탈로그 경로와 승인 해시를 함께 바꾸고, 실제 AI 응답→추천→권한→Preview→ZIP을 다시 검증한다.

skills.sh의 [공식 API](https://www.skills.sh/docs/api)는 OIDC 인증이 필요하다. 현재 공개 검색 경로는 후보 수집에만 사용하며, 검색 결과를 승인 카탈로그로 자동 승격하지 않는다.
