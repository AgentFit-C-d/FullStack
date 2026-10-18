import test from 'node:test'
import assert from 'node:assert/strict'
import { canEnterDemoEnvironment, emptyEnvironmentEntry, validateEnvironment } from '../src/features/environment/environmentModel.js'
import { demoReducer, initialDemoState } from '../src/features/projects/projectModel.js'
function session() {
  let state = demoReducer(initialDemoState, { type: 'enter' })
  for (const id of ['a', 'b']) {
    state = demoReducer(state, { type: 'create', id, name: id })
    state = demoReducer(state, { type: 'profile-preview', id })
    state = demoReducer(state, { type: 'profile-edit', id, edit: 'confirm' })
    state = demoReducer(state, { type: 'role-confirm', id })
  }
  return state
}
const edit = (state, values, id = 'a') => demoReducer(state, { type: 'environment-edit', id, ...values })
const confirm = state => demoReducer(state, { type: 'environment-confirm', id: 'a' })

test('모든 필드는 선택이며 유효한 Role 확인 후에만 환경 화면에 진입한다', () => {
  assert.deepEqual(validateEnvironment(emptyEnvironmentEntry.draft), {})
  const state = session()
  assert.equal(canEnterDemoEnvironment(state.demoProfiles.a, state.roleDrafts.a), true)
  assert.equal(canEnterDemoEnvironment(state.demoProfiles.a, undefined), false)
  assert.equal(canEnterDemoEnvironment(state.demoProfiles.a, { ...state.roleDrafts.a, confirmed: false }), false)
  assert.equal(canEnterDemoEnvironment(state.demoProfiles.a, { ...state.roleDrafts.a, source: 'server' }), false)
  assert.equal(edit(state, { edit: 'scalar', field: 'osFamily', value: 'LINUX' }, 'missing'), state)
})

test('모름 null과 알고 있는 빈 목록 [] 및 값 목록을 구분하고 숨긴 초안을 보존한다', () => {
  let state = confirm(session())
  assert.equal(state.environmentDrafts.a.value.runtimes, null)
  state = edit(state, { edit: 'scalar', field: 'runtimeKnowledge', value: 'KNOWN' })
  state = confirm(state)
  assert.deepEqual(state.environmentDrafts.a.value.runtimes, [])
  state = edit(state, { edit: 'add', field: 'runtimes' })
  state = edit(state, { edit: 'item', field: 'runtimes', index: 0, property: 'name', value: 'Node.js' })
  state = edit(state, { edit: 'item', field: 'runtimes', index: 0, property: 'version', value: '20' })
  state = confirm(state)
  assert.deepEqual(state.environmentDrafts.a.value.runtimes, [{ name: 'Node.js', version: '20' }])
  state = edit(state, { edit: 'scalar', field: 'runtimeKnowledge', value: 'UNKNOWN' })
  state = confirm(state)
  assert.equal(state.environmentDrafts.a.value.runtimes, null)
  state = edit(state, { edit: 'scalar', field: 'runtimeKnowledge', value: 'KNOWN' })
  assert.equal(state.environmentDrafts.a.draft.runtimes[0].name, 'Node.js')
  state = edit(state, { edit: 'remove', field: 'runtimes', index: 0 })
  assert.deepEqual(confirm(state).environmentDrafts.a.value.runtimes, [])
})

test('프로젝트별 환경 보존·잘못된 입력 시 마지막 유효 값 보호·오류 수정', () => {
  let state = edit(session(), { edit: 'scalar', field: 'osVersion', value: '정상 버전' })
  state = confirm(state)
  const valid = state.environmentDrafts.a.value
  state = edit(state, { edit: 'scalar', field: 'osVersion', value: '🦊'.repeat(201) })
  assert.ok(validateEnvironment(state.environmentDrafts.a.draft).osVersion)
  assert.equal(confirm(state).environmentDrafts.a.value, valid)
  state = edit(state, { edit: 'scalar', field: 'osVersion', value: '🦊'.repeat(200) })
  assert.deepEqual(validateEnvironment(state.environmentDrafts.a.draft), {})
  state = edit(state, { edit: 'scalar', field: 'osFamily', value: 'MACOS' }, 'b')
  assert.equal(state.environmentDrafts.b.draft.osVersion, '')
  const saved = state.environmentDrafts.a
  state = demoReducer(state, { type: 'select', id: 'b' })
  state = demoReducer(state, { type: 'role-confirm', id: 'a' })
  assert.equal(state.environmentDrafts.a, saved)
  assert.equal(edit(state, { edit: 'scalar', field: 'osFamily', value: '임의 OS' }), state)
  assert.deepEqual(demoReducer(state, { type: 'exit' }).environmentDrafts, {})
})

