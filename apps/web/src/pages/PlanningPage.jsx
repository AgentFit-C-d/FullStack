import { useRef, useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { getPlanningDraft, getVisibleProjects } from '../features/projects/projectModel.js'
import { useDemoProjects } from '../features/projects/DemoContext.js'
import { limitPlanningText, validatePlanningFiles, validatePlanningDraft } from '../features/planning/planningModel.js'
import WizardProgress from '../features/projects/WizardProgress.jsx'
import PageHeader from '../components/layout/PageHeader.jsx'
import { Badge, Button, Card, FormField } from '../components/ui/UI.jsx'
import styles from './PlanningPage.module.css'

export default function PlanningPage() {
  const { demoId } = useParams()
  const { state } = useDemoProjects()
  const project = getVisibleProjects(state).find(item => item.id === demoId)
  if (!project) return <Card padded><h1>프로젝트를 찾을 수 없어요</h1><p>현재 시연 세션의 목록에서 프로젝트를 다시 선택해주세요.</p><Button to="/demo/projects">내 프로젝트로</Button></Card>
  return <PlanningForm key={project.id} project={project} />
}

function PlanningForm({ project }) {
  const navigate = useNavigate()
  const { state, dispatch } = useDemoProjects()
  const draft = getPlanningDraft(state, project.id)
  const [errors, setErrors] = useState({ file: '', text: '' })
  const [notice, setNotice] = useState('')
  const [dragging, setDragging] = useState(false)
  const [composing, setComposing] = useState(false)
  const [compositionValue, setCompositionValue] = useState('')
  const fileInput = useRef(null)
  const fileButton = useRef(null)
  const textInput = useRef(null)
  const text = composing ? compositionValue : draft.text
  const count = Array.from(text).length

  function selectFiles(files) {
    const error = validatePlanningFiles(files)
    if (error) {
      setErrors(previous => ({ ...previous, file: error + (draft.file ? ' 기존에 선택한 유효 파일은 유지됩니다.' : '') }))
      setNotice('')
      return
    }
    dispatch({ type: 'planning-file', id: project.id, file: files[0] })
    setErrors(previous => ({ ...previous, file: '' }))
    setNotice(`${files[0].name} 파일을 선택했어요. 실제 업로드·내용 검증은 수행하지 않았습니다.`)
  }
  function changeText(value) {
    const limited = limitPlanningText(value)
    dispatch({ type: 'planning-text', id: project.id, text: limited })
    if (limited.trim()) setErrors(previous => ({ ...previous, text: '' }))
    setNotice(value !== limited ? '직접 입력은 최대 100,000 코드포인트까지 가능합니다.' : '')
  }
  function submit(event) {
    event.preventDefault()
    if (composing) return
    const error = validatePlanningDraft(draft)
    setErrors(previous => ({ ...previous, [draft.tab]: error }))
    if (error) {
      setNotice('')
      if (draft.tab === 'file') fileButton.current?.focus()
      else textInput.current?.focus()
      return
    }
    navigate(`/demo/projects/${encodeURIComponent(project.id)}/analysis`)
  }
  return <>
    <PageHeader eyebrow="02 / 10 · PLANNING MATERIAL" title="기획 자료 입력" description="한 번에 한 가지 방식으로 기획 자료를 제공해주세요." />
    <p className="sr-only">선택한 프로젝트: {project.name}</p>
    <WizardProgress currentStep={2} />
    <div className={styles.help}><b>파일 업로드 또는 직접 입력 중 하나를 선택합니다.</b><br />파일 최대 10 MiB · PDF 최대 100페이지 · 직접 입력 최대 100,000자. 실제 파일 전송이나 AI 분석은 연결 전이에요.</div>
    <form noValidate onSubmit={submit} aria-label={`${project.name} 기획 자료 입력`}>
      <Card padded className={styles.panel}>
        <div className={styles.tabs} role="group" aria-label="기획 자료 입력 방식">
          {[['file', '파일 업로드'], ['text', '직접 입력']].map(([tab, label]) => <button key={tab} type="button" className={`${styles.tab} ${draft.tab === tab ? styles.active : ''}`} aria-pressed={draft.tab === tab} aria-controls="planning-input-panel" onClick={() => { dispatch({ type: 'planning-tab', id: project.id, tab }); setNotice('') }}>{label}</button>)}
        </div>
        <div id="planning-input-panel">
          {draft.tab === 'file' ? <>
            <button ref={fileButton} type="button" className={`${styles.drop} ${dragging ? styles.dragging : ''}`} aria-label={draft.file ? `선택한 기획 문서: ${draft.file.name} · 파일 바꾸기` : '기획 문서 선택하기'} aria-invalid={!!errors.file} aria-describedby={`planning-file-hint${errors.file ? ' planning-file-error' : ''}`} onClick={() => fileInput.current?.click()}
              onDragOver={event => { event.preventDefault(); setDragging(true) }} onDragLeave={() => setDragging(false)} onDrop={event => { event.preventDefault(); setDragging(false); selectFiles(Array.from(event.dataTransfer.files)) }}>
              <span><span className={styles.uploadIcon} aria-hidden="true">⇧</span><strong>{draft.file ? draft.file.name : '기획 문서 선택하기'}</strong><span className={styles.dropDescription}>PDF 또는 Markdown 파일 한 개를 선택해주세요. <Badge variant="required">필수</Badge></span>{draft.file && <span className={styles.dropDescription}>{(draft.file.size / 1024 / 1024).toFixed(2)} MiB</span>}</span>
            </button>
            <input ref={fileInput} type="file" hidden accept=".pdf,.md,.markdown,application/pdf,text/markdown" aria-label="기획 자료 파일" onChange={event => { const files = Array.from(event.target.files || []); if (files.length) selectFiles(files); event.target.value = '' }} />
            {errors.file && <p id="planning-file-error" className={styles.error} role="alert">{errors.file}</p>}
            <p id="planning-file-hint" className={styles.fileHint}>파일당 최대 10 MiB, PDF 최대 100페이지. 확장자와 크기만 확인하며, PDF 페이지 수·파일 내용은 검증하지 않습니다. 페이지 제한은 서버 계약 확인이 필요합니다.</p>
          </> : <FormField id="planning-text" label="기획 내용" required error={errors.text}>
            <textarea ref={textInput} id="planning-text" className={styles.textarea} value={text} required aria-invalid={!!errors.text} aria-describedby={`planning-count${errors.text ? ' planning-text-error' : ''}`} placeholder="준비한 기획 내용을 입력해주세요."
              onCompositionStart={event => { setComposing(true); setCompositionValue(event.currentTarget.value) }} onCompositionEnd={event => { setComposing(false); changeText(event.currentTarget.value) }}
              onChange={event => { if (composing) setCompositionValue(event.target.value); else changeText(event.target.value) }} />
            <div id="planning-count" className={styles.count}><span>{count.toLocaleString('ko-KR')}</span> / 100,000자</div>
          </FormField>}
        </div>
        <div className={styles.alert}><span aria-hidden="true">ⓘ</span><span>실제 서비스에서도 비밀번호나 API Key 등 Secret을 기획 자료에 넣지 마세요. 현재 시연은 파일·텍스트를 전송하지 않습니다.</span></div>
      </Card>
      <div className={styles.notice} role="status">{notice}</div>
      <div className={styles.actions}><Button to="/demo/projects">← 이전</Button><Button variant="primary" large type="submit">AI로 기획 분석하기 →</Button></div>
    </form>
  </>
}
