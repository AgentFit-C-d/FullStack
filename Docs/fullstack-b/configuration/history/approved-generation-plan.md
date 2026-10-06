# 승인된 ZIP·생성 이력 결합 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 승인·현재 basis·재생성 검증과 ZIP 생성이 성공한 뒤에만 `GENERATED` 이력 메타데이터를 반환한다.

**Architecture:** 새 공개 진입점이 기존 `ApprovedPreviewZipExporter`로 정확한 ZIP bytes를 만든 후 같은 요청의 지문을 재확인해 최소 이력을 생성한다. 이력 생성자는 패키지 내부로 제한하고, 반환 ZIP bytes는 방어 복사한다. DB 저장과 HTTP 응답은 A 연동 후 연결한다.

**Tech Stack:** Java 21 대상 Spring Boot 모듈, JUnit 5.

**Spec:** [설정 API 초안의 승인·다운로드·생성 이력](../../../../../Docs/api/03-configuration.draft.md), [생성 이력 메타데이터](generation-history-plan.md).

## Global Constraints

- 유효 승인·현재 basis·동일 Preview 지문을 확인한 ZIP bytes만 성공 결과에 포함한다.
- `GENERATED`는 서버가 ZIP을 생성했음을 뜻하며 브라우저 수신·로컬 적용·인증·검증을 뜻하지 않는다.
- 원본 설정·Diff·생성 파일 내용·ZIP bytes를 이력 메타데이터에 넣지 않는다.
- A의 소유권 확인·승인 조회/저장·이력 DB 트랜잭션은 이 단계에 없다.

## Review Focus

- 승인 ID/Preview ID/지문/만료 불일치면 ZIP과 이력이 함께 나오지 않는다.
- ZIP 생성 실패 후 `GENERATED` 이력이 만들어지지 않는다.
- 반환 byte 배열을 호출자가 바꿔도 다음 접근 결과는 변하지 않는다.
- 시각은 서버 Clock 하나에서 얻는다.
- 내용이 달라진 재생성 요청은 기존 승인으로 성공하지 않는다.

---

### Task 1: 승인 생성 단일 진입점

**Files:** `services/core-api/src/main/java/com/agentfit/coreapi/configuration/history/ApprovedConfigurationGenerator.java`, `ConfigurationGenerationHistory.java`; 같은 패키지의 `ApprovedConfigurationGeneratorTest.java`.

**Interfaces:** `generate(StoredPreviewState, StoredApprovalState, PreviewBasis currentBasis, String requestedApprovalId, String requestedPreviewId, String submittedFingerprint, PreviewFingerprintInput regeneratedInput, String generationId, Clock serverClock)` → ZIP bytes와 최소 이력을 가진 불변 결과.

- [x] 성공·만료/지문 실패·byte 방어 복사 테스트를 먼저 쓰고 실패를 확인한다.
- [x] ZIP 생성 후 이력을 만들도록 결합하고 이력 팩토리를 패키지 내부로 제한한다.
- [x] 집중 테스트와 전체 `mvn clean verify`를 통과시킨다.
- [x] B11/B12의 부분 구현 및 남은 DB 트랜잭션 경계를 기록하고 한글 `feat/` 커밋을 만든다.
