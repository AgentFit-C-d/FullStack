import { useState } from 'react'
import { useLoaderData, useNavigate } from 'react-router'
import Brand from '../components/layout/Brand.jsx'
import LogoutButton from '../components/layout/LogoutButton.jsx'
import PageHeader from '../components/layout/PageHeader.jsx'
import { Button, Card, FormField } from '../components/ui/UI.jsx'
import { validateProjectName } from '../features/projects/projectName.js'
import { isAuthenticatedSession } from '../features/service/serviceModel.js'
import { apiRequest } from '../features/live/api.js'
import styles from './ServiceProjectCreatePage.module.css'

export function ServiceProjectCreateScreen({ name, onNameChange, onSubmit, busy = false, error = '' }) {
  return <main className={styles.shell}>
    <header className={styles.header}><Brand /><div className={styles.headerActions}><Button to="/app/projects">내 프로젝트로</Button><LogoutButton disabled={busy} /></div></header>
    <PageHeader eyebrow="PROJECT" title="새 프로젝트 만들기" description="먼저 프로젝트 이름을 입력해주세요." />
    <form onSubmit={onSubmit} noValidate>
      <Card padded className={styles.panel}>
        <FormField id="service-project-name" label="프로젝트 이름" required error={error} hint="최대 100자까지 입력할 수 있어요.">
          <input id="service-project-name" className={styles.input} value={name} onChange={event => onNameChange(event.target.value)} placeholder="프로젝트 이름을 입력해주세요." aria-invalid={!!error} autoFocus />
        </FormField>
      </Card>
      <div className={styles.actions}><Button to="/app/projects">취소</Button><Button type="submit" variant="primary" disabled={busy}>{busy ? '저장 중…' : '프로젝트 만들기 →'}</Button></div>
    </form>
  </main>
}

export default function ServiceProjectCreatePage() {
  const { auth } = useLoaderData()
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  if (!isAuthenticatedSession(auth)) return <main className={styles.shell}><Brand /><Card padded><h1>{auth?.status === 'unauthenticated' ? '로그인이 필요해요' : '지금은 로그인 서비스를 이용할 수 없어요'}</h1><Button to="/login">로그인 화면으로</Button></Card></main>

  async function create(event) {
    event.preventDefault()
    const validation = validateProjectName(name)
    if (validation) { setError(validation); return }
    setBusy(true); setError('')
    try {
      const result = await apiRequest('/api/projects', { method: 'POST', body: { name: name.trim() } })
      if (!result?.project?.id) throw new Error('프로젝트 저장 결과를 확인할 수 없어요.')
      navigate(`/app/live?project=${encodeURIComponent(result.project.id)}`, { replace: true })
    } catch (cause) { setError(cause.message || '프로젝트를 저장하지 못했어요.') }
    finally { setBusy(false) }
  }
  return <ServiceProjectCreateScreen name={name} onNameChange={setName} onSubmit={create} busy={busy} error={error} />
}
