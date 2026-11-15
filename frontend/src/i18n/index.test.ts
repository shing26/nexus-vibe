import { afterEach, describe, expect, it } from 'vitest';
import i18next from 'i18next';
import { setAppLanguage } from './index';

/**
 * A6 / contract criterion 6: the browser tab follows the site's language. The
 * bootstrap applies the title once i18next resolves the stored/detected
 * language; the manual toggle applies it again once the new language lands.
 * These pins ride the same i18next instance the components resolve through.
 */
describe('document.title follows the app language', () => {
  afterEach(async () => {
    await i18next.changeLanguage('en');
  });

  it('names the site in zh after switching to zh', async () => {
    await setAppLanguage('zh');
    expect(document.title).toBe('Nexus-Vibe | AI 开发者社区');
  });

  it('names the site in en after switching to en', async () => {
    await setAppLanguage('en');
    expect(document.title).toBe('Nexus-Vibe | AI Developer Community');
  });
});