test('설치 항목·외부 인증은 최대 30행이며 빈 이름·길이·정의되지 않은 상태를 검증한다', () => {
  let state = edit(session(), { edit: 'scalar', field: 'installedComponentsKnowledge', value: 'KNOWN' })
  state = edit(state, { edit: 'add', field: 'installedComponents' })
  assert.ok(validateEnvironment(state.environmentDrafts.a.draft)['installedComponents-0'])
  state = edit(state, { edit: 'item', field: 'installedComponents', index: 0, property: 'name', value: 'Docker' })
  assert.deepEqual(validateEnvironment(state.environmentDrafts.a.draft), {})
  state = edit(state, { edit: 'add', field: 'authReadiness' })
  assert.ok(validateEnvironment(state.environmentDrafts.a.draft)['authReadiness-0'])
  state = edit(state, { edit: 'item', field: 'authReadiness', index: 0, property: 'service', value: 'GitHub' })
  state = edit(state, { edit: 'item', field: 'authReadiness', index: 0, property: 'state', value: 'READY' })
  state = confirm(state)
  assert.deepEqual(state.environmentDrafts.a.value.authReadiness, [{ service: 'GitHub', state: 'READY' }])
  for (let i = 0; i < 35; i++) state = edit(state, { edit: 'add', field: 'authReadiness' })
  assert.equal(state.environmentDrafts.a.draft.authReadiness.length, 30)
  assert.equal(edit(state, { edit: 'item', field: 'authReadiness', index: 0, property: 'state', value: 'INVALID' }), state)
})


test('Claude Code 입력은 Client 식별자와 함께 확인하고 프로젝트별로 보존한다', () => {
  assert.equal(emptyEnvironmentEntry.clientId, 'CLAUDE_CODE')
  let state = edit(session(), { edit: 'scalar', field: 'clientVersion', value: '직접 확인한 버전' })
  state = edit(state, { edit: 'scalar', field: 'configurationKnowledge', value: 'HAS_EXISTING' })
  state = confirm(state)
  assert.equal(state.environmentDrafts.a.value.clientId, 'CLAUDE_CODE')
  assert.equal(state.environmentDrafts.a.value.clientVersion, '직접 확인한 버전')
  const valid = state.environmentDrafts.a.value
  state = edit(state, { edit: 'scalar', field: 'clientVersion', value: 'x'.repeat(201) })
  assert.equal(confirm(state).environmentDrafts.a.value, valid)
  assert.equal(state.environmentDrafts.a.draft.clientVersion.length, 201)
  assert.equal(state.environmentDrafts.b, undefined)
})

test('이전 Codex 입력은 보존하고 Claude Code 버전·설정으로 자동 매핑하지 않는다', async () => {
  const { prepareEnvironmentEntry, editEnvironment } = await import('../src/features/environment/environmentModel.js')
  const { canEnterDemoRecommendation } = await import('../src/features/recommendation/recommendationModel.js')
  const old = structuredClone(emptyEnvironmentEntry)
  delete old.clientId
  old.draft.osFamily = 'MACOS'
  old.draft.clientVersion = 'old-codex-version'
  old.draft.configurationKnowledge = 'HAS_EXISTING'
  old.value = { ...old.value, clientVersion: 'old-codex-version' }
  const entry = prepareEnvironmentEntry(old)
  const state = session()
  assert.equal(canEnterDemoRecommendation(state.demoProfiles.a, state.roleDrafts.a, { ...old, confirmed: true }), false)
  assert.equal(entry.clientId, 'CLAUDE_CODE')
  assert.equal(entry.draft.osFamily, 'MACOS')
  assert.equal(entry.draft.clientVersion, '')
  assert.equal(entry.draft.configurationKnowledge, 'UNKNOWN')
  assert.equal(entry.legacyClientInput.clientId, 'CODEX')
  assert.equal(entry.legacyClientInput.clientVersion, 'old-codex-version')
  assert.equal(entry.legacyClientInput.configurationKnowledge, 'HAS_EXISTING')
  assert.equal(entry.value, old.value)
  assert.equal(entry.confirmed, false)
  assert.equal(prepareEnvironmentEntry(entry), entry)
  assert.equal(old.draft.clientVersion, 'old-codex-version')
  const confirmed = editEnvironment(entry, { type: 'environment-confirm' })
  assert.equal(canEnterDemoRecommendation(state.demoProfiles.a, state.roleDrafts.a, confirmed), true)
  assert.equal(confirmed.value.clientId, 'CLAUDE_CODE')
  assert.equal(confirmed.value.clientVersion, null)
  assert.equal(confirmed.value.configurationKnowledge, 'UNKNOWN')
  assert.equal(confirmed.legacyClientInput.clientVersion, 'old-codex-version')
})
