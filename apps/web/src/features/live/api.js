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
