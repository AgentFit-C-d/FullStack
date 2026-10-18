import test from 'node:test'
import assert from 'node:assert/strict'
import { canEnterDemoRole, emptyRoleEntry, parseResponsibilities, validateRole } from '../src/features/role/roleModel.js'
import { demoReducer, initialDemoState } from '../src/features/projects/projectModel.js'

function session() {
  let state = demoReducer(initialDemoState, { type: 'enter' })
  for (const id of ['a', 'b']) {
    state = demoReducer(state, { type: 'create', id, name: id })
    state = demoReducer(state, { type: 'profile-preview', id })
    state = demoReducer(state, { type: 'profile-edit', id, edit: 'confirm' })
  }
  return state
}
const edit = (state, field, value, id = 'a') => demoReducer(state, { type: 'role-edit', id, field, value })

test('Role 모든 항목은 선택이며 업무 30개·각 200 코드포인트를 검증한다', () => {
  assert.deepEqual(validateRole(emptyRoleEntry.draft), {})
  assert.deepEqual(parseResponsibilities('  첫 업무  \n\n둘째 업무\r\n '), ['첫 업무', '둘째 업무'])
  const draft = { role: 'AI', experience: '초보', responsibilitiesText: Array(30).fill('🦊'.repeat(199)).join('\n') }
  assert.deepEqual(validateRole(draft), {})
  assert.ok(validateRole({ ...draft, responsibilitiesText: '🦊'.repeat(201) }).responsibilitiesText)
  assert.ok(validateRole({ ...draft, responsibilitiesText: Array(31).fill('업무').join('\n') }).responsibilitiesText)
  assert.ok(validateRole({ ...draft, role: '임의 직무' }).role)
  assert.ok(validateRole({ ...draft, experience: '임의 수준' }).experience)
})

test('Role 진입은 유효 확인한 시연 Profile만 허용하고 실제 분석 완료를 만들지 않는다', () => {
  const state = session()
  assert.equal(canEnterDemoRole(state.demoProfiles.a), true)
  assert.equal(canEnterDemoRole(undefined), false)
  assert.equal(canEnterDemoRole({ ...state.demoProfiles.a, source: 'server' }), false)
  const invalid = demoReducer(state, { type: 'profile-edit', id: 'a', edit: 'scalar', key: 'project_name', value: '' })
  assert.equal(canEnterDemoRole(invalid.demoProfiles.a), false)
  assert.equal(edit(invalid, 'role', 'AI'), invalid)
  assert.equal(Object.hasOwn(state, 'analysisStatus'), false)
})

test('역할 선택은 단일·독립적이고 프로젝트별 초안·값은 재진입 중 보존된다', () => {
  let state = session()
  state = edit(state, 'role', '프론트엔드')
  state = edit(state, 'experience', '중급')
  state = edit(state, 'responsibilitiesText', '화면 구현\n브라우저 검증')
  state = edit(state, 'role', '백엔드')
  assert.equal(state.roleDrafts.a.draft.experience, '중급')
  assert.equal(state.roleDrafts.a.draft.role, '백엔드')
  state = edit(state, 'role', 'AI', 'b')
  const entry = state.roleDrafts.a
  state = demoReducer(state, { type: 'select', id: 'b' })
  state = demoReducer(state, { type: 'profile-preview', id: 'a' })
  state = demoReducer(state, { type: 'profile-edit', id: 'a', edit: 'confirm' })
  assert.equal(state.roleDrafts.a, entry)
  assert.equal(state.roleDrafts.b.draft.role, 'AI')
  state = demoReducer(state, { type: 'role-confirm', id: 'a' })
  assert.deepEqual(state.roleDrafts.a.value.responsibilities, ['화면 구현', '브라우저 검증'])
  assert.deepEqual(demoReducer(state, { type: 'exit' }).roleDrafts, {})
})

test('잘못된 업무는 마지막 유효 값을 지우지 않고 초안에 보존한다', () => {
  let state = edit(session(), 'responsibilitiesText', '정상 업무')
  state = demoReducer(state, { type: 'role-confirm', id: 'a' })
  const valid = state.roleDrafts.a.value
  state = edit(state, 'responsibilitiesText', '가'.repeat(201))
  state = demoReducer(state, { type: 'role-confirm', id: 'a' })
  assert.equal(state.roleDrafts.a.value, valid)
  assert.equal(state.roleDrafts.a.draft.responsibilitiesText.length, 201)
  assert.equal(edit(state, 'role', '없는 역할'), state)
  assert.equal(edit(state, 'role', 'AI', 'missing'), state)
  state = edit(state, 'responsibilitiesText', '')
  state = demoReducer(state, { type: 'role-confirm', id: 'a' })
  assert.deepEqual(state.roleDrafts.a.value.responsibilities, [])
})


test('담당 업무 전체 6000 경계는 줄바꿈·공백·구분자를 포함하고 초안을 자르지 않는다', () => {
  const rows = [...Array(29).fill('🦊'.repeat(200)), '🦊'.repeat(171)]
  const exact = rows.join('\n')
  assert.equal(Array.from(exact).length, 6000)
  assert.deepEqual(validateRole({ ...emptyRoleEntry.draft, responsibilitiesText: exact }), {})
  const over = exact + ' '
  assert.match(validateRole({ ...emptyRoleEntry.draft, responsibilitiesText: over }).responsibilitiesText, /6,000/)
  assert.match(validateRole({ ...emptyRoleEntry.draft, responsibilitiesText: rows.join('\r\n') }).responsibilitiesText, /6,000/)
  assert.match(validateRole({ ...emptyRoleEntry.draft, responsibilitiesText: exact + '\n' }).responsibilitiesText, /6,000/)
  let state = edit(session(), 'responsibilitiesText', '정상 업무')
  state = demoReducer(state, { type: 'role-confirm', id: 'a' })
  const valid = state.roleDrafts.a.value
  state = edit(state, 'responsibilitiesText', over)
  state = demoReducer(state, { type: 'role-confirm', id: 'a' })
  assert.equal(state.roleDrafts.a.value, valid)
  assert.equal(state.roleDrafts.a.draft.responsibilitiesText, over)
  assert.equal(state.roleDrafts.a.confirmed, false)
  assert.equal(state.roleDrafts.b, undefined)
})
