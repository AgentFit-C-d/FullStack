import { useRef, useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router'
import { useDemoProjects } from '../features/projects/DemoContext.js'
import { getVisibleProjects } from '../features/projects/projectModel.js'
import { canEnterDemoRole, emptyRoleEntry, experienceOptions, roleOptions, validateRole } from '../features/role/roleModel.js'
import PageHeader from '../components/layout/PageHeader.jsx'
import WizardProgress from '../features/projects/WizardProgress.jsx'
import { Button, Card, FormField } from '../components/ui/UI.jsx'
import styles from './RolePage.module.css'

export default function RolePage() {
  const { demoId } = useParams()
  const navigate = useNavigate()
  const { state, dispatch } = useDemoProjects()
  const project = getVisibleProjects(state).find(item => item.id === demoId)
  if (!project) return <Card padded><h1>프로젝트를 찾을 수 없어요</h1><p>현재 시연 세션의 목록에서 프로젝트를 다시 선택해주세요.</p><Button to="/demo/projects">내 프로젝트로</Button></Card>
  const path = `/demo/projects/${encodeURIComponent(project.id)}`
  if (!canEnterDemoRole(state.demoProfiles[project.id])) return <Navigate to={`${path}/review`} replace />
  return <RoleEditor key={project.id} mode="demo" entry={state.roleDrafts[project.id] || emptyRoleEntry} reviewPath={`${path}/review`} onEdit={(field, value) => dispatch({ type: 'role-edit', id: project.id, field, value })} onConfirm={() => { dispatch({ type: 'role-confirm', id: project.id }); navigate(`${path}/environment`) }} />
}

export function RoleEditor({ mode, entry, reviewPath, onEdit, onConfirm }) {
  const [submitted, setSubmitted] = useState(false)
  const [notice, setNotice] = useState('')
  const form = useRef(null)
  const errors = submitted ? validateRole(entry.draft) : {}
  if (mode !== 'demo') return <Card padded><h1>역할 입력을 이용할 수 없어요</h1><p>인증된 세션과 역할 데이터 API 연결이 필요합니다.</p></Card>
  if (entry.source !== 'demo-user') return <Card padded><h1>역할 정보를 불러올 수 없어요</h1></Card>
  function change(field, value) { onEdit(field, value); setNotice('') }
  function submit(event) {
    event.preventDefault()
    setSubmitted(true)
    const invalid = Object.keys(validateRole(entry.draft))[0]
    if (invalid) { setNotice(''); form.current?.querySelector(`[data-field="${invalid}"]`)?.focus(); return }
    onConfirm()

  }
  return <>
    <PageHeader eyebrow="05 / 10 · DEVELOPER PROFILE" title="내 역할과 담당 업무" description="역할 하나와 이번 프로젝트에서 맡을 업무를 입력해주세요." />
    <WizardProgress currentStep={5} navigationOnly />
    <div className={styles.help}>개발 기준 MVP 역할은 다섯 가지이며 한 번에 하나만 선택합니다.</div>
    <form ref={form} noValidate onSubmit={submit}>
      <Card padded className={styles.panel}>
        <div className={styles.field}>
          <div className={styles.label}>역할 <span className={styles.optional}>선택</span></div>
          <div className={styles.options} role="group" aria-label="역할 선택">{roleOptions.map(role => <button key={role} data-field="role" type="button" className={`${styles.pill} ${entry.draft.role === role ? styles.selected : ''}`} aria-pressed={entry.draft.role === role} onClick={() => change('role', role)}>{role}</button>)}</div>
        </div>
        <FormField id="responsibilities" label={<>담당 업무 <span className={styles.optional}>선택</span> <span className={styles.multiple}>여러 개 입력 가능</span></>} error={errors.responsibilitiesText} hint="최대 30개 업무, 항목당 200 코드포인트. 줄바꿈·공백·구분자 포함 전체 6,000 코드포인트입니다. 빈칸은 미입력으로 남습니다.">
          <textarea id="responsibilities" data-field="responsibilitiesText" className={styles.textarea} value={entry.draft.responsibilitiesText} aria-invalid={!!errors.responsibilitiesText} aria-describedby={`responsibilities-hint${errors.responsibilitiesText ? ' responsibilities-error' : ''}`} placeholder={'한 줄에 담당 업무 하나씩 입력해주세요. 예: 로그인 화면 구현\n브라우저에서 주요 흐름 확인'} onChange={event => change('responsibilitiesText', event.target.value)} />
        </FormField>
        <div className={styles.field}>
          <div className={styles.label}>경험 수준 <span className={styles.optional}>선택</span></div>
          <div className={styles.options} role="group" aria-label="경험 수준">{experienceOptions.map(experience => <button key={experience} data-field="experience" type="button" className={`${styles.pill} ${entry.draft.experience === experience ? styles.selected : ''}`} aria-pressed={entry.draft.experience === experience} onClick={() => change('experience', experience)}>{experience}</button>)}</div>
          <p className={styles.hint}>선택하지 않아도 다음 단계로 이동할 수 있습니다.</p>
        </div>
      </Card>
      <p className={styles.demo}>시연 모드 · 새로고침하거나 시연을 종료하면 역할 입력값이 초기화됩니다. 실제 분석·추천 결과가 아닙니다.</p>
      <p className={styles.notice} role="status">{notice}</p>
      <div className={styles.actions}><Button to={reviewPath}>← 분석 내용 확인</Button><Button type="submit" variant="primary" large>다음: 개발환경 입력 →</Button></div>
    </form>
  </>
}
