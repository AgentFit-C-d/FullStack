# 합성 카탈로그 데모

`catalog-synthetic`은 B 추천·미리보기·승인·다운로드 경로를 연결 시연하기 위한 **가짜 데이터**입니다. `PASS`와 근거 URL도 시연용 값이며 Claude Code 또는 도구의 실제 호환성을 검증했다는 뜻이 아닙니다. 생성된 설정은 설치하지 마세요.

`infra/compose.demo.yaml`을 기본 Compose 파일 뒤에 지정할 때만 이 카탈로그가 로드됩니다. 환경 입력은 `WINDOWS` / `demo-claude-code` / `demo-1`을 사용합니다. 일반 `infra/compose.fullstack.yaml`에는 이 카탈로그를 연결하지 않습니다.

AI의 실제 needs 응답이 모든 Capability를 미정으로 판단하면 추천 결과는 `NEEDS_INFORMATION`이 정상입니다. `RECOMMENDED`를 시연하려면 AI 계약에 따른 충분한 기획 정보 또는 명시적인 테스트 응답이 필요합니다. 이 카탈로그는 AI 판단을 조작하지 않습니다.

시연용 단일 도구는 9개 Capability를 모두 보유한다고 **가정**합니다. 확정 기획의 기능 항목 하나에 `문서, repo 코드 탐색, 코드 검토, 테스트 작성 및 실행, 브라우저 검증, API 연동, DB 점검, AI 모델 평가`를 넣으면 deterministic AI가 9개를 모두 필수로 분류하는 흐름을 확인할 수 있습니다. 실제 제품 추천 품질이나 도구 호환성을 의미하지 않습니다.
