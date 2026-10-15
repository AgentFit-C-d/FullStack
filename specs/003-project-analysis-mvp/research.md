# 조사 결과: 프로젝트 생성과 분석 시작 MVP

## 결정 1: 기존 Core API 안에서 구현한다

- **결정**: Spring Boot Core API가 PostgreSQL을 단독으로 읽고 쓴다. 이번 MVP는 프런트엔드나 FastAPI 분석 호출을 추가하지 않고, Core API의 REST 계약과 저장·상태 전이만 구현한다.
- **근거**: 헌장은 브라우저와 AI Service의 DB 직접 접근을 금지한다. 현재 `ProjectController`, `ProjectService`, JDBC Repository, Flyway, Spring Session 구조가 이미 있다.
- **검토한 대안**: AI Service가 입력 또는 Attempt를 직접 저장하는 방식은 단일 쓰기 소유권과 소유권 검증 원칙을 위반하므로 제외한다.

## 결정 2: 기존 프로젝트 CRUD는 `ProjectService`에 유지한다

- **결정**: 프로젝트 생성·상세 조회·정보 수정·기획 입력 교체·생성 멱등성은 `ProjectService`와 전용 Repository에 추가한다. 분석 접수와 600초 만료 정리만 별도 `ProjectAnalysisService` 한 개가 맡는다.
- **근거**: 현재 `ProjectService`는 이미 생성·조회·삭제를 통합하고 있다. 사용자 요구에 따라 CRUD 목적의 서비스 분할을 늘리지 않는다.
- **검토한 대안**: 생성, 입력, 프로필, 런타임, 멱등성을 각각 서비스로 분리하면 클래스 수만 늘고 현재 MVP의 경계에 비해 과도하다.

## 결정 3: 기존 Flyway 파일은 바꾸지 않고 추가 마이그레이션으로 확장한다

- **결정**: 이미 적용되었을 수 있는 `V1`~`V3`은 수정하지 않고 `V4__project_analysis_mvp.sql`을 추가한다.
- **근거**: Flyway 적용 이력이 있는 개발 DB에서 과거 파일 변경은 checksum 오류를 만든다.
- **검토한 대안**: `V2`·`V3`을 직접 수정하는 방식은 신규 DB에서는 편해도 기존 로컬 DB를 깨므로 제외한다.

## 결정 4: 입력 전송은 하나의 multipart 생성/교체 계약으로 통일한다

- **결정**: 파일과 직접 입력을 모두 `multipart/form-data`로 보낸다. JSON `metadata` 파트에는 이름·역할·런타임을 넣고, `document` 파일 파트 또는 UTF-8 `inputText`(`text/plain`) 파트 중 정확히 하나만 허용한다.
- **근거**: PDF/Markdown 파일과 직접 텍스트를 하나의 생성 흐름에서 다루면서도 상호 배타 입력을 서버에서 명확히 검증할 수 있다. Markdown은 파일(`text/markdown`)만 허용하고 직접 입력은 일반 텍스트다.
- **검토한 대안**: JSON 생성 API와 파일 생성 API를 분리하면 같은 기능의 인증·멱등성·검증 계약이 중복된다.

## 결정 5: PDF 검증에는 PDFBox를 테스트/실행 의존성으로 추가한다

- **결정**: Apache PDFBox로 PDF 페이지 수와 추출 텍스트의 Unicode code point 수를 읽고, 100쪽·100,000 code point 제한을 초과하면 저장 전에 거부한다.
- **근거**: Spring Web의 multipart 지원만으로는 PDF 내부 페이지·텍스트 제한을 검증할 수 없다.
- **검토한 대안**: 파일 크기와 확장자만 검사하면 요구사항의 페이지·텍스트 제한을 충족하지 못한다. AI Service에 검증을 맡기면 Core API 단일 쓰기·입력 검증 경계가 흐려진다.

## 결정 6: 원문은 종료 시 정리하고 메타데이터만 이력으로 남긴다

- **결정**: 기획 입력 행은 형식·파일명·크기·페이지 수·문자 수·버전을 유지한다. 이번 MVP는 최대 10초 간격의 timeout 정리로 원문 바이트, 직접 입력 텍스트, PDF 추출문, 근거 인용문을 제거한다. 후속 `SUCCEEDED`, 취소·비정상 종료도 같은 내부 terminal 처리에서 `NULL` 처리 또는 행 삭제를 재사용한다.
- **근거**: 최신 사용자 결정은 원문 보관 기간을 0일로 정했다. `ANALYSIS_ATTEMPT`와 메타데이터 버전은 분석 이력을 설명하는 데 충분하다.
- **검토한 대안**: 원문을 프로젝트 삭제까지 보관하는 방식은 최신 보관 정책과 헌장의 정상 원문 비보관 원칙을 위반한다.

