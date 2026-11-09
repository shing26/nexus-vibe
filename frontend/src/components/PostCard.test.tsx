import { afterEach, describe, expect, it, vi } from 'vitest';
import { render } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import PostCard from './PostCard';
import type { PostPageVo } from '../types/post';

/**
 * A7: the card is unchanged for everyone else.
 *
 * <p>`PostCard` gained an optional `showStatus` prop for the author's own profile. A
 * public feed passes nothing, so every consumer that existed before this change must
 * render byte-for-byte what it rendered before — including a rejected row, whose
 * `rejectionReason` is present in the data but must not appear on a card that was not
 * asked to show its state. The expected strings below are `container.innerHTML` from
 * `PostCard` at the base commit (`1ce682a`), captured once and recorded here so the
 * assertion does not read git at run time. `Date.now` is pinned because the metadata
 * row prints a relative time.</p>
 */

const NOW = new Date('2026-11-09T12:00:00.000Z').getTime();

const fixtures: Record<string, PostPageVo> = {
  activeText: {
    id: '1001',
    title: 'A plain active post',
    content: 'First line\nSecond line',
    summary: '',
    authorName: 'alice',
    categoryName: 'general',
    viewCount: 12,
    likeCount: 3,
    commentCount: 1,
    status: 1,
    createTime: '2026-11-08T12:00:00.000Z',
    aiReviewed: 1,
    aiReviewScore: 8.5,
    postType: 'text',
  },
  pendingPrompt: {
    id: '1002',
    title: 'A held prompt',
    content: 'Intro\n```ts\nconst x = 1;\nconst y = 2;\n```\nTail',
    summary: '',
    authorName: 'bob',
    categoryName: 'prompts',
    viewCount: 0,
    likeCount: 0,
    commentCount: 0,
    status: 2,
    createTime: '2026-11-09T06:00:00.000Z',
    aiReviewed: 2,
    aiReviewScore: 0,
    postType: 'prompt',
    promptMetadata: '{"role":"reviewer"}',
  },
  rejected: {
    id: '1003',
    title: 'A rejected post',
    content: '<p>Bad <b>stuff</b></p>',
    summary: '',
    authorName: 'carol',
    categoryName: 'general',
    viewCount: 5,
    likeCount: 0,
    commentCount: 0,
    status: 3,
    createTime: '2026-11-07T12:00:00.000Z',
    aiReviewed: 0,
    aiReviewScore: 0,
    rejectionReason: 'Spam',
  },
  emptyFailed: {
    id: '1004',
    title: 'A failed empty post',
    content: '',
    summary: '',
    authorName: 'dave',
    categoryName: 'general',
    viewCount: 0,
    likeCount: 0,
    commentCount: 0,
    status: 1,
    createTime: '2026-11-09T11:59:00.000Z',
    aiReviewed: 3,
    aiReviewScore: 0,
  },
};

