import { useRef, useState } from 'react'
import { Navigate, useParams } from 'react-router'
import { useDemoProjects } from '../features/projects/DemoContext.js'
import { getVisibleProjects } from '../features/projects/projectModel.js'
import { authOptions, canEnterDemoEnvironment, configurationOptions, emptyEnvironmentEntry, osOptions, validateEnvironment } from '../features/environment/environmentModel.js'
import PageHeader from '../components/layout/PageHeader.jsx'
import WizardProgress from '../features/projects/WizardProgress.jsx'
import { Badge, Button, Card, FormField } from '../components/ui/UI.jsx'
import styles from './EnvironmentPage.module.css'

export default function EnvironmentPage() {
  const { demoId } = useParams()
  const { state, dispatch } = useDemoProjects()
  const project = getVisibleProjects(state).find(item => item.id === demoId)
  if (!project) return <Card padded><h1>프로젝트를 찾을 수 없어요</h1><p>현재 시연 세션의 목록에서 프로젝트를 다시 선택해주세요.</p><Button to="/demo/projects">내 프로젝트로</Button></Card>
  const path = `/demo/projects/${encodeURIComponent(project.id)}`
  if (!canEnterDemoEnvironment(state.demoProfiles[project.id], state.roleDrafts[project.id])) return <Navigate to={`${path}/role`} replace />
  return <EnvironmentEditor key={project.id} mode="demo" entry={state.environmentDrafts[project.id] || emptyEnvironmentEntry} rolePath={`${path}/role`} onEdit={action => dispatch({ ...action, type: 'environment-edit', id: project.id })} onConfirm={() => dispatch({ type: 'environment-confirm', id: project.id })} />
}

