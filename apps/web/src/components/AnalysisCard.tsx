import type { AnalysisAttempt } from '../api/types';
import { formatAnalysisDate, getAnalysisStateLabel } from '../utils/analysis';

interface AnalysisCardProps {
  attempt: AnalysisAttempt | null;
  busy: boolean;
  canStart: boolean;
  onStart: () => void;
}

export function AnalysisCard({ attempt, busy, canStart, onStart }: AnalysisCardProps) {
  return (
    <article className="card analysis">
      <div className="card-title">
        <span className="step">03</span>
        <h2>AI 분석</h2>
      </div>
      <p className="muted">업로드된 현재 기획서를 기준으로 분석을 시작합니다.</p>
      <button className="primary full" disabled={busy || !canStart} onClick={onStart}>
        AI 분석 시작
      </button>
      {attempt && (
        <div className={`status ${attempt.state.toLowerCase()}`}>
          <span className="status-dot" />
          <div>
            <strong>{getAnalysisStateLabel(attempt.state)}</strong>
            <p>시도 #{attempt.id} · 시작 {formatAnalysisDate(attempt.startedAt)}</p>
            {attempt.errorCode && <p className="error">오류 코드: {attempt.errorCode}</p>}
          </div>
        </div>
      )}
    </article>
  );
}
