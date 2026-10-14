# A-B Phase 2 데이터 모델

## 식별자와 초기화

- `USER.id`: UUID
- `PROJECT.id`: UUID
- A15·B9·Audit의 PK/FK: UUID 기준
- 기존 개발 데이터: 보존하지 않음
- 모든 project-scoped FK는 동일한 `PROJECT.id`를 참조

## A snapshot

- project ID/version
- confirmed Profile ID/version
- confirmation event ID
- review/developer/environment version
- 허용된 source field와 redacted AI input
- pending conflict 상태
- environment 및 installed component 선언

## B 저장 projection

- Recommendation: recommendation, capability, item, notice
- Preview: config preview, selection, permission selection, preview file
- Export/report: config export 및 사용자 보고 상태
- Audit: 성공 업무 상태와 같은 transaction에 저장되는 최소 사건

## 삭제 순서

1. CONFIG_EXPORT
2. CONFIG_PERMISSION_SELECTION, CONFIG_PREVIEW_FILE
3. CONFIG_SELECTION
4. CONFIG_PREVIEW
5. RECOMMENDATION_NOTICE, RECOMMENDATION_CAPABILITY, RECOMMENDATION_ITEM
6. RECOMMENDATION
7. A profile/review/analysis/environment 종속 데이터
8. PROJECT Audit
9. PROJECT

삭제 전 PROJECT를 lock하고 활성 역참조를 해제한다. 삭제 이후 늦은 분석·추천 결과는 ownership/deletion guard에서 거부한다.

