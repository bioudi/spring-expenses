import { useEffect, useMemo, useState } from 'react'
import { ArrowLeftRight, CheckCircle2 } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card'
import { Input } from '@/components/ui/input'
import { Button } from '@/components/ui/button'
import { Label } from '@/components/ui/label'
import { Textarea } from '@/components/ui/textarea'
import { Badge } from '@/components/ui/badge'
import { api } from '@/lib/api'
import { formatMoney } from '@/lib/formatters'
import { useI18n } from '@/i18n'
import { toast } from 'sonner'
import type {
  AccountResponse,
  TransferRequest,
  TransferResponse,
  TransferAccountSnapshot,
} from '@/types'

/**
 * Transfers page — moves funds between the user's own accounts, including
 * credit-card payments. The four balance cases (non-CREDIT ↔ non-CREDIT,
 * non-CREDIT → CREDIT, CREDIT → non-CREDIT, CREDIT → CREDIT) are all handled
 * by the backend {@code POST /api/transfers} endpoint; the UI just collects
 * inputs and renders the resulting balances.
 */
export default function TransfersPage() {
  const { t, language } = useI18n()
  const [accounts, setAccounts] = useState<AccountResponse[]>([])
  const [loading, setLoading] = useState(true)
  const [fromId, setFromId] = useState('')
  const [toId, setToId] = useState('')
  const [amount, setAmount] = useState('')
  const [description, setDescription] = useState('')
  const [submitting, setSubmitting] = useState(false)
  const [result, setResult] = useState<TransferResponse | null>(null)

  useEffect(() => {
    let cancelled = false
    async function load() {
      try {
        const data = await api.getAccounts()
        if (cancelled) return
        setAccounts(data)
      } catch {
        if (!cancelled) toast.error(t('transfers.failedLoad'))
      } finally {
        if (!cancelled) setLoading(false)
      }
    }
    load()
    return () => { cancelled = true }
  }, [t])

  const fromAccount = useMemo(
    () => accounts.find(a => a.id === fromId) ?? null,
    [accounts, fromId]
  )
  const toAccount = useMemo(
    () => accounts.find(a => a.id === toId) ?? null,
    [accounts, toId]
  )

  const sameAccount = !!(fromId && toId && fromId === toId)
  const parsedAmount = parseFloat(amount)
  const amountValid = !isNaN(parsedAmount) && parsedAmount > 0
  const canSubmit = !!fromId && !!toId && amountValid && !sameAccount && !submitting

  function swapAccounts() {
    const nextFrom = toId
    const nextTo = fromId
    setFromId(nextFrom)
    setToId(nextTo)
    setResult(null)
  }

  async function handleSubmit(e: React.FormEvent) {
    e.preventDefault()
    if (!canSubmit) return
    const payload: TransferRequest = {
      fromAccountId: fromId,
      toAccountId: toId,
      amount: parsedAmount,
      description: description.trim() || null,
    }
    try {
      setSubmitting(true)
      const res = await api.transfer(payload)
      setResult(res)
      toast.success(t('transfers.success'))
      // Refresh the account list so subsequent transfers see the new balances.
      const updated = await api.getAccounts()
      setAccounts(updated)
    } catch (err) {
      const message = err instanceof Error ? err.message : t('transfers.failedSubmit')
      toast.error(message)
    } finally {
      setSubmitting(false)
    }
  }

  function handleReset() {
    setResult(null)
    setAmount('')
    setDescription('')
  }

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      <div className="px-4 lg:px-6">
        <h1 className="text-2xl font-semibold">{t('transfers.title')}</h1>
        <p className="text-muted-foreground text-sm">{t('transfers.description')}</p>
      </div>

      <div className="grid gap-4 px-4 lg:px-6 md:grid-cols-2">
        {/* Form card */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t('transfers.newTransfer')}</CardTitle>
          </CardHeader>
          <CardContent>
            {loading ? (
              <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">
                {t('common.loading')}
              </div>
            ) : accounts.length < 2 ? (
              <div className="flex flex-col items-center justify-center gap-2 py-8 text-sm text-muted-foreground">
                <p>{t('transfers.needTwoAccounts')}</p>
              </div>
            ) : (
              <form onSubmit={handleSubmit} className="space-y-4">
                {/* From / To with swap button */}
                <div className="grid grid-cols-1 sm:grid-cols-[1fr_auto_1fr] gap-3 items-end">
                  <div className="space-y-2">
                    <Label htmlFor="transfer-from">{t('transfers.from')}</Label>
                    <select
                      id="transfer-from"
                      value={fromId}
                      onChange={(e) => { setFromId(e.target.value); setResult(null) }}
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                      required
                    >
                      <option value="">{t('transfers.selectAccount')}</option>
                      {accounts.map(a => (
                        <option key={a.id} value={a.id}>{a.name}</option>
                      ))}
                    </select>
                    {fromAccount && (
                      <p className="text-xs text-muted-foreground">
                        {t('accounts.balance')}: <span className="font-mono tabular-nums">{formatMoney(fromAccount.balance, language)}</span>
                      </p>
                    )}
                  </div>
                  <div className="flex justify-center sm:pb-0.5">
                    <Button
                      type="button"
                      variant="outline"
                      size="icon"
                      onClick={swapAccounts}
                      aria-label={t('transfers.swap')}
                      disabled={!fromId || !toId}
                      className="h-10 w-10"
                    >
                      <ArrowLeftRight className="h-4 w-4" />
                    </Button>
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="transfer-to">{t('transfers.to')}</Label>
                    <select
                      id="transfer-to"
                      value={toId}
                      onChange={(e) => { setToId(e.target.value); setResult(null) }}
                      className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2"
                      required
                    >
                      <option value="">{t('transfers.selectAccount')}</option>
                      {accounts.map(a => (
                        <option key={a.id} value={a.id}>{a.name}</option>
                      ))}
                    </select>
                    {toAccount && (
                      <p className="text-xs text-muted-foreground">
                        {t('accounts.balance')}: <span className="font-mono tabular-nums">{formatMoney(toAccount.balance, language)}</span>
                      </p>
                    )}
                  </div>
                </div>

                {/* Inline validation hints */}
                {sameAccount && (
                  <p className="text-sm text-destructive">{t('transfers.errorSameAccount')}</p>
                )}

                <div className="space-y-2">
                  <Label htmlFor="transfer-amount">{t('transfers.amount')}</Label>
                  <Input
                    id="transfer-amount"
                    type="number"
                    step="0.01"
                    min="0.01"
                    value={amount}
                    onChange={(e) => { setAmount(e.target.value); setResult(null) }}
                    placeholder="0.00"
                    required
                  />
                </div>

                <div className="space-y-2">
                  <Label htmlFor="transfer-description">{t('transfers.description')}</Label>
                  <Textarea
                    id="transfer-description"
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    rows={2}
                    placeholder={t('transfers.placeholderDescription')}
                  />
                </div>

                <div className="flex gap-2 pt-2">
                  <Button type="submit" disabled={!canSubmit}>
                    {submitting ? t('common.loading') : t('transfers.submit')}
                  </Button>
                  <Button
                    type="button"
                    variant="outline"
                    onClick={handleReset}
                    disabled={submitting}
                  >
                    {t('common.cancel')}
                  </Button>
                </div>
              </form>
            )}
          </CardContent>
        </Card>

        {/* Result card */}
        <Card>
          <CardHeader>
            <CardTitle className="text-base">{t('transfers.result')}</CardTitle>
          </CardHeader>
          <CardContent>
            {result ? (
              <TransferResultView result={result} t={t} />
            ) : (
              <div className="flex items-center justify-center py-12 text-sm text-muted-foreground">
                {t('transfers.noResult')}
              </div>
            )}
          </CardContent>
        </Card>
      </div>
    </div>
  )
}

