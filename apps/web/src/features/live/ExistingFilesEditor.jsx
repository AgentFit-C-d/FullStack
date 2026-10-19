import styles from './LivePage.module.css'

export default function ExistingFilesEditor({ targets, selected, onChange }) {
  function key(file) { return `${file.targetKey}\u0000${file.relativePath}` }
  function update(file, content) {
    onChange(selected.map(item => key(item) === key(file) ? { ...item, content } : item))
  }
  function toggle(file, checked) {
    onChange(checked ? [...selected, { ...file, content: '' }]
      : selected.filter(item => key(item) !== key(file)))
  }
  return <div className={styles.existingFiles}>
    <h3>기존 파일과 비교</h3>
    <p>생성 대상과 같은 경로의 기존 텍스트만 직접 붙여 넣으세요. 내용은 비교·다운로드 요청에만 사용하고 DB에는 저장하지 않습니다. 비밀키나 토큰은 입력하지 마세요.</p>
    {targets.map(file => {
      const current = selected.find(item => key(item) === key(file))
      return <div className={styles.existingFile} key={key(file)}>
        <label><input type="checkbox" checked={Boolean(current)} onChange={event => toggle(file, event.target.checked)} />{file.relativePath} ({file.targetKey})</label>
        {current && <textarea aria-label={`${file.relativePath} 기존 내용`} value={current.content} onChange={event => update(file, event.target.value)} placeholder="현재 파일의 UTF-8 텍스트를 붙여 넣으세요. 빈 파일도 비교할 수 있습니다." />}
      </div>
    })}
  </div>
}
