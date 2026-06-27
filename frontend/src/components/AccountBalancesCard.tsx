import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { formatMoney } from '@/lib/formatters'
import { cn } from '@/lib/utils'

export type AccountType = 'BASE' | 'SAVINGS' | 'EMERGENCY' | 'CREDIT'

export interface AccountBalanceItem {
  id: string
  name: string
  type: AccountType
  balance: number
}

/**
 * Per-type styling for account balances.
 *
 * Colors are intentionally aligned with the existing convention in AccountsPage:
 *   BASE       → blue   (#3b82f6)  (chequing)
 *   SAVINGS    → green  (#22c55e)
 *   EMERGENCY  → amber  (#f59e0b)
 *   CREDIT     → red    (#ef4444)
 *
 * The mapping is exported so the Account Balances card can be styled
 * consistently with the rest of the app.
 */
export const ACCOUNT_TYPE_COLORS: Record<AccountType, { text: string; dot: string; bar: string }> = {
  BASE:      { text: 'text-blue-600 dark:text-blue-400',   dot: '#3b82f6', bar: 'bg-blue-500' },
  SAVINGS:   { text: 'text-green-600 dark:text-green-400', dot: '#22c55e', bar: 'bg-green-500' },
  EMERGENCY: { text: 'text-amber-600 dark:text-amber-400', dot: '#f59e0b', bar: 'bg-amber-500' },
  CREDIT:    { text: 'text-red-600 dark:text-red-400',     dot: '#ef4444', bar: 'bg-red-500' },
}

export const ACCOUNT_TYPE_LABELS: Record<AccountType, string> = {
  BASE: 'Chequing',
  SAVINGS: 'Savings',
  EMERGENCY: 'Emergency',
  CREDIT: 'Credit',
}

/**
 * Mock data used for development and visual verification. The dashboard
 * integration task replaces this with live data from the backend; until
 * then, the card is fully self-contained.
 */
export const MOCK_ACCOUNT_BALANCES: AccountBalanceItem[] = [
  { id: 'mock-1', name: 'Chequing',    type: 'BASE',      balance: 4250.32 },
  { id: 'mock-2', name: 'High-Interest Savings', type: 'SAVINGS', balance: 12800.00 },
  { id: 'mock-3', name: 'Emergency Fund', type: 'EMERGENCY', balance: 3000.00 },
  { id: 'mock-4', name: 'Visa',        type: 'CREDIT',    balance: 845.17 },
  { id: 'mock-5', name: 'Mastercard',  type: 'CREDIT',    balance: 0 },
]

export interface AccountBalancesCardProps {
  /**
   * Accounts to render. Defaults to {@link MOCK_ACCOUNT_BALANCES} so the card
   * is self-contained for the dashboard widget task.
   */
  accounts?: AccountBalanceItem[]
  /** Locale key used by formatMoney. */
  language?: 'en' | 'fr'
}

/**
 * Self-contained dashboard card that lists account balances, color-coding
 * each row according to the account's type.
 *
 * The card deliberately uses mock data so it can be developed and reviewed
 * independently of the dashboard integration task that wires real backend
 * data into the layout.
 */
export default function AccountBalancesCard({
  accounts = MOCK_ACCOUNT_BALANCES,
  language = 'en',
}: AccountBalancesCardProps) {
  return (
    <Card data-testid="account-balances-card">
      <CardHeader className="pb-2">
        <CardDescription>Account Balances</CardDescription>
        <CardTitle className="text-base font-medium">By account type</CardTitle>
      </CardHeader>
      <CardContent className="space-y-1.5">
        {accounts.length === 0 ? (
          <div className="text-sm text-muted-foreground py-4 text-center">
            No accounts yet.
          </div>
        ) : (
          <ul role="list" className="divide-y divide-border">
            {accounts.map((account) => {
              const palette = ACCOUNT_TYPE_COLORS[account.type]
              const displayBalance =
                account.type === 'CREDIT' ? -Math.abs(account.balance) : account.balance
              return (
                <li
                  key={account.id}
                  data-testid="account-balances-row"
                  data-account-type={account.type}
                  className="flex items-center justify-between gap-3 py-2"
                >
                  <div className="flex items-center gap-3 min-w-0">
                    <span
                      aria-hidden="true"
                      className={cn('h-2.5 w-2.5 rounded-full shrink-0', palette.bar)}
                    />
                    <div className="min-w-0">
                      <p className="text-sm font-medium truncate">{account.name}</p>
                      <p className={cn('text-xs font-medium', palette.text)}>
                        {ACCOUNT_TYPE_LABELS[account.type]}
                      </p>
                    </div>
                  </div>
                  <span
                    className={cn(
                      'text-sm font-semibold tabular-nums whitespace-nowrap',
                      palette.text,
                    )}
                  >
                    {formatMoney(displayBalance, language)}
                  </span>
                </li>
              )
            })}
          </ul>
        )}
      </CardContent>
    </Card>
  )
}
