# 0006 — DeepSeek 官方 API 作为生产评审模型（本地 Ollama 回退）

日期：2026-09-07 ｜ 状态：Accepted ｜ 决策材料：[docs/research/llm-model-upgrade-options.md](../research/llm-model-upgrade-options.md)

## 背景

AI 评审/安全分类的质量天花板由模型决定。本地 Ollama qwen2.5:7b 在单机 16GB 环境下
输出泛泛（README 已声明"生产建议托管模型或 ≥14B"），且成为 AI 拓展功能的前置瓶颈。
2026-09-07 的选型调研给出五维对比（质量代理指标、结构化输出支持、成本、延迟、合规可达性），
随后用 NVIDIA NIM 免费节点对真实竞态缺陷帖做了端到端实测：托管模型
（nemotron-3-super 3/10 high、deepseek-v4-pro 4/10 high，后者中文原生）显著强于本地 7B 叙事基线。

## 决策

1. **生产主档：DeepSeek 官方 API（`deepseek-chat`）**，通过 OpenAI 兼容端点接入，
   `.env` 三变量切换：`LLM_ENDPOINT=https://api.deepseek.com`、`LLM_MODEL=deepseek-chat`、`LLM_API_KEY=...`。
2. **结构化模式：`response-format=json_object`**。DeepSeek 官方仅支持 json_object（不支持 json_schema）；
   字段结构由评审 prompt + 既有 repair-parse/自纠错链路承载（系统提示已含 "JSON object" 要求，满足
   DeepSeek "prompt 必须含 json 一词" 的硬性条件；安全检查 prompt 已补 "as JSON"）。
   配置项 `campus.ai.llm.response-format`（json_schema｜json_object｜none）为此引入，默认 json_schema 保持
   OpenAI/NIM/DashScope 行为不变。
3. **回退链**：DeepSeek 不可用时 → 本地 Ollama（`.env` 切回），评审自动 FAILED + 对账重试兜底；
   NVIDIA NIM 保留为免费评测/第二回退（已修 Accept 头，1c59b43）。

## 否决的备选

- **DashScope qwen-plus**：唯一原生 json_schema 的境内选项，质量/合规均优；败在 DeepSeek V4 的
  代码能力代理指标更强且价格同量级。若 DeepSeek 稳定性或合规出现问题时，这是第一顺位替补
  （`.env` 即切，零代码）。
- **OpenAI/Gemini**：境内不可达（OpenAI 2024-07 起切断大陆 API；Gemini 2.0 Flash 已宣布弃期）。
- **维持本地 7B**：质量代差已用实测数据确认。

## 后果

- 正向：中文评审质量显著提升（实测对照）；境内直连、数据不出境；月成本 ≈ $14 内（峰值价，
  缓存命中/非高峰更低）；`max-context-tokens` 可上调（128k 上下文）。
- 代价：评审结构稳定性从"grammar 级保证"退到"prompt+repair-parse 承载"——既有
  JsonRepairUtil/自纠错/语义校验三层兜底正是为此设计；断网时 fail-closed 链路（ADR-0004）不变。
- 中性：托管延迟 2-10s，现 lease-seconds=240/timeout 30s 无需放宽；抽样确认稳定后可收紧
  lease 至 60-90s（观察项，非本 ADR 范围）。
- 运维：API key 进 `.env`（gitignored）；用量与余额监控为后续增强。

## 验证路径

`.env` 切换后：236 测试全绿（无需改动）→ 复用 2026-09-07 的并发竞态测试帖对照评审输出 →
抽样确认稳定后评估收紧 lease。
