import test from 'node:test'
import assert from 'node:assert/strict'
import { demoReducer, initialDemoState } from '../src/features/projects/projectModel.js'
import { canEnterDemoRecommendation, describeRecommendationResult, unexecutedRecommendation } from '../src/features/recommendation/recommendationModel.js'
function session() {
  let state = demoReducer(initialDemoState, { type: 'enter' })
  for (const id of ['a', 'b']) {
    for (const action of [{ type: 'create', name: id }, { type: 'profile-preview' }, { type: 'profile-edit', edit: 'confirm' }, { type: 'role-confirm' }, { type: 'environment-confirm' }]) state = demoReducer(state, { ...action, id })
  }
  return state
}
const preview = (state, action, id = 'a') => demoReducer(state, { type: 'recommendation-preview', id, ...action })

test('유효 확인된 환경에서만 진입하고 기존 입력·이전 단계 데이터는 보존한다', () => {
  let state = session()
  assert.equal(canEnterDemoRecommendation(state.demoProfiles.a, state.roleDrafts.a, state.environmentDrafts.a), true)
  assert.equal(canEnterDemoRecommendation(state.demoProfiles.a, state.roleDrafts.a, undefined), false)
  const previous = { profile: state.demoProfiles.a, role: state.roleDrafts.a, environment: state.environmentDrafts.a }
  state = preview(state, { edit: 'visibility', value: true })
  assert.equal(state.demoProfiles.a, previous.profile)
  assert.equal(state.roleDrafts.a, previous.role)
  assert.equal(state.environmentDrafts.a, previous.environment)
  state = demoReducer(state, { type: 'environment-edit', id: 'a', edit: 'scalar', field: 'osVersion', value: 'x'.repeat(201) })
  state = demoReducer(state, { type: 'environment-confirm', id: 'a' })
  assert.equal(canEnterDemoRecommendation(state.demoProfiles.a, state.roleDrafts.a, state.environmentDrafts.a), false)
  assert.equal(preview(state, { edit: 'visibility', value: true }), state)
})

test('미리보기 선택은 명시적으로 연 뒤에만 가능하고 프로젝트별로 격리한다', () => {
  let state = session()
  assert.deepEqual(state.recommendationPreviews, {})
  assert.equal(preview(state, { edit: 'necessity', key: 'code_review', value: 'REQUIRED' }), state)
  state = preview(state, { edit: 'visibility', value: true })
  state = preview(state, { edit: 'necessity', key: 'code_review', value: 'REQUIRED' })
  state = preview(state, { edit: 'status', value: 'RECOMMENDED' })
  state = preview(state, { edit: 'visibility', value: true }, 'b')
  assert.equal(state.recommendationPreviews.a.source, 'demo-ui')
  assert.equal(state.recommendationPreviews.b.necessities.code_review, 'UNDETERMINED')
  state = preview(state, { edit: 'visibility', value: false })
  state = demoReducer(state, { type: 'environment-confirm', id: 'a' })
  state = preview(state, { edit: 'visibility', value: true })
  assert.equal(state.recommendationPreviews.a.necessities.code_review, 'REQUIRED')
  assert.equal(state.recommendationPreviews.a.status, 'RECOMMENDED')
  assert.equal(preview(state, { edit: 'status', value: 'FAILED' }), state)
  assert.equal(preview(state, { edit: 'necessity', key: 'invented', value: 'REQUIRED' }), state)
  assert.equal(preview(state, { edit: 'visibility', value: true }, 'missing'), state)
  assert.deepEqual(demoReducer(state, { type: 'exit' }).recommendationPreviews, {})
})

test('시연 선택은 실제 결과를 만들지 않고 미실행·null·빈 목록·오류를 구분한다', () => {
  let state = session()
  state = preview(state, { edit: 'visibility', value: true })
  state = preview(state, { edit: 'status', value: 'NO_ADDITIONS_NEEDED' })
  assert.deepEqual(unexecutedRecommendation, { status: 'NOT_RUN', capabilities: null, tools: null })
  assert.equal(Object.hasOwn(state, 'recommendationResults'), false)
  assert.equal(Object.hasOwn(state, 'analysisStatus'), false)
  assert.equal(describeRecommendationResult(unexecutedRecommendation), '추천 미실행')
  assert.equal(describeRecommendationResult({ status: 'RECEIVED', tools: null }), '추천 결과 데이터 없음')
  assert.equal(describeRecommendationResult({ status: 'RECEIVED', tools: [] }), '확인된 추천 도구 없음')
  assert.equal(describeRecommendationResult({ status: 'FAILED', tools: [] }), '추천 요청 실패')
  assert.equal(describeRecommendationResult({ status: 'RECEIVED', tools: [{ name: '계약이 필요한 값' }] }), '추천 결과 확인 필요')
})
