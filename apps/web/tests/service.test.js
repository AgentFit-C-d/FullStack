import test from 'node:test'
import assert from 'node:assert/strict'
import { createHttpTransport, createServiceApi, serviceContracts } from '../src/features/service/serviceApi.js'
import { isAuthenticatedSession, loadServiceRoute } from '../src/features/service/serviceModel.js'
const session = { user: { id: 'user-1', name: '팀 사용자' }, session: { expiresAt: '2099-01-01T00:00:00Z' } }
const project = { id: 'p/1', name: '프로젝트', version: 0, createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z' }
const request = new Request('http://localhost/app/projects/p%2F1')

test('실서비스 읽기 transport는 세션 쿠키를 보내고 문자열 null을 비인증으로 해석한다', async () => {
  const calls = []
  const transport = createHttpTransport(async (path, options) => {
    calls.push({ path, options })
    return Response.json('null')
  })
  const result = await createServiceApi({ transport }).read('session')
  assert.equal(result.status, 'unauthenticated')
  assert.equal(calls[0].path, '/api/auth/get-session')
  assert.equal(calls[0].options.credentials, 'same-origin')
})
test('기본 서비스 API는 실제 연결 없이 세션·결과·다운로드 성공을 만들지 않는다', async () => {
  const api = createServiceApi()
  for (const key of ['session', 'projects', 'project', 'role', 'environment', 'recommendation', 'catalog', 'selection', 'preview', 'approval', 'download', 'report']) {
    const result = await api.read(key, { projectId: 'a' })
    assert.equal(result.status, 'unavailable')
    assert.equal(result.data, null)
    assert.equal(isAuthenticatedSession(result), false)
  }
  for (const key of ['signIn', 'createProject', 'updateProject', 'startAnalysis', 'confirmProfile', 'saveRole', 'saveEnvironment', 'approve', 'download', 'saveReport']) assert.equal((await api.execute(key, {})).status, 'unavailable')
  assert.equal(serviceContracts.session.path, '/api/auth/get-session')
  assert.equal(serviceContracts.confirmProfile.path, '/api/projects/{projectId}/profile')
  assert.equal(serviceContracts.role, undefined)
})
test('서비스 세션은 서버 출처·공개 스키마·만료를 검사하고 demo나 로그인 플래그를 거부한다', () => {
  const valid = { status: 'ready', source: 'server', data: session }
  assert.equal(isAuthenticatedSession(valid), true)
  for (const result of [{ ...valid, source: 'demo' }, { ...valid, data: null }, { ...valid, data: { active: true } }, { ...valid, data: { ...session, token: 'private' } }, { ...valid, data: { ...session, session: { expiresAt: '2000-01-01' } } }, { ...valid, data: { ...session, source: 'demo-ui' } }]) assert.equal(isAuthenticatedSession(result), false)
})
test('읽기 어댑터는 확정 계약만 호출하고 null·빈 목록·오류·프로젝트 식별을 구분한다', async () => {
  const calls = []
  let response = { status: 200, data: session }
  const api = createServiceApi({ transport: async input => { calls.push(input); return response } })
  assert.equal(isAuthenticatedSession(await api.read('session')), true)
  response = { status: 200, data: null }
  assert.equal((await api.read('session')).status, 'unauthenticated')
  response = { status: 200, data: { projects: [] } }
  assert.equal((await api.read('projects')).status, 'empty')
  response = { status: 200, data: { projects: null } }
  assert.equal((await api.read('projects')).status, 'error')
  response = { status: 200, data: { project, confirmed: null, draft: null, latestAttempt: null } }
  assert.equal((await api.read('project', { projectId: 'p/1' })).status, 'ready')
  assert.equal(calls.at(-1).path, '/api/projects/p%2F1')
  assert.equal((await api.read('project', { projectId: 'other' })).status, 'error')
  response = { status: 200, data: { source: 'demo-ui', projects: [] } }
  assert.equal((await api.read('projects')).status, 'error')
  for (const [status, expected] of [[401, 'unauthenticated'], [403, 'forbidden'], [404, 'not-found'], [503, 'error']]) {
    response = { status, data: { message: '내부 상세는 화면에 전달하지 않음' } }
    assert.equal((await api.read('projects')).status, expected)
  }
  const before = calls.length
  await api.read('preview'); await api.execute('confirmProfile', {})
  assert.equal(calls.length, before)
})
test('인증이 없으면 어떤 단계도 조회하지 않고 인증은 분석 완료를 만들지 않는다', async () => {
  let calls = []
  const api = { read: async (key, params) => { calls.push([key, params]); return { status: 'unavailable', data: null } } }
  const blocked = await loadServiceRoute(api, 'project', { request, params: { projectId: 'p/1' } })
  assert.equal(blocked.resource, null)
  assert.deepEqual(calls.map(([key]) => key), ['session'])
  calls = []
  const connected = createServiceApi({ transport: async ({ path }) => {
    calls.push(path)
    return { status: 200, data: path === '/api/auth/get-session' ? session : { project, confirmed: null, draft: null, latestAttempt: null } }
  } })
  const loaded = await loadServiceRoute(connected, 'project', { request, params: { projectId: 'p/1' } })
  assert.equal(loaded.projectId, 'p/1')
  assert.equal(loaded.resource.data.latestAttempt, null)
  assert.equal(Object.hasOwn(loaded, 'analysisStatus'), false)
  assert.equal(loaded.resource.data.confirmed, null)
  assert.equal(calls.length, 2)
})


test('중단·네트워크 실패·리소스 401은 데이터나 완료 상태를 남기지 않는다', async () => {
  const controller = new AbortController()
  controller.abort()
  let requested = false
  const api = createServiceApi({ transport: async () => { requested = true; throw new Error('내부 메시지') } })
  assert.equal((await api.read('projects', {}, { signal: controller.signal })).code, 'CANCELLED')
  assert.equal(requested, false)
  assert.deepEqual(await api.read('projects'), { status: 'error', data: null, code: 'REQUEST_FAILED' })
  const expired = createServiceApi({ transport: async ({ path }) => ({ status: path.endsWith('get-session') ? 200 : 401, data: path.endsWith('get-session') ? session : null }) })
  const result = await loadServiceRoute(expired, 'projects', { request, params: {} })
  assert.equal(result.auth.status, 'unauthenticated')
  assert.equal(result.resource.data, null)
})
