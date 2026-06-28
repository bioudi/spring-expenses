import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { format } from 'date-fns'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription, AlertDialogFooter, AlertDialogAction, AlertDialogCancel } from '@/components/ui/alert-dialog'
import { api } from '@/lib/api'
import { formatMoney, toLocalDateTimeInput } from '@/lib/formatters'
import { useI18n } from '@/i18n'
import { toast } from 'sonner'
import type { IncomeResponse, IncomeRequest, IncomeType, IncomeCategory, AccountResponse } from '@/types'

const INCOME_TYPES: { value: IncomeType; label: string }[] = [
  { value: 'CASH', label: 'Cash' },
  { value: 'TRANSFER', label: 'Transfer' },
]

const INCOME_CATEGORIES: { value: IncomeCategory; label: string }[] = [
  { value: 'PAYCHECK', label: 'Paycheck' },
  { value: 'REFUND', label: 'Refund' },
  { value: 'TAX_RETURN', label: 'Tax Return' },
]

export default function IncomesPage() {
  const { t } = useI18n()
  const [incomes, setIncomes] = useState<IncomeResponse[]>([])
  const [accounts, setAccounts] = useState<AccountResponse[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<IncomeResponse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<IncomeResponse | null>(null)
  const [loading, setLoading] = useState(true)

  const [name, setName] = useState('')
  const [amount, setAmount] = useState('')
  const [type, setType] = useState<IncomeType>('CASH')
  const [category, setCategory] = useState<IncomeCategory>('PAYCHECK')
  const [accountId, setAccountId] = useState('')
  const [timestamp, setTimestamp] = useState('')
  const [notes, setNotes] = useState('')

  useEffect(() => {
    loadIncomes()
    loadAccounts()
  }, [])

  async function loadIncomes() {
    setLoading(true)
    try {
      const data = await api.getIncomes()
      setIncomes(data)
    } catch {
      toast.error(t('incomes.failedToLoad'))
    } finally {
      setLoading(false)
    }
  }

  async function loadAccounts() {
    try {
      const data = await api.getAccounts()
      setAccounts(data)
    } catch {
      // accounts are optional, ignore
    }
  }

  function openAdd() {
    setEditing(null)
    setName('')
    setAmount('')
    setType('CASH')
    setCategory('PAYCHECK')
    setAccountId('')
    setTimestamp(toLocalDateTimeInput())
    setNotes('')
    setModalOpen(true)
  }

  function openEdit(inc: IncomeResponse) {
    setEditing(inc)
    setName(inc.name)
    setAmount(String(inc.amount))
    setType(inc.type)
    setCategory(inc.category)
    setAccountId(inc.accountId || '')
    setTimestamp(toLocalDateTimeInput(new Date(inc.timestamp)))
    setNotes(inc.notes || '')
    setModalOpen(true)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()

    const data: IncomeRequest = {
      name,
      amount: parseFloat(amount),
      type,
      category,
      accountId: accountId || null,
      timestamp: timestamp || null,
      notes: notes || null,
    }

    try {
      if (editing) {
        await api.updateIncome(editing.id, data)
        toast.success(t('incomes.incomeUpdated'))
      } else {
        await api.createIncome(data)
        toast.success(t('incomes.incomeCreated'))
      }
      setModalOpen(false)
      loadIncomes()
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('incomes.failedToSave'))
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    try {
      await api.deleteIncome(deleteTarget.id)
      setDeleteTarget(null)
      loadIncomes()
      toast.success(t('incomes.incomeDeleted'))
    } catch {
      toast.error(t('incomes.failedToDelete'))
    }
  }

  function handleDeleteClick(
    e: React.MouseEvent<HTMLButtonElement>,
    inc: IncomeResponse,
  ) {
    // Prevent the click from bubbling up to ancestors that may have
    // their own handlers (e.g. future row-level click navigation).
    e.stopPropagation()
    setDeleteTarget(inc)
  }

  function handleEditClick(
    e: React.MouseEvent<HTMLButtonElement>,
    inc: IncomeResponse,
  ) {
    e.stopPropagation()
    openEdit(inc)
  }

  const accountMap = new Map(accounts.map(a => [a.id, a.name]))

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      <div className="flex items-center justify-between px-4 lg:px-6">
        <div>
          <h1 className="text-2xl font-semibold">{t('incomes.title')}</h1>
          <p className="text-muted-foreground text-sm">{t('incomes.description')}</p>
        </div>
        <Button onClick={openAdd} size="sm">
          <Plus className="h-4 w-4 mr-2" /> {t('incomes.addIncome')}
        </Button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">{t('common.loading')}</div>
      ) : incomes.length === 0 ? (
        <div className="px-4 lg:px-6">
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16">
              <p className="text-sm text-muted-foreground mb-4">{t('incomes.noIncomes')}</p>
              <Button onClick={openAdd} variant="outline" size="sm">
                <Plus className="h-4 w-4 mr-2" /> {t('incomes.addFirstIncome')}
              </Button>
            </CardContent>
          </Card>
        </div>
      ) : (
        <>
          {/* Desktop Table */}
          <div className="hidden md:block px-4 lg:px-6">
            <div className="rounded-lg border bg-card">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>{t('incomes.name')}</TableHead>
                    <TableHead className="text-right">{t('incomes.amount')}</TableHead>
                    <TableHead>{t('incomes.type')}</TableHead>
                    <TableHead>{t('incomes.category')}</TableHead>
                    <TableHead>{t('incomes.account')}</TableHead>
                    <TableHead>{t('incomes.date')}</TableHead>
                    <TableHead className="text-right w-[100px]">{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {incomes.map((inc) => (
                    <TableRow key={inc.id}>
                      <TableCell className="font-medium">{inc.name}</TableCell>
                      <TableCell className="text-right font-medium">
                        <span className="text-green-600">{formatMoney(inc.amount)}</span>
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">{inc.type}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className="font-normal">{inc.category}</Badge>
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {inc.accountId ? (accountMap.get(inc.accountId) || inc.accountId) : '-'}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                        {format(new Date(inc.timestamp), 'MMM d, yyyy')}
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button size="icon" variant="ghost" className="h-8 w-8" onClick={(e) => handleEditClick(e, inc)}>
                            <Pencil className="h-3.5 w-3.5" />
                          </Button>
                          <Button size="icon" variant="ghost" className="h-8 w-8 text-muted-foreground hover:text-destructive" onClick={(e) => handleDeleteClick(e, inc)}>
                            <Trash2 className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </div>
          </div>

          {/* Mobile Cards */}
          <div className="md:hidden flex flex-col gap-3 px-4 lg:px-6">
            {incomes.map((inc) => (
              <Card key={inc.id}>
                <CardContent className="p-4">
                  <div className="flex justify-between items-start mb-2">
                    <div>
                      <p className="text-sm font-medium">{inc.name}</p>
                      <p className="text-xs text-muted-foreground">{format(new Date(inc.timestamp), 'MMM d, yyyy')}</p>
                    </div>
                    <p className="text-sm font-bold text-green-600">{formatMoney(inc.amount)}</p>
                  </div>
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-2">
                      <Badge variant="outline" className="font-normal text-xs">{inc.category}</Badge>
                      <span className="text-xs text-muted-foreground">{inc.type}</span>
                      {inc.accountId && <span className="text-xs text-muted-foreground">· {accountMap.get(inc.accountId) || inc.accountId}</span>}
                    </div>
                    <div className="flex gap-1">
                      <Button size="icon" variant="ghost" className="h-7 w-7" onClick={(e) => handleEditClick(e, inc)}><Pencil className="h-3.5 w-3.5" /></Button>
                      <Button size="icon" variant="ghost" className="h-7 w-7 text-muted-foreground hover:text-destructive" onClick={(e) => handleDeleteClick(e, inc)}><Trash2 className="h-3.5 w-3.5" /></Button>
                    </div>
                  </div>
                  {inc.notes && <p className="text-xs text-muted-foreground mt-2 truncate">{inc.notes}</p>}
                </CardContent>
              </Card>
            ))}
          </div>
        </>
      )}

      {/* Add/Edit Dialog */}
      <Dialog open={modalOpen} onOpenChange={setModalOpen}>
        <DialogContent onClose={() => setModalOpen(false)}>
          <DialogHeader>
            <DialogTitle>{editing ? t('incomes.editIncome') : t('incomes.addIncome')}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('incomes.name')}</Label>
                <Input value={name} onChange={(e) => setName(e.target.value)} required placeholder={t('incomes.placeholderName')} />
              </div>
              <div className="space-y-2">
                <Label>{t('incomes.amount')}</Label>
                <Input type="number" step="0.01" min="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required placeholder={t('incomes.placeholderAmount')} />
              </div>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('incomes.type')}</Label>
                <select
                  value={type}
                  onChange={(e) => setType(e.target.value as IncomeType)}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  {INCOME_TYPES.map((it) => (
                    <option key={it.value} value={it.value}>{it.label}</option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label>{t('incomes.category')}</Label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value as IncomeCategory)}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  {INCOME_CATEGORIES.map((ic) => (
                    <option key={ic.value} value={ic.value}>{ic.label}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('incomes.account')}</Label>
              <select
                value={accountId}
                onChange={(e) => setAccountId(e.target.value)}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <option value="">{t('incomes.noAccount')}</option>
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.name}</option>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label>{t('incomes.date')}</Label>
              <Input type="datetime-local" value={timestamp} onChange={(e) => setTimestamp(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>{t('incomes.notes')}</Label>
              <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} placeholder={t('incomes.placeholderNotes')} rows={2} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>{t('common.cancel')}</Button>
              <Button type="submit">{editing ? t('common.saveChanges') : t('incomes.addIncome')}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('common.areYouSure')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('incomes.deleteConfirm', { name: deleteTarget?.name ?? '', amount: formatMoney(deleteTarget?.amount ?? 0) })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteTarget(null)}>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">{t('common.delete')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
