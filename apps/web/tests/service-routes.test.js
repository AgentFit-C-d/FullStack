import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { createMemoryRouter, RouterProvider, MemoryRouter, matchRoutes } from 'react-router'
import { createServer } from 'vite'
import { createServiceApi } from '../src/features/service/serviceApi.js'
import { serviceWorkflowPath } from '../src/features/service/serviceWorkflowPath.js'
import { serviceStages } from '../src/features/service/serviceStages.js'
let server, createAppRoutes, ServiceScreen, ServiceResourceState, ServiceProjectCreateScreen
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false }, appType: 'custom' })
  ;({ createAppRoutes } = await server.ssrLoadModule('/src/app/routes.jsx'))
  ;({ ServiceScreen, ServiceResourceState } = await server.ssrLoadModule('/src/pages/ServicePage.jsx'))
  ;({ ServiceProjectCreateScreen } = await server.ssrLoadModule('/src/pages/ServiceProjectCreatePage.jsx'))
})
after(async () => { await server?.close() })
async function renderRoute(path, api) {
  const router = createMemoryRouter(createAppRoutes(api ? { serviceApi: api } : {}), { initialEntries: [path] })
  if (!router.state.initialized) await new Promise(resolve => {
    const unsubscribe = router.subscribe(state => { if (state.initialized && state.navigation.state === 'idle') { unsubscribe(); resolve() } })
  })
  try { return renderToStaticMarkup(h(RouterProvider, { router })) } finally { router.dispose() }
}
const noDemo = html => assert.doesNotMatch(html, /시연|예시 데이터|fixture|메모리|API|OAuth|연동 전|개발용|가상/)
test('서비스 목록과 생성은 인증 검사로 차단되고 단계별 경로는 실제 작업 화면으로 이어진다', async () => {
  const paths = serviceStages.filter(stage => ['projects', 'new'].includes(stage.key)).map(stage => `/app/${stage.path}`)
  for (const path of ['/app', ...paths, '/app/unknown', '/app/projects?mode=demo&active=true']) {
    const html = await renderRoute(path)
    assert.match(html, /지금은 로그인 서비스를 이용할 수 없어요/)
    noDemo(html)
    assert.doesNotMatch(html, /확인하고 적용|사용자 보고 저장|프로젝트 만들기 →/)
  }
  const routes = createAppRoutes()
  for (const stage of serviceStages) {
    const path = `/demo/${stage.path.replace(':projectId', 'demo-a')}`
    assert.equal(matchRoutes(routes, path).find(match => match.route.handle?.mode)?.route.handle.mode, 'demo')
  }
  assert.equal(matchRoutes(routes, '/app/projects/a/complete').find(match => match.route.handle?.mode)?.route.handle.mode, 'service')
  assert.equal(matchRoutes(routes, '/app/live').find(match => match.route.handle?.mode)?.route.handle.mode, 'service')
  assert.equal(serviceWorkflowPath('id with space'), '/app/live?project=id%20with%20space')
})
test('서비스 로그인은 기존 디자인을 재사용하며 시연 컨트롤·내부 설명이 없다', async () => {
  const service = await renderRoute('/login')
  assert.match(service, /GitHub로 계속하기/)
  assert.match(service, /href="\/api\/oauth2\/authorization\/github"/)
  assert.doesNotMatch(service, /지금은 GitHub 로그인을 이용할 수 없습니다/)
  noDemo(service)
  const demo = await renderRoute('/')
  assert.match(demo, /개발용 시연 모드로 둘러보기/)
  assert.match(demo, /메모리에만 유지/)
})
test('서버 null 세션과 세션 만료는 정상 로그인 안내로 차단한다', async () => {
  for (const data of [null, { user: { id: 'a', name: 'a' }, session: { expiresAt: '2000-01-01' } }]) {
    const api = createServiceApi({ transport: async () => ({ status: 200, data }) })
    const html = await renderRoute('/app/projects', api)
    assert.match(html, /로그인이 필요해요/)
    noDemo(html)
  }
})
test('서비스 로딩·빈 상태·오류·이용 불가를 구분하며 demo 입력과 원시 오류를 표시하지 않는다', () => {
  const auth = { status: 'ready', source: 'server', data: { user: { id: 'a', name: 'a' }, session: { expiresAt: '2099-01-01T00:00:00Z' } } }
  const cases = { loading: '정보를 불러오고 있어요', empty: '표시할 정보가 없어요', error: '정보를 불러오지 못했어요', unavailable: '지금은 이 기능을 이용할 수 없어요', 'not-found': '요청한 정보를 찾을 수 없어요', forbidden: '이 정보에 접근할 수 없어요' }
  for (const [status, text] of Object.entries(cases)) {
    const html = renderToStaticMarkup(h(MemoryRouter, null, h(ServiceScreen, { stage: serviceStages[0], auth, resource: { status, source: 'server', data: status === 'empty' ? [] : null, message: 'API 개발 중' } })))
    assert.ok(html.includes(text)); noDemo(html)
  }
  const blocked = renderToStaticMarkup(h(ServiceResourceState, { result: { status: 'ready', source: 'demo-ui', data: { source: 'demo-fixture' } } }))
  assert.match(blocked, /정보를 불러오지 못했어요/)
  noDemo(blocked)
})

test('실제 프로젝트 목록에서 저장된 프로젝트의 작업 화면으로 이동한다', () => {
  const auth = { status: 'ready', source: 'server', data: { user: { id: 'a', name: 'a' }, session: { expiresAt: '2099-01-01T00:00:00Z' } } }
  const resource = { status: 'ready', source: 'server', data: { projects: [{ id: 'project-1', name: '우리 프로젝트', version: 1, createdAt: '2026-10-18T00:00:00Z', updatedAt: '2026-10-18T00:00:00Z' }] } }
  const html = renderToStaticMarkup(h(MemoryRouter, null, h(ServiceScreen, { stage: serviceStages[0], auth, resource })))
  assert.match(html, /우리 프로젝트/)
  assert.match(html, /href="\/app\/live\?project=project-1"/)
  assert.match(html, /새 프로젝트 만들기/)
  assert.match(html, /href="\/app\/projects\/new"/)
  assert.doesNotMatch(html, /지금은 이 기능을 이용할 수 없어요/)
})

test('실제 프로젝트 생성 화면은 이름 입력과 서버 저장 동작을 제공한다', async () => {
  const auth = { user: { id: 'a', name: 'a' }, session: { expiresAt: '2099-01-01T00:00:00Z' } }
  const api = createServiceApi({ transport: async () => ({ status: 200, data: auth }) })
  const html = await renderRoute('/app/projects/new', api)
  assert.match(html, /프로젝트 이름/)
  assert.match(html, /프로젝트 만들기/)
  assert.match(html, /type="submit"/)
  noDemo(html)
  const invalid = renderToStaticMarkup(h(MemoryRouter, null, h(ServiceProjectCreateScreen, { name: '', onNameChange() {}, onSubmit() {}, error: '프로젝트 이름을 입력해주세요.' })))
  assert.match(invalid, /aria-invalid="true"/)
})


test('demo 경로와 서버 단계 경로는 서로 다른 모드로 유지된다', () => {
  const routes = createAppRoutes()
  assert.equal(matchRoutes(routes, '/demo/projects/a/preview').find(match => match.route.handle?.mode)?.route.handle.mode, 'demo')
  assert.equal(matchRoutes(routes, '/app/projects/a/preview').find(match => match.route.handle?.mode)?.route.handle.mode, 'service')
})
