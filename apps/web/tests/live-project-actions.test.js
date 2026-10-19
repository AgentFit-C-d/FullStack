import test from 'node:test'
import assert from 'node:assert/strict'
import { canRenameProject, renameProjectRequest, deleteProjectRequest } from '../src/features/live/projectActions.js'

test('이름 변경은 공백을 정리하고 현재 프로젝트 버전을 전송한다', () => {
  assert.deepEqual(renameProjectRequest('  새 이름  ', 7), { name: '새 이름', expectedVersion: 7 })
  assert.throws(() => renameProjectRequest('  ', 7), /1~100자/)
  assert.throws(() => renameProjectRequest('가'.repeat(101), 7), /1~100자/)
})

test('삭제는 현재 프로젝트 이름을 정확히 입력했을 때만 확인 요청을 만든다', () => {
  assert.throws(() => deleteProjectRequest('프로젝트 A', '프로젝트 B', 7), /프로젝트 이름/)
  assert.deepEqual(deleteProjectRequest('프로젝트 A', '프로젝트 A', 7), {
    confirmation: true, expectedVersion: 7,
  })
})

test('기획 분석이 시작된 프로젝트는 Profile 이름 불일치를 막기 위해 이름 변경을 차단한다', () => {
  assert.equal(canRenameProject({ draft: null, confirmed: null, latestAttempt: null }), true)
  assert.equal(canRenameProject({ draft: { id: 'd1' }, confirmed: null, latestAttempt: null }), false)
  assert.equal(canRenameProject({ draft: null, confirmed: { id: 'c1' }, latestAttempt: null }), false)
  assert.equal(canRenameProject({ draft: null, confirmed: null, latestAttempt: { id: 'a1' } }), false)
})
