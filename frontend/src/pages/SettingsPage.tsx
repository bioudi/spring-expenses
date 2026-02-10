import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Copy, RefreshCw } from 'lucide-react'
import { AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription, AlertDialogFooter, AlertDialogAction, AlertDialogCancel } from '@/components/ui/alert-dialog'
import { api } from '@/lib/api'
import { formatDate } from '@/lib/formatters'
import { useI18n } from '@/i18n'
import { toast } from 'sonner'
import type { UserProfile } from '@/types'

export default function SettingsPage() {
  const { t, language, setLanguage } = useI18n()
  const [profile, setProfile] = useState<UserProfile | null>(null)
  const [regenOpen, setRegenOpen] = useState(false)
  const [pwError, setPwError] = useState('')
  const [pwSuccess, setPwSuccess] = useState('')

  useEffect(() => {
    api.getProfile().then(setProfile)
  }, [])

  function copyApiKey() {
    if (!profile) return
    navigator.clipboard.writeText(profile.apiKey)
    toast.success(t('settings.copied'))
  }

  async function regenerateApiKey() {
    setRegenOpen(false)
    try {
      const data = await api.regenerateApiKey()
      setProfile((p: UserProfile | null) => p ? { ...p, apiKey: data.apiKey } : p)
      toast.success(t('settings.apiKeyRegenerated'))
    } catch {
      toast.error(t('settings.failedRegenerate'))
    }
  }

  async function handlePasswordChange(e: React.FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setPwError('')
    setPwSuccess('')

    const form = new FormData(e.currentTarget)
    const currentPassword = form.get('currentPassword') as string
    const newPassword = form.get('newPassword') as string
    const confirmNewPassword = form.get('confirmNewPassword') as string

    if (newPassword !== confirmNewPassword) {
      setPwError(t('settings.passwordsNoMatch'))
      return
    }

    if (newPassword.length < 8) {
      setPwError(t('settings.passwordMinLength'))
      return
    }

    try {
      await api.changePassword({ currentPassword, newPassword })
      setPwSuccess(t('settings.passwordUpdated'))
      ;(e.target as HTMLFormElement).reset()
    } catch (err) {
      setPwError(err instanceof Error ? err.message : t('settings.failedPassword'))
    }
  }

  if (!profile) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-sm text-muted-foreground">{t('common.loading')}</div>
      </div>
    )
  }

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      <div className="flex items-center justify-between px-4 lg:px-6">
        <div>
          <h1 className="text-2xl font-semibold">{t('settings.title')}</h1>
          <p className="text-muted-foreground text-sm">{t('settings.description')}</p>
        </div>
      </div>

      <div className="flex flex-col gap-4 px-4 lg:px-6 max-w-2xl md:gap-6">
        {/* Language */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t('settings.language')}</CardTitle>
            <CardDescription>{t('settings.languageDescription')}</CardDescription>
          </CardHeader>
          <CardContent>
            <select
              value={language}
              onChange={(e) => setLanguage(e.target.value as 'en' | 'fr')}
              className="flex h-9 w-full max-w-xs rounded-md border border-input bg-transparent px-3 py-1 text-sm shadow-sm transition-colors focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
            >
              <option value="en">English</option>
              <option value="fr">Français</option>
            </select>
          </CardContent>
        </Card>

        {/* Profile */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t('settings.profile')}</CardTitle>
            <CardDescription>{t('settings.profileDescription')}</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-col gap-1 sm:grid sm:grid-cols-[120px_1fr] sm:items-center sm:gap-4">
              <span className="text-xs sm:text-sm text-muted-foreground">{t('settings.email')}</span>
              <span className="text-sm">{profile.email}</span>
            </div>
            <div className="flex flex-col gap-1 sm:grid sm:grid-cols-[120px_1fr] sm:items-center sm:gap-4">
              <span className="text-xs sm:text-sm text-muted-foreground">{t('settings.displayName')}</span>
              <span className="text-sm">{profile.displayName || '—'}</span>
            </div>
            <div className="flex flex-col gap-1 sm:grid sm:grid-cols-[120px_1fr] sm:items-center sm:gap-4">
              <span className="text-xs sm:text-sm text-muted-foreground">{t('settings.memberSince')}</span>
              <span className="text-sm">{formatDate(profile.createdAt, language)}</span>
            </div>
          </CardContent>
        </Card>

        {/* API Key */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t('settings.apiKey')}</CardTitle>
            <CardDescription>
              {t('settings.apiKeyDescription', { code: 'X-API-Key' })}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col sm:flex-row gap-2">
              <code className="flex-1 rounded-md bg-muted px-3 py-2 text-sm font-mono break-all">
                {profile.apiKey}
              </code>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={copyApiKey}>
                  <Copy className="h-3.5 w-3.5 mr-1.5" /> {t('settings.copy')}
                </Button>
                <Button variant="outline" size="sm" onClick={() => setRegenOpen(true)}>
                  <RefreshCw className="h-3.5 w-3.5 mr-1.5" /> {t('settings.regenerate')}
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Change Password */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t('settings.password')}</CardTitle>
            <CardDescription>{t('settings.passwordDescription')}</CardDescription>
          </CardHeader>
          <CardContent>
            {pwError && (
              <div className="rounded-md border border-destructive/50 bg-destructive/10 px-4 py-3 mb-4 text-sm text-destructive">
                {pwError}
              </div>
            )}
            {pwSuccess && (
              <div className="rounded-md border border-green-600/50 bg-green-500/10 px-4 py-3 mb-4 text-sm text-green-700 dark:text-green-400">
                {pwSuccess}
              </div>
            )}
            <form onSubmit={handlePasswordChange} className="space-y-4">
              <div className="space-y-2">
                <Label htmlFor="currentPassword">{t('settings.currentPassword')}</Label>
                <Input id="currentPassword" name="currentPassword" type="password" required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="newPassword">{t('settings.newPassword')}</Label>
                <Input id="newPassword" name="newPassword" type="password" required minLength={8} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirmNewPassword">{t('settings.confirmNewPassword')}</Label>
                <Input id="confirmNewPassword" name="confirmNewPassword" type="password" required />
              </div>
              <Button type="submit" size="sm">{t('settings.updatePassword')}</Button>
            </form>
          </CardContent>
        </Card>
      </div>

      {/* Regenerate Dialog */}
      <AlertDialog open={regenOpen} onOpenChange={setRegenOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('common.areYouSure')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('settings.regenConfirm')}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setRegenOpen(false)}>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction onClick={regenerateApiKey} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              {t('settings.regenerate')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
