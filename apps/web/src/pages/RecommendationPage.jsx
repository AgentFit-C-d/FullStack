import { useState } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router'
import { useDemoProjects } from '../features/projects/DemoContext.js'
import { getVisibleProjects } from '../features/projects/projectModel.js'
import { canEnterDemoRecommendation, createRecommendationPreview, describeRecommendationResult, unexecutedRecommendation } from '../features/recommendation/recommendationModel.js'
import { capabilityOptions, necessityOptions, previewStates } from '../features/recommendation/recommendationDemoFixture.js'
import PageHeader from '../components/layout/PageHeader.jsx'
import WizardProgress from '../features/projects/WizardProgress.jsx'
import { Badge, Button, Card } from '../components/ui/UI.jsx'
import styles from './RecommendationPage.module.css'

export default function RecommendationPage() {
  const { demoId } = useParams()
  const navigate = useNavigate()
  const { state, dispatch } = useDemoProjects()
  const project = getVisibleProjects(state).find(item => item.id === demoId)
  if (!project) return <Card padded><h1>프로젝트를 찾을 수 없어요</h1><p>현재 시연 세션의 목록에서 프로젝트를 다시 선택해주세요.</p><Button to="/demo/projects">내 프로젝트로</Button></Card>
  const path = `/demo/projects/${encodeURIComponent(project.id)}`
  if (!canEnterDemoRecommendation(state.demoProfiles[project.id], state.roleDrafts[project.id], state.environmentDrafts[project.id])) return <Navigate to={`${path}/environment`} replace />
  return <RecommendationScreen key={project.id} mode="demo" preview={state.recommendationPreviews[project.id] || createRecommendationPreview()} environmentPath={`${path}/environment`} onSelectionPreview={() => { dispatch({ type: 'selection-enter', id: project.id }); navigate(`${path}/selection`) }} onPreview={action => dispatch({ ...action, type: 'recommendation-preview', id: project.id })} />
}

export function RecommendationScreen({ mode, preview, environmentPath, onPreview, onSelectionPreview }) {
  const [notice, setNotice] = useState('')
  if (mode !== 'demo') return <Card padded><h1>{describeRecommendationResult({ status: 'UNAVAILABLE' })}</h1><p>인증된 세션과 추천 API 연결이 필요합니다.</p></Card>
  if (preview.source !== 'demo-ui') return <Card padded><h1>추천 상태를 확인할 수 없어요</h1></Card>
  const sample = preview.visible ? previewStates.find(([status]) => status === preview.status) : null
  return <>
    <PageHeader eyebrow="07 / 10 · CAPABILITY ANALYSIS" title="필요한 능력과 도구 추천" description="프로젝트와 업무를 바탕으로 확인할 Capability와 추천 결과를 검토합니다." />
    <WizardProgress currentStep={7} navigationOnly />
    <div className={styles.alert}><Badge>시연 모드</Badge><span>Capability 상태는 UI 확인용입니다. 실제 AI 판정이나 Catalog 추천이 아닙니다. 역할 이름 하나만으로 필수 능력을 정하지 않습니다.</span></div>
    <div className={styles.preview}><Button variant="soft" aria-expanded={preview.visible} aria-controls="recommendation-preview" onClick={() => onPreview({ edit: 'visibility', value: !preview.visible })}>{preview.visible ? '시연용 상태 미리보기 닫기' : '시연용 상태 미리보기'}</Button><p>고정 시안의 상태·선택 UI만 조작합니다. 실제 추천 결과에 저장되지 않습니다.</p></div>
    <div id="recommendation-preview">
      {preview.visible && <div className={styles.modes} role="group" aria-label="정상 추천 상태 시안 선택">{previewStates.map(([status, label]) => <button key={status} type="button" aria-pressed={status === preview.status} className={`${styles.mode} ${status === preview.status ? styles.active : ''}`} onClick={() => onPreview({ edit: 'status', value: status })}>{label}</button>)}</div>}
      <div className={styles.grid}>
        <Card padded><div className={styles.eyebrow}>CAPABILITY</div><h3 className={styles.heading}>이번 프로젝트에서 확인할 능력</h3><p>{preview.visible ? '각 상태는 실제 분석 결과가 아닌 시안 조작값입니다.' : '추천을 실행하지 않았습니다. 시안에 정의된 항목을 모두 미정으로 표시합니다.'}</p>
          <div className={styles.list}>{capabilityOptions.map(([key, label]) => <div className={styles.row} key={key}><div><strong>{label}</strong><p>AI 분석·검증된 근거 미연결</p></div><select className={styles.select} aria-label={`${label} 필요성`} disabled={!preview.visible} value={preview.visible ? preview.necessities[key] : 'UNDETERMINED'} onChange={event => onPreview({ edit: 'necessity', key, value: event.target.value })}>{necessityOptions.map(([value, text]) => <option key={value} value={value}>{text}</option>)}</select></div>)}</div>
        </Card>
        <Card padded><div className={styles.eyebrow}>RECOMMENDATION STATUS</div><h3 className={styles.heading}>{sample ? `${sample[1]} · 시연 미리보기` : describeRecommendationResult(unexecutedRecommendation)}</h3><p>오류나 Catalog 장애는 정상 추천 상태로 바꾸지 않습니다. 실제 연동 전에는 추천 결과가 확정되지 않습니다.</p><div className={styles.divider} />
          <div className={styles.item}><strong>{sample ? sample[2] : '실제 추천 결과가 없습니다.'}</strong><p>{sample ? sample[3] : '추천 API와 지원 대상 정책이 연결되지 않았습니다. 요청을 보내거나 사용자 입력에서 추천·점수·근거를 생성하지 않았습니다.'}</p></div>
        </Card>
      </div>
    </div>
    <p id="recommendation-next-hint" className={styles.notice}>실제 추천 결과가 없으며, 실제 도구 선택은 사용할 수 없습니다. 별도 시연 경로에서 화면만 확인할 수 있습니다.</p>
    <p className={styles.notice} role="status">{notice}</p>
    <div className={styles.preview}><Button variant="soft" onClick={onSelectionPreview}>시연용 도구·권한 화면 미리보기</Button></div>
    <div className={styles.actions}><Button to={environmentPath}>← 개발환경 수정</Button><Button variant="primary" large aria-describedby="recommendation-next-hint" onClick={() => setNotice('실제 추천 결과가 없어 추천을 확정하지 않습니다. Catalog·추천 API 미연동 상태입니다. 별도 시연 진입을 이용해주세요.')}>도구·권한 선택 →</Button></div>
  </>
}
