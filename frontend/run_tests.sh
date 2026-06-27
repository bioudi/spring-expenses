cd /opt/data/spring-expenses/frontend

# Write dashboard.ts
cat > src/lib/dashboard.ts << 'DASHTS'
import type { AccountType } from '@/types'

export interface DashboardMetrics {
  netWorth: number
  totalAssets: number
  totalDebt: number
  accountBalances: AccountBalanceEntry[]
}

export interface AccountBalanceEntry {
  accountId: string
  name: string
  type: AccountType
  balance: number
}

interface AccountInput {
  id: string
  name: string
  balance: number
  type: AccountType
}

export function computeDashboardMetrics(accounts: AccountInput[]): DashboardMetrics {
  let totalAssets = 0
  let totalDebt = 0

  const accountBalances: AccountBalanceEntry[] = accounts.map((a) => {
    if (a.type === 'CREDIT') {
      totalDebt += a.balance
    } else {
      totalAssets += a.balance
    }
    return {
      accountId: a.id,
      name: a.name,
      type: a.type,
      balance: a.balance,
    }
  })

  return {
    netWorth: totalAssets - totalDebt,
    totalAssets,
    totalDebt,
    accountBalances,
  }
}
DASHTS

# Write test file
mkdir -p src/lib/__tests__
cat > src/lib/__tests__/dashboard.test.ts << 'TESTTS'
import { describe, it, expect } from 'vitest'
import { computeDashboardMetrics } from '../dashboard'

describe('computeDashboardMetrics', () => {
  it('returns zero values for an empty accounts list', () => {
    const result = computeDashboardMetrics([])
    expect(result).toEqual({
      netWorth: 0,
      totalAssets: 0,
      totalDebt: 0,
      accountBalances: [],
    })
  })

  it('computes net worth as totalAssets minus totalDebt for mixed accounts', () => {
    const result = computeDashboardMetrics([
      { id: '1', name: 'Chequing', balance: 5000, type: 'BASE' as const },
      { id: '2', name: 'Savings', balance: 12000, type: 'SAVINGS' as const },
      { id: '3', name: 'Emergency', balance: 3000, type: 'EMERGENCY' as const },
      { id: '4', name: 'Visa', balance: 1500, type: 'CREDIT' as const },
    ])
    expect(result.totalAssets).toBe(20000)
    expect(result.totalDebt).toBe(1500)
    expect(result.netWorth).toBe(18500)
  })

  it('returns zero net worth when assets equal debts', () => {
    const result = computeDashboardMetrics([
      { id: '1', name: 'Chequing', balance: 3000, type: 'BASE' as const },
      { id: '2', name: 'Mastercard', balance: 3000, type: 'CREDIT' as const },
    ])
    expect(result.totalAssets).toBe(3000)
    expect(result.totalDebt).toBe(3000)
    expect(result.netWorth).toBe(0)
  })

  it('includes all accounts in accountBalances array with accountId', () => {
    const result = computeDashboardMetrics([
      { id: 'a1', name: 'Chequing', balance: 100, type: 'BASE' as const },
      { id: 'a2', name: 'Visa', balance: 50, type: 'CREDIT' as const },
    ])
    expect(result.accountBalances).toHaveLength(2)
    expect(result.accountBalances[0]).toEqual({
      accountId: 'a1',
      name: 'Chequing',
      type: 'BASE',
      balance: 100,
    })
    expect(result.accountBalances[1]).toEqual({
      accountId: 'a2',
      name: 'Visa',
      type: 'CREDIT',
      balance: 50,
    })
  })

  it('preserves fractional balances', () => {
    const result = computeDashboardMetrics([
      { id: '1', name: 'Chequing', balance: 1234.56, type: 'BASE' as const },
      { id: '2', name: 'Visa', balance: 400.25, type: 'CREDIT' as const },
    ])
    expect(result.netWorth).toBeCloseTo(834.31)
    expect(result.totalAssets).toBeCloseTo(1234.56)
    expect(result.totalDebt).toBeCloseTo(400.25)
  })
})
TESTTS

# Verify files exist
echo "=== Files ==="
ls -la src/lib/dashboard.ts src/lib/__tests__/dashboard.test.ts

# Run vitest
echo "=== Running vitest ==="
npx vitest run --reporter=verbose 2>&1
echo "EXIT=$?"