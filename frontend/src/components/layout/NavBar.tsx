import { NavLink, useLocation } from 'react-router-dom'
import { cn } from '@/lib/utils'
import { LayoutDashboard, Receipt, Store, Settings, LogOut, Wallet, Moon, Sun, Menu, X, Repeat, Target, Globe, Landmark } from 'lucide-react'
import { useTheme } from '@/components/theme-provider'
import { useI18n } from '@/i18n'
import { Button } from '@/components/ui/button'
import { useState, useEffect } from 'react'

export default function NavBar() {
  const { theme, setTheme } = useTheme()
  const { t, language, setLanguage } = useI18n()
  const [mobileOpen, setMobileOpen] = useState(false)
  const location = useLocation()

  const navItems = [
    { to: '/dashboard', label: t('nav.dashboard'), icon: LayoutDashboard },
    { to: '/expenses', label: t('nav.expenses'), icon: Receipt },
    { to: '/accounts', label: t('nav.accounts'), icon: Landmark },
    { to: '/recurring', label: t('nav.recurring'), icon: Repeat },
    { to: '/budgets', label: t('nav.budgets'), icon: Target },
    { to: '/merchants', label: t('nav.merchants'), icon: Store },
    { to: '/settings', label: t('nav.settings'), icon: Settings },
  ]

  // Close mobile nav on route change
  useEffect(() => {
    setMobileOpen(false)
  }, [location.pathname])

  // Prevent body scroll when mobile menu is open
  useEffect(() => {
    if (mobileOpen) {
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
    }
    return () => { document.body.style.overflow = '' }
  }, [mobileOpen])

  function toggleTheme() {
    if (theme === 'dark') {
      setTheme('light')
    } else {
      setTheme('dark')
    }
  }

  function toggleLanguage() {
    setLanguage(language === 'en' ? 'fr' : 'en')
  }

  function handleLogout() {
    const form = document.createElement('form')
    form.method = 'POST'
    form.action = '/logout'
    document.body.appendChild(form)
    form.submit()
  }

  const isDark = theme === 'dark' || (theme === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)

  const sidebarContent = (
    <>
      {/* Brand */}
      <div className="flex h-14 items-center gap-2 border-b px-4">
        <div className="flex h-6 w-6 items-center justify-center rounded-md bg-primary text-primary-foreground">
          <Wallet className="h-3.5 w-3.5" />
        </div>
        <span className="text-sm font-semibold tracking-tight text-sidebar-foreground">
          Spendifi
        </span>
      </div>

      {/* Nav Links */}
      <nav className="flex-1 space-y-1 px-2 py-3">
        {navItems.map((item) => (
          <NavLink
            key={item.to}
            to={item.to}
            end={item.to === '/dashboard'}
            className={({ isActive }) =>
              cn(
                'flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors no-underline',
                isActive
                  ? 'bg-sidebar-accent text-sidebar-accent-foreground'
                  : 'text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground'
              )
            }
          >
            <item.icon className="h-4 w-4" />
            {item.label}
          </NavLink>
        ))}
      </nav>

      {/* Bottom section */}
      <div className="border-t p-2 space-y-1">
        <Button
          variant="ghost"
          onClick={toggleLanguage}
          className="flex w-full items-center gap-3 justify-start px-3 py-2 h-auto text-sm font-medium text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
        >
          <Globe className="h-4 w-4" />
          {language === 'en' ? 'Français' : 'English'}
        </Button>
        <Button
          variant="ghost"
          onClick={toggleTheme}
          className="flex w-full items-center gap-3 justify-start px-3 py-2 h-auto text-sm font-medium text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
        >
          {isDark ? <Sun className="h-4 w-4" /> : <Moon className="h-4 w-4" />}
          {isDark ? t('nav.lightMode') : t('nav.darkMode')}
        </Button>
        <button
          onClick={handleLogout}
          className="flex w-full items-center gap-3 rounded-md px-3 py-2 text-sm font-medium text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-accent-foreground"
        >
          <LogOut className="h-4 w-4" />
          {t('nav.logout')}
        </button>
      </div>
    </>
  )

  return (
    <>
      {/* Mobile top bar */}
      <div className="sticky top-0 z-40 flex h-14 items-center gap-3 border-b bg-sidebar px-4 md:hidden">
        <Button
          variant="ghost"
          size="icon"
          className="h-8 w-8 text-sidebar-foreground"
          onClick={() => setMobileOpen(true)}
        >
          <Menu className="h-5 w-5" />
        </Button>
        <div className="flex items-center gap-2">
          <div className="flex h-6 w-6 items-center justify-center rounded-md bg-primary text-primary-foreground">
            <Wallet className="h-3.5 w-3.5" />
          </div>
          <span className="text-sm font-semibold tracking-tight text-sidebar-foreground">
            Spendifi
          </span>
        </div>
      </div>

      {/* Mobile drawer overlay */}
      {mobileOpen && (
        <div
          className="fixed inset-0 z-50 bg-black/50 md:hidden"
          onClick={() => setMobileOpen(false)}
        />
      )}

      {/* Mobile drawer */}
      <aside
        className={cn(
          'fixed inset-y-0 left-0 z-50 flex w-64 flex-col bg-sidebar transition-transform duration-200 ease-in-out md:hidden',
          mobileOpen ? 'translate-x-0' : '-translate-x-full'
        )}
      >
        <Button
          variant="ghost"
          size="icon"
          className="absolute right-2 top-3 h-8 w-8 text-sidebar-foreground"
          onClick={() => setMobileOpen(false)}
        >
          <X className="h-5 w-5" />
        </Button>
        {sidebarContent}
      </aside>

      {/* Desktop sidebar — hidden on mobile */}
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-56 flex-col border-r bg-sidebar md:flex">
        {sidebarContent}
      </aside>
    </>
  )
}
