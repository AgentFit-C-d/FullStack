# Preview 최종 승인 메타데이터 발급

기획된 승인 API의 요청에는 Preview 입력·기존 파일 원문이 없다. 원문을 저장하지도 않으므로 승인 시 재생성을 필수로 요구할 수 없다. `CatalogReadyPreviewApprovalWorkflow.issue`는 활성 Catalog의 manifest·파일 해시와 독립 승인 hash/release를 확인하고 `PreviewApprovalIssuer.issueStoredReady`를 호출한다. 후자는 A가 소유권과 **READY 상태**를 확인해 조회한 저장 Preview의 지문과 사용자가 제출한 지문, 현재 basis, 만료와 명시적 확인을 검사한다. 모두 맞을 때만 서버가 부여한 approval ID, Preview ID, 지문, 승인 시각과 만료 시각으로 `StoredApprovalState`를 만든다. 승인 만료는 Preview 만료를 넘지 않는다.

승인 요청의 `previewId`도 저장 READY Preview ID와 직접 비교한다. 요청 ID를 저장 레코드에서 대신 채우면 잘못된 대상을 지정한 요청을 B 경계가 알 수 없다. 다른 ID는 승인 저장 후보를 만들기 전에 거부한다.

발급 객체는 **저장 후보**다. A가 같은 프로젝트의 READY Preview·현재 basis를 짧은 트랜잭션에서 재확인하고 승인·Audit를 원자적으로 저장하기 전에는 사용자에게 승인 성공으로 응답하면 안 된다. 브라우저가 승인 시각을 지정해서는 안 된다. 서버는 설정 원문·Diff를 승인 기록에 보관하지 않는다. 원본 입력과 Catalog 템플릿의 정확한 bytes 재생성은 export 요청에서 수행한다.

합성 입력을 사용한 테스트는 저장 지문·제출 지문 불일치, 미확인, 만료, basis 변경을 승인에서 막고, export에서는 원본 재제출·템플릿을 재검사해 정확한 ZIP만 생성함을 확인한다. 실제 API·DB 저장과 승인 중복 요청 처리는 A 계약 이후 작업이다.
