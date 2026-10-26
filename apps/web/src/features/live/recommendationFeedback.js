const capabilityLabels = {
  cap_document_reference: '문서 참조',
  cap_repository_navigation: '저장소 탐색',
  cap_code_review: '코드 검토',
  cap_test_authoring: '테스트 작성',
  cap_test_execution: '테스트 실행',
  cap_browser_verification: '브라우저 검증',
  cap_api_verification: 'API 검증',
  cap_database_schema_inspection: 'DB 스키마 점검',
  cap_ai_evaluation: 'AI 평가',
}

export function requiredCapabilityLabels(recommendation) {
  return [...new Set((recommendation?.capabilities || [])
    .filter(item => (item.necessity || item.need) === 'REQUIRED')
    .map(item => capabilityLabels[item.key || item.capabilityKey])
    .filter(Boolean))]
}

export function recommendationFeedback(recommendation) {
  if (recommendation?.validity === 'STALE') {
    return '입력이 바뀌어 이전 추천이 만료됐습니다. 추천을 다시 생성해 주세요.'
  }

  switch (recommendation?.status) {
    case 'NEEDS_INFORMATION': {
      const questions = {
        pending_conflict: '기획의 충돌 항목을 검토해 주세요.',
        undetermined_capability: '기획·담당 업무를 구체적으로 보완해 주세요.',
        environment_target: 'OS·Client와 버전을 확인해 주세요.',
        installed_components: '설치 도구 목록을 확인해 주세요.',
        installed_component_version: '설치 도구의 버전을 확인해 주세요.',
      }
      const instructions = [...new Set((recommendation.notices || [])
        .filter(notice => notice.type === 'QUESTION')
        .map(notice => questions[notice.code]).filter(Boolean))]
      return instructions.length
        ? `${instructions.join(' ')} 저장한 뒤 추천을 다시 실행해 주세요.`
        : '추천에 추가 정보가 필요합니다. 질문·안내 내용을 확인해 주세요.'
    }
    case 'NO_COMPATIBLE_TOOLS': {
      const reasons = new Set((recommendation.notices || []).map(notice => notice.code))
      if (reasons.has('no_verified_capability_support')) {
        return '필수 기능을 지원하는 승인된 도구가 없습니다. 카탈로그 검증 범위를 확인해 주세요.'
      }
      if (reasons.has('no_verified_compatible_combination')) {
        return '현재 환경에서 필수 기능을 모두 충족하는 승인된 도구 조합이 없습니다.'
      }
      return '현재 조건에 맞는 승인된 도구가 없습니다. 환경과 카탈로그 지원 범위를 확인해 주세요.'
    }
    case 'NO_ADDITIONS_NEEDED':
      return (recommendation.notices || []).some(notice => notice.code === 'installed_components_cover_required')
        ? '이미 설치된 검증 도구가 필요한 기능을 충족해 추가 도구가 필요하지 않습니다.'
        : '필요한 기능이 확인되지 않아 추가 도구가 필요하지 않습니다.'
    case 'RECOMMENDED':
      return ''
    default:
      return '추천 결과를 확인해 주세요.'
  }
}
