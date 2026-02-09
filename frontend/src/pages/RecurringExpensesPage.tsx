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
import { CATEGORY_COLORS, CATEGORY_GROUPS, PAYMENT_METHODS } from '@/lib/categories'
import { toast } from 'sonner'
import { toISODate } from '@/lib/formatters'
import type { RecurringExpenseResponse, RecurringExpenseRequest, RecurrenceFrequency, DayOfWeek } from '@/types'

const FREQUENCIES: { value: RecurrenceFrequency; label: string }[] = [
  { value: 'DAILY', label: 'Daily' },
  { value: 'WEEKLY', label: 'Weekly' },
  { value: 'BI_WEEKLY', label: 'Bi-weekly' },
  { value: 'MONTHLY', label: 'Monthly' },
]

const DAYS_OF_WEEK: { value: DayOfWeek; label: string }[] = [
  { value: 'MONDAY', label: 'Monday' },
  { value: 'TUESDAY', label: 'Tuesday' },
  { value: 'WEDNESDAY', label: 'Wednesday' },
  { value: 'THURSDAY', label: 'Thursday' },
  { value: 'FRIDAY', label: 'Friday' },
  { value: 'SATURDAY', label: 'Saturday' },
  { value: 'SUNDAY', label: 'Sunday' },
]

