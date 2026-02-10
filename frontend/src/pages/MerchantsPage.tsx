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
import { useI18n } from '@/i18n'
import { toast } from 'sonner'
import type { MerchantCategory } from '@/types'

export default function MerchantsPage() {
  const { t, tc, language } = useI18n()
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
      toast.success(t('merchants.mappingUpdated'))
    } catch {
      toast.error(t('merchants.failedUpdate'))
    }
  }

  async function confirmDelete() {
    if (!deleteTarget) return
    try {
      await api.deleteMerchant(deleteTarget.id)
      setDeleteTarget(null)
      loadMerchants()
      toast.success(t('merchants.mappingDeleted'))
    } catch {
      toast.error(t('merchants.failedDelete'))
    }
  }

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      <div className="flex items-center justify-between px-4 lg:px-6">
        <div>
          <h1 className="text-2xl font-semibold">{t('merchants.title')}</h1>
          <p className="text-muted-foreground text-sm">
            {t('merchants.description', { count: filtered.length })}
          </p>
        </div>
      </div>

      <div className="px-4 lg:px-6">
        <div className="relative max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder={t('merchants.filterPlaceholder')}
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-9"
          />
        </div>
      </div>

      {/* Desktop Table */}
      <div className="hidden md:block px-4 lg:px-6">
        <div className="rounded-lg border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>{t('merchants.merchantCol')}</TableHead>
                <TableHead>{t('merchants.category')}</TableHead>
                <TableHead>{t('merchants.learnedOn')}</TableHead>
                <TableHead className="text-right w-[100px]">{t('common.actions')}</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {filtered.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={4} className="h-24 text-center text-muted-foreground">
                    {search ? t('merchants.noMatching') : t('merchants.noMappings')}
                  </TableCell>
                </TableRow>
              ) : (
                filtered.map((m) => (
                  <TableRow key={m.id}>
                    <TableCell className="font-mono text-sm">{m.merchantKey}</TableCell>
                    <TableCell>
                      {editingId === m.id ? (
                        <div className="flex items-center gap-2">
                          <select
                            value={editCategory}
                            onChange={(e) => setEditCategory(e.target.value)}
                            className="h-8 rounded-md border border-input bg-background px-2 text-sm focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
                          >
                            {CATEGORY_GROUPS.map((g) => (
                              <optgroup key={g.label} label={t(`categoryGroups.${g.label}`)}>
                                {g.categories.map((c) => (
                                  <option key={c} value={c}>{tc(c)}</option>
                                ))}
                              </optgroup>
                            ))}
                          </select>
                          <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => saveEdit(m.id)}>
                            <Check className="h-3.5 w-3.5 text-green-500" />
                          </Button>
                          <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => setEditingId(null)}>
                            <X className="h-3.5 w-3.5" />
                          </Button>
                        </div>
                      ) : (
                        <Badge variant="outline" className="font-normal">
                          <div className="h-2 w-2 rounded-full mr-1.5 shrink-0" style={{ backgroundColor: CATEGORY_COLORS[m.category] || '#27272a' }} />
                          {tc(m.category)}
                        </Badge>
                      )}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">{formatDateTime(m.createdAt, language)}</TableCell>
                    <TableCell className="text-right">
                      <div className="flex justify-end gap-1">
                        <Button size="icon" variant="ghost" className="h-8 w-8" onClick={() => startEdit(m)}>
                          <Pencil className="h-3.5 w-3.5" />
                        </Button>
                        <Button size="icon" variant="ghost" className="h-8 w-8 text-muted-foreground hover:text-destructive" onClick={() => setDeleteTarget(m)}>
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
        {filtered.map((m) => (
          <Card key={m.id}>
            <CardContent className="p-4">
              <div className="flex justify-between items-start mb-2">
                <span className="font-mono text-sm">{m.merchantKey}</span>
                <span className="text-xs text-muted-foreground">{formatDateTime(m.createdAt, language)}</span>
              </div>
              {editingId === m.id ? (
                <div className="flex items-center gap-2 mt-2">
                  <select
                    value={editCategory}
                    onChange={(e) => setEditCategory(e.target.value)}
                    className="h-8 rounded-md border border-input bg-background px-2 text-sm flex-1"
                  >
                    {CATEGORY_GROUPS.map((g) => (
                      <optgroup key={g.label} label={t(`categoryGroups.${g.label}`)}>
                        {g.categories.map((c) => (
                          <option key={c} value={c}>{tc(c)}</option>
                        ))}
                      </optgroup>
                    ))}
                  </select>
                  <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => saveEdit(m.id)}><Check className="h-3.5 w-3.5 text-green-500" /></Button>
                  <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => setEditingId(null)}><X className="h-3.5 w-3.5" /></Button>
                </div>
              ) : (
                <div className="flex justify-between items-center mt-2">
                  <Badge variant="outline" className="font-normal">
                    <div className="h-2 w-2 rounded-full mr-1.5 shrink-0" style={{ backgroundColor: CATEGORY_COLORS[m.category] || '#27272a' }} />
                    {tc(m.category)}
                  </Badge>
                  <div className="flex gap-1">
                    <Button size="icon" variant="ghost" className="h-7 w-7" onClick={() => startEdit(m)}><Pencil className="h-3.5 w-3.5" /></Button>
                    <Button size="icon" variant="ghost" className="h-7 w-7 text-muted-foreground hover:text-destructive" onClick={() => setDeleteTarget(m)}><Trash2 className="h-3.5 w-3.5" /></Button>
                  </div>
                </div>
              )}
            </CardContent>
          </Card>
        ))}
        {filtered.length === 0 && (
          <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">
            {search ? t('merchants.noMatching') : t('merchants.noMappings')}
          </div>
        )}
      </div>

      <AlertDialog open={!!deleteTarget} onOpenChange={(open) => { if (!open) setDeleteTarget(null) }}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>{t('common.areYouSure')}</AlertDialogTitle>
            <AlertDialogDescription>
              {t('merchants.deleteConfirm', { merchant: deleteTarget?.merchantKey ?? '' })}
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
