import { useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { apiClient } from "../api/client";
import { useAuthStore } from "../stores/authStore";
import { useThemeStore } from "../stores/themeStore";
import { useToastStore } from "../stores/toastStore";
import { Sun, Moon } from "lucide-react";

export default function LoginPage() {
  const { t } = useTranslation("auth");
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((s) => s.setAuth);
  const addToast = useToastStore((s) => s.addToast);
  const { dark, toggle } = useThemeStore();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!username.trim()) { setError(t("login.errors.usernameRequired")); return; }
    if (!password) { setError(t("login.errors.passwordRequired")); return; }
    setLoading(true);
    try {
      const res = await apiClient.post("/auth/login", { username: username.trim(), password });
      const d = res.data.data;
      setAuth(d.token, {
        id: d.userId,
        username: d.username,
        nickname: d.nickname,
        role: d.role,
        avatar: d.avatar,
        avatarUrl: d.avatar,
      }, d.refreshToken);
      addToast(t("login.welcome"), "success");
      const from = (location.state as any)?.from || "/";
      navigate(from, { replace: true });
    } catch (err: any) {
      setError(err?.response?.data?.message || t("login.failed"));
    } finally { setLoading(false); }
  };

  return (
    <div className="flex min-h-[calc(100vh-3.5rem)] items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
          <div className="flex items-center justify-between border-b border-vibe-border px-4 py-2.5">
            <h1 className="text-sm font-semibold text-vibe-text">{t("login.title")}</h1>
            <button type="button" onClick={toggle} aria-label="Toggle theme" className="rounded p-1 text-vibe-muted transition-colors hover:text-vibe-text">
              {dark ? <Sun className="h-3.5 w-3.5" /> : <Moon className="h-3.5 w-3.5" />}
            </button>
          </div>
          <form onSubmit={handleSubmit} className="space-y-3 p-6">
            {error && (
              <div role="alert" className="rounded-md border border-vibe-danger/40 bg-vibe-danger/5 px-3 py-2 text-xs text-vibe-danger">
                ! {error}
              </div>
            )}

            <label className="block space-y-1">
              <span className="text-xs text-vibe-muted">{t("login.usernamePlaceholder")}</span>
              <input
                type="text"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder={t("login.usernamePlaceholder")}
                className="w-full rounded-md border border-vibe-border bg-vibe-bg px-3 py-2 text-sm text-vibe-text placeholder-vibe-faint transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
                autoComplete="username"
              />
            </label>
            <label className="block space-y-1">
              <span className="text-xs text-vibe-muted">{t("login.passwordPlaceholder")}</span>
              <input
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder={t("login.passwordPlaceholder")}
                className="w-full rounded-md border border-vibe-border bg-vibe-bg px-3 py-2 text-sm text-vibe-text placeholder-vibe-faint transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
                autoComplete="current-password"
              />
            </label>

            <button
              type="submit"
              disabled={loading}
              className="w-full rounded-md bg-vibe-accent px-4 py-2 text-sm font-medium text-white transition-colors hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {loading ? t("login.authenticating") : t("login.submit")}
            </button>
          </form>
          <p className="border-t border-vibe-border px-6 py-3 text-center text-xs text-vibe-muted">
            {t("login.noAccount")}{" "}
            <Link to="/register" className="text-vibe-accent hover:underline">{t("login.registerLink")}</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
