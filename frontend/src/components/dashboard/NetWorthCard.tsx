import { useMemo } from 'react'
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { cn } from '@/lib/utils'
import { formatMoney } from '@/lib/formatters'
import { computeDashboardMetrics, type AccountInput } from '@/lib/dashboard'

type LocaleKey = 'en' | 'fr'

/**
 * Mock placeholder accounts used until the live accounts API is wired up.
 * Mirrors the data shape produced by the future /api/accounts endpoint.
 */
const MOCK_ACCOUNTS: AccountInput[] = [
  { id: 'mock-base', name: 'Chequing', balance: 4250.75, type: 'BASE' },
  { id: 'mock-savings', name: 'Savings', balance: 12780.4, type: 'SAVINGS' },
  { id: 'mock-emergency', name: 'Emergency Fund', balance: 5000, type: 'EMERGENCY' },
  { id: 'mock-credit', name: 'Visa', balance: 842.1, type: 'CREDIT' },
]

export interface NetWorthCardProps {
  /** Optional accounts to render instead of the mock placeholder data. */
  accounts?: AccountInput[]
  /** Locale used to format the currency. */
  language?: LocaleKey
  /** Optional aria-label override for the root region. */
  ariaLabel?: string
}

/**
 * Self-contained Net Worth dashboard card.
 *
 * Renders the headline net worth value in green when positive and red when
 * negative, with sub-lines for total assets and total debt. Uses mock
 * placeholder data when no accounts are supplied so the card is renderable
 * before the live accounts endpoint is wired up.
 */
export function NetWorthCard({
  accounts,
  language = 'en',
  ariaLabel,
}: NetWorthCardProps) {
  const { netWorth, totalAssets, totalDebt } = useMemo(
    () => computeDashboardMetrics(accounts ?? MOCK_ACCOUNTS),
    [accounts],
  )

  const isPositive = netWorth >= 0
  const valueColor = isPositive
    ? 'text-green-600 dark:text-green-400'
    : 'text-red-600 dark:text-red-400'

  return (
    <Card aria-label={ariaLabel ?? 'Net worth'} data-testid="net-worth-card">
      <CardHeader className="pb-2">
        <CardDescription>Net Worth</CardDescription>
        <CardTitle
          className={cn('text-3xl font-bold tabular-nums', valueColor)}
          data-testid="net-worth-value"
          data-sign={isPositive ? 'positive' : 'negative'}
        >
          {formatMoney(netWorth, language)}
        </CardTitle>
      </CardHeader>
      <CardContent>
        <div className="flex items-center justify-between text-sm">
          <span
            className="text-green-600 dark:text-green-400 font-medium tabular-nums"
            data-testid="net-worth-assets"
          >
            Assets: {formatMoney(totalAssets, language)}
          </span>
          <span
            className="text-red-600 dark:text-red-400 font-medium tabular-nums"
            data-testid="net-worth-debt"
          >
            Debt: {formatMoney(totalDebt, language)}
          </span>
        </div>
      </CardContent>
    </Card>
  )
}

export default NetWorthCard
