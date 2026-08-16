import { Link, useLocation } from 'react-router-dom';
import { Terminal, Palette, Cpu, Zap, Bug, BookOpen, Megaphone, FileText, Activity, Tag, Mail, Settings, ShieldCheck, LayoutDashboard } from 'lucide-react';
import { useAuthStore } from '../stores/authStore';

const channels = [
  { slug: 'announcements', label: 'Announcements', icon: Megaphone, desc: '平台公告与更新' },
  { slug: 'prompts', label: 'Prompt 工坊', icon: Terminal, desc: 'System Prompt 设计、CoT' },
  { slug: 'showcase', label: '作品展示', icon: Palette, desc: 'Vibe Coding 成品展示' },
  { slug: 'agents', label: 'Agent 实战', icon: Cpu, desc: 'Agent 架构与案例' },
  { slug: 'vibe-coding', label: 'Vibe Coding', icon: Zap, desc: 'AI Coding 经验分享' },
  { slug: 'debug', label: '代码急诊室', icon: Bug, desc: 'Bug 诊断与修复讨论' },
  { slug: 'resources', label: '资源聚合', icon: BookOpen, desc: '学习资源与工具收集' },
];

export default function Sidebar({ className = '' }: { className?: string }) {
  const location = useLocation();
  const user = useAuthStore((s) => s.user);

  return (
    <aside className={'w-12 shrink-0 ' + className}>
      <div className="bg-vibe-surface border border-vibe-border rounded-xl py-3 flex flex-col items-center gap-1">
        {channels.map((ch) => {
          const Icon = ch.icon;
          const isActive = location.pathname === '/channel/' + ch.slug;
          return (
            <Link
              key={ch.slug}
              to={'/channel/' + ch.slug}
              title={ch.label + ' — ' + ch.desc}
              className={
                'relative group w-8 h-8 flex items-center justify-center rounded-lg text-xs transition-all active:scale-[0.92] ' +
                (isActive
                  ? 'bg-vibe-cyan/15 text-vibe-cyan ring-1 ring-vibe-cyan/30'
                  : 'text-slate-500 hover:bg-vibe-card hover:text-slate-200')
              }
            >
              <Icon className="w-4 h-4" />
              <span className="absolute left-full ml-2 px-2 py-1 rounded-md bg-vibe-card border border-vibe-border text-[10px] font-mono text-slate-300 whitespace-nowrap opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-150 pointer-events-none z-50 shadow-lg">
                {ch.label}
              </span>
            </Link>
          );
        })}

        <div className="w-6 h-px bg-vibe-border my-1" />

        <Link
          to="/drafts"
          title="My Drafts"
          className="relative group w-8 h-8 flex items-center justify-center rounded-lg text-slate-500 hover:bg-vibe-card hover:text-slate-200 transition-all active:scale-[0.92]"
        >
          <FileText className="w-4 h-4" />
          <span className="absolute left-full ml-2 px-2 py-1 rounded-md bg-vibe-card border border-vibe-border text-[10px] font-mono text-slate-300 whitespace-nowrap opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-150 pointer-events-none z-50 shadow-lg">My Drafts</span>
        </Link>
        <Link
          to="/tags"
          title="Hot Tags"
          className="relative group w-8 h-8 flex items-center justify-center rounded-lg text-slate-500 hover:bg-vibe-card hover:text-slate-200 transition-all active:scale-[0.92]"
        >
          <Tag className="w-4 h-4" />
          <span className="absolute left-full ml-2 px-2 py-1 rounded-md bg-vibe-card border border-vibe-border text-[10px] font-mono text-slate-300 whitespace-nowrap opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-150 pointer-events-none z-50 shadow-lg">Hot Tags</span>
        </Link>

        <div className="w-6 h-px bg-vibe-border my-1" />

        <Link
          to="/user/messages"
          title="Messages"
          className="relative group w-8 h-8 flex items-center justify-center rounded-lg text-slate-500 hover:bg-vibe-card hover:text-slate-200 transition-all active:scale-[0.92]"
        >
          <Mail className="w-4 h-4" />
          <span className="absolute left-full ml-2 px-2 py-1 rounded-md bg-vibe-card border border-vibe-border text-[10px] font-mono text-slate-300 whitespace-nowrap opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-150 pointer-events-none z-50 shadow-lg">Messages</span>
        </Link>
        <Link
          to="/user/settings"
          title="Settings"
          className="relative group w-8 h-8 flex items-center justify-center rounded-lg text-slate-500 hover:bg-vibe-card hover:text-slate-200 transition-all active:scale-[0.92]"
        >
          <Settings className="w-4 h-4" />
          <span className="absolute left-full ml-2 px-2 py-1 rounded-md bg-vibe-card border border-vibe-border text-[10px] font-mono text-slate-300 whitespace-nowrap opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-150 pointer-events-none z-50 shadow-lg">Settings</span>
        </Link>

        {user?.role === 'ADMIN' && (
          <>
            <div className="w-6 h-px bg-vibe-border my-1" />
            <Link
              to="/admin/audit"
              title="Audit Queue"
              className="relative group w-8 h-8 flex items-center justify-center rounded-lg text-amber-400/80 hover:bg-vibe-card hover:text-amber-300 transition-all active:scale-[0.92]"
            >
              <ShieldCheck className="w-4 h-4" />
              <span className="absolute left-full ml-2 px-2 py-1 rounded-md bg-vibe-card border border-vibe-border text-[10px] font-mono text-slate-300 whitespace-nowrap opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-150 pointer-events-none z-50 shadow-lg">Audit Queue</span>
            </Link>
            <Link
              to="/admin/dashboard"
              title="Dashboard"
              className="relative group w-8 h-8 flex items-center justify-center rounded-lg text-amber-400/80 hover:bg-vibe-card hover:text-amber-300 transition-all active:scale-[0.92]"
            >
              <LayoutDashboard className="w-4 h-4" />
              <span className="absolute left-full ml-2 px-2 py-1 rounded-md bg-vibe-card border border-vibe-border text-[10px] font-mono text-slate-300 whitespace-nowrap opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-150 pointer-events-none z-50 shadow-lg">Dashboard</span>
            </Link>
            <Link
              to="/agent-logs"
              title="Agent Logs"
              className="relative group w-8 h-8 flex items-center justify-center rounded-lg text-amber-400/80 hover:bg-vibe-card hover:text-amber-300 transition-all active:scale-[0.92]"
            >
              <Activity className="w-4 h-4" />
              <span className="absolute left-full ml-2 px-2 py-1 rounded-md bg-vibe-card border border-vibe-border text-[10px] font-mono text-slate-300 whitespace-nowrap opacity-0 invisible group-hover:opacity-100 group-hover:visible transition-all duration-150 pointer-events-none z-50 shadow-lg">Agent Logs</span>
            </Link>
          </>
        )}
      </div>
    </aside>
  );
}
