# 풀스택 B 작업 안내

이 폴더는 지금까지 개발한 B 영역을 **Catalog → 추천·권한 → Preview·설정** 순서로 찾기 위한 입구다. 팀 기준은 [1차 AB 통합 문서](../AgentFit_AB_1차통합_개발기준.pdf)이며, 아래 설계·코드는 그중 B 담당 영역의 진행 결과다.

## 먼저 읽을 파일

1. [현재 상태](STATUS.md): B01–B12 체크리스트와 구현/미구현 경계.
2. [다음 작업](NEXT-STEPS.md): A·AI·팀 검증에 의존하는 항목과 후속 구현 순서.
3. [A·AI 연동 계약 제안](integration/ab-ai-contract-proposal.md): 입력·출력·미합의 항목과 계약 테스트 사례.
4. 필요한 기능 폴더의 안내: [Catalog](catalog/README.md) · [추천·선택](recommendation/README.md) · [Preview·설정](configuration/README.md).
5. 이전 단계별 빌드 결과는 [이력](HISTORY.md)에 따로 보관했다.

## 문서 구조

```text
FullStack/Docs/
├─ AgentFit_AB_1차통합_개발기준.pdf  # 팀 공통 기준
├─ README.md                         # 전체 문서 입구
└─ fullstack-b/
   ├─ README.md                      # 지금 읽는 파일
   ├─ STATUS.md                      # 현재 체크리스트
   ├─ NEXT-STEPS.md                  # 다음 작업·의존성
   ├─ HISTORY.md                     # 단계별 검증 이력
   ├─ integration/                   # A·AI 연동 계약 제안과 미합의 항목
   ├─ catalog/                       # 릴리스·해시·스키마·호환 조합
   ├─ recommendation/                # 추천·도구/권한 선택
   └─ configuration/                 # 조립 → 비교·지문 → 승인·ZIP
      ├─ assembly/                   # Catalog 템플릿·조립·크기 예산 설계
      ├─ preview/                    # 비교·민감정보·지문·신선도 설계
      └─ export/                     # 승인·ZIP 내보내기 설계
```

## Java 파일 찾기

`services/core-api/src/main/java/com/agentfit/coreapi/`를 기준으로 찾는다. 테스트도 `src/test/java` 아래 같은 패키지 구조를 따른다.

| 찾으려는 기능 | Java 폴더 | 문서 |
| --- | --- | --- |
| 릴리스 파일·스키마·템플릿 검증과 Preview 조립 | `catalog/` | [Catalog](catalog/README.md) · [설정 조립](configuration/README.md) |
| Catalog의 도구·지원·조합 모델 | `catalog/model/` | [Catalog](catalog/README.md) |
| Capability 및 결정적 추천 | `recommendation/` | [추천](recommendation/README.md) |
| AI Capability 후보의 내부 검증 | `recommendation/ai/` | [AI 후보 검증](recommendation/ai-capability-intake-plan.md) |
| 도구 선택·권한 정책 검증 | `recommendation/selection/` | [선택·권한](recommendation/README.md) |
| 파일 비교·민감정보·지문 | `configuration/preview/` | [Preview](configuration/README.md) |
| 만료·승인·ZIP 내보내기 | `configuration/export/` | [내보내기](configuration/README.md) |
| 사용자 적용 보고 검증 | `configuration/report/` | [적용 보고](configuration/report/user-application-report-plan.md) |
| 최소 생성 이력 메타데이터 | `configuration/history/` | [생성 이력](configuration/history/generation-history-plan.md) |

B 코드는 `feat/fullstack-b-phase1` 브랜치에서 기능별 패키지로 관리한다. 이 문서에서 말하는 구현은 외부 서비스·DB에 연결되지 않은 경계 코드다.

## 현재 구현 수준

| 영역 | 구현된 범위 | 아직 필요한 것 |
| --- | --- | --- |
| 추천·선택 | 9개 Capability, AI 후보의 내부 검증, 결정적 추천 결과, 정확한 OS/Client/버전 지원·조합·의존성·충돌·권한 검증 | A의 현재 스냅샷, AI 전송 계약·실패 처리, 추천 저장·API |
| Catalog | 매니페스트 SHA-256 검증, 엄격한 합성 v1 스키마, 템플릿 출처·승인 해시 확인 | 실제 Client 지원·템플릿 검증과 승인된 릴리스 |
| Preview·설정 | 임시 비교·Diff, 식별 가능한 Secret 검사, 지문 계산, 저장된 승인 메타데이터를 검사하는 동일 바이트 ZIP 재생성, Catalog 기반 임시 Preview 연결, 사용자 적용 보고 값 검증, 최소 생성 이력 메타데이터 | 인증/소유권, 승인·만료·감사·DB, 공개 API·화면 연동 |

마지막 전체 검증은 `mvn clean verify`에서 **112개 테스트 발견, 111개 통과, 1개 건너뜀**이었다. 건너뛴 테스트는 이 Windows 환경에서 심볼릭 링크 생성 권한이 없어 실행되지 않은 파일시스템 테스트다. JDK 24로 Java 21 대상 바이트코드를 만들었으며 Java 21 런타임 실행은 아직 확인하지 못했다. 세부 검증은 [HISTORY.md](HISTORY.md)에 기록했다.

**1차 B 상위 목표 B01–B12 기준: 4/12 완료(33.3%), 8개 남음.** B08–B11의 메모리 검증·생성 하위 작업은 진행됐지만, 실제 Catalog·A/AI 계약·인증/DB/API와 연결되지 않아 상위 목표 완료로 계산하지 않았다.

현재 코드는 도메인/메모리 경계의 구현이다. **실제 Catalog 데이터, PostgreSQL 영속화, 인증된 B API, AI 연동, 사용자 승인 흐름은 완료되지 않았다.**
1차 실제 Catalog는 **Claude Code부터**, Codex는 후속이다. 현재 Java 테스트의 `example-client`/`1.0` 값은 합성 환경 fixture이며 실제 지원 증거가 아니다. 각 Client의 지원 버전·템플릿·권한 매핑·조합은 검증된 Catalog가 준비되기 전까지 활성화하지 않는다.

## 재개할 때

`services/core-api`에서 다음을 실행한다. 로컬 JDK와 Maven 경로는 환경에 맞게 선택한다.

```text
mvn package
git diff --check
```

구현은 [NEXT-STEPS.md](NEXT-STEPS.md)의 계약 확인부터 시작하고, 팀에서 합의하지 않은 Client 버전·도구 조합을 `PASS`로 채우지 않는다.
