# 데이터 모델 및 상태

## 소유권과 보관 정책

| 데이터 | 소유자 | 정책 |
|---|---|---|
| 원문 PDF/Markdown/Text bytes·전체 추출문 | Core 분석 처리 | terminal 상태 직후 폐기, 로그·DB·재조회 경로 금지 |
| 분석 시도 | Core A | requestId, generation, deadline, 계약 버전, 상태, 최소 오류 코드만 보관 |
| 구조화 분석 결과 | Core A | DRAFT로 기존 profile/review에 검증 후 저장; CONFIRMED 생성 뒤에도 DRAFT 유지, 원문·전체 추출문·정상 원시 모델 응답 제외 |
| 사용자 확인 snapshot | Core A | 불변 confirmation event |
| 최종 분석 실패 진단 | Core A | 실제 수신한 최종 실패 응답만 암호화 1건/시도·최대 7일; 원문·미수신·정상 응답·일반 로그·DTO·Audit 제외 |
| Capability claim·질문 | Core B | 확인 snapshot basis로 추천 생성 시 저장 |
| 추천·항목·notice | Core B | 승인 Catalog release/hash와 저장 |
| AI needs/rank DTO | AI↔Core 일시 데이터 | rank는 B 담당자 결정 전 영속 추천 데이터에 저장하지 않음 |

## 논리 엔터티

### AnalysisAttempt

기존 Core 엔터티를 사용한다. 상태는 `PROCESSING → SUCCEEDED|FAILED|EXPIRED`다. AI 호출은 DB 트랜잭션 밖에서 실행하며 저장 직전 project 소유권·generation·deadline·상태를 재검증한다.

### TransientAnalysisInput

영속 엔터티가 아니다. `{kind, displayName, bytes, byteSize, deadline}`만 분석 terminal 상태까지 가진다. 정리 함수는 성공·공급자 실패·계약 실패·취소·예외에서 모두 실행된다. 구현 전 `document_summary`가 새 분석 경로에 원문 또는 재조회 위치를 남기지 않는지 감사한다.

### DraftAnalysisResult와 FailureDiagnostic

구조화 분석 결과는 A DRAFT로 남고 사용자 확인 전에도 수정·재조회할 수 있다. 사용자의 전체 확인은 CONFIRMED Profile과 불변 confirmation event를 새로 만들며 DRAFT를 삭제하지 않는다. 최종 분석 실패에서 실제 응답을 수신한 경우에만 `FailureDiagnostic`을 암호화해 시도당 최대 하나 저장하고, 최초 수신 시각부터 최대 7일 안에 삭제한다.

### ConfirmedAnalysisSnapshot

사용자 확인 뒤 A가 제공하는 불변 입력이다. B에는 허용 field path, 구조화 값, 관련 pending, review·developer·environment version만 제공하고 원문은 포함하지 않는다.

### CapabilityAssessment

기존 B 형식을 유지한다.

```text
claims[]: capabilityKey, need(REQUIRED|OPTIONAL|UNDETERMINED), reason, sourceFields[]
questions[]: field, reason
```

허용 key는 `cap_document_reference`, `cap_repository_navigation`, `cap_code_review`, `cap_test_authoring`, `cap_test_execution`, `cap_browser_verification`, `cap_api_verification`, `cap_database_schema_inspection`, `cap_ai_evaluation`이다. 알 수 없는·중복 key, 허용되지 않은 source field, 근거 없는 REQUIRED/OPTIONAL, 형식 오류는 `FAILED`다. 유효한 `UNDETERMINED`와 질문은 `NEEDS_INFORMATION`이다.

### SkillNeedsResult와 SkillRankResult

`needs`는 최대 6개의 `{id, name, query, sourceFields}`를 만들고 Capability mapper의 입력으로만 쓴다. `rank`는 최대 15개 후보의 `{candidateId, needIds, score(0..100), reason}`을 반환한다. B 담당자 승인 전에는 rank 결과로 추천 상태나 항목을 바꾸지 않는다.

## B 결과 상태

| 상태 | 의미 | 항목 |
|---|---|---|
| `RECOMMENDED` | 승인 Catalog 조합이 REQUIRED Capability를 충족 | 1개 이상 가능 |
| `NO_ADDITIONS_NEEDED` | 기존 검증 도구로 충분 | 0개 |
| `NEEDS_INFORMATION` | 판단을 바꿀 미정과 유효 질문 | 0개, 질문 1개 이상 |
| `NO_COMPATIBLE_TOOLS` | 정보는 충분하지만 검증된 조합 없음 | 0개, 사유 필수 |
| `FAILED` | AI·네트워크·timeout·계약·저장·버전 오류 | 정상 추천으로 저장하지 않음 |

새 추천이 실패해도 이전 성공 추천과 basis는 보존한다.
