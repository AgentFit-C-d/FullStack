const stageAnchors = {
  planning: 'planning-input',
  analysis: 'planning-input',
  review: 'profile-review',
  role: 'recommendation-inputs',
  environment: 'recommendation-inputs',
  recommendation: 'recommendation-result',
  selection: 'recommendation-result',
  preview: 'configuration-output',
  complete: 'configuration-output',
}

export function serviceWorkflowPath(projectId, stage) {
  const anchor = stageAnchors[stage]
  return `/app/live?project=${encodeURIComponent(projectId)}${anchor ? `#${anchor}` : ''}`
}
