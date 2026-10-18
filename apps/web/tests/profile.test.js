import test from 'node:test'
import assert from 'node:assert/strict'
import { demoReducer, initialDemoState } from '../src/features/projects/projectModel.js'
import { validateProfile } from '../src/features/profile/profileModel.js'

function session() {
  let state = demoReducer(initialDemoState, { type: 'enter' })
  for (const id of ['a', 'b']) {
    state = demoReducer(state, { type: 'create', id, name: id })
    state = demoReducer(state, { type: 'profile-preview', id })
    state = demoReducer(state, { type: 'profile-load-example', id, confirmed: true })
  }
  return state
}
const edit = (state, values) => demoReducer(state, { type: 'profile-edit', id: 'a', ...values })

test('시연 fixture는 프로젝트별 독립 상태이며 재진입·Planning 변경으로 초기화하지 않는다', () => {
  let state = session()
  state = edit(state, { edit: 'scalar', key: 'domain', value: '수정된 도메인' })
  const entry = state.demoProfiles.a
  state = demoReducer(state, { type: 'planning-text', id: 'a', text: '이 내용을 추론하지 않는다' })
  state = demoReducer(state, { type: 'profile-preview', id: 'a' })
  assert.equal(state.demoProfiles.a, entry)
  assert.equal(entry.source, 'demo-fixture')
  assert.equal(entry.draft.domain, '수정된 도메인')
  assert.equal(state.demoProfiles.b.draft.domain, '협업')
  assert.equal(demoReducer(state, { type: 'profile-preview', id: 'missing' }), state)
  assert.deepEqual(demoReducer(state, { type: 'exit' }).demoProfiles, {})
})

test('null·명시적 빈 목록·실제 목록을 구분하고 목록 추가·수정·삭제한다', () => {
  let state = session()
  assert.equal(state.demoProfiles.a.draft.backend, null)
  assert.deepEqual(state.demoProfiles.a.draft.ai, [])
  state = edit(state, { edit: 'mode', key: 'backend', value: 'KNOWN' })
  state = edit(state, { edit: 'add', key: 'backend' })
  state = edit(state, { edit: 'item', key: 'backend', index: 0, value: 'Node.js' })
  assert.deepEqual(state.demoProfiles.a.draft.backend, ['Node.js'])
  assert.equal(state.demoProfiles.b.draft.backend, null)
  state = edit(state, { edit: 'remove', key: 'backend', index: 0 })
  assert.deepEqual(state.demoProfiles.a.draft.backend, [])
  assert.equal(state.demoProfiles.a.modes.backend, 'KNOWN_EMPTY')
  state = edit(state, { edit: 'mode', key: 'backend', value: 'UNKNOWN' })
  assert.equal(state.demoProfiles.a.draft.backend, null)
})

test('필수·길이·빈 항목 오류는 편집 초안을 유지하며 마지막 유효 Profile을 덮어쓰지 않는다', () => {
  let state = session()
  const valid = state.demoProfiles.a.profile
  state = edit(state, { edit: 'scalar', key: 'project_name', value: ' ' })
  state = edit(state, { edit: 'item', key: 'features', index: 0, value: '' })
  state = edit(state, { edit: 'scalar', key: 'domain', value: '🦊'.repeat(201) })
  assert.equal(Object.keys(validateProfile(state.demoProfiles.a.draft, state.demoProfiles.a.modes)).length, 3)
  state = edit(state, { edit: 'confirm' })
  assert.equal(state.demoProfiles.a.profile, valid)
  assert.equal(state.demoProfiles.a.draft.project_name, ' ')
  assert.equal(state.demoProfiles.a.confirmed, false)
  state = edit(state, { edit: 'scalar', key: 'project_name', value: '고친 이름' })
  state = edit(state, { edit: 'scalar', key: 'domain', value: '🦊'.repeat(200) })
  state = edit(state, { edit: 'item', key: 'features', index: 0, value: '고친 기능' })
  state = edit(state, { edit: 'confirm' })
  assert.equal(state.demoProfiles.a.confirmed, true)
  assert.equal(state.demoProfiles.a.profile.project_name, '고친 이름')
  assert.equal(state.projects.find(p => p.id === 'a').name, 'a')
})

