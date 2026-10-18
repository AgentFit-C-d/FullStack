import { canEnterDemoPreview, selectionSignature } from '../preview/previewModel.js'
export const reportStatusOptions = [['', '선택해주세요'], ['APPLIED', '적용 완료 (APPLIED)'], ['FAILED', '적용 실패 (FAILED)']]
export const failureReasons = ['파일 복사 실패', '인증 필요', '의존성 누락', '알 수 없음']
export const unpreparedCompleteData = Object.freeze({ artifact: null, approval: null, download: null, verification: 'NOT_RUN' })
export function canPreviewComplete(state, id) {
  const preview = state.previewDrafts?.[id]
  return !!(canEnterDemoPreview(state, id) && preview.visible && preview.acknowledged && preview.selectionSignature === selectionSignature(state.selectionPreviews[id]))
}
export function canEnterDemoComplete(state, id) {
  const entry = state.completeDrafts?.[id]
  return canPreviewComplete(state, id) && entry?.source === 'demo-ui' && entry.entered === true && entry.selectionSignature === state.previewDrafts[id].selectionSignature
}
export function createCompleteDraft(signature) {
  return { source: 'demo-ui', entered: true, selectionSignature: signature, draft: { status: '', reason: '' }, report: null }
}
export function revokeCompleteAccess(entries, id) {
  const entry = entries?.[id]
  return entry?.entered ? { ...entries, [id]: { ...entry, entered: false } } : entries
}
export function validateCompleteReport(draft) {
  const errors = {}
  if (!['APPLIED', 'FAILED'].includes(draft.status)) errors.status = '적용 상태를 선택해주세요.'
  if (draft.status === 'FAILED' && !failureReasons.includes(draft.reason)) errors.reason = '실패 사유를 선택해주세요.'
  return errors
}
export function editCompleteDraft(entry, action) {
  if (entry.source !== 'demo-ui') return entry
  if (action.type === 'complete-report') {
    if (Object.keys(validateCompleteReport(entry.draft)).length) return entry
    return { ...entry, report: { source: 'demo-user-report', status: entry.draft.status, reason: entry.draft.status === 'FAILED' ? entry.draft.reason : '', verification: 'NOT_RUN', selectionSignature: entry.selectionSignature } }
  }
  if (action.type !== 'complete-edit' || !['status', 'reason'].includes(action.field) || typeof action.value !== 'string') return entry
  if (action.field === 'status' && !reportStatusOptions.some(([value]) => value === action.value)) return entry
  if (action.field === 'reason' && action.value !== '' && !failureReasons.includes(action.value)) return entry
  return { ...entry, draft: { ...entry.draft, [action.field]: action.value } }
}
