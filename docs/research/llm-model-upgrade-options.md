# 模型升级选型调研：本地 7B → 托管 API？

> 调研日期：2026-09-07。数据来源以官方文档/定价页为主（标注"截至调研日"）；无法直接访问的页面经官方页搜索快照交叉核对并标注。
> 背景：Nexus-Vibe 的 AI 代码评审与安全分类目前跑在本机 Ollama（生产叙事 qwen2.5:7b，本机容器实测只有 qwen2.5:3b），
> 输出质量是质量敏感 AI 功能的天花板（README 已声明"生产建议托管模型或 ≥14B"）。本文是 ADR-0006 的决策材料。

## 结论先行

**三档推荐：**

| 档位 | 选择 | 一句话理由 |
|---|---|---|
| 保守 | 维持本地 qwen2.5:7b | 零成本、隐私最优、零改动；接受质量天花板 |
| **平衡（推荐）** | **阿里云百炼 DashScope compatible-mode（qwen-plus 档）** | **唯一同时满足：原生 json_schema、境内直连、OpenAI 兼容零代码迁移、中文内容同源模型** |
| 激进 | DeepSeek V4（或 gpt-4o-mini，若境外可达） | 质量上限更高；DeepSeek 仅支持 json_object，需依赖既有 repair-parse 兜底 |

**如果只能选一个：选 DashScope qwen-plus。** 本项目负载下的月成本各家都在几美元量级（见成本估算），
成本不是决策因子；真正拉开差距的是三点——① 原生 `json_schema` 结构化输出（省掉降级-修复链路的不确定性）；
② 中国大陆直连（OpenAI 已于 2024-07 切断大陆 API 访问）；③ 迁移成本为零（改 `.env` 三个变量即可，见迁移评估）。
质量上 DeepSeek V4 是本题里最强的非旗舰选项，但其结构化输出只有 json_object 模式，
而本项目的评分/枚举字段恰恰最依赖结构稳定性——这一票投给 Qwen。

## 一、代码质量基准（代理指标）

> 注意：没有"代码评审"的权威公开 benchmark，下列 polyglot 数据衡量的是代码编辑/生成能力，
> 只能作为模型代码能力相对强弱的**代理指标**；评审质量的最终判据应是接入后的抽样对比实验（见"下一步"）。

