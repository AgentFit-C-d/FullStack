import { validateProjectName } from '../projects/projectName.js'
import { containsDemoData, isPublicSession, validServerId } from './serviceModel.js'

// Existing phase1 OpenAPI contracts only. These are not new endpoints.
export const serviceContracts = Object.freeze({
  session: Object.freeze({ method: 'GET', path: '/api/auth/get-session' }),
  projects: Object.freeze({ method: 'GET', path: '/api/projects' }),
  project: Object.freeze({ method: 'GET', path: '/api/projects/{projectId}' }),
  createProject: Object.freeze({ method: 'POST', path: '/api/projects', request: 'CreateProjectRequest' }),
  startAnalysis: Object.freeze({ method: 'POST', path: '/api/projects/{projectId}/analysis', request: 'raw PDF/Markdown/text' }),
  confirmProfile: Object.freeze({ method: 'PATCH', path: '/api/projects/{projectId}/profile', request: 'SaveProfileRequest' }),
})
const unavailable = code => ({ status: 'unavailable', data: null, code })
const failure = (status, code) => ({ status, data: null, code })
const validProject = value => value && validServerId(value.id) && !validateProjectName(value.name) && Number.isInteger(value.version) && value.version >= 0 && typeof value.createdAt === 'string' && Number.isFinite(Date.parse(value.createdAt)) && typeof value.updatedAt === 'string' && Number.isFinite(Date.parse(value.updatedAt))
// transport is a future real HTTP adapter: ({method,path,signal}) => {status,data}.
// There is deliberately no fetch, sample response, local session, or storage fallback.
export function createServiceApi({ transport } = {}) {
  return {
    async read(operation, input = {}, { signal } = {}) {
      const contract = serviceContracts[operation]
      if (!contract || contract.method !== 'GET') return unavailable('CONTRACT_PENDING')
      if (!transport) return unavailable('NOT_CONNECTED')
      if (signal?.aborted) return failure('error', 'CANCELLED')
      if (operation === 'project' && !validServerId(input.projectId)) return failure('error', 'INVALID_INPUT')
      const path = contract.path.replace('{projectId}', encodeURIComponent(input.projectId ?? ''))
      try {
        const response = await transport({ method: contract.method, path, signal })
        if (signal?.aborted) return failure('error', 'CANCELLED')
        if (response?.status === 401) return failure('unauthenticated', 'UNAUTHENTICATED')
        if (response?.status === 403) return failure('forbidden', 'FORBIDDEN')
        if (response?.status === 404) return failure('not-found', 'NOT_FOUND')
        if (response?.status !== 200) return failure('error', 'REQUEST_FAILED')
        if (!Object.hasOwn(response, 'data') || containsDemoData(response)) return failure('error', 'INVALID_RESPONSE')
        const data = response.data
        if (operation === 'session') {
          if (data === null) return failure('unauthenticated', 'UNAUTHENTICATED')
          if (!isPublicSession(data)) return failure('unauthenticated', 'INVALID_SESSION')
        }
        if (operation === 'projects' && (!Array.isArray(data?.projects) || !data.projects.every(validProject))) return failure('error', 'INVALID_RESPONSE')
        if (operation === 'project' && (!validProject(data?.project) || data.project.id !== input.projectId || !['confirmed', 'draft', 'latestAttempt'].every(key => Object.hasOwn(data, key)))) return failure('error', 'INVALID_RESPONSE')
        return { status: operation === 'projects' && data.projects.length === 0 ? 'empty' : 'ready', source: 'server', data }
      } catch {
        return failure('error', 'REQUEST_FAILED')
      }
    },
    // Writes, OAuth start, approval and download remain disconnected, even with a read transport.
    async execute(operation) { return unavailable(serviceContracts[operation] ? 'NOT_CONNECTED' : 'CONTRACT_PENDING') },
  }
}
export const serviceApi = createServiceApi()

export function createHttpTransport(fetcher = fetch) {
  return async ({ method, path, signal }) => {
    const response = await fetcher(path, {
      method, signal, credentials: 'same-origin', headers: { Accept: 'application/json' },
    })
    const contentType = response.headers.get('content-type') || ''
    const data = contentType.includes('application/json') ? await response.json() : null
    return { status: response.status, data: data === 'null' ? null : data }
  }
}

export const httpServiceApi = createServiceApi({ transport: createHttpTransport() })
