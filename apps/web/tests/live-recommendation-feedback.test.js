import test from 'node:test'
import assert from 'node:assert/strict'
import { recommendationFeedback, requiredCapabilityLabels } from '../src/features/live/recommendationFeedback.js'

test('저장된 AI 판정에서 필수 기능만 사용자에게 설명한다', () => {
  const labels = requiredCapabilityLabels({ capabilities: [
    { key: 'cap_test_authoring', necessity: 'REQUIRED' },
    { key: 'cap_test_execution', necessity: 'REQUIRED' },
    { key: 'cap_code_review', necessity: 'NOT_REQUESTED' },
  ] })
  assert.deepEqual(labels, ['테스트 작성', '테스트 실행'])
})

test('추천 결과별로 다음 행동이 다른 안내를 표시한다', () => {
  assert.match(recommendationFeedback({ status: 'NEEDS_INFORMATION' }), /추가 정보/)
  assert.match(recommendationFeedback({ status: 'NO_COMPATIBLE_TOOLS' }), /승인된 도구/)
  assert.match(recommendationFeedback({ status: 'NO_ADDITIONS_NEEDED' }), /추가 도구/)
  assert.equal(recommendationFeedback({ status: 'RECOMMENDED' }), '')
})

test('질문 코드에 따라 실제로 수정할 입력을 안내한다', () => {
  assert.match(recommendationFeedback({ status: 'NEEDS_INFORMATION', notices: [
    { type: 'QUESTION', code: 'installed_components' },
  ] }), /설치 도구 목록/)
  assert.match(recommendationFeedback({ status: 'NEEDS_INFORMATION', notices: [
    { type: 'QUESTION', code: 'environment_target' },
  ] }), /OS·Client/)
  assert.match(recommendationFeedback({ status: 'NEEDS_INFORMATION', notices: [
    { type: 'QUESTION', code: 'undetermined_capability' },
  ] }), /기획·담당 업무/)
})

test('호환 결과는 저장된 사유 코드로 설명한다', () => {
  assert.match(recommendationFeedback({ status: 'NO_COMPATIBLE_TOOLS', notices: [
    { type: 'REASON', code: 'no_verified_capability_support' },
  ] }), /필수 기능을 지원하는/)
  assert.match(recommendationFeedback({ status: 'NO_COMPATIBLE_TOOLS', notices: [
    { type: 'REASON', code: 'no_verified_compatible_combination' },
  ] }), /조합/)
  assert.match(recommendationFeedback({ status: 'NO_ADDITIONS_NEEDED', notices: [
    { type: 'REASON', code: 'installed_components_cover_required' },
  ] }), /이미 설치된/)
})

test('입력이 바뀐 추천은 저장 당시의 결과보다 만료 안내를 우선한다', () => {
  assert.match(recommendationFeedback({ status: 'NO_COMPATIBLE_TOOLS', validity: 'STALE' }), /만료/)
})
