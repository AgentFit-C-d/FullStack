# Phase 3: A 확정 프로필 → B 추천 연결

기준 브랜치: `develop`의 A Phase 2 및 B Phase 2 병합본. A는 분석 결과를 DRAFT로 저장하고 사용자 확인 후 CONFIRMED Profile과 확인 이벤트를 생성한다. B는 인증·소유권을 통과한 프로젝트의 최신 CONFIRMED Profile만 읽어 AI needs와 Catalog 판정에 사용한다. DRAFT만 있으면 추천을 시작하지 않는다.

## 현재 연결

1. `POST /api/projects/{projectId}/recommendations`에서 A 세션 인증과 프로젝트 소유권을 검사한다.
2. B 스냅샷은 확정 프로필·확인 이벤트를 필수로 읽고, 개발자·환경·검토 데이터는 있으면 버전과 함께 읽는다. 아직 입력되지 않은 데이터의 버전은 0이다. 기획의 프로젝트명·유형·도메인 및 허용된 항목만 AI facts로 전달한다.
3. AI needs 응답의 9개 Capability를 검증하고, 서버가 승인한 Catalog 해시의 도구·OS·클라이언트·버전 및 조합만 판정한다.
4. 환경 대상이나 설치 도구 확인 상태가 미정이면 `NEEDS_INFORMATION`과 해당 질문을 저장한다. 설치 목록 미입력은 빈 목록 확인과 구분한다.
5. 저장 트랜잭션은 프로젝트를 잠근 뒤 확정 프로필·확인 이벤트·검토·개발자·환경 버전을 다시 비교한다. 중간 변경이 있으면 추천을 저장하지 않는다. 성공 시 추천과 감사 이벤트를 함께 기록한다.

## 검증 범위와 남은 연결

- PostgreSQL V1–V6 마이그레이션에서 DRAFT 차단, CONFIRMED 직후 미입력 상태의 `NEEDS_INFORMATION` 저장, 환경 설치 상태 UNKNOWN 판정을 검증한다. 로컬 HMAC HTTP 테스트는 AI needs 요청 필드를 검증한다. 테스트 Catalog는 합성 데이터다.
- 실제 A 확인 API → B 추천 HTTP API의 한 번에 이어지는 시연, Solar 실호출, 첫 Claude Code Catalog의 독립 검증·승인, 개발자/환경 입력 API와 화면 연결은 별도 작업이다. 현재 확인되지 않은 Catalog를 실제 지원 도구로 표시하지 않는다.
- A가 `profile_review`를 생성하지 않아도 추천은 가능하며 검토 버전은 0으로 기록한다. 추후 검토가 생기면 확정 프로필 ID·버전에 연결된 검토만 적용한다.
