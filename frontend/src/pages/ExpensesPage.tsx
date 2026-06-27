import { useEffect, useState, useMemo } from 'react'
import { Plus, Pencil, Trash2, Repeat, Filter, ChevronLeft, ChevronRight, X, Download } from 'lucide-react'
import { startOfMonth, endOfMonth, addMonths, format, isSameMonth } from 'date-fns'
import { fr as frLocale, enUS } from 'date-fns/locale'
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
import { formatMoney, formatDateTime, toLocalDateTimeInput, toISODate } from '@/lib/formatters'
import { CATEGORY_COLORS, CATEGORY_GROUPS, PAYMENT_METHODS } from '@/lib/categories'
import { downloadCsv } from '@/lib/csv'
import { useI18n } from '@/i18n'
import { toast } from 'sonner'
import type { ExpenseResponse, ExpenseRequest, RecurrenceFrequency, DayOfWeek, AccountResponse } from '@/types'

export default function ExpensesPage() {
  const { t, tc, language } = useI18n()
  const [expenses, setExpenses] = useState<ExpenseResponse[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ExpenseResponse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<ExpenseResponse | null>(null)
  const [loading, setLoading] = useState(true)

  // Month navigation
  const [currentMonth, setCurrentMonth] = useState(() => startOfMonth(new Date()))
  const isCurrentMonth = isSameMonth(currentMonth, new Date())

  // Filter state
  const [showFilters, setShowFilters] = useState(false)
  const [filterMerchant, setFilterMerchant] = useState('')
  const [filterMinAmount, setFilterMinAmount] = useState('')
  const [filterMaxAmount, setFilterMaxAmount] = useState('')
  const [filterCardName, setFilterCardName] = useState('')
  const [filterPaymentMethod, setFilterPaymentMethod] = useState('')
  const [filterCategory, setFilterCategory] = useState('')
  const [filterNote, setFilterNote] = useState('')

  const activeFilterCount = [filterMerchant, filterMinAmount, filterMaxAmount, filterCardName, filterPaymentMethod, filterCategory, filterNote].filter(Boolean).length

  const [recurringTarget, setRecurringTarget] = useState<ExpenseResponse | null>(null)
  const [recFrequency, setRecFrequency] = useState<RecurrenceFrequency>('MONTHLY')
  const [recDayOfWeek, setRecDayOfWeek] = useState<DayOfWeek | ''>('')
  const [recDayOfMonth, setRecDayOfMonth] = useState('')
  const [recStartDate, setRecStartDate] = useState('')
  const [recEndDate, setRecEndDate] = useState('')

  const [amount, setAmount] = useState('')
  const [merchant, setMerchant] = useState('')
  const [category, setCategory] = useState('')
  const [paymentMethod, setPaymentMethod] = useState('Card')
  const [cardName, setCardName] = useState('')
  const [timestamp, setTimestamp] = useState('')
  const [notes, setNotes] = useState('')

  const [accounts, setAccounts] = useState<AccountResponse[]>([])
  const [accountId, setAccountId] = useState('')

  const FREQUENCIES: { value: RecurrenceFrequency; label: string }[] = [
    { value: 'DAILY', label: t('frequency.daily') },
    { value: 'WEEKLY', label: t('frequency.weekly') },
    { value: 'BI_WEEKLY', label: t('frequency.biWeekly') },
    { value: 'MONTHLY', label: t('frequency.monthly') },
  ]

  const DAYS_OF_WEEK: { value: DayOfWeek; label: string }[] = [
    { value: 'MONDAY', label: t('frequency.days.MONDAY') },
    { value: 'TUESDAY', label: t('frequency.days.TUESDAY') },
    { value: 'WEDNESDAY', label: t('frequency.days.WEDNESDAY') },
    { value: 'THURSDAY', label: t('frequency.days.THURSDAY') },
    { value: 'FRIDAY', label: t('frequency.days.FRIDAY') },
    { value: 'SATURDAY', label: t('frequency.days.SATURDAY') },
    { value: 'SUNDAY', label: t('frequency.days.SUNDAY') },
  ]

  useEffect(() => {
    loadExpenses()
  }, [currentMonth])

  useEffect(() => {
    api.getAccounts().then(setAccounts).catch(() => {})
  }, [])

  async function loadExpenses() {
    setLoading(true)
    const startDate = toISODate(startOfMonth(currentMonth))
    const endDate = toISODate(endOfMonth(currentMonth))
    const data = await api.getExpenses({ startDate, endDate })
    setExpenses(data)
    setLoading(false)
  }

  const filteredExpenses = useMemo(() => {
    return expenses.filter((exp) => {
      if (filterMerchant && !exp.merchant.toLowerCase().includes(filterMerchant.toLowerCase())) return false
      if (filterMinAmount && exp.amount < parseFloat(filterMinAmount)) return false
      if (filterMaxAmount && exp.amount > parseFloat(filterMaxAmount)) return false
      if (filterCardName && !(exp.cardName || '').toLowerCase().includes(filterCardName.toLowerCase())) return false
      if (filterPaymentMethod && exp.paymentMethod !== filterPaymentMethod) return false
      if (filterCategory && exp.category !== filterCategory) return false
      if (filterNote && !(exp.notes || '').toLowerCase().includes(filterNote.toLowerCase())) return false
      return true
    })
  }, [expenses, filterMerchant, filterMinAmount, filterMaxAmount, filterCardName, filterPaymentMethod, filterCategory, filterNote])

  function clearFilters() {
    setFilterMerchant('')
    setFilterMinAmount('')
    setFilterMaxAmount('')
    setFilterCardName('')
    setFilterPaymentMethod('')
    setFilterCategory('')
    setFilterNote('')
  }

  function handleExportCsv() {
    const headers = ['Date', 'Merchant', 'Category', 'Amount', 'Payment Method', 'Card', 'Notes']
    const rows = filteredExpenses.map(exp => [
      format(new Date(exp.timestamp), 'yyyy-MM-dd HH:mm'),
      exp.merchant,
      exp.category,
      String(exp.amount),
      exp.paymentMethod || '',
      exp.cardName || '',
      exp.notes || '',
    ])
    downloadCsv(`expenses-${format(currentMonth, 'yyyy-MM')}.csv`, headers, rows)
  }

  const monthLabel = format(currentMonth, 'MMMM yyyy', { locale: language === 'fr' ? frLocale : enUS })

  function openAdd() {
    setEditing(null)
    setAmount('')
    setMerchant('')
    setCategory('')
    setPaymentMethod('Card')
    setCardName('')
    setTimestamp(toLocalDateTimeInput())
    setNotes('')
    setAccountId('')
    setModalOpen(true)
  }

  function openEdit(exp: ExpenseResponse) {
    setEditing(exp)
    setAmount(String(exp.amount))
    setMerchant(exp.merchant)
    setCategory(exp.category)
    setPaymentMethod(exp.paymentMethod || 'Card')
    setCardName(exp.cardName || '')
    setTimestamp(toLocalDateTimeInput(new Date(exp.timestamp)))
    setNotes(exp.notes || '')
    setAccountId(exp.accountId || '')
    setModalOpen(true)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()

    const data: ExpenseRequest = {
      amount: parseFloat(amount),
      merchant,
      category: category || undefined,
      paymentMethod,
      cardName: (paymentMethod === 'Card' || paymentMethod === 'Debit') ? cardName : null,
      timestamp: timestamp || null,
      notes: notes || null,
      accountId: accountId || null,
    }

    try {
      if (editing) {
        await api.updateExpense(editing.id, data)
        toast.success(t('expenses.expenseUpdated'))
      } else {
        await api.createExpense(data)
        toast.success(t('expenses.expenseCreated'))
      }
      setModalOpen(false)
      loadExpenses()
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('expenses.failedToSave'))
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    try {
      await api.deleteExpense(deleteTarget.id)
      setDeleteTarget(null)
      loadExpenses()
      toast.success(t('expenses.expenseDeleted'))
    } catch {
      toast.error(t('expenses.failedToDelete'))
    }
  }

  function openMakeRecurring(exp: ExpenseResponse) {
    setRecurringTarget(exp)
    setRecFrequency('MONTHLY')
    setRecDayOfWeek('')
    setRecDayOfMonth('')
    setRecStartDate(toISODate(new Date()))
    setRecEndDate('')
  }

  async function handleMakeRecurring(e: React.FormEvent) {
    e.preventDefault()
    if (!recurringTarget) return
    try {
      await api.createRecurringFromExpense(recurringTarget.id, {
        amount: recurringTarget.amount,
        merchant: recurringTarget.merchant,
        frequency: recFrequency,
        dayOfWeek: (recFrequency === 'WEEKLY' || recFrequency === 'BI_WEEKLY') && recDayOfWeek ? recDayOfWeek as DayOfWeek : null,
        dayOfMonth: recFrequency === 'MONTHLY' && recDayOfMonth ? parseInt(recDayOfMonth) : null,
        startDate: recStartDate,
        endDate: recEndDate || null,
      })
      setRecurringTarget(null)
      loadExpenses()
      toast.success(t('expenses.recurringCreated'))
    } catch (err) {
      toast.error(err instanceof Error ? err.message : t('expenses.failedRecurring'))
    }
  }

  const showCardName = paymentMethod === 'Card' || paymentMethod === 'Debit'

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      <div className="flex items-center justify-between px-4 lg:px-6">
        <div>
          <h1 className="text-2xl font-semibold">{t('expenses.title')}</h1>
          <p className="text-muted-foreground text-sm">{t('expenses.description')}</p>
        </div>
        <Button onClick={openAdd} size="sm">
          <Plus className="h-4 w-4 mr-2" /> {t('expenses.addExpense')}
        </Button>
      </div>

      {/* Month Navigation */}
      <div className="flex items-center justify-end gap-2 px-4 lg:px-6">
        <Button variant="outline" size="icon" className="h-8 w-8" onClick={() => setCurrentMonth(m => addMonths(m, -1))}>
          <ChevronLeft className="h-4 w-4" />
        </Button>
        <span className="text-sm font-medium capitalize">{monthLabel}</span>
        <Button variant="outline" size="icon" className="h-8 w-8" onClick={() => setCurrentMonth(m => addMonths(m, 1))} disabled={isCurrentMonth}>
          <ChevronRight className="h-4 w-4" />
        </Button>
      </div>

      {/* Filter Controls */}
      <div className="flex items-center justify-between px-4 lg:px-6">
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => setShowFilters(f => !f)}>
            <Filter className="h-4 w-4 mr-2" />
            {t('expenses.filters')}
            {activeFilterCount > 0 && (
              <Badge className="ml-1.5 h-5 w-5 rounded-full p-0 flex items-center justify-center text-xs">{activeFilterCount}</Badge>
            )}
          </Button>
          {activeFilterCount > 0 && (
            <Button variant="ghost" size="sm" onClick={clearFilters}>
              <X className="h-4 w-4 mr-1" />
              {t('expenses.clearFilters')}
            </Button>
          )}
        </div>
        <div className="flex items-center gap-2">
          {expenses.length > 0 && (
            <span className="text-sm text-muted-foreground">
              {t('expenses.showingCount', { count: filteredExpenses.length, total: expenses.length })}
            </span>
          )}
          <Button variant="outline" size="sm" onClick={handleExportCsv} disabled={filteredExpenses.length === 0}>
            <Download className="h-4 w-4 mr-2" />
            {t('expenses.exportCsv')}
          </Button>
        </div>
      </div>

      {/* Filter Panel */}
      {showFilters && (
        <div className="px-4 lg:px-6">
          <Card>
            <CardContent className="p-4">
              <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-3">
                <Input
                  placeholder={t('expenses.filterMerchantPlaceholder')}
                  value={filterMerchant}
                  onChange={(e) => setFilterMerchant(e.target.value)}
                />
                <Input
                  type="number"
                  step="0.01"
                  placeholder={t('expenses.minAmount')}
                  value={filterMinAmount}
                  onChange={(e) => setFilterMinAmount(e.target.value)}
                />
                <Input
                  type="number"
                  step="0.01"
                  placeholder={t('expenses.maxAmount')}
                  value={filterMaxAmount}
                  onChange={(e) => setFilterMaxAmount(e.target.value)}
                />
                <Input
                  placeholder={t('expenses.filterCardPlaceholder')}
                  value={filterCardName}
                  onChange={(e) => setFilterCardName(e.target.value)}
                />
                <select
                  value={filterPaymentMethod}
                  onChange={(e) => setFilterPaymentMethod(e.target.value)}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  <option value="">{t('expenses.allPayments')}</option>
                  {PAYMENT_METHODS.map((m) => (
                    <option key={m} value={m}>{t(`payment.${m}`)}</option>
                  ))}
                </select>
                <select
                  value={filterCategory}
                  onChange={(e) => setFilterCategory(e.target.value)}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  <option value="">{t('expenses.allCategories')}</option>
                  {CATEGORY_GROUPS.map((g) => (
                    <optgroup key={g.label} label={t(`categoryGroups.${g.label}`)}>
                      {g.categories.map((c) => (
                        <option key={c} value={c}>{tc(c)}</option>
                      ))}
                    </optgroup>
                  ))}
                </select>
                <Input
                  placeholder={t('expenses.filterNotesPlaceholder')}
                  value={filterNote}
                  onChange={(e) => setFilterNote(e.target.value)}
                />
              </div>
            </CardContent>
          </Card>
        </div>
      )}

      {loading ? (
        <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">{t('expenses.loadingExpenses')}</div>
      ) : expenses.length === 0 ? (
        <div className="px-4 lg:px-6">
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16">
              <p className="text-sm text-muted-foreground mb-4">{t('expenses.noExpenses')}</p>
              <Button onClick={openAdd} variant="outline" size="sm">
                <Plus className="h-4 w-4 mr-2" /> {t('expenses.addFirstExpense')}
              </Button>
            </CardContent>
          </Card>
        </div>
      ) : filteredExpenses.length === 0 ? (
        <div className="px-4 lg:px-6">
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16">
              <p className="text-sm text-muted-foreground">{t('expenses.noMatchingExpenses')}</p>
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
                    <TableHead>{t('expenses.date')}</TableHead>
                    <TableHead>{t('expenses.merchant')}</TableHead>
                    <TableHead>{t('expenses.category')}</TableHead>
                    <TableHead className="text-right">{t('expenses.amount')}</TableHead>
                    <TableHead>{t('expenses.paymentCol')}</TableHead>
                    <TableHead>{t('expenses.notes')}</TableHead>
                    <TableHead className="text-right w-[100px]">{t('common.actions')}</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {filteredExpenses.map((exp) => (
                    <TableRow key={exp.id}>
                      <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                        {formatDateTime(exp.timestamp, language)}
                      </TableCell>
                      <TableCell className="font-medium">
                        <span className="inline-flex items-center gap-1.5">
                          {exp.merchant}
                          {exp.recurringExpenseId && <span title={t('expenses.makeRecurring')}><Repeat className="h-3 w-3 text-muted-foreground" /></span>}
                        </span>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className="font-normal">
                          <div className="h-2 w-2 rounded-full mr-1.5 shrink-0" style={{ backgroundColor: CATEGORY_COLORS[exp.category] || '#27272a' }} />
                          {tc(exp.category)}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right font-medium">{formatMoney(exp.amount, language)}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {t(`payment.${exp.paymentMethod}`)}{exp.cardName ? ` · ${exp.cardName}` : ''}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground max-w-[200px] truncate">{exp.notes}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => openEdit(exp)}>
                            <Pencil className="h-3.5 w-3.5" />
                          </Button>
                          {!exp.recurringExpenseId && (
                            <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => openMakeRecurring(exp)} title={t('expenses.makeRecurring')}>
                              <Repeat className="h-3.5 w-3.5" />
                            </Button>
                          )}
                          <Button size="icon" variant="ghost" className="h-8 w-8 text-muted-foreground hover:text-destructive" onClick={() => setDeleteTarget(exp)}>
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
            {filteredExpenses.map((exp) => (
              <Card key={exp.id}>
                <CardContent className="p-4">
                  <div className="flex justify-between items-start mb-2">
                    <div>
                      <p className="text-sm font-medium inline-flex items-center gap-1.5">
                        {exp.merchant}
                        {exp.recurringExpenseId && <Repeat className="h-3 w-3 text-muted-foreground" />}
                      </p>
                      <p className="text-xs text-muted-foreground">{formatDateTime(exp.timestamp, language)}</p>
                    </div>
                    <p className="text-sm font-bold">{formatMoney(exp.amount, language)}</p>
                  </div>
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-2">
                      <Badge variant="outline" className="font-normal text-xs">
                        <div className="h-1.5 w-1.5 rounded-full mr-1 shrink-0" style={{ backgroundColor: CATEGORY_COLORS[exp.category] || '#27272a' }} />
                        {tc(exp.category)}
                      </Badge>
                      <span className="text-xs text-muted-foreground">{t(`payment.${exp.paymentMethod}`)}</span>
                    </div>
                    <div className="flex gap-1">
                      <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => openEdit(exp)}><Pencil className="h-3.5 w-3.5" /></Button>
                      {!exp.recurringExpenseId && (
                        <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => openMakeRecurring(exp)}><Repeat className="h-3.5 w-3.5" /></Button>
                      )}
                      <Button size="icon" variant="ghost" className="h-7 w-7 text-muted-foreground hover:text-destructive" onClick={() => setDeleteTarget(exp)}><Trash2 className="h-3.5 w-3.5" /></Button>
                    </div>
                  </div>
                  {exp.notes && <p className="text-xs text-muted-foreground mt-2 truncate">{exp.notes}</p>}
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
            <DialogTitle>{editing ? t('expenses.editExpense') : t('expenses.addExpense')}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('expenses.amount')}</Label>
                <Input type="number" step="0.01" min="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required placeholder={t('expenses.placeholderAmount')} />
              </div>
              <div className="space-y-2">
                <Label>{t('expenses.merchant')}</Label>
                <Input value={merchant} onChange={(e) => setMerchant(e.target.value)} required placeholder={t('expenses.placeholderMerchant')} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>{t('expenses.category')}</Label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <option value="">{t('expenses.autoDetect')}</option>
                {CATEGORY_GROUPS.map((g) => (
                  <optgroup key={g.label} label={t(`categoryGroups.${g.label}`)}>
                    {g.categories.map((c) => (
                      <option key={c} value={c}>{tc(c)}</option>
                    ))}
                  </optgroup>
                ))}
              </select>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('expenses.paymentMethod')}</Label>
                <select
                  value={paymentMethod}
                  onChange={(e) => setPaymentMethod(e.target.value)}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  {PAYMENT_METHODS.map((m) => (
                    <option key={m} value={m}>{t(`payment.${m}`)}</option>
                  ))}
                </select>
              </div>
              {showCardName && (
                <div className="space-y-2">
                  <Label>{t('expenses.cardName')}</Label>
                  <Input value={cardName} onChange={(e) => setCardName(e.target.value)} placeholder={t('expenses.placeholderCard')} />
                </div>
              )}
            </div>
            <div className="space-y-2">
              <Label>{t('expenses.dateTime')}</Label>
              <Input type="datetime-local" value={timestamp} onChange={(e) => setTimestamp(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>{t('expenses.notes')}</Label>
              <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} placeholder={t('expenses.placeholderNotes')} rows={2} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>{t('common.cancel')}</Button>
              <Button type="submit">{editing ? t('common.saveChanges') : t('expenses.addExpense')}</Button>
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
              {t('expenses.deleteConfirm', { amount: formatMoney(deleteTarget?.amount ?? 0, language), merchant: deleteTarget?.merchant ?? '' })}
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteTarget(null)}>{t('common.cancel')}</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">{t('common.delete')}</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Make Recurring Dialog */}
      <Dialog open={!!recurringTarget} onOpenChange={(open) => { if (!open) setRecurringTarget(null) }}>
        <DialogContent onClose={() => setRecurringTarget(null)}>
          <DialogHeader>
            <DialogTitle>{t('expenses.makeRecurring')}</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            {t('expenses.makeRecurringDesc', { merchant: recurringTarget?.merchant ?? '', amount: formatMoney(recurringTarget?.amount ?? 0, language) })}
          </p>
          <form onSubmit={handleMakeRecurring} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('expenses.frequencyLabel')}</Label>
                <select
                  value={recFrequency}
                  onChange={(e) => setRecFrequency(e.target.value as RecurrenceFrequency)}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  {FREQUENCIES.map((f) => (
                    <option key={f.value} value={f.value}>{f.label}</option>
                  ))}
                </select>
              </div>
              {(recFrequency === 'WEEKLY' || recFrequency === 'BI_WEEKLY') && (
                <div className="space-y-2">
                  <Label>{t('expenses.dayOfWeek')}</Label>
                  <select
                    value={recDayOfWeek}
                    onChange={(e) => setRecDayOfWeek(e.target.value as DayOfWeek)}
                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  >
                    <option value="">{t('expenses.any')}</option>
                    {DAYS_OF_WEEK.map((d) => (
                      <option key={d.value} value={d.value}>{d.label}</option>
                    ))}
                  </select>
                </div>
              )}
              {recFrequency === 'MONTHLY' && (
                <div className="space-y-2">
                  <Label>{t('expenses.dayOfMonth')}</Label>
                  <Input type="number" min="1" max="31" value={recDayOfMonth} onChange={(e) => setRecDayOfMonth(e.target.value)} placeholder={t('expenses.placeholderDay')} />
                </div>
              )}
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>{t('expenses.startDate')}</Label>
                <Input type="date" value={recStartDate} onChange={(e) => setRecStartDate(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label>{t('expenses.endDateOptional')}</Label>
                <Input type="date" value={recEndDate} onChange={(e) => setRecEndDate(e.target.value)} />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setRecurringTarget(null)}>{t('common.cancel')}</Button>
              <Button type="submit">{t('expenses.createRecurring')}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
