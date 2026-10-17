# Docker 런타임 계약

## Core API 환경변수

| 변수 | 값 |
|---|---|
| `AGENTFIT_AI_SERVICE_ANALYSIS_URL` | `http://ai-service:8001/internal/v1/analysis` |
| `AGENTFIT_AI_SERVICE_NEEDS_URL` | `http://ai-service:8001/internal/v1/skills/needs` |
| `AGENTFIT_AI_SERVICE_RANK_URL` | `http://ai-service:8001/internal/v1/skills/rank` |
| `AGENTFIT_HMAC_CURRENT_KEY_ID` | `.env.docker`의 현재 key id |
| `AGENTFIT_HMAC_CURRENT_SECRET` | `.env.docker`의 현재 HMAC secret |

## AI service 환경변수

| 변수 | 값 |
|---|---|
| `AGENTFIT_HMAC_CURRENT_KEY_ID` / `AGENTFIT_HMAC_CURRENT_SECRET` | Core와 동일한 HMAC 값 |
| `AGENTFIT_AI_V2_PROVIDER` | 기본 `deterministic`, Solar smoke 시 `solar` |
| `UPSTAGE_API_KEY` | `.env.docker`에서만 제공하는 Solar key |

## 비밀값 규칙

- `.env.docker`는 Git ignored 파일이며 실제 비밀값을 가진다.
- `.env.docker.example`, Compose, Dockerfile, 테스트 fixture에는 변수 이름과 placeholder만 둔다.
- `docker compose config` 출력, 애플리케이션 로그, Audit, API 응답에 secret 전체를 출력하지 않는다.
- Solar smoke는 작은 텍스트 입력 하나로 실행하며, 키가 없으면 Solar smoke는 실패해야 하고
  결정적 테스트는 여전히 실행 가능해야 한다.
