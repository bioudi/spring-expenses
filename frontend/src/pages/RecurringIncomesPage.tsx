import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Plus, Pencil, Trash2, Pause, Play, ArrowLeftRight } from 'lucide-react'
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
import { formatMoney, formatDate, formatRecurrence, toISODate } from '@/lib/formatters'
import { useI18n } from '@/i18n'
import { toast } from 'sonner'
import type {
  RecurringIncomeResponse,
  RecurringIncomeRequest,
  RecurrenceFrequency,
  DayOfWeek,
  IncomeType,
  IncomeCategory,
  AccountResponse,
} from '@/types'

const INCOME_TYPES: { value: IncomeType; key: string }[] = [
  { value: 'CASH', key: 'incomeTypes.CASH' },
  { value: 'TRANSFER', key: 'incomeTypes.TRANSFER' },
]

const INCOME_CATEGORIES: { value: IncomeCategory; key: string }[] = [
  { value: 'PAYCHECK', key: 'incomeCategories.PAYCHECK' },
  { value: 'REFUND', key: 'incomeCategories.REFUND' },
  { value: 'TAX_RETURN', key: 'incomeCategories.TAX_RETURN' },
]

export default function RecurringIncomesPage() {
  const { t, language } = useI18n()
  const [items, setItems] = useState<RecurringIncomeResponse[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<RecurringIncomeResponse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<RecurringIncomeResponse | null>(null)
  const [loading, setLoading] = useState(true)

  const [amount, setAmount] = useState('')
  const [name, setName] = useState('')
  const [type, setType] = useState<IncomeType>('CASH')
  const [category, setCategory] = useState<IncomeCategory>('PAYCHECK')
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
    try {
      const data = await api.getRecurringIncomes()
      setItems(data)
    } catch {
      toast.error(t('common.noData'))
    } finally {
      setLoading(false)
    }
  }

  function openAdd() {
    setEditing(null)
    setAmount('')
    setName('')
    setType('CASH')
    setCategory('PAYCHECK')
    setAccountId('')
    setNotes('')
    setFrequency('MONTHLY')
    setDayOfWeek('')
    setDayOfMonth('')
    setStartDate(toISODate(new Date()))
    setEndDate('')
    setModalOpen(true)
  }

  function openEdit(item: RecurringIncomeResponse) {
    setEditing(item)
    setAmount(String(item.amount))
    setName(item.name)
    setType(item.type)
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

    const data: RecurringIncomeRequest = {
      name,
      amount: parseFloat(amount),
      type,
      category,
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
        await api.updateRecurringIncome(editing.id, data)
        toast.success(t('recurringIncome.updated'))
      } else {
        await api.createRecurringIncome(data)
        toast.success(t('recurringIncome.created'))
      }
      setModalOpen(false)
      loadItems()
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('recurringIncome.failedToSave'))
    }
  }

  async function handleToggle(item: RecurringIncomeResponse) {
    try {
      await api.toggleRecurringIncome(item.id)
      toast.success(item.active ? t('recurringIncome.pausedToast') : t('recurringIncome.resumedToast'))
      loadItems()
    } catch {
      toast.error(t('recurringIncome.failedToggle'))
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    try {
      await api.deleteRecurringIncome(deleteTarget.id)
      setDeleteTarget(null)
      loadItems()
      toast.success(t('recurringIncome.deleted'))
    } catch {
      toast.error(t('recurringIncome.failedDelete'))
    }
  }

  const showDayOfWeek = frequency === 'WEEKLY' || frequency === 'BI_WEEKLY'
  const showDayOfMonth = frequency === 'MONTHLY'

  const accountMap = new Map(accounts.map(a => [a.id, a.name]))

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      <div className="flex items-center justify-between px-4 lg:px-6">
        <div>
          <h1 className="text-2xl font-semibold">{t('recurringIncome.title')}</h1>
          <p className="text-muted-foreground text-sm">{t('recurringIncome.description')}</p>
        </div>
        <div className="flex items-center gap-2">
          <Button asChild variant="outline" size="sm">
            <Link to="/incomes">
              <ArrowLeftRight className="h-4 w-4 mr-2" /> {t('nav.incomes')}
            </Link>
          </Button>
          <Button onClick={openAdd} size="sm">
            <Plus className="h-4 w-4 mr-2" /> {t('recurringIncome.addRecurring')}
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">{t('common.loading')}</div>
      ) : items.length === 0 ? (
        <div className="px-4 lg:px-6">
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16">
              <p className="text-sm text-muted-foreground mb-4">{t('recurringIncome.noRecurring')}</p>
              <Button onClick={openAdd} variant="outline" size="sm">
                <Plus className="h-4 w-4 mr-2" /> {t('recurringIncome.addFirstRecurring')}
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
                    <TableHead>{t('recurringIncome.name')}</TableHead>
                    <TableHead>{t('recurringIncome.type')}</TableHead>
                    <TableHead>{t('recurringIncome.category')}</TableHead>
                    <TableHead className="text-right">{t('recurringIncome.amount')}</TableHead>
                    <TableHead>{t('recurringIncome.frequencyCol')}</TableHead>
                    <TableHead>{t('recurringIncome.next')}</TableHead>
                    <TableHead>{t('recurringIncome.status')}</TableHead>
                    <TableHead className="text-right w-[130px]">{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-medium">{item.name}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className="font-normal">
                          {t(`incomeTypes.${item.type}`)}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className="font-normal">
                          {t(`incomeCategories.${item.category}`)}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right font-medium text-green-600">{formatMoney(item.amount, language)}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {formatRecurrence(item.frequency, item.dayOfWeek, item.dayOfMonth, t, language)}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                        {formatDate(item.nextOccurrence, language)}
                      </TableCell>
                      <TableCell>
                        <Badge variant={item.active ? 'default' : 'secondary'}>
                          {item.active ? t('recurringIncome.active') : t('recurringIncome.paused')}
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
                      <p className="text-sm font-medium">{item.name}</p>
                      <p className="text-xs text-muted-foreground">
                        {formatRecurrence(item.frequency, item.dayOfWeek, item.dayOfMonth, t, language)}
                      </p>
                    </div>
                    <p className="text-sm font-bold text-green-600">{formatMoney(item.amount, language)}</p>
                  </div>
                  <div className="flex flex-wrap items-center gap-2 mb-2">
                    <Badge variant="outline" className="font-normal text-xs">
                      {t(`incomeTypes.${item.type}`)}
                    </Badge>
                    <Badge variant="outline" className="font-normal text-xs">
                      {t(`incomeCategories.${item.category}`)}
                    </Badge>
                    <Badge variant={item.active ? 'default' : 'secondary'} className="text-xs">
                      {item.active ? t('recurringIncome.active') : t('recurringIncome.paused')}
                    </Badge>
                  </div>
                  <div className="flex justify-between items-center">
                    <p className="text-xs text-muted-foreground">
                      {item.accountId
                        ? `${t('recurringIncome.account')}: ${accountMap.get(item.accountId) || item.accountId}`
                        : t('recurringIncome.nextLabel', { date: formatDate(item.nextOccurrence, language) })}
                    </p>
                    <div className="flex gap-1">
                      <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => openEdit(item)}><Pencil className="h-3.5 w-3.5" /></Button>
                      <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => handleToggle(item)}>
                        {item.active ? <Pause className="h-3.5 w-3.5" /> : <Play className="h-3.5 w-3.5" />}
                      </Button>
                      <Button size="icon" variant="ghost" className="h-7 w-7 text-muted-foreground hover:text-destructive" onClick={() => setDeleteTarget(item)}><Trash2 className="h-3.5 w-3.5" /></Button>
                    </div>
                  </div>
                  <p className="text-xs text-muted-foreground mt-2">
                    {t('recurringIncome.nextLabel', { date: formatDate(item.nextOccurrence, language) })}
                  </p>
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
            <DialogTitle>{editing ? t('recurringIncome.editRecurring') : t('recurringIncome.addRecurringTitle')}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('recurringIncome.name')}</Label>
                <Input value={name} onChange={(e) => setName(e.target.value)} required placeholder={t('recurringIncome.placeholderName')} />
              </div>
              <div className="space-y-2">
                <Label>{t('recurringIncome.amount')}</Label>
                <Input type="number" step="0.01" min="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required placeholder={t('recurringIncome.placeholderAmount')} />
              </div>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('recurringIncome.type')}</Label>
                <select
                  value={type}
                  onChange={(e) => setType(e.target.value as IncomeType)}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  {INCOME_TYPES.map((it) => (
                    <option key={it.value} value={it.value}>{t(it.key)}</option>
                  ))}
                </select>
              </div>
              <div className="space-y-2">
                <Label>{t('recurringIncome.category')}</Label>
                <select
                  value={category}
                  onChange={(e) => setCategory(e.target.value as IncomeCategory)}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  {INCOME_CATEGORIES.map((ic) => (
                    <option key={ic.value} value={ic.value}>{t(ic.key)}</option>
                  ))}
                </select>
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('recurringIncome.account')}</Label>
              <select
                value={accountId}
                onChange={(e) => setAccountId(e.target.value)}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <option value="">— {t('recurringIncome.noAccount')} —</option>
                {accounts.map((a) => (
                  <option key={a.id} value={a.id}>{a.name} ({t(`accountTypes.${a.type}`)})</option>
                ))}
              </select>
            </div>

            {/* Recurrence fields */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('recurringIncome.frequencyLabel')}</Label>
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
                  <Label>{t('recurringIncome.dayOfWeek')}</Label>
                  <select
                    value={dayOfWeek}
                    onChange={(e) => setDayOfWeek(e.target.value as DayOfWeek)}
                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  >
                    <option value="">{t('recurringIncome.any')}</option>
                    {DAYS_OF_WEEK_OPTIONS.map((d) => (
                      <option key={d.value} value={d.value}>{d.label}</option>
                    ))}
                  </select>
                </div>
              )}
              {showDayOfMonth && (
                <div className="space-y-2">
                  <Label>{t('recurringIncome.dayOfMonth')}</Label>
                  <Input type="number" min="1" max="31" value={dayOfMonth} onChange={(e) => setDayOfMonth(e.target.value)} placeholder={t('recurringIncome.placeholderDay')} />
                </div>
              )}
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('recurringIncome.startDate')}</Label>
                <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label>{t('recurringIncome.endDateOptional')}</Label>
                <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('recurringIncome.notes')}</Label>
              <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} placeholder={t('recurringIncome.placeholderNotes')} rows={2} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>{t('common.cancel')}</Button>
              <Button type="submit">{editing ? t('common.saveChanges') : t('recurringIncome.addRecurring')}</Button>
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
              {t('recurringIncome.deleteConfirm', { amount: formatMoney(deleteTarget?.amount ?? 0, language), name: deleteTarget?.name ?? '' })}
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