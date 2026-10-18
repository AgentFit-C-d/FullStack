// Temporary frontend paths. Server endpoint names and authorization do not derive from these paths.
export const serviceStages = [
  { key: 'projects', path: 'projects', title: '내 프로젝트', resource: 'projects' },
  { key: 'new', path: 'projects/new', title: '새 프로젝트 만들기', step: 1, resource: null },
  { key: 'planning', path: 'projects/:projectId', title: '기획 자료 입력', step: 2, resource: 'project' },
  { key: 'analysis', path: 'projects/:projectId/analysis', title: 'AI 기획 분석', step: 3, resource: 'project' },
  { key: 'review', path: 'projects/:projectId/review', title: '분석 내용 확인', step: 4, resource: 'project' },
  { key: 'role', path: 'projects/:projectId/role', title: '내 역할 입력', step: 5, resource: 'role' },
  { key: 'environment', path: 'projects/:projectId/environment', title: '개발환경 입력', step: 6, resource: 'environment' },
  { key: 'recommendation', path: 'projects/:projectId/recommendation', title: 'Capability·추천', step: 7, resource: 'recommendation' },
  { key: 'selection', path: 'projects/:projectId/selection', title: '도구·권한 선택', step: 8, resource: 'selection' },
  { key: 'preview', path: 'projects/:projectId/preview', title: '설정 확인·승인', step: 9, resource: 'preview' },
  { key: 'complete', path: 'projects/:projectId/complete', title: '다운로드·적용 안내', step: 10, resource: 'download' },
]
