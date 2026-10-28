import test from 'node:test'
import assert from 'node:assert/strict'
import { demoReducer, initialDemoState } from '../src/features/projects/projectModel.js'
import { canEnterDemoSelection, describeSelectionData, unqueriedSelectionData, validateSelectionPreview } from '../src/features/selection/selectionModel.js'
function session() {
  let state = demoReducer(initialDemoState, { type: 'enter' })
  for (const id of ['a', 'b']) for (const action of [{ type: 'create', name: id }, { type: 'profile-preview' }, { type: 'profile-edit', edit: 'confirm' }, { type: 'role-confirm' }, { type: 'environment-confirm' }]) state = demoReducer(state, { ...action, id })
  return state
}
const edit = (state, action, id = 'a') => demoReducer(state, { type: 'selection-preview', id, ...action })

test('Selection은 명시적인 시연 진입만 허용하고 실제 추천 결과를 만들지 않는다', () => {
  let state = session()
  assert.equal(canEnterDemoSelection(state, 'a'), false)
  state = demoReducer(state, { type: 'selection-enter', id: 'a' })
  assert.equal(canEnterDemoSelection(state, 'a'), true)
  assert.equal(state.selectionPreviews.a.visible, false)
  assert.deepEqual(unqueriedSelectionData, { tools: null, permissions: null })
  assert.equal(Object.hasOwn(state, 'recommendationResults'), false)
  assert.equal(describeSelectionData(null), '추천 도구 미조회')
  assert.equal(describeSelectionData([]), '조회된 추천 도구 없음')
  assert.equal(describeSelectionData([{ id: 'contract-needed' }]), '추천 도구 목록 있음')
  assert.equal(demoReducer(state, { type: 'selection-enter', id: 'missing' }), state)
  const invalid = demoReducer(state, { type: 'environment-edit', id: 'a', edit: 'scalar', field: 'osVersion', value: 'x'.repeat(201) })
  assert.equal(canEnterDemoSelection(invalid, 'a'), false)
})

test('예시 열기 후 선택 카드만 토글하고 필수 카드는 고정한다', () => {
  let state = demoReducer(session(), { type: 'selection-enter', id: 'a' })
  assert.equal(edit(state, { edit: 'tool', toolId: 'ui-optional' }), state)
  state = edit(state, { edit: 'visibility', value: true })
  state = edit(state, { edit: 'tool', toolId: 'ui-optional' })
  assert.deepEqual(state.selectionPreviews.a.selectedIds, ['ui-required', 'ui-optional'])
  assert.equal(edit(state, { edit: 'tool', toolId: 'ui-required' }), state)
  state = edit(state, { edit: 'details' })
  state = edit(state, { edit: 'permission', value: 'DENY' })
  assert.ok(validateSelectionPreview(state.selectionPreviews.a))
  assert.equal(state.selectionPreviews.a.permissionDetailsOpen, true)
  state = edit(state, { edit: 'tool', toolId: 'ui-optional' })
  assert.deepEqual(state.selectionPreviews.a.selectedIds, ['ui-required'])
  assert.equal(state.selectionPreviews.a.permission, 'DENY')
  state = edit(state, { edit: 'permission', value: 'ASK_EACH_TIME' })
  assert.equal(validateSelectionPreview(state.selectionPreviews.a), '')
})

test('프로젝트별 선택·권한·정보 표시를 격리하고 닫기·재진입 중 보존한다', () => {
  let state = session()
  const inputs = { profile: state.demoProfiles.a, role: state.roleDrafts.a, environment: state.environmentDrafts.a }
  state = demoReducer(state, { type: 'selection-enter', id: 'a' })
  state = edit(state, { edit: 'visibility', value: true })
  state = edit(state, { edit: 'permission', value: 'ALWAYS_ALLOW' })
  state = edit(state, { edit: 'tool', toolId: 'ui-optional' })
  state = edit(state, { edit: 'visibility', value: false })
  const stored = state.selectionPreviews.a
  state = demoReducer(state, { type: 'selection-enter', id: 'a' })
  assert.equal(state.selectionPreviews.a, stored)
  state = demoReducer(state, { type: 'selection-enter', id: 'b' })
  assert.equal(state.selectionPreviews.b.permission, 'ASK_EACH_TIME')
  assert.deepEqual(state.selectionPreviews.b.selectedIds, ['ui-required'])
  assert.equal(state.demoProfiles.a, inputs.profile)
  assert.equal(state.roleDrafts.a, inputs.role)
  assert.equal(state.environmentDrafts.a, inputs.environment)
  assert.equal(edit(state, { edit: 'tool', toolId: 'invented' }), state)
  assert.deepEqual(demoReducer(state, { type: 'exit' }).selectionPreviews, {})
})
