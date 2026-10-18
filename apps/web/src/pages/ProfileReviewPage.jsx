import { useRef, useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router'
import { useDemoProjects } from '../features/projects/DemoContext.js'
import { getVisibleProjects } from '../features/projects/projectModel.js'
import { scalarFields, listFields, validateProfile, getTechnicalFieldLabel, automaticScalarFields } from '../features/profile/profileModel.js'
import WizardProgress from '../features/projects/WizardProgress.jsx'
import PageHeader from '../components/layout/PageHeader.jsx'
import { Badge, Button, Card, FormField } from '../components/ui/UI.jsx'
import styles from './ProfileReviewPage.module.css'
import { getProfilePresentation } from '../features/analysis/analysisPresentation.js'

export default function ProfileReviewPage() {
  const { demoId } = useParams()
  const { state } = useDemoProjects()
  const project = getVisibleProjects(state).find(item => item.id === demoId)
  if (!project) return <Card padded><h1>프로젝트를 찾을 수 없어요</h1><p>현재 시연 세션의 목록에서 프로젝트를 다시 선택해주세요.</p><Button to="/demo/projects">내 프로젝트로</Button></Card>
  const entry = state.demoProfiles[project.id]
  if (!entry || !['demo-fixture', 'demo-manual'].includes(entry.source)) return <Navigate to={`/demo/projects/${encodeURIComponent(project.id)}/analysis`} replace />
  return <ProfileEditor key={project.id} project={project} entry={entry} mode="demo" />
}

export function ProfileEditor({ project, entry, mode }) {
  const { dispatch } = useDemoProjects()
  const navigate = useNavigate()
  const [submitted, setSubmitted] = useState(false)
  const [confirmExample, setConfirmExample] = useState(false)
  const copy = getProfilePresentation(mode)
  const form = useRef(null)
  const errors = submitted ? validateProfile(entry.draft, entry.modes) : {}
  const path = `/demo/projects/${encodeURIComponent(project.id)}`
  function edit(edit, key, values = {}) {
    dispatch({ type: 'profile-edit', id: project.id, edit, key, ...values })
  }
  function confirm(event) {
    event.preventDefault()
    setSubmitted(true)
    const invalid = Object.keys(validateProfile(entry.draft, entry.modes))[0]
    if (invalid) {
      form.current?.querySelector(`#profile-${invalid}`)?.focus()
      return
    }
    if (mode !== 'demo') return
    edit('confirm')
    navigate(`${path}/role`)
  }
  if (mode !== 'demo' && ['demo-fixture', 'demo-manual'].includes(entry.source)) return <Card padded><h1>프로젝트 정보를 불러올 수 없어요</h1><p>서버에서 확인한 프로젝트 정보가 필요합니다.</p></Card>

  function scalar(key) {
    const label = scalarFields.find(([field]) => field === key)[1]
    const value = entry.draft[key]
    return <FormField key={key} id={`profile-${key}`} label={<>{label}{key !== 'project_name' && <span className={styles.optional}>선택</span>} <Badge>{automaticScalarFields.includes(key) ? getTechnicalFieldLabel(entry, key) : value === null ? '확인되지 않음' : key === 'project_name' && entry.nameOrigin === 'project' ? '프로젝트 생성 시 입력' : copy.valueLabel}</Badge></>} required={key === 'project_name'} error={errors[key]}>
      <input id={`profile-${key}`} className={styles.input} value={value ?? ''} required={key === 'project_name'} placeholder="확인되지 않음" aria-invalid={!!errors[key]} aria-describedby={errors[key] ? `profile-${key}-error` : undefined} onChange={event => edit('scalar', key, { value: event.target.value })} />
    </FormField>
  }
  function list(key) {
    const label = listFields.find(([field]) => field === key)[1]
    const mode = entry.modes[key]
    const rows = entry.draft[key] ?? []
    const add = mode === 'KNOWN' && <Button variant="soft" disabled={rows.length >= 30} onClick={() => edit('add', key)}>＋ 항목 추가</Button>
    return <div key={key} className={styles.field}>
      <div className={styles.head}><label htmlFor={`profile-${key}`}>{label} <span className={styles.optional}>선택</span> <Badge>{mode === 'UNKNOWN' ? '미정' : mode === 'KNOWN_EMPTY' ? '없음' : copy.valueLabel}</Badge></label>{key === 'features' && add}</div>
      <select id={`profile-${key}`} className={styles.input} aria-label={`${label} 정보 상태`} value={mode} aria-invalid={!!errors[key]} aria-describedby={errors[key] ? `profile-${key}-error` : undefined} onChange={event => edit('mode', key, { value: event.target.value })}>
        <option value="UNKNOWN">확인되지 않음 · null</option><option value="KNOWN_EMPTY">명시적으로 없음 · []</option><option value="KNOWN">값 입력</option>
      </select>
      {mode === 'KNOWN' ? <>
        {rows.map((value, index) => <div className={styles.row} key={index}>
          <label className="sr-only" htmlFor={`list-${key}-${index}`}>{label} {index + 1}</label>
          <input id={`list-${key}-${index}`} className={styles.input} value={value} placeholder="항목을 입력해주세요." aria-invalid={!!errors[key]} aria-describedby={errors[key] ? `profile-${key}-error` : undefined} onChange={event => edit('item', key, { index, value: event.target.value })} />
          <Button aria-label={`${label} ${index + 1} 삭제`} onClick={() => edit('remove', key, { index })}>삭제</Button>
        </div>)}
        {!rows.length && <p>입력된 항목이 없습니다. ‘항목 추가’를 눌러 입력할 수 있어요.</p>}
        {key !== 'features' && add}
        <p className={styles.hint}>최대 30개, 항목당 200 코드포인트입니다. 항목 추가로 나누어 입력해주세요.</p>
      </> : <p>{mode === 'UNKNOWN' ? '확인되지 않았습니다. 임의의 값을 채우지 않습니다.' : '명시적으로 항목이 없습니다.'}</p>}
      {errors[key] && <p className={styles.error} id={`profile-${key}-error`} role="alert">{errors[key]}</p>}
    </div>
  }
  return <>
    <PageHeader eyebrow="04 / 10 · PROJECT PROFILE" title={copy.title} description={copy.description} />
    <WizardProgress currentStep={4} incompleteSteps={mode === 'demo' ? [] : [3]} navigationOnly={mode === 'demo'} />
    <div className={styles.alert}><Badge>{mode === 'demo' ? '시연 Profile' : 'Project Profile'} · {entry.confirmed ? '확인 완료' : '확인 전'}</Badge><span>프로젝트 목록 이름: <strong>{project.name}</strong>. 프로젝트명은 최대 100 코드포인트이며, 유효한 전체 입력을 확인하면 목록 이름에도 반영됩니다. 프로젝트명만 필수입니다.{mode === 'demo' && ' 변경 내용은 이 시연 세션의 메모리에만 유지되며 AI 분석 결과가 아닙니다.'}</span></div>
    {mode === 'demo' && <div className={styles.examples}>
      <Button variant="soft" onClick={() => setConfirmExample(true)}>예시 데이터 불러오기</Button>
      {confirmExample && <div role="group" aria-label="예시 데이터 교체 확인"><p>프로젝트명을 제외한 기존 Profile 편집 내용과 확인한 값을 고정 예시로 교체할까요? Planning 입력과 프로젝트명은 유지됩니다.</p><Button onClick={() => { dispatch({ type: 'profile-load-example', id: project.id, confirmed: true }); setConfirmExample(false); setSubmitted(false) }}>예시로 교체하기</Button> <Button onClick={() => setConfirmExample(false)}>취소</Button></div>}
    </div>}
    <form ref={form} noValidate onSubmit={confirm}>
      <div className={styles.grid}>
        <Card padded><h3>프로젝트 기본 정보</h3><div className={styles.divider} />{scalar('project_name')}{scalar('project_type')}{scalar('domain')}</Card>
        <Card padded><h3>주요 기능</h3><div className={styles.divider} />{list('features')}</Card>
        <Card padded><h3>기술 구성</h3><div className={styles.divider} />{scalar('database')}{scalar('deployment')}{list('frontend')}{list('backend')}{list('ai')}</Card>
        <Card padded><h3>외부 연동</h3><div className={styles.divider} />{list('external_integrations')}</Card>
      </div>
      <p className={styles.notice} role="status">{mode === 'demo' ? (entry.confirmed ? '시연 Profile의 유효 입력을 메모리에 확인했어요. 서버 저장·실제 분석 완료가 아니며, 다음은 내 역할 입력 단계입니다.' : '시연 Profile을 확인해도 실제 AI 분석 상태는 변경되지 않습니다. 유효 입력을 확인하면 내 역할 입력 단계로 이동합니다.') : '입력 내용을 확인해주세요.'}</p>
      <div className={styles.actions}><Button to={`${path}/analysis`}>← 이전 단계</Button><Button type="submit" variant="primary" large>{mode === 'demo' ? '전체 Project Profile 확인하고 다음으로 (시연) →' : copy.confirmLabel}</Button></div>
    </form>
  </>
}
