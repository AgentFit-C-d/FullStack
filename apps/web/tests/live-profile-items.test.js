import test from 'node:test'
import assert from 'node:assert/strict'
import { addProfileItem, confirmedProfileItems, renameProfileItem } from '../src/features/live/profileItems.js'

test('사용자 항목 추가·수정은 문서 근거로 가장하지 않고 전체 확인 목록에 반영한다', () => {
  const original = [{ fieldKey: 'features', ordinal: 0, name: '검색', category: null,
    area: null, version: null, source: 'DOCUMENT', evidence: '{"line":1}' }]
  const added = addProfileItem(original, 'features', ' 알림 ')
  assert.equal(added[1].ordinal, 1)
  assert.equal(added[1].source, 'USER')
  assert.equal(added[1].evidence, '{}')
  const changed = renameProfileItem(added, 0, '검색 및 필터')
  assert.equal(changed[0].source, 'USER')
  assert.equal(changed[0].evidence, '{}')
  assert.equal(original[0].source, 'DOCUMENT')
  assert.deepEqual(confirmedProfileItems(changed).map(item => item.name), ['검색 및 필터', '알림'])
  assert.deepEqual(confirmedProfileItems(changed.slice(1)).map(item => item.name), ['알림'])
  assert.throws(() => confirmedProfileItems(renameProfileItem(added, 0, ' ')), /이름/)
})
