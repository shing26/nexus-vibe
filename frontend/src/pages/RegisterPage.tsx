import { useState } from "react";
import { Link, useNavigate, useLocation } from "react-router-dom";
import { apiClient } from "../api/client";
import { useAuthStore } from "../stores/authStore";
import { useThemeStore } from "../stores/themeStore";
import { useToastStore } from "../stores/toastStore";
import { Sun, Moon } from "lucide-react";

export default function RegisterPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const setAuth = useAuthStore((s) => s.setAuth);
  const addToast = useToastStore((s) => s.addToast);
  const { dark, toggle } = useThemeStore();
  const [username, setUsername] = useState("");
  const [nickname, setNickname] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!username.trim() || username.trim().length < 3) { setError("Username needs 3+ chars"); return; }
    if (username.trim().length > 50) { setError("Username must not exceed 50 characters"); return; }
    if (!nickname.trim()) { setError("Nickname is required"); return; }
    if (nickname.trim().length > 50) { setError("Nickname must not exceed 50 characters"); return; }
    if (!password || password.length < 8 || password.length > 20) { setError("Password must be 8-20 characters"); return; }
    if (!/(?=.*[a-z])(?=.*[A-Z])(?=.*\d)/.test(password)) {
      setError("Password must contain uppercase, lowercase and a number");
      return;
    }
    if (password !== confirmPassword) { setError("Passwords don't match"); return; }
    setLoading(true);
    try {
      const res = await apiClient.post("/auth/register", {
        username: username.trim(), nickname: nickname.trim(), password,
      });
      const d = res.data.data;
      setAuth(d.token, {
        id: d.userId,
        username: d.username,
        nickname: d.nickname,
        role: d.role,
        avatar: d.avatar,
        avatarUrl: d.avatar,
      });
      addToast('Account created!', 'success');
      const from = (location.state as any)?.from || "/";
      navigate(from, { replace: true });
    } catch (err: any) {
      setError(err?.response?.data?.message || "Registration failed");
    } finally { setLoading(false); }
  };

  return (
    <div className="min-h-[calc(100vh-3.5rem)] flex items-center justify-center px-4">
      <div className="w-full max-w-sm">
        <div className="bg-vibe-surface border border-vibe-border rounded-xl overflow-hidden">
          <div className="flex items-center h-9 bg-vibe-card border-b border-vibe-border px-3">
            <div className="flex items-center gap-1.5">
              <span className="w-2.5 h-2.5 rounded-full bg-red-500/80" />
              <span className="w-2.5 h-2.5 rounded-full bg-yellow-500/80" />
              <span className="w-2.5 h-2.5 rounded-full bg-green-500/80" />
            </div>
            <span className="flex-1 text-center text-[11px] font-mono text-slate-500">auth_register.sh</span>
            <button onClick={toggle} className="p-1 rounded hover:bg-vibe-card/50 transition-colors">
              {dark ? <Sun className="w-3 h-3 text-slate-500" /> : <Moon className="w-3 h-3 text-slate-500" />}
            </button>
          </div>
          <div className="p-6 space-y-4">
            <h1 className="text-sm font-mono font-semibold text-slate-100">
              <span className="text-vibe-cyan">$</span> Register
            </h1>

            <form onSubmit={handleSubmit} className="space-y-3">
              {error && (
                <div className="bg-red-900/30 border border-red-500/40 text-red-400 px-3 py-2 rounded-lg text-[11px] font-mono">
                  ! {error}
                </div>
              )}

              <input type="text" value={username} onChange={(e) => setUsername(e.target.value)}
                placeholder="username" autoComplete="username"
                className="w-full px-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
              />
              <input type="text" value={nickname} onChange={(e) => setNickname(e.target.value)}
                placeholder="nickname"
                className="w-full px-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
              />
              <input type="password" value={password} onChange={(e) => setPassword(e.target.value)}
                placeholder="password" autoComplete="new-password"
                className="w-full px-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
              />
              <input type="password" value={confirmPassword} onChange={(e) => setConfirmPassword(e.target.value)}
                placeholder="confirm password" autoComplete="new-password"
                className="w-full px-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
              />

              <button type="submit" disabled={loading}
                className="w-full py-2 px-4 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 text-vibe-cyan text-xs font-mono hover:bg-vibe-cyan/30 transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
              >
                {loading ? "Creating..." : "Create Account"}
              </button>
            </form>

            <p className="text-center text-[11px] font-mono text-slate-500">
              Already have an account?{" "}
              <Link to="/login" className="text-vibe-cyan hover:underline">Login</Link>
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