function TransferResultView({
  result,
  t,
}: {
  result: TransferResponse
  t: (key: string, vars?: Record<string, string | number>) => string
}) {
  return (
    <div className="space-y-4">
      <div className="flex items-center gap-2 text-sm">
        <CheckCircle2 className="h-5 w-5 text-green-600" />
        <span className="font-medium">{t('transfers.success')}</span>
        <span className="ml-auto font-mono tabular-nums">
          {formatMoney(result.amount)}
        </span>
      </div>
      {result.description && (
        <p className="text-sm text-muted-foreground italic">"{result.description}"</p>
      )}
      <div className="grid grid-cols-2 gap-3">
        <AccountSnapCard
          label={t('transfers.from')}
          snapshot={result.fromAccount}
          t={t}
        />
        <AccountSnapCard
          label={t('transfers.to')}
          snapshot={result.toAccount}
          t={t}
        />
      </div>
    </div>
  )
}

function AccountSnapCard({
  label,
  snapshot,
  t,
}: {
  label: string
  snapshot: TransferAccountSnapshot
  t: (key: string, vars?: Record<string, string | number>) => string
}) {
  const typeLabel = t(
    `accounts.type${snapshot.type.charAt(0) + snapshot.type.slice(1).toLowerCase()}` as const
  )
  return (
    <div className="rounded-md border bg-muted/30 p-3">
      <p className="text-xs uppercase tracking-wide text-muted-foreground">{label}</p>
      <p className="text-sm font-medium mt-1 truncate">{snapshot.name}</p>
      <Badge variant="outline" className="font-normal mt-1">{typeLabel}</Badge>
      <p className={`mt-2 font-mono text-base tabular-nums ${snapshot.type === 'CREDIT' ? 'text-destructive' : ''}`}>
        {snapshot.type === 'CREDIT' ? '-' : ''}{formatMoney(snapshot.balance)}
      </p>
      <p className="text-xs text-muted-foreground">{t('transfers.newBalance')}</p>
    </div>
  )
}