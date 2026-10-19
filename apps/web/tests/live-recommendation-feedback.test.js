import test from 'node:test'
import assert from 'node:assert/strict'
import { recommendationFeedback } from '../src/features/live/recommendationFeedback.js'

test('추천 결과별로 다음 행동이 다른 안내를 표시한다', () => {
  assert.match(recommendationFeedback({ status: 'NEEDS_INFORMATION' }), /기획·담당 업무/)
  assert.match(recommendationFeedback({ status: 'NO_COMPATIBLE_TOOLS' }), /OS·Client/)
  assert.match(recommendationFeedback({ status: 'NO_ADDITIONS_NEEDED' }), /추가 도구/)
  assert.equal(recommendationFeedback({ status: 'RECOMMENDED' }), '')
})

test('입력이 바뀐 추천은 저장 당시의 결과보다 만료 안내를 우선한다', () => {
  assert.match(recommendationFeedback({ status: 'NO_COMPATIBLE_TOOLS', validity: 'STALE' }), /만료/)
})
