import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2, Repeat } from 'lucide-react'
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
import { formatMoney, formatDateTime, toLocalDateTimeInput } from '@/lib/formatters'
import { CATEGORY_COLORS, CATEGORY_GROUPS, PAYMENT_METHODS } from '@/lib/categories'
import { toast } from 'sonner'
import { toISODate } from '@/lib/formatters'
import type { ExpenseResponse, ExpenseRequest, RecurrenceFrequency, DayOfWeek } from '@/types'

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

export default function ExpensesPage() {
  const [expenses, setExpenses] = useState<ExpenseResponse[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ExpenseResponse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<ExpenseResponse | null>(null)
  const [loading, setLoading] = useState(true)

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

  useEffect(() => {
    loadExpenses()
  }, [])

  async function loadExpenses() {
    setLoading(true)
    const data = await api.getExpenses()
    setExpenses(data)
    setLoading(false)
  }

  function openAdd() {
    setEditing(null)
    setAmount('')
    setMerchant('')
    setCategory('')
    setPaymentMethod('Card')
    setCardName('')
    setTimestamp(toLocalDateTimeInput())
    setNotes('')
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
    }

    try {
      if (editing) {
        await api.updateExpense(editing.id, data)
        toast.success('Expense updated')
      } else {
        await api.createExpense(data)
        toast.success('Expense created')
      }
      setModalOpen(false)
      loadExpenses()
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to save expense')
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    try {
      await api.deleteExpense(deleteTarget.id)
      setDeleteTarget(null)
      loadExpenses()
      toast.success('Expense deleted')
    } catch {
      toast.error('Failed to delete expense')
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
      toast.success('Recurring expense created')
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to create recurring expense')
    }
  }

  const showCardName = paymentMethod === 'Card' || paymentMethod === 'Debit'

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      <div className="flex items-center justify-between px-4 lg:px-6">
        <div>
          <h1 className="text-2xl font-semibold">Expenses</h1>
          <p className="text-muted-foreground text-sm">Manage and track your expenses.</p>
        </div>
        <Button onClick={openAdd} size="sm">
          <Plus className="h-4 w-4 mr-2" /> Add Expense
        </Button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">Loading expenses...</div>
      ) : expenses.length === 0 ? (
        <div className="px-4 lg:px-6">
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16">
              <p className="text-sm text-muted-foreground mb-4">No expenses yet.</p>
              <Button onClick={openAdd} variant="outline" size="sm">
                <Plus className="h-4 w-4 mr-2" /> Add your first expense
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
                    <TableHead>Date</TableHead>
                    <TableHead>Merchant</TableHead>
                    <TableHead>Category</TableHead>
                    <TableHead className="text-right">Amount</TableHead>
                    <TableHead>Payment</TableHead>
                    <TableHead>Notes</TableHead>
                    <TableHead className="text-right w-[100px]">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {expenses.map((exp) => (
                    <TableRow key={exp.id}>
                      <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                        {formatDateTime(exp.timestamp)}
                      </TableCell>
                      <TableCell className="font-medium">
                        <span className="inline-flex items-center gap-1.5">
                          {exp.merchant}
                          {exp.recurringExpenseId && <span title="Recurring"><Repeat className="h-3 w-3 text-muted-foreground" /></span>}
                        </span>
                      </TableCell>
                      <TableCell>
                        <Badge variant="outline" className="font-normal">
                          <div className="h-2 w-2 rounded-full mr-1.5 shrink-0" style={{ backgroundColor: CATEGORY_COLORS[exp.category] || '#27272a' }} />
                          {exp.category}
                        </Badge>
                      </TableCell>
                      <TableCell className="text-right font-medium">{formatMoney(exp.amount)}</TableCell>
                      <TableCell className="text-sm text-muted-foreground">
                        {exp.paymentMethod}{exp.cardName ? ` · ${exp.cardName}` : ''}
                      </TableCell>
                      <TableCell className="text-sm text-muted-foreground max-w-[200px] truncate">{exp.notes}</TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => openEdit(exp)}>
                            <Pencil className="h-3.5 w-3.5" />
                          </Button>
                          {!exp.recurringExpenseId && (
                            <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => openMakeRecurring(exp)} title="Make Recurring">
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
            {expenses.map((exp) => (
              <Card key={exp.id}>
                <CardContent className="p-4">
                  <div className="flex justify-between items-start mb-2">
                    <div>
                      <p className="text-sm font-medium inline-flex items-center gap-1.5">
                        {exp.merchant}
                        {exp.recurringExpenseId && <Repeat className="h-3 w-3 text-muted-foreground" />}
                      </p>
                      <p className="text-xs text-muted-foreground">{formatDateTime(exp.timestamp)}</p>
                    </div>
                    <p className="text-sm font-bold">{formatMoney(exp.amount)}</p>
                  </div>
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-2">
                      <Badge variant="outline" className="font-normal text-xs">
                        <div className="h-1.5 w-1.5 rounded-full mr-1 shrink-0" style={{ backgroundColor: CATEGORY_COLORS[exp.category] || '#27272a' }} />
                        {exp.category}
                      </Badge>
                      <span className="text-xs text-muted-foreground">{exp.paymentMethod}</span>
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
            <DialogTitle>{editing ? 'Edit Expense' : 'Add Expense'}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Amount</Label>
                <Input type="number" step="0.01" min="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required placeholder="0.00" />
              </div>
              <div className="space-y-2">
                <Label>Merchant</Label>
                <Input value={merchant} onChange={(e) => setMerchant(e.target.value)} required placeholder="e.g. Walmart" />
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
            <div className="space-y-2">
              <Label>Date & Time</Label>
              <Input type="datetime-local" value={timestamp} onChange={(e) => setTimestamp(e.target.value)} />
            </div>
            <div className="space-y-2">
              <Label>Notes</Label>
              <Textarea value={notes} onChange={(e) => setNotes(e.target.value)} placeholder="Optional notes..." rows={2} />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
              <Button type="submit">{editing ? 'Save changes' : 'Add Expense'}</Button>
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
              This will permanently delete the {formatMoney(deleteTarget?.amount ?? 0)} expense from {deleteTarget?.merchant}. This action cannot be undone.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel onClick={() => setDeleteTarget(null)}>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={confirmDelete} className="bg-destructive text-destructive-foreground hover:bg-destructive/90">Delete</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>

      {/* Make Recurring Dialog */}
      <Dialog open={!!recurringTarget} onOpenChange={(open) => { if (!open) setRecurringTarget(null) }}>
        <DialogContent onClose={() => setRecurringTarget(null)}>
          <DialogHeader>
            <DialogTitle>Make Recurring</DialogTitle>
          </DialogHeader>
          <p className="text-sm text-muted-foreground">
            Set up {recurringTarget?.merchant} ({formatMoney(recurringTarget?.amount ?? 0)}) as a recurring expense.
          </p>
          <form onSubmit={handleMakeRecurring} className="space-y-4">
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Frequency</Label>
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
                  <Label>Day of Week</Label>
                  <select
                    value={recDayOfWeek}
                    onChange={(e) => setRecDayOfWeek(e.target.value as DayOfWeek)}
                    className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                  >
                    <option value="">Any</option>
                    {DAYS_OF_WEEK.map((d) => (
                      <option key={d.value} value={d.value}>{d.label}</option>
                    ))}
                  </select>
                </div>
              )}
              {recFrequency === 'MONTHLY' && (
                <div className="space-y-2">
                  <Label>Day of Month</Label>
                  <Input type="number" min="1" max="31" value={recDayOfMonth} onChange={(e) => setRecDayOfMonth(e.target.value)} placeholder="e.g. 15" />
                </div>
              )}
            </div>
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div className="space-y-2">
                <Label>Start Date</Label>
                <Input type="date" value={recStartDate} onChange={(e) => setRecStartDate(e.target.value)} required />
              </div>
              <div className="space-y-2">
                <Label>End Date (optional)</Label>
                <Input type="date" value={recEndDate} onChange={(e) => setRecEndDate(e.target.value)} />
              </div>
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setRecurringTarget(null)}>Cancel</Button>
              <Button type="submit">Create Recurring</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>
    </div>
  )
}
