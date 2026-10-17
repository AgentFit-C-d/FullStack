# Requirements Quality Checklist: 분석 Worker 연결과 A→확인→B 데모 흐름 완성

**Purpose**: 풀스택 A의 남은 작업 범위와 인계 조건이 구현·검증 가능한 요구사항으로 정리되었는지 확인한다.
**Created**: 2026-10-17
**Feature**: [spec.md](../spec.md)

**Review Ownership**: This checklist is a reviewer-owned requirements-quality review artifact. Mark an item `[x]` only when the reviewer determines the requirements-quality criterion is satisfied.
**Marker Semantics**: `[x]` means the criterion has been reviewed and satisfied for requirements quality. It does not mean implementation work is complete.

## 명세 범위

- [x] CHK001 현재 남은 세 가지 작업(Worker, PostgreSQL 정합성, Docker 데모)이 하나의 기능 범위로 명시되어 있다.
- [x] CHK002 A 분석, DRAFT 저장, 원문 제거, 사용자 확정, B 추천의 순서와 분리 관계가 명시되어 있다.
- [x] CHK003 프론트엔드 구현이 이번 기능의 완료 조건에 포함되지 않으며 백엔드 실행으로 검증한다고 명시되어 있다.
- [x] CHK004 풀스택 B에게 넘기는 세 가지 인계 조건이 명시되어 있다.

## 사용자 시나리오와 상태

- [x] CHK005 각 사용자 스토리는 독립 테스트 방법과 우선순위를 가진다.
- [x] CHK006 분석 성공, 분석 실패, 시간 초과, 중복 처리, 미확정 추천 요청의 기대 결과가 명시되어 있다.
- [x] CHK007 `NEEDS_INFORMATION`, `NO_COMPATIBLE_TOOLS`, 연동 실패를 구분하는 요구사항이 명시되어 있다.
- [x] CHK008 사용자가 나중에 원문 없이 DRAFT를 확인·수정·확정할 수 있다는 데이터 수명 규칙이 명시되어 있다.

## 데이터와 보안 경계

- [x] CHK009 원문·전체 추출문은 terminal 상태 이후 보관하지 않는다는 요구사항이 성공·실패 경로 모두에 적용되어 있다.
- [x] CHK010 A DRAFT와 CONFIRMED Profile의 역할 및 B 추천 진입 조건이 구분되어 있다.
- [x] CHK011 PostgreSQL 식별자 타입, 상태 컬럼, 상태 전이 정합성 요구가 명시되어 있다.
- [x] CHK012 HMAC 공유 설정과 Solar 키의 환경 주입 및 비노출 요구가 명시되어 있다.

## 검증 가능성

- [x] CHK013 Worker가 실제 AI 호출부터 결과 저장까지 수행해야 한다는 검증 기준이 있다.
- [x] CHK014 Docker에서 analysis·needs·rank 내부 연결을 검증할 수 있는 구체적 목적지가 명시되어 있다.
- [x] CHK015 결정적 제공자를 사용한 키 없는 데모와 Solar 제공자 교체 조건이 명시되어 있다.
- [x] CHK016 구현 완료가 아닌 요구사항 품질 검토 문서임을 체크리스트에 명시했다.

## Notes

- 이 체크리스트의 모든 항목이 요구사항 품질 기준을 충족한다는 의미로 `[x]` 처리했다.
- 체크가 완료되었다는 뜻은 구현이 끝났다는 뜻이 아니다. 실제 구현과 검증은 다음 plan/tasks/implement 단계에서 수행한다.
