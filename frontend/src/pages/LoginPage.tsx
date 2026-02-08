import { useSearchParams, Link } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Wallet } from 'lucide-react'

export default function LoginPage() {
  const [searchParams] = useSearchParams()
  const hasError = searchParams.get('error') === 'true'
  const hasLogout = searchParams.get('logout') === 'true'
  const hasRegistered = searchParams.get('registered') === 'true'

  return (
    <div className="bg-muted flex min-h-svh flex-col items-center justify-center p-6 md:p-10">
      <div className="w-full max-w-sm md:max-w-3xl">
        <Card className="overflow-hidden p-0">
          <CardContent className="grid p-0 md:grid-cols-2">
            <div className="p-6 md:p-8">
              <div className="flex flex-col gap-6">
                <div className="flex flex-col items-center text-center">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary text-primary-foreground mb-2">
                    <Wallet className="h-5 w-5" />
                  </div>
                  <h1 className="text-2xl font-bold">Welcome back</h1>
                  <p className="text-muted-foreground text-balance">
                    Sign in to your Spendifi account
                  </p>
                </div>

                {hasError && (
                  <div className="rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
                    Invalid email or password
                  </div>
                )}
                {hasLogout && (
                  <div className="rounded-md border border-green-600/50 bg-green-500/10 px-4 py-3 text-sm text-green-700 dark:text-green-400">
                    You have been logged out
                  </div>
                )}
                {hasRegistered && (
                  <div className="rounded-md border border-green-600/50 bg-green-500/10 px-4 py-3 text-sm text-green-700 dark:text-green-400">
                    Account created! Please sign in.
                  </div>
                )}

                <form action="/login" method="POST">
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

                <div className="text-center text-sm text-muted-foreground">
                  Don&apos;t have an account?{' '}
                  <Link
                    to="/register"
                    className="underline underline-offset-4 hover:text-foreground"
                  >
                    Sign up
                  </Link>
                </div>
              </div>
            </div>

            <div className="bg-muted relative hidden md:flex md:flex-col md:items-center md:justify-center">
              <div className="flex flex-col items-center gap-4 p-8 text-center">
                <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-primary text-primary-foreground">
                  <Wallet className="h-8 w-8" />
                </div>
                <div className="space-y-2">
                  <h2 className="text-xl font-semibold tracking-tight">
                    Spendifi
                  </h2>
                  <p className="text-sm text-muted-foreground text-balance">
                    Track your spending, manage merchants, and gain insights into
                    your financial habits.
                  </p>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="text-muted-foreground *:[a]:hover:text-foreground text-center text-xs text-balance mt-4">
          By signing in, you agree to our{' '}
          <a href="#" className="underline underline-offset-4">
            Terms of Service
          </a>{' '}
          and{' '}
          <a href="#" className="underline underline-offset-4">
            Privacy Policy
          </a>
          .
        </div>
      </div>
    </div>
  )
}
