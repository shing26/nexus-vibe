import { Link } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { FileQuestion } from 'lucide-react';

export default function NotFoundPage() {
  const { t } = useTranslation('common');

  return (
    <div className="mx-auto max-w-3xl px-4 py-24 text-center">
      <FileQuestion className="mx-auto mb-4 h-16 w-16 text-vibe-faint" />
      <h1 className="mb-2 text-xl font-semibold text-vibe-text">{t('notFoundPage.title')}</h1>
      <p className="mb-6 text-sm text-vibe-muted">{t('notFoundPage.desc')}</p>
      <Link
        to="/"
        className="inline-flex items-center gap-2 rounded-md bg-vibe-accent px-4 py-2 text-xs font-medium text-white transition-colors hover:opacity-90"
      >
        {t('notFoundPage.backHome')}
      </Link>
    </div>
  );
}
