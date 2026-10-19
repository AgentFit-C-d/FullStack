import { Navigate } from 'react-router'
import App, { DemoRoutes } from './App.jsx'
import AppLayout from '../components/layout/AppLayout.jsx'
import LoginPage, { ServiceLoginPage } from '../pages/LoginPage.jsx'
import ProjectsPage from '../pages/ProjectsPage.jsx'
import ProjectCreatePage from '../pages/ProjectCreatePage.jsx'
import PlanningPage from '../pages/PlanningPage.jsx'
import AnalysisPage from '../pages/AnalysisPage.jsx'
import ProfileReviewPage from '../pages/ProfileReviewPage.jsx'
import RolePage from '../pages/RolePage.jsx'
import EnvironmentPage from '../pages/EnvironmentPage.jsx'
import RecommendationPage from '../pages/RecommendationPage.jsx'
import CompletePage from '../pages/CompletePage.jsx'
import PreviewPage from '../pages/PreviewPage.jsx'
import SelectionPage from '../pages/SelectionPage.jsx'

import ServicePage, { ServiceLoading } from '../pages/ServicePage.jsx'
import { serviceStages } from '../features/service/serviceStages.js'
import { serviceApi as defaultServiceApi } from '../features/service/serviceApi.js'
import { loadServiceRoute } from '../features/service/serviceModel.js'
import LivePage from '../features/live/LivePage.jsx'
import ServiceProjectCreatePage from '../pages/ServiceProjectCreatePage.jsx'
import ServiceWorkflowEntry from '../features/service/ServiceWorkflowEntry.jsx'
import ServiceEntryPage from '../features/service/ServiceEntryPage.jsx'

export function createAppRoutes({ serviceApi = defaultServiceApi } = {}) {
  return [{
    element: <App />,
    children: [
      {
        element: <DemoRoutes />,
        handle: { mode: 'demo' },
        children: [
          { path: '/demo/login', element: <LoginPage /> },
          {
            path: '/demo', element: <AppLayout />,
            children: [
              { index: true, element: <Navigate to="/demo/projects" replace /> },
              { path: 'projects', element: <ProjectsPage /> },
              { path: 'projects/new', element: <ProjectCreatePage /> },
              { path: 'projects/:demoId', element: <PlanningPage /> },
              { path: 'projects/:demoId/analysis', element: <AnalysisPage /> },
              { path: 'projects/:demoId/review', element: <ProfileReviewPage /> },
              { path: 'projects/:demoId/role', element: <RolePage /> },
              { path: 'projects/:demoId/environment', element: <EnvironmentPage /> },
              { path: 'projects/:demoId/recommendation', element: <RecommendationPage /> },
              { path: 'projects/:demoId/selection', element: <SelectionPage /> },
              { path: 'projects/:demoId/preview', element: <PreviewPage /> },
              { path: 'projects/:demoId/complete', element: <CompletePage /> },
            ],
          },
        ],
      },
      { path: '/', element: <ServiceEntryPage />, handle: { mode: 'service' } },
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
