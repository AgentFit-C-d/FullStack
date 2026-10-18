import { afterEach, test } from 'node:test'
import assert from 'node:assert/strict'
import { apiRequest, projectPath } from '../src/features/live/api.js'

const originalFetch = globalThis.fetch
const originalDocument = globalThis.document

afterEach(() => {
  globalThis.fetch = originalFetch
  globalThis.document = originalDocument
})

test('쓰기 요청은 같은 출처 CSRF 쿠키를 받은 뒤 헤더와 세션으로 전송한다', async () => {
  const calls = []
  globalThis.document = { cookie: '' }
  globalThis.fetch = async (path, options) => {
    calls.push({ path, options })
    if (path === '/api/auth/csrf') {
      globalThis.document.cookie = 'XSRF-TOKEN=token%2Bvalue'
      return new Response(null, { status: 204 })
    }
    return Response.json({ project: { id: 'saved' } }, { status: 201 })
  }
  const result = await apiRequest('/api/projects', { method: 'POST', body: { name: 'Demo' } })
  assert.equal(result.project.id, 'saved')
  assert.equal(calls[0].path, '/api/auth/csrf')
  assert.equal(calls[1].options.credentials, 'same-origin')
  assert.equal(calls[1].options.headers['X-XSRF-TOKEN'], 'token+value')
  assert.deepEqual(JSON.parse(calls[1].options.body), { name: 'Demo' })
})

test('읽기 요청은 CSRF 요청 없이 서버 오류 상태를 전달한다', async () => {
  const paths = []
  globalThis.document = { cookie: '' }
  globalThis.fetch = async path => {
    paths.push(path)
    return Response.json({ code: 'UNAUTHENTICATED' }, { status: 401 })
  }
  await assert.rejects(apiRequest('/api/projects'), error => error.status === 401)
  assert.deepEqual(paths, ['/api/projects'])
  assert.equal(projectPath('project id'), '/api/projects/project%20id')
})
