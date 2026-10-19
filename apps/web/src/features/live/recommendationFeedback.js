export function recommendationFeedback(recommendation) {
  if (recommendation?.validity === 'STALE') {
    return '입력이 바뀌어 이전 추천이 만료됐습니다. 추천을 다시 생성해 주세요.'
  }

  switch (recommendation?.status) {
    case 'NEEDS_INFORMATION':
      return '필요한 작업을 확정하지 못했습니다. 기획·담당 업무를 구체적으로 보완한 뒤 추천을 다시 실행해 주세요.'
    case 'NO_COMPATIBLE_TOOLS':
      return '현재 OS·Client 버전에 승인된 호환 도구가 없습니다. 환경을 확인한 뒤 추천을 다시 실행해 주세요.'
    case 'NO_ADDITIONS_NEEDED':
      return '필요한 기능을 이미 충족해 추가 도구가 필요하지 않습니다.'
    case 'RECOMMENDED':
      return ''
    default:
      return '추천 결과를 확인해 주세요.'
  }
}
