# B12a 사용자 적용 보고 검증

설정 ZIP을 생성한 것과 사용자의 PC에 복사·적용한 것은 다르다. 사용자가 `APPLIED`라고 보고해도 AgentFit이 실제 설치·인증·연결을 검증한 결과로 승격하지 않는다. 이 경계는 [설정 API 초안의 생성 이력·사용자 진술](../../../../../Docs/api/03-configuration.draft.md)에 나온 상태와 사유를 내부 도메인 값으로 제한한다.

`ConfigurationUserReport.Submission`에는 `reportedState`와 `reasonCode`만 있다. `APPLIED`에는 사유가 없어야 하며 `FAILED`에는 `COPY_FAILED`, `AUTH_REQUIRED`, `DEPENDENCY_MISSING`, `UNKNOWN` 중 하나가 필요하다. 수락 결과의 출처는 항상 `USER`, 시각은 서버 Clock에서 얻는다. 결과 객체는 외부에서 임의로 생성할 수 없다. 자유 형식 로그·설정 본문·Secret·검증 성공 상태는 이 모델에 포함하지 않는다.

이 코드는 HTTP JSON의 추가 필드·문자열 파싱, 생성 이력 ID의 소유권·프로젝트 소속, 트랜잭션·감사·삭제 연동을 수행하지 않는다. A가 제공할 인증·저장 계약과 B의 이력 API가 연결된 뒤 별도 검증이 필요하다. B12 전체는 미완료다.

테스트를 먼저 추가해 구현 누락을 확인했고, `mvn clean verify`에서 88개 테스트 발견, 87개 통과, Windows 심볼릭 링크 테스트 1개 건너뜀을 확인했다.
