import test from 'node:test'
import assert from 'node:assert/strict'
import { initialDemoState, demoReducer, limitProjectName, validateProjectName, getVisibleProjects } from '../src/features/projects/projectModel.js'

test('공백 이름은 거부하고 일반 이름은 허용한다', () => {
  assert.equal(validateProjectName(' \n '), '프로젝트 이름을 입력해주세요.')
  assert.equal(validateProjectName(' 운동 기록 '), '')
})

test('200 코드포인트 제한은 이모지를 자르지 않는다', () => {
  const value = '🦊'.repeat(201)
  assert.equal(Array.from(limitProjectName(value)).length, 200)
  assert.equal(limitProjectName(value), '🦊'.repeat(200))
  assert.equal(validateProjectName(value), '프로젝트 이름은 최대 200자까지 입력할 수 있어요.')
})

test('시연 진입은 빈 세션이며 이름이 같은 프로젝트도 서로 식별한다', () => {
  let state = demoReducer(initialDemoState, { type: 'enter' })
  assert.equal(state.active, true)
  assert.deepEqual(state.projects, [])
  state = demoReducer(state, { type: 'create', id: 'demo-a', name: ' 같은 이름 ' })
  state = demoReducer(state, { type: 'create', id: 'demo-b', name: '같은 이름' })
  assert.deepEqual(state.projects.map(p => p.id), ['demo-b', 'demo-a'])
  assert.equal(state.projects[0].name, '같은 이름')
  assert.equal(state.projects[0].source, 'demo')
})

test('비활성 세션이나 잘못된 이름으로 프로젝트를 만들 수 없다', () => {
  assert.equal(demoReducer(initialDemoState, {type: 'create', id: 'demo-a', name:'A'}), initialDemoState)
  const state = demoReducer(initialDemoState, {type:'enter'})
  assert.equal(demoReducer(state, {type:'create',id:'demo-a',name:' '}), state)
})

test('초안은 이동 중 유지하고 생성 후 비우며 시연 종료 시 데이터가 사라진다', () => {
  let state = demoReducer(initialDemoState, {type:'enter'})
  state = demoReducer(state, {type:'draft', value:'작성 중'})
  assert.equal(state.draftName, '작성 중')
  state = demoReducer(state, {type:'create',id:'demo-a',name:state.draftName})
  assert.equal(state.draftName, '')
  assert.deepEqual(demoReducer(state, {type:'exit'}), initialDemoState)
  assert.deepEqual(initialDemoState.projects, [])
})

test('예시 표시 토글은 생성 프로젝트를 보존하고 개수를 일관되게 계산한다', () => {
  let state = demoReducer(initialDemoState, {type:'enter'})
  state = demoReducer(state, {type:'create',id:'demo-user',name:'운동 기록 웹 서비스'})
  const created = state.projects[0]
  assert.equal(getVisibleProjects(state).length, 1)
  for (let i = 0; i < 3; i++) {
    state = demoReducer(state, {type:'examples'})
    assert.equal(state.examplesVisible, true)
    assert.equal(getVisibleProjects(state).length, 3)
    assert.equal(new Set(getVisibleProjects(state).map(p => p.id)).size, 3)
    assert.ok(getVisibleProjects(state).every(p => p.source === 'demo'))
    state = demoReducer(state, {type:'examples'})
    assert.equal(state.examplesVisible, false)
    assert.deepEqual(getVisibleProjects(state), [created])
    assert.equal(state.selectedProjectId, 'demo-user')
  }
})

test('예시를 닫으면 숨긴 예시 선택만 해제하고 생성 데이터는 보존한다', () => {
  let state = demoReducer(initialDemoState, {type:'enter'})
  state = demoReducer(state, {type:'examples'})
  state = demoReducer(state, {type:'select',id:'demo-example-workout'})
  assert.equal(state.selectedProjectId, 'demo-example-workout')
  state = demoReducer(state, {type:'examples'})
  assert.equal(state.selectedProjectId, null)
  assert.deepEqual(getVisibleProjects(state), [])
  assert.equal(demoReducer(state, {type:'select',id:'missing'}), state)
  assert.equal(demoReducer(initialDemoState, {type:'examples'}), initialDemoState)
})

test('취소는 작성 중인 이름만 비우고 기존 프로젝트는 유지한다', () => {
  let state = demoReducer(initialDemoState, {type:'enter'})
  state = demoReducer(state, {type:'create',id:'demo-a',name:'기존 프로젝트'})
  state = demoReducer(state, {type:'draft',value:'취소할 이름'})
  const canceled = demoReducer(state, {type:'cancel'})
  assert.equal(canceled.draftName, '')
  assert.deepEqual(canceled.projects, state.projects)
})
