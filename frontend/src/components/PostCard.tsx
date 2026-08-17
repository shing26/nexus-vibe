import { Link, useNavigate } from 'react-router-dom';
import type { PostPageVo } from '../types/post';
import { SpotlightCard } from './ui/SpotlightCard';
import { BorderBeam } from './ui/BorderBeam';
import { Heart, MessageCircle, Eye, Copy, Check, GitFork } from 'lucide-react';
import { useState } from 'react';
import { apiClient } from '../api/client';
import { useAuthStore } from '../stores/authStore';
import { useToastStore } from '../stores/toastStore';

interface PostCardProps {
  post: PostPageVo;
}

const SUMMARY_LENGTH = 80;

function stripHtml(text: string): string {
  return text?.replace(/<[^>]*>/g, '') ?? '';
}

interface PreviewInfo {
  label: string;
  lines: string[];
  code: string;
}

function getPreview(post: PostPageVo): PreviewInfo | null {
  const content = post.content || '';
  const fenced = content.match(/```([a-zA-Z0-9_+-]*)\n([\s\S]*?)```/);
  if (fenced) {
    const code = fenced[2].trim();
    const lines = code.split('\n').map((l) => l.trim()).filter(Boolean).slice(0, 2);
    return { label: fenced[1] || 'code', lines, code };
  }
  const plain = stripHtml(content).split('\n').map((l) => l.trim()).filter(Boolean).slice(0, 2);
  if (plain.length === 0) return null;
  return {
    label: post.postType === 'prompt' ? 'prompt' : 'text',
    lines: plain,
    code: plain.join('\n'),
  };
}

