import { environmentClient } from '../environment/clientDefinition.js'
import { canEnterDemoEnvironment, validateEnvironment } from '../environment/environmentModel.js'
import { capabilityOptions, necessityOptions, previewStates } from './recommendationDemoFixture.js'

// Client-side view model, not a server response or a recommendation policy.
export const unexecutedRecommendation = Object.freeze({ status: 'NOT_RUN', capabilities: null, tools: null })
export function createRecommendationPreview() {
  return { source: 'demo-ui', visible: false, status: 'NEEDS_INFORMATION', necessities: Object.fromEntries(capabilityOptions.map(([key]) => [key, 'UNDETERMINED'])) }
}
export function canEnterDemoRecommendation(profile, role, environment) {
  return canEnterDemoEnvironment(profile, role) && !!environment && environment.source === 'demo-user' && environment.clientId === environmentClient.id && environment.confirmed === true && !Object.keys(validateEnvironment(environment.draft)).length
}
export function editRecommendationPreview(entry, action) {
  if (entry.source !== 'demo-ui') return entry
  if (action.edit === 'visibility' && typeof action.value === 'boolean') return { ...entry, visible: action.value }
  if (!entry.visible) return entry
  if (action.edit === 'status' && previewStates.some(([status]) => status === action.value)) return { ...entry, status: action.value }
  if (action.edit === 'necessity' && capabilityOptions.some(([key]) => key === action.key) && necessityOptions.some(([value]) => value === action.value)) return { ...entry, necessities: { ...entry.necessities, [action.key]: action.value } }
  return entry
}
export function describeRecommendationResult(result) {
  if (result.status === 'NOT_RUN') return '추천 미실행'
  if (result.status === 'UNAVAILABLE') return '추천 서비스를 이용할 수 없어요'
  if (result.status === 'FAILED') return '추천 요청 실패'
  if (result.tools === null) return '추천 결과 데이터 없음'
  if (Array.isArray(result.tools) && !result.tools.length) return '확인된 추천 도구 없음'
  return '추천 결과 확인 필요'
}
