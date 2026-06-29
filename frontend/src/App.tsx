import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Toaster } from 'sonner'
import { ThemeProvider } from '@/components/theme-provider'
import { I18nProvider } from '@/i18n'
import AppLayout from '@/components/layout/AppLayout'
import LandingPage from '@/pages/LandingPage'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import DashboardPage from '@/pages/DashboardPage'
import ExpensesPage from '@/pages/ExpensesPage'
import AccountsPage from '@/pages/AccountsPage'
import MerchantsPage from '@/pages/MerchantsPage'
import SettingsPage from '@/pages/SettingsPage'
import RecurringExpensesPage from '@/pages/RecurringExpensesPage'
import RecurringIncomesPage from '@/pages/RecurringIncomesPage'
import BudgetsPage from '@/pages/BudgetsPage'
import IncomesPage from '@/pages/IncomesPage'
import TransfersPage from '@/pages/TransfersPage'

export default function App() {
  return (
    <ThemeProvider defaultTheme="light" storageKey="ui-theme">
      <I18nProvider>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<LandingPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route element={<AppLayout />}>
            <Route path="/dashboard" element={<DashboardPage />} />
            <Route path="/expenses" element={<ExpensesPage />} />
            <Route path="/recurring" element={<RecurringExpensesPage />} />
            <Route path="/recurring-incomes" element={<RecurringIncomesPage />} />
            <Route path="/accounts" element={<AccountsPage />} />
            <Route path="/budgets" element={<BudgetsPage />} />
            <Route path="/incomes" element={<IncomesPage />} />
            <Route path="/transfers" element={<TransfersPage />} />
            <Route path="/merchants" element={<MerchantsPage />} />
            <Route path="/settings" element={<SettingsPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
        <Toaster
          position="bottom-right"
          richColors
        />
      </BrowserRouter>
      </I18nProvider>
    </ThemeProvider>
  )
}
