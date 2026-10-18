export function serviceWorkflowPath(projectId) {
  return `/app/live?project=${encodeURIComponent(projectId)}`
}
