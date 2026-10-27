import { useState } from 'react'
import { apiRequest } from '../../features/live/api.js'
import { Button } from '../ui/UI.jsx'

export default function LogoutButton({ disabled = false }) {
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')

  async function logout() {
    setBusy(true)
    setError('')
    try {
      await apiRequest('/api/auth/logout', { method: 'POST' })
      window.location.replace('/')
    } catch (cause) {
      setError(cause.message)
      setBusy(false)
    }
  }

  return <span>
    <Button onClick={logout} disabled={disabled || busy}>로그아웃</Button>
    {error && <span role="alert">{error}</span>}
  </span>
}
