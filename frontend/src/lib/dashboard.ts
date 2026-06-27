export type AccountType = 'BASE' | 'SAVINGS' | 'EMERGENCY' | 'CREDIT'

export interface DashboardMetrics {
  netWorth: number
  totalAssets: number
  totalDebt: number
  accountBalances: Array<{
    accountId: string
    name: string
    type: string
    balance: number
  }>
}

export interface AccountInput {
  id: string
  name: string
  balance: number
  type: AccountType
}

const ASSET_TYPES: AccountType[] = ['BASE', 'SAVINGS', 'EMERGENCY']
const DEBT_TYPES: AccountType[] = ['CREDIT']

/**
 * Computes dashboard metrics (net worth, total assets/total debt, and per-account balances)
 * from an array of account objects.
 *
 * Returns a DashboardMetrics object with all fields initialised to zero / empty
 * when the accounts array is empty.
 */
export function computeDashboardMetrics(accounts: AccountInput[]): DashboardMetrics {
  const totalAssets = accounts
    .filter((a) => ASSET_TYPES.includes(a.type))
    .reduce((sum, a) => sum + a.balance, 0)

  const totalDebt = accounts
    .filter((a) => DEBT_TYPES.includes(a.type))
    .reduce((sum, a) => sum + a.balance, 0)

  const netWorth = totalAssets - totalDebt

  const accountBalances = accounts.map((a) => ({
    accountId: a.id,
    name: a.name,
    type: a.type,
    balance: a.balance,
  }))

  return { netWorth, totalAssets, totalDebt, accountBalances }
}
