# 생성 이력 최소 메타데이터 구현 계획

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 성공한 설정 생성 뒤 내용·Diff 없이 생성 이력의 최소 메타데이터만 만들고 현재 basis에서 유효성을 판정한다.

**Architecture:** B의 기존 `PreviewFingerprint.compute`로 임시 입력의 파일·지문을 다시 확인한 다음 ID·basis·지문·파일 경로/동작/해시만 불변 객체로 복사한다. 생성 이력의 상태는 `GENERATED`, 검증은 `NOT_RUN/NONE`으로 제한한다. 저장·소유권·프로젝트 삭제는 A의 DB 계약에 연결한다.

**Tech Stack:** Java 21 대상 Spring Boot 모듈, JUnit 5.

**Spec:** [설정 API 초안 — 생성 이력](../../../../../Docs/api/03-configuration.draft.md) 및 [B12 상태](../../STATUS.md).

## Global Constraints

- 기존 설정 원문·생성 파일 내용·Diff·ZIP bytes를 이력 객체에 저장하지 않는다.
- 생성 이력 팩토리는 패키지 내부다. 공개 `ApprovedConfigurationGenerator`가 승인된 ZIP 생성 성공 후 이력 객체를 만든다. DB 트랜잭션은 아직 없다.
- 원격/로컬 적용·인증·연결 검증 완료를 생성 상태에서 추론하지 않는다.
- 현재 basis는 A의 인증·소유권 검사된 스냅샷에서 가져온다.

## Review Focus

- 재생성 지문이 승인 지문과 다르면 이력을 만들지 않는다.
- 파일 경로·내용이 바뀌면 기존 지문으로 이력을 만들지 않는다.
- Profile·환경·Catalog basis가 달라지면 `STALE`을 표시한다.
- 현재 basis 조회 실패를 `STALE`이나 `CURRENT`로 꾸미지 않는다.
- 이력에는 내용·Diff가 없고 검증 결과는 `NOT_RUN/NONE`이다.

---

### Task 1: 생성 이력 메타데이터

**Files:** `services/core-api/src/main/java/com/agentfit/coreapi/configuration/history/ConfigurationGenerationHistory.java`; 같은 패키지의 `ConfigurationGenerationHistoryTest.java`.

**Interfaces:** `capture(String generationId, String previewId, String approvalId, String expectedFingerprint, PreviewFingerprintInput input, Clock serverClock)`와 `validity(PreviewBasis currentBasis)`.

- [x] 정상 생성의 최소 필드·상태·검증 출처·신선도 테스트를 먼저 쓰고 실패를 확인한다.
- [x] 지문 불일치·잘못된 입력·현재 basis 부재를 거부하는 테스트를 쓴다.
- [x] Preview를 재계산해 불변 메타데이터만 복사한다.
- [x] 집중 테스트와 전체 `mvn clean verify`를 통과시킨다.
- [x] B12 부분 구현과 남은 A 저장 경계를 문서화하고 한글 `feat/` 커밋을 만든다.
