import { createContext, useContext, useState, useCallback } from 'react'
import { en } from './en'
import { fr } from './fr'

export type Language = 'en' | 'fr'

// eslint-disable-next-line @typescript-eslint/no-explicit-any
const locales: Record<Language, any> = { en, fr }

type I18nState = {
  language: Language
  setLanguage: (lang: Language) => void
  t: (key: string, vars?: Record<string, string | number>) => string
  tc: (category: string) => string
}

const I18nContext = createContext<I18nState>({
  language: 'en',
  setLanguage: () => null,
  t: (key) => key,
  tc: (cat) => cat,
})

function getNestedValue(obj: Record<string, unknown>, path: string): string | undefined {
  const keys = path.split('.')
  let current: unknown = obj
  for (const key of keys) {
    if (current === null || current === undefined || typeof current !== 'object') return undefined
    current = (current as Record<string, unknown>)[key]
  }
  return typeof current === 'string' ? current : undefined
}

export function I18nProvider({ children }: { children: React.ReactNode }) {
  const [language, setLanguageState] = useState<Language>(
    () => (localStorage.getItem('app-language') as Language) || 'en'
  )

  const setLanguage = useCallback((lang: Language) => {
    localStorage.setItem('app-language', lang)
    setLanguageState(lang)
  }, [])

  const t = useCallback((key: string, vars?: Record<string, string | number>): string => {
    const translations = locales[language] as Record<string, unknown>
    let value = getNestedValue(translations, key) ?? key
    if (vars) {
      for (const [k, v] of Object.entries(vars)) {
        value = value.replace(new RegExp(`\\{\\{${k}\\}\\}`, 'g'), String(v))
      }
    }
    return value
  }, [language])

  const tc = useCallback((category: string): string => {
    const cats = locales[language].categories as Record<string, string>
    return cats[category] ?? category
  }, [language])

  return (
    <I18nContext.Provider value={{ language, setLanguage, t, tc }}>
      {children}
    </I18nContext.Provider>
  )
}

export const useI18n = () => useContext(I18nContext)
