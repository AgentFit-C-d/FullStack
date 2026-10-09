# 풀스택 B ↔ A·AI 연동 계약 제안

> 상태: **제안 / 미합의**. 2026-10-06 현재 A·AI의 실제 연동 계약·구현은 이 레포에서 확인되지 않았다. 아래 필드와 흐름은 B의 구현 경계를 연결하기 위한 검토안이며, 공개 API나 AI 응답 형식을 확정했다는 뜻이 아니다. **1차 실제 Catalog는 Claude Code부터, Codex는 후속**이라는 범위를 사용자와 확인했다. 정확한 버전·OS·지원 기능은 검증 전까지 활성화하지 않는다.

## 소유권과 호출 순서

1. A는 인증 사용자와 프로젝트 소유권을 확인하고 현재 확정된 Profile, Developer, Environment 및 버전·확인 이벤트를 하나의 일관된 스냅샷으로 읽는다. 미확정 Profile과 다른 프로젝트 데이터는 B 입력에서 제외한다.
2. AI는 A가 허용한 최소 입력으로 고정 9개 Capability의 필요도·근거·추가 질문 **후보**를 만든다. AI는 Catalog ID, 설치 완료, 권한 적용, 추천 성공을 판정하지 않는다. FastAPI는 Spring의 PostgreSQL에 직접 접근하지 않는다.
3. B는 AI 응답의 계약 버전·구조·값을 검증하고, A가 제공한 허용 필드 경로와 대조한다. 검증 실패나 AI 장애는 성공한 빈 Capability 목록으로 바꾸지 않는다. B가 승인된 Catalog와 사용자 환경을 대조해 네 가지 추천 결과를 결정한다.
4. A는 추천·선택·Preview·승인·생성 메타데이터를 소유권 검사와 트랜잭션 경계 안에서 저장한다. B는 저장된 추천·현재 스냅샷과 **A가 별도로 조회한 현재 환경 대상**을 요청과 대조한 후에만 Preview를 만들고, 저장된 승인·Preview와 현재 스냅샷을 다시 대조한 후에만 ZIP을 만든다.

## A → B: 신뢰된 현재 스냅샷

| 구분 | 최소 필요 정보 | 사용 이유 |
| --- | --- | --- |
| 소유권 | 인증 사용자 ID, 프로젝트 ID, 현재 소유권 검사 결과 | 직접 API 요청과 하위 리소스 접근 차단. 브라우저가 보낸 사용자/프로젝트 관계를 신뢰하지 않음 |
| 기획 | CONFIRMED Profile ID·버전, 확인 이벤트 ID, Project 버전, Review 버전, AI 근거로 허용할 기획 필드 값·경로 | 초안 혼입 방지, 근거 검증, 변경 시 추천·Preview 무효화 |
| 역할 | Developer Profile의 현재 값·버전·유효 상태 | 역할별 Capability·추천과 변경 감지 |
| 환경 | Environment Profile의 현재 값·버전·유효 상태, OS·Client·Client 버전, 선언된 설치 구성 및 버전 또는 미확인 표시 | 정확한 지원 대상 판정. 사용자 진술은 실제 로컬 검증과 구분 |
| Catalog | 서버가 검토·활성화한 release ID와 독립 승인 SHA-256 | 클라이언트 제공 해시나 단순 manifest 존재만으로 활성화하지 않음 |
| 저장 추천 | 프로젝트에 속한 추천 ID, 네 가지 상태, 생성 당시 전체 basis, 제공한 도구 키 | Preview에 현재 RECOMMENDED 결과의 도구만 사용 |
| 설정 흐름 | 프로젝트에 속한 Preview·승인 메타데이터, fingerprint·만료·선택·정책·basis | 승인 및 ZIP 재생성 시 신선도·내용 일치 확인 |

현재 B의 `PreviewBasis`는 `projectId`, `projectVersion`, `confirmedProfileId`, `confirmedProfileVersion`, `confirmationEventId`, `reviewVersion`, `developerVersion`, `environmentVersion`, `catalogReleaseId`, `catalogHash`를 요구한다. 필드 의미와 증가 시점은 A가 DB 모델에 맞춰 확인해야 한다. A는 Preview 생성·승인·내보내기 직전 **현재** 값을 재조회해야 한다. Project 삭제 시 B 메타데이터·감사·사용자 보고도 함께 삭제한다.

## AI ↔ Spring: Capability 후보

요청은 A가 허용한 기획·역할·환경 필드만 포함한다. Secret, 기존 설정 원문, 문서 원문·추출문 전체, 다른 사용자의 정보는 전달하지 않는다. 요청 ID와 계약/스키마 버전을 양쪽 로그에 연결하고 모델·프롬프트 버전, 지연·오류 유형을 관측한다. 재시도 가능성과 중복 방지는 분석 작업 ID 또는 idempotency key의 소유자를 정한 뒤 확정한다.

성공 응답에서 B가 받으려는 최소 의미는 다음과 같다.

| 필드 의미 | 제약 |
| --- | --- |
| Capability claim | 고정 9개 키 중 하나, `REQUIRED` / `OPTIONAL` / `UNDETERMINED`, 비어 있지 않은 이유, 근거가 된 A 허용 필드 경로 목록 |
| 추가 질문 | A가 보유한 허용 필드 경로와 이유. 질문을 사용자에게 보여줄 주체·저장 위치는 합의 필요 |
| 처리 메타데이터 | 요청 ID, 계약/스키마 버전, 모델·프롬프트 버전 및 실패 식별 정보. 실제 응답 DTO는 AI·A와 합의 필요 |

