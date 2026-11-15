import { Languages } from 'lucide-react';
import { useTranslation } from 'react-i18next';
import i18next, { setAppLanguage } from '../i18n';
import type { AppLanguage } from '../i18n';

/**
 * The language toggle. The button's label names the language it would switch to,
 * in that language — the standard picker convention, so the target is readable
 * no matter which language the reader is currently in.
 */
export default function LanguageToggle({ className = '' }: { className?: string }) {
  const { t } = useTranslation('common');
  const current = i18next.resolvedLanguage === 'en' ? 'en' : 'zh';
  const next: AppLanguage = current === 'en' ? 'zh' : 'en';

  return (
    <button
      type="button"
      onClick={() => setAppLanguage(next)}
      aria-label={t('language.toggle')}
      title={t('language.toggle')}
      className={
        'inline-flex items-center gap-1.5 rounded-md border border-vibe-border px-2 py-1.5 text-xs text-vibe-muted hover:bg-vibe-card hover:text-vibe-text transition-colors ' +
        className
      }
    >
      <Languages className="h-3.5 w-3.5" />
      <span>{next === 'en' ? 'EN' : '中'}</span>
    </button>
  );
}
