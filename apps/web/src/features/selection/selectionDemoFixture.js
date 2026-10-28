// Abstract interaction samples, not Catalog tools, compatibility claims, or permissions.
export const selectionCardExamples = [
  { id: 'ui-required', name: '필수 카드 UI 예시', required: true, description: '필수 카드 선택 고정 동작을 확인하는 가상 카드입니다. 실제 도구·추천·필수 권한이 아닙니다.' },
  { id: 'ui-optional', name: '선택 카드 UI 예시', required: false, description: '선택·해제 동작을 확인하는 가상 카드입니다. 실제 도구·추천·호환 정보가 아닙니다.' },
]
export const permissionExamples = [['ALWAYS_ALLOW', '항상 허용 (ALWAYS_ALLOW)'], ['ASK_EACH_TIME', '사용할 때마다 확인 (ASK_EACH_TIME)'], ['DENY', '허용하지 않음 (DENY)']]
