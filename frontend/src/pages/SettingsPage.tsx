import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Copy, RefreshCw } from 'lucide-react'
import { AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription, AlertDialogFooter, AlertDialogAction, AlertDialogCancel } from '@/components/ui/alert-dialog'
import { api } from '@/lib/api'
import { formatDate } from '@/lib/formatters'
import { toast } from 'sonner'
import type { UserProfile } from '@/types'

export default function SettingsPage() {
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
    toast.success('Copied to clipboard!')
  }

  async function regenerateApiKey() {
    setRegenOpen(false)
    try {
      const data = await api.regenerateApiKey()
      setProfile((p: UserProfile | null) => p ? { ...p, apiKey: data.apiKey } : p)
      toast.success('API key regenerated!')
    } catch {
      toast.error('Failed to regenerate API key')
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
      setPwError('New passwords do not match')
      return
    }

    if (newPassword.length < 8) {
      setPwError('Password must be at least 8 characters')
      return
    }

    try {
      await api.changePassword({ currentPassword, newPassword })
      setPwSuccess('Password updated successfully')
      ;(e.target as HTMLFormElement).reset()
    } catch (err) {
      setPwError(err instanceof Error ? err.message : 'Failed to update password')
    }
  }

  if (!profile) {
    return (
      <div className="flex items-center justify-center py-20">
        <div className="text-sm text-muted-foreground">Loading...</div>
      </div>
    )
  }

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      <div className="flex items-center justify-between px-4 lg:px-6">
        <div>
          <h1 className="text-2xl font-semibold">Settings</h1>
          <p className="text-muted-foreground text-sm">Manage your account preferences.</p>
        </div>
      </div>

      <div className="flex flex-col gap-4 px-4 lg:px-6 max-w-2xl md:gap-6">
        {/* Profile */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Profile</CardTitle>
            <CardDescription>Your account information.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="flex flex-col gap-1 sm:grid sm:grid-cols-[120px_1fr] sm:items-center sm:gap-4">
              <span className="text-xs sm:text-sm text-muted-foreground">Email</span>
              <span className="text-sm">{profile.email}</span>
            </div>
            <div className="flex flex-col gap-1 sm:grid sm:grid-cols-[120px_1fr] sm:items-center sm:gap-4">
              <span className="text-xs sm:text-sm text-muted-foreground">Display Name</span>
              <span className="text-sm">{profile.displayName || '—'}</span>
            </div>
            <div className="flex flex-col gap-1 sm:grid sm:grid-cols-[120px_1fr] sm:items-center sm:gap-4">
              <span className="text-xs sm:text-sm text-muted-foreground">Member Since</span>
              <span className="text-sm">{formatDate(profile.createdAt)}</span>
            </div>
          </CardContent>
        </Card>

        {/* API Key */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">API Key</CardTitle>
            <CardDescription>
              Use this key in the <code className="relative rounded bg-muted px-[0.3rem] py-[0.2rem] font-mono text-xs">X-API-Key</code> header for webhook requests.
            </CardDescription>
          </CardHeader>
          <CardContent>
            <div className="flex flex-col sm:flex-row gap-2">
              <code className="flex-1 rounded-md bg-muted px-3 py-2 text-sm font-mono break-all">
                {profile.apiKey}
              </code>
              <div className="flex gap-2">
                <Button variant="outline" size="sm" onClick={copyApiKey}>
                  <Copy className="h-3.5 w-3.5 mr-1.5" /> Copy
                </Button>
                <Button variant="outline" size="sm" onClick={() => setRegenOpen(true)}>
                  <RefreshCw className="h-3.5 w-3.5 mr-1.5" /> Regenerate
                </Button>
              </div>
            </div>
          </CardContent>
        </Card>

        {/* Change Password */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">Password</CardTitle>
            <CardDescription>Update your password.</CardDescription>
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
                <Label htmlFor="currentPassword">Current Password</Label>
                <Input id="currentPassword" name="currentPassword" type="password" required />
              </div>
              <div className="space-y-2">
                <Label htmlFor="newPassword">New Password</Label>
                <Input id="newPassword" name="newPassword" type="password" required minLength={8} />
              </div>
              <div className="space-y-2">
                <Label htmlFor="confirmNewPassword">Confirm New Password</Label>
                <Input id="confirmNewPassword" name="confirmNewPassword" type="password" required />
              </div>
              <Button type="submit" size="sm">Update Password</Button>
            </form>
          </CardContent>
        </Card>
      </div>

      {/* Regenerate Dialog */}
      <AlertDialog open={regenOpen} onOpenChange={setRegenOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Are you sure?</AlertDialogTitle>
            <AlertDialogDescription>
              This will invalidate your current API key. Any existing integrations using the current key will stop working.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setRegenOpen(false)}>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={regenerateApiKey} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              Regenerate
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