export default function RecurringExpensesPage() {
  const [items, setItems] = useState<RecurringExpenseResponse[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<RecurringExpenseResponse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<RecurringExpenseResponse | null>(null)
  const [loading, setLoading] = useState(true)

  const [amount, setAmount] = useState('')
  const [merchant, setMerchant] = useState('')
  const [category, setCategory] = useState('')
  const [paymentMethod, setPaymentMethod] = useState('Card')
  const [cardName, setCardName] = useState('')
  const [notes, setNotes] = useState('')
  const [frequency, setFrequency] = useState<RecurrenceFrequency>('MONTHLY')
  const [dayOfWeek, setDayOfWeek] = useState<DayOfWeek | ''>('')
  const [dayOfMonth, setDayOfMonth] = useState('')
  const [startDate, setStartDate] = useState('')
  const [endDate, setEndDate] = useState('')

  useEffect(() => { loadItems() }, [])

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
    setPaymentMethod('Card')
    setCardName('')
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
    setPaymentMethod(item.paymentMethod || 'Card')
    setCardName(item.cardName || '')
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
      paymentMethod,
      cardName: (paymentMethod === 'Card' || paymentMethod === 'Debit') ? cardName : null,
      notes: notes || null,
      frequency,
      dayOfWeek: (frequency === 'WEEKLY' || frequency === 'BI_WEEKLY') && dayOfWeek ? dayOfWeek as DayOfWeek : null,
      dayOfMonth: frequency === 'MONTHLY' && dayOfMonth ? parseInt(dayOfMonth) : null,
      startDate,
      endDate: endDate || null,
    }

    try {
      if (editing) {
        await api.updateRecurringExpense(editing.id, data)
        toast.success('Recurring expense updated')
      } else {
        await api.createRecurringExpense(data)
        toast.success('Recurring expense created')
      }
      setModalOpen(false)
      loadItems()
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to save')
    }
  }

  async function handleToggle(item: RecurringExpenseResponse) {
    try {
      await api.toggleRecurringExpense(item.id)
      toast.success(item.active ? 'Paused' : 'Resumed')
      loadItems()
    } catch {
      toast.error('Failed to toggle')
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    try {
      await api.deleteRecurringExpense(deleteTarget.id)
      setDeleteTarget(null)
      loadItems()
      toast.success('Recurring expense deleted')
    } catch {
      toast.error('Failed to delete')
    }
  }

  const showCardName = paymentMethod === 'Card' || paymentMethod === 'Debit'
  const showDayOfWeek = frequency === 'WEEKLY' || frequency === 'BI_WEEKLY'
  const showDayOfMonth = frequency === 'MONTHLY'

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      <div className="flex items-center justify-between px-4 lg:px-6">
        <div>
          <h1 className="text-2xl font-semibold">Recurring Expenses</h1>
          <p className="text-muted-foreground text-sm">Manage your recurring expenses and subscriptions.</p>
        </div>
        <Button onClick={openAdd} size="sm">
          <Plus className="h-4 w-4 mr-2" /> Add Recurring
        </Button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">Loading...</div>
      ) : items.length === 0 ? (
        <div className="px-4 lg:px-6">
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16">
              <p className="text-sm text-muted-foreground mb-4">No recurring expenses yet.</p>
              <Button onClick={openAdd} variant="outline" size="sm">
                <Plus className="h-4 w-4 mr-2" /> Add your first recurring expense
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
                    <TableHead>Merchant</TableHead>
                    <TableHead>Category</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                    <TableHead>Frequency</TableHead>
                    <TableHead>Next</TableHead>
                    <TableHead>Status</TableHead>
                    <TableHead className="text-right w-[130px]">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {items.map((item) => (
                    <TableRow key={item.id}>
                      <TableCell className="font-medium">{item.merchant}</TableCell>
                      <TableCell>
                        <Badge variant="outline" className="font-normal">
                          <div className="h-2 w-2 rounded-full mr-1.5 shrink-0" style={{ backgroundColor: CATEGORY_COLORS[item.category] || '#27272a' }} />
                          {item.category}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right font-medium">{formatMoney(item.amount)}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {formatRecurrence(item.frequency, item.dayOfWeek, item.dayOfMonth)}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                        {formatDate(item.nextOccurrence)}
                      </TableCell>
                      <TableCell>
                        <Badge variant={item.active ? 'default' : 'secondary'}>
                          {item.active ? 'Active' : 'Paused'}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => openEdit(item)} title="Edit">
                            <Pencil className="h-3.5 w-3.5" />
                          </Button>
                          <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => handleToggle(item)} title={item.active ? 'Pause' : 'Resume'}>
                            {item.active ? <Pause className="h-3.5 w-3.5" /> : <Play className="h-3.5 w-3.5" />}
                          </Button>
                          <Button size="icon" variant="ghost" className="h-8 w-8 text-muted-foreground hover:text-destructive" onClick={() => setDeleteTarget(item)} title="Delete">
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
                        {formatRecurrence(item.frequency, item.dayOfWeek, item.dayOfMonth)}
                      </p>
                    </div>
                    <p className="text-sm font-bold">{formatMoney(item.amount)}</p>
                  </div>
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-2">
                      <Badge variant="outline" className="font-normal text-xs">
                        <div className="h-1.5 w-1.5 rounded-full mr-1 shrink-0" style={{ backgroundColor: CATEGORY_COLORS[item.category] || '#27272a' }} />
                        {item.category}
                      </Badge>
                      <Badge variant={item.active ? 'default' : 'secondary'} className="text-xs">
                        {item.active ? 'Active' : 'Paused'}
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
                  <p className="text-xs text-muted-foreground mt-2">Next: {formatDate(item.nextOccurrence)}</p>
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
            <DialogTitle>{editing ? 'Edit Recurring Expense' : 'Add Recurring Expense'}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Amount</Label>
                <Input type="number" step="0.01" min="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required placeholder="0.00" />
              </div>
              <div className="space-y-2">
                <Label>Merchant</Label>
                <Input value={merchant} onChange={(e) => setMerchant(e.target.value)} required placeholder="e.g. Netflix" />
              </div>
            </div>
            <div className="space-y-2">
              <Label>Category</Label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
              >
                <option value="">Auto-detect (AI)</option>
                {CATEGORY_GROUPS.map((g) => (
                  <optgroup key={g.label} label={g.label}>
                    {g.categories.map((c) => (
                      <option key={c} value={c}>{c}</option>
                    ))}
                  </optgroup>
                ))}
              </select>
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Payment Method</Label>
                <select
                  value={paymentMethod}
                  onChange={(e) => setPaymentMethod(e.target.value)}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                >
                  {PAYMENT_METHODS.map((m) => (
                    <option key={m} value={m}>{m}</option>
                  ))}
                </select>
              </div>
              {showCardName && (
                <div className="space-y-2">
                  <Label>Card Name</Label>
                  <Input value={cardName} onChange={(e) => setCardName(e.target.value)} placeholder="e.g. AMEX" />
                </div>
              )}
            </div>

            {/* Recurrence fields */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Frequency</Label>
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
                  <Label>Day of Week</Label>
                  <select
                    value={dayOfWeek}
                    onChange={(e) => setDayOfWeek(e.target.value as DayOfWeek)}
                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  >
                    <option value="">Any</option>
                    {DAYS_OF_WEEK.map((d) => (
                      <option key={d.value} value={d.value}>{d.label}</option>
                    ))}
                  </select>
                </div>
              )}
              {showDayOfMonth && (
                <div className="space-y-2">
                  <Label>Day of Month</Label>
                  <Input type="number" min="1" max="31" value={dayOfMonth} onChange={(e) => setDayOfMonth(e.target.value)} placeholder="e.g. 15" />
                </div>
              )}
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Start Date</Label>
                <Input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label>End Date (optional)</Label>
                <Input type="date" value={endDate} onChange={(e) => setEndDate(e.target.value)} />
              </div>
            </div>
            <div className="space-y-2">
              <Label>Notes</Label>
              <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Optional notes..." rows={2} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
              <Button type="submit">{editing ? 'Save changes' : 'Add Recurring'}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Are you sure?</AlertDialogTitle>
            <AlertDialogDescription>
              This will delete the recurring {formatMoney(deleteTarget?.amount ?? 0)} expense for {deleteTarget?.merchant}. Already-generated expenses will not be affected.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteTarget(null)}>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">Delete</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  )
}
