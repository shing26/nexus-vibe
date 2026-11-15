import { useState, useRef, useCallback, useMemo, useEffect } from "react";
import { useNavigate, useSearchParams } from "react-router-dom";
import { useTranslation } from "react-i18next";
import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { apiClient } from "../api/client";
import { useAuthStore } from "../stores/authStore";
import { useToastStore } from "../stores/toastStore";
import { useChannels, type Channel } from "../api/useChannels";
import { withWorkedExample } from "../components/promptMetadata";
import { channelLabel } from "../lib/channels";
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

const DRAFT_KEY = "nexus_vibe_drafts";

const fieldClass =
  "w-full rounded-md border border-vibe-border bg-vibe-bg px-3 py-2 text-sm text-vibe-text placeholder-vibe-faint transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50";
const smallFieldClass =
  "w-full rounded-md border border-vibe-border bg-vibe-bg px-3 py-2 text-xs text-vibe-text placeholder-vibe-faint transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50";
const labelClass = "block text-xs text-vibe-muted";

export default function CreatePostPage() {
  const { t } = useTranslation("editor");
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
  // Optional worked example (item 5): two keys inside the same promptMetadata
  // JSON, so the column and the API stay unchanged.
  const [exampleInput, setExampleInput] = useState("");
  const [exampleOutput, setExampleOutput] = useState("");
  const [template, setTemplate] = useState<string>(searchParams.get("template") || "");

  // Unsaved-changes guard: content is dirty once it drifts from the last
  // restored/templated baseline; beforeunload warns on reload/close.
  const baseline = useRef({ title: "", content: "" });
  const dirty = title !== baseline.current.title || content !== baseline.current.content;
  useEffect(() => {
    if (!dirty) return;
    const handler = (e: BeforeUnloadEvent) => {
      e.preventDefault();
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);

  // Apply template scaffold once channels are available
  useEffect(() => {
    if (draftId || !channels || !template) return;
    const templates = ["debug", "prompt", "showcase", "agents"].map((id) => ({
      id,
      channelSlug: { debug: "debug", prompt: "prompts", showcase: "showcase", agents: "agents" }[id]!,
      postType: (id === "prompt" ? "prompt" : "post") as "post" | "prompt",
    }));
    const tpl = templates.find((t) => t.id === template);
    if (!tpl) return;
    const ch = channels.find((c: Channel) => c.slug === tpl.channelSlug);
    if (ch) setCategoryId(ch.id);
    setPostType(tpl.postType);
    const scaffold = t("templates." + template + ".scaffold");
    setContent(scaffold);
    setTitle("");
    setPromptRole(template === "prompt" ? "Expert coding assistant" : "");
    setRecommendedModel("");
    setTemperature(0.7);
    setVariablesStr("");
    setExampleInput("");
    setExampleOutput("");
    setActiveTab("editor");
    baseline.current = { title: "", content: scaffold };
  }, [template, channels, draftId, t]);

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
      setExampleInput(draft.exampleInput || "");
      setExampleOutput(draft.exampleOutput || "");
      baseline.current = { title: draft.title || "", content: draft.content || "" };
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
      insertion = "```\n" + selected + "\n```";
      cursorOffset = start + insertion.length;
    } else {
      insertion = "```\n\n```";
      cursorOffset = start + 4;
    }
    setContent(before + insertion + after);
    requestAnimationFrame(() => {
      textarea.focus();
      textarea.setSelectionRange(cursorOffset, cursorOffset);
    });
  }, [content]);

  const handleAiPolish = () => {
    setContent((prev) => (prev.trim() ? prev + "\n\n" + t("promptScaffold") : t("promptScaffold")));
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
      exampleInput,
      exampleOutput,
      updatedAt: new Date().toISOString(),
    };
    const index = drafts.findIndex((d: any) => String(d.id) === String(payload.id));
    if (index >= 0) {
      drafts[index] = payload;
    } else {
      drafts.push(payload);
    }
    localStorage.setItem(DRAFT_KEY, JSON.stringify(drafts));
    addToast(t("draftSaved"), "success");
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
      setError(t("errors.titleAndContent"));
      return;
    }
    if (title.trim().length > 150) {
      setError(t("errors.titleTooLong"));
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
        body.promptMetadata = JSON.stringify(
          withWorkedExample(
            { role: promptRole.trim(), recommendedModel: recommendedModel.trim(), temperature, variables },
            exampleInput,
            exampleOutput,
          ),
        );
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
        // The post was held for moderation, not published — success-colored
        // relief for a warning event read as "it went through fine".
        addToast(t("heldForReview"), "info");
        navigate(isAdmin ? "/admin/audit" : "/", { replace: true });
      } else {
        addToast(t("published"), "success");
        navigate("/post/" + res.data.data.postId);
      }
    } catch (err: any) {
      setError(t("errorPrefix") + " " + (err.response?.data?.message || t("errors.failedToPublish")));
      addToast(t("errors.failedToPublish"), "error");
    } finally {
      setSubmitting(false);
    }
  };

  // Errors persist until the next submit clears them — an auto-dismissing
  // validation message disappears before the user can read and act on it.

  const TEMPLATES = [
    { id: "debug", icon: Bug },
    { id: "prompt", icon: Terminal },
    { id: "showcase", icon: Palette },
    { id: "agents", icon: Cpu },
  ];

  return (
    <div className="mx-auto max-w-5xl px-4 py-8">
      {/* Header */}
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <div className="flex h-9 w-9 items-center justify-center rounded-md border border-vibe-border bg-vibe-surface text-vibe-accent">
            <Terminal className="h-4 w-4" />
          </div>
          <div>
            <h1 className="text-lg font-semibold text-vibe-text">{t("studioTitle")}</h1>
            <p className="text-xs text-vibe-muted">{t("studioSubtitle")}</p>
          </div>
        </div>
        <button
          type="button"
          onClick={handleAiPolish}
          className="inline-flex items-center gap-1.5 rounded-md border border-vibe-border bg-vibe-card px-3 py-1.5 text-xs text-vibe-text transition-colors hover:border-vibe-accent/40 hover:text-vibe-accent"
          title={t("insertScaffoldTitle")}
        >
          <Sparkles className="h-3.5 w-3.5" />
          {t("insertScaffold")}
        </button>
      </div>

      <form onSubmit={handleSubmit} className="space-y-4">
        {/* TEMPLATE WIZARD */}
        <div className="overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
          <div className="flex items-center justify-between border-b border-vibe-border bg-vibe-card/60 px-3 py-2">
            <span className="text-xs font-semibold uppercase tracking-wide text-vibe-muted">{t("templateWizard")}</span>
            <span className="text-xs text-vibe-faint">{t("templateWizardHint")}</span>
          </div>
          <div className="grid grid-cols-2 gap-2 p-2.5 sm:grid-cols-4">
            {TEMPLATES.map((tpl) => {
              const Icon = tpl.icon;
              const active = template === tpl.id;
              return (
                <button
                  key={tpl.id}
                  type="button"
                  onClick={() => selectTemplate(tpl.id)}
                  className={
                    "flex items-center gap-2.5 rounded-md border px-3 py-2.5 text-left transition-colors " +
                    (active
                      ? "border-vibe-accent/50 bg-vibe-accent/10"
                      : "border-vibe-border bg-vibe-card hover:border-vibe-accent/30")
                  }
                >
                  <span
                    className={
                      "flex h-7 w-7 shrink-0 items-center justify-center rounded-md border " +
                      (active
                        ? "border-vibe-accent/40 bg-vibe-accent/15 text-vibe-accent"
                        : "border-vibe-border bg-vibe-bg text-vibe-muted")
                    }
                  >
                    <Icon className="h-3.5 w-3.5" />
                  </span>
                  <span className="min-w-0">
                    <span className={"block text-xs font-medium " + (active ? "text-vibe-accent" : "text-vibe-text")}>
                      {t("templates." + tpl.id + ".label")}
                    </span>
                    <span className="block truncate text-xs text-vibe-faint">{t("templates." + tpl.id + ".desc")}</span>
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
            aria-pressed={postType === "post"}
            className={"rounded-full border px-3 py-1.5 text-xs transition-colors " + (postType === "post" ? "border-vibe-accent/50 bg-vibe-accent/10 text-vibe-accent" : "border-vibe-border bg-vibe-card text-vibe-muted")}
          >
            {t("postType.post")}
          </button>
          <button
            type="button"
            onClick={() => handlePostTypeChange("prompt")}
            aria-pressed={postType === "prompt"}
            className={"rounded-full border px-3 py-1.5 text-xs transition-colors " + (postType === "prompt" ? "border-vibe-accent/50 bg-vibe-accent/10 text-vibe-accent" : "border-vibe-border bg-vibe-card text-vibe-muted")}
          >
            {t("postType.prompt")}
          </button>
        </div>

        {/* METADATA */}
        <div className="space-y-3 rounded-lg border border-vibe-border bg-vibe-surface p-4">
          <span className="block text-xs text-vibe-faint">{t("metadataTitle")}</span>
          <div className="flex gap-3">
            <div className="flex-1">
              <input
                type="text"
                value={title}
                onChange={(e) => setTitle(e.target.value)}
                maxLength={150}
                placeholder={t("titlePlaceholder")}
                aria-label={t("titleAria")}
                className={fieldClass}
              />
            </div>
            <select
              value={categoryId ?? ""}
              onChange={(e) => setCategoryId(Number(e.target.value))}
              aria-label={t("channelAria")}
              className="w-1/4 rounded-md border border-vibe-border bg-vibe-bg px-3 py-2 text-sm text-vibe-text transition-colors focus:border-vibe-accent/50 focus:outline-none focus:ring-1 focus:ring-vibe-accent/50"
              disabled={channelsLoading}
            >
              {channelsLoading ? (
                <option value="" className="bg-vibe-bg">{t("loadingChannels")}</option>
              ) : (
                displayChannels.map((ch: Channel) => (
                  <option key={ch.id} value={ch.id} className="bg-vibe-bg">
                    {channelLabel(ch.slug) || ch.name}
                    {ch.slug === "announcements" ? " (" + t("adminChannelSuffix") + ")" : ""}
                  </option>
                ))
              )}
            </select>
          </div>
          {/* Tags */}
          <div className="relative">
            <Tag className="absolute left-3 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-vibe-muted" />
            <input
              type="text"
              value={tags}
              onChange={(e) => setTags(e.target.value)}
              placeholder={t("tagsPlaceholder")}
              className={fieldClass + " pl-9 text-xs"}
            />
          </div>

          {/* Prompt-specific fields */}
          {postType === "prompt" && (
            <div className="space-y-3 border-t border-vibe-border pt-3">
              <h4 className="text-xs font-semibold uppercase tracking-wide text-vibe-muted">{t("promptConfig.heading")}</h4>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <label className={labelClass}>{t("promptConfig.role")}</label>
                  <textarea
                    value={promptRole}
                    onChange={(e) => setPromptRole(e.target.value)}
                    placeholder={t("promptConfig.rolePlaceholder")}
                    rows={2}
                    className={smallFieldClass + " resize-none"}
                  />
                </div>
                <div className="space-y-1.5">
                  <label className={labelClass}>{t("promptConfig.model")}</label>
                  <select
                    value={recommendedModel}
                    onChange={(e) => setRecommendedModel(e.target.value)}
                    className={smallFieldClass}
                  >
                    <option value="" className="bg-vibe-bg">{t("promptConfig.modelPlaceholder")}</option>
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
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <label className={labelClass}>{t("promptConfig.temperature")}</label>
                  <input
                    type="number"
                    min="0"
                    max="1"
                    step="0.1"
                    value={temperature}
                    onChange={(e) => setTemperature(Number(e.target.value))}
                    className={smallFieldClass}
                  />
                </div>
                <div className="space-y-1.5">
                  <label className={labelClass}>{t("promptConfig.variables")}</label>
                  <input
                    type="text"
                    value={variablesStr}
                    onChange={(e) => setVariablesStr(e.target.value)}
                    placeholder={t("promptConfig.variablesPlaceholder")}
                    className={smallFieldClass}
                  />
                </div>
              </div>
              <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <label className={labelClass}>{t("promptConfig.exampleInput")}</label>
                  <textarea
                    value={exampleInput}
                    onChange={(e) => setExampleInput(e.target.value)}
                    placeholder={t("promptConfig.exampleInputPlaceholder")}
                    rows={3}
                    className={smallFieldClass + " resize-none"}
                  />
                </div>
                <div className="space-y-1.5">
                  <label className={labelClass}>{t("promptConfig.exampleOutput")}</label>
                  <textarea
                    value={exampleOutput}
                    onChange={(e) => setExampleOutput(e.target.value)}
                    placeholder={t("promptConfig.exampleOutputPlaceholder")}
                    rows={3}
                    className={smallFieldClass + " resize-none"}
                  />
                </div>
              </div>
            </div>
          )}
        </div>

        {/* ADMIN NOTICE */}
        {isAdmin && (
          <div className="rounded-md border border-vibe-accent/30 bg-vibe-accent/10 px-3 py-1.5 text-xs text-vibe-accent">
            {t("adminNotice")}
          </div>
        )}

        {/* EDITOR */}
        <div className="overflow-hidden rounded-lg border border-vibe-border bg-vibe-surface">
          {/* Toolbar */}
          <div className="flex items-center gap-1 border-b border-vibe-border bg-vibe-card/50 px-3 py-2">
            {/* Left tabs */}
            <div className="flex items-center gap-1">
              <button
                type="button"
                onClick={() => setActiveTab("editor")}
                aria-pressed={activeTab === "editor"}
                className={
                  "rounded-md px-3 py-1 text-xs transition-colors " +
                  (activeTab === "editor"
                    ? "bg-vibe-accent/15 text-vibe-accent"
                    : "text-vibe-muted hover:text-vibe-text")
                }
              >
                {t("editorTabs.editor")}
              </button>
              <button
                type="button"
                onClick={() => setActiveTab("preview")}
                aria-pressed={activeTab === "preview"}
                className={
                  "rounded-md px-3 py-1 text-xs transition-colors " +
                  (activeTab === "preview"
                    ? "bg-vibe-accent/15 text-vibe-accent"
                    : "text-vibe-muted hover:text-vibe-text")
                }
              >
                {t("editorTabs.preview")}
              </button>
            </div>
            {/* Right status */}
            <div className="ml-auto flex items-center gap-3 text-xs tabular-nums text-vibe-faint">
              <span>{t("tokensLabel", { n: tokens })}</span>
              <span className="hidden sm:inline">{t("markdownNote")}</span>
            </div>
          </div>

          {/* Editor body */}
          {activeTab === "editor" ? (
            <div className="relative">
              <textarea
                ref={textareaRef}
                value={content}
                onChange={(e) => setContent(e.target.value)}
                placeholder={t("contentPlaceholder")}
                className="h-80 w-full resize-none border-0 bg-vibe-bg p-4 font-mono text-sm leading-relaxed text-vibe-text placeholder-vibe-faint focus:outline-none"
              />
              {/* Code block FAB */}
              <button
                type="button"
                onClick={insertCodeBlock}
                className="absolute bottom-3 right-3 inline-flex items-center gap-1 rounded-md border border-vibe-border bg-vibe-card px-2.5 py-1.5 text-xs text-vibe-muted transition-colors hover:border-vibe-accent/40 hover:text-vibe-accent"
                title={t("insertCodeTitle")}
              >
                <Code2 className="h-3 w-3" />
                {t("codeButton")}
              </button>
            </div>
          ) : (
            <div className="h-80 overflow-y-auto bg-vibe-bg p-4 prose prose-sm max-w-none dark:prose-invert">
              {content ? (
                <ReactMarkdown remarkPlugins={[remarkGfm]}>{content}</ReactMarkdown>
              ) : (
                <p className="text-xs italic text-vibe-faint">{t("previewPlaceholder")}</p>
              )}
            </div>
          )}

          {/* Footer Dock */}
          <div className="flex items-center justify-between border-t border-vibe-border bg-vibe-card/50 px-3 py-2.5">
            {/* Error toast (inline) */}
            {error && (
              <span role="alert" className="text-xs text-vibe-danger">{error}</span>
            )}
            {!error && <span />}
            {/* Actions */}
            <div className="ml-auto flex items-center gap-2">
              <button
                type="button"
                onClick={handleSaveDraft}
                className="inline-flex items-center gap-1.5 rounded-md border border-vibe-border bg-vibe-card px-3 py-1.5 text-xs text-vibe-muted transition-colors hover:border-vibe-accent/40 hover:text-vibe-accent"
              >
                <Save className="h-3.5 w-3.5" />
                {t("saveDraft")}
              </button>
              <button
                type="submit"
                disabled={submitting}
                className="inline-flex items-center gap-1.5 rounded-md bg-vibe-accent px-4 py-1.5 text-xs font-medium text-white transition-colors hover:opacity-90 disabled:cursor-not-allowed disabled:opacity-50"
              >
                <Send className="h-3.5 w-3.5" />
                {submitting ? t("publishing") : t("publish")}
              </button>
            </div>
          </div>
        </div>
      </form>
    </div>
  );
}
