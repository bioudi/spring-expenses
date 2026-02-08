import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2, X } from 'lucide-react'
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
import type { ExpenseResponse, ExpenseRequest } from '@/types'

export default function ExpensesPage() {
  const [expenses, setExpenses] = useState<ExpenseResponse[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<ExpenseResponse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<ExpenseResponse | null>(null)
  const [loading, setLoading] = useState(true)

  // Form state
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

  const showCardName = paymentMethod === 'Card' || paymentMethod === 'Debit'

  return (
    <div className="max-w-6xl mx-auto p-6 space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-4">
        <h1 className="text-2xl font-bold">Expenses</h1>
        <Button onClick={openAdd}>
          <Plus className="h-4 w-4 mr-2" /> Add Expense
        </Button>
      </div>

      {loading ? (
        <div className="text-center py-12 text-muted-foreground">Loading expenses...</div>
      ) : expenses.length === 0 ? (
        <div className="text-center py-12 text-muted-foreground">
          No expenses yet. Click "Add Expense" to create your first one.
        </div>
      ) : (
        <>
          {/* Desktop Table */}
          <div className="hidden md:block">
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Date</TableHead>
                  <TableHead>Merchant</TableHead>
                  <TableHead>Category</TableHead>
                  <TableHead className="text-right">Amount</TableHead>
                  <TableHead>Payment</TableHead>
                  <TableHead>Notes</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {expenses.map((exp) => (
                  <TableRow key={exp.id}>
                    <TableCell className="text-sm text-muted-foreground whitespace-nowrap">
                      {formatDateTime(exp.timestamp)}
                    </TableCell>
                    <TableCell className="font-medium">{exp.merchant}</TableCell>
                    <TableCell>
                      <Badge variant="secondary" style={{ backgroundColor: CATEGORY_COLORS[exp.category] + '20', color: CATEGORY_COLORS[exp.category] || '#8b949e', borderColor: 'transparent' }}>
                        {exp.category}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-right font-semibold text-primary">{formatMoney(exp.amount)}</TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {exp.paymentMethod}{exp.cardName ? ` (${exp.cardName})` : ''}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground max-w-[200px] truncate">{exp.notes}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => openEdit(exp)}>
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button size="icon" variant="ghost" className="h-8 w-8 text-red-400 hover:text-red-300" onClick={() => setDeleteTarget(exp)}>
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>

          {/* Mobile Cards */}
          <div className="md:hidden space-y-3">
            {expenses.map((exp) => (
              <Card key={exp.id}>
                <CardContent className="p-4">
                  <div className="flex justify-between items-start mb-2">
                    <div>
                      <p className="font-medium">{exp.merchant}</p>
                      <p className="text-xs text-muted-foreground">{formatDateTime(exp.timestamp)}</p>
                    </div>
                    <p className="text-lg font-bold text-primary">{formatMoney(exp.amount)}</p>
                  </div>
                  <div className="flex justify-between items-center">
                    <div className="flex items-center gap-2">
                      <Badge variant="secondary" style={{ backgroundColor: CATEGORY_COLORS[exp.category] + '20', color: CATEGORY_COLORS[exp.category] || '#8b949e', borderColor: 'transparent' }}>
                        {exp.category}
                      </Badge>
                      <span className="text-xs text-muted-foreground">{exp.paymentMethod}</span>
                    </div>
                    <div className="flex gap-1">
                      <Button size="sm" variant="ghost" onClick={() => openEdit(exp)}><Pencil className="h-4 w-4" /></Button>
                      <Button size="sm" variant="ghost" className="text-red-400" onClick={() => setDeleteTarget(exp)}><Trash2 className="h-4 w-4" /></Button>
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
                <Input type="number" step="0.01" min="0.01" value={amount} onChange={(e) => setAmount(e.target.value)} required />
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
                className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
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
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
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
              <Button type="submit">{editing ? 'Update' : 'Add Expense'}</Button>
            </DialogFooter>
          </form>
        </DialogContent>
      </Dialog>

      {/* Delete Confirmation */}
      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Expense?</AlertDialogTitle>
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
    </div>
  )
}
