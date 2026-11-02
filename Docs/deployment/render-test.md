# Render 배포 테스트

`chore/render-deployment-test` 브랜치를 기준으로 `render.yaml` Blueprint를 사용한다.

## 2026-11-02 배포 상태

- [웹](https://agentfit-test-web.onrender.com): `live`, `/health` 200, `/app` 200
- [Core API](https://agentfit-test-core.onrender.com): `live`, `/health` 200, Flyway v10 적용
- [AI](https://agentfit-test-ai.onrender.com): `live`, `/health` 200, Solar 키 설정
- [PostgreSQL](https://dashboard.render.com/d/dpg-dbk18lec6qcc73d7sbqg-a): `available`, 무료 DB 만료 예정 2026-12-02
- Web의 `/api/auth/get-session` 200, `/api/auth/csrf` 204를 확인했다.

GitHub OAuth Client ID와 Secret은 별도 Render용 앱을 만들 때까지 자리 표시자다. 따라서 아직 로그인과 인증 후 전체 흐름은 검증하지 않았다. 두 값을 Core 서비스의 Environment에서 교체하고 재배포해야 한다.

## 구성

- `agentfit-test-web`: React 정적 파일과 `/api/` HTTPS 프록시
- `agentfit-test-core`: Spring Boot API, Flyway, 분석 작업 워커
- `agentfit-test-ai`: FastAPI, Solar 분석 및 내부 HMAC 경로
- `agentfit-test-db`: PostgreSQL

네 서비스는 싱가포르 리전의 무료 플랜으로 선언했다. Core와 AI는 각각 공개 URL을 사용하지만, 내부 AI API 요청에는 동일한 HMAC 키가 적용된다. 브라우저는 Web URL만 사용한다.

## 최초 연결

1. GitHub에서 Render용 별도 OAuth App을 만든다. Authorization callback URL은 `https://agentfit-test-web.onrender.com/api/login/oauth2/code/github`로 지정한다.
2. Render Core 서비스의 Environment에서 `GITHUB_CLIENT_ID`와 `GITHUB_CLIENT_SECRET`의 자리 표시자를 새 앱의 값으로 교체하고 배포한다. 값을 Git이나 문서에 넣지 않는다.
3. 로그인 후 프로젝트 생성 → 기획서 저장 → 분석 → 확정 → 추천 → 설정 ZIP 경로를 순서대로 확인한다.

## 데이터와 운영 범위

- Render PostgreSQL은 로컬 Docker 데이터와 별개다. Flyway가 새 DB의 스키마를 만든다.
- `render.yaml`의 DB 접속 정보는 Render 참조로 주입된다. Core 시작 스크립트가 PostgreSQL 접속 URL을 JDBC 형식으로 변환한다.
- 무료 Web 서비스는 유휴 상태에서 멈추고 첫 요청 때 다시 시작할 수 있다. 무료 DB에는 보존 기간 제한이 있으므로 이 환경을 영구 데이터 저장소로 사용하지 않는다.
- `render.yaml`에 적힌 서비스 이름으로 공개 주소를 예상했다. 실제 생성된 Render URL이 다르면 Web 프록시, Core AI URL, 웹 Origin, GitHub 콜백을 새 URL에 맞춰 변경한다.
