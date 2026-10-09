# 풀스택 B 연동 포트 인계안

> 2026-10-07 기준 **제안 / 미합의**. 이 문서는 현재 B Java 모델에 맞춰 A·AI가 결정해야 할 입력과 저장 경계를 구체화한다. 인터페이스 이름은 설명용이며 구현된 API·DB 계약이 아니다. [전체 연동 계약 제안](ab-ai-contract-proposal.md)과 [1차 완료 게이트](../PHASE1-CLOSEOUT.md)를 함께 본다.

## 신뢰 경계

브라우저는 ID·선택·기존 파일 입력을 보낼 수 있지만, 프로젝트 소유권, 현재 확정 Profile, 버전 basis, 현재 Environment, 승인된 Catalog hash와 저장된 추천·승인은 증명할 수 없다. A가 인증·소유권 검사 후 같은 프로젝트의 현재값을 조회해 B에 전달해야 한다. AI 응답은 Capability **후보**이며 Catalog 지원이나 권한 승인을 증명하지 않는다.

| B에서 사용 중인 입력·진입점 | A·AI가 제공하거나 결정할 값 | 현재 B 검증 | 연결 전에 합의할 점 |
| --- | --- | --- | --- |
| `PreviewBasis` | A의 project ID/version, CONFIRMED Profile ID/version, 확인 이벤트 ID, Review/Developer/Environment version; 서버 승인 Catalog release ID/hash | 빈 값·버전·hash 형식과 저장/현재 basis 일치 확인 | 각 버전의 증가 시점, 일관된 읽기와 동시 변경 처리 |
| `AiCapabilityIntake.validate` | AI claim·질문, A가 허용한 근거 필드 경로 집합 | 고정 9개 ID, 중복·근거·허용 필드 검사 | 실제 wire DTO/버전, 9개 전량 여부, 미정·질문 의미, 실패 envelope |
| `RecommendationInputAssembler` → `RecommendationEngine` | 검증된 AI 평가, A의 미해결 충돌·현재 Environment·설치 버전 선언, 승인 Catalog | 누락된 Capability를 불필요로 간주하지 않도록 현재는 9개 전량 요구; 최소 검증 조합 판정 | AI 생략 규칙, 설치 버전 미확인 표현, 선택 도구·설명 응답 형식 |
| `CatalogRecommendationWorkflow.evaluate` | 위 AI 후보·A 현재값 및 서버가 독립 승인한 Catalog 해시 | Catalog 파일/승인 해시·AI 9개 평가·조합 판정을 한 번에 실행해 결과와 Catalog ID/해시·AI 질문 반환 | A 인증 조회·현재 basis·저장 트랜잭션·AI wire 계약은 여전히 미합의 |
| `RecommendationPreviewGate` → `CatalogPreviewAssembler` | A가 소유권 확인 후 조회한 현재 추천 ID/status/tool keys/basis와 별도 현재 Environment target; 요청의 선택·정책·기존 파일 | 추천 밖 도구·stale basis·대상 불일치·미검증 Catalog/권한/템플릿 차단 | 추천·Preview 저장 ID, 요청 전체 JSON 한도, 인증/오류 규칙 |
| `CatalogReadyPreviewApprovalWorkflow` → `CatalogApprovedConfigurationWorkflow` | 승인 시 A가 조회한 READY Preview·활성 승인 Catalog·현재 basis·제출 지문·확인값; export 시 저장 승인과 재제출한 PreviewInput | 승인에서는 Catalog 파일·저장 지문·만료·현재 basis, export에서는 승인 Catalog 기반 재생성 지문과 ZIP bytes 확인 | READY/소유권 조회, 승인 저장 트랜잭션, 입력 원문 비보관, 중복 요청, Catalog 승인 철회 처리 |
| `ApprovedConfigurationGenerator` | A가 부여한 generation ID와 서버 시계, 조회된 승인 상태 | ZIP 성공 후 경로·동작·해시만 포함한 이력 반환 | ZIP 생성 후 DB 저장 실패 시 응답/재시도, 이력·감사 저장과 삭제 cascade |
| `ConfigurationUserReport.accept` | 소유권을 확인한 generation ID, 사용자 APPLIED/FAILED 보고 | 사용자 진술로 표시하고 서버 시각 부여 | 보고 저장·조회 주체와 실패 코드 공개 계약 |

## 연결 순서와 저장 효과

1. **추천 생성:** A가 소유권과 확정 상태를 확인해 일관된 현재 스냅샷을 읽는다. AI 호출 성공 응답을 계약 버전·형식부터 검사한 뒤 B의 Capability 검증·추천 판정으로 넘긴다. AI 실패·파싱 실패·누락된 키는 새 성공 추천으로 저장하지 않고 기존 추천을 보존한다. A는 판정·전체 basis·Catalog hash를 하나의 저장 단위로 기록한다.
2. **Preview:** A가 저장된 추천과 현재 basis/Environment를 다시 읽는다. B는 `RECOMMENDED`의 제공 도구만 허용하고, 승인된 Catalog 파일의 hash·지원 대상·조합·권한·템플릿을 검사한다. 기존 파일 원문과 Diff는 응답 생성 중에만 사용한다. A가 저장할 수 있는 것은 Preview ID·basis·fingerprint·만료와 필요한 선택 메타데이터뿐이다.
3. **승인·다운로드:** 최종 확인 요청에서 A는 현재 basis와 Preview 만료를 재조회한다. 승인은 서버 저장 지문에 묶는다. 다운로드 때도 A가 저장 승인·현재 basis를 재조회한 뒤 B가 같은 입력으로 ZIP을 재생성한다. A는 **이력 DB 저장이 성공한 뒤** 생성 완료 응답을 확정해야 한다. ZIP bytes 생성 성공만으로 이력 저장 성공이나 사용자 PC 적용을 주장하지 않는다.
4. **변경·삭제:** Project/Profile/Review/Developer/Environment/Catalog 또는 선택·권한·템플릿 변경 시 오래된 Preview/승인은 재확인이 필요하다. Project 삭제 시 B 메타데이터·사용자 보고·감사 범위도 A의 삭제 계약에 포함한다.

## A·AI와 확인할 최소 결정

| 우선순위 | 결정 질문 | 결정 전 B가 할 수 없는 일 |
| --- | --- | --- |
| 1 | A의 소유권 검사 후 **일관된 현재 스냅샷**을 어떤 메서드/트랜잭션으로 읽고 각 version을 언제 올리는가? | 현재 basis에 연결한 추천·Preview·승인 API |
| 2 | AI는 9개 Capability를 모두 반환하는가? 누락·`UNDETERMINED`·질문·실패를 어떤 wire 형식으로 표현하는가? | FastAPI 응답을 성공 추천으로 저장 |
| 3 | 추천/Preview/승인/생성 이력의 ID·저장 위치·동시성·중복 요청·삭제 책임은 무엇인가? | 승인·다운로드의 일관된 저장 효과를 갖는 사용자 흐름 |
| 4 | 실제 Claude Code의 대상 OS·버전, 후보 도구·고정 버전, 시험·독립 승인 담당은 누구인가? | 실제 지원 후보를 Catalog에서 활성화 |

최소 수용 시험은 타인 프로젝트 거부, 미확정 Profile 거부, AI 누락/실패 시 기존 추천 보존, 추천 뒤 version 변경, 미지원 Client/조합, 추천 밖 선택, 승인 후 파일·권한·basis 변경, ZIP 생성 뒤 저장 실패, Project 삭제 뒤 조회 차단이다. HTTP 상태·DB 효과는 A의 공통 계약과 함께 확정한다.
