import { validateProfile } from '../profile/profileModel.js'

export const roleOptions = ['프론트엔드', '백엔드', '풀스택', 'AI', '디자이너']
export const experienceOptions = ['초보', '중급', '숙련']
export const emptyRoleEntry = {
  source: 'demo-user', draft: { role: '', responsibilitiesText: '', experience: '' },
  value: { role: '', responsibilities: [], experience: '' }, confirmed: false,
}
export const parseResponsibilities = text => text.split(/\r?\n/).map(line => line.trim()).filter(Boolean)

export function validateRole(draft) {
  const errors = {}
  if (draft.role && !roleOptions.includes(draft.role)) errors.role = '시안에 정의된 역할을 선택해주세요.'
  if (draft.experience && !experienceOptions.includes(draft.experience)) errors.experience = '시안에 정의된 경험 수준을 선택해주세요.'
  const items = parseResponsibilities(draft.responsibilitiesText)
  if (Array.from(draft.responsibilitiesText).length > 6000) errors.responsibilitiesText = '담당 업무는 줄바꿈·공백·구분자를 포함해 전체 최대 6,000 코드포인트까지 입력할 수 있어요.'
  else if (items.length > 30) errors.responsibilitiesText = '담당 업무는 최대 30개까지 입력할 수 있어요.'
  else if (items.some(item => Array.from(item).length > 200)) errors.responsibilitiesText = '담당 업무는 항목당 최대 200 코드포인트까지 입력할 수 있어요.'
  return errors
}

export function canEnterDemoRole(profile) {
  return !!profile && ['demo-manual', 'demo-fixture'].includes(profile.source) && profile.confirmed === true && Object.keys(validateProfile(profile.profile, profile.modes)).length === 0
}

export function editRole(entry, action) {
  if (entry.source !== 'demo-user') return entry
  if (action.type === 'role-edit') {
    if (!['role', 'responsibilitiesText', 'experience'].includes(action.field) || typeof action.value !== 'string') return entry
    if (action.field === 'role' && action.value && !roleOptions.includes(action.value)) return entry
    if (action.field === 'experience' && action.value && !experienceOptions.includes(action.value)) return entry
    return { ...entry, draft: { ...entry.draft, [action.field]: action.value }, confirmed: false }
  }
  if (Object.keys(validateRole(entry.draft)).length) return entry
  return { ...entry, value: { role: entry.draft.role, responsibilities: parseResponsibilities(entry.draft.responsibilitiesText), experience: entry.draft.experience }, confirmed: true }
}
