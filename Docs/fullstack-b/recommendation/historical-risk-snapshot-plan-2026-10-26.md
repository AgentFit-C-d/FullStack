# 추천 당시 위험도 검토 정보 보존

## 목표

저장된 추천이 `STALE`가 된 뒤에도 당시 도구의 위험도·외부 인증 검토 상태와 근거를 읽을 수 있게 한다. 현재 Catalog의 검토 결과를 과거 추천에 소급 적용하지 않는다.

## 저장 범위

- `recommendation_item`에 `risk_status`(`UNASSESSED`/`REVIEWED`)와 검토된 경우에만 등급·외부 인증·이유·근거 URL·검토 날짜를 저장한다.
- 기존 행은 V9 마이그레이션에서 `UNASSESSED`로 해석한다. `UNASSESSED`는 낮은 위험도나 인증 불필요라는 뜻이 아니다.
- 추천 `savedItems`는 이 최소 스냅샷을 조회용으로 반환한다. 현재 지원·권한·설치 가능성을 뜻하지 않는다.
- 원본 기획서, 설정 내용, Diff는 보관하지 않는다.

## 순서

1. H2 저장/조회와 `STALE` 공개 응답의 실패 테스트를 작성한다.
2. V9 마이그레이션, B 추천 메타데이터 조립·JDBC 쓰기·읽기를 연결한다.
3. 최신 추천 공개 응답과 OpenAPI에 저장 당시 위험도 필드를 추가한다.
4. B 추천·미리보기 관련 회귀 테스트를 실행한다. Docker가 켜져 있으면 PostgreSQL도 확인한다.

## 검증 기록

- H2에서 추천 항목 저장과 위험도 행 복원을 각각 검증하고, `STALE` 최신 추천 HTTP 응답의 `savedItems.riskReview`를 확인했다.
- PostgreSQL 전용 `FOR SHARE`가 필요한 전체 JDBC 조회 및 Flyway V9 적용은 Docker 엔진이 실행될 때 통합 검증한다.

## 경계

A·AI·Frontend 소스는 수정하지 않는다. 실제 도구의 위험도 검토나 Catalog 승인은 이 기능의 범위 밖이다.
