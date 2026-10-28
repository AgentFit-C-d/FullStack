import { useEffect, useRef, useState } from 'react'
import { NavLink, useNavigate } from 'react-router'
import { useDemoProjects } from '../../features/projects/DemoContext.js'
import Brand from './Brand.jsx'
import styles from './Layout.module.css'

export default function Sidebar() {
  const [open, setOpen] = useState(false)
  const areaRef = useRef(null)
  const buttonRef = useRef(null)
  const { dispatch } = useDemoProjects()
  const navigate = useNavigate()
  useEffect(() => {
    if (!open) return
    const closeOutside = event => { if (!areaRef.current?.contains(event.target)) setOpen(false) }
    const closeEscape = event => {
      if (event.key === 'Escape') { setOpen(false); buttonRef.current?.focus() }
    }
    document.addEventListener('pointerdown', closeOutside)
    document.addEventListener('keydown', closeEscape)
    return () => {
      document.removeEventListener('pointerdown', closeOutside)
      document.removeEventListener('keydown', closeEscape)
    }
  }, [open])
  function exitDemo() { dispatch({ type: 'exit' }); navigate('/') }
  return <aside className={styles.sidebar}>
    <Brand />
    <div className={styles.navCaption}>WORKSPACE</div>
    <nav className={styles.nav} aria-label="주요 화면">
      <NavLink to="/demo/projects" end className={({ isActive }) => `${styles.navItem} ${isActive ? styles.active : ''}`}><span aria-hidden="true">▤</span>내 프로젝트</NavLink>
      <NavLink to="/demo/projects/new" className={({ isActive }) => `${styles.navItem} ${isActive ? styles.active : ''}`}><span aria-hidden="true">✚</span>새 프로젝트</NavLink>
    </nav>
    <div className={styles.sideBottom}>
      <div className={styles.sideTip}><div>✦</div><strong>나에게 맞는 AI 도구 찾기</strong><p>프로젝트 정보와 내 환경을 바탕으로 필요한 개발 도구를 확인해요.</p></div>
      <div ref={areaRef} className={styles.profileArea}>
        <button ref={buttonRef} className={styles.profileButton} type="button" aria-expanded={open} aria-controls="demo-profile-menu" onClick={() => setOpen(!open)}>
          <span className={styles.avatar}>시</span><span><b>시안 사용자</b><small>시안 미리보기 모드</small></span><span className={styles.caret} aria-hidden="true">⌃</span>
        </button>
        {open && <div id="demo-profile-menu" className={styles.profileMenu}>
          <div className={styles.profileMenuHead}><strong>시안 사용자</strong><small>OAuth 연동 전</small></div>
          <button type="button" className={styles.profileMenuAction} onClick={exitDemo}>로그인 화면으로</button>
        </div>}
      </div>
    </div>
  </aside>
}