/** `container.innerHTML` from the component at base commit `1ce682a`. */
const BASE_MARKUP: Record<string, string> = {
  activeText: "<div class=\"relative overflow-hidden\"><div style=\"--size: 150px; --duration: 6; --color-from: #06B6D4; --color-to: #A855F7;\" class=\"pointer-events-none absolute inset-0 rounded-[inherit] border border-transparent [mask-clip:padding-box,border-box] [mask-composite:intersect] [mask-image:linear-gradient(transparent,transparent),linear-gradient(#000,#000)] \"><div class=\"absolute aspect-square w-[var(--size)] animate-border-beam motion-reduce:animate-none bg-gradient-to-l from-[var(--color-from)] via-[var(--color-to)] to-transparent\" style=\"offset-path: rect(0 auto auto 0 round 12px);\"></div></div><div class=\"active:scale-[0.99] transition-transform\"><div class=\"relative overflow-hidden rounded-xl border border-vibe-border bg-vibe-surface/90 p-4 transition-colors duration-300 group/card\"><div class=\"pointer-events-none absolute -inset-px transition-opacity duration-300 z-0\" style=\"opacity: 0; background: radial-gradient(500px circle at 0px 0px, rgba(16, 185, 129, 0.12), transparent 40%);\"></div><div class=\"relative z-10\"><div class=\"absolute top-3 right-3 z-20 flex items-center gap-1.5 opacity-0 translate-y-1 group-hover/card:opacity-100 group-hover/card:translate-y-0 group-focus-within/card:opacity-100 group-focus-within/card:translate-y-0 transition-all duration-200\"><button title=\"Copy prompt\" class=\"inline-flex items-center gap-1 px-2.5 py-1.5 rounded-md bg-vibe-card/90 backdrop-blur border border-vibe-border text-[10px] font-mono text-slate-300 hover:text-vibe-cyan hover:border-vibe-cyan/40 transition-colors active:scale-[0.95]\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-copy w-3 h-3\" aria-hidden=\"true\"><rect width=\"14\" height=\"14\" x=\"8\" y=\"8\" rx=\"2\" ry=\"2\"></rect><path d=\"M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2\"></path></svg>Copy</button></div><a class=\"block\" href=\"/post/1001\"><h3 class=\"font-mono text-sm text-slate-100 leading-snug hover:text-vibe-cyan transition-colors\"><span class=\"text-slate-500\"># </span>A plain active post</h3><p class=\"mt-1.5 font-mono text-[11px] text-slate-500 line-clamp-1\">// First line\nSecond line...</p><div class=\"mt-3 rounded-lg overflow-hidden border border-vibe-border bg-vibe-bg/80\"><div class=\"flex items-center gap-1.5 px-2.5 py-1.5 bg-vibe-card/70 border-b border-vibe-border\"><span class=\"w-1.5 h-1.5 rounded-full bg-red-500/70\"></span><span class=\"w-1.5 h-1.5 rounded-full bg-yellow-500/70\"></span><span class=\"w-1.5 h-1.5 rounded-full bg-green-500/70\"></span><span class=\"ml-2 text-[9px] font-mono text-slate-500 truncate\">notes.txt</span></div><div class=\"px-3 py-2 font-mono text-[11px] leading-relaxed text-slate-400\"><div class=\"flex gap-2.5 truncate\"><span class=\"text-slate-700 select-none\">1</span><span class=\"truncate\">First line</span></div><div class=\"flex gap-2.5 truncate\"><span class=\"text-slate-700 select-none\">2</span><span class=\"truncate\">Second line</span></div></div></div></a><div class=\"mt-2.5 flex flex-wrap items-center gap-3 text-[11px] font-mono\"><span class=\"bg-vibe-cyan/10 border border-vibe-cyan/30 text-vibe-cyan rounded-md px-2 py-0.5\">general</span><span class=\"font-mono text-vibe-cyan\">AI: 85</span><span class=\"text-slate-500\">alice</span><span class=\"text-slate-600\">·</span><span class=\"text-slate-500\">1d ago</span><div class=\"ml-auto flex items-center gap-2 text-slate-500\"><button class=\"hover:text-vibe-cyan transition-colors active:scale-[0.95]\" title=\"Copy link\"><svg class=\"w-3 h-3\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1\"></path></svg></button><span class=\"flex items-center gap-1\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-heart w-3 h-3\" aria-hidden=\"true\"><path d=\"M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5\"></path></svg>3</span><span class=\"flex items-center gap-1\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-message-circle w-3 h-3\" aria-hidden=\"true\"><path d=\"M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719\"></path></svg>1</span><span class=\"flex items-center gap-1\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-eye w-3 h-3\" aria-hidden=\"true\"><path d=\"M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0\"></path><circle cx=\"12\" cy=\"12\" r=\"3\"></circle></svg>12</span></div></div></div></div></div></div>",
  pendingPrompt: "<div class=\"relative overflow-hidden\"><div class=\"active:scale-[0.99] transition-transform\"><div class=\"relative overflow-hidden rounded-xl border border-vibe-border bg-vibe-surface/90 p-4 transition-colors duration-300 group/card\"><div class=\"pointer-events-none absolute -inset-px transition-opacity duration-300 z-0\" style=\"opacity: 0; background: radial-gradient(500px circle at 0px 0px, rgba(16, 185, 129, 0.12), transparent 40%);\"></div><div class=\"relative z-10\"><div class=\"absolute top-3 right-3 z-20 flex items-center gap-1.5 opacity-0 translate-y-1 group-hover/card:opacity-100 group-hover/card:translate-y-0 group-focus-within/card:opacity-100 group-focus-within/card:translate-y-0 transition-all duration-200\"><button title=\"Copy prompt\" class=\"inline-flex items-center gap-1 px-2.5 py-1.5 rounded-md bg-vibe-card/90 backdrop-blur border border-vibe-border text-[10px] font-mono text-slate-300 hover:text-vibe-cyan hover:border-vibe-cyan/40 transition-colors active:scale-[0.95]\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-copy w-3 h-3\" aria-hidden=\"true\"><rect width=\"14\" height=\"14\" x=\"8\" y=\"8\" rx=\"2\" ry=\"2\"></rect><path d=\"M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2\"></path></svg>Copy</button><button title=\"Fork template\" class=\"inline-flex items-center gap-1 px-2.5 py-1.5 rounded-md bg-vibe-card/90 backdrop-blur border border-vibe-border text-[10px] font-mono text-slate-300 hover:text-vibe-purple hover:border-vibe-purple/40 transition-colors active:scale-[0.95] disabled:opacity-50\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-git-fork w-3 h-3\" aria-hidden=\"true\"><circle cx=\"12\" cy=\"18\" r=\"3\"></circle><circle cx=\"6\" cy=\"6\" r=\"3\"></circle><circle cx=\"18\" cy=\"6\" r=\"3\"></circle><path d=\"M18 9v2c0 .6-.4 1-1 1H7c-.6 0-1-.4-1-1V9\"></path><path d=\"M12 12v3\"></path></svg>Fork</button></div><a class=\"block\" href=\"/post/1002\"><h3 class=\"font-mono text-sm text-slate-100 leading-snug hover:text-vibe-cyan transition-colors\"><span class=\"text-slate-500\"># </span>A held prompt</h3><p class=\"mt-1.5 font-mono text-[11px] text-slate-500 line-clamp-1\">// Intro\n```ts\nconst x = 1;\nconst y = 2;\n```\nTail...</p><div class=\"mt-3 rounded-lg overflow-hidden border border-vibe-border bg-vibe-bg/80\"><div class=\"flex items-center gap-1.5 px-2.5 py-1.5 bg-vibe-card/70 border-b border-vibe-border\"><span class=\"w-1.5 h-1.5 rounded-full bg-red-500/70\"></span><span class=\"w-1.5 h-1.5 rounded-full bg-yellow-500/70\"></span><span class=\"w-1.5 h-1.5 rounded-full bg-green-500/70\"></span><span class=\"ml-2 text-[9px] font-mono text-slate-500 truncate\">ts.</span></div><div class=\"px-3 py-2 font-mono text-[11px] leading-relaxed text-slate-400\"><div class=\"flex gap-2.5 truncate\"><span class=\"text-slate-700 select-none\">1</span><span class=\"truncate\">const x = 1;</span></div><div class=\"flex gap-2.5 truncate\"><span class=\"text-slate-700 select-none\">2</span><span class=\"truncate\">const y = 2;</span></div></div></div></a><div class=\"mt-2.5 flex flex-wrap items-center gap-3 text-[11px] font-mono\"><span class=\"bg-vibe-cyan/10 border border-vibe-cyan/30 text-vibe-cyan rounded-md px-2 py-0.5\">prompts</span><span class=\"bg-vibe-purple/10 border border-vibe-purple/30 text-vibe-purple rounded-md px-2 py-0.5 text-[10px] font-mono\">Template</span><span class=\"text-slate-500\">bob</span><span class=\"text-slate-500 truncate max-w-[120px]\" title=\"reviewer\">role: reviewer</span><span class=\"text-slate-600\">·</span><span class=\"text-slate-500\">6h ago</span><div class=\"ml-auto flex items-center gap-2 text-slate-500\"><button class=\"hover:text-vibe-cyan transition-colors active:scale-[0.95]\" title=\"Copy link\"><svg class=\"w-3 h-3\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1\"></path></svg></button><span class=\"flex items-center gap-1\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-heart w-3 h-3\" aria-hidden=\"true\"><path d=\"M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5\"></path></svg>0</span><span class=\"flex items-center gap-1\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-message-circle w-3 h-3\" aria-hidden=\"true\"><path d=\"M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719\"></path></svg>0</span><span class=\"flex items-center gap-1\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-eye w-3 h-3\" aria-hidden=\"true\"><path d=\"M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0\"></path><circle cx=\"12\" cy=\"12\" r=\"3\"></circle></svg>0</span></div></div></div></div></div></div>",
  rejected: "<div class=\"relative overflow-hidden\"><div class=\"active:scale-[0.99] transition-transform\"><div class=\"relative overflow-hidden rounded-xl border border-vibe-border bg-vibe-surface/90 p-4 transition-colors duration-300 group/card\"><div class=\"pointer-events-none absolute -inset-px transition-opacity duration-300 z-0\" style=\"opacity: 0; background: radial-gradient(500px circle at 0px 0px, rgba(16, 185, 129, 0.12), transparent 40%);\"></div><div class=\"relative z-10\"><div class=\"absolute top-3 right-3 z-20 flex items-center gap-1.5 opacity-0 translate-y-1 group-hover/card:opacity-100 group-hover/card:translate-y-0 group-focus-within/card:opacity-100 group-focus-within/card:translate-y-0 transition-all duration-200\"><button title=\"Copy prompt\" class=\"inline-flex items-center gap-1 px-2.5 py-1.5 rounded-md bg-vibe-card/90 backdrop-blur border border-vibe-border text-[10px] font-mono text-slate-300 hover:text-vibe-cyan hover:border-vibe-cyan/40 transition-colors active:scale-[0.95]\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-copy w-3 h-3\" aria-hidden=\"true\"><rect width=\"14\" height=\"14\" x=\"8\" y=\"8\" rx=\"2\" ry=\"2\"></rect><path d=\"M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2\"></path></svg>Copy</button></div><a class=\"block\" href=\"/post/1003\"><h3 class=\"font-mono text-sm text-slate-100 leading-snug hover:text-vibe-cyan transition-colors\"><span class=\"text-slate-500\"># </span>A rejected post</h3><p class=\"mt-1.5 font-mono text-[11px] text-slate-500 line-clamp-1\">// Bad stuff...</p><div class=\"mt-3 rounded-lg overflow-hidden border border-vibe-border bg-vibe-bg/80\"><div class=\"flex items-center gap-1.5 px-2.5 py-1.5 bg-vibe-card/70 border-b border-vibe-border\"><span class=\"w-1.5 h-1.5 rounded-full bg-red-500/70\"></span><span class=\"w-1.5 h-1.5 rounded-full bg-yellow-500/70\"></span><span class=\"w-1.5 h-1.5 rounded-full bg-green-500/70\"></span><span class=\"ml-2 text-[9px] font-mono text-slate-500 truncate\">notes.txt</span></div><div class=\"px-3 py-2 font-mono text-[11px] leading-relaxed text-slate-400\"><div class=\"flex gap-2.5 truncate\"><span class=\"text-slate-700 select-none\">1</span><span class=\"truncate\">Bad stuff</span></div></div></div></a><div class=\"mt-2.5 flex flex-wrap items-center gap-3 text-[11px] font-mono\"><span class=\"bg-vibe-cyan/10 border border-vibe-cyan/30 text-vibe-cyan rounded-md px-2 py-0.5\">general</span><span class=\"text-slate-500\">carol</span><span class=\"text-slate-600\">·</span><span class=\"text-slate-500\">2d ago</span><div class=\"ml-auto flex items-center gap-2 text-slate-500\"><button class=\"hover:text-vibe-cyan transition-colors active:scale-[0.95]\" title=\"Copy link\"><svg class=\"w-3 h-3\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1\"></path></svg></button><span class=\"flex items-center gap-1\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-heart w-3 h-3\" aria-hidden=\"true\"><path d=\"M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5\"></path></svg>0</span><span class=\"flex items-center gap-1\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-message-circle w-3 h-3\" aria-hidden=\"true\"><path d=\"M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719\"></path></svg>0</span><span class=\"flex items-center gap-1\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-eye w-3 h-3\" aria-hidden=\"true\"><path d=\"M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0\"></path><circle cx=\"12\" cy=\"12\" r=\"3\"></circle></svg>5</span></div></div></div></div></div></div>",
  emptyFailed: "<div class=\"relative overflow-hidden\"><div class=\"active:scale-[0.99] transition-transform\"><div class=\"relative overflow-hidden rounded-xl border border-vibe-border bg-vibe-surface/90 p-4 transition-colors duration-300 group/card\"><div class=\"pointer-events-none absolute -inset-px transition-opacity duration-300 z-0\" style=\"opacity: 0; background: radial-gradient(500px circle at 0px 0px, rgba(16, 185, 129, 0.12), transparent 40%);\"></div><div class=\"relative z-10\"><a class=\"block\" href=\"/post/1004\"><h3 class=\"font-mono text-sm text-slate-100 leading-snug hover:text-vibe-cyan transition-colors\"><span class=\"text-slate-500\"># </span>A failed empty post</h3></a><div class=\"mt-2.5 flex flex-wrap items-center gap-3 text-[11px] font-mono\"><span class=\"bg-vibe-cyan/10 border border-vibe-cyan/30 text-vibe-cyan rounded-md px-2 py-0.5\">general</span><span class=\"text-slate-500\">dave</span><span class=\"text-slate-600\">·</span><span class=\"text-slate-500\">1m ago</span><div class=\"ml-auto flex items-center gap-2 text-slate-500\"><button class=\"hover:text-vibe-cyan transition-colors active:scale-[0.95]\" title=\"Copy link\"><svg class=\"w-3 h-3\" fill=\"none\" stroke=\"currentColor\" viewBox=\"0 0 24 24\"><path stroke-linecap=\"round\" stroke-linejoin=\"round\" stroke-width=\"2\" d=\"M13.828 10.172a4 4 0 00-5.656 0l-4 4a4 4 0 105.656 5.656l1.102-1.101m-.758-4.899a4 4 0 005.656 0l4-4a4 4 0 00-5.656-5.656l-1.1 1.1\"></path></svg></button><span class=\"flex items-center gap-1\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-heart w-3 h-3\" aria-hidden=\"true\"><path d=\"M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5\"></path></svg>0</span><span class=\"flex items-center gap-1\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-message-circle w-3 h-3\" aria-hidden=\"true\"><path d=\"M2.992 16.342a2 2 0 0 1 .094 1.167l-1.065 3.29a1 1 0 0 0 1.236 1.168l3.413-.998a2 2 0 0 1 1.099.092 10 10 0 1 0-4.777-4.719\"></path></svg>0</span><span class=\"flex items-center gap-1\"><svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" class=\"lucide lucide-eye w-3 h-3\" aria-hidden=\"true\"><path d=\"M2.062 12.348a1 1 0 0 1 0-.696 10.75 10.75 0 0 1 19.876 0 1 1 0 0 1 0 .696 10.75 10.75 0 0 1-19.876 0\"></path><circle cx=\"12\" cy=\"12\" r=\"3\"></circle></svg>0</span></div></div></div></div></div></div>",
};

describe('PostCard rendered without the status prop (A7)', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  for (const name of Object.keys(fixtures)) {
    it(`renders the ${name} fixture exactly as the base commit did`, () => {
      vi.spyOn(Date, 'now').mockReturnValue(NOW);

      const { container } = render(
        <MemoryRouter>
          <PostCard post={fixtures[name]} />
        </MemoryRouter>,
      );

      expect(container.innerHTML).toBe(BASE_MARKUP[name]);
    });
  }
});
