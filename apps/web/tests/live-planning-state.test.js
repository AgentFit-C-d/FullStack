import test from 'node:test'
import assert from 'node:assert/strict'
import { hasUnanalyzedPlanningInput } from '../src/features/live/planningState.js'

test('분석 전 저장한 기획 자료는 새로고침 후에도 분석 가능하다', () => {
  assert.equal(hasUnanalyzedPlanningInput({ confirmed: { revision: 1, inputType: 'TEXT' }, latestAttempt: null }), true)
  assert.equal(hasUnanalyzedPlanningInput({ draft: { revision: 1, inputType: 'MARKDOWN' }, latestAttempt: null }), true)
})

test('완료 또는 실패한 분석의 이전 자료는 다시 분석하도록 표시하지 않는다', () => {
  assert.equal(hasUnanalyzedPlanningInput({ confirmed: { revision: 1, inputType: 'TEXT' }, latestAttempt: { state: 'SUCCEEDED' } }), false)
  assert.equal(hasUnanalyzedPlanningInput({ confirmed: { revision: 1, inputType: 'TEXT' }, latestAttempt: { state: 'FAILED' } }), false)
  assert.equal(hasUnanalyzedPlanningInput({ draft: { id: 'draft-1', kind: 'DRAFT' }, latestAttempt: null }), false)
  assert.equal(hasUnanalyzedPlanningInput(null), false)
})
