# PR #11 develop 병합

## 프론트 충돌 해결

동일한 `apps/web` 경로에 B의 JavaScript 서비스와 A의 TypeScript 분석 화면이 각각 추가되어 6개 공통 파일에서 add/add 충돌이 발생했다.

- 기본 진입점은 `index.html` → `main.jsx`로 유지한다.
- A의 `main.tsx`는 `analysis.html`에서 제공하며 원래 화면·API 코드·테스트를 보존한다.
- 합친 package.json에서 lock 파일을 재생성하고 Node 테스트와 Vitest를 모두 실행한다.
- Vite 설정을 명시적으로 선택하고 두 진입점을 빌드한다. 개발 API 프록시도 보존한다.
- Dockerfile에 dev/build/Nginx 단계를 두고 루트 Compose는 dev를 선택한다. 기존 B Compose는 Nginx를 사용한다.

## DB 마이그레이션 번호 충돌

develop에는 A의 `V7__analysis_input_lease_and_result_uuid.sql`이 이미 포함됐다. B의 질문 답변 테이블도 V7을 사용해 병합하면 Flyway가 중복 버전으로 시작을 거부한다. develop의 V7은 유지하고 B의 질문 답변 마이그레이션을 V10으로 이동했다. SQL 내용은 변경하지 않았다.

**기존 B 로컬 DB 주의:** 예전 B V7~V9가 적용된 DB는 병합 후 V7 체크섬이 달라진다. 이 변경은 해당 DB의 이력을 자동 repair하거나 데이터를 삭제하지 않는다. 기존 DB를 백업한 뒤 `flyway_schema_history`와 실제 테이블을 대조해 별도 이관해야 한다. 신규 DB와 develop의 A V7까지 적용한 DB는 정렬된 버전으로 적용한다. 먼저 격리 DB에서 검사한다.

## 검증

- 기존 프론트 테스트 100건과 A 분석 화면 테스트 9건 통과.
- 병합 후 Core API `mvn -q verify` 통과.
- TypeScript 검사, 두 HTML 진입점의 Vite 빌드, JavaScript lint 통과.
- Docker 개발 서버 단계와 Nginx 최종 이미지 빌드 통과.
- 일회용 PostgreSQL 17의 격리 스키마에서 Flyway 마이그레이션 및 B 저장 테스트 3건 통과. 기존 사용자 DB는 변경하지 않음.
