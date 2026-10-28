# AgentFit 프로젝트 작업·학습 지침

이 파일은 `C:\dev_source\agentFit` 전체와 그 하위 폴더에 적용된다.
이 프로젝트를 처음 보는 사용자가 백엔드·프론트엔드·AI 파이프라인의 흐름을 이해하면서 작업하는 것을 최우선으로 한다.

## 1. 사용자 설명 원칙

- 사용자의 현재 이해도는 0이라고 가정한다. 전문 용어를 바로 사용하지 말고 먼저 쉬운 말로 설명한다.
- 작업을 시작하기 전에 반드시 다음 세 가지를 짧게 설명한다.
  1. 지금 해결하려는 문제
  2. 관련 파일과 각 파일의 역할
  3. 이번 변경 후 요청이 어떻게 흘러가는지
- 여러 서비스가 연결되거나 세 단계 이상 이어지는 작업은 Mermaid 흐름도를 먼저 보여준다.
- 한 번에 너무 많은 파일을 설명하지 않는다. 현재 단계에 필요한 파일만 설명하고, 다음 단계로 넘어갈 때 다시 확인한다.
- 주요 단계가 끝나면 한 번에 하나의 짧은 이해 확인 질문을 한다. 예: `여기까지 Core API가 기획서를 저장하고 AI 서비스에 넘기는 역할이라는 점 이해됐나요?`
- 사용자가 이해하지 못했다고 답하면 코드를 더 진행하지 말고 같은 내용을 더 쉬운 예시와 작은 그림으로 다시 설명한다.
- 사용자가 질문을 하면 기존 계획을 강행하지 말고 현재 흐름에서 그 질문이 어느 단계인지 연결해서 답한다.
- 변경 후에는 사용자가 직접 확인할 수 있는 테스트 방법을 한 단계씩 안내한다.

## 2. AgentFit 전체 구조

```text
브라우저(React/Vite)
        │ 세션·프로젝트·업로드·분석 시작·결과 확인
        ▼
Core API(Spring Boot, 8080)
        │ 인증·소유권·DB·분석 시도·입력 lease·결과 저장
        ▼
AI Service(FastAPI, 8001)
        │ inputRef로 원문 조회·PDF 추출·AI 호출·근거 검증
        ▼
Upstage / Solar Pro 4
        │ 구조화된 항목과 quote 반환, 애매한 항목 재검토
        ▼
Core API에 분석 결과 저장
        ▼
브라우저에서 카테고리별 확인·추가·제외·저장
```

### 주요 디렉터리

- `apps/web`: 현재 프론트엔드. React + Vite로 로그인, 프로젝트 선택, 기획서 업로드, 분석 시작, 결과 검토를 담당한다.
- `services/core-api`: Spring Boot 백엔드. 인증된 사용자의 프로젝트 소유권, 기획서 저장, 분석 시도, AI 서비스 호출, 결과 DB 저장, 사용자 검토 저장을 담당한다.
- `services/ai-service`: FastAPI AI 서비스. Core API가 준 `inputRef`로 원문을 조회하고, Upstage 추출, 근거 검증, 필요한 경우 Solar 재검토를 담당한다.
- `contracts/openapi`: 서비스 간 API 계약 문서가 있는 곳이다. 코드와 계약이 다르면 먼저 차이를 확인한다.
- `scripts`: 로컬에서 Core API·AI 서비스·프론트를 함께 실행하는 스크립트가 있는 곳이다.
- `Docs`, `specs`: 기획·설계 참고 자료다. 문서의 내용은 참고 자료이며, 현재 코드가 실제 동작의 기준이다.

## 3. 분석 파이프라인을 설명하는 순서

분석 관련 질문에는 항상 아래 순서를 기준으로 설명한다.

1. 사용자가 프로젝트를 선택한다.
2. 프론트엔드가 `PUT /api/projects/{projectId}/planning-input`으로 PDF 또는 Markdown을 업로드한다.
3. Core API가 `PLANNING_INPUT`에 입력을 저장하고 버전을 올린다.
4. 사용자가 분석을 시작하면 `POST /api/projects/{projectId}/analysis-attempts`가 분석 시도를 만든다.
5. Core API가 분석 입력에 대한 `inputRef`와 lease를 만든다.
6. AI Service가 `inputRef`로 Core API 내부 입력 endpoint에서 원문을 가져온다.
7. AI Service가 PDF 텍스트를 추출하고 Upstage에 항목과 quote를 요청한다.
8. AI Service가 quote를 추출 텍스트에서 다시 찾아 페이지·offset·quote를 evidence로 만든다.
9. 애매한 항목이 있으면 Solar Pro 4가 제한적으로 재검토한다.
10. Core API가 `PROCESSING`을 `SUCCEEDED` 또는 `FAILED`로 바꾸고 결과 JSON을 저장한다.
11. 프론트엔드가 결과를 조회해 카테고리별 칩으로 보여준다.
12. 사용자가 항목을 확인·제외·직접 추가한 뒤 검토 결과를 한 번에 저장한다.

