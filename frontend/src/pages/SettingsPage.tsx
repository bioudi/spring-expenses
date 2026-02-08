import { useEffect, useState } from 'react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Copy, RefreshCw, AlertCircle, CheckCircle2 } from 'lucide-react'
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
        <div className="text-muted-foreground">Loading...</div>
      </div>
    )
  }

  return (
    <div className="max-w-[700px] mx-auto p-6 space-y-6">
      <h1 className="text-2xl font-bold">Settings</h1>

      {/* Profile */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Profile</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="flex justify-between items-center py-2 border-b border-border">
            <span className="text-sm text-muted-foreground">Email</span>
            <span className="text-sm">{profile.email}</span>
          </div>
          <div className="flex justify-between items-center py-2 border-b border-border">
            <span className="text-sm text-muted-foreground">Display Name</span>
            <span className="text-sm">{profile.displayName || 'Not set'}</span>
          </div>
          <div className="flex justify-between items-center py-2">
            <span className="text-sm text-muted-foreground">Member Since</span>
            <span className="text-sm">{formatDate(profile.createdAt)}</span>
          </div>
        </CardContent>
      </Card>

      {/* API Key */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">API Key</CardTitle>
          <CardDescription>
            Use this key in the <code className="bg-background px-1.5 py-0.5 rounded text-xs">X-API-Key</code> header for webhook requests.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <div className="flex flex-col sm:flex-row gap-2">
            <code className="flex-1 bg-background border rounded-md px-3 py-2 text-sm font-mono break-all">
              {profile.apiKey}
            </code>
            <div className="flex gap-2">
              <Button variant="outline" size="sm" onClick={copyApiKey}>
                <Copy className="h-4 w-4 mr-1" /> Copy
              </Button>
              <Button variant="outline" size="sm" className="text-red-400 border-destructive hover:bg-destructive/15" onClick={() => setRegenOpen(true)}>
                <RefreshCw className="h-4 w-4 mr-1" /> Regenerate
              </Button>
            </div>
          </div>
        </CardContent>
      </Card>

      {/* Change Password */}
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Change Password</CardTitle>
        </CardHeader>
        <CardContent>
          {pwError && (
            <div className="flex items-center gap-2 p-3 rounded-lg mb-4 bg-destructive/15 border border-destructive text-red-400 text-sm">
              <AlertCircle className="h-4 w-4 shrink-0" />
              {pwError}
            </div>
          )}
          {pwSuccess && (
            <div className="flex items-center gap-2 p-3 rounded-lg mb-4 bg-green-500/15 border border-green-600 text-green-400 text-sm">
              <CheckCircle2 className="h-4 w-4 shrink-0" />
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
            <Button type="submit">Update Password</Button>
          </form>
        </CardContent>
      </Card>

      {/* Regenerate Dialog */}
      <AlertDialog open={regenOpen} onOpenChange={setRegenOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Regenerate API Key?</AlertDialogTitle>
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
