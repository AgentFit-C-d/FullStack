import test from 'node:test'
import assert from 'node:assert/strict'
import { validatePlanningFiles, validatePlanningText, limitPlanningText, MAX_PLANNING_BYTES } from '../src/features/planning/planningModel.js'
import { demoReducer, initialDemoState, getPlanningDraft } from '../src/features/projects/projectModel.js'

const pdf = { name: '기획.PDF', size: 10 * 1024 * 1024 }

test('PDF/Markdown 한 개와 정확히 10 MiB까지 허용한다', () => {
  assert.equal(validatePlanningFiles([pdf]), '')
  assert.equal(validatePlanningFiles([{ name: 'plan.MD', size: 1 }]), '')
  assert.equal(validatePlanningFiles([{ name: 'plan.markdown', size: 1 }]), '')
  assert.ok(validatePlanningFiles([]))
  assert.ok(validatePlanningFiles([pdf, pdf]))
  assert.ok(validatePlanningFiles([{ name: 'plan.docx', size: 1 }]))
  assert.ok(validatePlanningFiles([{ ...pdf, size: MAX_PLANNING_BYTES + 1 }]))
})

test('직접 입력은 공백을 거부하고 Unicode 100,000 코드포인트를 보존한다', () => {
  assert.ok(validatePlanningText(' \n '))
  assert.equal(validatePlanningText('테스트 기획'), '')
  const text = '🦊'.repeat(100001)
  assert.equal(limitPlanningText(text), '🦊'.repeat(100000))
  assert.equal(validatePlanningText(limitPlanningText(text)), '')
  assert.ok(validatePlanningText(text))
})

test('프로젝트별 파일 참조와 텍스트를 탭 전환·다른 프로젝트 이동 중 보존한다', () => {
  let state = demoReducer(initialDemoState, { type: 'enter' })
  state = demoReducer(state, { type: 'create', id: 'a', name: 'A' })
  state = demoReducer(state, { type: 'create', id: 'b', name: 'B' })
  state = demoReducer(state, { type: 'planning-file', id: 'a', file: pdf })
  state = demoReducer(state, { type: 'planning-tab', id: 'a', tab: 'text' })
  state = demoReducer(state, { type: 'planning-text', id: 'a', text: 'A의 입력' })
  state = demoReducer(state, { type: 'planning-text', id: 'b', text: 'B의 입력' })
  state = demoReducer(state, { type: 'planning-tab', id: 'a', tab: 'file' })
  assert.equal(getPlanningDraft(state, 'a').file, pdf)
  assert.equal(getPlanningDraft(state, 'a').text, 'A의 입력')
  assert.equal(getPlanningDraft(state, 'b').text, 'B의 입력')
  assert.equal(getPlanningDraft(state, 'b').file, null)
})

test('잘못된 교체 파일은 기존 유효 파일을 지우지 않고 존재하지 않는 프로젝트는 수정하지 않는다', () => {
  let state = demoReducer(initialDemoState, { type: 'enter' })
  state = demoReducer(state, { type: 'create', id: 'a', name: 'A' })
  state = demoReducer(state, { type: 'planning-file', id: 'a', file: pdf })
  const rejected = demoReducer(state, { type: 'planning-file', id: 'a', file: {name:'bad.exe', size:1} })
  assert.equal(getPlanningDraft(rejected, 'a').file, pdf)
  assert.equal(demoReducer(state, { type: 'planning-text', id: 'missing', text: 'bad' }), state)
  assert.equal(demoReducer(state, { type: 'planning-tab', id: 'a', tab: 'invalid' }), state)
  const reset = demoReducer(state, { type: 'exit' })
  assert.equal(getPlanningDraft(reset, 'a').file, null)
  assert.equal(getPlanningDraft(reset, 'a').text, '')
})

test('분석 진입은 현재 활성 탭의 유효한 입력만 허용한다', async () => {
  const { validatePlanningDraft } = await import('../src/features/planning/planningModel.js')
  assert.ok(validatePlanningDraft({ tab: 'file', file: null, text: '다른 탭의 유효 입력' }))
  assert.equal(validatePlanningDraft({ tab: 'file', file: pdf, text: '' }), '')
  assert.ok(validatePlanningDraft({ tab: 'text', file: pdf, text: '  ' }))
  assert.equal(validatePlanningDraft({ tab: 'text', file: null, text: '기획 내용' }), '')
  assert.ok(validatePlanningDraft({ tab: 'unknown', file: pdf, text: '기획 내용' }))
})
