export function comparisonTargets(files = []) {
  return files.map(({ targetKey, relativePath }) => ({ targetKey, relativePath }))
}

export function providedFilesForPreview(targets, selected) {
  if (!selected.length) throw new Error('비교할 기존 파일을 하나 이상 선택해 주세요.')
  const allowed = new Set(targets.map(file => `${file.targetKey}\u0000${file.relativePath}`))
  if (selected.some(file => !allowed.has(`${file.targetKey}\u0000${file.relativePath}`))) {
    throw new Error('기존 파일은 생성 대상에 포함된 경로만 비교할 수 있습니다.')
  }
  return selected.map(({ targetKey, relativePath, content }) => ({ targetKey, relativePath, content }))
}
