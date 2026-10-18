import { useNavigate } from 'react-router'
import { useDemoProjects } from '../../features/projects/DemoContext.js'
import { Badge } from '../ui/UI.jsx'
import styles from './Layout.module.css'

export default function Topbar({ title }) {
  const { dispatch } = useDemoProjects()
  const navigate = useNavigate()
  return <header className={styles.topbar}>
    <div className={styles.crumb}>WORKSPACE <span aria-hidden="true">›</span> <b>{title}</b></div>
    <div className={styles.topbarRight}><span className={styles.modeBadge}><Badge>시연 모드 · 실제 연동 전</Badge></span><span className={styles.mobileMode}><Badge>시연 모드</Badge></span><button type="button" className={styles.exit} onClick={() => { dispatch({ type: 'exit' }); navigate('/') }}>시연 종료</button></div>
  </header>
}
