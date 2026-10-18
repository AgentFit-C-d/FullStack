const exactKeys = (value, keys) => !!value && typeof value === 'object' && !Array.isArray(value) && Object.keys(value).length === keys.length && keys.every(key => Object.hasOwn(value, key))
export const validServerId = value => typeof value === 'string' && value.length > 0 && Array.from(value).length <= 128
export function containsDemoData(value) {
  if (!value || typeof value !== 'object') return false
  if (typeof value.source === 'string' && (value.source === 'demo' || value.source.startsWith('demo-'))) return true
  return Object.values(value).some(containsDemoData)
}
export function isPublicSession(data, now = Date.now()) {
  return exactKeys(data, ['user', 'session']) && exactKeys(data.user, ['id', 'name']) && validServerId(data.user.id) && typeof data.user.name === 'string' && exactKeys(data.session, ['expiresAt']) && typeof data.session.expiresAt === 'string' && Date.parse(data.session.expiresAt) > now
}
export function isAuthenticatedSession(result) {
  return result?.status === 'ready' && result.source === 'server' && !containsDemoData(result.data) && isPublicSession(result.data)
}
export async function loadServiceRoute(api, resource, { request, params }) {
  const projectId = params.projectId ?? null
  try {
    const auth = await api.read('session', {}, { signal: request.signal })
    if (!isAuthenticatedSession(auth)) return { auth: auth.status === 'ready' ? { status: 'unauthenticated', data: null } : auth, resource: null, projectId }
    const result = resource ? await api.read(resource, { projectId }, { signal: request.signal }) : { status: 'unavailable', data: null }
    // A resource 401 also expires this route's authentication UX.
    return { auth: result.status === 'unauthenticated' ? result : auth, resource: result, projectId }
  } catch {
    return { auth: { status: 'error', data: null }, resource: null, projectId }
  }
}
