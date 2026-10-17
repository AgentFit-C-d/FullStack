# 연구 및 설계 결정

## 기존 HMAC 계약 유지

**결정**: 기존 `/internal/v1/analysis`의 HMAC canonical string, timestamp, key rotation, replay 방지를 유지하고 `needs`와 `rank`에도 적용한다.

**근거**: 현재 `HttpAiAnalysisClient`와 AI 서비스 인증 로직이 이 경계를 이미 구현한다.

**대안**: AI v2 Bearer 토큰을 그대로 사용.
**제외 이유**: 기존 Core 호출 계약과 키 회전·재전송 방지가 이중화된다.

## AI v2에서 이식할 범위

**결정**: 문서 추출, 입력 한도, Solar/Information Extract 공급자 호출, 인용·근거 검증, facts·needs·candidate ranking 계산만 `services/ai-service/app/ai_v2/`로 이식한다.

**근거**: AI v2는 Express API, 로그인, DB, Skill 검색, 패키지 검증, 최종 추천 선택을 별도 서비스 책임으로 명시한다.

**대안**: AI v2 FastAPI 앱 전체를 교체.
**제외 이유**: HMAC과 Core 단일 DB·권한 소유 원칙을 깬다.

## 원문 처리

**결정**: Core가 권한 검사 후 PDF·Markdown·직접 입력 원문 bytes를 AI 처리에만 전달하고, 분석 terminal 상태 뒤 원문과 전체 추출문을 모두 폐기한다. DB에는 구조화 A DRAFT와 최소 Audit만 남긴다. 사용자의 나중 확인은 원문이 아닌 DRAFT를 대상으로 하며, CONFIRMED 생성도 DRAFT를 삭제하지 않는다.

**대안**: PDF를 재분석용으로 장기 보관.
**제외 이유**: 이번 결정과 데이터 최소화 원칙에 어긋난다. 재분석은 파일 재제출로 한다.

## 최종 분석 실패 진단

**결정**: 실제 수신한 최종 분석 실패 응답만 시도당 암호화 1건으로 최대 7일 보관할 수 있다. 정상 응답·미수신·원문은 대상이 아니며, 일반 로그·DTO·Audit에는 넣지 않는다.

**근거**: 정상 입력과 정상 AI 원시 응답을 보관하지 않으면서도 실제 연동 장애는 제한적으로 진단할 수 있다.

**대안**: 모든 provider 응답을 보관하거나 실패 진단도 전혀 보관하지 않는다.
**제외 이유**: 전자는 데이터 최소화 원칙을 깨고, 후자는 실제 수신 실패의 원인 추적을 불필요하게 어렵게 만든다.

## A 확인 뒤 B의 Capability 판단

**결정**: B는 사용자 확인을 통과한 `OwnerCheckedSnapshot`의 허용 필드만 AI에 전달하고, 결과를 기존 9개 고정 Capability claim과 질문으로 변환·검증한다.

**근거**: 현재 `RecommendationCreationService`, `AiCapabilityAnalyzerAdapter`, `AiCapabilityIntake`가 이 경계를 제공한다. 자유형 AI v2 need를 B에 직접 저장하지 않는다.

## rank의 보류

**결정**: `/internal/v1/skills/rank`는 후보 점수를 반환하지만 B 담당자가 매핑·승인 규칙을 확정하기 전에는 recommendation item·status·저장에 영향을 주지 않는다.

**근거**: AI v2 rank는 외부 후보 점수화이고, 현재 B의 최종 결과는 승인 Catalog와 검증 조합에 근거한다.

## deadline

**결정**: Core가 deadline을 소유하고 AI에는 남은 시간과 178초 상한 중 더 이른 시각을 적용한다.

**근거**: Core의 attempt generation·persistence guard와 AI v2의 180초 요청 예산을 일치시킨다.

## 참조

- AI v2: <https://github.com/AgentFit-C-d/AgentFit_AI_v2>
- 현재 AI 계약: `contracts/openapi/ai-service.yml`
- B 경계: `services/core-api/src/main/java/com/agentfit/coreapi/catalog/RecommendationCreationService.java`
- 기준 문서: `Docs/AgentFit_AB_1차통합_개발기준.pdf`