export default function PostCard({ post }: PostCardProps) {
  const navigate = useNavigate();
  const addToast = useToastStore((s) => s.addToast);
  const user = useAuthStore((s) => s.user);
  const [copied, setCopied] = useState(false);
  const [forking, setForking] = useState(false);
  const preview = getPreview(post);

  const timeAgo = (dateStr: string) => {
    const diff = Date.now() - new Date(dateStr).getTime();
    const minutes = Math.floor(diff / 60000);
    if (minutes < 1) return 'just now';
    if (minutes < 60) return minutes + 'm ago';
    const hours = Math.floor(minutes / 60);
    if (hours < 24) return hours + 'h ago';
    const days = Math.floor(hours / 24);
    if (days < 30) return days + 'd ago';
    return new Date(dateStr).toLocaleDateString();
  };

  const aiScore = (post.aiReviewScore ?? 0) * 10;
  const scoreColor = aiScore >= 80 ? 'text-vibe-cyan' : aiScore >= 50 ? 'text-yellow-400' : 'text-red-400';
  const shortSummary = stripHtml(post.summary || post.content).slice(0, SUMMARY_LENGTH);

  const meta = post.promptMetadata ? (() => { try { return JSON.parse(post.promptMetadata); } catch { return null; } })() : null;

  const handleCopyLink = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    navigator.clipboard.writeText(window.location.origin + '/post/' + post.id);
    addToast('Link copied to clipboard', 'success');
  };

  const handleCopyPrompt = (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!preview) return;
    navigator.clipboard.writeText(preview.code);
    setCopied(true);
    addToast('Prompt copied', 'success');
    setTimeout(() => setCopied(false), 2000);
  };

  const handleFork = async (e: React.MouseEvent) => {
    e.preventDefault();
    e.stopPropagation();
    if (!user) {
      navigate('/login');
      return;
    }
    setForking(true);
    try {
      const res = await apiClient.post('/posts/' + post.id + '/fork');
      addToast('Template forked', 'success');
      navigate('/post/' + res.data.data.postId);
    } catch {
      addToast('Fork failed', 'error');
    } finally {
      setForking(false);
    }
  };

  return (
    <div className="relative overflow-hidden">
      {post.aiReviewed === 1 && <BorderBeam size={150} duration={6} />}
      <div className="active:scale-[0.99] transition-transform">
        <SpotlightCard className="group/card">
          {/* Quick Action Bar — hover reveal */}
          {(preview || post.postType === 'prompt') && (
            <div className="absolute top-3 right-3 z-20 flex items-center gap-1.5 opacity-0 translate-y-1 group-hover/card:opacity-100 group-hover/card:translate-y-0 transition-all duration-200">
              {preview && (
                <button
                  onClick={handleCopyPrompt}
                  title="Copy prompt"
                  className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-md bg-vibe-card/90 backdrop-blur border border-vibe-border text-[10px] font-mono text-slate-300 hover:text-vibe-cyan hover:border-vibe-cyan/40 transition-colors active:scale-[0.95]"
                >
                  {copied ? <Check className="w-3 h-3 text-vibe-cyan" /> : <Copy className="w-3 h-3" />}
                  {copied ? 'Copied' : 'Copy'}
                </button>
              )}
              {post.postType === 'prompt' && (
                <button
                  onClick={handleFork}
                  disabled={forking}
                  title="Fork template"
                  className="inline-flex items-center gap-1 px-2.5 py-1.5 rounded-md bg-vibe-card/90 backdrop-blur border border-vibe-border text-[10px] font-mono text-slate-300 hover:text-vibe-purple hover:border-vibe-purple/40 transition-colors active:scale-[0.95] disabled:opacity-50"
                >
                  <GitFork className="w-3 h-3" />
                  {forking ? 'Forking' : 'Fork'}
                </button>
              )}
            </div>
          )}

          <Link to={'/post/' + post.id} className="block">
            {/* Title as code comment */}
            <h3 className="font-mono text-sm text-slate-100 leading-snug hover:text-vibe-cyan transition-colors">
              <span className="text-slate-500"># </span>{post.title}
            </h3>
            {/* Summary as code */}
            {shortSummary && (
              <p className="mt-1.5 font-mono text-[11px] text-slate-500 line-clamp-1">
                // {shortSummary}...
              </p>
            )}
            {/* Terminal preview block */}
            {preview && (
              <div className="mt-3 rounded-lg overflow-hidden border border-vibe-border bg-vibe-bg/80">
                <div className="flex items-center gap-1.5 px-2.5 py-1.5 bg-vibe-card/70 border-b border-vibe-border">
                  <span className="w-1.5 h-1.5 rounded-full bg-red-500/70" />
                  <span className="w-1.5 h-1.5 rounded-full bg-yellow-500/70" />
                  <span className="w-1.5 h-1.5 rounded-full bg-green-500/70" />
                  <span className="ml-2 text-[9px] font-mono text-slate-500 truncate">
                    {preview.label === 'prompt' ? 'prompt.md' : preview.label === 'text' ? 'notes.txt' : preview.label + '.'}
                  </span>
                </div>
                <div className="px-3 py-2 font-mono text-[11px] leading-relaxed text-slate-400">
                  {preview.lines.length > 0 ? (
                    preview.lines.map((line, i) => (
                      <div key={i} className="flex gap-2.5 truncate">
                        <span className="text-slate-700 select-none">{i + 1}</span>
                        <span className="truncate">{line || '\u00A0'}</span>
                      </div>
                    ))
                  ) : (
                    <div className="flex gap-2.5 truncate">
                      <span className="text-slate-700 select-none">1</span>
                      <span className="text-slate-600 truncate">// no preview available</span>
                    </div>
                  )}
                </div>
              </div>
            )}
          </Link>
          {/* Metadata row — compact */}
          <div className="mt-2.5 flex flex-wrap items-center gap-3 text-[11px] font-mono">
            <span className="bg-vibe-cyan/10 border border-vibe-cyan/30 text-vibe-cyan rounded-md px-2 py-0.5">
              {post.categoryName}
            </span>
            {post.postType === 'prompt' && (
              <span className="bg-vibe-purple/10 border border-vibe-purple/30 text-vibe-purple rounded-md px-2 py-0.5 text-[10px] font-mono">Template</span>
            )}
            {post.aiReviewed === 1 && (
              <span className={'font-mono ' + scoreColor}>AI: {aiScore}</span>
            )}
            <span className="text-slate-500">{post.authorName}</span>
            {meta?.role && (
              <span className="text-slate-500 truncate max-w-[120px]" title={meta.role}>role: {meta.role}</span>
            )}
            <span className="text-slate-600">·</span>
            <span className="text-slate-500">{timeAgo(post.createTime)}</span>
            <div className="ml-auto flex items-center gap-2 text-slate-500">
              <button onClick={handleCopyLink} className="hover:text-vibe-cyan transition-colors active:scale-[0.95]" title="Copy link">
                <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1" />
                </svg>
              </button>
              <span className="flex items-center gap-1"><Heart className="w-3 h-3" />{post.likeCount}</span>
              <span className="flex items-center gap-1"><MessageCircle className="w-3 h-3" />{post.commentCount}</span>
              <span className="flex items-center gap-1"><Eye className="w-3 h-3" />{post.viewCount}</span>
            </div>
          </div>
        </SpotlightCard>
      </div>
    </div>
  );
}
