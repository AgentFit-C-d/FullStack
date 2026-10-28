import { useEffect, useMemo, useState } from 'react';
import type { AnalysisAttempt, AnalysisCandidate, AnalysisResultResponse, Project } from '../api/types';
import type { AnalysisReviewRequest } from '../api/agentfitApi';
import { formatAnalysisDate, getAnalysisStateLabel } from '../utils/analysis';
import './AnalysisResultCard.css';

interface AnalysisResultCardProps {
  attempt: AnalysisAttempt | null;
  project?: Project;
  result?: AnalysisResultResponse | null;
  resultError?: string;
  onReload?: () => void;
  onSaveReview?: (review: AnalysisReviewRequest) => Promise<void>;
}

export function AnalysisResultCard({ attempt, project, result, resultError, onReload, onSaveReview }: AnalysisResultCardProps) {
  return (
    <article className="card result">
      <div className="card-title">
        <span className="step">04</span>
        <h2>분석 확인</h2>
      </div>
      {attempt?.state === 'SUCCEEDED' ? (
        <>
          <div className="result-ready">분석 시도가 성공적으로 완료되었습니다.</div>
          {result ? <AnalysisDetails result={result} onSaveReview={onSaveReview} /> : (
            <div className="result-loading">
              <p>{resultError || '분석 결과를 불러오는 중입니다…'}</p>
              {resultError && <button className="secondary" onClick={onReload}>결과 다시 불러오기</button>}
            </div>
          )}
          <dl>
            <dt>프로젝트</dt>
            <dd>{project?.name}</dd>
            <dt>상태</dt>
            <dd>{getAnalysisStateLabel(attempt.state)}</dd>
            <dt>시작 시각</dt>
            <dd>{formatAnalysisDate(attempt.startedAt)}</dd>
          </dl>
        </>
      ) : (
        <p className="muted">분석이 완료되면 이곳에서 사람이 확인할 수 있는 결과 패널이 열립니다.</p>
      )}
    </article>
  );
}

const FIELD_LABELS: Record<string, string> = {
  project_name: '프로젝트 이름',
  project_type: '프로젝트 유형',
  domain: '분야',
  features: '프로젝트에 필요한 기능',
  frontend: '프론트엔드',
  backend: '백엔드',
  ai: 'AI',
  database: '데이터베이스',
  deployment: '배포',
  external_integrations: '외부 연동',
};

