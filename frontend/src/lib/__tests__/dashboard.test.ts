import { describe, it, expect } from 'vitest'
import { computeDashboardMetrics } from '../dashboard'
import type { AccountInput } from '../dashboard'

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
    const accounts: AccountInput[] = [
      { id: '1', name: 'Chequing', balance: 5000, type: 'BASE' },
      { id: '2', name: 'Savings', balance: 12000, type: 'SAVINGS' },
      { id: '3', name: 'Emergency', balance: 3000, type: 'EMERGENCY' },
      { id: '4', name: 'Visa', balance: 1500, type: 'CREDIT' },
    ]
    const result = computeDashboardMetrics(accounts)
    expect(result.totalAssets).toBe(20000)
    expect(result.totalDebt).toBe(1500)
    expect(result.netWorth).toBe(18500)
  })

  it('returns zero net worth when assets equal debts', () => {
    const accounts: AccountInput[] = [
      { id: '1', name: 'Chequing', balance: 3000, type: 'BASE' },
      { id: '2', name: 'Mastercard', balance: 3000, type: 'CREDIT' },
    ]
    const result = computeDashboardMetrics(accounts)
    expect(result.totalAssets).toBe(3000)
    expect(result.totalDebt).toBe(3000)
    expect(result.netWorth).toBe(0)
  })

  it('returns negative net worth when debts exceed assets', () => {
    const accounts: AccountInput[] = [
      { id: '1', name: 'Chequing', balance: 500, type: 'BASE' },
      { id: '2', name: 'Visa', balance: 2000, type: 'CREDIT' },
    ]
    const result = computeDashboardMetrics(accounts)
    expect(result.netWorth).toBe(-1500)
  })

  it('returns only assets when no credit accounts exist', () => {
    const accounts: AccountInput[] = [
      { id: '1', name: 'Chequing', balance: 2500, type: 'BASE' },
      { id: '2', name: 'Savings', balance: 7500, type: 'SAVINGS' },
    ]
    const result = computeDashboardMetrics(accounts)
    expect(result.totalAssets).toBe(10000)
    expect(result.totalDebt).toBe(0)
    expect(result.netWorth).toBe(10000)
  })

  it('returns only debts when no asset accounts exist', () => {
    const accounts: AccountInput[] = [
      { id: '1', name: 'Visa', balance: 800, type: 'CREDIT' },
    ]
    const result = computeDashboardMetrics(accounts)
    expect(result.totalAssets).toBe(0)
    expect(result.totalDebt).toBe(800)
    expect(result.netWorth).toBe(-800)
  })

  it('includes all accounts in accountBalances array', () => {
    const accounts: AccountInput[] = [
      { id: 'a1', name: 'Chequing', balance: 100, type: 'BASE' },
      { id: 'a2', name: 'Visa', balance: 50, type: 'CREDIT' },
    ]
    const result = computeDashboardMetrics(accounts)
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

  it('handles accounts with zero balance correctly', () => {
    const accounts: AccountInput[] = [
      { id: '1', name: 'Empty Chequing', balance: 0, type: 'BASE' },
      { id: '2', name: 'Paid Card', balance: 0, type: 'CREDIT' },
    ]
    const result = computeDashboardMetrics(accounts)
    expect(result.netWorth).toBe(0)
    expect(result.totalAssets).toBe(0)
    expect(result.totalDebt).toBe(0)
  })

  it('handles accounts with negative balance (overdraft)', () => {
    const accounts: AccountInput[] = [
      { id: '1', name: 'Overdrawn', balance: -200, type: 'BASE' },
      { id: '2', name: 'Visa', balance: 1000, type: 'CREDIT' },
    ]
    const result = computeDashboardMetrics(accounts)
    expect(result.totalAssets).toBe(-200)
    expect(result.totalDebt).toBe(1000)
    expect(result.netWorth).toBe(-1200)
  })

  it('preserves fractional balances', () => {
    const accounts: AccountInput[] = [
      { id: '1', name: 'Chequing', balance: 1234.56, type: 'BASE' },
      { id: '2', name: 'Visa', balance: 400.25, type: 'CREDIT' },
    ]
    const result = computeDashboardMetrics(accounts)
    expect(result.netWorth).toBeCloseTo(834.31)
    expect(result.totalAssets).toBeCloseTo(1234.56)
    expect(result.totalDebt).toBeCloseTo(400.25)
  })
})
