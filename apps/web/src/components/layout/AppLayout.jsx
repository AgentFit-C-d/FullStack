import { useEffect, useRef } from 'react'
import { Navigate, Outlet, useLocation } from 'react-router'
import { useDemoProjects } from '../../features/projects/DemoContext.js'
import Sidebar from './Sidebar.jsx'
import Topbar from './Topbar.jsx'
import styles from './Layout.module.css'

export default function AppLayout() {
  const { state } = useDemoProjects()
  const location = useLocation()
  const headingRef = useRef(null)
  useEffect(() => { headingRef.current?.focus(); window.scrollTo(0, 0) }, [location.pathname])
  if (!state.active) return <Navigate to="/" replace />
  return <div className={styles.app}>
    <a className={styles.skip} href="#main-content">본문으로 건너뛰기</a>
    <Sidebar />
    <div className={styles.main}>
      <Topbar title={location.pathname.endsWith('/new') ? '프로젝트 생성' : location.pathname === '/demo/projects' ? '내 프로젝트' : location.pathname.endsWith('/analysis') ? 'AI 기획 분석' : location.pathname.endsWith('/review') ? '분석 내용 확인' : location.pathname.endsWith('/role') ? '내 역할 입력' : location.pathname.endsWith('/environment') ? '개발환경 입력' : '기획 자료 입력'} />
      <main id="main-content" tabIndex={-1} ref={headingRef} className={styles.content}><Outlet /></main>
    </div>
  </div>
}
