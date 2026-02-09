import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import { Toaster } from 'sonner'
import { ThemeProvider } from '@/components/theme-provider'
import AppLayout from '@/components/layout/AppLayout'
import LoginPage from '@/pages/LoginPage'
import RegisterPage from '@/pages/RegisterPage'
import DashboardPage from '@/pages/DashboardPage'
import ExpensesPage from '@/pages/ExpensesPage'
import MerchantsPage from '@/pages/MerchantsPage'
import SettingsPage from '@/pages/SettingsPage'
import RecurringExpensesPage from '@/pages/RecurringExpensesPage'

export default function App() {
  return (
    <ThemeProvider defaultTheme="light" storageKey="ui-theme">
      <BrowserRouter>
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/register" element={<RegisterPage />} />
          <Route element={<AppLayout />}>
            <Route path="/" element={<DashboardPage />} />
            <Route path="/expenses" element={<ExpensesPage />} />
            <Route path="/recurring" element={<RecurringExpensesPage />} />
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
    </ThemeProvider>
  )
}