## 결정 7: 분석 동시성은 PostgreSQL 제약과 단일 admission 행으로 보장한다

- **결정**:
  - 같은 프로젝트와 같은 사용자의 활성 Attempt는 PostgreSQL partial unique index로 각각 1건만 허용한다.
  - 서버 전체 2건 제한은 `ANALYSIS_ADMISSION`의 단일 행을 `SELECT ... FOR UPDATE`로 잠가 검사·증가·감소한다.
  - 시작과 만료 정리는 항상 `PROJECT` 행 → admission 행 순서로 잠근다.
- **근거**: 단순 `COUNT` 뒤 `INSERT`는 동시 요청에서 2건 제한을 넘길 수 있다. JVM `synchronized`는 다중 인스턴스에도 안전하지 않다.
- **검토한 대안**: 애플리케이션 메모리 카운터는 재시작·다중 인스턴스에 안전하지 않아 제외한다. 메시지 큐는 이번 MVP 범위를 넘는다.

## 결정 8: `PROCESSING`은 600초 기한과 스케줄러로 회수한다

- **결정**: Attempt 생성 시 `deadline_at = started_at + 600초`를 기록한다. `@Scheduled` 정리 작업이 만료된 Attempt를 `FAILED`/`TIMEOUT`으로 조건부 전환하고, admission 슬롯과 원문을 한 번만 회수한다.
- **근거**: 실제 AI 완료 처리가 아직 없어도 활성 슬롯과 원문이 영구히 남지 않는다.
- **검토한 대안**: 영구 `PROCESSING`은 프로젝트·사용자·서버 동시 제한을 계속 점유하므로 제외한다.

## 결정 9: 생성 멱등성은 서버 저장 레코드로 처리한다

- **결정**: `POST /api/projects`는 `Idempotency-Key` 헤더를 필수로 받는다. `(owner_id, operation, key)` 유니크 레코드를 먼저 예약하고, 같은 키의 완료 요청은 최초 프로젝트 응답을 재사용한다. 같은 키에 다른 요청 본문을 재사용하면 `409 IDEMPOTENCY_KEY_REUSED`를 반환한다.
- **근거**: 응답 유실 뒤 재시도해도 중복 프로젝트가 생기지 않아야 하며, `request_id`만으로는 멱등성을 보장하지 않는다.
- **검토한 대안**: UI의 중복 클릭 방지만으로는 네트워크 재시도를 막을 수 없다.

## 결정 10: 활성 분석 중 프로젝트 삭제는 차단한다

- **결정**: `PROCESSING` Attempt가 있는 프로젝트 삭제는 `409 ANALYSIS_ACTIVE_PROJECT_DELETE_FORBIDDEN`으로 거부한다. terminal 상태 뒤에만 기존 cascade 삭제를 허용한다.
- **근거**: Attempt를 cascade 삭제하면 `ANALYSIS_ADMISSION.active_count`를 회수하지 못해 서버 전체 2건 슬롯이 누수될 수 있다.
- **검토한 대안**: 삭제 중 Attempt를 강제 실패 처리하는 방식은 admission 회수·원문 scrub·Audit을 모두 같은 트랜잭션으로 보장해야 하므로, 실제 취소 요구가 생길 후속 기능까지 도입하지 않는다.

## 결정 11: 계약·테스트를 같은 변경에 맞춘다

- **결정**: `contracts/openapi/core-api.yml`을 0.2.0으로 올리고, DTO, 예외 코드, MockMvc 계약 테스트, 순수 서비스 단위 테스트, PostgreSQL 통합 테스트를 함께 변경한다. PostgreSQL의 partial index·행 잠금은 H2와 동등하지 않으므로 Testcontainers PostgreSQL을 test scope에 추가한다.
- **근거**: 헌장은 계약·DTO·서버 검증·테스트의 동시 정합성과 DB 경쟁 테스트를 요구한다.
- **검토한 대안**: H2만으로 PostgreSQL partial index와 동시 admission을 검증하면 실제 DB 동작을 보장하지 못한다.
