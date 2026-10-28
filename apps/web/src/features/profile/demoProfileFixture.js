// Fixed demo fixture. Never derived from Planning or treated as an AI response.
export function createDemoProfileFixture() {
  return {
    project_name: '예시 팀 일정 서비스', project_type: '웹 서비스', domain: '협업',
    database: null, deployment: null,
    features: ['일정 등록', '팀 일정 조회'], frontend: ['React'], backend: null,
    ai: [], external_integrations: null,
  }
}
