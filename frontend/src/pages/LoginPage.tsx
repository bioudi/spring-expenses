import { useSearchParams, Link } from 'react-router-dom'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Wallet } from 'lucide-react'
import { useI18n } from '@/i18n'

export default function LoginPage() {
  const { t } = useI18n()
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
                  <h1 className="text-2xl font-bold">{t('login.welcomeBack')}</h1>
                  <p className="text-muted-foreground text-balance">
                    {t('login.signInTo')}
                  </p>
                </div>

                {hasError && (
                  <div className="rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 text-sm text-destructive">
                    {t('login.invalidCredentials')}
                  </div>
                )}
                {hasLogout && (
                  <div className="rounded-md border border-green-600/50 bg-green-500/10 px-4 py-3 text-sm text-green-700 dark:text-green-400">
                    {t('login.loggedOut')}
                  </div>
                )}
                {hasRegistered && (
                  <div className="rounded-md border border-green-600/50 bg-green-500/10 px-4 py-3 text-sm text-green-700 dark:text-green-400">
                    {t('login.accountCreated')}
                  </div>
                )}

                <form action="/login" method="POST">
                  <div className="flex flex-col gap-6">
                    <div className="grid gap-3">
                      <Label htmlFor="email">{t('login.email')}</Label>
                      <Input
                        id="email"
                        name="email"
                        type="email"
                        required
                        autoFocus
                        placeholder={t('login.placeholderEmail')}
                      />
                    </div>
                    <div className="grid gap-3">
                      <Label htmlFor="password">{t('login.password')}</Label>
                      <Input
                        id="password"
                        name="password"
                        type="password"
                        required
                        placeholder={t('login.placeholderPassword')}
                      />
                    </div>
                    <Button type="submit" className="w-full">
                      {t('login.signIn')}
                    </Button>
                  </div>
                </form>

                <div className="text-center text-sm text-muted-foreground">
                  {t('login.noAccount')}{' '}
                  <Link
                    to="/register"
                    className="underline underline-offset-4 hover:text-foreground"
                  >
                    {t('login.signUp')}
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
                    {t('login.sidebarText')}
                  </p>
                </div>
              </div>
            </div>
          </CardContent>
        </Card>

        <div className="text-muted-foreground *:[a]:hover:text-foreground text-center text-xs text-balance mt-4">
          {t('login.termsText')}{' '}
          <a href="#" className="underline underline-offset-4">
            {t('login.termsOfService')}
          </a>{' '}
          {t('login.and')}{' '}
          <a href="#" className="underline underline-offset-4">
            {t('login.privacyPolicy')}
          </a>
          .
        </div>
      </div>
    </div>
  )
}
