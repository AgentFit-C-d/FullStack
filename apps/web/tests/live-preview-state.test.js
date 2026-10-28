import test from 'node:test'
import assert from 'node:assert/strict'

test('미리보기는 생성 당시 추천이 최신일 때만 승인과 다운로드에 사용할 수 있다', async () => {
  const state = await import('../src/features/live/previewState.js')
  assert.equal(typeof state.previewIsCurrent, 'function')
  const request = { recommendationId: 'r1' }
  const valid = { expiresAt: '2026-10-28T00:00:00Z' }
  const expired = { expiresAt: '2026-10-26T00:00:00Z' }
  const now = Date.parse('2026-10-27T00:00:00Z')
  assert.equal(state.previewIsCurrent(request, { recommendationId: 'r1', validity: 'CURRENT' }, valid, now), true)
  assert.equal(state.previewIsCurrent(request, { recommendationId: 'r1', validity: 'STALE' }, valid, now), false)
  assert.equal(state.previewIsCurrent(request, { recommendationId: 'r2', validity: 'CURRENT' }, valid, now), false)
  assert.equal(state.previewIsCurrent(request, { recommendationId: 'r1', validity: 'CURRENT' }, expired, now), false)
  assert.equal(state.previewIsCurrent(null, { recommendationId: 'r1', validity: 'CURRENT' }, valid, now), false)
})
