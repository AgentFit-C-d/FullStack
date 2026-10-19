export function canRenameProject(detail) {
  return Boolean(detail && !detail.draft && !detail.confirmed && !detail.latestAttempt)
}

export function renameProjectRequest(name, expectedVersion) {
  const normalized = name.trim()
  if (!normalized || Array.from(normalized).length > 100) {
    throw new Error('프로젝트 이름을 1~100자로 입력해 주세요.')
  }
  return { name: normalized, expectedVersion }
}

export function deleteProjectRequest(currentName, confirmationName, expectedVersion) {
  if (confirmationName !== currentName) {
    throw new Error('삭제하려면 현재 프로젝트 이름을 정확히 입력해 주세요.')
  }
  return { confirmation: true, expectedVersion }
}
