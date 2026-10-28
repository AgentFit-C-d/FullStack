import test from 'node:test'
import assert from 'node:assert/strict'
import { inputChanges, inputsDirty } from '../src/features/live/inputState.js'

const saved = {
  developer: { role: 'FULLSTACK', responsibilities: ['API', 'DB'] },
  environment: { osFamily: 'WINDOWS', clientId: 'demo-claude-code', clientVersion: 'demo-1',
    installedComponentsKnown: true, installedComponents: [] },
}
const form = { role: 'FULLSTACK', responsibilities: 'API\nDB', osFamily: 'WINDOWS',
  clientId: 'demo-claude-code', clientVersion: 'demo-1', installedComponentsKnown: true,
  installedComponents: [] }

test('저장된 역할·환경과 다른 초안은 추천·승인 전에 저장이 필요하다', () => {
  assert.equal(inputsDirty(saved, form), false)
  assert.equal(inputsDirty(saved, { ...form, responsibilities: 'API\nDB\n테스트' }), true)
  assert.equal(inputsDirty(saved, { ...form, clientVersion: 'demo-2' }), true)
  assert.equal(inputsDirty(saved, { ...form, installedComponentsKnown: false }), true)
  assert.equal(inputsDirty(saved, { ...form, installedComponents: [{ toolKey: 'skill-a', version: '' }] }), true)
  assert.equal(inputsDirty(null, form), false)
})

test('역할과 환경 변경을 분리해 불필요한 버전 증가를 막는다', () => {
  assert.deepEqual(inputChanges(saved, form), { developer: false, environment: false })
  assert.deepEqual(inputChanges(saved, { ...form, responsibilities: 'API\nDB\n테스트' }),
    { developer: true, environment: false })
  assert.deepEqual(inputChanges(saved, { ...form, installedComponents: [{ toolKey: 'skill-a', version: null }] }),
    { developer: false, environment: true })
  assert.deepEqual(inputChanges(null, form), { developer: true, environment: true })
})
