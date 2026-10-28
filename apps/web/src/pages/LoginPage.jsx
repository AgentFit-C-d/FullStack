import { useState } from 'react'
import { useNavigate } from 'react-router'
import Brand from '../components/layout/Brand.jsx'
import { Button } from '../components/ui/UI.jsx'
import OnboardingCarousel from '../features/auth/OnboardingCarousel.jsx'
import { useDemoProjects } from '../features/projects/DemoContext.js'
import styles from './LoginPage.module.css'

export default function LoginPage() {
  const { dispatch } = useDemoProjects()
  const navigate = useNavigate()
  return <LoginScreen mode="demo" onDemo={() => { dispatch({ type: 'enter' }); navigate('/demo/projects') }} />
}

export function ServiceLoginPage() {
  return <LoginScreen mode="service" />
}

export function LoginScreen({ mode, onDemo }) {
  const [notice, setNotice] = useState('')
  return <main className={styles.layout}>
    <div className={styles.side}><Brand auth /><div className={styles.slideArea}><OnboardingCarousel /></div></div>
    <div className={styles.main}><div className={styles.form}>
      <div className={styles.eyebrow}>WELCOME TO AGENTFIT</div>
      <h1>AgentFit에 오신 것을 환영해요</h1>
      <p className={styles.description}>GitHub 계정으로 로그인하고 프로젝트를 시작하세요.</p>
      {mode === 'service' ? <a className={styles.github} href="/api/oauth2/authorization/github"><span>GitHub로 계속하기</span><span aria-hidden="true">→</span></a>
        : <button type="button" className={styles.github} aria-describedby="oauth-status" onClick={() => setNotice('GitHub OAuth 연동 전입니다. 실제 인증은 수행하지 않았습니다.')}>
          <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 .7a11.5 11.5 0 0 0-3.64 22.4c.58.1.79-.25.79-.56v-2.23c-3.22.7-3.9-1.37-3.9-1.37-.53-1.34-1.29-1.7-1.29-1.7-1.05-.72.08-.71.08-.71 1.17.08 1.78 1.2 1.78 1.2 1.04 1.78 2.72 1.27 3.38.97.11-.75.41-1.27.74-1.56-2.57-.29-5.27-1.28-5.27-5.68 0-1.26.45-2.28 1.19-3.09-.12-.29-.52-1.47.11-3.05 0 0 .97-.31 3.16 1.18A10.9 10.9 0 0 1 12 6.11c.98 0 1.95.13 2.87.39 2.19-1.49 3.15-1.18 3.15-1.18.63 1.58.23 2.76.11 3.05.74.81 1.19 1.83 1.19 3.09 0 4.41-2.71 5.38-5.29 5.67.42.36.79 1.07.79 2.16v3.25c0 .31.21.67.8.55A11.5 11.5 0 0 0 12 .7Z" /></svg>
          <span>GitHub로 계속하기</span><span aria-hidden="true">→</span>
        </button>}
      <p className={styles.note}>AgentFit을 이용하려면 GitHub 계정이 필요합니다.</p>
      <div className={styles.signup}>GitHub 계정이 없으신가요? &nbsp;<a href="https://github.com/signup" target="_blank" rel="noopener noreferrer">GitHub에서 계정 만들기 ↗</a></div>
      <div className={styles.demo}>
        <p id="oauth-status">{mode === 'demo' ? 'GitHub 로그인 연동 전 · 실제 인증은 진행하지 않습니다.' : 'GitHub 로그인 후 프로젝트 화면으로 돌아옵니다.'}</p>
        {mode === 'demo' && import.meta.env.DEV && <><Button variant="soft" onClick={onDemo}>개발용 시연 모드로 둘러보기 →</Button><p>시연 데이터는 이 탭의 메모리에만 유지되며 새로고침하면 초기화됩니다.</p></>}
        <div role="status" className={styles.notice}>{notice}</div>
      </div>
    </div></div>
  </main>
}