function AnalysisDetails({ result, onSaveReview }: { result: AnalysisResultResponse; onSaveReview?: (review: AnalysisReviewRequest) => Promise<void> }) {
  const [expandedField, setExpandedField] = useState<string | null>(null);
  const [removedIds, setRemovedIds] = useState<string[]>(result.result.humanReview?.removedCandidateIds ?? []);
  const [confirmedIds, setConfirmedIds] = useState<string[]>(result.result.humanReview?.confirmedCandidateIds ?? []);
  const [manualCandidates, setManualCandidates] = useState<AnalysisCandidate[]>(result.result.humanReview?.manualCandidates ?? []);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);
  const [savedMessage, setSavedMessage] = useState('');

  useEffect(() => {
    setRemovedIds(result.result.humanReview?.removedCandidateIds ?? []);
    setConfirmedIds(result.result.humanReview?.confirmedCandidateIds ?? []);
    setManualCandidates(result.result.humanReview?.manualCandidates ?? []);
  }, [result]);

  const visibleCandidates = useMemo(
    () => result.result.candidates.filter((candidate) => !removedIds.includes(candidate.id)).concat(manualCandidates),
    [manualCandidates, removedIds, result.result.candidates],
  );
  const groups = Object.entries(FIELD_LABELS).map(([field, label]) => ({ field, label, candidates: visibleCandidates.filter((candidate) => candidate.field === field) }));
  const knownFields = new Set(Object.keys(FIELD_LABELS));
  const otherCandidates = visibleCandidates.filter((candidate) => !candidate.field || !knownFields.has(candidate.field));
  if (otherCandidates.length > 0) groups.push({ field: 'other', label: '기타 확인 항목', candidates: otherCandidates });
  const unresolvedCount = visibleCandidates.filter((candidate) => candidate.status !== 'suggested' && !confirmedIds.includes(candidate.id)).length;

  function removeCandidate(candidate: AnalysisCandidate) {
    if (candidate.source === 'USER_INPUT') {
      setManualCandidates((current) => current.filter((item) => item.id !== candidate.id));
      return;
    }
    setRemovedIds((current) => current.includes(candidate.id) ? current : [...current, candidate.id]);
    setConfirmedIds((current) => current.filter((id) => id !== candidate.id));
  }

  function addManualCandidate(field: string) {
    const value = drafts[field]?.trim();
    if (!value) return;
    setManualCandidates((current) => [...current, { id: `manual-${field}-${Date.now()}`, version: 1, field, proposedValue: value, status: 'confirmed', source: 'USER_INPUT', evidence: [] }]);
    setDrafts((current) => ({ ...current, [field]: '' }));
  }

  async function saveReview() {
    if (!onSaveReview) return;
    setSaving(true);
    setSavedMessage('');
    try {
      await onSaveReview({
        confirmedCandidateIds: confirmedIds,
        removedCandidateIds: removedIds,
        manualCandidates: manualCandidates.map((candidate) => ({ field: candidate.field ?? 'other', value: String(candidate.proposedValue ?? '') })),
      });
      setSavedMessage('사용자 검토 결과를 저장했습니다.');
    } catch (error) {
      setSavedMessage(error instanceof Error ? error.message : '검토 결과 저장에 실패했습니다.');
    } finally {
      setSaving(false);
    }
  }

  return <div className="analysis-details">
    {groups.map((group) => <section key={group.field}>
      <h3>{group.label}</h3>
      <div className="candidate-chips">
        {group.candidates.map((candidate) => <CandidateChip key={candidate.id} candidate={candidate} confirmed={confirmedIds.includes(candidate.id) || candidate.source === 'USER_INPUT'} onConfirm={() => setConfirmedIds((current) => current.includes(candidate.id) ? current : [...current, candidate.id])} onRemove={() => removeCandidate(candidate)} />)}
        {group.candidates.length === 0 && <span className="empty-field">AI가 확인하지 못했습니다. 직접 입력해 주세요.</span>}
      </div>
      <div className="manual-input-row">
        <input value={drafts[group.field] ?? ''} onChange={(event) => setDrafts((current) => ({ ...current, [group.field]: event.target.value }))} placeholder={`${group.label} 직접 입력`} aria-label={`${group.label} 직접 입력`} />
        <button type="button" className="secondary" onClick={() => addManualCandidate(group.field)}>추가 후 확인</button>
      </div>
      {group.candidates.length > 0 && <>
        <button type="button" className="more-button" aria-expanded={expandedField === group.field} onClick={() => setExpandedField((current) => current === group.field ? null : group.field)}>{expandedField === group.field ? '접기' : '근거 더보기'}</button>
        {expandedField === group.field && <div className="evidence-panel">{group.candidates.map((candidate) => <EvidenceDetails key={candidate.id} candidate={candidate} />)}</div>}
      </>}
    </section>)}
    {unresolvedCount > 0 && <p className="review-notice">사람 확인 필요: {unresolvedCount}개 항목</p>}
    {onSaveReview && <div className="review-actions"><button type="button" className="primary" disabled={saving} onClick={() => void saveReview()}>{saving ? '저장 중…' : '검토 결과 저장'}</button>{savedMessage && <span>{savedMessage}</span>}</div>}
    <p className="result-meta">{result.result.modelMetadata.provider} · {result.result.modelMetadata.model} · {result.result.modelMetadata.latencyMs}ms</p>
  </div>;
}

function CandidateChip({ candidate, confirmed, onConfirm, onRemove }: { candidate: AnalysisCandidate; confirmed: boolean; onConfirm: () => void; onRemove: () => void }) {
  const value = Array.isArray(candidate.proposedValue) ? candidate.proposedValue.join(', ') : candidate.proposedValue ?? '미정';
  return (
    <span className={`candidate-chip ${candidate.status} ${confirmed ? 'user-confirmed' : ''}`}>
      <strong>{value}</strong>
      <small>{confirmed ? '확인 완료' : candidate.status === 'suggested' ? 'AI 근거 있음' : '확인 필요'}</small>
      {!confirmed && <button type="button" onClick={onConfirm}>확인</button>}
      <button type="button" className="chip-remove" aria-label={`${value} 제외`} onClick={onRemove}>×</button>
    </span>
  );
}

function EvidenceDetails({ candidate }: { candidate: AnalysisCandidate }) {
  const value = Array.isArray(candidate.proposedValue) ? candidate.proposedValue.join(', ') : candidate.proposedValue ?? '미정';
  return (
    <div className="evidence-item">
      <strong>{value}</strong>
      {candidate.evidence.length === 0 && <small>{candidate.source === 'USER_INPUT' ? '사용자가 직접 입력한 항목입니다.' : '저장된 근거가 없습니다. 사람 확인이 필요합니다.'}</small>}
      {candidate.evidence.map((evidence, index) => (
        <div key={`${candidate.id}-${index}`}>
          <small>{evidence.page ? `PDF ${evidence.page}페이지` : 'PDF 근거'}</small>
          {evidence.quote && <p>“{evidence.quote}”</p>}
        </div>
      ))}
    </div>
  );
}
