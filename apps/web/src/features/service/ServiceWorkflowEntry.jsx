import { Navigate, useParams } from 'react-router'
import { serviceWorkflowPath } from './serviceWorkflowPath.js'

export default function ServiceWorkflowEntry() {
  const { projectId } = useParams()
  return <Navigate to={serviceWorkflowPath(projectId)} replace />
}
