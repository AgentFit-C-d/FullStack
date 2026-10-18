import test from 'node:test'
import assert from 'node:assert/strict'
import { inputsDirty } from '../src/features/live/inputState.js'

const saved = {
  developer: { role: 'FULLSTACK', responsibilities: ['API', 'DB'] },
  environment: { osFamily: 'WINDOWS', clientId: 'demo-claude-code', clientVersion: 'demo-1', installedComponentsKnown: true },
}
const form = { role: 'FULLSTACK', responsibilities: 'API\nDB', osFamily: 'WINDOWS',
  clientId: 'demo-claude-code', clientVersion: 'demo-1', installedComponentsKnown: true }

test('저장된 역할·환경과 다른 초안은 추천·승인 전에 저장이 필요하다', () => {
  assert.equal(inputsDirty(saved, form), false)
  assert.equal(inputsDirty(saved, { ...form, responsibilities: 'API\nDB\n테스트' }), true)
  assert.equal(inputsDirty(saved, { ...form, clientVersion: 'demo-2' }), true)
  assert.equal(inputsDirty(saved, { ...form, installedComponentsKnown: false }), true)
  assert.equal(inputsDirty(null, form), false)
})
