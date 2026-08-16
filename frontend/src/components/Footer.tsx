import { useEffect, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Bot, Radio } from 'lucide-react';
import { apiClient } from '../api/client';

interface AiLog {
  id: string;
  postId: string;
  postTitle: string;
  reviewer: string;
  severity: string;
  isApproved: number;
  status?: 'completed' | 'unavailable';
  createdAt: string;
}

function formatTime(value: string): string {
  if (!value) return '--:--:--';
  return value.replace('T', ' ').slice(11, 19);
}

function AgentTicker() {
  const [index, setIndex] = useState(0);

  const { data: logsData } = useQuery({
    queryKey: ['agent-ticker'],
    queryFn: async () => (await apiClient.get('/agent-logs/ticker')).data.data,
    staleTime: 1000 * 60,
    refetchInterval: 1000 * 60,
  });

  const logs: AiLog[] = logsData ?? [];

  useEffect(() => {
    if (logs.length <= 1) return;
    const timer = setInterval(() => {
      setIndex((i) => (i + 1) % logs.length);
    }, 6000);
    return () => clearInterval(timer);
  }, [logs.length]);

  const active = logs[index];
  const unavailable = !!active && (active.status === 'unavailable' || (!active.status && !active.severity));
  const status = !active ? 'IDLE' : unavailable ? 'UNAVAILABLE' : active.isApproved === 1 ? 'APPROVED' : 'FLAGGED';
  const severityLabel = active?.severity || '--';

  const message = useMemo(() => {
    if (!active) return 'Agent fleet idle. Publish a post with code to trigger auto review.';
    return (
      '[' +
      formatTime(active.createdAt) +
      '] ' +
      active.reviewer +
      ' verified post "' +
      active.postTitle +
      '" · Post #' +
      active.postId +
      ' · Severity: ' +
      severityLabel +
      ' · Status: ' +
      status
    );
  }, [active, severityLabel, status]);

  return (
    <div className="border-b border-vibe-border bg-vibe-bg/70">
      <div className="max-w-[1400px] mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center gap-3 py-2 min-w-0">
          <span className="flex items-center gap-1.5 shrink-0 text-[10px] font-mono text-vibe-emerald">
            <span className="relative flex w-1.5 h-1.5">
              <span className="absolute inline-flex h-full w-full rounded-full bg-vibe-emerald opacity-60 animate-ping" />
              <span className="relative inline-flex w-1.5 h-1.5 rounded-full bg-vibe-emerald" />
            </span>
            agent.ticker
          </span>
          <span className="hidden sm:inline text-[10px] font-mono text-slate-600 shrink-0">
            <Bot className="w-3 h-3 inline mr-1 align-[-2px]" />
            {logs.length}
          </span>
          <div className="flex-1 min-w-0 text-[10px] font-mono text-slate-400 truncate">
            <span className="text-vibe-cyan select-none mr-1.5">$</span>
            <span key={active?.id ?? 'idle'} className="inline-block max-w-full align-middle">
              {message}
              <span className="inline-block w-1.5 h-3 bg-vibe-cyan/70 ml-0.5 animate-pulse align-[-2px]" />
            </span>
          </div>
          <span className={'shrink-0 text-[9px] font-mono ring-1 ring-inset rounded px-1.5 py-0.5 ' + (unavailable ? 'text-slate-500 ring-slate-700/40' : active?.isApproved === 1 ? 'text-vibe-emerald ring-vibe-emerald/30' : active ? 'text-amber-400 ring-amber-500/40' : 'text-slate-600 ring-vibe-border')}>
            {status}
          </span>
          <Radio className="hidden md:block w-3 h-3 text-slate-700 shrink-0" />
        </div>
      </div>
    </div>
  );
}

export default function Footer() {
  return (
    <footer className="bg-vibe-surface border-t border-vibe-border mt-auto">
      <AgentTicker />
      <div className="max-w-[1400px] mx-auto px-4 sm:px-6 lg:px-8 py-6">
        <div className="flex flex-col sm:flex-row items-center justify-between gap-3">
          <p className="text-xs font-mono text-slate-500">
            &copy; {new Date().getFullYear()} Nexus-Vibe. All rights reserved.
          </p>
          <p className="text-[10px] font-mono text-slate-600">
            Prompt studio · Multi-agent review · Gravity-ranked feed
          </p>
        </div>
      </div>
    </footer>
  );
}
