import { useEffect, useState, useMemo } from 'react'
import { Doughnut, Bar } from 'react-chartjs-2'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Card, CardContent, CardHeader, CardTitle, CardDescription, CardFooter } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { CATEGORY_COLORS } from '@/lib/categories'
import { formatMoney, toISODate } from '@/lib/formatters'
import { cn } from '@/lib/utils'
import { useTheme } from '@/components/theme-provider'
import type { DashboardResponse, Period, PeriodSummary, CategoryBreakdown, MerchantSummary, DailySpending, BudgetResponse } from '@/types'
import {
  addDays, subDays, addWeeks, subWeeks, addMonths, subMonths, addYears, subYears,
  format, startOfWeek, endOfWeek, isToday
} from 'date-fns'

const PERIODS: { key: Period; label: string }[] = [
  { key: 'today', label: 'Today' },
  { key: 'week', label: 'Week' },
  { key: 'month', label: 'Month' },
  { key: 'year', label: 'Year' },
]

function formatPeriodLabel(period: Period, date: Date): string {
  switch (period) {
    case 'today':
      return isToday(date) ? 'Today' : format(date, 'EEEE, MMM d, yyyy')
    case 'week': {
      const start = startOfWeek(date, { weekStartsOn: 1 })
      const end = endOfWeek(date, { weekStartsOn: 1 })
      return `${format(start, 'MMM d')} – ${format(end, 'MMM d, yyyy')}`
    }
    case 'month':
      return format(date, 'MMMM yyyy')
    case 'year':
      return format(date, 'yyyy')
  }
  return ''
}

function navigateDate(date: Date, period: Period, dir: 1 | -1): Date {
  switch (period) {
    case 'today': return dir === 1 ? addDays(date, 1) : subDays(date, 1)
    case 'week': return dir === 1 ? addWeeks(date, 1) : subWeeks(date, 1)
    case 'month': return dir === 1 ? addMonths(date, 1) : subMonths(date, 1)
    case 'year': return dir === 1 ? addYears(date, 1) : subYears(date, 1)
  }
  return date
}

function resolveThemeColors() {
  const style = getComputedStyle(document.documentElement)
  const border = style.getPropertyValue('--border').trim()
  const mutedFg = style.getPropertyValue('--muted-foreground').trim()
  const chart1 = style.getPropertyValue('--chart-1').trim()
  return {
    gridColor: border ? `hsl(${border})` : 'hsl(240 5.9% 90%)',
    tickColor: mutedFg ? `hsl(${mutedFg})` : 'hsl(240 3.8% 46.1%)',
    chartColor: chart1 ? `hsl(${chart1})` : 'hsl(12 76% 61%)',
    chartColorAlpha: chart1 ? `hsl(${chart1} / 0.6)` : 'hsl(12 76% 61% / 0.6)',
  }
}

