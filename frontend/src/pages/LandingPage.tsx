import { Link } from 'react-router-dom'
import { Button } from '@/components/ui/button'
import { useI18n } from '@/i18n'
import { useTheme } from '@/components/theme-provider'
import {
  Wallet,
  BarChart3,
  Bot,
  Bell,
  ShieldCheck,
  ArrowRight,
  Moon,
  Sun,
  Globe,
} from 'lucide-react'

export default function LandingPage() {
  const { t, language, setLanguage } = useI18n()
  const { theme, setTheme } = useTheme()
  const isDark =
    theme === 'dark' ||
    (theme === 'system' &&
      window.matchMedia('(prefers-color-scheme: dark)').matches)

  return (
    <div className="min-h-svh bg-background text-foreground">
      {/* Nav */}
      <header className="sticky top-0 z-40 border-b bg-background/80 backdrop-blur-sm">
        <div className="mx-auto flex h-14 max-w-5xl items-center justify-between px-4">
          <Link to="/" className="flex items-center gap-2 no-underline">
            <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-primary text-primary-foreground">
              <Wallet className="h-4 w-4" />
            </div>
            <span className="text-sm font-semibold tracking-tight">
              Spendifi
            </span>
          </Link>

          <div className="flex items-center gap-1">
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8"
              onClick={() => setLanguage(language === 'en' ? 'fr' : 'en')}
            >
              <Globe className="h-4 w-4" />
            </Button>
            <Button
              variant="ghost"
              size="icon"
              className="h-8 w-8"
              onClick={() => setTheme(isDark ? 'light' : 'dark')}
            >
              {isDark ? (
                <Sun className="h-4 w-4" />
              ) : (
                <Moon className="h-4 w-4" />
              )}
            </Button>
            <Link to="/login">
              <Button variant="ghost" size="sm">
                {t('landing.signIn')}
              </Button>
            </Link>
            <Link to="/register">
              <Button size="sm">{t('landing.getStarted')}</Button>
            </Link>
          </div>
        </div>
      </header>

      {/* Hero */}
      <section className="mx-auto max-w-5xl px-4 pt-20 pb-16 md:pt-28 md:pb-24">
        <div className="flex flex-col items-center text-center">
          <div className="mb-6 flex h-14 w-14 items-center justify-center rounded-2xl bg-primary text-primary-foreground">
            <Wallet className="h-7 w-7" />
          </div>
          <h1 className="text-4xl font-bold tracking-tight sm:text-5xl md:text-6xl">
            {t('landing.heroTitle')}
          </h1>
          <p className="mt-4 max-w-xl text-lg text-muted-foreground text-balance">
            {t('landing.heroSubtitle')}
          </p>
          <div className="mt-8 flex flex-col gap-3 sm:flex-row">
            <Link to="/register">
              <Button size="lg" className="gap-2">
                {t('landing.getStartedFree')}
                <ArrowRight className="h-4 w-4" />
              </Button>
            </Link>
            <Link to="/login">
              <Button variant="outline" size="lg">
                {t('landing.signIn')}
              </Button>
            </Link>
          </div>
        </div>
      </section>

      {/* Feature cards */}
      <section className="border-t bg-muted/50">
        <div className="mx-auto max-w-5xl px-4 py-16 md:py-24">
          <div className="mb-12 text-center">
            <h2 className="text-2xl font-bold tracking-tight sm:text-3xl">
              {t('landing.featuresTitle')}
            </h2>
            <p className="mt-2 text-muted-foreground">
              {t('landing.featuresSubtitle')}
            </p>
          </div>

          <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
            {[
              {
                icon: BarChart3,
                title: t('landing.featureDashboardTitle'),
                desc: t('landing.featureDashboardDesc'),
              },
              {
                icon: Bot,
                title: t('landing.featureAiTitle'),
                desc: t('landing.featureAiDesc'),
              },
              {
                icon: Bell,
                title: t('landing.featureRecurringTitle'),
                desc: t('landing.featureRecurringDesc'),
              },
              {
                icon: ShieldCheck,
                title: t('landing.featurePrivacyTitle'),
                desc: t('landing.featurePrivacyDesc'),
              },
            ].map((f) => (
              <div
                key={f.title}
                className="rounded-xl border bg-card p-6 transition-shadow hover:shadow-md"
              >
                <div className="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-primary/10 text-primary">
                  <f.icon className="h-5 w-5" />
                </div>
                <h3 className="font-semibold">{f.title}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{f.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* How it works */}
      <section className="border-t">
        <div className="mx-auto max-w-5xl px-4 py-16 md:py-24">
          <div className="mb-12 text-center">
            <h2 className="text-2xl font-bold tracking-tight sm:text-3xl">
              {t('landing.howTitle')}
            </h2>
          </div>

          <div className="grid gap-8 sm:grid-cols-3">
            {[
              {
                step: '1',
                title: t('landing.step1Title'),
                desc: t('landing.step1Desc'),
              },
              {
                step: '2',
                title: t('landing.step2Title'),
                desc: t('landing.step2Desc'),
              },
              {
                step: '3',
                title: t('landing.step3Title'),
                desc: t('landing.step3Desc'),
              },
            ].map((s) => (
              <div key={s.step} className="text-center">
                <div className="mx-auto mb-4 flex h-10 w-10 items-center justify-center rounded-full bg-primary text-primary-foreground font-bold">
                  {s.step}
                </div>
                <h3 className="font-semibold">{s.title}</h3>
                <p className="mt-1 text-sm text-muted-foreground">{s.desc}</p>
              </div>
            ))}
          </div>
        </div>
      </section>

      {/* CTA */}
      <section className="border-t bg-muted/50">
        <div className="mx-auto max-w-5xl px-4 py-16 md:py-24">
          <div className="flex flex-col items-center text-center">
            <h2 className="text-2xl font-bold tracking-tight sm:text-3xl">
              {t('landing.ctaTitle')}
            </h2>
            <p className="mt-2 max-w-md text-muted-foreground">
              {t('landing.ctaSubtitle')}
            </p>
            <Link to="/register" className="mt-6">
              <Button size="lg" className="gap-2">
                {t('landing.getStartedFree')}
                <ArrowRight className="h-4 w-4" />
              </Button>
            </Link>
          </div>
        </div>
      </section>

      {/* Footer */}
      <footer className="border-t">
        <div className="mx-auto flex max-w-5xl items-center justify-between px-4 py-6">
          <div className="flex items-center gap-2">
            <div className="flex h-5 w-5 items-center justify-center rounded bg-primary text-primary-foreground">
              <Wallet className="h-3 w-3" />
            </div>
            <span className="text-xs text-muted-foreground">Spendifi</span>
          </div>
          <p className="text-xs text-muted-foreground">
            {t('landing.footer')}
          </p>
        </div>
      </footer>
    </div>
  )
}
