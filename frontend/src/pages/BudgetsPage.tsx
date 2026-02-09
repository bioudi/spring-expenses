import { useEffect, useState } from 'react'
import { Plus, Pencil, Trash2 } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { Dialog, DialogContent, DialogHeader, DialogTitle, DialogFooter } from '@/components/ui/dialog'
import { AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription, AlertDialogFooter, AlertDialogAction, AlertDialogCancel } from '@/components/ui/alert-dialog'
import { api } from '@/lib/api'
import { formatMoney } from '@/lib/formatters'
import { CATEGORY_COLORS, CATEGORY_GROUPS } from '@/lib/categories'
import { toast } from 'sonner'
import type { BudgetResponse, BudgetRequest } from '@/types'

function progressColor(percent: number): string {
  if (percent >= 100) return '#ef4444'
  if (percent >= 80) return '#eab308'
  return '#22c55e'
}

function formatCategories(categories: string[]): string {
  if (categories.length <= 2) return categories.join(', ')
  return `${categories[0]} + ${categories.length - 1} more`
}

export default function BudgetsPage() {
  const [budgets, setBudgets] = useState<BudgetResponse[]>([])
  const [modalOpen, setModalOpen] = useState(false)
  const [editing, setEditing] = useState<BudgetResponse | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<BudgetResponse | null>(null)
  const [loading, setLoading] = useState(true)

  const [selectedCategories, setSelectedCategories] = useState<string[]>([])
  const [monthlyLimit, setMonthlyLimit] = useState('')

  const loadBudgets = async () => {
    setLoading(true)
    try {
      const data = await api.getBudgets()
      setBudgets(data)
    } catch {
      toast.error('Failed to load budgets')
    }
    setLoading(false)
  }

  useEffect(() => { loadBudgets() }, [])

  function toggleCategory(cat: string) {
    setSelectedCategories(prev =>
      prev.includes(cat) ? prev.filter(c => c !== cat) : [...prev, cat]
    )
  }

  function openAdd() {
    setEditing(null)
    setSelectedCategories([])
    setMonthlyLimit('')
    setModalOpen(true)
  }

  function openEdit(budget: BudgetResponse) {
    setEditing(budget)
    setSelectedCategories([...budget.categories])
    setMonthlyLimit(String(budget.monthlyLimit))
    setModalOpen(true)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()

    if (selectedCategories.length === 0) {
      toast.error('Select at least one category')
      return
    }

    const data: BudgetRequest = {
      categories: selectedCategories,
      monthlyLimit: parseFloat(monthlyLimit),
    }

    try {
      if (editing) {
        await api.updateBudget(editing.id, data)
        toast.success('Budget updated')
      } else {
        await api.createBudget(data)
        toast.success('Budget created')
      }
      setModalOpen(false)
      loadBudgets()
    } catch (err) {
      toast.error(err instanceof Error ? err.message : 'Failed to save')
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    try {
      await api.deleteBudget(deleteTarget.id)
      setDeleteTarget(null)
      loadBudgets()
      toast.success('Budget deleted')
    } catch {
      toast.error('Failed to delete')
    }
  }

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      <div className="flex items-center justify-between px-4 lg:px-6">
        <div>
          <h1 className="text-2xl font-semibold">Budgets</h1>
          <p className="text-muted-foreground text-sm">Set monthly spending limits for one or more categories.</p>
        </div>
        <Button onClick={openAdd} size="sm">
          <Plus className="h-4 w-4 mr-2" /> Add Budget
        </Button>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">Loading...</div>
      ) : budgets.length === 0 ? (
        <div className="px-4 lg:px-6">
          <Card>
            <CardContent className="flex flex-col items-center justify-center py-16">
              <p className="text-sm text-muted-foreground mb-4">No budgets set yet.</p>
              <Button onClick={openAdd} variant="outline" size="sm">
                <Plus className="h-4 w-4 mr-2" /> Add your first budget
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
                    <TableHead>Categories</TableHead>
                    <TableHead className="text-right">Monthly Limit</TableHead>
                    <TableHead className="text-right">Spent</TableHead>
                    <TableHead className="text-right">Remaining</TableHead>
                    <TableHead className="w-[180px]">Usage</TableHead>
                    <TableHead className="text-right w-[100px]">Actions</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {budgets.map((budget) => (
                    <TableRow key={budget.id}>
                      <TableCell>
                        <div className="flex flex-wrap gap-1">
                          {budget.categories.map((cat) => (
                            <Badge key={cat} variant="outline" className="font-normal">
                              <div className="h-2 w-2 rounded-full mr-1.5 shrink-0" style={{ backgroundColor: CATEGORY_COLORS[cat] || '#27272a' }} />
                              {cat}
                            </Badge>
                          ))}
                        </div>
                      </TableCell>
                      <TableCell className="text-right font-medium">{formatMoney(budget.monthlyLimit)}</TableCell>
                      <TableCell className="text-right font-medium">{formatMoney(budget.spent)}</TableCell>
                      <TableCell className={`text-right font-medium ${budget.remaining < 0 ? 'text-red-500' : ''}`}>
                        {formatMoney(budget.remaining)}
                      </TableCell>
                      <TableCell>
                        <div className="flex items-center gap-2">
                          <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
                            <div
                              className="h-full rounded-full transition-all"
                              style={{
                                width: `${Math.min(budget.percentUsed, 100)}%`,
                                backgroundColor: progressColor(budget.percentUsed),
                              }}
                            />
                          </div>
                          <span className="text-xs text-muted-foreground w-10 text-right">{budget.percentUsed.toFixed(0)}%</span>
                        </div>
                      </TableCell>
                      <TableCell className="text-right">
                        <div className="flex justify-end gap-1">
                          <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => openEdit(budget)} title="Edit">
                            <Pencil className="h-3.5 w-3.5" />
                          </Button>
                          <Button size="icon" variant="ghost" className="h-8 w-8 text-muted-foreground hover:text-destructive" onClick={() => setDeleteTarget(budget)} title="Delete">
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
            {budgets.map((budget) => (
              <Card key={budget.id}>
                <CardContent className="p-4">
                  <div className="flex justify-between items-start mb-2">
                    <div className="flex flex-wrap gap-1">
                      {budget.categories.map((cat) => (
                        <Badge key={cat} variant="outline" className="font-normal text-xs">
                          <div className="h-1.5 w-1.5 rounded-full mr-1 shrink-0" style={{ backgroundColor: CATEGORY_COLORS[cat] || '#27272a' }} />
                          {cat}
                        </Badge>
                      ))}
                    </div>
                    <div className="flex gap-1 shrink-0 ml-2">
                      <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => openEdit(budget)}><Pencil className="h-3.5 w-3.5" /></Button>
                      <Button size="icon" variant="ghost" className="h-7 w-7 text-muted-foreground hover:text-destructive" onClick={() => setDeleteTarget(budget)}><Trash2 className="h-3.5 w-3.5" /></Button>
                    </div>
                  </div>
                  <div className="flex justify-between text-sm mb-2">
                    <span className="text-muted-foreground">{formatMoney(budget.spent)} / {formatMoney(budget.monthlyLimit)}</span>
                    <span className={`font-medium ${budget.remaining < 0 ? 'text-red-500' : ''}`}>{formatMoney(budget.remaining)} left</span>
                  </div>
                  <div className="flex items-center gap-2">
                    <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
                      <div
                        className="h-full rounded-full transition-all"
                        style={{
                          width: `${Math.min(budget.percentUsed, 100)}%`,
                          backgroundColor: progressColor(budget.percentUsed),
                        }}
                      />
                    </div>
                    <span className="text-xs text-muted-foreground">{budget.percentUsed.toFixed(0)}%</span>
                  </div>
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
            <DialogTitle>{editing ? 'Edit Budget' : 'Add Budget'}</DialogTitle>
          </DialogHeader>
          <form onSubmit={handleSubmit} className="space-y-4">
            <div className="space-y-2">
              <Label>Categories {selectedCategories.length > 0 && <span className="text-muted-foreground font-normal">({selectedCategories.length} selected)</span>}</Label>
              <div className="max-h-[240px] overflow-y-auto rounded-md border border-input p-3 space-y-3">
                {CATEGORY_GROUPS.map((g) => (
                  <div key={g.label}>
                    <p className="text-xs font-semibold text-muted-foreground uppercase tracking-wide mb-1.5">{g.label}</p>
                    <div className="flex flex-wrap gap-1.5">
                      {g.categories.map((cat) => {
                        const selected = selectedCategories.includes(cat)
                        return (
                          <button
                            key={cat}
                            type="button"
                            onClick={() => toggleCategory(cat)}
                            className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium border transition-colors ${
                              selected
                                ? 'bg-primary text-primary-foreground border-primary'
                                : 'bg-background text-foreground border-input hover:bg-accent'
                            }`}
                          >
                            <div className="h-2 w-2 rounded-full shrink-0" style={{ backgroundColor: CATEGORY_COLORS[cat] || '#27272a' }} />
                            {cat}
                          </button>
                        )
                      })}
                    </div>
                  </div>
                ))}
              </div>
            </div>
            <div className="space-y-2">
              <Label>Monthly Limit</Label>
              <Input type="number" step="0.01" min="0.01" value={monthlyLimit} onChange={(e) => setMonthlyLimit(e.target.value)} required placeholder="0.00" />
            </div>
            <DialogFooter>
              <Button type="button" variant="outline" onClick={() => setModalOpen(false)}>Cancel</Button>
              <Button type="submit" disabled={selectedCategories.length === 0}>{editing ? 'Save changes' : 'Add Budget'}</Button>
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
              This will delete the budget for {formatCategories(deleteTarget?.categories ?? [])} ({formatMoney(deleteTarget?.monthlyLimit ?? 0)}/month).
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
