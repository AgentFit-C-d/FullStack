import { canEnterDemoRecommendation } from '../recommendation/recommendationModel.js'
import { permissionExamples, selectionCardExamples } from './selectionDemoFixture.js'

export const unqueriedSelectionData = Object.freeze({ tools: null, permissions: null })
export function createSelectionPreview() {
  return { source: 'demo-ui', visible: false, selectedIds: ['ui-required'], permission: 'ASK_EACH_TIME', permissionDetailsOpen: false }
}
export function canEnterDemoSelection(state, id) {
  return canEnterDemoRecommendation(state.demoProfiles[id], state.roleDrafts[id], state.environmentDrafts[id]) && state.selectionPreviews[id]?.source === 'demo-ui'
}
export function editSelectionPreview(entry, action) {
  if (entry.source !== 'demo-ui') return entry
  if (action.edit === 'visibility' && typeof action.value === 'boolean') return { ...entry, visible: action.value }
  if (!entry.visible) return entry
  if (action.edit === 'permission' && permissionExamples.some(([value]) => value === action.value)) return { ...entry, permission: action.value }
  if (action.edit === 'details') return { ...entry, permissionDetailsOpen: !entry.permissionDetailsOpen }
  if (action.edit === 'tool') {
    const tool = selectionCardExamples.find(item => item.id === action.toolId)
    if (!tool || tool.required) return entry
    return { ...entry, selectedIds: entry.selectedIds.includes(tool.id) ? entry.selectedIds.filter(id => id !== tool.id) : [...entry.selectedIds, tool.id] }
  }
  return entry
}
export function validateSelectionPreview(entry) {
  if (!entry.visible) return '실제 추천 결과가 없어 다음 단계로 진행할 수 없습니다.'
  if (!permissionExamples.some(([value]) => value === entry.permission)) return '시연 권한 상태를 확인해주세요.'
  if (entry.permission === 'DENY' && selectionCardExamples.some(item => item.required && entry.selectedIds.includes(item.id))) return '시연 필수 카드와 허용하지 않음(DENY)의 조합입니다. 이 예시에서는 Preview 진행을 막습니다. 실제 필수 권한을 판정한 것은 아닙니다.'
  return ''
}
export function describeSelectionData(tools) {
  if (tools === null) return '추천 도구 미조회'
  return tools.length ? '추천 도구 목록 있음' : '조회된 추천 도구 없음'
}
