import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { DollarSign } from 'lucide-react'
import { api } from '@/lib/api'

export default function RegisterPage() {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)

  async function handleSubmit(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError('')

    const form = new FormData(e.currentTarget)
    const email = (form.get('email') as string).trim()
    const displayName = (form.get('displayName') as string).trim() || undefined
    const password = form.get('password') as string
    const confirmPassword = form.get('confirmPassword') as string

    if (password !== confirmPassword) {
      setError('Passwords do not match')
      return
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters')
      return
    }

    setLoading(true)

    try {
      await api.register({ email, password, displayName })
      navigate('/login?registered=true')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Registration failed')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="bg-muted flex min-h-svh flex-col items-center justify-center p-6 md:p-10">
      <div className="w-full max-w-sm md:max-w-3xl">
        <Card className="overflow-hidden p-0">
          <CardContent className="grid p-0 md:grid-cols-2">
            <div className="p-6 md:p-8">
              <div className="flex flex-col gap-6">
                <div className="flex flex-col items-center text-center">
                  <h1 className="text-2xl font-bold">Create an account</h1>
                  <p className="text-muted-foreground text-balance">
                    Enter your details to get started
                  </p>
                </div>

                {error && (
                  <div className="rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
                    {error}
                  </div>
                )}

                <form onSubmit={handleSubmit}>
                  <div className="flex flex-col gap-6">
                    <div className="grid gap-3">
                      <Label htmlFor="email">Email</Label>
                      <Input
                        id="email"
                        name="email"
                        type="email"
                        required
                        autoFocus
                        placeholder="you@example.com"
                      />
                    </div>
                    <div className="grid gap-3">
                      <Label htmlFor="displayName">Display Name</Label>
                      <Input
                        id="displayName"
                        name="displayName"
                        placeholder="Your name (optional)"
                      />
                    </div>
                    <div className="grid gap-3">
                      <Label htmlFor="password">Password</Label>
                      <Input
                        id="password"
                        name="password"
                        type="password"
                        required
                        minLength={8}
                      />
                      <p className="text-xs text-muted-foreground">
                        Must be at least 8 characters
                      </p>
                    </div>
                    <div className="grid gap-3">
                      <Label htmlFor="confirmPassword">Confirm Password</Label>
                      <Input
                        id="confirmPassword"
                        name="confirmPassword"
                        type="password"
                        required
                      />
                    </div>
                    <Button type="submit" className="w-full" disabled={loading}>
                      {loading ? 'Creating Account...' : 'Create Account'}
                    </Button>
                  </div>
                </form>

                <div className="text-center text-sm">
                  Already have an account?{' '}
                  <Link
                    to="/login"
                    className="underline underline-offset-4 hover:text-primary"
                  >
                    Sign in
                  </Link>
                </div>
              </div>
            </div>

            <div className="bg-muted relative hidden md:flex md:flex-col md:items-center md:justify-center">
              <div className="flex flex-col items-center gap-4">
                <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-primary text-primary-foreground">
                  <DollarSign className="h-8 w-8" />
                </div>
                <span className="text-lg font-semibold">Expense Tracker</span>
              </div>
            </div>
          </CardContent>
        </Card>
      </div>
    </div>
  )
}
