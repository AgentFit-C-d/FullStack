# 승인 Catalog 기반 최종 확인 경계

`CatalogPreviewApprovalWorkflow.issue`는 저장 Preview에 대한 최종 확인 시 현재 Recommendation·basis·Environment target을 다시 검사하고, 승인된 Catalog 파일과 정적 템플릿으로 Preview를 재조립한다. 서버가 계산한 지문이 저장 Preview와 사용자가 제출한 지문에 모두 일치하고 만료되지 않았으며 명시적 확인이 있을 때만 승인 저장 후보를 만든다. 변경된 권한, Environment, Catalog 파일이나 확인 누락은 승인을 차단한다.

호출자는 A가 소유권을 확인해 조회한 Recommendation·Preview 및 현재 프로젝트 상태를 전달해야 한다. `CatalogPreviewRequest`도 최초 Preview와 동일한 선택·사용자 제공 원본으로 재구성해야 한다. B는 메모리에서 승인 후보만 만들며, A의 트랜잭션에서 현재 상태를 재확인해 승인·Audit를 저장한 뒤 성공 응답해야 한다. 실제 Catalog, HTTP 요청·DB 연동, 재요청 중복 방지는 남아 있다.
