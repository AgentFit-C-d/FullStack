# AgentFit demo web

`D:/capstone/Frontend`의 React/Vite 화면을 커밋 `5893007` 기준으로 `demo` 브랜치에 복사한 소스입니다. 원본 저장소의 `/demo` 메모리 기반 시연은 유지하고, `/app`에 실제 Core API 연동 화면을 별도로 추가했습니다. 프론트 원본 저장소에는 변경을 기록하지 않았습니다.

루트 `D:/capstone/FullStack`에서 Docker Desktop을 켠 뒤, Git에서 제외된 `env.docker`를 준비하고 실행합니다.

```powershell
docker compose --env-file env.docker -f infra/compose.fullstack.yaml -f infra/compose.demo.yaml -f infra/compose.web-demo.yaml up --build -d
```

`http://localhost:8080`에서 GitHub 로그인으로 진입합니다. `/app`은 같은 출처 `/api` 프록시를 사용하므로 세션·CSRF 쿠키가 브라우저에서 함께 전송됩니다. OAuth 앱에 등록한 콜백은 `http://localhost:8080/api/login/oauth2/code/github`이어야 합니다. Core API 직접 접근은 `localhost:18080`, AI는 `localhost:8001`, PostgreSQL은 `localhost:5434`입니다.

이 데모 Compose는 GitHub OAuth 요청을 `read:user`로 제한합니다. 기존 공통 설정의 `user:email` 요청은 데모 로그인에 필요하지 않아 덮어씁니다. 앱에 대한 GitHub 동의 자체는 여전히 필요합니다.

화면에서는 프로젝트 생성, 자료 저장, 비동기 분석 상태 조회, DRAFT 확인, 역할·환경 저장, 추천, 설정 미리보기·승인·ZIP 다운로드를 시도할 수 있습니다. AI 공급자는 `env.docker`의 설정을 따릅니다. 데모 Catalog는 합성 데이터이며, 생성된 파일은 실제 설치 지침으로 사용하지 않습니다. 로그인 후 실제 사용자 흐름은 GitHub OAuth 콜백과 계정 설정에 의존합니다.

로컬 검증: `npm ci`, `npm test`, `npm run lint`, `npm run build`. 비밀키를 `VITE_` 변수에 넣지 마세요.
