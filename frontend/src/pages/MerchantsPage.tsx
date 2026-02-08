import { useEffect, useState } from 'react'
import { Search, Pencil, Trash2, X, Check } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { AlertDialog, AlertDialogContent, AlertDialogHeader, AlertDialogTitle, AlertDialogDescription, AlertDialogFooter, AlertDialogAction, AlertDialogCancel } from '@/components/ui/alert-dialog'
import { api } from '@/lib/api'
import { formatDateTime } from '@/lib/formatters'
import { CATEGORY_COLORS, CATEGORY_GROUPS } from '@/lib/categories'
import { toast } from 'sonner'
import type { MerchantCategory } from '@/types'

export default function MerchantsPage() {
  const [merchants, setMerchants] = useState<MerchantCategory[]>([])
  const [search, setSearch] = useState('')
  const [editingId, setEditingId] = useState<string | null>(null)
  const [editCategory, setEditCategory] = useState('')
  const [deleteTarget, setDeleteTarget] = useState<MerchantCategory | null>(null)

  useEffect(() => {
    loadMerchants()
  }, [])

  async function loadMerchants() {
    const data = await api.getMerchants()
    setMerchants(data)
  }

  const filtered = merchants.filter((m) => {
    const q = search.toLowerCase()
    return m.merchantKey.toLowerCase().includes(q) || m.category.toLowerCase().includes(q)
  })

  function startEdit(m: MerchantCategory) {
    setEditingId(m.id)
    setEditCategory(m.category)
  }

  async function saveEdit(id: string) {
    try {
      await api.updateMerchant(id, editCategory)
      setEditingId(null)
      loadMerchants()
      toast.success('Mapping updated')
    } catch {
      toast.error('Failed to update mapping')
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    try {
      await api.deleteMerchant(deleteTarget.id)
      setDeleteTarget(null)
      loadMerchants()
      toast.success('Mapping deleted')
    } catch {
      toast.error('Failed to delete mapping')
    }
  }

  return (
    <div className="max-w-5xl mx-auto p-6 space-y-4">
      <div className="flex items-center justify-between flex-wrap gap-4">
        <h1 className="text-2xl font-bold">Merchant Mappings</h1>
        <span className="text-sm text-muted-foreground">{filtered.length} mappings</span>
      </div>

      <div className="relative">
        <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
        <Input
          placeholder="Search merchants or categories..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pl-9"
        />
      </div>

      {/* Desktop Table */}
      <div className="hidden md:block">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Merchant</TableHead>
              <TableHead>Category</TableHead>
              <TableHead>Learned On</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filtered.map((m) => (
              <TableRow key={m.id}>
                <TableCell className="font-mono text-sm">{m.merchantKey}</TableCell>
                <TableCell>
                  {editingId === m.id ? (
                    <div className="flex items-center gap-2">
                      <select
                        value={editCategory}
                        onChange={(e) => setEditCategory(e.target.value)}
                        className="bg-background border border-input rounded-md px-2 py-1 text-sm"
                      >
                        {CATEGORY_GROUPS.map((g) => (
                          <optgroup key={g.label} label={g.label}>
                            {g.categories.map((c) => (
                              <option key={c} value={c}>{c}</option>
                            ))}
                          </optgroup>
                        ))}
                      </select>
                      <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => saveEdit(m.id)}>
                        <Check className="h-4 w-4 text-green-400" />
                      </Button>
                      <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => setEditingId(null)}>
                        <X className="h-4 w-4" />
                      </Button>
                    </div>
                  ) : (
                    <Badge variant="secondary" style={{ backgroundColor: CATEGORY_COLORS[m.category] + '20', color: CATEGORY_COLORS[m.category] || '#8b949e', borderColor: 'transparent' }}>
                      {m.category}
                    </Badge>
                  )}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">{formatDateTime(m.createdAt)}</TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-1">
                    <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => startEdit(m)}>
                      <Pencil className="h-4 w-4" />
                    </Button>
                    <Button size="icon" variant="ghost" className="h-8 w-8 text-red-400 hover:text-red-300" onClick={() => setDeleteTarget(m)}>
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
        {filtered.map((m) => (
          <Card key={m.id}>
            <CardContent className="p-4">
              <div className="flex justify-between items-start mb-2">
                <span className="font-mono text-sm">{m.merchantKey}</span>
                <span className="text-xs text-muted-foreground">{formatDateTime(m.createdAt)}</span>
              </div>
              {editingId === m.id ? (
                <div className="flex items-center gap-2 mt-2">
                  <select
                    value={editCategory}
                    onChange={(e) => setEditCategory(e.target.value)}
                    className="bg-background border border-input rounded-md px-2 py-1 text-sm flex-1"
                  >
                    {CATEGORY_GROUPS.map((g) => (
                      <optgroup key={g.label} label={g.label}>
                        {g.categories.map((c) => (
                          <option key={c} value={c}>{c}</option>
                        ))}
                      </optgroup>
                    ))}
                  </select>
                  <Button size="sm" variant="ghost" onClick={() => saveEdit(m.id)}><Check className="h-4 w-4 text-green-400" /></Button>
                  <Button size="sm" variant="ghost" onClick={() => setEditingId(null)}><X className="h-4 w-4" /></Button>
                </div>
              ) : (
                <div className="flex justify-between items-center mt-2">
                  <Badge variant="secondary" style={{ backgroundColor: CATEGORY_COLORS[m.category] + '20', color: CATEGORY_COLORS[m.category] || '#8b949e', borderColor: 'transparent' }}>
                    {m.category}
                  </Badge>
                  <div className="flex gap-1">
                    <Button size="sm" variant="ghost" onClick={() => startEdit(m)}><Pencil className="h-4 w-4" /></Button>
                    <Button size="sm" variant="ghost" className="text-red-400" onClick={() => setDeleteTarget(m)}><Trash2 className="h-4 w-4" /></Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        ))}
      </div>

      {filtered.length === 0 && (
        <div className="text-center py-12 text-muted-foreground">
          {search ? 'No matching merchants found.' : 'No merchant mappings yet. They will appear here as expenses are categorized.'}
        </div>
      )}

      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Delete Merchant Mapping?</AlertDialogTitle>
            <AlertDialogDescription>
              Remove the mapping for "{deleteTarget?.merchantKey}". The next time this merchant appears, it will be re-categorized by AI.
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
