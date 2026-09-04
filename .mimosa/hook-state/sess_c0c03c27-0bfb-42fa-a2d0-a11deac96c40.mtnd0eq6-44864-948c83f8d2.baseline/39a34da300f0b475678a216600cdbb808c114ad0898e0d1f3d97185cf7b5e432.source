import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';

type Tab = 'profile' | 'password';

export default function SettingsPage() {
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
      setProfileSuccess('Profile updated successfully.');
    } catch (err: any) {
      setProfileError(err.response?.data?.message || 'Failed to update profile.');
    } finally {
      setProfileSaving(false);
    }
  };

  const handlePasswordSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setPasswordError('');
    setPasswordSuccess('');

    if (newPassword !== confirmPassword) {
      setPasswordError('New passwords do not match.');
      return;
    }
    if (newPassword.length < 8) {
      setPasswordError('New password must be at least 8 characters.');
      return;
    }

    setPasswordSaving(true);
    try {
      await apiClient.put('/users/password', {
        currentPassword,
        newPassword,
      });
      setPasswordSuccess('Password changed successfully.');
      setCurrentPassword('');
      setNewPassword('');
      setConfirmPassword('');
    } catch (err: any) {
      setPasswordError(err.response?.data?.message || 'Failed to change password.');
    } finally {
      setPasswordSaving(false);
    }
  };

  if (!isAuthenticated || !user) return null;

  const tabs: { key: Tab; label: string }[] = [
    { key: 'profile', label: 'Profile' },
    { key: 'password', label: 'Password' },
  ];

  return (
    <div className="max-w-3xl mx-auto px-4 py-8">
      <h1 className="text-base font-semibold font-mono text-slate-100 mb-8"><span className="text-vibe-cyan">$</span> Settings</h1>

      {/* Tabs */}
      <div className="flex border-b border-vibe-border mb-8">
        {tabs.map((tab) => (
          <button
            key={tab.key}
            onClick={() => setActiveTab(tab.key)}
            className={`px-6 py-3 text-xs font-mono border-b-2 transition-colors ${
              activeTab === tab.key
                ? 'border-vibe-cyan text-vibe-cyan'
                : 'border-transparent text-slate-500 hover:text-slate-200'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {activeTab === 'profile' && (
        <form onSubmit={handleProfileSubmit} className="space-y-6">
          {profileError && (
            <div className="bg-red-900/30 border border-red-500/40 text-red-400 px-4 py-3 rounded-lg text-[11px] font-mono">
              {profileError}
            </div>
          )}
          {profileSuccess && (
            <div className="bg-vibe-emerald/10 border border-vibe-emerald/30 text-vibe-emerald px-4 py-3 rounded-lg text-[11px] font-mono">
              {profileSuccess}
            </div>
          )}

          <div>
            <label className="block text-[11px] font-mono text-slate-400 mb-1">Username</label>
            <input
              type="text"
              value={user.username}
              disabled
              className="w-full px-4 py-2.5 border border-vibe-border rounded-lg text-xs font-mono bg-vibe-card text-slate-500 cursor-not-allowed"
            />
            <p className="mt-1 text-[10px] font-mono text-slate-600">Username cannot be changed.</p>
          </div>

          <div>
            <label className="block text-[11px] font-mono text-slate-400 mb-1">Nickname</label>
            <input
              type="text"
              value={nickname}
              onChange={(e) => setNickname(e.target.value)}
              placeholder="Your display name"
              className="w-full px-4 py-2.5 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
            />
          </div>

          <div>
            <label className="block text-[11px] font-mono text-slate-400 mb-1">Avatar URL</label>
            <input
              type="text"
              value={avatarUrl}
              onChange={(e) => setAvatarUrl(e.target.value)}
              placeholder="https://example.com/avatar.jpg"
              className="w-full px-4 py-2.5 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
            />
          </div>

          <div>
            <label className="block text-[11px] font-mono text-slate-400 mb-1">Bio</label>
            <textarea
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              placeholder="Tell us about yourself..."
              rows={4}
              className="w-full px-4 py-2.5 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors resize-none"
            />
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={profileSaving}
              className="px-6 py-2 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 text-vibe-cyan text-xs font-mono hover:bg-vibe-cyan/30 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {profileSaving ? 'Saving...' : 'Save Profile'}
            </button>
          </div>
        </form>
      )}

      {activeTab === 'password' && (
        <form onSubmit={handlePasswordSubmit} className="space-y-6 max-w-md">
          {passwordError && (
            <div className="bg-red-900/30 border border-red-500/40 text-red-400 px-4 py-3 rounded-lg text-[11px] font-mono">
              {passwordError}
            </div>
          )}
          {passwordSuccess && (
            <div className="bg-vibe-emerald/10 border border-vibe-emerald/30 text-vibe-emerald px-4 py-3 rounded-lg text-[11px] font-mono">
              {passwordSuccess}
            </div>
          )}

          <div>
            <label className="block text-[11px] font-mono text-slate-400 mb-1">Current Password</label>
            <input
              type="password"
              value={currentPassword}
              onChange={(e) => setCurrentPassword(e.target.value)}
              className="w-full px-4 py-2.5 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
            />
          </div>

          <div>
            <label className="block text-[11px] font-mono text-slate-400 mb-1">New Password</label>
            <input
              type="password"
              value={newPassword}
              onChange={(e) => setNewPassword(e.target.value)}
              className="w-full px-4 py-2.5 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
            />
          </div>

          <div>
            <label className="block text-[11px] font-mono text-slate-400 mb-1">Confirm New Password</label>
            <input
              type="password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              className="w-full px-4 py-2.5 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
            />
          </div>

          <div className="flex justify-end">
            <button
              type="submit"
              disabled={passwordSaving}
              className="px-6 py-2 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 text-vibe-cyan text-xs font-mono hover:bg-vibe-cyan/30 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {passwordSaving ? 'Changing...' : 'Change Password'}
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
