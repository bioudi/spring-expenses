import { useEffect, useState, useMemo } from 'react'
import { Doughnut, Bar } from 'react-chartjs-2'
import { ChevronLeft, ChevronRight, DollarSign, Receipt, TrendingUp } from 'lucide-react'
import { Card, CardContent } from '@/components/ui/card'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { api } from '@/lib/api'
import { CATEGORY_COLORS } from '@/lib/categories'
import { formatMoney, toISODate, formatDateShort } from '@/lib/formatters'
import { cn } from '@/lib/utils'
import type { DashboardResponse, Period, PeriodSummary, CategoryBreakdown, MerchantSummary, DailySpending } from '@/types'
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
      return `${format(start, 'MMM d')} - ${format(end, 'MMM d, yyyy')}`
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

export default function DashboardPage() {
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null)
  const [period, setPeriod] = useState<Period>('month')
  const [refDate, setRefDate] = useState(new Date())
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    setLoading(true)
    api.getDashboard(toISODate(refDate))
      .then(setDashboard)
      .finally(() => setLoading(false))
  }, [refDate])

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
        backgroundColor: entries.map(([k]) => CATEGORY_COLORS[k] || '#484f58'),
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
        backgroundColor: '#58a6ff',
        borderRadius: 4,
      }],
    }
  }, [data])

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
        backgroundColor: 'rgba(88, 166, 255, 0.6)',
        borderRadius: 4,
      }],
    }
  }, [data])

  return (
    <div className="max-w-6xl mx-auto p-6 space-y-6">
      {/* Period Selector */}
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div className="flex bg-card border rounded-lg p-1 gap-1">
          {PERIODS.map((p) => (
            <button
              key={p.key}
              onClick={() => setPeriod(p.key)}
              className={cn(
                'px-4 py-2 rounded-md text-sm font-medium transition-colors',
                period === p.key
                  ? 'bg-primary text-primary-foreground'
                  : 'text-muted-foreground hover:text-foreground hover:bg-secondary'
              )}
            >
              {p.label}
            </button>
          ))}
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="icon" className="h-8 w-8" onClick={() => setRefDate(navigateDate(refDate, period, -1))}>
            <ChevronLeft className="h-4 w-4" />
          </Button>
          <span className="text-sm font-medium min-w-[180px] text-center">
            {formatPeriodLabel(period, refDate)}
          </span>
          <Button variant="outline" size="icon" className="h-8 w-8" onClick={() => setRefDate(navigateDate(refDate, period, 1))}>
            <ChevronRight className="h-4 w-4" />
          </Button>
        </div>
      </div>

      {loading ? (
        <div className="text-center py-12 text-muted-foreground">Loading dashboard...</div>
      ) : !data ? (
        <div className="text-center py-12 text-muted-foreground">No data available</div>
      ) : (
        <>
          {/* Stats Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-primary/10">
                    <DollarSign className="h-5 w-5 text-primary" />
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground uppercase tracking-wider">Total Spent</p>
                    <p className="text-2xl font-bold text-primary">{formatMoney(data.totalSpent)}</p>
                  </div>
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-purple-500/10">
                    <Receipt className="h-5 w-5 text-purple-400" />
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground uppercase tracking-wider">Transactions</p>
                    <p className="text-2xl font-bold text-purple-400">{data.transactionCount}</p>
                  </div>
                </div>
              </CardContent>
            </Card>
            <Card>
              <CardContent className="p-5">
                <div className="flex items-center gap-3">
                  <div className="p-2 rounded-lg bg-violet-500/10">
                    <TrendingUp className="h-5 w-5 text-violet-400" />
                  </div>
                  <div>
                    <p className="text-xs text-muted-foreground uppercase tracking-wider">Avg / Transaction</p>
                    <p className="text-2xl font-bold text-violet-400">{formatMoney(data.avgPerTransaction)}</p>
                  </div>
                </div>
              </CardContent>
            </Card>
          </div>

          {/* Charts */}
          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Category Doughnut */}
            <Card>
              <CardContent className="p-5">
                <h3 className="text-sm font-semibold mb-4">Category Breakdown</h3>
                {categoryChartData ? (
                  <div className="h-[280px] flex items-center justify-center">
                    <Doughnut
                      data={categoryChartData}
                      options={{
                        responsive: true,
                        maintainAspectRatio: false,
                        cutout: '65%',
                        plugins: {
                          legend: {
                            position: 'right' as const,
                            labels: { color: '#8b949e', font: { size: 11 }, padding: 8, boxWidth: 12 },
                          },
                        },
                      }}
                    />
                  </div>
                ) : (
                  <div className="h-[280px] flex items-center justify-center text-muted-foreground text-sm">No data</div>
                )}
              </CardContent>
            </Card>

            {/* Top Merchants Bar */}
            <Card>
              <CardContent className="p-5">
                <h3 className="text-sm font-semibold mb-4">Top Merchants</h3>
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
                          x: { ticks: { color: '#8b949e' }, grid: { color: '#2d333b' } },
                          y: { ticks: { color: '#8b949e' }, grid: { display: false } },
                        },
                      }}
                    />
                  </div>
                ) : (
                  <div className="h-[280px] flex items-center justify-center text-muted-foreground text-sm">No data</div>
                )}
              </CardContent>
            </Card>
          </div>

          {/* Daily Spending */}
          <Card>
            <CardContent className="p-5">
              <h3 className="text-sm font-semibold mb-4">Daily Spending</h3>
              {dailyChartData ? (
                <div className="h-[250px]">
                  <Bar
                    data={dailyChartData}
                    options={{
                      responsive: true,
                      maintainAspectRatio: false,
                      plugins: { legend: { display: false } },
                      scales: {
                        x: { ticks: { color: '#8b949e', maxRotation: 45 }, grid: { display: false } },
                        y: { ticks: { color: '#8b949e' }, grid: { color: '#2d333b' } },
                      },
                    }}
                  />
                </div>
              ) : (
                <div className="h-[250px] flex items-center justify-center text-muted-foreground text-sm">No data</div>
              )}
            </CardContent>
          </Card>

          {/* Category Details Table */}
          {data.categoryBreakdown && Object.keys(data.categoryBreakdown).length > 0 && (
            <Card>
              <CardContent className="p-5">
                <h3 className="text-sm font-semibold mb-4">Category Details</h3>
                <Table>
                  <TableHeader>
                    <TableRow>
                      <TableHead>Category</TableHead>
                      <TableHead className="text-right">Total</TableHead>
                      <TableHead className="text-right">Count</TableHead>
                      <TableHead className="text-right">Avg</TableHead>
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
                                <div className="w-3 h-3 rounded-full shrink-0" style={{ backgroundColor: CATEGORY_COLORS[cat] || '#484f58' }} />
                                {cat}
                              </div>
                            </TableCell>
                            <TableCell className="text-right">{formatMoney(cb.total)}</TableCell>
                            <TableCell className="text-right">{cb.count}</TableCell>
                            <TableCell className="text-right">{formatMoney(cb.avgPerTransaction)}</TableCell>
                            <TableCell className="text-right">{cb.percentage.toFixed(1)}%</TableCell>
                          </TableRow>
                        )
                      })}
                  </TableBody>
                </Table>
              </CardContent>
            </Card>
          )}

          {/* Top Merchants List */}
          {data.topMerchants && data.topMerchants.length > 0 && (
            <Card>
              <CardContent className="p-5">
                <h3 className="text-sm font-semibold mb-4">Top Merchants</h3>
                <div className="space-y-3">
                  {data.topMerchants.map((m: MerchantSummary, i: number) => (
                    <div key={m.merchant} className="flex items-center gap-3">
                      <span className="text-lg font-bold text-primary w-8">{i + 1}</span>
                      <div className="flex-1">
                        <p className="text-sm font-medium">{m.merchant}</p>
                        <p className="text-xs text-muted-foreground">{m.count} transaction{m.count !== 1 ? 's' : ''}</p>
                      </div>
                      <span className="text-sm font-semibold text-muted-foreground">{formatMoney(m.total)}</span>
                    </div>
                  ))}
                </div>
              </CardContent>
            </Card>
          )}

          {data.transactionCount === 0 && (
            <div className="text-center py-12 text-muted-foreground">
              No expenses recorded for this period.
            </div>
          )}
        </>
      )}
    </div>
  )
}