export default function DashboardPage() {
  const { theme } = useTheme()
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null)
  const [period, setPeriod] = useState<Period>('month')
  const [refDate, setRefDate] = useState(new Date())
  const [loading, setLoading] = useState(true)
  const [budgets, setBudgets] = useState<BudgetResponse[]>([])
  const [themeColors, setThemeColors] = useState(resolveThemeColors)

  // Re-resolve CSS variable colors after theme class is applied (needs a frame)
  useEffect(() => {
    const id = requestAnimationFrame(() => setThemeColors(resolveThemeColors()))
    return () => cancelAnimationFrame(id)
  }, [theme])

  const { gridColor, tickColor, chartColor, chartColorAlpha } = themeColors

  useEffect(() => {
    setLoading(true)
    api.getDashboard(toISODate(refDate))
      .then(setDashboard)
      .finally(() => setLoading(false))
  }, [refDate])

  useEffect(() => {
    api.getBudgets().then(setBudgets).catch(() => {})
  }, [])

  const data: PeriodSummary | null = dashboard ? dashboard[period] : null

  const categoryChartData = useMemo(() => {
    if (!data || !data.categoryBreakdown || Object.keys(data.categoryBreakdown).length === 0) return null
    const entries = Object.entries(data.categoryBreakdown).sort(
      (a, b) => (b[1] as CategoryBreakdown).total - (a[1] as CategoryBreakdown).total
    )
    return {
      labels: entries.map(([k]) => k),
      datasets: [{
        data: entries.map(([, v]) => (v as CategoryBreakdown).total),
        backgroundColor: entries.map(([k]) => CATEGORY_COLORS[k] || '#27272a'),
        borderWidth: 0,
      }],
    }
  }, [data])

  const merchantChartData = useMemo(() => {
    if (!data || !data.topMerchants || data.topMerchants.length === 0) return null
    return {
      labels: data.topMerchants.map((m: MerchantSummary) => m.merchant),
      datasets: [{
        data: data.topMerchants.map((m: MerchantSummary) => m.total),
        backgroundColor: chartColor,
        borderRadius: 4,
      }],
    }
  }, [data, chartColor])

  const dailyChartData = useMemo(() => {
    if (!data || !data.dailySpending || data.dailySpending.length === 0) return null
    return {
      labels: data.dailySpending.map((d: DailySpending) => {
        const date = new Date(d.date + 'T00:00:00')
        return format(date, 'MMM d')
      }),
      datasets: [{
        label: 'Spending',
        data: data.dailySpending.map((d: DailySpending) => d.total),
        backgroundColor: chartColorAlpha,
        borderRadius: 4,
      }],
    }
  }, [data, chartColorAlpha])

  const categoryCount = data?.categoryBreakdown ? Object.keys(data.categoryBreakdown).length : 0

  return (
    <div className="flex flex-1 flex-col gap-4 py-4 md:gap-6 md:py-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4 px-4 lg:px-6">
        <div>
          <h2 className="text-2xl font-bold tracking-tight">Dashboard</h2>
          <p className="text-sm text-muted-foreground">Your expense overview and analytics.</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" className="h-8 w-8" onClick={() => setRefDate(navigateDate(refDate, period, -1))}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm font-medium min-w-[140px] sm:min-w-[180px] text-center">
            {formatPeriodLabel(period, refDate)}
          </span>
          <Button variant="outline" size="icon" className="h-8 w-8" onClick={() => setRefDate(navigateDate(refDate, period, 1))}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {/* Period Tabs */}
      <div className="px-4 lg:px-6">
        <div className="inline-flex h-10 items-center justify-center rounded-md bg-muted p-1 text-muted-foreground">
          {PERIODS.map((p) => (
            <button
              key={p.key}
              onClick={() => setPeriod(p.key)}
              className={cn(
                'inline-flex items-center justify-center whitespace-nowrap rounded-sm px-3 py-1.5 text-sm font-medium ring-offset-background transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
                period === p.key
                  ? 'bg-background text-foreground shadow-sm'
                  : 'hover:bg-background/50 hover:text-foreground'
              )}
            >
              {p.label}
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">Loading dashboard...</div>
      ) : !data ? (
        <div className="flex items-center justify-center py-16 text-sm text-muted-foreground">No data available</div>
      ) : (
        <>
          {/* Stat Cards */}
          <div className="grid grid-cols-1 gap-4 px-4 lg:px-6 sm:grid-cols-2 xl:grid-cols-4">
            <Card className="@container/card">
              <CardHeader>
                <CardDescription>Total Spent</CardDescription>
                <CardTitle className="text-2xl font-semibold tabular-nums">
                  {formatMoney(data.totalSpent)}
                </CardTitle>
              </CardHeader>
              <CardFooter className="flex-col items-start gap-1 text-sm">
                <div className="text-muted-foreground">
                  {formatPeriodLabel(period, refDate)}
                </div>
              </CardFooter>
            </Card>
            <Card className="@container/card">
              <CardHeader>
                <CardDescription>Transactions</CardDescription>
                <CardTitle className="text-2xl font-semibold tabular-nums">
                  {data.transactionCount}
                </CardTitle>
              </CardHeader>
              <CardFooter className="flex-col items-start gap-1 text-sm">
                <div className="text-muted-foreground">
                  {formatPeriodLabel(period, refDate)}
                </div>
              </CardFooter>
            </Card>
            <Card className="@container/card">
              <CardHeader>
                <CardDescription>Avg / Transaction</CardDescription>
                <CardTitle className="text-2xl font-semibold tabular-nums">
                  {formatMoney(data.avgPerTransaction)}
                </CardTitle>
              </CardHeader>
              <CardFooter className="flex-col items-start gap-1 text-sm">
                <div className="text-muted-foreground">
                  {data.transactionCount} transaction{data.transactionCount !== 1 ? 's' : ''} this period
                </div>
              </CardFooter>
            </Card>
            <Card className="@container/card">
              <CardHeader>
                <CardDescription>Categories</CardDescription>
                <CardTitle className="text-2xl font-semibold tabular-nums">
                  {categoryCount}
                </CardTitle>
              </CardHeader>
              <CardFooter className="flex-col items-start gap-1 text-sm">
                <div className="text-muted-foreground">
                  Distinct categories used
                </div>
              </CardFooter>
            </Card>
          </div>

          {/* Charts Row */}
          <div className="grid grid-cols-1 gap-4 px-4 lg:px-6 lg:grid-cols-2">
            <Card>
              <CardHeader>
                <CardTitle>Category Breakdown</CardTitle>
                <CardDescription>Spending distribution by category</CardDescription>
              </CardHeader>
              <CardContent>
                {categoryChartData ? (
                  <div className="h-[280px] sm:h-[280px] flex items-center justify-center">
                    <Doughnut
                      data={categoryChartData}
                      options={{
                        responsive: true,
                        maintainAspectRatio: false,
                        cutout: '65%',
                        plugins: {
                          legend: {
                            position: window.innerWidth < 640 ? 'bottom' as const : 'right' as const,
                            labels: { color: tickColor, font: { size: 11 }, padding: 8, boxWidth: 12 },
                          },
                        },
                      }}
                    />
                  </div>
                ) : (
                  <div className="h-[280px] flex items-center justify-center text-sm text-muted-foreground">No category data</div>
                )}
              </CardContent>
            </Card>

            <Card>
              <CardHeader>
                <CardTitle>Top Merchants</CardTitle>
                <CardDescription>Highest spending merchants</CardDescription>
              </CardHeader>
              <CardContent>
                {merchantChartData ? (
                  <div className="h-[280px]">
                    <Bar
                      data={merchantChartData}
                      options={{
                        responsive: true,
                        maintainAspectRatio: false,
                        indexAxis: 'y' as const,
                        plugins: { legend: { display: false } },
                        scales: {
                          x: { ticks: { color: tickColor }, grid: { color: gridColor } },
                          y: { ticks: { color: tickColor }, grid: { display: false } },
                        },
                      }}
                    />
                  </div>
                ) : (
                  <div className="h-[280px] flex items-center justify-center text-sm text-muted-foreground">No merchant data</div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Budget Overview */}
          {budgets.length > 0 && (
            <div className="px-4 lg:px-6">
              <Card>
                <CardHeader>
                  <CardTitle>Budget Overview</CardTitle>
                  <CardDescription>Monthly spending limits by category</CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {[...budgets]
                      .sort((a, b) => b.percentUsed - a.percentUsed)
                      .map((budget) => (
                        <div key={budget.id} className="space-y-1.5">
                          <div className="flex items-center justify-between">
                            <div className="flex items-center gap-2 flex-wrap">
                              {budget.categories.map((cat) => (
                                <div key={cat} className="flex items-center gap-1">
                                  <div className="h-2.5 w-2.5 rounded-full shrink-0" style={{ backgroundColor: CATEGORY_COLORS[cat] || '#27272a' }} />
                                  <span className="text-sm font-medium">{cat}</span>
                                </div>
                              ))}
                            </div>
                            <span className="text-sm text-muted-foreground whitespace-nowrap ml-2">
                              {formatMoney(budget.spent)} / {formatMoney(budget.monthlyLimit)}
                            </span>
                          </div>
                          <div className="flex items-center gap-2">
                            <div className="flex-1 h-2 bg-muted rounded-full overflow-hidden">
                              <div
                                className="h-full rounded-full transition-all"
                                style={{
                                  width: `${Math.min(budget.percentUsed, 100)}%`,
                                  backgroundColor: budget.percentUsed >= 100 ? '#ef4444' : budget.percentUsed >= 80 ? '#eab308' : '#22c55e',
                                }}
                              />
                            </div>
                            <span className="text-xs text-muted-foreground w-10 text-right">{budget.percentUsed.toFixed(0)}%</span>
                          </div>
                          {budget.percentUsed >= 100 && (
                            <p className="text-xs text-red-500 font-medium">Over budget by {formatMoney(Math.abs(budget.remaining))}</p>
                          )}
                        </div>
                      ))}
                  </div>
                </CardContent>
              </Card>
            </div>
          )}

          {/* Daily Spending */}
          <div className="px-4 lg:px-6">
            <Card>
              <CardHeader>
                <CardTitle>Daily Spending</CardTitle>
                <CardDescription>Spending over time for this period</CardDescription>
              </CardHeader>
              <CardContent>
                {dailyChartData ? (
                  <div className="h-[250px]">
                    <Bar
                      data={dailyChartData}
                      options={{
                        responsive: true,
                        maintainAspectRatio: false,
                        plugins: { legend: { display: false } },
                        scales: {
                          x: { ticks: { color: tickColor, maxRotation: 45 }, grid: { display: false } },
                          y: { ticks: { color: tickColor }, grid: { color: gridColor } },
                        },
                      }}
                    />
                  </div>
                ) : (
                  <div className="h-[250px] flex items-center justify-center text-sm text-muted-foreground">No daily data</div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Category Details */}
          {data.categoryBreakdown && Object.keys(data.categoryBreakdown).length > 0 && (
            <div className="px-4 lg:px-6">
              <Card>
                <CardHeader>
                  <CardTitle>Category Details</CardTitle>
                  <CardDescription>Detailed breakdown by category</CardDescription>
                </CardHeader>
                <CardContent>
                  {/* Desktop table */}
                  <div className="hidden sm:block">
                    <Table>
                      <TableHeader>
                        <TableRow>
                          <TableHead>Category</TableHead>
                          <TableHead className="text-right">Total</TableHead>
                          <TableHead className="text-right">Count</TableHead>
                          <TableHead className="text-right hidden md:table-cell">Avg</TableHead>
                          <TableHead className="text-right">%</TableHead>
                        </TableRow>
                      </TableHeader>
                      <TableBody>
                        {Object.entries(data.categoryBreakdown)
                          .sort((a, b) => (b[1] as CategoryBreakdown).total - (a[1] as CategoryBreakdown).total)
                          .map(([cat, rawCb]) => {
                            const cb = rawCb as CategoryBreakdown
                            return (
                              <TableRow key={cat}>
                                <TableCell>
                                  <div className="flex items-center gap-2">
                                    <div className="h-2.5 w-2.5 rounded-full shrink-0" style={{ backgroundColor: CATEGORY_COLORS[cat] || '#27272a' }} />
                                    <span className="text-sm font-medium">{cat}</span>
                                  </div>
                                </TableCell>
                                <TableCell className="text-right font-medium">{formatMoney(cb.total)}</TableCell>
                                <TableCell className="text-right text-muted-foreground">{cb.count}</TableCell>
                                <TableCell className="text-right text-muted-foreground hidden md:table-cell">{formatMoney(cb.avgPerTransaction)}</TableCell>
                                <TableCell className="text-right text-muted-foreground">{cb.percentage.toFixed(1)}%</TableCell>
                              </TableRow>
                            )
                          })}
                      </TableBody>
                    </Table>
                  </div>
                  {/* Mobile cards */}
                  <div className="flex flex-col gap-3 sm:hidden">
                    {Object.entries(data.categoryBreakdown)
                      .sort((a, b) => (b[1] as CategoryBreakdown).total - (a[1] as CategoryBreakdown).total)
                      .map(([cat, rawCb]) => {
                        const cb = rawCb as CategoryBreakdown
                        return (
                          <div key={cat} className="flex items-center justify-between rounded-lg border p-3">
                            <div className="flex items-center gap-2 min-w-0">
                              <div className="h-2.5 w-2.5 rounded-full shrink-0" style={{ backgroundColor: CATEGORY_COLORS[cat] || '#27272a' }} />
                              <div className="min-w-0">
                                <p className="text-sm font-medium truncate">{cat}</p>
                                <p className="text-xs text-muted-foreground">{cb.count} txn · {cb.percentage.toFixed(1)}%</p>
                              </div>
                            </div>
                            <span className="text-sm font-medium tabular-nums ml-2">{formatMoney(cb.total)}</span>
                          </div>
                        )
                      })}
                  </div>
                </CardContent>
              </Card>
            </div>
          )}

          {/* Top Merchants List */}
          {data.topMerchants && data.topMerchants.length > 0 && (
            <div className="px-4 lg:px-6">
              <Card>
                <CardHeader>
                  <CardTitle>Top Merchants</CardTitle>
                  <CardDescription>Most frequent and highest spending merchants</CardDescription>
                </CardHeader>
                <CardContent>
                  <div className="space-y-4">
                    {data.topMerchants.map((m: MerchantSummary, i: number) => (
                      <div key={m.merchant} className="flex items-center">
                        <span className="text-sm font-medium text-muted-foreground w-8">{i + 1}.</span>
                        <div className="flex-1 min-w-0">
                          <p className="text-sm font-medium truncate">{m.merchant}</p>
                          <p className="text-xs text-muted-foreground">{m.count} transaction{m.count !== 1 ? 's' : ''}</p>
                        </div>
                        <span className="text-sm font-medium">{formatMoney(m.total)}</span>
                      </div>
                    ))}
                  </div>
                </CardContent>
              </Card>
            </div>
          )}

          {data.transactionCount === 0 && (
            <div className="flex flex-col items-center justify-center py-16 text-sm text-muted-foreground">
              <p>No expenses recorded for this period.</p>
            </div>
          )}
        </>
      )}
    </div>
  )
}
