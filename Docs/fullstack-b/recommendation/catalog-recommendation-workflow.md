# B 추천 평가 내부 워크플로

`CatalogRecommendationWorkflow.evaluate(releaseDirectory, approvedCatalogHash, request)`는 B의 기존 검증 단계를 한 번에 실행하는 서버 내부 진입점이다. 합성 Catalog 테스트로만 검증했으며 공개 API·DB 저장·실제 Claude Code 지원을 뜻하지 않는다.

1. 해시가 맞는 Catalog 파일을 읽고 의미·참조·검증 근거를 검사한다. 승인 해시는 요청자가 아닌 서버의 독립된 승인 설정/저장소에서 가져와야 한다.
2. AI Capability 후보가 고정 9개 키, 허용된 A 필드 경로, 필수·선택·미정 규칙을 만족하는지 검사한다. 누락된 키는 성공 추천으로 해석하지 않는다.
3. A가 확인한 환경·설치 버전·미해결 충돌을 조합 판정에 넣는다. 도구 후보는 오직 검증된 Catalog 릴리스에서만 나온다. 결과에는 Catalog 릴리스 ID·해시와 AI 추가 질문을 같이 반환한다.
4. `RECOMMENDED`라면 선택 도구에 검토된 정적 설정 템플릿·가이드가 있는지 같은 승인 Catalog에서 확인하고 생성 결과에 Preview의 파일 수·내용 크기 제한을 적용한다. 출력이 누락됐거나 인덱스가 잘못됐거나 Preview 한도를 초과한다면 사용 가능한 추천으로 반환하지 않고 Catalog 오류로 처리한다. 선택 도구마다 고정 버전, 해당 OS·Client·버전에서 PASS인 지원 행 ID와 근거, 필수 Capability와 겹치는 키, 검토된 권한 선택지와 기본 `ASK_EACH_TIME`을 붙인다. 다른 상태의 결과에는 도구 상세를 붙이지 않는다. 전체 Capability 평가·근거 필드도 검증된 값으로 함께 반환한다.

호출자는 먼저 A의 인증·프로젝트 소유권·확정 Profile을 확인하고 현재 값을 일관되게 읽어야 한다. AI의 HTTP 응답 버전/형식 검증, 결과와 현재 basis의 원자적 저장, 중복 요청·오류 응답은 A·AI 계약 확정 후 연결한다. skills.sh 후보 파일과 검토 상태는 이 진입점의 입력이 아니다. 도구 이름·종류·인증 요건 및 사람에게 보여줄 서술형 선택 이유는 실제 Catalog 데이터·A/AI/Frontend 응답 계약이 정해지기 전까지 채우지 않는다.
