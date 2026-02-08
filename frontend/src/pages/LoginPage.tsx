import { useSearchParams, Link } from 'react-router-dom'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { AlertCircle, CheckCircle2, LogOut } from 'lucide-react'

export default function LoginPage() {
  const [searchParams] = useSearchParams()
  const hasError = searchParams.get('error') === 'true'
  const hasLogout = searchParams.get('logout') === 'true'
  const hasRegistered = searchParams.get('registered') === 'true'

  return (
    <div className="min-h-screen flex items-center justify-center p-4">
      <Card className="w-full max-w-[400px]">
        <CardHeader className="text-center">
          <CardTitle className="text-2xl font-bold">
            <span className="text-primary">$</span> Expense Tracker
          </CardTitle>
          <CardDescription>Sign in to your dashboard</CardDescription>
        </CardHeader>
        <CardContent>
          {hasError && (
            <div className="flex items-center gap-2 p-3 rounded-lg mb-4 bg-destructive/15 border border-destructive text-red-400 text-sm">
              <AlertCircle className="h-4 w-4 shrink-0" />
              Invalid email or password
            </div>
          )}
          {hasLogout && (
            <div className="flex items-center gap-2 p-3 rounded-lg mb-4 bg-green-500/15 border border-green-600 text-green-400 text-sm">
              <LogOut className="h-4 w-4 shrink-0" />
              You have been logged out
            </div>
          )}
          {hasRegistered && (
            <div className="flex items-center gap-2 p-3 rounded-lg mb-4 bg-green-500/15 border border-green-600 text-green-400 text-sm">
              <CheckCircle2 className="h-4 w-4 shrink-0" />
              Account created successfully! Please sign in.
            </div>
          )}

          <form action="/login" method="POST">
            <div className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="email">Email</Label>
                <Input
                  id="email"
                  name="email"
                  type="email"
                  required
                  autoFocus
                  placeholder="Enter your email"
                />
              </div>
              <div className="space-y-2">
                <Label htmlFor="password">Password</Label>
                <Input
                  id="password"
                  name="password"
                  type="password"
                  required
                  placeholder="Enter your password"
                />
              </div>
              <Button type="submit" className="w-full">
                Sign In
              </Button>
            </div>
          </form>

          <p className="text-center mt-4 text-sm text-muted-foreground">
            Don&apos;t have an account?{' '}
            <Link to="/register" className="text-primary hover:underline font-medium">
              Sign up
            </Link>
          </p>
        </CardContent>
      </Card>
    </div>
  )
}
