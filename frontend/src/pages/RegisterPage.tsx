import { useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { useTranslation } from "react-i18next";
import { apiClient } from "../api/client";
import { useAuthStore } from "../stores/authStore";
import { useThemeStore } from "../stores/themeStore";
import { useToastStore } from "../stores/toastStore";
import { Sun, Moon } from "lucide-react";

export default function RegisterPage() {
  const { t } = useTranslation("auth");
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((s) => s.setAuth);
  const addToast = useToastStore((s) => s.addToast);
  const { dark, toggle } = useThemeStore();
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!username.trim() || username.trim().length < 3) { setError(t("register.errors.usernameShort")); return; }
    if (username.trim().length > 50) { setError(t("register.errors.usernameTooLong")); return; }
    if (!email.trim()) { setError(t("register.errors.emailRequired")); return; }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())) { setError(t("register.errors.emailInvalid")); return; }
    if (!nickname.trim()) { setError(t("register.errors.nicknameRequired")); return; }
    if (nickname.trim().length > 50) { setError(t("register.errors.nicknameTooLong")); return; }
    if (!password || password.length < 8 || password.length > 20) { setError(t("register.errors.passwordLength")); return; }
    if (!/(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/.test(password)) {
      setError(t("register.errors.passwordComplexity"));
      return;
    }
    if (password !== confirmPassword) { setError(t("register.errors.passwordMismatch")); return; }
    setLoading(true);
    try {
      const res = await apiClient.post("/auth/register", {
        username: username.trim(), email: email.trim(), nickname: nickname.trim(), password,
      });
      const d = res.data.data;
      setAuth(d.token, {
        id: d.userId,
        username: d.username,
        nickname: d.nickname,
        role: d.role,
        avatar: d.avatar,
        avatarUrl: d.avatar,
      }, d.refreshToken);
      addToast(t("register.created"), "success");
      const from = (location.state as any)?.from || "/";
      navigate(from, { replace: true });
    } catch (err: any) {
      setError(err?.response?.data?.message || t("register.failed"));
    } finally { setLoading(false); }
  };

  const fieldClass =
    "w-full rounded-md border border-vibe-border bg-vibe-bg px-3 py-2 text-sm text-vibe-text placeholder-vibe-faint transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50";

  return (
    <div className="flex min-h-[calc(100vh-3.5rem)] items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
          <div className="flex items-center justify-between border-b border-vibe-border px-4 py-2.5">
            <h1 className="text-sm font-semibold text-vibe-text">{t("register.title")}</h1>
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

            <input type="text" value={username} onChange={(e) => setUsername(e.target.value)}
              placeholder={t("register.usernamePlaceholder")} autoComplete="username" className={fieldClass}
            />
            <input type="email" value={email} onChange={(e) => setEmail(e.target.value)}
              placeholder={t("register.emailPlaceholder")} autoComplete="email" className={fieldClass}
            />
            <input type="text" value={nickname} onChange={(e) => setNickname(e.target.value)}
              placeholder={t("register.nicknamePlaceholder")} className={fieldClass}
            />
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
              placeholder={t("register.passwordPlaceholder")} autoComplete="new-password" className={fieldClass}
            />
            <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)}
              placeholder={t("register.confirmPlaceholder")} autoComplete="new-password" className={fieldClass}
            />

            <button type="submit" disabled={loading}
              className="w-full rounded-md bg-vibe-accent px-4 py-2 text-sm font-medium text-vibe-on-accent transition-colors hover:bg-vibe-accent/90 disabled:cursor-not-allowed disabled:opacity-40"
            >
              {loading ? t("register.creating") : t("register.submit")}
            </button>
          </form>
          <p className="border-t border-vibe-border px-6 py-3 text-center text-xs text-vibe-muted">
            {t("register.hasAccount")}{" "}
            <Link to="/login" className="text-vibe-accent hover:underline">{t("register.loginLink")}</Link>
          </p>
        </div>
      </div>
    </div>
  );
}
