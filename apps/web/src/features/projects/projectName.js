// Project.name is trimmed and measured in Unicode code points by the shared API.
export const PROJECT_NAME_MAX = 100
export const limitProjectName = value => Array.from(value).slice(0, PROJECT_NAME_MAX).join('')
export function validateProjectName(value) {
  if (typeof value !== 'string' || !value.trim()) return '프로젝트 이름을 입력해주세요.'
  if (Array.from(value.trim()).length > PROJECT_NAME_MAX) return '프로젝트 이름은 최대 100 코드포인트까지 입력할 수 있어요.'
  return ''
}
