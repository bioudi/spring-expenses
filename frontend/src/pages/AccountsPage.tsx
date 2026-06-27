import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogDescription, DialogFooter } from '@/components/ui/dialog'
import { AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription, AlertDialogFooter, AlertDialogAction, AlertDialogCancel } from '@/components/ui/alert-dialog'
import { api } from '@/lib/api'
import { formatMoney } from '@/lib/formatters'
import { useI18n } from '@/i18n'
import { toast } from 'sonner'
import type { AccountResponse, AccountRequest } from '@/types'

type AccountType = AccountRequest['type']

const ACCOUNT_TYPES: AccountType[] = ['BASE', 'SAVINGS', 'EMERGENCY', 'CREDIT']

const initialForm: AccountRequest = { name: '', type: 'BASE', balance: 0 }

export default function AccountsPage() {
  const { t } = useI18n()
  const [accounts, setAccounts] = useState<AccountResponse[]>([])
  const [loading, setLoading] = useState(true)

  // Modal state
  const [modalOpen, setModalOpen] = useState(false)
  const [editingId, setEditingId] = useState<string | null>(null)
  const [form, setForm] = useState<AccountRequest>(initialForm)
  const [saving, setSaving] = useState(false)

  // Delete confirmation
  const [deleteTarget, setDeleteTarget] = useState<AccountResponse | null>(null)
  const [deleting, setDeleting] = useState(false)

  useEffect(() => {
    loadAccounts()
  }, [])

  async function loadAccounts() {
    try {
      setLoading(true)
      const data = await api.getAccounts()
      setAccounts(data)
    } catch {
      toast.error(t('common.failedLoad') || 'Failed to load accounts')
    } finally {
      setLoading(false)
    }
  }

  function openAddModal() {
    setEditingId(null)
    setForm(initialForm)
    setModalOpen(true)
  }

  function openEditModal(account: AccountResponse) {
    setEditingId(account.id)
    setForm({ name: account.name, type: account.type, balance: account.balance })
    setModalOpen(true)
  }

  async function handleSave() {
    if (!form.name.trim()) {
      toast.error(t('accounts.name') + ' is required')
      return
    }
    try {
      setSaving(true)
      if (editingId) {
        await api.updateAccount(editingId, form)
        toast.success(t('accounts.accountUpdated'))
      } else {
        await api.createAccount(form)
        toast.success(t('accounts.accountCreated'))
      }
      setModalOpen(false)
      loadAccounts()
    } catch {
      toast.error(t('accounts.failedToSave'))
    } finally {
      setSaving(false)
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    try {
      setDeleting(true)
      await api.deleteAccount(deleteTarget.id)
      setDeleteTarget(null)
      toast.success(t('accounts.accountDeleted'))
      loadAccounts()
    } catch {
      toast.error(t('accounts.failedToDelete'))
    } finally {
      setDeleting(false)
    }
  }

  function typeBadge(type: AccountType) {
    const colorMap: Record<AccountType, string> = {
      BASE: '#22c55e',
      SAVINGS: '#3b82f6',
      EMERGENCY: '#f59e0b',
      CREDIT: '#ef4444',
    }
    const labelKey = `accounts.type${type.charAt(0) + type.slice(1).toLowerCase()}` as const
    return (
      <Badge variant="outline" className="font-normal">
        <div className="h-2 w-2 rounded-full mr-1.5 shrink-0" style={{ backgroundColor: colorMap[type] }} />
        {t(labelKey)}
      </Badge>
    )
  }

  const emptyRows = accounts.length === 0 && !loading

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      <div className="flex items-center justify-between px-4 lg:px-6">
        <div>
          <h1 className="text-2xl font-semibold">{t('accounts.title')}</h1>
          <p className="text-muted-foreground text-sm">
            {t('accounts.description')}
          </p>
        </div>
        <Button onClick={openAddModal}>
          <Plus className="h-4 w-4 mr-2" />
          {t('accounts.addAccount')}
        </Button>
      </div>

      {/* Desktop Table */}
      <div className="hidden md:block px-4 lg:px-6">
        <div className="rounded-lg border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('accounts.name')}</TableHead>
                <TableHead>{t('accounts.type')}</TableHead>
                <TableHead className="text-right">{t('accounts.balance')}</TableHead>
                <TableHead className="text-right w-[100px]">{t('common.actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {loading ? (
                <TableRow>
                  <TableCell colSpan={4} className="h-24 text-center text-muted-foreground">
                    {t('common.loading')}
                  </TableCell>
                </TableRow>
              ) : emptyRows ? (
                <TableRow>
                  <TableCell colSpan={4} className="h-24 text-center text-muted-foreground">
                    {t('accounts.noAccounts')}
                  </TableCell>
                </TableRow>
              ) : (
                accounts.map((a) => (
                  <TableRow key={a.id}>
                    <TableCell className="font-medium">{a.name}</TableCell>
                    <TableCell>{typeBadge(a.type)}</TableCell>
                    <TableCell className={`text-right font-mono tabular-nums ${a.type === 'CREDIT' ? 'text-destructive' : ''}`}>
                      {a.type === 'CREDIT' ? '-' : ''}{formatMoney(a.balance)}
                    </TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => openEditModal(a)}>
                          <Pencil className="h-3.5 w-3.5" />
                        </Button>
                        <Button size="icon" variant="ghost" className="h-8 w-8 text-muted-foreground hover:text-destructive" onClick={() => setDeleteTarget(a)}>
                          <Trash2 className="h-3.5 w-3.5" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </div>
      </div>

      {/* Mobile Cards */}
      <div className="md:hidden flex flex-col gap-3 px-4 lg:px-6">
        {loading ? (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            {t('common.loading')}
          </div>
        ) : emptyRows ? (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            {t('accounts.noAccounts')}
          </div>
        ) : (
          accounts.map((a) => (
            <Card key={a.id}>
              <CardContent className="p-4">
                <div className="flex justify-between items-start mb-2">
                  <span className="font-medium">{a.name}</span>
                  <span className={`font-mono text-sm tabular-nums ${a.type === 'CREDIT' ? 'text-destructive' : ''}`}>
                    {a.type === 'CREDIT' ? '-' : ''}{formatMoney(a.balance)}
                  </span>
                </div>
                <div className="flex justify-between items-center">
                  {typeBadge(a.type)}
                  <div className="flex gap-1">
                    <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => openEditModal(a)}>
                      <Pencil className="h-3.5 w-3.5" />
                    </Button>
                    <Button size="icon" variant="ghost" className="h-7 w-7 text-muted-foreground hover:text-destructive" onClick={() => setDeleteTarget(a)}>
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
              </CardContent>
            </Card>
          ))
        )}
      </div>

      {/* Add/Edit Modal */}
      <Dialog open={modalOpen} onOpenChange={(open) => { if (!open && !saving) setModalOpen(false) }}>
        <DialogContent onClose={() => { if (!saving) setModalOpen(false) }}>
          <DialogHeader>
            <DialogTitle>{editingId ? t('accounts.editAccount') : t('accounts.addAccount')}</DialogTitle>
            <DialogDescription>
              {editingId ? '' : t('accounts.addFirstAccount')}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-2">
              <label className="text-sm font-medium">{t('accounts.name')}</label>
              <Input
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                placeholder="e.g. Chequing"
              />
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">{t('accounts.type')}</label>
              <select
                value={form.type}
                onChange={(e) => setForm({ ...form, type: e.target.value as AccountType })}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                {ACCOUNT_TYPES.map((type) => (
                  <option key={type} value={type}>
                    {t(`accounts.type${type.charAt(0) + type.slice(1).toLowerCase()}`)}
                  </option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <label className="text-sm font-medium">{t('accounts.balance')}</label>
              <Input
                type="number"
                step="0.01"
                value={form.balance || ''}
                onChange={(e) => setForm({ ...form, balance: parseFloat(e.target.value) || 0 })}
                placeholder="0.00"
              />
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setModalOpen(false)} disabled={saving}>
              {t('common.cancel')}
            </Button>
            <Button onClick={handleSave} disabled={saving}>
              {saving ? t('common.loading') : t('common.save')}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('common.areYouSure')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('accounts.deleteConfirm', { name: deleteTarget?.name ?? '' })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteTarget(null)} disabled={deleting}>
              {t('common.cancel')}
            </AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete} disabled={deleting} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">
              {deleting ? t('common.loading') : t('common.delete')}
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}