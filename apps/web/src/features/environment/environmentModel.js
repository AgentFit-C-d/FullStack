import { canEnterDemoRole, validateRole } from '../role/roleModel.js'

export const osOptions = [['', '미정'], ['WINDOWS', 'Windows'], ['MACOS', 'macOS'], ['LINUX', 'Linux'], ['OTHER', '기타']]
export const configurationOptions = [['UNKNOWN', '모름'], ['USER_SAYS_EMPTY', '기존 설정 없음'], ['HAS_EXISTING', '기존 설정 있음']]
export const authOptions = [['UNKNOWN', '모름'], ['READY', '준비됨'], ['NOT_READY', '준비 안 됨']]
export const emptyEnvironmentEntry = {
  source: 'demo-user', confirmed: false,
  draft: { osFamily: '', osVersion: '', clientVersion: '', configurationKnowledge: 'UNKNOWN', runtimeKnowledge: 'UNKNOWN', runtimes: [], installedComponentsKnowledge: 'UNKNOWN', installedComponents: [], authReadiness: [] },
  value: { osFamily: null, osVersion: null, clientVersion: null, configurationKnowledge: 'UNKNOWN', runtimes: null, installedComponents: null, authReadiness: [] },
}
export function canEnterDemoEnvironment(profile, role) {
  return canEnterDemoRole(profile) && !!role && role.source === 'demo-user' && role.confirmed === true && !Object.keys(validateRole(role.draft)).length
}
const overLimit = value => Array.from(value).length > 200
export function validateEnvironment(draft) {
  const errors = {}
  if (!osOptions.some(([value]) => value === draft.osFamily)) errors.osFamily = '시안에 정의된 OS를 선택해주세요.'
  if (!configurationOptions.some(([value]) => value === draft.configurationKnowledge)) errors.configurationKnowledge = '현재 설정 상태를 선택해주세요.'
  for (const key of ['osVersion', 'clientVersion']) if (overLimit(draft[key])) errors[key] = '최대 200 코드포인트까지 입력할 수 있어요.'
  for (const [key, knowledge] of [['runtimes', 'runtimeKnowledge'], ['installedComponents', 'installedComponentsKnowledge']]) {
    if (!['UNKNOWN', 'KNOWN'].includes(draft[knowledge])) errors[knowledge] = '정보 상태를 선택해주세요.'
    if (draft[knowledge] !== 'KNOWN') continue
    if (draft[key].length > 30) errors[key] = '최대 30개까지 입력할 수 있어요.'
    draft[key].forEach((item, index) => {
      if (!item.name.trim() || overLimit(item.name) || overLimit(item.version)) errors[`${key}-${index}`] = '이름을 입력하거나 빈 행을 삭제해주세요. 이름·버전은 각각 최대 200 코드포인트입니다.'
    })
  }
  if (draft.authReadiness.length > 30) errors.authReadiness = '최대 30개까지 입력할 수 있어요.'
  draft.authReadiness.forEach((item, index) => {
    if (!item.service.trim() || overLimit(item.service) || !authOptions.some(([value]) => value === item.state)) errors[`authReadiness-${index}`] = '서비스 이름(최대 200 코드포인트)과 준비 상태를 확인하거나 빈 행을 삭제해주세요.'
  })
  return errors
}
export function editEnvironment(entry, action) {
  if (entry.source !== 'demo-user') return entry
  const draft = structuredClone(entry.draft)
  const { field, value, index, property } = action
  if (action.type === 'environment-confirm') {
    if (Object.keys(validateEnvironment(draft)).length) return entry
    const nullable = text => text.trim() ? text : null
    return { ...entry, confirmed: true, value: {
      osFamily: draft.osFamily || null, osVersion: nullable(draft.osVersion), clientVersion: nullable(draft.clientVersion), configurationKnowledge: draft.configurationKnowledge,
      runtimes: draft.runtimeKnowledge === 'UNKNOWN' ? null : structuredClone(draft.runtimes),
      installedComponents: draft.installedComponentsKnowledge === 'UNKNOWN' ? null : structuredClone(draft.installedComponents), authReadiness: structuredClone(draft.authReadiness),
    } }
  }
  if (action.edit === 'scalar') {
    if (!['osFamily', 'osVersion', 'clientVersion', 'configurationKnowledge', 'runtimeKnowledge', 'installedComponentsKnowledge'].includes(field) || typeof value !== 'string') return entry
    if (field === 'osFamily' && !osOptions.some(([option]) => option === value)) return entry
    if (field === 'configurationKnowledge' && !configurationOptions.some(([option]) => option === value)) return entry
    if (['runtimeKnowledge', 'installedComponentsKnowledge'].includes(field) && !['UNKNOWN', 'KNOWN'].includes(value)) return entry
    draft[field] = value
  } else {
    if (!['runtimes', 'installedComponents', 'authReadiness'].includes(field)) return entry
    if (field === 'runtimes' && draft.runtimeKnowledge !== 'KNOWN' || field === 'installedComponents' && draft.installedComponentsKnowledge !== 'KNOWN') return entry
    if (action.edit === 'add') {
      if (draft[field].length >= 30) return entry
      draft[field].push(field === 'authReadiness' ? { service: '', state: 'UNKNOWN' } : { name: '', version: '' })
    } else {
      if (!Number.isInteger(index) || index < 0 || index >= draft[field].length) return entry
      if (action.edit === 'remove') draft[field].splice(index, 1)
      else if (action.edit === 'item') {
        if (!(field === 'authReadiness' ? ['service', 'state'] : ['name', 'version']).includes(property) || typeof value !== 'string') return entry
        if (property === 'state' && !authOptions.some(([option]) => option === value)) return entry
        draft[field][index][property] = value
      } else return entry
    }
  }
  return { ...entry, draft, confirmed: false }
}