| 模型 | Aider polyglot（225 题，% correct） | 编辑格式合规 | 来源 |
|---|---|---|---|
| gpt-4o-mini (2024-07-18) | ~28-32% | 100% | [Aider leaderboards](https://aider.chat/docs/leaderboards/) |
| DeepSeek V3 (0324) | 显著高于 4o-mini（R1 为 56.9%，V3 chat 曾是榜首性价比） | 99.6% | 同上 |
| Qwen2.5-Coder-32B-Instruct | 16.4% | 99.6% | 同上 |
| qwen2.5:7b / 3b（本地） | 未上榜（7B 级远低于上述模型） | — | 推断，无一手数据 |

要点：**gpt-4o-mini 的"格式可靠"不等于"质量高"**（32% correct）；DeepSeek V3 的代码能力在中等价位里最强；
本地 7B 与上述所有模型存在代差——这正是升级的动机。

## 二、结构化输出支持矩阵（本项目最关键维度）

本项目 `LlmClient` 先发 `response_format: json_schema`，失败降级普通 completion 再 repair-parse + 自纠错。
各家支持情况（截至调研日，逐家核对官方文档）：

| 提供方 | json_schema 原生支持 | 降级路径 | 已知坑 | 官方来源 |
|---|---|---|---|---|
| OpenAI gpt-4o-mini | ✅ `response_format={"type":"json_schema", strict:true}`（2024-08 起原生，grammar 级保证） | — | strict 模式对 schema 有约束（allOf/部分关键字不支持） | [官方公告](https://openai.com/index/introducing-structured-outputs-in-the-api/) |
| DeepSeek V4 | ❌ 仅 `json_object`，**不支持 json_schema** | prompt 给 JSON 示例 + 自行解析 | 官方承认"偶发空内容"；需设 max_tokens 防截断 | [JSON Mode 文档](https://api-docs.deepseek.com/guides/json_mode) |
| Gemini 2.0/2.5 Flash | ✅ 原生 `responseSchema`（非 OpenAI 协议；另有 OpenAI 兼容层） | — | 兼容层覆盖度需实测；**2.0 Flash 已宣布弃用（2026-06）**，应选 2.5 | [Structured output 文档](https://ai.google.dev/gemini-api/docs/structured-output)、[定价](https://ai.google.dev/gemini-api/docs/pricing)（本机网络未能直连，经搜索快照核对） |
| Qwen（DashScope compatible-mode） | ✅ 原生 `response_format={"type":"json_schema"}`，官方明示"精确控制结构与类型，无需额外验证或重试" | json_object | compatible-mode 仅暴露 /chat/completions | [结构化输出文档](https://help.aliyun.com/zh/model-studio/qwen-structured-output)、[OpenAI 兼容说明](https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope) |
| 本地 Ollama | ✅ `format` 参数传 JSON schema（grammar 约束采样） | — | 采样无 GPU 加速、官方自述"性能与准确度仍需改进"——小模型上 schema 合规但**内容质量**差 | [Structured outputs 博客](https://ollama.com/blog/structured-outputs) |

## 三、成本估算（均含假设，截至调研日）

**负载假设**：每天 200 次评审（含编辑重审），每次输入 ~3k tokens（系统提示+代码块）、输出 ~800 tokens
→ 月用量 ≈ 18M 输入 + 4.8M 输出。

| 提供方 | 输入价/1M | 输出价/1M | 月成本估算 | 来源 |
|---|---|---|---|---|
| gpt-4o-mini | $0.15 | $0.60 | **≈ $5.6** | [官方公告](https://openai.com/index/gpt-4o-mini-advancing-cost-efficient-intelligence/) |
| DeepSeek v4-flash（高峰价） | $0.44 | $1.32 | **≈ $14.3**（非高峰减半；缓存命中低至 $0.014） | [官方定价页](https://api-docs.deepseek.com/quick_start/pricing/)（2026-09-07 实测抓取） |
| Gemini 2.0 Flash | $0.075 | $0.30 | **≈ $2.8**（第三方一致口径，官方页未直连成功） | [定价页](https://ai.google.dev/gemini-api/docs/pricing) |
| qwen-plus（百炼，促销价） | ¥0.8 | ¥2.0 | **≈ ¥24（≈ $3.4）** | [模型定价](https://help.aliyun.com/en/model-studio/model-pricing) |
| 本地 qwen2.5:7b | 电费 | 电费 | ≈ 0 | — |

**结论：全档位月成本都低于一杯咖啡，成本不构成决策因子**——真正要防的是"为了省钱留在质量天花板下面"。

## 四、延迟与管线参数影响

| 项 | 本地 7B（16GB 单机） | 托管 API |
|---|---|---|
| 单次评审耗时 | 30-90s 量级（CPU/共享 GPU，长上下文更慢） | 2-10s 量级 |
| 现参数适配 | lease-seconds=240 正好为本机最坏耗时而设（ADR-0005 修订） | **无需放宽，反而可收紧**：timeout 30s、重试 3 次、熔断 3 次/60s 全部沿用 |
| max-context-tokens | 12000（8k-32k 模型约束） | 可上调（各家 ≥128k 上下文），但需重估 token 预算注释 |

托管化后**没有任何管线参数需要放宽**；若要精细化，lease 可降到 60-90s、并发能力显著提升
（agentLlmExecutor 队列 50 的饱和概率大幅下降，`publishReviewEventSafely` 的 FAILED 降级路径更少触发）。

## 五、隐私与合规（大陆部署视角）

- **OpenAI：不可行于境内**。官方支持国家列表不含中国大陆，且 2024-07-09 起对大陆开发者**主动切断 API**（[官方列表](https://developers.openai.com/api/docs/supported-countries)、[Reuters 报道](https://www.reuters.com/technology/artificial-intelligence/openai-cut-access-tools-developers-china-other-regions-chinese-state-media-says-2024-06-25/)）。
- **Gemini：境内同样不可直连**（Google 服务在大陆不可达；且 2.0 Flash 已宣布弃用）。
- **DeepSeek / Qwen 百炼：境内直连**，数据不出境；本地 Ollama 隐私最优但质量垫底。
- 作品集语境的权衡：校园/开发者社区帖子的内容敏感度低，但"数据出境"仍是答辩时的必问项——**境内托管 + 可切换本地**是最稳的故事线。

## 六、迁移工作量评估：零代码改动

`LlmClient` 面向 OpenAI-compatible Chat Completions 编写，三家候选都提供兼容端点，**只改 `.env`**：

```bash
# DashScope（推荐档）
LLM_ENDPOINT=https://dashscope-intl.aliyuncs.com/compatible-mode/v1   # 或境内 https://dashscope.aliyuncs.com/compatible-mode/v1
LLM_MODEL=qwen-plus
LLM_API_KEY=sk-***

# DeepSeek（激进档）
LLM_ENDPOINT=https://api.deepseek.com
LLM_MODEL=deepseek-chat
```

注意点：
1. DashScope compatible-mode 仅 `/chat/completions`（[官方说明](https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope)）——本项目只用该端点 ✅。
2. DeepSeek 走 json_object 时**系统提示必须包含 "json" 一词并给示例**（[官方要求](https://api-docs.deepseek.com/guides/json_mode)）——
   本项目系统提示已含 "Output your analysis as a JSON object matching the provided schema"，天然满足；
   json_schema 请求发出后若报错，`LlmClient` 的既有降级链路（json_schema → 普通 completion → repair-parse → 自纠错）正好兜底，无需改代码。
3. Gemini 原生协议不兼容，需走其 OpenAI 兼容层（覆盖度未实测），且 2.0 Flash 弃用期临近——除非专程要 Google 系，否则不选。

## 七、下一步（决策落地路径）

1. 用户拍板档位 → 写 **ADR-0006**（模型选型 + 可切换本地/托管的回退策略）。
2. 按第 6 节改 `.env`，重跑 236 测试（无需改测试）+ 人工抽样 10 篇帖子对比新旧评审质量（把"代理指标"换成真证据）。
3. 视实测决定是否收紧 lease-seconds 与上调 max-context-tokens。

## 来源清单

- OpenAI：[gpt-4o-mini 定价公告](https://openai.com/index/gpt-4o-mini-advancing-cost-efficient-intelligence/) · [Structured Outputs 公告](https://openai.com/index/introducing-structured-outputs-in-the-api/) · [支持国家列表](https://developers.openai.com/api/docs/supported-countries) · [API 定价页](https://developers.openai.com/api/docs/pricing)
- DeepSeek：[Models & Pricing](https://api-docs.deepseek.com/quick_start/pricing/)（2026-09-07 抓取，V4 系列）· [JSON Mode](https://api-docs.deepseek.com/guides/json_mode)
- Gemini：[定价](https://ai.google.dev/gemini-api/docs/pricing) · [Structured output](https://ai.google.dev/gemini-api/docs/structured-output)（未直连，经搜索快照核对；2.0 Flash 弃用信息来自第三方口径，需在采用前复核）
- Qwen：[结构化输出](https://help.aliyun.com/zh/model-studio/qwen-structured-output) · [OpenAI 兼容](https://www.alibabacloud.com/help/en/model-studio/compatibility-of-openai-with-dashscope) · [模型定价](https://help.aliyun.com/en/model-studio/model-pricing)
- Ollama：[Structured outputs](https://ollama.com/blog/structured-outputs)
- 基准：[Aider polyglot leaderboard](https://aider.chat/docs/leaderboards/)（含 [原始 YAML](https://github.com/Aider-AI/aider/blob/main/aider/website/_data/polyglot_leaderboard.yml)）
- 大陆可达性：[Reuters：OpenAI 切断大陆 API](https://www.reuters.com/technology/artificial-intelligence/openai-cut-access-tools-developers-china-other-regions-chinese-state-media-says-2024-06-25/)
