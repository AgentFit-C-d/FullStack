# A/B 병합 후 최소 변경 정리 설계

## 목적

`origin/develop`의 A/B 병합본을 기능 재작성이나 대규모 패키지 이동 없이 빌드 가능한 상태로 복구하고, 이미 구현된 A와 B의 책임 경계 및 미연결 상태를 코드와 문서에서 명확히 한다.

## 확인된 상태

- `services/core-api/pom.xml`에 `org.postgresql:postgresql` 런타임 의존성이 두 번 선언되어 Maven 프로젝트 로딩이 실패한다.
- A는 인증, 프로젝트 소유권, 기획 입력, 분석, 공통 Audit을 소유한다.
- B는 Catalog, 추천, Preview, 승인, Export의 순수 도메인 및 caller-owned JDBC 구성요소를 제공한다.
- B의 A snapshot, AI 요청/응답, 저장 트랜잭션, 삭제 연계, 공개 API는 아직 실제 adapter로 연결되지 않았다.
- B의 PostgreSQL DDL은 현재 `b-schema.sql`에 있고 A의 Flyway migration으로 아직 편입되지 않았다.

## 범위

### 포함

1. 중복 PostgreSQL 의존성을 하나로 정리하고 Maven 전체 테스트를 다시 실행한다.
2. A/B 연계 상태와 소유 경계를 루트 README 및 B 상태 문서에서 한 곳에 명확히 기록한다.
3. 기존 B auto-configuration이 요구하는 A/AI 포트를 표로 문서화하여 조건부 빈이 비활성인 이유를 명확히 한다.
4. A Project 삭제, B JDBC writer, B schema의 현재 연결 제약을 문서화한다. 아직 실제 A 삭제 코드나 B DDL을 변경하지 않는다.
5. 병합 후 빌드·테스트 실행 방법과 현재 환경 의존 사항(Java 21, Python 개발 의존성, opt-in PostgreSQL)을 정리한다.

### 제외

- A snapshot reader, AI HTTP contract, 공개 Catalog/추천 API, Preview/승인/다운로드 endpoint의 신규 구현
- B DDL을 A Flyway migration에 통합하거나 기존 데이터베이스를 마이그레이션하는 작업
- A `domain/*` 또는 B `catalog`/`recommendation`/`configuration`의 일괄 이동·이름 변경
- Catalog 승인, 외부 LLM, 실제 Client 설치 검증의 정책 결정

## 설계 원칙

- A는 인증, 소유권, 현재 version 확인, Project 잠금, Audit, 외부 HTTP orchestration을 계속 소유한다.
- B는 순수 추천·구성 규칙과 A가 제공한 단일 트랜잭션에서 실행하는 JDBC writer/reader를 계속 소유한다.
- B의 conditional auto-configuration을 강제로 활성화하거나 가짜 adapter를 추가하지 않는다.
- 문서에서 `구현됨`은 내부 도메인/포트 구현을, `연결됨`은 인증·원자성·공개 API까지 실제 실행 경로가 존재함을 뜻하도록 구분한다.
- 기능 동작을 바꾸지 않는 최소 수정만 허용한다.

## 성공 기준

1. Maven이 POM 검증 단계에서 실패하지 않고 Java 21에서 전체 테스트를 실행한다.
2. 새 문서만 보아도 A/B의 현재 연결 수준, 미연결 포트, 안전하게 다음에 연결할 지점을 파악할 수 있다.
3. 기존 프로덕션 Java 파일은 POM의 중복 수정 외에는 변경하지 않는다.
4. PostgreSQL 통합 테스트는 명시적으로 opt-in일 때만 실행되는 기존 정책을 유지한다.

## 검증

- `services/core-api`에서 `mvn -B -ntp test`
- Python 개발 의존성이 준비된 환경에서 `services/ai-service`의 `python -m pytest -q`
- README/상태 문서의 명령과 경로를 수동으로 교차 점검

## 위험과 후속 작업

이번 정리는 통합 기능을 완성하지 않는다. A snapshot과 B 저장, 프로젝트 삭제, AI 계약을 실제로 연결하려면 별도 합의된 API/DB 계약과 전용 통합 테스트가 필요하다. 이 작업에서 그것들을 구현한 것처럼 문서화하지 않는다.