test('목록 30개 제한과 잘못된 필드·인덱스를 안전하게 처리한다', () => {
  let state = session()
  for (let i = 0; i < 40; i++) state = edit(state, { edit: 'add', key: 'features' })
  assert.equal(state.demoProfiles.a.draft.features.length, 30)
  const entry = state.demoProfiles.a
  for (const values of [{ edit: 'scalar', key: 'bogus', value: 'bad' }, { edit: 'item', key: 'features', index: -1, value: 'bad' }]) {
    state = edit(state, values)
    assert.equal(state.demoProfiles.a, entry)
  }
  assert.equal(demoReducer(initialDemoState, { type: 'profile-preview', id: 'a' }), initialDemoState)
})


test('신규 Profile은 생성 이름만 가져오며 예시는 확인된 명시적 요청만 허용한다', () => {
  let state = demoReducer(initialDemoState, { type: 'enter' })
  state = demoReducer(state, { type: 'create', id: 'a', name: '내 프로젝트' })
  state = demoReducer(state, { type: 'profile-preview', id: 'a' })
  const first = state.demoProfiles.a
  assert.equal(first.source, 'demo-manual')
  assert.equal(first.draft.project_name, '내 프로젝트')
  assert.equal(first.nameOrigin, 'project')
  assert.ok(Object.entries(first.draft).every(([key, value]) => key === 'project_name' || value === null))
  assert.equal(demoReducer(state, { type: 'profile-load-example', id: 'a' }), state)
  state = edit(state, { edit: 'scalar', key: 'domain', value: '' })
  assert.equal(state.demoProfiles.a.draft.domain, null)
  assert.equal(validateProfile(state.demoProfiles.a.draft, state.demoProfiles.a.modes).domain, undefined)
  state = edit(state, { edit: 'confirm' })
  assert.equal(state.demoProfiles.a.profile.domain, null)
  state = edit(state, { edit: 'unknown', key: 'domain' })
  assert.equal(state.demoProfiles.a.draft.domain, null)
  state = edit(state, { edit: 'scalar', key: 'project_name', value: '내 Profile 이름' })
  const changed = state.demoProfiles.a
  assert.equal(demoReducer(state, { type: 'profile-preview', id: 'a' }).demoProfiles.a, changed)
  assert.equal(state.projects[0].name, '내 프로젝트')
  state = demoReducer(state, { type: 'profile-load-example', id: 'a', confirmed: true })
  assert.equal(state.demoProfiles.a.source, 'demo-fixture')
  assert.equal(state.demoProfiles.a.draft.project_name, '예시 팀 일정 서비스')
})

test('선택형 단일 필드의 삭제·공백은 null이며 직접 편집 출처와 마지막 유효 값을 보존한다', async () => {
  const { getTechnicalFieldLabel } = await import('../src/features/profile/profileModel.js')
  let state = session()
  const other = structuredClone(state.demoProfiles.a.draft.features)
  for (const key of ['project_type', 'domain', 'database', 'deployment']) {
    state = edit(state, { edit: 'scalar', key, value: '' })
    assert.equal(getTechnicalFieldLabel(state.demoProfiles.a, key), '확인되지 않음')
    state = edit(state, { edit: 'scalar', key, value: 'PostgreSQL' })
    assert.equal(getTechnicalFieldLabel(state.demoProfiles.a, key), '사용자 입력')
    state = edit(state, { edit: 'confirm' })
    state = edit(state, { edit: 'scalar', key, value: 'x'.repeat(201) })
    state = edit(state, { edit: 'confirm' })
    assert.equal(state.demoProfiles.a.profile[key], 'PostgreSQL')
    state = edit(state, { edit: 'scalar', key, value: '   ' })
    assert.equal(state.demoProfiles.a.draft[key], null)
    assert.equal(getTechnicalFieldLabel(state.demoProfiles.a, key), '확인되지 않음')
    state = edit(state, { edit: 'confirm' })
    assert.equal(state.demoProfiles.a.profile[key], null)
    state = edit(state, { edit: 'scalar', key, value: '' })
    assert.equal(state.demoProfiles.a.draft[key], null)
  }
  assert.deepEqual(state.demoProfiles.a.draft.features, other)
  const server = { ...state.demoProfiles.a, draft: { ...state.demoProfiles.a.draft, database: '서버 문서 값' }, editedFields: {}, sources: { database: 'DOCUMENT' } }
  assert.equal(getTechnicalFieldLabel(server, 'database'), '문서에서 확인')
})
