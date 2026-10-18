export const MAX_PLANNING_BYTES = 10 * 1024 * 1024
export const MAX_PLANNING_CODEPOINTS = 100000
export const emptyPlanningDraft = { tab: 'file', file: null, text: '' }

export function validatePlanningFiles(files) {
  if (!files.length) return '기획 자료 파일을 선택해주세요.'
  if (files.length !== 1) return '파일은 한 개만 선택해주세요.'
  const file = files[0]
  if (!/\.(pdf|md|markdown)$/i.test(file.name)) return 'PDF 또는 Markdown 파일만 선택할 수 있어요.'
  if (file.size > MAX_PLANNING_BYTES) return '파일은 최대 10 MiB까지 선택할 수 있어요.'
  return ''
}

export const limitPlanningText = value => Array.from(value).slice(0, MAX_PLANNING_CODEPOINTS).join('')

export function validatePlanningText(text) {
  if (!text.trim()) return '기획 내용을 입력해주세요.'
  if (Array.from(text).length > MAX_PLANNING_CODEPOINTS) return '직접 입력은 100,000자까지 가능합니다.'
  return ''
}

// Shared by Planning submit and the analysis route guard; validates only local input.
export function validatePlanningDraft(draft) {
  if (draft.tab === 'file') return validatePlanningFiles(draft.file ? [draft.file] : [])
  if (draft.tab === 'text') return validatePlanningText(draft.text)
  return '기획 자료 입력 방식을 선택해주세요.'
}
