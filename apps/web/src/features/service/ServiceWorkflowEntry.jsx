import { Navigate, useLocation, useParams } from 'react-router'
import { serviceWorkflowPath } from './serviceWorkflowPath.js'

export default function ServiceWorkflowEntry() {
  const { projectId } = useParams()
  const { pathname } = useLocation()
  const stage = pathname.endsWith(`/${projectId}`) ? 'planning' : pathname.split('/').at(-1)
  return <Navigate to={serviceWorkflowPath(projectId, stage)} replace />
}
