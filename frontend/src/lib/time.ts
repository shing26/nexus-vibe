import i18next from 'i18next';

/**
 * Relative time, in words, localized. Shared by every surface that prints a
 * relative timestamp (post rows, detail page, versions, profile activity) so
 * the same span renders the same sentence everywhere.
 */
export function timeAgo(dateStr: string | null | undefined): string {
  if (!dateStr) return '--';
  const diff = Date.now() - new Date(dateStr).getTime();
  const minutes = Math.floor(diff / 60000);
  if (minutes < 1) return i18next.t('common:time.justNow');
  if (minutes < 60) return i18next.t('common:time.minutesAgo', { n: minutes });
  const hours = Math.floor(minutes / 60);
  if (hours < 24) return i18next.t('common:time.hoursAgo', { n: hours });
  const days = Math.floor(hours / 24);
  if (days < 30) return i18next.t('common:time.daysAgo', { n: days });
  return new Date(dateStr).toLocaleDateString();
}
