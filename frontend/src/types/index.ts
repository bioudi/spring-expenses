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
