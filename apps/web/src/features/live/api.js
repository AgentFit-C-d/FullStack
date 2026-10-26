function csrfCookie() {
  const value = document.cookie.split('; ').find(part => part.startsWith('XSRF-TOKEN='))
  return value ? decodeURIComponent(value.slice('XSRF-TOKEN='.length)) : null
}

export async function apiRequest(path, { method = 'GET', body, signal, download = false } = {}) {
  if (method !== 'GET' && method !== 'HEAD') {
    const csrfResponse = await fetch('/api/auth/csrf', { credentials: 'same-origin', signal })
    if (!csrfResponse.ok) throw new Error('보안 토큰을 가져오지 못했습니다. 페이지를 새로고침해 주세요.')
  }
  const headers = {}
  const token = csrfCookie()
  if (method !== 'GET' && method !== 'HEAD' && token) headers['X-XSRF-TOKEN'] = token
  if (body !== undefined && !(body instanceof FormData)) headers['Content-Type'] = 'application/json'
  const response = await fetch(path, {
    method, credentials: 'same-origin', signal, headers,
    body: body instanceof FormData ? body : body === undefined ? undefined : JSON.stringify(body),
  })
  if (download && response.ok) return response.blob()
  const contentType = response.headers.get('content-type') || ''
  const result = contentType.includes('application/json') ? await response.json() : await response.text()
  if (!response.ok) {
    const code = result?.error?.code || result?.code || response.status
    const error = new Error(`요청 실패 (${code}). 입력 내용이나 현재 상태를 확인해 주세요.`)
    error.status = response.status
    throw error
  }
  return result || null
}

export const projectPath = id => `/api/projects/${encodeURIComponent(id)}`

export async function loadSession() {
  const value = await apiRequest('/api/auth/get-session')
  if (value === null || value === 'null') return null
  if (!value?.user?.id || !value.user.name) throw new Error('세션 응답이 올바르지 않습니다.')
  return value
}

export function environmentProfileBody(expectedVersion, osFamily, clientId, clientVersion, installedComponentsKnown, installedComponents = []) {
  if (!osFamily?.trim() || !clientId?.trim() || !clientVersion?.trim()) {
    throw new Error('OS·AI Client·Client 버전을 확인해 입력해 주세요.')
  }
  const components = installedComponentsKnown ? installedComponents.map(item => ({
    toolKey: item.toolKey.trim(), version: item.version?.trim() || null,
  })) : []
  if (components.length > 50 || components.some(item => !item.toolKey || Array.from(item.toolKey).length > 200
    || item.version && Array.from(item.version).length > 200)
    || new Set(components.map(item => item.toolKey)).size !== components.length) {
    throw new Error('설치 도구는 최대 50개이며, 이름은 필수·중복 불가입니다. 버전은 선택 입력입니다.')
  }
  return {
    expectedVersion, osFamily: osFamily.trim().toUpperCase(), clientId: clientId.trim(),
    clientVersion: clientVersion.trim(), installedComponentsKnown, installedComponents: components,
  }
}

export function planningInputBody(expectedVersion, inputText, file) {
  const body = new FormData()
  body.append('expectedVersion', new Blob([JSON.stringify(expectedVersion)], { type: 'application/json' }))
  if (file) body.append('file', file)
  else body.append('inputText', inputText)
  return body
}

export function reportBody(reportedState, reasonCode) {
  if (reportedState === 'APPLIED') {
    if (reasonCode) throw new Error('적용 완료에는 실패 사유를 보낼 수 없습니다.')
    return { reportedState, reasonCode: null }
  }
  if (reportedState === 'FAILED') {
    if (!['COPY_FAILED', 'AUTH_REQUIRED', 'DEPENDENCY_MISSING', 'UNKNOWN'].includes(reasonCode)) {
      throw new Error('실패 사유를 선택해 주세요.')
    }
    return { reportedState, reasonCode }
  }
  throw new Error('적용 상태를 선택해 주세요.')
}
