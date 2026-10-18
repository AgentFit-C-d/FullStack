import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { createServer } from 'vite'
import { createDemoProfile } from '../src/features/profile/profileModel.js'
import { demoReducer, initialDemoState } from '../src/features/projects/projectModel.js'

let server, AnalysisScreen, ProfileEditor, RoleEditor, EnvironmentEditor, WizardProgress, DemoContext
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false }, appType: 'custom' })
  ;({ AnalysisScreen } = await server.ssrLoadModule('/src/pages/AnalysisPage.jsx'))
  ;({ ProfileEditor } = await server.ssrLoadModule('/src/pages/ProfileReviewPage.jsx'))
  ;({ default: WizardProgress } = await server.ssrLoadModule('/src/features/projects/WizardProgress.jsx'))
  ;({ EnvironmentEditor } = await server.ssrLoadModule('/src/pages/EnvironmentPage.jsx'))
  ;({ RoleEditor } = await server.ssrLoadModule('/src/pages/RolePage.jsx'))
  ;({ DemoContext } = await server.ssrLoadModule('/src/features/projects/DemoContext.js'))
})
after(async () => { await server?.close() })
const project = { id: 'a', name: '사용자 프로젝트' }
const render = element => renderToStaticMarkup(h(MemoryRouter, null, element))

test('Analysis 실제 렌더링에서 시연 진입은 demo에만 보이고 결과 버튼은 비활성이다', () => {
  for (const mode of ['demo', 'service']) {
    const html = render(h(AnalysisScreen, { project, planningPath: '/demo/projects/a', mode, status: 'unavailable', onPreview() {} }))
    assert.match(html, /disabled=""/)
    if (mode === 'demo') assert.match(html, /시연용 Profile 편집 화면 미리보기/)
    else {
      assert.doesNotMatch(html, /시연|fixture|연결 전|미리보기/)
      assert.match(html, /분석 서비스를 이용할 수 없어요/)
    }
  }
})

test('Profile 렌더링의 예시 불러오기는 demo에만 표시하고 서비스에 demo 데이터 유입을 거부한다', () => {
  const entry = createDemoProfile(project.name)
  const view = (mode, data) => render(h(DemoContext.Provider, { value: { dispatch() {} } }, h(ProfileEditor, { mode, project, entry: data })))
  assert.match(view('demo', entry), /예시 데이터 불러오기/)
  assert.doesNotMatch(view('demo', entry), /예시 팀 일정 서비스/)
  // A supplied server-shaped value tests presentation only; no API/session is simulated.
  const service = view('service', { ...entry, source: 'server' })
  assert.doesNotMatch(service, /시연|fixture|메모리에|예시 데이터 불러오기|예시 팀 일정 서비스/)
  const rejected = view('service', createDemoProfile('', true))
  assert.match(rejected, /서버에서 확인한 프로젝트 정보가 필요합니다/)
  assert.doesNotMatch(rejected, /예시 팀 일정 서비스|<form/)
})

test('시연 진입·프로필 확인은 분석 완료 상태를 만들지 않고 인증 관련 action도 상태를 바꾸지 않는다', () => {
  let state = demoReducer(initialDemoState, { type: 'enter' })
  state = demoReducer(state, { type: 'create', id: 'a', name: 'A' })
  state = demoReducer(state, { type: 'profile-preview', id: 'a' })
  state = demoReducer(state, { type: 'profile-edit', id: 'a', edit: 'confirm' })
  assert.equal(state.demoProfiles.a.confirmed, true)
  assert.equal(Object.hasOwn(state, 'analysisStatus'), false)
  assert.equal(demoReducer(state, { type: 'oauth-success' }), state)
  assert.equal(demoReducer(state, { type: 'analysis-completed' }), state)
})


test('Role 시연 입력 UI는 demo에만 제공하고 서비스는 미연동 안내로 차단한다', () => {
  const entry = { source: 'demo-user', draft: { role: '', responsibilitiesText: '', experience: '' } }
  const demo = render(h(RoleEditor, { mode: 'demo', entry, reviewPath: '/demo/projects/a/review' }))
  assert.match(demo, /내 역할과 담당 업무/)
  assert.match(demo, /시연 모드/)
  assert.match(demo, /다음: 개발환경 입력/)
  assert.match(demo, /href="\/demo\/projects\/a\/review"/)
  const service = render(h(RoleEditor, { mode: 'service', entry, reviewPath: '/projects/a/review' }))
  assert.match(service, /인증된 세션과 역할 데이터 API 연결이 필요합니다/)
  assert.doesNotMatch(service, /시연|fixture|<form|프론트엔드/)
})


test('시연 체크는 화면 흐름이며 실제 서비스의 03단계 제한을 해제하지 않는다', () => {
  for (const currentStep of [4, 5]) {
    const html = render(h(WizardProgress, { currentStep, navigationOnly: true }))
    assert.match(html, /체크는 화면 방문을 의미합니다/)
    assert.equal((html.match(/✓/g) || []).length, currentStep - 1)
  }
  const service = render(h(WizardProgress, { currentStep: 4, incompleteSteps: [3] }))
  assert.equal((service.match(/✓/g) || []).length, 2)
})

test('기술 필드에는 미확인 설정 버튼 없이 자동 상태 배지를 표시한다', () => {
  const entry = createDemoProfile('이름')
  entry.draft.database = 'PostgreSQL'
  entry.editedFields = { database: true }
  const html = render(h(DemoContext.Provider, { value: { dispatch() {} } }, h(ProfileEditor, { mode: 'demo', project, entry })))
  assert.match(html, /Database.*?사용자 입력/s)
  assert.match(html, /Deployment.*?확인되지 않음/s)
  assert.doesNotMatch(html, />미확인으로 설정</)
})


test('Environment 시연 입력 UI와 서비스 미연동 안내를 분리한다', async () => {
  const { emptyEnvironmentEntry } = await import('../src/features/environment/environmentModel.js')
  const demo = render(h(EnvironmentEditor, { mode: 'demo', entry: emptyEnvironmentEntry, rolePath: '/demo/projects/a/role' }))
  assert.match(demo, /PC 환경을 자동 검사하지 않습니다/)
  assert.match(demo, /href="\/demo\/projects\/a\/role"/)
  assert.match(demo, /개발환경 저장하고 추천 보기/)
  const service = render(h(EnvironmentEditor, { mode: 'service', entry: emptyEnvironmentEntry, rolePath: '/projects/a/role' }))
  assert.match(service, /인증된 세션과 개발환경 데이터 API 연결이 필요합니다/)
  assert.doesNotMatch(service, /시연|Codex|<form/)
})


test('프로젝트 유형·도메인 배지는 직접 편집과 서버 문서 출처를 구분한다', () => {
  const entry = createDemoProfile('이름')
  entry.draft.project_type = '웹 서비스'
  entry.draft.domain = '협업'
  entry.editedFields = { project_type: true }
  entry.sources = { domain: 'DOCUMENT' }
  const html = render(h(DemoContext.Provider, { value: { dispatch() {} } }, h(ProfileEditor, { mode: 'demo', project, entry })))
  assert.match(html, /프로젝트 유형.*?사용자 입력/s)
  assert.match(html, /도메인.*?문서에서 확인/s)
  assert.doesNotMatch(html, />미확인으로 설정</)
})
