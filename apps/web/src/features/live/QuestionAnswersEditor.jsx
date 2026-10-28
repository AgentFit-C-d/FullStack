import styles from './LivePage.module.css'

const environmentTargets = new Set([
  'environment.target',
  'environment.installedComponents',
  'environment.installedComponentVersions',
])

export default function QuestionAnswersEditor({ notices, answers, drafts, busy, current = true, onDraft, onSave }) {
  if (!notices.length) return null
  return <details>
    <summary>질문·안내 ({notices.length})</summary>
    <p>답변은 질문 기록으로 저장됩니다. 확정 기획·환경이나 추천 판정에는 자동 반영되지 않습니다.</p>
    {!current && <p>이전 추천의 질문입니다. 현재 입력으로 추천을 다시 생성한 뒤 답변해 주세요.</p>}
    {notices.map(notice => <div key={notice.id || notice.code} className={styles.existingFile}>
      <strong>{notice.type === 'QUESTION' ? '추가 질문' : '판정 사유'} · {notice.message}</strong>
      {notice.type === 'QUESTION' && <>
        <p>답변 대상: {notice.answerTarget}</p>
        {environmentTargets.has(notice.answerTarget) && <p><a href="#recommendation-inputs">역할·개발환경 입력으로 이동</a>해 실제 값을 확인하세요. 환경 정보를 저장한 뒤 추천을 다시 생성해야 결과에 반영됩니다.</p>}
        <textarea aria-label={`${notice.code} 답변`} value={drafts[notice.id] || ''} disabled={!current}
          onChange={event => onDraft(notice.id, event.target.value)} />
        <button type="button" disabled={!current || busy || !drafts[notice.id]?.trim()} onClick={() => onSave(notice.id)}>답변 저장</button>
        {answers.some(item => item.noticeId === notice.id) && <p>저장된 답변입니다.</p>}
      </>}
    </div>)}
  </details>
}
