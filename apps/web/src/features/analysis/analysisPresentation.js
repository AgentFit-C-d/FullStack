// View-only states. Authentication does not select or advance an analysis state.
const states = {
  unavailable: { title: '기획 자료 분석을 준비하고 있어요', heading: '분석 서비스를 이용할 수 없어요', description: '현재 분석 서비스를 이용할 수 없습니다. 잠시 후 다시 확인해주세요.' },
  queued: { title: '기획 자료 분석을 기다리고 있어요', heading: '분석 대기', description: '분석 요청이 대기 중입니다.' },
  processing: { title: '기획 자료를 분석하고 있어요', heading: 'AI 분석 진행 중', description: '프로젝트의 주요 기능과 기술 구성을 확인하고 있습니다.' },
  completed: { title: '기획 자료 분석이 완료되었어요', heading: '분석 완료', description: '분석된 Project Profile을 확인해주세요.' },
  failed: { title: '기획 자료를 분석하지 못했어요', heading: '분석 실패', description: '분석에 실패했습니다. 입력 자료를 확인한 뒤 다시 시도해주세요.' },
}
export function getAnalysisPresentation(status, mode = 'service') {
  const safeStatus = Object.hasOwn(states, status) ? status : 'unavailable'
  const view = states[safeStatus]
  return mode === 'demo' && safeStatus === 'unavailable'
    ? { ...view, heading: 'AI 분석 연결 전', description: '현재는 실제 AI 요청을 보내지 않아 분석 결과가 생성되지 않습니다.' }
    : view
}
export function getProfilePresentation(mode = 'service') {
  return mode === 'demo'
    ? { title: '프로젝트 정보를 확인해주세요', description: '시연용 편집 화면입니다. AI 분석 결과가 아닙니다.', valueLabel: '시연 편집값', confirmLabel: '전체 Project Profile 확인하기 (시연) →' }
    : { title: '프로젝트 정보를 확인해주세요', description: '잘못되었거나 빠진 내용이 있다면 직접 수정해주세요.', valueLabel: '사용자 입력', confirmLabel: '전체 Project Profile 확인하기 →' }
}
