# A-B Phase 2 검증 가이드

## 전제

- JDK 21, Maven, PostgreSQL
- 개발 DB는 기존 데이터를 보존하지 않고 초기화
- Flyway A15+B9+Audit migration 적용
- 승인된 Catalog와 내부 AI 설정

## 개발 DB 초기화 절차

Phase 2는 기존 개발 DB를 변환하지 않는다. 기존 개발 DB를 폐기하고 새 빈 DB를 생성한 뒤
V1~V5 전체 migration을 적용한다. 기존 `USER`, `PROJECT`, session 및 종속 데이터는
보존하거나 UUID로 mapping하지 않는다.

예시 PostgreSQL 절차:

```powershell
dropdb agentfit_dev
createdb agentfit_dev
cd services/core-api
mvn -B -ntp -DskipTests flyway:migrate
```

실제 접속 정보는 로컬 환경 변수로 주입하며 저장소에 남기지 않는다.

## 핵심 검증

```powershell
cd services/core-api
mvn -B -ntp test
```

검증 항목:

- UUID USER/PROJECT와 전체 A/B FK 생성
- 타 프로젝트·미인증·미확정·stale basis 거부
- `POST /api/projects/{projectId}/recommendations`
- `GET /api/projects/{projectId}/recommendations/latest`
- `POST /api/projects/{projectId}/config-previews`
- AI 실패/누락 응답 시 기존 추천 유지
- 업무 상태와 Audit 원자성
- B 데이터 선삭제 후 A 프로젝트 삭제
- 삭제 후 late result 거부

## 주의

PDF의 예시와 인수 사례는 NOT_RUN 상태였다. migration·OpenAPI·실제 테스트가 통과하기 전에는 통합 완료로 표시하지 않는다.

