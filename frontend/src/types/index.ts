// Backend DTO types

export interface ExpenseResponse {
  id: string
  amount: number
  category: string
  merchant: string
  paymentMethod: string
  cardName: string | null
  timestamp: string
  notes: string | null
  createdAt: string
  recurringExpenseId: string | null
}

export interface ExpenseRequest {
  amount: number
  merchant: string
  category?: string
  paymentMethod: string
  cardName?: string | null
  timestamp?: string | null
  notes?: string | null
}

export interface CategoryBreakdown {
  total: number
  count: number
  percentage: number
  avgPerTransaction: number
}

export interface MerchantSummary {
  merchant: string
  total: number
  count: number
}

export interface DailySpending {
  date: string
  total: number
  count: number
}

export interface PeriodSummary {
  startDate: string
  endDate: string
  totalSpent: number
  transactionCount: number
  avgPerTransaction: number
  categoryBreakdown: Record<string, CategoryBreakdown>
  topMerchants: MerchantSummary[]
  dailySpending: DailySpending[]
}

export interface DashboardResponse {
  today: PeriodSummary
  week: PeriodSummary
  month: PeriodSummary
  year: PeriodSummary
}

export type Period = 'today' | 'week' | 'month' | 'year'

export interface MerchantCategory {
  id: string
  merchantKey: string
  category: string
  createdAt: string
}

export interface UserProfile {
  email: string
  displayName: string | null
  apiKey: string
  createdAt: string
}

export interface RegisterRequest {
  email: string
  password: string
  displayName?: string
}

export interface ChangePasswordRequest {
  currentPassword: string
  newPassword: string
}

export type RecurrenceFrequency = 'DAILY' | 'WEEKLY' | 'BI_WEEKLY' | 'MONTHLY'

export type DayOfWeek = 'MONDAY' | 'TUESDAY' | 'WEDNESDAY' | 'THURSDAY' | 'FRIDAY' | 'SATURDAY' | 'SUNDAY'

export interface RecurringExpenseRequest {
  amount: number
  merchant: string
  category?: string
  paymentMethod?: string
  cardName?: string | null
  notes?: string | null
  frequency: RecurrenceFrequency
  dayOfWeek?: DayOfWeek | null
  dayOfMonth?: number | null
  startDate: string
  endDate?: string | null
}

export interface BudgetRequest {
  categories: string[]
  monthlyLimit: number
}

export interface BudgetResponse {
  id: string
  categories: string[]
  monthlyLimit: number
  spent: number
  remaining: number
  percentUsed: number
  createdAt: string
  updatedAt: string
}

export interface RecurringExpenseResponse {
  id: string
  amount: number
  category: string
  merchant: string
  paymentMethod: string
  cardName: string | null
  notes: string | null
  frequency: RecurrenceFrequency
  dayOfWeek: DayOfWeek | null
  dayOfMonth: number | null
  startDate: string
  endDate: string | null
  nextOccurrence: string
  active: boolean
  createdAt: string
  updatedAt: string
}
