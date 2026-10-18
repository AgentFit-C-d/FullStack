export const profileFieldKeys = [
  'frontend', 'backend', 'ai', 'database', 'deployment', 'features', 'external_integrations',
]

export function addProfileItem(items, fieldKey, name) {
  const value = name.trim()
  if (!profileFieldKeys.includes(fieldKey)) throw new Error('추가할 기획 항목 종류를 선택해 주세요.')
  if (!value || Array.from(value).length > 200) throw new Error('기획 항목을 1~200자로 입력해 주세요.')
  const ordinal = Math.max(-1, ...items.filter(item => item.fieldKey === fieldKey).map(item => item.ordinal)) + 1
  return [...items, { fieldKey, ordinal, category: null, area: null, name: value,
    version: null, source: 'USER', evidence: '{}' }]
}

export function renameProfileItem(items, index, name) {
  if (!items[index]) throw new Error('수정할 항목이 없습니다.')
  return items.map((item, position) => position === index && item.name !== name
    ? { ...item, name, source: 'USER', evidence: '{}' } : item)
}

export function confirmedProfileItems(items) {
  if (items.some(item => !item.name?.trim() || Array.from(item.name.trim()).length > 200)) {
    throw new Error('모든 기획 항목의 이름을 1~200자로 입력해 주세요.')
  }
  return items.map(item => ({ ...item, name: item.name.trim() }))
}
