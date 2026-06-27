import { describe, it, expect } from 'vitest'
import { render, screen } from '@testing-library/react'
import { NetWorthCard } from '../NetWorthCard'
import type { AccountInput } from '@/lib/dashboard'

describe('NetWorthCard', () => {
  it('renders with mock data when no accounts prop is supplied', () => {
    render(<NetWorthCard />)

    const valueEl = screen.getByTestId('net-worth-value')
    expect(valueEl).toBeInTheDocument()
    // Mock accounts: chequing 4250.75 + savings 12780.4 + emergency 5000 = 22031.15 assets
    // minus visa 842.1 debt => 21189.05 net worth
    expect(valueEl.textContent).toContain('21,189')
  })

  it('marks the headline value as positive when net worth >= 0', () => {
    render(
      <NetWorthCard
        accounts={[
          { id: '1', name: 'Chequing', balance: 5000, type: 'BASE' },
        ]}
      />,
    )

    const valueEl = screen.getByTestId('net-worth-value')
    expect(valueEl).toHaveAttribute('data-sign', 'positive')
    expect(valueEl.className).toContain('text-green-600')
  })

  it('marks the headline value as negative when net worth < 0', () => {
    render(
      <NetWorthCard
        accounts={[
          { id: '1', name: 'Chequing', balance: 200, type: 'BASE' },
          { id: '2', name: 'Visa', balance: 1500, type: 'CREDIT' },
        ]}
      />,
    )

    const valueEl = screen.getByTestId('net-worth-value')
    expect(valueEl).toHaveAttribute('data-sign', 'negative')
    expect(valueEl.className).toContain('text-red-600')
  })

  it('treats zero net worth as positive (green)', () => {
    render(
      <NetWorthCard
        accounts={[
          { id: '1', name: 'Chequing', balance: 0, type: 'BASE' },
        ]}
      />,
    )

    const valueEl = screen.getByTestId('net-worth-value')
    expect(valueEl).toHaveAttribute('data-sign', 'positive')
    expect(valueEl.textContent).toContain('0')
  })

  it('renders the assets sub-line in green', () => {
    render(
      <NetWorthCard
        accounts={[
          { id: '1', name: 'Chequing', balance: 1000, type: 'BASE' },
          { id: '2', name: 'Visa', balance: 250, type: 'CREDIT' },
        ]}
      />,
    )

    const assetsEl = screen.getByTestId('net-worth-assets')
    expect(assetsEl.className).toContain('text-green-600')
    expect(assetsEl.textContent).toContain('1,000')
  })

  it('renders the debt sub-line in red', () => {
    render(
      <NetWorthCard
        accounts={[
          { id: '1', name: 'Chequing', balance: 1000, type: 'BASE' },
          { id: '2', name: 'Visa', balance: 250, type: 'CREDIT' },
        ]}
      />,
    )

    const debtEl = screen.getByTestId('net-worth-debt')
    expect(debtEl.className).toContain('text-red-600')
    expect(debtEl.textContent).toContain('250')
  })

  it('shows zero assets when only credit accounts are present', () => {
    const accounts: AccountInput[] = [
      { id: '1', name: 'Visa', balance: 750, type: 'CREDIT' },
    ]
    render(<NetWorthCard accounts={accounts} />)

    expect(screen.getByTestId('net-worth-assets').textContent).toContain('0')
    expect(screen.getByTestId('net-worth-debt').textContent).toContain('750')
    expect(screen.getByTestId('net-worth-value')).toHaveAttribute('data-sign', 'negative')
  })

  it('formats money in the supplied locale', () => {
    render(
      <NetWorthCard
        language="fr"
        accounts={[
          { id: '1', name: 'Chequing', balance: 1234.56, type: 'BASE' },
        ]}
      />,
    )

    // French formatting uses non-breaking space as thousands separator and
    // comma as decimal separator (fr-CA).
    const valueEl = screen.getByTestId('net-worth-value')
    expect(valueEl.textContent).toMatch(/1[\s\u00A0\u202F]234,56/)
  })

  it('renders a self-contained card with the dashboard heading', () => {
    render(<NetWorthCard />)

    expect(screen.getByText('Net Worth')).toBeInTheDocument()
    expect(screen.getByText(/Assets:/)).toBeInTheDocument()
    expect(screen.getByText(/Debt:/)).toBeInTheDocument()
  })
})
