# A-B Phase 2 조사 결과

## 결정 1: PDF를 canonical schema로 사용

사용자가 PDF 기준 B를 선택했다. 따라서 `USER`, `PROJECT`, A15, B9, 공통 Audit의 식별자를 UUID로 통일한다. 기존 개발 DB 데이터는 보존하지 않고 새 schema를 생성한다.

## 결정 2: Flyway 초기화 migration으로 편입

현재 B schema는 별도 SQL 파일이며 자동 실행되지 않는다. A 테이블을 포함한 전체 schema를 Flyway migration으로 편입해 실제 제약·FK·삭제 순서를 검증한다.

## 결정 3: A-owned adapter로 기존 B 포트 연결

B의 `RecommendationCreationService`와 `PreviewCreationService`는 이미 owner-checked snapshot/context reader와 atomic store 포트를 정의한다. A가 해당 포트를 구현하며 B 서비스 인터페이스와 DTO는 변경하지 않는다.

## 결정 4: 외부 AI는 transaction 밖에서 호출

AI 호출 후 저장 직전에 project ownership, 대상 ID, version, generation, 삭제 여부를 재검증한다. AI 실패·형식 오류·누락 capability는 성공 추천으로 저장하지 않는다.

## 충돌 및 해소

1. PDF의 A-B 공개 계약은 proposal/NOT_RUN이므로 OpenAPI·fixture·Controller를 이번 작업에서 함께 확정한다.
2. B의 `RecommendationInputAssembler`는 9개 capability 전량을 요구한다. 공유 계약이 바뀌기 전까지 incomplete/undetermined 결과는 fail-closed로 처리한다.
3. B 삭제기는 caller-owned JDBC transaction을 요구한다. A 프로젝트 삭제가 outer transaction을 소유하고 B 삭제를 먼저 호출한다.

