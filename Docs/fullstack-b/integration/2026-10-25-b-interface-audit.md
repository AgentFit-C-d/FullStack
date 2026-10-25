# B 추천·설정 연동 점검 — 2026-10-25

## 기준

- 코드: `feat/fullstack-b-finalization`의 `apps/web`, `services/core-api`, `services/ai-service`.
- 별도 `D:/capstone/Frontend`의 `main`(`0c39dad`)은 단계별 서비스 화면의 원본이다. 이 화면은 아직 API 데이터 폼으로 연결되지 않았다. 레포의 `/app/live`가 실제 백엔드 연동 화면이다.
- 변경 범위: B 소유 Java와 이 문서. A, AI, Frontend 소스는 변경하지 않는다.

## 호출 경로와 확인 결과

| 경로 | 현재 상태 |
| --- | --- |
| `/app/live` → 역할·환경 저장 → 추천 POST/최신 GET | 연결됨. A의 CONFIRMED Profile, 버전 및 소유권, AI `needs`, B 카탈로그 판정을 사용한다. |
| AI `needs` → B Capability | 9개 키와 `NOT_REQUESTED`를 받아 검증·DB 저장한다. `rank`는 실제 추천 선택 경로에 참여하지 않는다. |
| `NEEDS_INFORMATION` 질문 → 프론트 답변 | 프론트는 `noticeId`별 목록·저장을 지원한다. B가 AI 질문 중 첫 건만 보존하던 문제를 수정해 여러 질문을 각각 저장한다. 답변은 기록이며 확정 기획이나 재추천 입력을 자동 변경하지 않는다. |
| 추천 → 선택·권한 → Preview·승인·ZIP·이력 | 합성 승인 카탈로그를 사용한 백엔드/브라우저 데모 경로가 있다. 이번 점검에서는 질문 저장 관련 B 회귀 테스트를 실행했다. |
| skills.sh 후보 → 승인 카탈로그 | 검색·상세 해시 수집과 `GET /api/tool-candidates` 조회까지만 연결된다. OS·Claude Code 버전·권한·설정 출력 검증이 없어 추천·ZIP에는 들어가지 않는다. |

## FSB-01–12 상위 기능 상태

아래 수치는 **데모 범위의 기능 경로**를 세며, 실도구 출시 완료율이 아니다. 이 문서의 `FSB`는 역할 PRD의 요구사항 ID이고, 오래된 `STATUS.md`의 `B01–B12` 구현 마일스톤과 다르다.

| 요구 | 데모 범위 판정 | 남은 핵심 |
| --- | --- | --- |
| FSB-01 검증 도구 Catalog | 부분 | 실제 Claude Code 도구 15–20개 검증·승인 미완 |
| FSB-02 Plugin·컴포넌트 관계 | 동작 | 실제 도구 데이터 적용 |
| FSB-03 Capability·환경 호환 판정 | 동작 | 실제 지원 기록 적용 |
| FSB-04 네 결과·최소 구성 | 부분 | 선택 도구 구분 및 AI `OPTIONAL` 의미 합의 |
| FSB-05 추천 이유·근거 | 부분 | 개별 추천 이유와 위험도를 서비스 화면에 명확히 표시 |
| FSB-06 권한 대응표 | 동작 | 실제 Client 정책의 검증 근거 적용 |
| FSB-07 권한 선택·기본 정책 | 동작 | 실제 권한 정책 적용 검증 |
| FSB-08 고정 설정 생성 | 동작 | 실제 Claude Code 출력 검증 |
| FSB-09 기존 설정 비교 | 동작 | 제공 파일 이외의 상태는 알 수 없으며 자동 병합은 미구현 |
| FSB-10 승인·산출물 일치 | 동작 | 운영 환경 회귀 검증 |
| FSB-11 적용·인증·확인 안내 | 부분 | 실제 Client 설치·연결·무해한 확인 절차 검증 |
| FSB-12 변경 영향·이력 | 부분 | 실도구 버전 변경 재검토와 Catalog 갱신 운영 |

데모 기준 **7/12개가 현재 연결 경로로 동작하고, 5/12개는 부분 구현**이다. 7/12는 엄격한 제품 완료율이나 실제 Claude Code 지원율이 아니다. 실제 검증·승인 Catalog가 현재 최대 차단 요인이다.

## 이번 B 수정과 프론트 인계

- B 저장 경로는 AI가 반환한 모든 `clarificationQuestions`를 각각 `recommendation_notice`로 저장한다. 동일한 `code=undetermined_capability`여도 `noticeId`와 `answerTarget`은 항목별로 다르다.
- 프론트는 이미 `noticeId`별 질문 목록·답변 API를 사용하므로 소스 수정 없이 추가 질문을 표시할 수 있다. 다만 별도 Frontend 레포의 단계별 `/app/projects/...` 화면은 여전히 `/app/live`로 이동하며 개별 API 바인딩은 프론트 작업이다.
- B API의 추천 항목에는 지원 근거·충족 Capability·권한 옵션이 있지만 `/app/live`는 현재 도구명과 권한 선택 위주로 보여준다. 추천 이유를 사용자에게 보여주는 화면 작업과 이유 문구 계약은 후속 합의가 필요하다.
