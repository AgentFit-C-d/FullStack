import { useLoaderData, useNavigation, useParams } from 'react-router'
import Brand from '../components/layout/Brand.jsx'
import LogoutButton from '../components/layout/LogoutButton.jsx'
import PageHeader from '../components/layout/PageHeader.jsx'
import { Button, Card } from '../components/ui/UI.jsx'
import { containsDemoData, isAuthenticatedSession } from '../features/service/serviceModel.js'
import styles from './ServicePage.module.css'

const messages = {
  loading: ['정보를 불러오고 있어요', '잠시만 기다려주세요.'],
  empty: ['표시할 정보가 없어요', '아직 등록된 정보가 없습니다.'],
  unavailable: ['지금은 이 기능을 이용할 수 없어요', '잠시 후 다시 이용해주세요.'],
  error: ['정보를 불러오지 못했어요', '잠시 후 다시 시도해주세요.'],
  'not-found': ['요청한 정보를 찾을 수 없어요', '프로젝트 목록에서 다시 확인해주세요.'],
  forbidden: ['이 정보에 접근할 수 없어요', '사용 가능한 계정과 프로젝트를 확인해주세요.'],
}
export function ServiceResourceState({ result }) {
  const status = containsDemoData(result) || ['ready', 'empty'].includes(result?.status) && result.source !== 'server' ? 'error' : result?.status
  // Ready DTOs must be connected to server-backed forms before enabling a service action.
  const [title, description] = messages[status] || messages.unavailable
  return <Card padded role="status" aria-busy={status === 'loading'}><h2>{title}</h2><p>{description}</p></Card>
}
export function ServiceLoading() {
  return <main className={styles.shell}><Brand /><ServiceResourceState result={{ status: 'loading' }} /></main>
}
function SavedProjects({ projects }) {
  return <section className={styles.projects} aria-label="저장된 프로젝트">
    <div className={styles.projectHeading}><h2>저장된 프로젝트</h2><span>{projects.length}개</span></div>
    {projects.map(project => <Card key={project.id} className={styles.project}>
      <div><strong>{project.name}</strong><span>마지막 수정 {new Date(project.updatedAt).toLocaleDateString('ko-KR')}</span></div>
      <Button to={`/app/live?project=${encodeURIComponent(project.id)}`} variant="primary">이어하기 →</Button>
    </Card>)}
  </section>
}
export function ServiceScreen({ stage, auth, resource, projectId = null }) {
  const authenticated = isAuthenticatedSession(auth)
  const projectList = stage.key === 'projects' && ['ready', 'empty'].includes(resource?.status) && resource.source === 'server' && Array.isArray(resource.data?.projects) && !containsDemoData(resource)
  const projectDetail = stage.key === 'planning' && resource?.status === 'ready' && resource.source === 'server' && resource.data?.project?.id === projectId && !containsDemoData(resource)
  return <main className={styles.shell}>
    <header className={styles.header}><Brand />{authenticated ? <LogoutButton /> : <Button to="/login">로그인 화면으로</Button>}</header>
    {!authenticated ? <Card padded role="status"><h1>{auth?.status === 'unauthenticated' ? '로그인이 필요해요' : '지금은 로그인 서비스를 이용할 수 없어요'}</h1><p>{auth?.status === 'unauthenticated' ? '로그인 후 다시 이용해주세요.' : '잠시 후 다시 시도해주세요.'}</p><Button to="/login">로그인 화면으로</Button></Card> : <>
      <PageHeader title={stage.title} description="프로젝트 정보를 확인해주세요." />
      {projectList ? resource.data.projects.length ? <SavedProjects projects={resource.data.projects} /> : <Card padded><h2>아직 프로젝트가 없어요</h2><p>프로젝트 이름부터 입력해 시작해 주세요.</p></Card>
        : projectDetail ? <Card padded><h2>{resource.data.project.name}</h2><p>저장된 프로젝트의 기획 자료부터 이어서 작업할 수 있어요.</p></Card>
          : <ServiceResourceState result={resource} />}
      <div className={styles.actions}><Button to={projectList ? '/app/projects/new' : projectDetail ? `/app/live?project=${encodeURIComponent(projectId)}` : '/app/live'} variant="primary">{projectList ? '새 프로젝트 만들기' : '이어서 작업하기'}</Button>{stage.key !== 'projects' && <Button to="/app/projects">내 프로젝트로</Button>}</div>
    </>}
  </main>
}
export default function ServicePage({ stage }) {
  const data = useLoaderData()
  const navigation = useNavigation()
  const { projectId } = useParams()
  if (navigation.state !== 'idle' || data.projectId !== (projectId ?? null)) return <ServiceLoading />
  return <ServiceScreen stage={stage} auth={data.auth} resource={data.resource} projectId={projectId} />
}
