import { canEnterDemoSelection } from '../selection/selectionModel.js'
export const unpreparedPreviewData = Object.freeze({ status: 'NOT_PREPARED', files: null, approval: null, download: null })
// UI identity only, never a server approval fingerprint or a configuration checksum.
export const selectionSignature = selection => JSON.stringify([selection.selectedIds, selection.permission])
export const createPreviewDraft = selection => ({ source: 'demo-ui', visible: false, acknowledged: false, selectionSignature: selectionSignature(selection) })
export function canEnterDemoPreview(state, id) {
  return state.active && canEnterDemoSelection(state, id) && state.previewDrafts?.[id]?.source === 'demo-ui'
}
export function syncPreviewSelection(entry, selection) {
  const signature = selectionSignature(selection)
  return entry.selectionSignature === signature ? entry : { ...entry, selectionSignature: signature, acknowledged: false }
}
export function editPreviewDraft(entry, action) {
  if (entry.source !== 'demo-ui') return entry
  if (action.edit === 'visibility' && typeof action.value === 'boolean') return { ...entry, visible: action.value, acknowledged: false }
  if (action.edit === 'acknowledge' && entry.visible && typeof action.value === 'boolean') return { ...entry, acknowledged: action.value }
  return entry
}
