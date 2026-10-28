import { useDemoProjects } from '../features/projects/DemoContext.js'
import { getVisibleProjects } from '../features/projects/projectModel.js'
import PageHeader from '../components/layout/PageHeader.jsx'
import { Badge, Button, Card } from '../components/ui/UI.jsx'
import styles from './ProjectsPage.module.css'

export default function ProjectsPage() {
  const { state, dispatch } = useDemoProjects()
  const projects = getVisibleProjects(state)
  const selected = projects.find(project => project.id === state.selectedProjectId)
  return <>
    <PageHeader eyebrow="WORKSPACE" title="내 프로젝트" description="프로젝트를 만들고 기획 정보를 확인해 이어서 작업하세요." action={<Button to="/demo/projects/new" variant="primary">＋ 새 프로젝트</Button>} />
    <section className={styles.hero} aria-label="프로젝트 시작 안내"><div className={styles.copy}><div className={styles.eyebrow}>GET STARTED</div><h2>프로젝트에 맞는 AI 도구,<br />함께 찾아볼까요?</h2><p>기획 자료와 내 업무·환경을 바탕으로 필요한 구성을 확인해요.</p><Button to="/demo/projects/new" variant="primary">새 프로젝트 시작하기 &nbsp;→</Button></div></section>
    <div className={styles.stats}>
      <Card className={styles.stat}><span>내 프로젝트</span><b>{projects.length}</b><span>현재 시연 세션</span></Card>
      <Card className={styles.stat}><span>기획 분석</span><b>연동 전</b><span>AI 결과 미생성</span></Card>
      <Card className={styles.stat}><span>도구 추천</span><b>검증 전</b><span>Catalog 미연결</span></Card>
    </div>
    <div className={styles.listHeader}><h2>최근 프로젝트</h2><span>{projects.length}개 프로젝트</span></div>
    <div id="demo-project-list">
    {projects.length ? <div className={styles.list}>
      {projects.map(project => <Card as={Link} to={`/demo/projects/${encodeURIComponent(project.id)}`} onClick={() => dispatch({ type: 'select', id: project.id })} key={project.id} className={`${styles.project} ${selected?.id === project.id ? styles.selected : ''}`}>
        <span className={styles.projectIcon} aria-hidden="true">⌘</span><span className={styles.projectBody}><strong>{project.name}</strong><span className={styles.projectMeta}><Badge>시연 데이터</Badge><span>이 탭의 메모리에만 유지</span></span></span><span className={styles.arrow} aria-hidden="true">→</span>
      </Card>)}
    </div> : <Card padded className={styles.empty}><h3>아직 프로젝트가 없어요</h3><p>프로젝트 이름을 만들고 기획 자료를 입력하면 시작할 수 있어요.</p><Button to="/demo/projects/new" variant="primary">첫 프로젝트 만들기</Button></Card>}
    </div>
    <p className={styles.footer}>시연 모드 · 실제 로그인이나 서버 저장 없이 동작합니다. 프로젝트는 새로고침하거나 시연을 종료하면 초기화됩니다.</p>
    {import.meta.env.DEV && <Button variant="soft" aria-expanded={state.examplesVisible} aria-controls="demo-project-list" onClick={() => dispatch({ type: 'examples' })}>{state.examplesVisible ? '개발용 예시 프로젝트 닫기' : '개발용 예시 프로젝트 불러오기'}</Button>}
  </>
}
import { Link } from 'react-router'
