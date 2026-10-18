import { Navigate, useNavigate, useParams } from 'react-router'
import { useDemoProjects } from '../features/projects/DemoContext.js'
import { getPlanningDraft, getVisibleProjects } from '../features/projects/projectModel.js'
import { validatePlanningDraft } from '../features/planning/planningModel.js'
import WizardProgress from '../features/projects/WizardProgress.jsx'
import PageHeader from '../components/layout/PageHeader.jsx'
import { Button, Card } from '../components/ui/UI.jsx'
import styles from './AnalysisPage.module.css'
import { getAnalysisPresentation } from '../features/analysis/analysisPresentation.js'

export default function AnalysisPage() {
  const { demoId } = useParams()
  const { state, dispatch } = useDemoProjects()
  const navigate = useNavigate()
  const project = getVisibleProjects(state).find(item => item.id === demoId)
  if (!project) return <Card padded><h1>프로젝트를 찾을 수 없어요</h1><p>현재 시연 세션의 목록에서 프로젝트를 다시 선택해주세요.</p><Button to="/demo/projects">내 프로젝트로</Button></Card>

  const planningPath = `/demo/projects/${encodeURIComponent(project.id)}`
  if (validatePlanningDraft(getPlanningDraft(state, project.id))) return <Navigate to={planningPath} replace />

  return <AnalysisScreen project={project} planningPath={planningPath} mode="demo" status="unavailable" onPreview={() => {
    dispatch({ type: 'profile-preview', id: project.id })
    navigate(`${planningPath}/review`)
  }} />
}

// API integration supplies analysis status separately from session/authentication.
export function AnalysisScreen({ project, planningPath, mode, status, onPreview }) {
  const view = getAnalysisPresentation(status, mode)
  return <>
    <PageHeader eyebrow="03 / 10 · AI ANALYSIS" title={view.title} description="프로젝트의 주요 기능과 기술 구성을 확인하는 단계입니다." />
    <WizardProgress currentStep={3} />
    <Card padded className={styles.panel} role="status" aria-live="polite">
      <div className={styles.icon} aria-hidden="true">◫</div>
      <h3>{view.heading}</h3>
      <p className={styles.project}>선택한 프로젝트: <strong>{project.name}</strong></p>
      <p>{view.description}</p>
      {mode === 'demo' && <p>시연 UI입니다. 기획 자료는 전송되지 않았으며 분석은 시작되지 않았습니다.</p>}
      <p id="analysis-unavailable">{status === 'unavailable' ? (mode === 'demo' ? '분석 API가 연결되지 않아 실제 분석 결과를 확인할 수 없습니다.' : '분석 서비스를 이용할 수 없어 결과를 확인할 수 없습니다.') : 'Project Profile 연결은 실제 분석 결과와 함께 제공될 예정입니다.'}</p>
      {mode === 'demo' && <div className={styles.preview}><Button variant="soft" onClick={onPreview}>시연용 Profile 편집 화면 미리보기</Button></div>}
    </Card>
    <div className={styles.actions}>
      <Button to={planningPath}>← 기획 자료 수정</Button>
      <Button variant="primary" large disabled className={styles.unavailable} aria-describedby="analysis-unavailable">Project Profile 확인하기 →</Button>
    </div>

  </>
}
