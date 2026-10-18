import test, { before, after } from 'node:test'
import assert from 'node:assert/strict'
import { createElement as h } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Routes, Route } from 'react-router'
import { createServer } from 'vite'
import { createDemoProfile } from '../src/features/profile/profileModel.js'
import { demoReducer, initialDemoState } from '../src/features/projects/projectModel.js'

let server, AnalysisScreen, ProfileEditor, RoleEditor, EnvironmentEditor, RecommendationScreen, SelectionScreen, PreviewScreen, CompleteScreen, CompletePage, WizardProgress, DemoContext
before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false, ws: false }, appType: 'custom' })
  ;({ AnalysisScreen } = await server.ssrLoadModule('/src/pages/AnalysisPage.jsx'))
  ;({ ProfileEditor } = await server.ssrLoadModule('/src/pages/ProfileReviewPage.jsx'))
  ;({ default: WizardProgress } = await server.ssrLoadModule('/src/features/projects/WizardProgress.jsx'))
  ;({ CompleteScreen, default: CompletePage } = await server.ssrLoadModule('/src/pages/CompletePage.jsx'))
  ;({ PreviewScreen } = await server.ssrLoadModule('/src/pages/PreviewPage.jsx'))
  ;({ SelectionScreen } = await server.ssrLoadModule('/src/pages/SelectionPage.jsx'))
  ;({ RecommendationScreen } = await server.ssrLoadModule('/src/pages/RecommendationPage.jsx'))
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
  assert.match(demo, /Claude Code 버전/)
  assert.match(demo, /Claude Code 설정 상태/)
  assert.match(demo, /Codex는 후속 지원/)
  assert.doesNotMatch(demo, /Codex 버전|Codex<|현재 PC에 설치되어 있습니다/)
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


test('추천 기본 상태·명시적 시안·서비스 오류를 분리하고 실제 결과 흐름을 가장하지 않는다', async () => {
  const { createRecommendationPreview } = await import('../src/features/recommendation/recommendationModel.js')
  const preview = createRecommendationPreview()
  const props = { mode: 'demo', preview, environmentPath: '/demo/projects/a/environment' }
  const initial = render(h(RecommendationScreen, props))
  assert.match(initial, /추천 미실행/)
  assert.equal((initial.match(/disabled=""/g) || []).length, 9)
  assert.match(initial, /시연용 도구·권한 화면 미리보기/)
  assert.match(initial, /href="\/demo\/projects\/a\/environment"/)
  assert.doesNotMatch(initial, /href="[^"]*selection|Codex|Claude/)
  const sample = render(h(RecommendationScreen, { ...props, preview: { ...preview, visible: true, status: 'RECOMMENDED' } }))
  assert.match(sample, /도구 추천 · 시연 미리보기/)
  assert.match(sample, /검증된 추천 도구는 아직 표시할 수 없습니다/)
  assert.doesNotMatch(sample, /disabled=""/)
  const service = render(h(RecommendationScreen, { ...props, mode: 'service' }))
  assert.match(service, /추천 서비스를 이용할 수 없어요/)
  assert.doesNotMatch(service, /시연|fixture|미리보기|문서 참조|<select/)
})


test('Selection 기본은 미조회이며 예시는 명시적으로 열고 서비스는 차단한다', async () => {
  const { createSelectionPreview } = await import('../src/features/selection/selectionModel.js')
  const preview = createSelectionPreview()
  const props = { mode: 'demo', preview, recommendationPath: '/demo/projects/a/recommendation' }
  const initial = render(h(SelectionScreen, props))
  assert.match(initial, /선택할 수 있는 검증된 도구가 없습니다/)
  assert.doesNotMatch(initial, /추천 도구 미조회|>시연 화면</)
  assert.doesNotMatch(initial, /필수 카드 UI 예시/)
  assert.match(initial, /disabled=""/)
  assert.match(initial, /시연용 설정 확인·승인 화면 미리보기/)
  assert.match(initial, /실제 설정 Preview와 승인은 제공하지 않습니다/)
  assert.match(initial, /href="\/demo\/projects\/a\/recommendation"/)
  const example = render(h(SelectionScreen, { ...props, preview: { ...preview, visible: true } }))
  assert.match(example, /필수 카드 UI 예시/)
  assert.match(example, /선택 카드 UI 예시/)
  assert.match(example, /aria-disabled="true"/)
  const service = render(h(SelectionScreen, { ...props, mode: 'service' }))
  assert.match(service, /검증된 추천·Catalog 데이터가 필요합니다/)
  assert.doesNotMatch(service, /시연|예시|<select|미리보기/)
})


