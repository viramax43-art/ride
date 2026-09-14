import i18next from 'i18next'
import { initReactI18next } from 'react-i18next'

import { DEFAULT_LANGUAGE, normalizeLanguage, type AppLanguage } from './languages'
import { i18nResources } from './resources'

const STORAGE_KEY = 'lithcar:language'
const MIGRATION_KEY = 'lithcar:language_migrated_to_ru_v1'

export function getStoredLanguage(): AppLanguage {
  if (typeof window === 'undefined') return DEFAULT_LANGUAGE
  const stored = window.localStorage.getItem(STORAGE_KEY)
  if (stored === 'lt') {
    if (!window.localStorage.getItem(MIGRATION_KEY)) {
      window.localStorage.setItem(STORAGE_KEY, DEFAULT_LANGUAGE)
      window.localStorage.setItem(MIGRATION_KEY, '1')
      return DEFAULT_LANGUAGE
    }
    return normalizeLanguage(stored)
  }
  if (!stored) {
    window.localStorage.setItem(STORAGE_KEY, DEFAULT_LANGUAGE)
    return DEFAULT_LANGUAGE
  }
  return normalizeLanguage(stored)
}

export function storeLanguage(language: AppLanguage): void {
  if (typeof window === 'undefined') return
  window.localStorage.setItem(STORAGE_KEY, language)
}

const initialLanguage = getStoredLanguage()

void i18next
  .use(initReactI18next)
  .init({
    resources: i18nResources,
    lng: initialLanguage,
    fallbackLng: DEFAULT_LANGUAGE,
    interpolation: { escapeValue: false },
  })

i18next.on('languageChanged', (language) => {
  const normalized = normalizeLanguage(language)
  storeLanguage(normalized)
  if (typeof document !== 'undefined') {
    document.documentElement.lang = normalized
  }
})

if (typeof document !== 'undefined') {
  document.documentElement.lang = initialLanguage
}

export default i18next
