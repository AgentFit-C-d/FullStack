import { createDemoProfileFixture } from './demoProfileFixture.js'

export const scalarFields = [['project_name', '프로젝트명'], ['project_type', '프로젝트 유형'], ['domain', '도메인'], ['database', 'Database'], ['deployment', 'Deployment']]
export const listFields = [['features', '주요 기능'], ['frontend', 'Frontend'], ['backend', 'Backend'], ['ai', 'AI'], ['external_integrations', 'External Integrations']]
export const automaticScalarFields = ['project_type', 'domain', 'database', 'deployment']
const copy = value => structuredClone(value)

export function createDemoProfile(projectName, example = false) {
  const profile = example ? createDemoProfileFixture() : Object.fromEntries([...scalarFields, ...listFields].map(([key]) => [key, key === 'project_name' ? projectName : null]))
  return { source: example ? 'demo-fixture' : 'demo-manual', nameOrigin: example ? 'fixture' : 'project', profile, draft: copy(profile), modes: Object.fromEntries(listFields.map(([key]) => [key, profile[key] === null ? 'UNKNOWN' : profile[key].length ? 'KNOWN' : 'KNOWN_EMPTY'])), confirmed: false }
}

export function validateProfile(draft, modes) {
  const errors = {}
  for (const [key] of scalarFields) {
    const value = draft[key]
    if (key === 'project_name' && !value?.trim()) errors[key] = '프로젝트 이름을 입력해주세요.'
    else if (automaticScalarFields.includes(key) && typeof value === 'string' && !value.trim()) continue
    else if (value !== null && (typeof value !== 'string' || !value.trim() || Array.from(value).length > 200)) errors[key] = '1~200 코드포인트를 입력하거나 미확인으로 설정해주세요.'
  }
  for (const [key] of listFields) {
    const value = draft[key]
    if (modes[key] === 'UNKNOWN' && value === null) continue
    if (modes[key] === 'KNOWN_EMPTY' && Array.isArray(value) && value.length === 0) continue
    if (modes[key] !== 'KNOWN' || !Array.isArray(value) || !value.length || value.length > 30 || value.some(item => typeof item !== 'string' || !item.trim() || Array.from(item).length > 200)) errors[key] = '값 입력은 1~30개, 항목당 1~200 코드포인트가 필요해요. 항목이 없으면 ‘명시적으로 없음’을 선택해주세요.'
  }
  return errors
}

export function editDemoProfile(entry, action) {
  if (!['demo-fixture', 'demo-manual'].includes(entry.source)) return entry
  const { key } = action
  const draft = copy(entry.draft)
  const modes = { ...entry.modes }
  const scalar = scalarFields.some(([field]) => field === key)
  const list = listFields.some(([field]) => field === key)
  switch (action.edit) {
    case 'scalar':
      if (!scalar || typeof action.value !== 'string') return entry
      draft[key] = automaticScalarFields.includes(key) && !action.value.trim() ? null : action.value
      break
    case 'unknown':
      if (!scalar || key === 'project_name') return entry
      draft[key] = null
      break
    case 'mode':
      if (!list || !['UNKNOWN', 'KNOWN_EMPTY', 'KNOWN'].includes(action.value)) return entry
      modes[key] = action.value
      draft[key] = action.value === 'UNKNOWN' ? null : action.value === 'KNOWN_EMPTY' ? [] : (draft[key] ?? [])
      break
    case 'add':
      if (!list || modes[key] !== 'KNOWN' || draft[key].length >= 30) return entry
      draft[key].push('')
      break
    case 'item':
      if (!list || modes[key] !== 'KNOWN' || !Number.isInteger(action.index) || action.index < 0 || action.index >= draft[key].length || typeof action.value !== 'string') return entry
      draft[key][action.index] = action.value
      break
    case 'remove':
      if (!list || modes[key] !== 'KNOWN' || !Number.isInteger(action.index) || action.index < 0 || action.index >= draft[key].length) return entry
      draft[key].splice(action.index, 1)
      if (!draft[key].length) modes[key] = 'KNOWN_EMPTY'
      break
    case 'confirm':
      if (Object.keys(validateProfile(draft, modes)).length) return entry
      return { ...entry, profile: { ...copy(draft), ...Object.fromEntries(automaticScalarFields.map(field => [field, draft[field]?.trim() ? draft[field] : null])) }, confirmed: true }
    default: return entry
  }
  return { ...entry, draft, modes, confirmed: false, editedFields: action.edit === 'scalar' ? { ...entry.editedFields, [key]: true } : entry.editedFields, nameOrigin: key === 'project_name' ? 'user' : entry.nameOrigin }
}


export function getTechnicalFieldLabel(entry, key) {
  const value = entry.draft[key]
  if (value === null || !value?.trim()) return '확인되지 않음'
  if (Array.from(value).length > 200) return '입력 확인 필요'
  if (entry.editedFields?.[key] || entry.sources?.[key] === 'USER') return '사용자 입력'
  if (entry.sources?.[key] === 'DOCUMENT') return '문서에서 확인'
  return entry.source === 'demo-fixture' ? '예시 데이터' : '확인된 값'
}
