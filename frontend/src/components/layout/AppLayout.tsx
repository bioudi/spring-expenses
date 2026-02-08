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
        <div className="text-sm text-muted-foreground">Loading...</div>
      </div>
    )
  }

  return (
    <div className="min-h-screen bg-background">
      <NavBar />
      <main className="md:pl-56">
        <div className="min-h-screen">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
