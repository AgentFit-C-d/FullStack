import styles from './LivePage.module.css'

export default function QuestionAnswersEditor({ notices, answers, drafts, busy, onDraft, onSave }) {
  if (!notices.length) return null
  return <details>
    <summary>질문·안내 ({notices.length})</summary>
    <p>답변은 질문 기록으로 저장됩니다. 확정 기획·환경이나 추천 판정에는 자동 반영되지 않습니다.</p>
    {notices.map(notice => <div key={notice.id || notice.code} className={styles.existingFile}>
      <strong>{notice.type === 'QUESTION' ? '추가 질문' : '판정 사유'} · {notice.message}</strong>
      {notice.type === 'QUESTION' && <>
        <p>답변 대상: {notice.answerTarget}</p>
        <textarea aria-label={`${notice.code} 답변`} value={drafts[notice.id] || ''}
          onChange={event => onDraft(notice.id, event.target.value)} />
        <button type="button" disabled={busy || !drafts[notice.id]?.trim()} onClick={() => onSave(notice.id)}>답변 저장</button>
        {answers.some(item => item.noticeId === notice.id) && <p>저장된 답변입니다.</p>}
      </>}
    </div>)}
  </details>
}