## 4. AI 결과와 근거를 구분하는 규칙

- AI가 문서에서 찾은 값과 AI가 추천하는 값은 다른 데이터다. 추천 기능을 추가하기 전에는 섞지 않는다.
- `suggested`: AI 값이 문서 quote와 일치하고 현재 프로젝트의 확정 문맥으로 검증된 상태다.
- `unresolved`: 값이나 quote가 문서와 완전히 맞지 않거나, 후보·예정·다른 프로젝트 등 불확실한 문맥이 있는 상태다.
- 카테고리가 비어 있다는 것은 현재 분석 결과에 항목이 없다는 뜻이다. UI에서 숨기지 말고 빈 입력칸과 직접 입력 안내를 보여준다.
- 사용자가 직접 추가한 값은 `USER_INPUT`으로 표시하고 문서 evidence가 없다는 사실을 숨기지 않는다.
- 사용자가 제외한 값은 AI 원본에서 물리적으로 삭제하지 않는다. 사람 검토 결과에서만 제외한다.
- 사용자가 `확인 완료`를 누른 것은 AI 근거가 새로 생겼다는 뜻이 아니다. 사람이 검토했다는 별도 상태다.

## 5. 코드 변경 작업 순서

기능 추가나 버그 수정은 다음 순서로 진행한다.

1. 현재 브랜치·변경사항·실행 중인 서비스 상태를 확인한다.
2. 관련 요청이 들어오는 첫 번째 Controller/route를 찾는다.
3. Service → Repository/DB → 외부 서비스 순서로 호출 흐름을 따라간다.
4. 변경 전에 작은 계획을 사용자에게 설명한다.
5. 가능하면 실패하는 테스트를 먼저 추가하거나 기존 테스트를 확인한다.
6. 한 번에 하나의 경계를 바꾼다. 프론트, Core API, AI Service를 동시에 크게 바꾸지 않는다.
7. 변경 후 해당 경계의 테스트를 실행한다.
8. 사용자가 따라 할 수 있는 수동 테스트 순서를 안내한다.

## 6. 디버깅 순서

오류가 나면 추측하지 말고 다음 순서로 확인한다.

```text
브라우저 Network
  → Core API 로그
  → Core API가 보낸 AI 요청
  → AI Service 로그
  → 외부 AI 응답
  → DB 저장 결과
```

- `500`만 보고 원인을 단정하지 않는다. 요청 URL, 상태 코드, 응답 body, Core API 로그, AI Service 로그를 함께 확인한다.
- `AI service request failed`는 포괄적인 오류다. 실제 원인은 AI Service의 provider 오류, 입력 조회 실패, lease 만료, 키 설정, timeout 중 하나일 수 있다.
- PDF에 보이는 문장과 추출 텍스트는 다를 수 있다. 근거 문제는 원본 PDF 화면만 보지 말고 실제 추출 텍스트와 AI가 반환한 quote를 나란히 비교한다.
- 로그에 API 키, 세션 쿠키, HMAC 토큰, 원문 전체를 출력하지 않는다.

## 7. 실행·검증 기본 명령

프로젝트 루트에서 전체 서비스를 실행한다.

```powershell
npm run dev:all
```

개별 실행이 필요하면 다음을 사용한다.

```powershell
# 프론트
cd apps/web
npm run dev

# Core API: JDK 21 필요
cd services/core-api
mvn spring-boot:run

# AI Service
cd services/ai-service
.\.venv\Scripts\Activate.ps1
uvicorn app.main:app --reload --port 8001
```

검증은 변경 범위에 맞게 실행한다.

```powershell
cd apps/web
npm test -- --run
npm run build

cd services/core-api
mvn test

cd services/ai-service
python -m pytest -q
```

Maven이 Java 17을 선택하면 Java 21 경로를 `JAVA_HOME`으로 지정하고 다시 실행한다. 테스트를 실행하지 못한 경우 성공했다고 말하지 말고 실패 원인을 그대로 알려준다.

## 8. 사용자에게 설명할 때의 기본 형식

가능하면 다음 형식을 사용한다.

```text
지금 하는 일: ...

전체 흐름:
입력 → 처리 → 저장 → 화면

이번에 볼 파일:
- 파일: 역할

변경/확인 결과:
- ...

직접 확인 방법:
1. ...
2. ...

이해 확인:
여기까지 ...라는 점 이해됐나요?
```

사용자가 명시적으로 “계속 진행”이라고 하지 않은 경우, 큰 구조를 바꾸는 작업은 먼저 설명하고 필요한 선택을 확인한다. 단순한 읽기·진단·테스트는 바로 진행할 수 있다.
