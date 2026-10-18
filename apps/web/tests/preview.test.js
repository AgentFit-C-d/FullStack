import test from 'node:test'
import assert from 'node:assert/strict'
import { demoReducer, initialDemoState } from '../src/features/projects/projectModel.js'
import { canEnterDemoPreview, unpreparedPreviewData, selectionSignature } from '../src/features/preview/previewModel.js'
function session() {
  let state = demoReducer(initialDemoState, { type: 'enter' })
  for (const id of ['a', 'b']) for (const action of [{ type: 'create', name: id }, { type: 'profile-preview' }, { type: 'profile-edit', edit: 'confirm' }, { type: 'role-confirm' }, { type: 'environment-confirm' }, { type: 'selection-enter' }]) state = demoReducer(state, { ...action, id })
  return state
}
const enter = (state, id = 'a') => demoReducer(state, { type: 'preview-enter', id })
const edit = (state, edit, value, id = 'a') => demoReducer(state, { type: 'preview-edit', id, edit, value })
test('Preview는 명시적 demo 진입이고 실제 파일·승인·다운로드 상태는 만들지 않는다', () => {
  const state = session()
  assert.equal(canEnterDemoPreview(state, 'a'), false)
  const opened = enter(state)
  assert.equal(canEnterDemoPreview(opened, 'a'), true)
  assert.deepEqual(unpreparedPreviewData, { status: 'NOT_PREPARED', files: null, approval: null, download: null })
  assert.equal(opened.previewDrafts.a.visible, false)
  assert.equal(opened.previewDrafts.a.acknowledged, false)
  assert.equal(edit(opened, 'acknowledge', true).previewDrafts.a.acknowledged, false)
  assert.equal(enter(state, 'missing'), state)
  assert.equal(enter({ ...state, active: false }).previewDrafts.a, undefined)
  const invalid = { ...state, selectionPreviews: { ...state.selectionPreviews, a: { ...state.selectionPreviews.a, source: 'server' } } }
  assert.equal(enter(invalid), invalid)
})
test('예시·체크는 프로젝트별 보존하고 닫기 및 Selection 내용 변경 시 재확인한다', () => {
  let state = enter(session())
  state = edit(state, 'visibility', true)
  state = edit(state, 'acknowledge', true)
  assert.equal(state.previewDrafts.a.acknowledged, true)
  const saved = state.previewDrafts.a
  assert.equal(enter(state).previewDrafts.a, saved)
  state = enter(state, 'b')
  assert.equal(state.previewDrafts.b.acknowledged, false)
  assert.equal(state.previewDrafts.b.visible, false)
  state = demoReducer(state, { type: 'selection-preview', id: 'a', edit: 'visibility', value: true })
  assert.equal(state.previewDrafts.a.acknowledged, true)
  state = demoReducer(state, { type: 'selection-preview', id: 'a', edit: 'tool', toolId: 'ui-optional' })
  assert.equal(state.previewDrafts.a.acknowledged, false)
  assert.equal(state.previewDrafts.a.selectionSignature, selectionSignature(state.selectionPreviews.a))
  state = edit(state, 'acknowledge', true)
  state = demoReducer(state, { type: 'selection-preview', id: 'a', edit: 'permission', value: 'ALWAYS_ALLOW' })
  assert.equal(state.previewDrafts.a.acknowledged, false)
  state = edit(state, 'acknowledge', true)
  state = edit(state, 'visibility', false)
  assert.equal(state.previewDrafts.a.acknowledged, false)
  assert.equal(state.previewDrafts.b.acknowledged, false)
  assert.deepEqual(demoReducer(state, { type: 'exit' }).previewDrafts, {})
  assert.equal(Object.hasOwn(state, 'approved'), false)
})
