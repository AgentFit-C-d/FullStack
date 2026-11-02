import styles from './LivePage.module.css'

const environmentTargets = new Set([
  'environment.target',
  'environment.installedComponents',
  'environment.installedComponentVersions',
])

function parsedAnswer(value, fallback) {
  try { return JSON.parse(value) } catch { return fallback }
}

function answerFields(notice, draft, onDraft) {
  if (notice.answerTarget === 'environment.target') {
    const values = parsedAnswer(draft, {})
    return <div className={styles.row}>{[
      ['osFamily', 'OS'], ['clientId', 'AI Client'], ['clientVersion', 'Client 버전'],
    ].map(([key, label]) => <label key={key}>{label}<input value={values[key] || ''}
      onChange={event => onDraft(notice.id, JSON.stringify({ ...values, [key]: event.target.value }))} /></label>)}</div>
  }
  if (notice.answerTarget === 'environment.installedComponents') {
    const values = parsedAnswer(draft, [])
    return <label>설치 도구 (한 줄에 이름 또는 이름 @ 버전)<textarea
      value={Array.isArray(values) ? values.map(item => `${item.toolKey}${item.version ? ` @ ${item.version}` : ''}`).join('\n') : ''}
      onChange={event => onDraft(notice.id, JSON.stringify(event.target.value.split('\n')
        .filter(line => line.trim()).map(line => {
          const [toolKey, version] = line.split(/\s+@\s+/, 2)
          return { toolKey: toolKey.trim(), version: version?.trim() || null }
        })))} /></label>
  }
  const values = parsedAnswer(draft, {})
  return <label>설치 도구 버전 (한 줄에 도구명 = 버전)<textarea
    value={Object.entries(values).map(([name, version]) => `${name} = ${version}`).join('\n')}
    onChange={event => onDraft(notice.id, JSON.stringify(Object.fromEntries(event.target.value.split('\n')
      .filter(line => line.trim()).map(line => {
        const [name, version] = line.split(/\s*=\s*/, 2)
        return [name.trim(), version?.trim() || '']
      }))))} /></label>
}

export default function QuestionAnswersEditor({ notices, answers, drafts, busy, current = true, onDraft, onSave }) {
  if (!notices.length) return null
  return <details>
    <summary>질문·안내 ({notices.length})</summary>
    <p>환경 질문 답변을 저장하면 개발환경이 갱신됩니다. 기획 관련 질문은 확정 기획을 수정해 주세요.</p>
    {!current && <p>이전 추천의 질문입니다. 현재 입력으로 추천을 다시 생성한 뒤 답변해 주세요.</p>}
    {notices.map(notice => <div key={notice.id || notice.code} className={styles.existingFile}>
      <strong>{notice.type === 'QUESTION' ? '추가 질문' : '판정 사유'} · {notice.message}</strong>
      {notice.type === 'QUESTION' && <>
        <p>답변 대상: {notice.answerTarget}</p>
        {environmentTargets.has(notice.answerTarget) ? <>
          {answerFields(notice, drafts[notice.id] || '', onDraft)}
          {notice.answerTarget === 'environment.installedComponents' &&
            <button type="button" disabled={!current} onClick={() => onDraft(notice.id, '[]')}>설치 도구 없음</button>}
          <button type="button" disabled={!current || busy || !drafts[notice.id]?.trim()} onClick={() => onSave(notice.id)}>답변 저장</button>
        </> : <p><a href="#profile-review">기획 정보 확인으로 이동</a>해 내용을 수정해 주세요.</p>}
        {answers.some(item => item.noticeId === notice.id) && <p>저장된 답변입니다.</p>}
      </>}
    </div>)}
  </details>
}
