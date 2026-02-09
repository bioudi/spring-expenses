import { format, parseISO } from 'date-fns'

export function formatMoney(amount: number | string): string {
  const num = typeof amount === 'string' ? parseFloat(amount) : amount
  return `$${num.toFixed(2)}`
}

export function formatDate(dateStr: string): string {
  return format(parseISO(dateStr), 'MMM d, yyyy')
}

export function formatDateTime(dateStr: string): string {
  return format(parseISO(dateStr), 'MMM d, yyyy h:mm a')
}

export function formatDateShort(dateStr: string): string {
  return format(parseISO(dateStr), 'MMM d')
}

export function toISODate(date: Date): string {
  const year = date.getFullYear()
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

export function toLocalDateTimeInput(date?: Date): string {
  const d = date || new Date()
  const offset = d.getTimezoneOffset()
  const local = new Date(d.getTime() - offset * 60 * 1000)
  return local.toISOString().slice(0, 16)
}

const DAY_NAMES: Record<string, string> = {
  MONDAY: 'Monday',
  TUESDAY: 'Tuesday',
  WEDNESDAY: 'Wednesday',
  THURSDAY: 'Thursday',
  FRIDAY: 'Friday',
  SATURDAY: 'Saturday',
  SUNDAY: 'Sunday',
}

function ordinal(n: number): string {
  const s = ['th', 'st', 'nd', 'rd']
  const v = n % 100
  return n + (s[(v - 20) % 10] || s[v] || s[0])
}

export function formatRecurrence(frequency: string, dayOfWeek?: string | null, dayOfMonth?: number | null): string {
  switch (frequency) {
    case 'DAILY':
      return 'Daily'
    case 'WEEKLY':
      return dayOfWeek ? `Every ${DAY_NAMES[dayOfWeek] || dayOfWeek}` : 'Weekly'
    case 'BI_WEEKLY':
      return dayOfWeek ? `Every other ${DAY_NAMES[dayOfWeek] || dayOfWeek}` : 'Bi-weekly'
    case 'MONTHLY':
      return dayOfMonth ? `Monthly on the ${ordinal(dayOfMonth)}` : 'Monthly'
    default:
      return frequency
  }
}
