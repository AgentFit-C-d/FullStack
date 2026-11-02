import { addProfileItem } from './profileItems.js'

const fields = [
  ['project_name', '프로젝트명'], ['project_type', '서비스 유형'], ['domain', '도메인'],
  ['frontend', '프론트엔드'], ['backend', '백엔드'], ['ai', 'AI'],
  ['database', '데이터베이스'], ['deployment', '배포'],
  ['features', '주요 기능'], ['external_integrations', '외부 연동'],
]
const scalarKeys = { project_name: 'projectName', project_type: 'projectType', domain: 'domain' }

export function analysisReviewFields(raw) {
  let result
  try { result = typeof raw === 'string' ? JSON.parse(raw) : raw }
  catch { return [] }
  if (!result || typeof result !== 'object' || !result.profile || !result.fieldStates) return []
  const candidates = Array.isArray(result.candidates) ? result.candidates : []
  const questions = Array.isArray(result.questions) ? result.questions : []
  return fields.map(([key, label]) => ({
    key, label,
    value: result.profile[key] ?? null,
    state: result.fieldStates[key] || 'unknown',
    evidence: candidates.filter(candidate => candidate?.field === key && candidate.status === 'suggested')
      .flatMap(candidate => Array.isArray(candidate.evidence) ? candidate.evidence : []),
    candidates: candidates.filter(candidate => candidate?.field === key &&
      (candidate.status === 'unresolved' || result.fieldStates[key] === 'unresolved')),
    questions: questions.filter(question => question?.field === key),
  }))
}

export function confirmedMatchesDraft(confirmed, draft) {
  return Boolean(confirmed && draft && confirmed.sourceDraftId === draft.id
    && Number(confirmed.sourceDraftVersion) === Number(draft.version))
}

export function applyAnalysisCandidate(profile, items, candidate) {
  if (!candidate || !fields.some(([key]) => key === candidate.field)) {
    throw new Error('반영할 기획 후보를 찾을 수 없습니다.')
  }
  const values = (Array.isArray(candidate.proposedValue) ? candidate.proposedValue : [candidate.proposedValue])
    .map(value => typeof value === 'string' ? value.trim() : '')
    .filter(Boolean)
  if (!values.length) throw new Error('값이 없는 후보는 반영할 수 없습니다.')
  const profileKey = scalarKeys[candidate.field]
  if (profileKey) {
    if (values.length !== 1) throw new Error('기본 정보에는 한 가지 값만 반영할 수 있습니다.')
    return { profile: { ...profile, [profileKey]: values[0] }, items }
  }
  let next = items
  for (const value of values) {
    if (!next.some(item => item.fieldKey === candidate.field && item.name.trim() === value)) {
      next = addProfileItem(next, candidate.field, value)
    }
  }
  return { profile, items: next }
}
