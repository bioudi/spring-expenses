import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2, Pause, Play } from 'lucide-react'
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
import { formatMoney, formatDate, formatRecurrence } from '@/lib/formatters'
import { CATEGORY_COLORS, CATEGORY_GROUPS } from '@/lib/categories'
import { useI18n } from '@/i18n'
import { toast } from 'sonner'
import { toISODate } from '@/lib/formatters'
import type { RecurringExpenseResponse, RecurringExpenseRequest, RecurrenceFrequency, DayOfWeek, AccountResponse } from '@/types'

export default function RecurringExpensesPage() {
  const { t, tc, language } = useI18n()
  const [items, setItems] = useState<RecurringExpenseResponse[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<RecurringExpenseResponse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<RecurringExpenseResponse | null>(null)
  const [loading, setLoading] = useState(true)

  const [amount, setAmount] = useState('')
  const [merchant, setMerchant] = useState('')
  const [category, setCategory] = useState('')
  const [accountId, setAccountId] = useState('')
  const [accounts, setAccounts] = useState<AccountResponse[]>([])
  const [notes, setNotes] = useState('')
  const [frequency, setFrequency] = useState<RecurrenceFrequency>('MONTHLY')
  const [dayOfWeek, setDayOfWeek] = useState<DayOfWeek | ''>('')
  const [dayOfMonth, setDayOfMonth] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')

  const FREQUENCIES: { value: RecurrenceFrequency; label: string }[] = [
    { value: 'DAILY', label: t('frequency.daily') },
    { value: 'WEEKLY', label: t('frequency.weekly') },
    { value: 'BI_WEEKLY', label: t('frequency.biWeekly') },
    { value: 'MONTHLY', label: t('frequency.monthly') },
  ]

  const DAYS_OF_WEEK_OPTIONS: { value: DayOfWeek; label: string }[] = [
    { value: 'MONDAY', label: t('frequency.days.MONDAY') },
    { value: 'TUESDAY', label: t('frequency.days.TUESDAY') },
    { value: 'WEDNESDAY', label: t('frequency.days.WEDNESDAY') },
    { value: 'THURSDAY', label: t('frequency.days.THURSDAY') },
    { value: 'FRIDAY', label: t('frequency.days.FRIDAY') },
    { value: 'SATURDAY', label: t('frequency.days.SATURDAY') },
    { value: 'SUNDAY', label: t('frequency.days.SUNDAY') },
  ]

  useEffect(() => {
    loadItems()
    api.getAccounts().then(setAccounts).catch(() => {})
  }, [])

  async function loadItems() {
    setLoading(true)
    const data = await api.getRecurringExpenses()
    setItems(data)
    setLoading(false)
  }

  function openAdd() {
    setEditing(null)
    setAmount('')
    setMerchant('')
    setCategory('')
    setAccountId('')
    setNotes('')
    setFrequency('MONTHLY')
    setDayOfWeek('')
    setDayOfMonth('')
    setStartDate(toISODate(new Date()))
    setEndDate('')
    setModalOpen(true)
  }

  function openEdit(item: RecurringExpenseResponse) {
    setEditing(item)
    setAmount(String(item.amount))
    setMerchant(item.merchant)
    setCategory(item.category)
    setAccountId(item.accountId || '')
    setNotes(item.notes || '')
    setFrequency(item.frequency)
    setDayOfWeek(item.dayOfWeek || '')
    setDayOfMonth(item.dayOfMonth ? String(item.dayOfMonth) : '')
    setStartDate(item.startDate)
    setEndDate(item.endDate || '')
    setModalOpen(true)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()

    const data: RecurringExpenseRequest = {
      amount: parseFloat(amount),
      merchant,
      category: category || undefined,
      notes: notes || null,
      frequency,
      dayOfWeek: (frequency === 'WEEKLY' || frequency === 'BI_WEEKLY') && dayOfWeek ? dayOfWeek as DayOfWeek : null,
      dayOfMonth: frequency === 'MONTHLY' && dayOfMonth ? parseInt(dayOfMonth) : null,
      startDate,
      endDate: endDate || null,
      accountId: accountId || null,
    }

    try {
      if (editing) {
        await api.updateRecurringExpense(editing.id, data)
        toast.success(t('recurring.updated'))
      } else {
        await api.createRecurringExpense(data)
        toast.success(t('recurring.created'))
      }
      setModalOpen(false)
      loadItems()
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('recurring.failedToSave'))
    }
  }

  async function handleToggle(item: RecurringExpenseResponse) {
    try {
      await api.toggleRecurringExpense(item.id)
      toast.success(item.active ? t('recurring.pausedToast') : t('recurring.resumedToast'))
      loadItems()
    } catch {
      toast.error(t('recurring.failedToggle'))
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    try {
      await api.deleteRecurringExpense(deleteTarget.id)
      setDeleteTarget(null)
      loadItems()
      toast.success(t('recurring.deleted'))
    } catch {
      toast.error(t('recurring.failedDelete'))
    }
  }

  const showDayOfWeek = frequency === 'WEEKLY' || frequency === 'BI_WEEKLY'
  const showDayOfMonth = frequency === 'MONTHLY'

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      <div className="flex items-center justify-between px-4 lg:px-6">
        <div>
          <h1 className="text-2xl font-semibold">{t('recurring.title')}</h1>
          <p className="text-muted-foreground text-sm">{t('recurring.description')}</p>
        </div>
        <Button onClick={openAdd} size="sm">
          <Plus className="h-4 w-4 mr-2" /> {t('recurring.addRecurring')}
        </Button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">{t('common.loading')}</div>
      ) : items.length === 0 ? (
        <div className="px-4 lg:px-6">
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16">
              <p className="text-sm text-muted-foreground mb-4">{t('recurring.noRecurring')}</p>
              <Button onClick={openAdd} variant="outline" size="sm">
                <Plus className="h-4 w-4 mr-2" /> {t('recurring.addFirstRecurring')}
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
                    <TableHead>{t('recurring.merchant')}</TableHead>
                    <TableHead>{t('recurring.category')}</TableHead>
                    <TableHead className="text-right">{t('recurring.amount')}</TableHead>
                    <TableHead>{t('recurring.frequencyCol')}</TableHead>
                    <TableHead>{t('recurring.next')}</TableHead>
                    <TableHead>{t('recurring.status')}</TableHead>
                    <TableHead className="text-right w-[130px]">{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-medium">{item.merchant}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className="font-normal">
                          <div className="h-2 w-2 rounded-full mr-1.5 shrink-0" style={{ backgroundColor: CATEGORY_COLORS[item.category] || '#27272a' }} />
                          {tc(item.category)}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right font-medium">{formatMoney(item.amount, language)}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {formatRecurrence(item.frequency, item.dayOfWeek, item.dayOfMonth, t, language)}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                        {formatDate(item.nextOccurrence, language)}
                      </TableCell>
                      <TableCell>
                        <Badge variant={item.active ? 'default' : 'secondary'}>
                          {item.active ? t('recurring.active') : t('recurring.paused')}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => openEdit(item)}>
                            <Pencil className="h-3.5 w-3.5" />
                          </Button>
                          <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => handleToggle(item)}>
                            {item.active ? <Pause className="h-3.5 w-3.5" /> : <Play className="h-3.5 w-3.5" />}
                          </Button>
                          <Button size="icon" variant="ghost" className="h-8 w-8 text-muted-foreground hover:text-destructive" onClick={() => setDeleteTarget(item)}>
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
            {items.map((item) => (
              <Card key={item.id}>
                <CardContent className="p-4">
                  <div className="flex justify-between items-start mb-2">
                    <div>
                      <p className="text-sm font-medium">{item.merchant}</p>
                      <p className="text-xs text-muted-foreground">
                        {formatRecurrence(item.frequency, item.dayOfWeek, item.dayOfMonth, t, language)}
                      </p>
                    </div>
                    <p className="text-sm font-bold">{formatMoney(item.amount, language)}</p>
                  </div>
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-2">
                      <Badge variant="outline" className="font-normal text-xs">
                        <div className="h-1.5 w-1.5 rounded-full mr-1 shrink-0" style={{ backgroundColor: CATEGORY_COLORS[item.category] || '#27272a' }} />
                        {tc(item.category)}
                      </Badge>
                      <Badge variant={item.active ? 'default' : 'secondary'} className="text-xs">
                        {item.active ? t('recurring.active') : t('recurring.paused')}
                      </Badge>
                    </div>
                    <div className="flex gap-1">
                      <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => openEdit(item)}><Pencil className="h-3.5 w-3.5" /></Button>
                      <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => handleToggle(item)}>
                        {item.active ? <Pause className="h-3.5 w-3.5" /> : <Play className="h-3.5 w-3.5" />}
                      </Button>
                      <Button size="icon" variant="ghost" className="h-7 w-7 text-muted-foreground hover:text-destructive" onClick={() => setDeleteTarget(item)}><Trash2 className="h-3.5 w-3.5" /></Button>
                    </div>
                  </div>
                  <p className="text-xs text-muted-foreground mt-2">{t('recurring.nextLabel', { date: formatDate(item.nextOccurrence, language) })}</p>
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
            <DialogTitle>{editing ? t('recurring.editRecurring') : t('recurring.addRecurringTitle')}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('recurring.amount')}</Label>
                <Input type="number" step="0.01" min="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required placeholder={t('recurring.placeholderAmount')} />
              </div>
              <div className="space-y-2">
                <Label>{t('recurring.merchant')}</Label>
                <Input value={merchant} onChange={(e) => setMerchant(e.target.value)} required placeholder={t('recurring.placeholderMerchant')} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('recurring.category')}</Label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <option value="">{t('recurring.autoDetect')}</option>
                {CATEGORY_GROUPS.map((g) => (
                  <optgroup key={g.label} label={t(`categoryGroups.${g.label}`)}>
                    {g.categories.map((c) => (
                      <option key={c} value={c}>{tc(c)}</option>
                    ))}
                  </optgroup>
                ))}
              </select>
            </div>
            <div className="space-y-2">
              <Label>{t('recurring.account')}</Label>
              <select
                value={accountId}
                onChange={(e) => setAccountId(e.target.value)}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <option value="">— {t('recurring.noAccount')} —</option>
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.name} ({t(`accountTypes.${a.type}`)})</option>
                ))}
              </select>
            </div>

            {/* Recurrence fields */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('recurring.frequencyLabel')}</Label>
                <select
                  value={frequency}
                  onChange={(e) => setFrequency(e.target.value as RecurrenceFrequency)}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  {FREQUENCIES.map((f) => (
                    <option key={f.value} value={f.value}>{f.label}</option>
                  ))}
                </select>
              </div>
              {showDayOfWeek && (
                <div className="space-y-2">
                  <Label>{t('recurring.dayOfWeek')}</Label>
                  <select
                    value={dayOfWeek}
                    onChange={(e) => setDayOfWeek(e.target.value as DayOfWeek)}
                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  >
                    <option value="">{t('recurring.any')}</option>
                    {DAYS_OF_WEEK_OPTIONS.map((d) => (
                      <option key={d.value} value={d.value}>{d.label}</option>
                    ))}
                  </select>
                </div>
              )}
              {showDayOfMonth && (
                <div className="space-y-2">
                  <Label>{t('recurring.dayOfMonth')}</Label>
                  <Input type="number" min="1" max="31" value={dayOfMonth} onChange={(e) => setDayOfMonth(e.target.value)} placeholder={t('recurring.placeholderDay')} />
                </div>
              )}
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('recurring.startDate')}</Label>
                <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label>{t('recurring.endDateOptional')}</Label>
                <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('recurring.notes')}</Label>
              <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} placeholder={t('recurring.placeholderNotes')} rows={2} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>{t('common.cancel')}</Button>
              <Button type="submit">{editing ? t('common.saveChanges') : t('recurring.addRecurring')}</Button>
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
              {t('recurring.deleteConfirm', { amount: formatMoney(deleteTarget?.amount ?? 0, language), merchant: deleteTarget?.merchant ?? '' })}
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