export function EnvironmentEditor({ mode, entry, rolePath, onEdit, onConfirm }) {
  const [submitted, setSubmitted] = useState(false)
  const [notice, setNotice] = useState('')
  const form = useRef(null)
  const details = useRef(null)
  const draft = entry.draft
  const errors = submitted ? validateEnvironment(draft) : {}
  if (mode !== 'demo') return <Card padded><h1>개발환경 입력을 이용할 수 없어요</h1><p>인증된 세션과 개발환경 데이터 API 연결이 필요합니다.</p></Card>
  if (entry.source !== 'demo-user') return <Card padded><h1>개발환경 정보를 불러올 수 없어요</h1></Card>
  function edit(action) { onEdit(action); setNotice('') }
  const options = items => items.map(([value, label]) => <option key={value} value={value}>{label}</option>)
  function scalar(field, label, values, placeholder) {
    const props = { id: field, value: draft[field], className: styles.input, 'data-field': field, 'aria-invalid': !!errors[field], 'aria-describedby': errors[field] ? `${field}-error` : undefined, onChange: event => edit({ edit: 'scalar', field, value: event.target.value }) }
    return <FormField id={field} label={label} error={errors[field]}>{values ? <select {...props}>{options(values)}</select> : <input {...props} placeholder={placeholder} />}</FormField>
  }
  function rows(field, label, addLabel) {
    return <>
      <div className={styles.list}>{draft[field].map((item, index) => {
        const errorKey = `${field}-${index}`
        return <div key={index}>
          <div className={field === 'authReadiness' ? styles.authRow : styles.row}>
            {field === 'authReadiness' ? <>
              <input id={errorKey} className={styles.input} data-field={errorKey} aria-label={`외부 서비스 ${index + 1} 이름`} placeholder="외부 서비스 이름" value={item.service} aria-invalid={!!errors[errorKey]} aria-describedby={errors[errorKey] ? `${errorKey}-error` : undefined} onChange={event => edit({ edit: 'item', field, index, property: 'service', value: event.target.value })} />
              <select className={styles.input} aria-label={`외부 서비스 ${index + 1} 인증 준비 상태`} value={item.state} onChange={event => edit({ edit: 'item', field, index, property: 'state', value: event.target.value })}>{options(authOptions)}</select>
            </> : <>
              {['name', 'version'].map(property => <input key={property} id={property === 'name' ? errorKey : `${errorKey}-version`} data-field={property === 'name' ? errorKey : undefined} className={styles.input} value={item[property]} placeholder={property === 'name' ? '이름' : '버전 (선택)'} aria-label={`${label} ${index + 1} ${property === 'name' ? '이름' : '버전'}`} aria-invalid={!!errors[errorKey]} aria-describedby={errors[errorKey] ? `${errorKey}-error` : undefined} onChange={event => edit({ edit: 'item', field, index, property, value: event.target.value })} />)}
            </>}
            <Button aria-label={`${label} ${index + 1} 삭제`} onClick={() => edit({ edit: 'remove', field, index })}>삭제</Button>
          </div>
          {errors[errorKey] && <p id={`${errorKey}-error`} className={styles.error} role="alert">{errors[errorKey]}</p>}
        </div>
      })}</div>
      {field !== 'authReadiness' && <Button variant="soft" disabled={draft[field].length >= 30} onClick={() => edit({ edit: 'add', field })}>{addLabel}</Button>}
    </>
  }
  function submit(event) {
    event.preventDefault()
    setSubmitted(true)
    const invalid = Object.keys(validateEnvironment(draft))[0]
    if (invalid) {
      setNotice('')
      if (invalid.startsWith('installed') || invalid.startsWith('authReadiness')) details.current.open = true
      requestAnimationFrame(() => form.current?.querySelector(`[data-field="${invalid}"]`)?.focus())
      return
    }
    onConfirm()
    setNotice('입력 조건을 확인했어요. Capability 추천(07/10)은 아직 구현되지 않아 이동하지 않습니다. 서버 저장·환경 검사·추천 결과 생성은 수행하지 않았고 입력값은 시연 메모리에만 유지됩니다.')
  }
  return <>
    <PageHeader eyebrow="06 / 10 · ENVIRONMENT PROFILE" title="개발환경 입력" description="PC 환경을 자동 검사하지 않습니다. 알고 있는 정보를 직접 입력해주세요." />
    <WizardProgress currentStep={6} navigationOnly />
    <div className={styles.alert}><span aria-hidden="true">ⓘ</span><span>환경 정보는 모두 선택 입력입니다. 아래 값은 사용자 입력값(USER_DECLARED)이며, 비밀번호나 API Key는 입력하지 마세요. 모르는 항목은 미정으로 둘 수 있어요.</span></div>
    <form ref={form} noValidate onSubmit={submit}>
      <div className={styles.grid}>
        <Card padded>
          <div className={styles.eyebrow}>OPERATING SYSTEM</div><h3 className={styles.heading}>운영체제</h3><div className={styles.divider} />
          {scalar('osFamily', 'OS 종류', osOptions)}{scalar('osVersion', 'OS 버전', null, '예: 11')}
          <div className={styles.divider} />
          <div className={styles.field}><div className={styles.label}>AI Client</div><div className={styles.readonly}>Codex <Badge variant="neutral">MVP 지원 대상</Badge></div><p className={styles.hint}>다른 Client는 현재 지원 대상으로 등록되어 있지 않습니다.</p></div>
          {scalar('clientVersion', 'Codex 버전 (알고 있다면)', null, '모르면 비워두기')}{scalar('configurationKnowledge', '현재 설정 상태', configurationOptions)}
        </Card>
        <Card padded>
          <div className={styles.eyebrow}>RUNTIME</div><h3 className={styles.heading}>실행 환경</h3><div className={styles.divider} />
          <select id="runtimeKnowledge" data-field="runtimeKnowledge" className={`${styles.input} ${styles.knowledge}`} aria-label="실행 환경 정보 상태" value={draft.runtimeKnowledge} onChange={event => edit({ edit: 'scalar', field: 'runtimeKnowledge', value: event.target.value })}>{options([['UNKNOWN', '모름'], ['KNOWN', '알고 있는 실행 환경 입력']])}</select>
          {draft.runtimeKnowledge === 'KNOWN' ? <>{rows('runtimes', '실행 환경', '＋ 실행 환경 추가')}<p className={styles.hint}>예: Node.js · 20. 최대 30개, 항목당 200자.</p>{!draft.runtimes.length && <p><Badge variant="neutral">알고 있는 실행 환경 없음</Badge></p>}</> : <p>모름은 실행 환경이 없다는 뜻이 아닙니다.</p>}
          <details ref={details} className={styles.details}><summary>추가 환경 정보</summary>
            {scalar('installedComponentsKnowledge', '설치된 구성요소', [['UNKNOWN', '모름'], ['KNOWN', '설치 항목을 알고 있음']])}
            {draft.installedComponentsKnowledge === 'KNOWN' ? <>{rows('installedComponents', '설치된 구성요소', '＋ 설치 항목 추가')}<p className={styles.hint}>KNOWN 상태에서 목록이 비어 있으면 ‘설치된 항목 없음’입니다.</p>{!draft.installedComponents.length && <p><Badge variant="neutral">설치된 항목 없음</Badge></p>}</> : <p>모름은 설치 항목 없음과 다릅니다.</p>}
            <div className={styles.divider} /><div className={styles.itemHead}><strong className={styles.label}>외부 서비스 인증 준비 상태</strong><Button variant="soft" disabled={draft.authReadiness.length >= 30} onClick={() => edit({ edit: 'add', field: 'authReadiness' })}>＋ 서비스 추가</Button></div>
            <p>Secret이나 인증 토큰은 입력하지 말고 준비 상태만 표시합니다.</p>
            {rows('authReadiness', '외부 서비스', '＋ 서비스 추가')}
          </details>
        </Card>
      </div>
      <p className={styles.demo}>시연 모드 · 새로고침하거나 시연을 종료하면 환경 입력값이 초기화됩니다. Codex 표시는 시안의 지원 대상이며 현재 PC에 설치되어 있다는 뜻이 아닙니다.</p>
      <p className={styles.notice} role="status">{notice}</p>
      <div className={styles.actions}><Button to={rolePath}>← 내 역할 수정</Button><Button type="submit" variant="primary" large>개발환경 저장하고 추천 보기 →</Button></div>
    </form>
  </>
}
