import { format, parseISO } from 'date-fns'
import { enUS, fr } from 'date-fns/locale'

type LocaleKey = 'en' | 'fr'

const dateFnsLocales = { en: enUS, fr }

export function formatMoney(amount: number | string, locale: LocaleKey = 'en'): string {
  const num = typeof amount === 'string' ? parseFloat(amount) : amount
  return new Intl.NumberFormat(locale === 'fr' ? 'fr-CA' : 'en-CA', {
    style: 'currency',
    currency: 'CAD',
  }).format(num)
}

export function formatDate(dateStr: string, locale: LocaleKey = 'en'): string {
  return format(parseISO(dateStr), 'PPP', { locale: dateFnsLocales[locale] })
}

export function formatDateTime(dateStr: string, locale: LocaleKey = 'en'): string {
  return format(parseISO(dateStr), 'PPP p', { locale: dateFnsLocales[locale] })
}

export function formatDateShort(dateStr: string, locale: LocaleKey = 'en'): string {
  return format(parseISO(dateStr), 'MMM d', { locale: dateFnsLocales[locale] })
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

function ordinal(n: number, locale: LocaleKey = 'en'): string {
  if (locale === 'fr') {
    return n === 1 ? '1er' : `${n}e`
  }
  const s = ['th', 'st', 'nd', 'rd']
  const v = n % 100
  return n + (s[(v - 20) % 10] || s[v] || s[0])
}

export function formatRecurrence(
  frequency: string,
  dayOfWeek: string | null | undefined,
  dayOfMonth: number | null | undefined,
  t: (key: string, vars?: Record<string, string | number>) => string,
  locale: LocaleKey = 'en',
): string {
  const dayName = dayOfWeek ? t(`frequency.days.${dayOfWeek}`) : ''
  switch (frequency) {
    case 'DAILY':
      return t('frequency.daily')
    case 'WEEKLY':
      return dayOfWeek ? t('frequency.everyDay', { day: dayName }) : t('frequency.weekly')
    case 'BI_WEEKLY':
      return dayOfWeek ? t('frequency.everyOtherDay', { day: dayName }) : t('frequency.biWeekly')
    case 'MONTHLY':
      return dayOfMonth
        ? t('frequency.monthlyOnThe', { ordinal: ordinal(dayOfMonth, locale) })
        : t('frequency.monthly')
    default:
      return frequency
  }
}
