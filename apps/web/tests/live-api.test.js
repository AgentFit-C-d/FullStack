import { afterEach, test } from 'node:test'
import assert from 'node:assert/strict'
import { apiRequest, environmentProfileBody, loadSession, planningInputBody, projectPath, reportBody } from '../src/features/live/api.js'

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

test('기획 자료의 버전은 Spring RequestPart가 읽는 JSON 숫자로 전송한다', async () => {
  const body = planningInputBody(1, '데모 기획', null)
  assert.equal(body.get('expectedVersion').type, 'application/json')
  assert.equal(await body.get('expectedVersion').text(), '1')
  assert.equal(body.get('inputText'), '데모 기획')
  assert.equal(body.get('file'), null)
})

test('설치 상태는 사용자 선언에 따라서만 KNOWN으로 저장한다', () => {
  assert.deepEqual(environmentProfileBody(0, 'WINDOWS', 'claude-code', '2.1.270', false), {
    expectedVersion: 0, osFamily: 'WINDOWS', clientId: 'claude-code',
    clientVersion: '2.1.270', installedComponentsKnown: false, installedComponents: [],
  })
  assert.equal(environmentProfileBody(1, 'WINDOWS', 'claude-code', '2.1.270', true)
    .installedComponentsKnown, true)
  assert.deepEqual(environmentProfileBody(1, 'WINDOWS', 'claude-code', '2.1.270', true,
    [{ toolKey: 'skill-a', version: '' }]).installedComponents,
  [{ toolKey: 'skill-a', version: null }])
  assert.throws(() => environmentProfileBody(1, 'WINDOWS', 'claude-code', '2.1.270', true,
    [{ toolKey: 'skill-a' }, { toolKey: 'skill-a' }]), /중복 불가/)
  assert.throws(() => environmentProfileBody(1, 'WINDOWS', 'claude-code', '', true), /Client 버전/)
})

test('Core 재시작 후 문자열 null 세션 응답을 로그아웃 상태로 처리한다', async () => {
  globalThis.document = { cookie: '' }
  globalThis.fetch = async () => Response.json('null')
  assert.equal(await loadSession(), null)
})

test('적용 결과 보고는 서버 계약의 상태·사유만 전달한다', () => {
  assert.deepEqual(reportBody('APPLIED', ''), { reportedState: 'APPLIED', reasonCode: null })
  assert.deepEqual(reportBody('FAILED', 'AUTH_REQUIRED'), { reportedState: 'FAILED', reasonCode: 'AUTH_REQUIRED' })
  assert.throws(() => reportBody('FAILED', ''), /실패 사유/)
  assert.throws(() => reportBody('APPLIED', 'UNKNOWN'), /적용 완료/)
})
