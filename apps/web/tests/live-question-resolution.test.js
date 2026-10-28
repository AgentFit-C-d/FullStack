import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createServer } from 'vite'

let server, QuestionAnswersEditor
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false }, appType: 'custom' })
  ;({ default: QuestionAnswersEditor } = await server.ssrLoadModule('/src/features/live/QuestionAnswersEditor.jsx'))
})
after(async () => { await server?.close() })

test('환경 추가 질문에서 실제 환경 입력으로 이동할 수 있다', () => {
  const html = renderToStaticMarkup(createElement(QuestionAnswersEditor, {
    notices: [{ id: 'n1', code: 'installed_components', type: 'QUESTION',
      message: '설치 도구 목록을 확인해 주세요.', answerTarget: 'environment.installedComponents' }],
    answers: [], drafts: {}, busy: false, onDraft: () => {}, onSave: () => {},
  }))
  assert.match(html, /href="#recommendation-inputs"/)
  assert.match(html, /환경 정보를 저장한 뒤 추천을 다시 생성/)
})

test('AI 기획 질문에는 환경 입력 링크를 잘못 표시하지 않는다', () => {
  const html = renderToStaticMarkup(createElement(QuestionAnswersEditor, {
    notices: [{ id: 'n2', code: 'undetermined_capability', type: 'QUESTION',
      message: '기획을 확인해 주세요.', answerTarget: 'profile.item.features.0' }],
    answers: [], drafts: {}, busy: false, onDraft: () => {}, onSave: () => {},
  }))
  assert.doesNotMatch(html, /href="#recommendation-inputs"/)
})
