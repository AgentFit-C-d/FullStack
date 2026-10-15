# AgentFit Core API

Spring Boot service for the AgentFit core domain.

## Responsibilities

- Own project, profile, recommendation, configuration, and audit data.
- Expose frontend-facing APIs.
- Call `ai-service` over HTTP for AI extraction and recommendation support.

## Package structure

```text
com.agentfit.coreapi
├─ global
│  ├─ config       # 보안·세션 등 공통 설정
│  ├─ exception    # ErrorCode·공통 예외·GlobalExceptionHandler
│  └─ health       # 공통 health endpoint
└─ domain
   ├─ auth        # OAuth 로그인·세션·사용자 저장소
   ├─ project     # controller / dto / entity / service / repository / exception
   ├─ analysis    # AI 호출·HMAC 계약
   └─ audit       # 감사 이벤트
```

도메인별 예외는 `BusinessException`의 `ErrorCode`를 사용하고,
공통 응답 변환은 `global.exception.GlobalExceptionHandler`가 담당합니다.

## Run

```powershell
mvn spring-boot:run
```

## Test

```powershell
mvn test
```
