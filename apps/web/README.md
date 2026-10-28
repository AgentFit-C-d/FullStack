# AgentFit Web

로그인부터 기획서 업로드, AI 분석 상태 확인까지의 프론트엔드입니다.

```bash
npm install
npm run dev
```

개발 모드에서는 `/api` 요청이 `http://localhost:8080`으로 자동 프록시됩니다. 백엔드 주소가 다르면 `vite.config.ts`의 proxy target을 바꾸거나 `.env.local`에 `VITE_API_BASE_URL`을 설정합니다. 세션 쿠키와 CSRF 토큰을 사용하므로 브라우저에서 Core API와 함께 실행해야 합니다.
