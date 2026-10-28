import { Navigate, redirect } from 'react-router'
import App from './App.jsx'
import { ServiceLoginPage } from '../pages/LoginPage.jsx'

import ServicePage, { ServiceLoading } from '../pages/ServicePage.jsx'
import { serviceStages } from '../features/service/serviceStages.js'
import { serviceApi as defaultServiceApi } from '../features/service/serviceApi.js'
import { isAuthenticatedSession, loadServiceRoute } from '../features/service/serviceModel.js'
import LivePage from '../features/live/LivePage.jsx'
import ServiceProjectCreatePage from '../pages/ServiceProjectCreatePage.jsx'
import ServiceWorkflowEntry from '../features/service/ServiceWorkflowEntry.jsx'
import ServiceEntryPage from '../features/service/ServiceEntryPage.jsx'

export function createAppRoutes({ serviceApi = defaultServiceApi } = {}) {
  return [{
    element: <App />,
    children: [
      { path: '/demo/*', element: <Navigate to="/app/live" replace /> },
      {
        path: '/', element: <ServiceEntryPage />, handle: { mode: 'service' },
        loader: async ({ request }) => {
          if (new URL(request.url).searchParams.get('login') === 'success') return redirect('/app')
          try {
            const session = await serviceApi.read('session', {}, { signal: request.signal })
            if (isAuthenticatedSession(session)) return redirect('/app')
          } catch { /* The login page remains available while the API is unreachable. */ }
          return null
        },
        HydrateFallback: ServiceLoading,
      },
      { path: '/login', element: <ServiceLoginPage />, handle: { mode: 'service' } },
      {
        path: '/app', handle: { mode: 'service' },
        children: [
          { index: true, element: <ServicePage stage={serviceStages[0]} />, loader: args => loadServiceRoute(serviceApi, 'projects', args), HydrateFallback: ServiceLoading },
          { path: 'live', element: <LivePage /> },
          ...serviceStages.map(stage => ({
            path: stage.path,
            element: stage.key === 'new' ? <ServiceProjectCreatePage />
              : stage.key === 'projects' ? <ServicePage stage={stage} /> : <ServiceWorkflowEntry />,
            ...(stage.key === 'new' || stage.key === 'projects'
              ? { loader: args => loadServiceRoute(serviceApi, stage.resource, args), HydrateFallback: ServiceLoading }
              : {}),
          })),
          { path: '*', element: <ServicePage stage={{ key: 'unknown', title: '요청한 화면을 찾을 수 없어요' }} />, loader: args => loadServiceRoute(serviceApi, null, args), HydrateFallback: ServiceLoading },
        ],
      },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  }]
}
