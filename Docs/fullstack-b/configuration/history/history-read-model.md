# 생성 이력 조회 내부 모델

`ConfigurationHistoryReadModel.project`는 A가 같은 프로젝트에서 소유권을 확인해 읽은 생성 이력과 현재 basis, 해당 생성 건의 최신 사용자 보고를 합친다. 저장 당시 basis와 현재 basis를 비교해 `CURRENT` 또는 `STALE`을 표시한다. 보고는 생성 ID에 속하고 생성 이후에 기록됐어야 한다. `APPLIED`도 출처가 `USER`인 진술이며 생성 이력의 검증 상태 `NOT_RUN/NONE`을 바꾸지 않는다.

생성 이력에는 설정 본문·ZIP bytes·Diff가 없고, 보고에는 상태·사유·출처·시각만 있다. B의 JDBC 읽기는 프로젝트 범위의 단건과 생성 시각·UUID 커서 목록(최대 50건)을 제공한다. A는 프로젝트 소유권을 확인하고 현재 basis를 읽어 각 항목의 CURRENT/STALE을 계산해야 한다. 공개 조회 API의 커서 인코딩, 저장·삭제·Audit는 이 순수 모델의 범위 밖이다. B12 상위 목표는 미완료다.
