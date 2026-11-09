import { useEffect, useState } from 'react';
import { Link, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { motion } from 'motion/react';
import { apiClient } from '../api/client';
import PostCard from '../components/PostCard';
import Pagination from '../components/Pagination';
import Sidebar from '../components/Sidebar';
import EmptyState from '../components/EmptyState';
import type { Channel, PostPageVo } from '../types/post';

const DESKTOP_QUERY = '(min-width: 768px)';

/**
 * A4-4: the unanswered panel is not mounted below `md`, rather than mounted and
 * hidden with CSS. At phone widths it would push the feed below the fold, and
 * "not rendered" is a stronger promise than `display: none`.
 */
function useIsDesktop(): boolean {
  const [matches, setMatches] = useState(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return false;
    return window.matchMedia(DESKTOP_QUERY).matches;
  });

  useEffect(() => {
    if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') return;
    const mql = window.matchMedia(DESKTOP_QUERY);
    const onChange = () => setMatches(mql.matches);
    onChange();
    mql.addEventListener('change', onChange);
    return () => mql.removeEventListener('change', onChange);
  }, []);

  return matches;
}

export default function ChannelPage() {
  const { slug } = useParams<{ slug: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const [page, setPage] = useState(1);
  const isDesktop = useIsDesktop();
  // A6-4: the ordering lives in the URL so a refresh keeps it and a link shares it.
  const sort = searchParams.get('sort') === 'liked' ? 'liked' : 'latest';

  const changeSort = (next: 'latest' | 'liked') => {
    const nextParams = new URLSearchParams(searchParams);
    if (next === 'liked') {
      nextParams.set('sort', 'liked');
    } else {
      nextParams.delete('sort');
    }
    setSearchParams(nextParams, { replace: true });
    setPage(1);
  };

  const { data: channels } = useQuery({
    queryKey: ['channels'],
    queryFn: async () => {
      const res = await apiClient.get('/channels');
      return res.data.data as Channel[];
    },
    staleTime: 1000 * 60 * 10,
  });

  const channel = channels?.find((c: any) => c.slug === slug);

  const emptyGuides: Record<string, { title: string; desc: string; action: string; actionLabel: string }> = {
    prompts: {
      title: '终端就绪，等待第一个 Prompt 入库',
      desc: '发布一个 System Prompt 模板，AI Agent 会自动审查并回帖。',
      action: '/post/new?template=prompt',
      actionLabel: '一键填充示例 Prompt',
    },
    debug: {
      title: '急诊室空转中',
      desc: '贴上你的报错上下文，AI Agent 与社区会一起定位问题。',
      action: '/post/new?template=debug',
      actionLabel: '提交第一个报错',
    },
    showcase: {
      title: '还没有成品展示',
      desc: '发布你 Vibe Coding 出来的网页、工具或自动化流程。',
      action: '/post/new?template=showcase',
      actionLabel: '发布第一个作品',
    },
    agents: {
      title: 'Agent 实战区等待案例',
      desc: '分享你基于 OpenClaw、Codex 或自研 Multi-Agent 的工作流。',
      action: '/post/new?template=agents',
      actionLabel: '分享 Agent 实战',
    },
    'vibe-coding': {
      title: '经验区等待第一篇',
      desc: '聊聊上下文控制、幻觉治理与 AI 时代架构设计。',
      action: '/post/new',
      actionLabel: '写第一篇经验',
    },
    resources: {
      title: '资源聚合区还是空的',
      desc: '推荐工具链、API 评测与高质量教程链接。',
      action: '/post/new',
      actionLabel: '推荐一个资源',
    },
  };
  const emptyGuide = channel ? emptyGuides[channel.slug] : undefined;

  const { data: postsData, isLoading } = useQuery({
    queryKey: ['posts', 'channel', slug, page, sort, slug === 'prompts' ? 'prompt' : 'all'],
    queryFn: async () => {
      const res = await apiClient.get('/posts', {
        params: {
          channelSlug: slug,
          page,
          size: 10,
          type: slug === 'prompts' ? 'prompt' : 'all',
          ...(sort === 'liked' ? { sort: 'liked' } : {}),
        },
      });
      return res.data.data;
    },
    enabled: !!slug && !!channel,
    staleTime: 1000 * 60 * 2,
  });

  const { data: unanswered } = useQuery<PostPageVo[]>({
    queryKey: ['posts', 'unanswered'],
    queryFn: async () => {
      const res = await apiClient.get('/posts/unanswered', { params: { size: 5 } });
      return (res.data.data ?? []) as PostPageVo[];
    },
    staleTime: 1000 * 60 * 2,
  });

  if (!slug) {
    return (
      <div className="max-w-[1400px] mx-auto px-4 py-16 text-center">
        <p className="text-slate-500 text-sm font-mono">No channel specified</p>
      </div>
    );
  }

  if (channels && !channel) {
    return (
      <div className="max-w-[1400px] mx-auto px-4 py-16 text-center">
        <h2 className="text-lg font-bold font-mono text-slate-100 mb-2">Channel Not Found</h2>
        <p className="text-sm font-mono text-slate-500">Channel &quot;{slug}&quot; doesn&apos;t exist</p>
      </div>
    );
  }

  return (
    <div className="max-w-[1400px] mx-auto px-4 py-8">
      <div className="flex gap-4">
        {/* Icon-rail sidebar */}
        <div className="w-12 shrink-0 hidden lg:block">
          <Sidebar />
        </div>
        <div className="flex-1 min-w-0">
          {channel && (
            <h1 className="text-xl font-bold font-mono text-slate-100 pb-3 border-b border-vibe-border mb-6">
              # {channel.name}
            </h1>
          )}

          {channel && (
            <div className="mb-4 flex items-center gap-2 font-mono text-[11px]">
              <span className="text-slate-500">Order:</span>
              {(['latest', 'liked'] as const).map((option) => (
                <button
                  key={option}
                  type="button"
                  onClick={() => changeSort(option)}
                  aria-pressed={sort === option}
                  className={
                    'rounded-md border px-2.5 py-1 transition-colors active:scale-[0.97] ' +
                    (sort === option
                      ? 'border-vibe-cyan/40 bg-vibe-cyan/15 text-vibe-cyan'
                      : 'border-vibe-border text-slate-400 hover:border-vibe-cyan/40 hover:text-slate-200')
                  }
                >
                  {option === 'latest' ? 'Latest' : 'Most liked'}
                </button>
              ))}
            </div>
          )}

          {isLoading ? (
            <div className="space-y-3">
              {[1, 2, 3].map((i) => (
                <div key={i} className="bg-vibe-surface/50 border border-vibe-border rounded-xl p-4 relative overflow-hidden">
                  <div className="absolute inset-0 bg-gradient-to-r from-transparent via-vibe-cyan/5 to-transparent bg-[length:200%_100%] animate-shimmer" />
                  <div className="h-3 bg-vibe-card rounded w-3/4 mb-3 relative" />
                  <div className="h-2.5 bg-vibe-card/50 rounded w-1/2 mb-2 relative" />
                  <div className="h-2 bg-vibe-card/30 rounded w-1/4 relative" />
                </div>
              ))}
            </div>
          ) : postsData && postsData.list.length > 0 ? (
            <>
              <div className="space-y-3">
                {postsData.list.map((post: any, i: number) => (
                  <motion.div
                    key={post.id}
                    initial={{ opacity: 0, y: 12 }}
                    animate={{ opacity: 1, y: 0 }}
                    transition={{ duration: 0.2, delay: i * 0.03 }}
                  >
                    <PostCard post={post} />
                  </motion.div>
                ))}
              </div>
              <Pagination
                page={postsData.page}
                pages={postsData.pages}
                onPageChange={setPage}
              />
            </>
          ) : (
            <EmptyState preset="noPosts" {...emptyGuide} />
          )}
        </div>

        {isDesktop && channel && (
          <aside aria-label="Waiting for an answer" className="w-72 shrink-0">
            <div className="rounded-xl border border-vibe-border bg-vibe-surface/80 p-4">
              <h2 className="mb-1 font-mono text-xs font-semibold text-slate-300">
                // Waiting for an answer
              </h2>
              <p className="mb-3 font-mono text-[10px] text-slate-500">
                Active posts with no replies yet.
              </p>
              {unanswered === undefined ? null : unanswered.length > 0 ? (
                <ul className="space-y-1">
                  {unanswered.map((post) => (
                    <li key={post.id}>
                      <Link
                        to={'/post/' + post.id}
                        className="group block rounded-lg p-2 transition-colors hover:bg-vibe-card/70"
                      >
                        <p className="line-clamp-2 font-mono text-[11px] leading-snug text-slate-300 group-hover:text-vibe-cyan">
                          {post.title}
                        </p>
                        <p className="mt-1 font-mono text-[10px] text-slate-500">
                          {post.authorName} · {post.likeCount} likes · no replies
                        </p>
                      </Link>
                    </li>
                  ))}
                </ul>
              ) : (
                <EmptyState
                  preset="noPosts"
                  title="Nothing is waiting"
                  desc="Posts with no replies appear here; write the first reply on a post and it leaves this list."
                  action={null}
                  actionLabel={null}
                  className="py-8"
                />
              )}
            </div>
          </aside>
        )}
      </div>
    </div>
  );
}
