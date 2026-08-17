import { useState, useRef, useCallback, useMemo, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { apiClient } from "../api/client";
import { useAuthStore } from "../stores/authStore";
import { useToastStore } from "../stores/toastStore";
import { useChannels, type Channel } from "../api/useChannels";
import {
  Terminal, Sparkles, Tag, Code2, Save, Send, Bug, Palette, Cpu,
} from "lucide-react";

function estimateTokens(text: string): number {
  if (!text.trim()) return 0;
  const chineseChars = (text.match(/[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/g) || []).length;
  const asciiText = text.replace(/[\u4e00-\u9fff\u3400-\u4dbf\uf900-\ufaff]/g, " ");
  const asciiWords = asciiText.split(/\s+/).filter(Boolean).length;
  return Math.round(chineseChars * 2 + asciiWords * 1.3);
}

/** macOS traffic-light dots */
function MacDots() {
  return (
    <div className="flex items-center gap-1.5 px-3">
      <span className="w-2.5 h-2.5 rounded-full bg-red-500/80" />
      <span className="w-2.5 h-2.5 rounded-full bg-yellow-500/80" />
      <span className="w-2.5 h-2.5 rounded-full bg-green-500/80" />
    </div>
  );
}

/** Terminal-styled window wrapper */
function TerminalWindow({ title, children, className = "" }: { title: string; children: React.ReactNode; className?: string }) {
  return (
    <div className={"bg-vibe-surface border border-vibe-border rounded-xl overflow-hidden " + className}>
      {/* Title bar */}
      <div className="flex items-center h-9 bg-vibe-card border-b border-vibe-border select-none">
        <MacDots />
        <span className="flex-1 text-center text-[11px] font-mono text-slate-500 truncate px-2">{title}</span>
        <div className="w-16" /> {/* balance */}
      </div>
      {children}
    </div>
  );
}

const PROMPT_TEMPLATE = `## System Prompt
You are an expert coding assistant. Follow these guidelines:
- Write clean, well-documented code
- Prioritize readability over brevity
- Include error handling

## User Request
`;

const TEMPLATES = [
  {
    id: "debug",
    label: "Debug",
    desc: "贴报错上下文",
    icon: Bug,
    channelSlug: "debug",
    postType: "post" as const,
    scaffold: `## 现象
<!-- 描述发生了什么，期望结果是什么 -->

## 报错上下文
\`\`\`text
粘贴报错信息
\`\`\`

## 已尝试
- 

## 补充信息
- 语言/框架：
- 运行环境：`,
  },
  {
    id: "prompt",
    label: "Prompt",
    desc: "System Prompt 模板",
    icon: Terminal,
    channelSlug: "prompts",
    postType: "prompt" as const,
    promptRole: "Expert coding assistant",
    recommendedModel: "",
    temperature: 0.7,
    variablesStr: "",
    scaffold: PROMPT_TEMPLATE,
  },
  {
    id: "showcase",
    label: "Showcase",
    desc: "Vibe Coding 成品",
    icon: Palette,
    channelSlug: "showcase",
    postType: "post" as const,
    scaffold: `## 作品简介
<!-- 一句话说明这是什么 -->

## 技术亮点
- 

## 演示 / 链接
- [项目链接]()

## 说明
<!-- 运行方式、截图或补充背景 -->`,
  },
  {
    id: "agents",
    label: "Agent",
    desc: "Agent 实战案例",
    icon: Cpu,
    channelSlug: "agents",
    postType: "post" as const,
    scaffold: `## Agent 目标
<!-- 这个 Agent 要完成什么任务 -->

## 架构与工具
- 

## 运行效果
- 

## 踩坑与经验
- `,
  },
];

const DRAFT_KEY = "nexus_vibe_drafts";

export default function CreatePostPage() {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const draftId = searchParams.get("draft");
  const isAuthenticated = useAuthStore((s) => s.isAuthenticated);
  const user = useAuthStore((s) => s.user);
  const isAdmin = user?.role === "ADMIN";
  const addToast = useToastStore((s) => s.addToast);
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  const [title, setTitle] = useState("");
  const [categoryId, setCategoryId] = useState<number | null>(null);
  const [content, setContent] = useState("");
  const [tags, setTags] = useState("");
  const [error, setError] = useState("");
  const { data: channels, isLoading: channelsLoading } = useChannels();
  const [submitting, setSubmitting] = useState(false);
  const [activeTab, setActiveTab] = useState<"editor" | "preview">("editor");

  // Post type state
  const [postType, setPostType] = useState<"post" | "prompt">("post");
  const [promptRole, setPromptRole] = useState("");
  const [recommendedModel, setRecommendedModel] = useState("");
  const [temperature, setTemperature] = useState(0.7);
  const [variablesStr, setVariablesStr] = useState("");
  const [template, setTemplate] = useState<string>(searchParams.get("template") || "");

  // Apply template scaffold once channels are available
  useEffect(() => {
    if (draftId || !channels || !template) return;
    const tpl = TEMPLATES.find((t) => t.id === template);
    if (!tpl) return;
    const ch = channels.find((c: Channel) => c.slug === tpl.channelSlug);
    if (ch) setCategoryId(ch.id);
    setPostType(tpl.postType);
    setContent(tpl.scaffold);
    setTitle("");
    setPromptRole("promptRole" in tpl && tpl.promptRole ? tpl.promptRole : "");
    setRecommendedModel("recommendedModel" in tpl && tpl.recommendedModel ? tpl.recommendedModel : "");
    setTemperature("temperature" in tpl && tpl.temperature != null ? tpl.temperature : 0.7);
    setVariablesStr("variablesStr" in tpl && tpl.variablesStr ? tpl.variablesStr : "");
    setActiveTab("editor");
  }, [template, channels, draftId]);

  const selectTemplate = (id: string) => {
    if (id === template) return;
    setTemplate(id);
    navigate("/post/new?template=" + id, { replace: true });
  };

  const displayChannels = useMemo(() => {
    if (!channels) return [];
    return isAdmin ? channels : channels.filter((ch: Channel) => ch.slug !== "announcements");
  }, [channels, isAdmin]);

  const generalChannels = useMemo(
    () => displayChannels.filter((ch: Channel) => ch.slug !== "prompts"),
    [displayChannels]
  );

  const tokens = useMemo(() => estimateTokens(content), [content]);

  useEffect(() => {
    if (!draftId) return;
    try {
      const drafts = JSON.parse(localStorage.getItem(DRAFT_KEY) || "[]");
      const draft = drafts.find((d: any) => String(d.id) === draftId);
      if (!draft) return;
      setTitle(draft.title || "");
      setCategoryId(draft.categoryId ?? null);
      setContent(draft.content || "");
      setTags(draft.tags || "");
      setPostType(draft.postType || "post");
      setPromptRole(draft.promptRole || "");
      setRecommendedModel(draft.recommendedModel || "");
      setTemperature(draft.temperature ?? 0.7);
      setVariablesStr(draft.variablesStr || "");
    } catch {
      // ignore malformed local drafts
    }
  }, [draftId]);

  const insertCodeBlock = useCallback(() => {
    const textarea = textareaRef.current;
    if (!textarea) return;
    const start = textarea.selectionStart;
    const end = textarea.selectionEnd;
    const selected = content.slice(start, end);
    const before = content.slice(0, start);
    const after = content.slice(end);
    let insertion: string, cursorOffset: number;
    if (selected) {
      insertion = "`\n" + selected + "\n`";
      cursorOffset = start + insertion.length;
    } else {
      insertion = "`\n\n`";
      cursorOffset = start + 4;
    }
    setContent(before + insertion + after);
    requestAnimationFrame(() => {
      textarea.focus();
      textarea.setSelectionRange(cursorOffset, cursorOffset);
    });
  }, [content]);

  const handleAiPolish = () => {
    setContent((prev) => (prev.trim() ? prev + "\n\n" + PROMPT_TEMPLATE : PROMPT_TEMPLATE));
  };

  const handleSaveDraft = () => {
    const drafts = JSON.parse(localStorage.getItem(DRAFT_KEY) || "[]");
    const payload = {
      id: draftId ? Number(draftId) : Date.now(),
      title,
      categoryId,
      content,
      tags,
      postType,
      promptRole,
      recommendedModel,
      temperature,
      variablesStr,
      updatedAt: new Date().toISOString(),
    };
    const index = drafts.findIndex((d: any) => String(d.id) === String(payload.id));
    if (index >= 0) {
      drafts[index] = payload;
    } else {
      drafts.push(payload);
    }
    localStorage.setItem(DRAFT_KEY, JSON.stringify(drafts));
    addToast("Draft saved", "success");
    navigate("/post/new?draft=" + payload.id, { replace: true });
  };

  useEffect(() => {
    if (displayChannels.length > 0) {
      const selectedIsAnnouncements =
        channels?.find((ch: Channel) => ch.id === categoryId)?.slug === "announcements";
      if (categoryId === null || (!isAdmin && selectedIsAnnouncements)) {
        setCategoryId(generalChannels[0]?.id ?? displayChannels[0].id);
      }
    }
  }, [displayChannels, generalChannels, channels, categoryId, isAdmin]);

  const handlePostTypeChange = (type: "post" | "prompt") => {
    const selected = channels?.find((ch: Channel) => ch.id === categoryId);
    if (type === "post" && selected?.slug === "prompts") {
      setCategoryId(generalChannels[0]?.id ?? selected.id);
    } else if (type === "prompt" && selected?.slug !== "prompts") {
      const prompts = channels?.find((ch: Channel) => ch.slug === "prompts");
      if (prompts && (isAdmin || prompts.slug !== "announcements")) {
        setCategoryId(prompts.id);
      }
    }
    setPostType(type);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!title.trim() || !content.trim()) {
      setError("// Error: Title and content are required");
      return;
    }
    if (title.trim().length > 150) {
      setError("// Error: Title must not exceed 150 characters");
      return;
    }
    if (!isAuthenticated) { navigate("/login"); return; }
    setSubmitting(true);
    setError("");
    try {
      const body: any = { title: title.trim(), categoryId, content };
      if (postType === "prompt") {
        body.postType = "prompt";
        const variables = variablesStr.split(",").map((v) => v.trim()).filter(Boolean);
        body.promptMetadata = JSON.stringify({ role: promptRole.trim(), recommendedModel: recommendedModel.trim(), temperature, variables });
      }
      const res = await apiClient.post("/posts", body);
      if (draftId) {
        const drafts = JSON.parse(localStorage.getItem(DRAFT_KEY) || "[]");
        localStorage.setItem(
          DRAFT_KEY,
          JSON.stringify(drafts.filter((d: any) => String(d.id) !== draftId))
        );
      }
      if (res.data.data.status === 2) {
        addToast("内容含敏感词，已提交审核", "success");
        navigate(isAdmin ? "/admin/audit" : "/", { replace: true });
      } else {
        addToast("Post published!", "success");
        navigate("/post/" + res.data.data.postId);
      }
    } catch (err: any) {
      setError("// Error: " + (err.response?.data?.message || "Failed to create post"));
      addToast('Failed to publish', 'error');
    } finally {
      setSubmitting(false);
    }
  };

  // Auto-dismiss error after 4s
  useEffect(() => {
    if (error) {
      const t = setTimeout(() => setError(""), 4000);
      return () => clearTimeout(t);
    }
  }, [error]);

  return (
    <div className="max-w-5xl mx-auto px-4 py-8">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-lg bg-vibe-cyan/20 border border-vibe-cyan/30 flex items-center justify-center">
            <Terminal className="w-4 h-4 text-vibe-cyan" />
          </div>
          <div>
            <h1 className="text-base font-semibold font-mono text-slate-100">Vibe Prompt Studio</h1>
            <p className="text-[11px] font-mono text-slate-500">Compose & publish your AI prompt / code snippet</p>
          </div>
        </div>
        <button
          type="button"
          onClick={handleAiPolish}
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-vibe-purple/20 border border-vibe-purple/30 text-vibe-purple text-xs font-mono hover:bg-vibe-purple/30 transition-colors"
          title="Insert AI prompt template"
        >
          <Sparkles className="w-3.5 h-3.5" />
          AI Polish Prompt
        </button>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        {/* TEMPLATE WIZARD */}
        <div className="rounded-xl border border-vibe-border overflow-hidden bg-vibe-surface/40">
          <div className="flex items-center justify-between px-3 py-2 bg-vibe-card/60 border-b border-vibe-border">
            <span className="text-[10px] font-mono uppercase tracking-widest text-slate-500">Template Wizard</span>
            <span className="text-[10px] font-mono text-slate-600">Select a mission to prefill the studio</span>
          </div>
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 p-2.5">
            {TEMPLATES.map((tpl) => {
              const Icon = tpl.icon;
              const active = template === tpl.id;
              return (
                <button
                  key={tpl.id}
                  type="button"
                  onClick={() => selectTemplate(tpl.id)}
                  className={
                    "flex items-center gap-2.5 rounded-lg border px-3 py-2.5 text-left transition-all active:scale-[0.97] " +
                    (active
                      ? "bg-vibe-cyan/15 border-vibe-cyan/40"
                      : "bg-vibe-card/40 border-vibe-border hover:border-vibe-cyan/30")
                  }
                >
                  <span
                    className={
                      "w-7 h-7 rounded-md border flex items-center justify-center shrink-0 " +
                      (active
                        ? "bg-vibe-cyan/20 border-vibe-cyan/40 text-vibe-cyan"
                        : "bg-vibe-surface border-vibe-border text-slate-500")
                    }
                  >
                    <Icon className="w-3.5 h-3.5" />
                  </span>
                  <span className="min-w-0">
                    <span className={"block text-[11px] font-mono font-semibold " + (active ? "text-vibe-cyan" : "text-slate-300")}>
                      {tpl.label}
                    </span>
                    <span className="block text-[10px] font-mono text-slate-600 truncate">{tpl.desc}</span>
                  </span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Post type toggle */}
        <div className="flex items-center gap-2">
          <button
            type="button"
            onClick={() => handlePostTypeChange("post")}
            className={"px-3 py-1.5 rounded-lg text-xs font-mono transition-colors " + (postType === "post" ? "bg-vibe-cyan/20 text-vibe-cyan border border-vibe-cyan/40" : "bg-vibe-card text-slate-400 border border-vibe-border")}
          >
            📝 Post
          </button>
          <button
            type="button"
            onClick={() => handlePostTypeChange("prompt")}
            className={"px-3 py-1.5 rounded-lg text-xs font-mono transition-colors " + (postType === "prompt" ? "bg-vibe-purple/20 text-vibe-purple border border-vibe-purple/40" : "bg-vibe-card text-slate-400 border border-vibe-border")}
          >
            🤖 Prompt Template
          </button>
        </div>

        {/* METADATA WINDOW */}
        <TerminalWindow title="config.json — Metadata">
          <div className="p-4 space-y-3">
            <div className="flex gap-3">
              <div className="flex-1">
                <input
                  type="text"
                  value={title}
                  onChange={(e) => setTitle(e.target.value)}
                  maxLength={150}
                  placeholder="# Post title..."
                  className="w-full px-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-sm font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
                />
              </div>
              <select
                value={categoryId ?? ""}
                onChange={(e) => setCategoryId(Number(e.target.value))}
                className="w-1/4 px-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-300 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
                disabled={channelsLoading}
              >
                {channelsLoading ? (
                  <option value="" className="bg-vibe-bg">Loading...</option>
                ) : (
                  displayChannels.map((ch: Channel) => (
                    <option key={ch.id} value={ch.id} className="bg-vibe-bg">
                      {ch.slug === "announcements" ? ch.name + " (Admin)" : ch.name}
                    </option>
                  ))
                )}
              </select>
            </div>
            {/* Tags */}
            <div className="relative">
              <Tag className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-slate-500" />
              <input
                type="text"
                value={tags}
                onChange={(e) => setTags(e.target.value)}
                placeholder="Tags (comma separated) — e.g. react, tailwind, animation"
                className="w-full pl-9 pr-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-300 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
              />
            </div>

            {/* Prompt-specific fields */}
            {postType === "prompt" && (
              <div className="border-t border-vibe-border pt-3 space-y-3">
                <h4 className="text-[10px] font-mono font-semibold text-slate-500 uppercase tracking-widest">Prompt Config</h4>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-mono text-slate-500">Role</label>
                    <textarea
                      value={promptRole}
                      onChange={(e) => setPromptRole(e.target.value)}
                      placeholder="System prompt role description..."
                      rows={2}
                      className="w-full px-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors resize-none"
                    />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-mono text-slate-500">Model</label>
                    <select
                      value={recommendedModel}
                      onChange={(e) => setRecommendedModel(e.target.value)}
                      className="w-full px-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-300 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
                    >
                      <option value="" className="bg-vibe-bg">Select model...</option>
                      <option value="gpt-4o" className="bg-vibe-bg">gpt-4o</option>
                      <option value="gpt-4o-mini" className="bg-vibe-bg">gpt-4o-mini</option>
                      <option value="claude-3.5-sonnet" className="bg-vibe-bg">claude-3.5-sonnet</option>
                      <option value="claude-3.5-haiku" className="bg-vibe-bg">claude-3.5-haiku</option>
                      <option value="claude-4-opus" className="bg-vibe-bg">claude-4-opus</option>
                      <option value="gemini-2.0-flash" className="bg-vibe-bg">gemini-2.0-flash</option>
                      <option value="deepseek-v3" className="bg-vibe-bg">deepseek-v3</option>
                    </select>
                  </div>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-mono text-slate-500">Temperature (0-1)</label>
                    <input
                      type="number"
                      min="0"
                      max="1"
                      step="0.1"
                      value={temperature}
                      onChange={(e) => setTemperature(Number(e.target.value))}
                      className="w-full px-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
                    />
                  </div>
                  <div className="space-y-1.5">
                    <label className="text-[10px] font-mono text-slate-500">Variables (comma-separated)</label>
                    <input
                      type="text"
                      value={variablesStr}
                      onChange={(e) => setVariablesStr(e.target.value)}
                      placeholder="e.g. language, framework, style"
                      className="w-full px-3 py-2 bg-vibe-bg border border-vibe-border rounded-lg text-xs font-mono text-slate-200 placeholder-slate-600 focus:outline-none focus:ring-1 focus:ring-vibe-cyan/50 focus:border-vibe-cyan/50 transition-colors"
                    />
                  </div>
                </div>
              </div>
            )}
          </div>
        </TerminalWindow>

        {/* ADMIN NOTICE */}
        {isAdmin && (
          <div className="text-[10px] font-mono text-vibe-cyan bg-vibe-cyan/10 border border-vibe-cyan/30 rounded-lg px-3 py-1.5">
            # Announcements channel — admin only
          </div>
        )}

        {/* EDITOR WINDOW */}
        <TerminalWindow title="prompt_editor.md — Markdown Editor">
          {/* Toolbar */}
          <div className="flex items-center gap-1 px-3 py-2 border-b border-vibe-border bg-vibe-card/50">
            {/* Left tabs */}
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setActiveTab("editor")}
                className={
                  "px-3 py-1 rounded-md text-[11px] font-mono transition-colors " +
                  (activeTab === "editor"
                    ? "bg-vibe-cyan/20 text-vibe-cyan"
                    : "text-slate-500 hover:text-slate-300")
                }
              >
                📝 Editor
              </button>
              <button
                type="button"
                onClick={() => setActiveTab("preview")}
                className={
                  "px-3 py-1 rounded-md text-[11px] font-mono transition-colors " +
                  (activeTab === "preview"
                    ? "bg-vibe-cyan/20 text-vibe-cyan"
                    : "text-slate-500 hover:text-slate-300")
                }
              >
                👁️ Preview
              </button>
            </div>
            {/* Right status */}
            <div className="ml-auto flex items-center gap-3 text-[10px] font-mono text-slate-600">
              <span>~{tokens} Tokens</span>
              <span className="hidden sm:inline">Markdown / Code Supported</span>
            </div>
          </div>

          {/* Editor body */}
          {activeTab === "editor" ? (
            <div className="relative">
              <textarea
                ref={textareaRef}
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder="// Write your prompt or code in Markdown..."
                className="w-full h-80 p-4 bg-vibe-bg text-slate-200 font-mono text-sm leading-relaxed resize-none focus:outline-none border-0 placeholder-slate-700"
              />
              {/* Code block FAB */}
              <button
                type="button"
                onClick={insertCodeBlock}
                className="absolute bottom-3 right-3 inline-flex items-center gap-1 px-2.5 py-1.5 rounded-md bg-vibe-card border border-vibe-border text-[10px] font-mono text-slate-400 hover:text-vibe-cyan hover:border-vibe-cyan/40 transition-colors"
                title="Insert code block"
              >
                <Code2 className="w-3 h-3" />
                Code
              </button>
            </div>
          ) : (
            <div className="h-80 overflow-y-auto p-4 bg-vibe-bg prose prose-invert prose-sm max-w-none">
              {content ? (
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
              ) : (
                <p className="text-slate-700 italic font-mono text-xs">// Preview will appear here...</p>
              )}
            </div>
          )}

          {/* Footer Dock */}
          <div className="flex items-center justify-between px-3 py-2.5 border-t border-vibe-border bg-vibe-card/50">
            {/* Error toast (inline) */}
            {error && (
              <span className="text-[11px] font-mono text-red-400 animate-pulse">{error}</span>
            )}
            {!error && <span />}
            {/* Actions */}
            <div className="flex items-center gap-2 ml-auto">
              <button
                type="button"
                onClick={handleSaveDraft}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg border border-vibe-border text-[11px] font-mono text-slate-400 hover:text-slate-200 hover:border-slate-500 transition-colors"
              >
                <Save className="w-3.5 h-3.5" />
                Save Draft
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="relative overflow-hidden rounded-lg bg-gradient-to-r from-vibe-cyan to-vibe-purple p-[1px] font-mono text-xs font-medium text-white transition-transform active:scale-95 hover:scale-[1.02] disabled:opacity-50 disabled:cursor-not-allowed"
              >
                <span className="absolute inset-0 bg-[linear-gradient(110deg,transparent,25%,rgba(255,255,255,0.3),45%,transparent)] bg-[length:200%_100%] animate-shimmer" />
                <span className="relative flex items-center gap-1.5 rounded-[7px] bg-vibe-bg/90 px-4 py-1.5 backdrop-blur-sm hover:bg-transparent transition-colors">
                  <Send className="w-3.5 h-3.5" />
                  {submitting ? "Publishing..." : "Publish Vibe Post"}
                </span>
              </button>
            </div>
          </div>
        </TerminalWindow>
      </form>
    </div>
  );
}

