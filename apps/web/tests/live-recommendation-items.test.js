import test from 'node:test'
import assert from 'node:assert/strict'
import { offeredItems, riskReviewText } from '../src/features/live/recommendationItems.js'
import { defaultPermissionChoices, permissionSelections } from '../src/features/live/permissions.js'

test('필수 도구는 기본 선택하고 선택 후보는 명시적으로 선택할 때만 권한에 포함한다', () => {
  const recommendation = {
    items: [{ toolKey: 'required', permissionOptions: [{ mappingKey: 'read', defaultPolicy: 'ASK_EACH_TIME', supportedPolicies: ['ASK_EACH_TIME'] }] }],
    optionalItems: [{ toolKey: 'optional', permissionOptions: [{ mappingKey: 'write', defaultPolicy: 'ASK_EACH_TIME', supportedPolicies: ['ASK_EACH_TIME'] }] }],
  }
  const all = offeredItems(recommendation)
  assert.deepEqual(all.map(item => item.toolKey), ['required', 'optional'])
  const defaults = defaultPermissionChoices(all)
  assert.deepEqual(permissionSelections(all, ['required'], defaults).map(item => item.toolKey), ['required'])
  assert.deepEqual(permissionSelections(all, ['required', 'optional'], defaults).map(item => item.toolKey), ['required', 'optional'])
})

test('위험도 검토 전에는 안전성을 단정하지 않고 검토된 정보만 설명한다', () => {
  assert.match(riskReviewText({ status: 'UNASSESSED' }), /검토 전/)
  assert.match(riskReviewText({ status: 'REVIEWED', level: 'HIGH', externalAuth: 'REQUIRED', reason: '외부 저장소에 연결' }), /높음/)
  assert.match(riskReviewText({ status: 'REVIEWED', level: 'HIGH', externalAuth: 'REQUIRED', reason: '외부 저장소에 연결' }), /외부 인증 필요/)
})
