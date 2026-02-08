import { Outlet, useNavigate } from 'react-router-dom'
import { useEffect, useState } from 'react'
import { api } from '@/lib/api'
import NavBar from './NavBar'

export default function AppLayout() {
  const navigate = useNavigate()
  const [ready, setReady] = useState(false)

  useEffect(() => {
    api.getProfile()
      .then(() => setReady(true))
      .catch(() => navigate('/login'))
  }, [navigate])

  if (!ready) {
    return (
      <div className="min-h-screen flex items-center justify-center">
        <div className="text-muted-foreground">Loading...</div>
      </div>
    )
  }

  return (
    <div className="min-h-screen">
      <NavBar />
      <main>
        <Outlet />
      </main>
    </div>
  )
}
