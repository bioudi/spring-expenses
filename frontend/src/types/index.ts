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
  accountId: string | null
}

export interface ExpenseRequest {
  amount: number
  merchant: string
  category?: string
  paymentMethod: string
  cardName?: string | null
  timestamp?: string | null
  notes?: string | null
  accountId?: string | null
}

// PUT /api/expenses/{id} — backend ExpenseRequest no longer carries
// paymentMethod/cardName (removed in PR #21). Sending them causes
// IllegalArgumentException via @JsonAnySetter → HTTP 400. The update
// payload is a strict subset of the create payload.
export type ExpenseUpdateRequest = Omit<
  ExpenseRequest,
  'paymentMethod' | 'cardName'
>

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

export interface AccountBalance {
  id: string
  name: string
  balance: number
  type: 'BASE' | 'SAVINGS' | 'EMERGENCY' | 'CREDIT'
}

export interface DashboardResponse {
  netWorth: number
  totalAssets: number
  totalDebt: number
  accountBalances: AccountBalance[]
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
  notes?: string | null
  frequency: RecurrenceFrequency
  dayOfWeek?: DayOfWeek | null
  dayOfMonth?: number | null
  startDate: string
  endDate?: string | null
  accountId?: string | null
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

export interface BudgetSuggestionResponse {
  categories: string[]
  suggestedLimit: number
  reasoning: string
}

// Account types
export type AccountType = 'BASE' | 'SAVINGS' | 'EMERGENCY' | 'CREDIT'

export interface AccountResponse {
  id: string
  name: string
  balance: number
  type: AccountType
  createdAt: string
  updatedAt: string
}

export interface AccountRequest {
  name: string
  balance?: number
  type: AccountType
}

// Income types
export type IncomeCategory = 'PAYCHECK' | 'REFUND' | 'TAX_RETURN'
export type IncomeType = 'CASH' | 'TRANSFER'

export interface IncomeResponse {
  id: string
  name: string
  amount: number
  type: IncomeType
  category: IncomeCategory
  accountId: string | null
  timestamp: string
  notes: string | null
  createdAt: string
  updatedAt: string
}

export interface IncomeRequest {
  name: string
  amount: number
  type: IncomeType
  category: IncomeCategory
  accountId?: string | null
  timestamp?: string | null
  notes?: string | null
}

export interface RecurringExpenseResponse {
  id: string
  amount: number
  category: string
  merchant: string
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
  accountId: string | null
}

// Recurring income — mirrors RecurringExpenseRequest/Response but uses
// IncomeType / IncomeCategory / `name` instead of merchant/category.
export interface RecurringIncomeRequest {
  name: string
  type: IncomeType
  category: IncomeCategory
  amount: number
  notes?: string | null
  frequency: RecurrenceFrequency
  dayOfWeek?: DayOfWeek | null
  dayOfMonth?: number | null
  startDate: string
  endDate?: string | null
  accountId?: string | null
}

export interface RecurringIncomeResponse {
  id: string
  name: string
  type: IncomeType
  category: IncomeCategory
  amount: number
  notes: string | null
  frequency: RecurrenceFrequency
  dayOfWeek: DayOfWeek | null
  dayOfMonth: number | null
  startDate: string
  endDate: string | null
  nextOccurrence: string
  active: boolean
  accountId: string | null
  createdAt: string
  updatedAt: string
}

// Transfer types — mirrors backend TransferRequest / TransferResponse.
// The response carries an `AccountSnapshot` per side so the success card can
// render post-transfer balances without an extra GET.
export interface TransferRequest {
  fromAccountId: string
  toAccountId: string
  amount: number
  description?: string | null
}

export interface TransferAccountSnapshot {
  id: string
  name: string
  type: AccountType
  balance: number
}

export interface TransferResponse {
  transferId: string
  fromAccount: TransferAccountSnapshot
  toAccount: TransferAccountSnapshot
  amount: number
  description: string | null
  timestamp: string
}
