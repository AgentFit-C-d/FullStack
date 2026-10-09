# 승인 Catalog 기반 ZIP 생성 경계

`CatalogApprovedConfigurationWorkflow.generate`는 다운로드 때 Catalog를 다시 로드하고 manifest 파일 해시와 별도 승인 해시를 검사한다. 현재 Recommendation·basis·Environment target에 선택 도구가 속하는지 확인한 다음, 검토된 정적 템플릿과 권한 매핑으로 Preview 입력을 재구성한다. 재구성한 지문이 저장 Preview 및 최종 승인과 일치하고 만료되지 않았을 때만 ZIP과 최소 생성 이력을 함께 반환한다.

export 요청의 `approvalId`, `previewId`, `previewFingerprint`를 그대로 전달한다. 저장 Preview의 ID·지문으로 요청 값을 대신 만들면 제출 값의 불일치를 놓치므로, B 경계에서 제출 값과 저장 Preview·승인 기록을 다시 비교한다. 다른 Preview ID나 지문은 ZIP·이력 생성 전에 거부한다.

이 경로는 B의 순수 내부 진입점이다. 호출자는 A가 소유권을 확인해 조회한 Recommendation·Preview·Approval 및 현재 basis/Environment를 전달해야 한다. export 요청은 Preview 때의 선택·권한·사용자가 제공한 원본을 다시 제출한다. 브라우저가 바꾼 값은 재생성 지문 불일치로 거부한다. A는 저장된 선택·정책 메타데이터도 요청과 대조해야 한다. 반환 이력은 A가 ZIP 응답 전에 감사 기록과 함께 원자적으로 저장해야 하며, 실패 시 이력을 남기지 않는다.

합성 Catalog 테스트는 승인된 정확한 파일이 ZIP에 들어가고, 권한 선택 변화와 Catalog 파일 변조 시 생성이 거부되는 것을 확인한다. `existingState=PROVIDED` 사례에서는 원본 없이 최종 승인을 발급하고, export에서 같은 원본을 다시 제출했을 때만 생성되며 한 글자라도 바뀌면 거부됨을 확인한다. 실제 Claude Code 지원 데이터·인증된 HTTP 엔드포인트·DB 저장은 아직 미구현이다. B11 상위 목표는 미완료다.
