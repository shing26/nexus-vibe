import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import LanguageToggle from '../components/LanguageToggle';

type Tab = 'profile' | 'password';

const fieldClass =
  'w-full rounded-md border border-vibe-border bg-vibe-bg px-4 py-2.5 text-sm text-vibe-text placeholder-vibe-faint transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50';
const labelClass = 'mb-1 block text-xs text-vibe-muted';

export default function SettingsPage() {
  const { t } = useTranslation('profile');
  const navigate = useNavigate();
  const { isAuthenticated, user, setAuth } = useAuthStore();

  const [activeTab, setActiveTab] = useState<Tab>('profile');

  // Profile state
  const [nickname, setNickname] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [bio, setBio] = useState('');
  const [profileSaving, setProfileSaving] = useState(false);
  const [profileError, setProfileError] = useState('');
  const [profileSuccess, setProfileSuccess] = useState('');

  // Password state
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [passwordSaving, setPasswordSaving] = useState(false);
  const [passwordError, setPasswordError] = useState('');
  const [passwordSuccess, setPasswordSuccess] = useState('');

  useEffect(() => {
    if (!isAuthenticated) {
      navigate('/login', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  useEffect(() => {
    if (user) {
      setNickname(user.nickname || '');
      setAvatarUrl(user.avatarUrl || user.avatar || '');
    }
  }, [user]);

  const handleProfileSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setProfileError('');
    setProfileSuccess('');
    setProfileSaving(true);
    try {
      const res = await apiClient.put('/users/profile', { nickname, avatarUrl, bio });
      // Update auth store with returned user data
      if (res.data.data) {
        setAuth(useAuthStore.getState().token!, res.data.data);
      }
      setProfileSuccess(t('settings.profile.updated'));
    } catch (err: any) {
      setProfileError(err.response?.data?.message || t('settings.profile.failed'));
    } finally {
      setProfileSaving(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordError('');
    setPasswordSuccess('');

    if (newPassword !== confirmPassword) {
      setPasswordError(t('settings.password.mismatch'));
      return;
    }
    if (newPassword.length < 8) {
      setPasswordError(t('settings.password.tooShort'));
      return;
    }

    setPasswordSaving(true);
    try {
      await apiClient.put('/users/password', {
        currentPassword,
        newPassword,
      });
      setPasswordSuccess(t('settings.password.changed'));
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: any) {
      setPasswordError(err.response?.data?.message || t('settings.password.failed'));
    } finally {
      setPasswordSaving(false);
    }
  };

  if (!isAuthenticated || !user) return null;

  const tabs: { key: Tab; label: string }[] = [
    { key: 'profile', label: t('settings.tabs.profile') },
    { key: 'password', label: t('settings.tabs.password') },
  ];

  return (
    <div className="mx-auto max-w-3xl px-4 py-8">
      <h1 className="mb-8 text-lg font-semibold text-vibe-text">{t('settings.title')}</h1>

      {/* Language — the in-place half of the toggle: the user-menu button switches
          in one tap; this section says what the choice does and where it lives. */}
      <section className="mb-8 rounded-lg border border-vibe-border bg-vibe-surface p-4">
        <h2 className="text-sm font-semibold text-vibe-text">{t('settings.language.heading')}</h2>
        <p className="mt-0.5 text-xs text-vibe-muted">{t('settings.language.description')}</p>
        <div className="mt-3">
          <LanguageToggle />
        </div>
      </section>

      {/* Tabs — the same GitHub underline style as the home feed */}
      <div className="mb-8 flex border-b border-vibe-border">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            type="button"
            onClick={() => setActiveTab(tab.key)}
            aria-pressed={activeTab === tab.key}
            className={`-mb-px border-b-2 px-6 py-3 text-sm transition-colors ${
              activeTab === tab.key
                ? 'border-vibe-accent font-medium text-vibe-text'
                : 'border-transparent text-vibe-muted hover:text-vibe-text'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'profile' && (
        <form onSubmit={handleProfileSubmit} className="space-y-6">
          {profileError && (
            <div role="alert" className="rounded-md border border-vibe-danger/40 bg-vibe-danger/5 px-4 py-3 text-xs text-vibe-danger">
              {profileError}
            </div>
          )}
          {profileSuccess && (
            <div role="status" className="rounded-md border border-vibe-success/40 bg-vibe-success/10 px-4 py-3 text-xs text-vibe-success">
              {profileSuccess}
            </div>
          )}

          <div>
            <label className={labelClass}>{t('settings.profile.username')}</label>
            <input
              type="text"
              value={user.username}
              disabled
              className="w-full cursor-not-allowed rounded-md border border-vibe-border bg-vibe-card px-4 py-2.5 text-sm text-vibe-faint"
            />
            <p className="mt-1 text-xs text-vibe-faint">{t('settings.profile.usernameLocked')}</p>
          </div>

          <div>
            <label htmlFor="nickname" className={labelClass}>{t('settings.profile.nickname')}</label>
            <input
              id="nickname"
              type="text"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              maxLength={50}
              placeholder={t('settings.profile.nicknamePlaceholder')}
              className={fieldClass}
            />
          </div>

          <div>
            <label htmlFor="avatarUrl" className={labelClass}>{t('settings.profile.avatarUrl')}</label>
            <input
              id="avatarUrl"
              type="text"
              value={avatarUrl}
              onChange={(e) => setAvatarUrl(e.target.value)}
              maxLength={255}
              placeholder={t('settings.profile.avatarPlaceholder')}
              className={fieldClass}
            />
          </div>

          <div>
            <label htmlFor="bio" className={labelClass}>{t('settings.profile.bio')}</label>
            <textarea
              id="bio"
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              maxLength={255}
              placeholder={t('settings.profile.bioPlaceholder')}
              rows={4}
              className={fieldClass + ' resize-none'}
            />
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={profileSaving}
              className="rounded-md bg-vibe-accent px-6 py-2 text-sm font-medium text-white transition-colors hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {profileSaving ? t('settings.profile.saving') : t('settings.profile.save')}
            </button>
          </div>
        </form>
      )}

      {activeTab === 'password' && (
        <form onSubmit={handlePasswordSubmit} className="max-w-md space-y-6">
          {passwordError && (
            <div role="alert" className="rounded-md border border-vibe-danger/40 bg-vibe-danger/5 px-4 py-3 text-xs text-vibe-danger">
              {passwordError}
            </div>
          )}
          {passwordSuccess && (
            <div role="status" className="rounded-md border border-vibe-success/40 bg-vibe-success/10 px-4 py-3 text-xs text-vibe-success">
              {passwordSuccess}
            </div>
          )}

          <div>
            <label htmlFor="currentPassword" className={labelClass}>{t('settings.password.current')}</label>
            <input
              id="currentPassword"
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className={fieldClass}
            />
          </div>

          <div>
            <label htmlFor="newPassword" className={labelClass}>{t('settings.password.new')}</label>
            <input
              id="newPassword"
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className={fieldClass}
            />
          </div>

          <div>
            <label htmlFor="confirmPassword" className={labelClass}>{t('settings.password.confirm')}</label>
            <input
              id="confirmPassword"
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className={fieldClass}
            />
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={passwordSaving}
              className="rounded-md bg-vibe-accent px-6 py-2 text-sm font-medium text-white transition-colors hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {passwordSaving ? t('settings.password.changing') : t('settings.password.change')}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
