# 승인 Catalog 기반 ZIP 생성 경계

`CatalogApprovedConfigurationWorkflow.generate`는 다운로드 때 Catalog를 다시 로드하고 manifest 파일 해시와 별도 승인 해시를 검사한다. 현재 Recommendation·basis·Environment target에 선택 도구가 속하는지 확인한 다음, 검토된 정적 템플릿과 권한 매핑으로 Preview 입력을 재구성한다. 재구성한 지문이 저장 Preview 및 최종 승인과 일치하고 만료되지 않았을 때만 ZIP과 최소 생성 이력을 함께 반환한다.

이 경로는 B의 순수 내부 진입점이다. 호출자는 A가 소유권을 확인해 조회한 Recommendation·Preview·Approval 및 현재 basis/Environment를 전달해야 한다. Preview 요청의 선택·권한·사용자가 제공한 원본은 승인 때 사용한 값으로 다시 구성해야 하며, 브라우저가 다운로드 때 임의로 바꾼 값을 그대로 신뢰하면 안 된다. 값이 바뀌면 지문 불일치로 거부하지만, 요청 원본 보관·재요청 방식은 A와 계약해야 한다. 반환 이력은 A가 ZIP 응답 전에 감사 기록과 함께 원자적으로 저장해야 하며, 실패 시 이력을 남기지 않는다.

합성 Catalog 테스트는 승인된 정확한 파일이 ZIP에 들어가고, 권한 선택 변화와 Catalog 파일 변조 시 생성이 거부되는 것을 확인한다. 실제 Claude Code 지원 데이터·인증된 HTTP 엔드포인트·DB 저장은 아직 미구현이다. B11 상위 목표는 미완료다.
