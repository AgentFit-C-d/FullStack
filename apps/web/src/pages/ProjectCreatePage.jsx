import { useRef, useState } from 'react'
import { useNavigate } from 'react-router'
import { useDemoProjects } from '../features/projects/DemoContext.js'
import { limitProjectName, validateProjectName } from '../features/projects/projectModel.js'
import WizardProgress from '../features/projects/WizardProgress.jsx'
import PageHeader from '../components/layout/PageHeader.jsx'
import { Button, Card, FormField } from '../components/ui/UI.jsx'
import styles from './ProjectCreatePage.module.css'

export default function ProjectCreatePage() {
  const { state, dispatch } = useDemoProjects()
  const [error, setError] = useState('')
  const [limitNotice, setLimitNotice] = useState('')
  const [composing, setComposing] = useState(false)
  const [compositionValue, setCompositionValue] = useState('')
  const inputRef = useRef(null)
  const navigate = useNavigate()
  function changeName(value) {
    const limited = limitProjectName(value)
    dispatch({ type: 'draft', value: limited })
    setLimitNotice(value !== limited ? '프로젝트 이름은 최대 200자까지 입력할 수 있어요.' : '')
    if (limited.trim()) setError('')
  }
  function submit(event) {
    event.preventDefault()
    if (composing) return
    const name = state.draftName
    const validation = validateProjectName(name)
    if (validation) { setError(validation); inputRef.current?.focus(); return }
    // This opaque ID exists only in the demo session, not in the server API contract.
    const id = `demo-${crypto.randomUUID()}`
    dispatch({ type: 'create', id, name })
    navigate(`/demo/projects/${id}`, { replace: true })
  }
  return <>
    <PageHeader eyebrow="01 / 10 · PROJECT" title="새 프로젝트 만들기" description="먼저 프로젝트 이름을 입력해주세요." />
    <WizardProgress />
    <form noValidate onSubmit={submit}>
      <Card padded className={styles.panel}>
        <FormField id="project-name" label="프로젝트 이름" required error={error}>
          <input ref={inputRef} id="project-name" className={styles.input} value={composing ? compositionValue : state.draftName} required aria-invalid={!!error}
            aria-describedby={[error && 'project-name-error', 'project-name-limit'].filter(Boolean).join(' ')}
            placeholder="프로젝트 이름을 입력해주세요."
            onKeyDown={event => { if (event.key === 'Enter' && (event.nativeEvent.isComposing || event.nativeEvent.keyCode === 229)) event.preventDefault() }}
            onCompositionStart={event => { setComposing(true); setCompositionValue(event.currentTarget.value) }}
            onCompositionEnd={event => { setComposing(false); changeName(event.currentTarget.value) }}
            onChange={event => { if (composing) setCompositionValue(event.target.value); else changeName(event.target.value) }} />
        </FormField>
        <p id="project-name-limit" className={styles.limit} role="status">{limitNotice || '최대 200자 · 시연 모드에서만 생성되며 서버에 저장되지 않습니다.'}</p>
      </Card>
      <div className={styles.actions}><Button to="/demo/projects" onClick={() => dispatch({ type: 'cancel' })}>취소</Button><Button variant="primary" large type="submit">프로젝트 만들기 →</Button></div>
    </form>
  </>
}