test('Preview 기본 미준비·명시적 예시·체크와 서비스 접근 차단을 렌더링한다', async () => {
  const { createPreviewDraft } = await import('../src/features/preview/previewModel.js')
  const { createSelectionPreview } = await import('../src/features/selection/selectionModel.js')
  const selection = createSelectionPreview()
  const entry = createPreviewDraft(selection)
  const props = { mode: 'demo', entry, selection, selectionPath: '/demo/projects/a/selection' }
  const initial = render(h(PreviewScreen, props))
  assert.match(initial, /실제 Preview 미준비/)
  assert.match(initial, /파일 경로: <b>확인되지 않음/)
  assert.match(initial, /type="checkbox" disabled=""/)
  assert.match(initial, /href="\/demo\/projects\/a\/selection"/)
  assert.doesNotMatch(initial, /설정 영역 UI 예시|href="[^"]*complete|다운로드 가능/)
  const example = render(h(PreviewScreen, { ...props, entry: { ...entry, visible: true, acknowledged: true } }))
  assert.match(example, /설정 영역 UI 예시/)
  assert.match(example, /type="checkbox" checked=""/)
  assert.match(example, /시연 체크는 실제 승인이 아닙니다/)
  assert.match(example, /10\/10/)
  assert.doesNotMatch(example, /href="[^"]*complete|승인 완료|download=/)
  const service = render(h(PreviewScreen, { ...props, mode: 'service' }))
  assert.match(service, /인증된 세션과 서버에서 검증한 설정 Preview·승인 API/)
  assert.doesNotMatch(service, /시연|예시|type="checkbox"/)
})


test('Complete는 다운로드 파일 없음과 시연 보고만 표시하며 서비스는 차단한다', async () => {
  const { createCompleteDraft } = await import('../src/features/complete/completeModel.js')
  const entry = createCompleteDraft('snapshot')
  const props = { mode: 'demo', entry, previewPath: '/demo/projects/a/preview' }
  const initial = render(h(CompleteScreen, props))
  assert.match(initial, /미보고/)
  assert.match(initial, /다운로드 연동 전/)
  assert.match(initial, /사용자가 직접 Claude Code에 적용/)
  assert.doesNotMatch(initial, /download=|blob:|data:|사용자가 직접 Codex/)
  assert.match(initial, /href="\/demo\/projects\/a\/preview"/)
  const failed = render(h(CompleteScreen, { ...props, entry: { ...entry, draft: { status: 'FAILED', reason: '' } } }))
  for (const reason of ['실패 사유', '파일 복사 실패', '인증 필요', '의존성 누락', '알 수 없음']) assert.ok(failed.includes(reason))
  const applied = render(h(CompleteScreen, { ...props, entry: { ...entry, report: { source: 'demo-user-report', status: 'APPLIED', reason: '', verification: 'NOT_RUN', selectionSignature: 'snapshot' } } }))
  assert.match(applied, /적용 완료 \(시연 사용자 보고\)/)
  assert.match(applied, /시스템 검증: NOT_RUN/)
  const service = render(h(CompleteScreen, { ...props, mode: 'service' }))
  assert.match(service, /인증된 세션과 서버의 승인·설정 산출물·다운로드 API/)
  assert.doesNotMatch(service, /시연|APPLIED|FAILED|<select|사용자 보고 저장/)
})


test('없는 프로젝트의 Complete 직접 진입은 목록 안내로 안전하게 처리한다', () => {
  const html = renderToStaticMarkup(h(MemoryRouter, { initialEntries: ['/demo/projects/missing/complete'] },
    h(DemoContext.Provider, { value: { state: { ...initialDemoState, active: true }, dispatch() {} } },
      h(Routes, null, h(Route, { path: '/demo/projects/:demoId/complete', element: h(CompletePage) })))))
  assert.match(html, /프로젝트를 찾을 수 없어요/)
  assert.match(html, /href="\/demo\/projects"/)
  assert.doesNotMatch(html, /APPLIED|사용자 보고 저장/)
})
