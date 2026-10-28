import test from 'node:test'
import assert from 'node:assert/strict'
import { getAnalysisPresentation, getProfilePresentation } from '../src/features/analysis/analysisPresentation.js'

test('분석 상태 5종은 명시적 입력으로 구분하고 서비스 문구에 시연 표현을 노출하지 않는다', () => {
  const statuses = ['unavailable', 'queued', 'processing', 'completed', 'failed']
  assert.equal(new Set(statuses.map(status => getAnalysisPresentation(status).heading)).size, 5)
  assert.equal(getAnalysisPresentation('processing').title, '기획 자료를 분석하고 있어요')
  for (const status of statuses) assert.doesNotMatch(JSON.stringify(getAnalysisPresentation(status)), /시연|fixture|연결 전|메모리/)
  assert.equal(getAnalysisPresentation('invalid').heading, getAnalysisPresentation('unavailable').heading)
  assert.equal(getAnalysisPresentation('unavailable', 'demo').heading, 'AI 분석 연결 전')
  assert.match(getProfilePresentation('demo').confirmLabel, /시연/)
  assert.doesNotMatch(JSON.stringify(getProfilePresentation()), /시연|fixture|메모리/)
})
