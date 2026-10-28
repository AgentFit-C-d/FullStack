// UI policy only. This is not a Catalog entry or a server/client configuration schema.
export const environmentClient = Object.freeze({
  id: 'CLAUDE_CODE', name: 'Claude Code',
  versionLabel: 'Claude Code 버전 (알고 있다면)',
  configurationLabel: 'Claude Code 설정 상태',
  applySteps: ['다운로드한 파일 내용을 확인', '사용자가 직접 Claude Code에 적용', '필요한 인증을 진행', '도구 동작을 직접 확인'],
  applyHint: '실제 파일 종류·경로·설정 범위·병합 방식은 미확정입니다. 현재는 Claude Code 적용·인증·동작 확인을 수행하지 않습니다.',
  supportHint: 'Claude Code 우선 지원 · Codex는 후속 지원 예정입니다.',
  configurationHint: '알고 있는 Claude Code 설정의 존재 여부만 입력해주세요. 사용자·프로젝트·관리 설정의 범위와 실제 적용 여부는 확인하지 않습니다.',
})
