import { Languages } from 'lucide-react';
import i18next from 'i18next';
import { useTranslation } from 'react-i18next';
import { setAppLanguage } from '../i18n';
import type { AppLanguage } from '../i18n';

/**
 * The language toggle. The visible glyph stays the language code itself (EN /
 * 中), but `aria-label` and `title` are a full sentence in the CURRENT language
 * naming the target: a zh reader hears "切换到英语", an en reader "Switch to
 * Chinese". Naming the target in the target language only helps a reader who
 * already reads it; the current-language sentence is the accessible convention.
 */
export default function LanguageToggle({ className = '' }: { className?: string }) {
  const { t } = useTranslation('common');
  const current = i18next.resolvedLanguage === 'en' ? 'en' : 'zh';
  const next: AppLanguage = current === 'en' ? 'zh' : 'en';
  const label = next === 'en' ? t('language.switchToEnglish') : t('language.switchToChinese');

  return (
    <button
      type="button"
      onClick={() => setAppLanguage(next)}
      aria-label={label}
      title={label}
      className={
        'press-feedback inline-flex items-center gap-1.5 rounded-md border border-vibe-border px-2 py-1.5 text-xs text-vibe-muted hover:bg-vibe-card hover:text-vibe-text transition-colors ' +
        className
      }
    >
      <Languages className="h-3.5 w-3.5" />
      <span>{next === 'en' ? 'EN' : '\u4e2d'}</span>
    </button>
  );
}
