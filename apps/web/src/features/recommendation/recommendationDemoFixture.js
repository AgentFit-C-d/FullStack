// Designer-defined UI examples only; no client, ranking, inferred requirements, or tools.
export const capabilityOptions = [
  ['document_reference', '문서 참조'], ['repository_exploration', '저장소 탐색'],
  ['code_review', '코드 검토'], ['test_authoring', '테스트 작성'],
  ['test_execution', '테스트 실행'], ['browser_validation', '브라우저 검증'],
  ['api_validation', 'API 검증'], ['database_schema_inspection', '데이터베이스 스키마 검사'],
  ['ai_evaluation', 'AI 평가'],
]
export const necessityOptions = [['REQUIRED', '필수 (REQUIRED)'], ['OPTIONAL', '선택 (OPTIONAL)'], ['UNDETERMINED', '미정 (UNDETERMINED)']]
export const previewStates = [
  ['RECOMMENDED', '도구 추천', '검증된 추천 도구는 아직 표시할 수 없습니다.', '현재 시안에는 Catalog와 추천 분석이 연결되지 않았습니다. 실제 도구명·버전·필수 여부·추천 이유는 검증된 결과가 있을 때만 표시됩니다.'],
  ['NO_ADDITIONS_NEEDED', '추가 도구 불필요', '추가 도구 불필요 상태 미리보기', '실제 기존 능력과 필수 Capability를 확인하기 전에는 이 상태를 서비스 결과로 확정하지 않습니다.'],
  ['NEEDS_INFORMATION', '추가 정보 필요', '추가 정보 필요 상태 미리보기', '미정 Capability와 필요한 질문을 실제 분석으로 확인하기 전까지 추천을 확정하지 않습니다.'],
  ['NO_COMPATIBLE_TOOLS', '호환 가능한 도구 없음', '호환 가능한 도구 없음 상태 미리보기', '검증된 지원 조합이 연결되기 전에는 호환 여부를 확정하지 않습니다.'],
]
