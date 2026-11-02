import styles from './AnalysisReviewDetails.module.css'

function displayValue(value) {
  if (Array.isArray(value)) return value.length ? value.join(' · ') : '명시적으로 없음'
  return typeof value === 'string' && value.trim() ? value : '확인되지 않음'
}

function location(evidence) {
  if (!evidence || typeof evidence !== 'object') return null
  const page = Number.isInteger(evidence.page) && evidence.page > 0 ? `${evidence.page}쪽` : null
  const offset = Number.isInteger(evidence.start) && Number.isInteger(evidence.end)
    ? `문자 ${evidence.start}–${evidence.end}` : null
  return [page, offset].filter(Boolean).join(' · ') || null
}

export default function AnalysisReviewDetails({ fields, onApply }) {
  if (!fields.length) return <p>분석 세부 정보가 없어 편집 가능한 초안만 표시합니다.</p>
  const pending = fields.filter(field => field.state !== 'suggested')
  return <section className={styles.section} aria-label="AI 분석 근거와 미확정 항목">
    <h3>AI 분석 근거와 확인할 항목</h3>
    <p>문서에서 확인한 값과 미정·검토 필요 항목을 구분합니다. 후보를 반영한 뒤 아래 기획 항목을 확인하고 확정 저장해 주세요.</p>
    <p className={styles.count}>{fields.length}개 항목 중 {pending.length}개 확인 필요</p>
    <div className={styles.grid}>{fields.map(field => <article className={styles.field} key={field.key}>
      <div className={styles.heading}><strong>{field.label}</strong><span>{field.state === 'suggested' ? '문서에서 확인' : field.candidates.length ? '후보 검토 필요' : '미정'}</span></div>
      <p className={styles.value}>{displayValue(field.value)}</p>
      {field.evidence.length > 0 && <p className={styles.evidence}>문서 근거 위치: {field.evidence.map(location).filter(Boolean).join(', ') || '위치 정보 없음'}</p>}
      {field.questions.map(question => <p className={styles.question} key={question.id}>{question.message}</p>)}
      {field.candidates.map(candidate => <div className={styles.candidate} key={candidate.id}>
        <p>추가 확인할 후보: <strong>{displayValue(candidate.proposedValue)}</strong></p>
        {candidate.evidence?.length > 0 && <p className={styles.evidence}>후보 근거 위치: {candidate.evidence.map(location).filter(Boolean).join(', ') || '위치 정보 없음'}</p>}
        <button type="button" onClick={() => onApply(candidate)}>후보 반영</button>
      </div>)}
    </article>)}</div>
  </section>
}
