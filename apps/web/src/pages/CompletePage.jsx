import { useRef, useState } from 'react'
import { Navigate, useParams } from 'react-router'
import { useDemoProjects } from '../features/projects/DemoContext.js'
import { getVisibleProjects } from '../features/projects/projectModel.js'
import { canEnterDemoComplete, failureReasons, reportStatusOptions, unpreparedCompleteData, validateCompleteReport } from '../features/complete/completeModel.js'
import { environmentClient } from '../features/environment/clientDefinition.js'
import PageHeader from '../components/layout/PageHeader.jsx'
import WizardProgress from '../features/projects/WizardProgress.jsx'
import { Badge, Button, Card, FormField } from '../components/ui/UI.jsx'
import styles from './CompletePage.module.css'

export default function CompletePage() {
  const { demoId } = useParams()
  const { state, dispatch } = useDemoProjects()
  const project = getVisibleProjects(state).find(item => item.id === demoId)
  if (!project) return <Card padded><h1>프로젝트를 찾을 수 없어요</h1><p>현재 시연 세션의 목록에서 프로젝트를 다시 선택해주세요.</p><Button to="/demo/projects">내 프로젝트로</Button></Card>
  const path = `/demo/projects/${encodeURIComponent(project.id)}`
  if (!canEnterDemoComplete(state, project.id)) return <Navigate to={`${path}/preview`} replace />
  return <CompleteScreen key={project.id} mode="demo" entry={state.completeDrafts[project.id]} previewPath={`${path}/preview`} onEdit={(field, value) => dispatch({ type: 'complete-edit', id: project.id, field, value })} onReport={() => dispatch({ type: 'complete-report', id: project.id })} />
}

export function CompleteScreen({ mode, entry, previewPath, onEdit, onReport }) {
  const [submitted, setSubmitted] = useState(false)
  const [notice, setNotice] = useState('')
  const form = useRef(null)
  if (mode !== 'demo') return <Card padded><h1>다운로드·적용 안내를 이용할 수 없어요</h1><p>인증된 세션과 서버의 승인·설정 산출물·다운로드 API 연결이 필요합니다.</p></Card>
  if (entry?.source !== 'demo-ui' || !entry.entered) return <Card padded><h1>시연 확인 정보를 불러올 수 없어요</h1><p>Preview 예시를 열고 화면 확인 체크를 완료해주세요.</p></Card>
  const errors = submitted ? validateCompleteReport(entry.draft) : {}
  const report = entry.report?.source === 'demo-user-report' ? entry.report : null
  function edit(field, value) { onEdit(field, value); setNotice('') }
  function submit(event) {
    event.preventDefault()
    setSubmitted(true)
    const invalid = Object.keys(validateCompleteReport(entry.draft))[0]
    if (invalid) {
      setNotice('')
      form.current?.querySelector(invalid === 'status' ? '#applyResult' : '#failureReason')?.focus()
      return
    }
    onReport()
    setNotice('시연 사용자 진술을 현재 프로젝트의 메모리에만 표시했어요. 실제 적용·시스템 검증·서버 저장은 수행하지 않았습니다.')
  }
  return <>
    <PageHeader eyebrow="10 / 10 · EXPORT & APPLY" title="다운로드·적용 안내" description="실제 서비스에서 설정 결과를 받은 뒤 사용자가 직접 적용하고 동작을 확인합니다." />
    <WizardProgress currentStep={10} navigationOnly />
    <div className={styles.grid}>
      <Card padded><span className={styles.warning}><Badge>다운로드 연동 전</Badge></span><h3 className={styles.downloadTitle}>설정 결과 다운로드</h3><p>현재 시안은 설정 파일이나 ZIP을 생성하지 않습니다.</p>
        <div className={styles.divider} /><ol className={styles.steps}>{environmentClient.applySteps.map(step => <li key={step}>{step}</li>)}</ol>
        <Button variant="primary" className={styles.download} aria-describedby="download-help" onClick={() => setNotice(unpreparedCompleteData.artifact === null ? '다운로드 연동 전이라 파일은 생성되거나 저장되지 않았습니다. 내려받을 실제 파일이 없습니다.' : '설정 산출물을 확인할 수 없습니다.')}>설정 결과 다운로드 (연동 전)</Button>
        <p id="download-help" className={styles.note}>버튼은 화면 동작만 보여주며 파일을 내려받지 않습니다.</p><p className={styles.note}>{environmentClient.applyHint}</p>
      </Card>
      <Card padded><h3>사용자 적용 결과 보고</h3><p>결과는 사용자 진술로만 기록합니다. 시스템 검증은 수행하지 않습니다.</p><p className={styles.note}>시연용 입력입니다. 실제 설치·적용 결과를 확인하거나 서버에 보고하지 않습니다.</p>
        <form ref={form} noValidate onSubmit={submit}>
          <FormField id="applyResult" label="적용 상태" required error={errors.status}><select id="applyResult" required className={styles.select} value={entry.draft.status} aria-invalid={!!errors.status} aria-describedby={errors.status ? 'applyResult-error' : undefined} onChange={event => edit('status', event.target.value)}>{reportStatusOptions.map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></FormField>
          {entry.draft.status === 'FAILED' && <FormField id="failureReason" label="실패 사유" required error={errors.reason}><select id="failureReason" required className={styles.select} value={entry.draft.reason} aria-invalid={!!errors.reason} aria-describedby={errors.reason ? 'failureReason-error' : undefined} onChange={event => edit('reason', event.target.value)}><option value="">선택해주세요</option>{failureReasons.map(reason => <option key={reason} value={reason}>{reason}</option>)}</select></FormField>}
          <Button type="submit" variant="primary">사용자 보고 저장</Button>
        </form>
        <div className={styles.alert}><span aria-hidden="true">ⓘ</span><span>보고 내용은 사용자의 진술이며 시스템 검증 결과가 아닙니다. 현재 시안에서는 브라우저 메모리에만 표시하고 서버에 저장하지 않습니다.</span></div>
        {report ? <div className={styles.record}><strong>{report.status === 'APPLIED' ? '적용 완료 (시연 사용자 보고)' : '적용 실패 (시연 사용자 보고)'}</strong>{report.reason && <p>사유: {report.reason}</p>}{report.selectionSignature !== entry.selectionSignature && <p>이전 시연 선택에 대한 보고입니다. 현재 선택의 결과가 아닙니다.</p>}<p>시스템 검증: {report.verification} · 세션 내 표시</p></div> : <p className={styles.note}>미보고 · 아직 시연 보고 기록이 없습니다.</p>}
      </Card>
    </div>
    <p className={styles.note}>시연 모드 · 10/10은 화면 진행 상태이며 실제 승인·다운로드·적용 완료가 아닙니다. 새로고침하거나 시연을 종료하면 입력과 보고가 초기화됩니다.</p>
    <p className={styles.note} role="status">{notice}</p>
    <div className={styles.actions}><Button to={previewPath}>← 설정 확인·승인</Button><Button to="/demo/projects" variant="primary">내 프로젝트로</Button></div>
  </>
}
