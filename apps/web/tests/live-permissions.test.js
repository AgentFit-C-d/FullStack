import test from 'node:test'
import assert from 'node:assert/strict'
import { defaultPermissionChoices, permissionKey, permissionSelections, previewPolicies } from '../src/features/live/permissions.js'

const items = [{ toolKey: 'skill', permissionOptions: [
  { mappingKey: 'read', required: false, supportedPolicies: ['ASK_EACH_TIME', 'DENY'], defaultPolicy: 'ASK_EACH_TIME' },
  { mappingKey: 'write', required: true, supportedPolicies: ['ASK_EACH_TIME', 'ALWAYS_ALLOW', 'DENY'], defaultPolicy: 'ASK_EACH_TIME' },
] }]

test('정적 설정에서 표현 가능한 권한만 Preview 요청에 반영한다', () => {
  const defaults = defaultPermissionChoices(items)
  assert.equal(defaults[permissionKey('skill', 'read')], 'ASK_EACH_TIME')
  assert.deepEqual(previewPolicies(items[0].permissionOptions[0]), ['ASK_EACH_TIME'])
  assert.deepEqual(previewPolicies(items[0].permissionOptions[1]), ['ASK_EACH_TIME', 'ALWAYS_ALLOW'])
  const changed = { ...defaults, [permissionKey('skill', 'write')]: 'ALWAYS_ALLOW' }
  assert.deepEqual(permissionSelections(items, ['skill'], changed), [
    { toolKey: 'skill', mappingKey: 'read', policy: 'ASK_EACH_TIME' },
    { toolKey: 'skill', mappingKey: 'write', policy: 'ALWAYS_ALLOW' },
  ])
  assert.deepEqual(permissionSelections(items, [], changed), [])
  assert.throws(() => permissionSelections(items, ['skill'], { ...changed, [permissionKey('skill', 'read')]: 'DENY' }), /개별 권한 거부/)
  assert.throws(() => permissionSelections(items, ['skill'], { ...changed, [permissionKey('skill', 'write')]: 'DENY' }), /개별 권한 거부/)
})
