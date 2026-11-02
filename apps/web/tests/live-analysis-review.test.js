import test from 'node:test'
import assert from 'node:assert/strict'
import { analysisReviewFields, applyAnalysisCandidate, confirmedMatchesDraft } from '../src/features/live/analysisReview.js'

const metadata = JSON.stringify({
  profile: { project_name: 'AgentFit', features: ['검색'], database: null },
  fieldStates: { project_name: 'suggested', features: 'suggested', database: 'unknown' },
  candidates: [
    { id: 'c0', field: 'project_name', proposedValue: 'AgentFit', status: 'suggested',
      evidence: [{ documentId: 'doc1', page: 1, start: 0, end: 8 }] },
    { id: 'c1', field: 'features', proposedValue: ['검색', '알림'], status: 'unresolved',
      evidence: [{ documentId: 'doc1', page: 2, start: 20, end: 30 }] },
    { id: 'c2', field: 'database', proposedValue: 'PostgreSQL', status: 'unresolved', evidence: [] },
  ],
  questions: [{ id: 'q1', field: 'database', message: 'DB를 확인해 주세요.' }],
})

test('DRAFT 메타데이터에서 미정·후보·근거 위치·질문을 함께 읽는다', () => {
  const fields = analysisReviewFields(metadata)
  assert.equal(fields.length, 10)
  assert.equal(fields.find(field => field.key === 'database').state, 'unknown')
  assert.equal(fields.find(field => field.key === 'database').questions[0].message, 'DB를 확인해 주세요.')
  assert.equal(fields.find(field => field.key === 'features').candidates[0].evidence[0].page, 2)
  assert.equal(fields.find(field => field.key === 'project_name').evidence[0].page, 1)
  assert.deepEqual(analysisReviewFields('{invalid'), [])
})

test('사용자가 미확정 후보를 선택하면 문서 추출값을 덮지 않고 사용자 수정으로 남긴다', () => {
  const fields = analysisReviewFields(metadata)
  const original = [{ fieldKey: 'features', ordinal: 0, name: '검색', source: 'DOCUMENT', evidence: '[]' }]
  const result = applyAnalysisCandidate({ projectName: 'AgentFit', projectType: '', domain: '' }, original,
    fields.find(field => field.key === 'features').candidates[0])
  assert.deepEqual(result.items.map(item => item.name), ['검색', '알림'])
  assert.equal(result.items[0].source, 'DOCUMENT')
  assert.equal(result.items[1].source, 'USER')
  assert.equal(result.items[1].evidence, '{}')
  const scalar = applyAnalysisCandidate(result.profile, result.items,
    fields.find(field => field.key === 'database').candidates[0])
  assert.equal(scalar.items.find(item => item.fieldKey === 'database').name, 'PostgreSQL')
  assert.equal(scalar.items.find(item => item.fieldKey === 'database').source, 'USER')
})

test('재분석으로 같은 DRAFT ID의 버전이 올라가면 이전 확정본으로 취급하지 않는다', () => {
  const confirmed = { sourceDraftId: 'draft-1', sourceDraftVersion: 1 }
  assert.equal(confirmedMatchesDraft(confirmed, { id: 'draft-1', version: 1 }), true)
  assert.equal(confirmedMatchesDraft(confirmed, { id: 'draft-1', version: 2 }), false)
  assert.equal(confirmedMatchesDraft(confirmed, { id: 'draft-2', version: 1 }), false)
})

test('같은 필드의 명시값이 충돌하면 두 후보를 모두 검토 대상으로 보인다', () => {
  const result = analysisReviewFields({
    profile: { database: 'PostgreSQL' }, fieldStates: { database: 'unresolved' },
    candidates: [
      { id: 'db1', field: 'database', proposedValue: 'PostgreSQL', status: 'suggested', evidence: [] },
      { id: 'db2', field: 'database', proposedValue: 'MySQL', status: 'suggested', evidence: [] },
    ],
  })
  assert.deepEqual(result.find(field => field.key === 'database').candidates.map(item => item.proposedValue),
    ['PostgreSQL', 'MySQL'])
})
