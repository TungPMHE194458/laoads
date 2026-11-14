import i18n from 'i18next'
import LanguageDetector from 'i18next-browser-languagedetector'
import { initReactI18next } from 'react-i18next'
import vi from '@/locales/vi.json'
import en from '@/locales/en.json'
import lo from '@/locales/lo.json'

export const LANGS = ['vi', 'en', 'lo'] as const
export type Lang = (typeof LANGS)[number]

/** BCP-47 cho Intl.NumberFormat / DateTimeFormat — dùng thay vì hardcode locale. */
export const LOCALE_MAP: Record<Lang, string> = { vi: 'vi-VN', en: 'en-US', lo: 'lo-LA' }

export const LANG_LABEL: Record<Lang, { label: string; flag: string }> = {
  vi: { label: 'Tiếng Việt', flag: '🇻🇳' },
  en: { label: 'English', flag: '🇬🇧' },
  lo: { label: 'ພາສາລາວ', flag: '🇱🇦' },
}

void i18n
  .use(LanguageDetector)
  .use(initReactI18next)
  .init({
    resources: { vi: { translation: vi }, en: { translation: en }, lo: { translation: lo } },
    fallbackLng: 'vi',
    supportedLngs: LANGS as unknown as string[],
    interpolation: { escapeValue: false },
    detection: {
      order: ['localStorage', 'navigator'],
      lookupLocalStorage: 'laoads_lang',
      caches: ['localStorage'],
    },
  })

i18n.on('languageChanged', (lng) => {
  document.documentElement.lang = lng
})

export default i18n
