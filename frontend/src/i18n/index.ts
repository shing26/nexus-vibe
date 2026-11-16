import i18next from 'i18next';
import { initReactI18next} from 'react-i18next';
import zh from './locales/zh.json';
import en from './locales/en.json';

/**
 * i18n bootstrap. Chinese is the source locale; `en` is the second resource.
 *
 * <p>Detection runs once at import: a stored choice (localStorage
 * `nexus-vibe-lang`) wins, otherwise `navigator.language` (en → `en`, anything
 * else → `zh`). The manual toggle calls {@link setAppLanguage}, which changes the
 * live language and persists the choice; there is no server round-trip.</p>
 */

export const LANG_STORAGE_KEY = 'nexus-vibe-lang';
export const SUPPORTED_LANGUAGES = ['zh', 'en'] as const;
export type AppLanguage = (typeof SUPPORTED_LANGUAGES)[number];

function detectInitialLanguage(): AppLanguage {
  try {
    const stored = localStorage.getItem(LANG_STORAGE_KEY);
    if (stored === 'zh' || stored === 'en') return stored;
  } catch {
    // Storage can be unavailable (private mode, disabled quota); fall through to detection.
  }
  const lang = typeof navigator !== 'undefined' ? navigator.language : '';
  return lang && lang.toLowerCase().startsWith('en') ? 'en' : 'zh';
}

if (!i18next.isInitialized) {
  i18next.use(initReactI18next).init({
    // Each locale file is a map of namespaces at its top level (common, post,
    // editor, ...), so the file itself is the language's namespace bundle.
    resources: { zh, en },
    defaultNS: 'common',
    lng: detectInitialLanguage(),
    fallbackLng: 'zh',
    // The SPA renders React text nodes; axios/envelope strings are passed as
    // plain values, so no double-escaping is wanted here.
    interpolation: { escapeValue: false },
    returnNull: false,
  });
  // The browser tab carries the site's language from the first painted frame:
  // index.html ships the zh default, and bootstrap overwrites it from the
  // dictionary for every other stored/detected language.
  applyDocumentTitle();
}

/** The document title follows the dictionary so the tab names the site in the active language. */
function applyDocumentTitle(): void {
  if (typeof document === 'undefined') return;
  document.title = i18next.t('common:site.title');
}

/** Change the live language and persist to localStorage for the next visit. */
export function setAppLanguage(lang: AppLanguage): Promise<void> {
  // The title is applied in the callback, once the new language is resolved —
  // a synchronous t() here would read the previous language's dictionary.
  const applied = i18next.changeLanguage(lang).then(applyDocumentTitle);
  try {
    localStorage.setItem(LANG_STORAGE_KEY, lang);
  } catch {
    // A failed write only costs persistence; the session keeps the new language.
  }
  document.documentElement.lang = lang === 'zh' ? 'zh-CN' : 'en';
  return applied;
}
