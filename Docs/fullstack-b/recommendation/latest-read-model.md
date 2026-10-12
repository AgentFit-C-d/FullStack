# 최신 추천 조회와 신선도 판정

`RecommendationReadModel.latest(stored, currentBasis)`는 A가 인증·소유권을 확인해 조회한 최신 추천과 현재 스냅샷 기준을 받는다. 저장 추천이 없으면 `null`을 반환한다. 있으면 AI·추천 엔진을 다시 실행하지 않고 저장 당시 basis와 현재 basis를 비교해 `CURRENT` 또는 `STALE`을 반환한다.

비교 범위는 프로젝트·확정 Profile·확인 이벤트·Review·Developer·Environment 버전과 Catalog 릴리스 ID/해시다. 하나라도 바뀌면 과거 결과는 읽을 수 있으나 새로운 Preview 선택의 기준으로 사용할 수 없다. 추천 상태와 도구 목록의 불변 조건이 깨진 저장 기록은 정상 결과로 표시하지 않는다.

단, 저장 추천과 현재 basis의 **프로젝트 ID가 다르면** 같은 프로젝트의 과거 결과가 아니므로 `STALE`로 표시하지 않고 입력을 거부한다. A의 인증·소유권 확인을 대체하는 검사는 아니며, 잘못 결합된 내부 조회 결과를 한 번 더 차단한다.

`RecommendationLatestDetailsService`는 A가 같은 소유권 검사·일관된 읽기 경계에서 제공한 현재 basis/환경과 저장 B1–B4 메타데이터를 합친다. 복원된 9개 Capability와 상태별 item/question/reason 불변 조건을 먼저 재검증한다. CURRENT 추천의 도구는 독립 승인된 **동일 릴리스·해시**에서 정확한 OS/Client/버전의 지원 근거·권한 선택지만 다시 읽고, 저장된 버전·지원 키와 불일치하면 거부한다. STALE 추천은 과거 저장 이유·질문을 읽을 수 있지만 현재 Catalog 선택지를 붙이지 않는다. AI를 다시 호출하지 않는다.

이것은 B 내부 투영이다. A의 실제 인증·프로젝트 소유권·두 조회의 동일 트랜잭션, 최종 공개 DTO와 `GET latest` 경로는 아직 연결되지 않았다. `currentBasis`가 없거나 저장소 조회 실패를 `STALE` 또는 추천 없음으로 치환해서는 안 된다.
