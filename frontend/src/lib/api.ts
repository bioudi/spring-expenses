import type {
  DashboardResponse,
  ExpenseRequest,
  ExpenseResponse,
  MerchantCategory,
  UserProfile,
  RegisterRequest,
  ChangePasswordRequest,
  RecurringExpenseRequest,
  RecurringExpenseResponse,
} from '@/types'

class ApiError extends Error {
  status: number
  constructor(message: string, status: number) {
    super(message)
    this.status = status
  }
}

async function apiFetch<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...options,
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      'X-Requested-With': 'XMLHttpRequest',
      ...options?.headers,
    },
  })

  if (res.status === 401) {
    window.location.href = '/login'
    throw new ApiError('Unauthorized', 401)
  }

  if (!res.ok) {
    const error = await res.json().catch(() => ({ error: 'Request failed' }))
    throw new ApiError(error.error || error.message || 'Request failed', res.status)
  }

  if (res.status === 204) return undefined as T
  return res.json()
}

export const api = {
  getDashboard: (date: string) =>
    apiFetch<DashboardResponse>(`/api/expenses/dashboard?date=${date}`),
  getExpenses: () =>
    apiFetch<ExpenseResponse[]>('/api/expenses'),
  createExpense: (data: ExpenseRequest) =>
    apiFetch<ExpenseResponse>('/api/expenses', { method: 'POST', body: JSON.stringify(data) }),
  updateExpense: (id: string, data: ExpenseRequest) =>
    apiFetch<ExpenseResponse>(`/api/expenses/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteExpense: (id: string) =>
    apiFetch<ExpenseResponse>(`/api/expenses/${id}`, { method: 'DELETE' }),
  getMerchants: () =>
    apiFetch<MerchantCategory[]>('/api/merchants'),
  updateMerchant: (id: string, category: string) =>
    apiFetch<MerchantCategory>(`/api/merchants/${id}`, { method: 'PUT', body: JSON.stringify({ category }) }),
  deleteMerchant: (id: string) =>
    apiFetch<void>(`/api/merchants/${id}`, { method: 'DELETE' }),
  getProfile: () =>
    apiFetch<UserProfile>('/api/user/profile'),
  changePassword: (data: ChangePasswordRequest) =>
    apiFetch<{ message: string }>('/api/user/password', { method: 'PUT', body: JSON.stringify(data) }),
  regenerateApiKey: () =>
    apiFetch<{ apiKey: string }>('/api/user/regenerate-api-key', { method: 'POST' }),
  register: (data: RegisterRequest) =>
    apiFetch<{ message: string }>('/api/auth/register', { method: 'POST', body: JSON.stringify(data) }),
  getRecurringExpenses: () =>
    apiFetch<RecurringExpenseResponse[]>('/api/recurring-expenses'),
  createRecurringExpense: (data: RecurringExpenseRequest) =>
    apiFetch<RecurringExpenseResponse>('/api/recurring-expenses', { method: 'POST', body: JSON.stringify(data) }),
  getRecurringExpense: (id: string) =>
    apiFetch<RecurringExpenseResponse>(`/api/recurring-expenses/${id}`),
  updateRecurringExpense: (id: string, data: RecurringExpenseRequest) =>
    apiFetch<RecurringExpenseResponse>(`/api/recurring-expenses/${id}`, { method: 'PUT', body: JSON.stringify(data) }),
  deleteRecurringExpense: (id: string) =>
    apiFetch<void>(`/api/recurring-expenses/${id}`, { method: 'DELETE' }),
  toggleRecurringExpense: (id: string) =>
    apiFetch<RecurringExpenseResponse>(`/api/recurring-expenses/${id}/toggle`, { method: 'PATCH' }),
  createRecurringFromExpense: (expenseId: string, data: RecurringExpenseRequest) =>
    apiFetch<RecurringExpenseResponse>(`/api/recurring-expenses/from-expense/${expenseId}`, { method: 'POST', body: JSON.stringify(data) }),
}
