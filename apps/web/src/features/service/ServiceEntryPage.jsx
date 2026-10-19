import { Navigate, useSearchParams } from 'react-router'
import { ServiceLoginPage } from '../../pages/LoginPage.jsx'

export default function ServiceEntryPage() {
  const [search] = useSearchParams()
  if (search.get('login') === 'success') return <Navigate to="/app" replace />
  return <ServiceLoginPage />
}
