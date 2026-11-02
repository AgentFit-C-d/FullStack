import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

let server, AnalysisReviewDetails
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false }, appType: 'custom' })
  ;({ default: AnalysisReviewDetails } = await server.ssrLoadModule('/src/features/live/AnalysisReviewDetails.jsx'))
})
after(async () => { await server?.close() })

test('실제 분석의 미정, 후보, 근거 위치, 질문을 검토 화면에 표시한다', () => {
  const html = renderToStaticMarkup(createElement(AnalysisReviewDetails, {
    fields: [
      { key: 'project_name', label: '프로젝트명', value: 'AgentFit', state: 'suggested',
        evidence: [{ page: 1, start: 0, end: 8 }], candidates: [], questions: [] },
      { key: 'database', label: '데이터베이스', value: null, state: 'unknown', evidence: [],
        candidates: [{ id: 'c1', field: 'database', proposedValue: 'PostgreSQL', status: 'unresolved',
          evidence: [{ page: 2, start: 20, end: 30 }] }],
        questions: [{ id: 'q1', message: 'DB를 확인해 주세요.' }] },
    ], onApply: () => {},
  }))
  assert.match(html, /프로젝트명/)
  assert.match(html, /1쪽/)
  assert.match(html, /미정/)
  assert.match(html, /PostgreSQL/)
  assert.match(html, /2쪽/)
  assert.match(html, /DB를 확인해 주세요/)
  assert.match(html, /후보 반영/)
})
