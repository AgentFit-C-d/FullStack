# 최신 추천 조회와 신선도 판정

`RecommendationReadModel.latest(stored, currentBasis)`는 A가 인증·소유권을 확인해 조회한 최신 추천과 현재 스냅샷 기준을 받는다. 저장 추천이 없으면 `null`을 반환한다. 있으면 AI·추천 엔진을 다시 실행하지 않고 저장 당시 basis와 현재 basis를 비교해 `CURRENT` 또는 `STALE`을 반환한다.

비교 범위는 프로젝트·확정 Profile·확인 이벤트·Review·Developer·Environment 버전과 Catalog 릴리스 ID/해시다. 하나라도 바뀌면 과거 결과는 읽을 수 있으나 새로운 Preview 선택의 기준으로 사용할 수 없다. 추천 상태와 도구 목록의 불변 조건이 깨진 저장 기록은 정상 결과로 표시하지 않는다.

단, 저장 추천과 현재 basis의 **프로젝트 ID가 다르면** 같은 프로젝트의 과거 결과가 아니므로 `STALE`로 표시하지 않고 입력을 거부한다. A의 인증·소유권 확인을 대체하는 검사는 아니며, 잘못 결합된 내부 조회 결과를 한 번 더 차단한다.

현재 모델은 추천 ID·상태·도구 키·basis·validity의 내부 투영이다. Capability 근거·도구 상세·질문을 포함한 최종 공개 DTO, A의 일관된 현재값 조회, 저장/삭제/페이지네이션, 인증된 `GET latest` 경로는 아직 연결되지 않았다. `currentBasis`가 없거나 저장소 조회 실패를 `STALE` 또는 추천 없음으로 치환해서는 안 된다.