`AiCapabilityIntake`는 현재 빈 claim 목록(질문만 있는 경우 포함), 중복·미지 키, 허용되지 않은 근거/질문 경로, 근거 없는 필수·선택 claim을 거부한다. **9개 키를 모두 반환해야 하는지, 생략된 키가 어떤 뜻인지, `UNDETERMINED`의 질문 연결 방식은 아직 미합의**다. 이 의미를 정하기 전에는 응답의 생략을 `OPTIONAL`이나 `NOT_NEEDED`로 해석하지 않는다. 오류, 타임아웃, 파싱 실패, 계약 버전 불일치에는 추천 성공 레코드를 만들지 않고 기존 결과를 보존한다. HTTP 상태·오류 envelope·재시도 횟수는 A·AI와 확정한다.

## B → A/Frontend: 판정과 저장

- 추천 상태는 `RECOMMENDED`, `NO_ADDITIONS_NEEDED`, `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS` 중 하나다. 결과마다 근거 basis와 Catalog release/hash를 함께 묶는다. AI의 설명은 B의 지원·권한 판정을 대체하지 않는다.
- 추천·선택·Preview·승인은 각각 별도 상태다. `RECOMMENDED`인 **현재** 저장 추천의 도구만 Preview 후보가 된다. 정책 선택은 ZIP 다운로드 승인이나 실제 Client 적용을 뜻하지 않는다.
- Preview에는 현재 basis, 선택·정책, 파일 경로·내용 해시·fingerprint·만료와 필요한 안내를 포함한다. 기존 파일 내용·Diff는 비교 중에만 사용하고 DB·로그·캐시에 남기지 않는다.
- 승인 요청에는 PreviewInput이 없으므로 A가 저장된 READY Preview의 소유권·현재 basis·지문·만료를 검사해 승인한다. 기존 파일 원문이나 최종 content는 승인 시 재요청하거나 저장하지 않는다. 브라우저는 현재 화면 메모리의 입력을 export 때 다시 제출하고, B는 승인 Catalog에서 파일을 재생성해 저장 지문과 비교한다. 새로고침 후 원문이 없다면 새 Preview부터 시작한다.
- 승인된 ZIP은 같은 입력으로 재생성해 fingerprint 및 압축 해제 후 파일 bytes를 확인한다. 성공 시 생성 이력에는 ID, basis, fingerprint, 경로·동작·해시만 남긴다. 서버의 `GENERATED`는 브라우저 수신·PC 설치·인증·동작 검증을 의미하지 않는다. 사용자 `APPLIED`/`FAILED` 진술도 검증 결과로 승격하지 않는다.
- 공개 경로·오류 코드·요청 한도는 [추천 API 초안](../../../../Docs/api/02-recommendations.draft.md)과 [설정 API 초안](../../../../Docs/api/03-configuration.draft.md)에 제안돼 있으나, 두 문서 모두 미확정이다. A의 공통 인증·오류 규칙과 맞춘 뒤 OpenAPI/계약 테스트로 고정한다.

## 합의가 필요한 항목과 수용 사례

| 쟁점 | 결정에 필요한 답 |
| --- | --- |
| A 필드 경로 | 확정 Profile·Developer·Environment에서 AI 근거로 허용할 정확한 JSON 경로와 null/빈 배열 의미는? |
| 스냅샷 버전 | Profile 확인 이벤트, Review, Developer, Environment 변경 시 어떤 버전이 증가하고, 동일 트랜잭션의 현재 basis는 어떻게 읽는가? |
| AI 성공 의미 | 9개 전량 응답 여부, 생략/미정/선택의 의미, 질문 담당 및 AI 실패 응답은? |
| 환경 대상 | Claude Code의 정확한 ID·버전·OS 범위, 사용자 선언 설치 버전 미확인 시 처리와 검증 주체는? Codex는 후속 릴리스에서 별도 검증 |
| Catalog 승인 | 실제 지원·템플릿·권한 매핑·조합 근거를 누가 검사하고 release/hash를 어떻게 별도 승인하는가? |
| 저장·동시성 | 추천/Preview/승인/생성 ID와 저장 위치, Project 삭제 cascade, 재요청·경쟁 변경의 트랜잭션/오류 처리는? |
| 승인 입력 수명 | A가 저장 Preview의 READY·선택/정책 메타데이터를 어떻게 조회하고, Frontend가 보관하지 않는 기존 파일 원문을 export 시 어떻게 다시 제출하거나 새 Preview로 돌아가는가? |

최소 계약 테스트에는 타인 프로젝트 ID, 확정 뒤 버전 변경, AI 미지/중복 키·허용 밖 근거, AI 실패와 빈 성공의 구분, 미지원 Client/버전, 오래된 추천, 추천 밖 도구, 승인 뒤 내용 변경, ZIP 생성 성공 뒤 DB 저장 실패, 프로젝트 삭제를 포함한다. 각 사례의 최종 HTTP 코드와 저장 효과는 계약 확정 시 함께 기록한다.
