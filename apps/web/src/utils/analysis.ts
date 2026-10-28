const ANALYSIS_STATE_LABELS: Record<string, string> = {
  PROCESSING: '분석 진행 중',
  QUEUED: '분석 대기 중',
  RUNNING: '분석 실행 중',
  SUCCEEDED: '분석 완료',
  FAILED: '분석 실패',
  TIMEOUT: '분석 시간 초과',
};

export function getAnalysisStateLabel(state: string): string {
  return ANALYSIS_STATE_LABELS[state] ?? state;
}

export function formatAnalysisDate(value?: string): string {
  return value ? new Date(value).toLocaleString('ko-KR') : '—';
}
