import test from 'node:test'
import assert from 'node:assert/strict'
import { createAndReadRecommendation } from '../src/features/live/recommendationFlow.js'

test('생성 직후 저장된 최신 추천을 읽어 질문·사유를 표시할 수 있다', async () => {
  const calls = []
  const latest = { status: 'NEEDS_INFORMATION', validity: 'CURRENT', notices: [
    { type: 'QUESTION', code: 'installed_components' },
  ] }
  const result = await createAndReadRecommendation('/api/projects/p1',
    { project: 2, developer: 1, environment: 1 }, async (path, options) => {
      calls.push([path, options?.method || 'GET'])
      return options?.method === 'POST' ? { status: 'NEEDS_INFORMATION', notices: [] } : latest
    })
  assert.equal(result, latest)
  assert.deepEqual(calls, [
    ['/api/projects/p1/recommendations', 'POST'],
    ['/api/projects/p1/recommendations/latest', 'GET'],
  ])
})
