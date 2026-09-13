# Nexus-Vibe 部署前优化清单

> 日期：2026-08-15
> 状态：全部实施完成；部署仍未执行，不 push。
> 基线：优化前 `mvn test` 167 个全绿；体验报告修复后 188 个全绿，`npm run build` 与 `npm run lint` 通过。

## 安全

- [x] 上传接口改为扩展名白名单 + 魔数嗅探，文件名只使用 `UUID.扩展名`，杜绝路径穿越与伪造 Content-Type。
- [x] CORS 从通配 `*` 改为配置注入，prod 只允许线上域名。
- [x] 限流优先信任 nginx 的 `X-Real-IP`，XFF 只取最右段；登录/注册也纳入限流。
- [x] JWT 过滤器细化公开路由：用户资料更新/改密必须带 token，agent 日志列表与统计仅管理员可见。
- [x] 新增公开 `agent-logs/ticker` 轻量接口，首页页脚不再调用管理员接口。
- [x] Demo 展示控制器加 `@Profile("!prod")`，prod 关闭 springdoc/Swagger，收紧 actuator 详情。
- [x] 生产环境 demo 账号改为显式开关（默认关闭），不再由 `init.sql` 写入已知密码。
- [x] 日志切面脱敏：不再把登录请求参数与 JWT 响应体打进日志。
- [x] 消息已读接口增加收件人归属校验；系统消息类型不再被错误映射成评论。
- [x] LLM 客户端在 API Key 为空时不发送空 `Authorization` 头。
- [x] 全局异常处理器补齐参数缺失/类型不匹配/请求体不可读/上传超限等 4xx 映射。
- [x] nginx 增加 CSP/HSTS/上传代理/上传体积限制/隐藏版本号/拒绝点文件。
- [x] nginx CSP 的 `font-src` 增加 `data:`，允许前端内置 data URI 字体（浏览器验证曾出现 13 条字体拦截报错）。

## 部署配置

- [x] Dockerfile 统一 `SPRING_PROFILES_ACTIVE=prod`、版本标签与可用的健康检查。
- [x] 前端镜像删除 nginx 默认站点配置，避免端口冲突。
- [x] Compose 删除 MySQL/Redis/ES 宿主端口映射，新增 Ollama 服务与上传命名卷。
- [x] `.env.example` 改为 Ollama 内网默认，补充 `DEMO_SEED_ENABLED` 与 CORS 配置。
- [x] CORS 允许源同时包含 `http://localhost:8080` 与线上域名，修复本地 Docker 全栈浏览器登录 403 `Invalid CORS request`。
- [x] `.dockerignore` 排除 `.env` 等本地敏感文件。

## 前端体验

- [x] 发帖/编辑/设置/消息/草稿等页面接入认证路由守卫。
- [x] 侧边栏移除硬编码频道计数与草稿/日志假数字，Agent Logs 只对管理员显示。
- [x] 页脚 agent ticker 改用公开接口，401 不再触发全局登出噪音。
- [x] 本地化字体替换 Google Fonts 外链，`lang` 改为中文并补 meta 描述。
- [x] 图标按钮补 `aria-label`，修复 lint 警告。
- [x] 按 `docs/archive/Nexus-Campus-体验报告.md` 修复 H1-H3、M4-M10、L11-L15、D1-D10（Prompt 工坊、Agent Logs 崩溃、公告越权、AI 降级文案、发帖默认频道、标题/密码校验、真实计数、敏感词审核提示、模板变量、移动端溢出、Dashboard 密度与 Playground 折叠等），并补充对应测试。

## 文档与 CI

- [x] README 更新测试数、端口表、部署步骤、Ollama、demo 账号开关与安全说明。
- [x] frontend README 替换 Vite 模板说明。
- [x] GitHub Actions 前端任务增加 lint 步骤。

## 测试

- [x] 新增上传接口测试：合法 PNG、伪装 HTML、扩展名/魔数不一致、超 5MB。
- [x] 扩展限流测试：`X-Real-IP` 优先、XFF 最右段防伪造。
- [x] 新增认证边界测试：用户资料接口未带 token 返回 401，agent 日志接口区分公开/管理员。
- [x] 回归 `mvn test` 188 全绿、前端 `npm run build` 与 `npm run lint` 通过；Playwright 验证登录、Agent Logs、首页计数、375px 溢出、Dashboard、Playground 替换均正常。

## 可观测性与生产种子（2026-09-13，分支 `codex/production-readiness`）

- [x] 日志落盘：prod 用 `logback-spring.xml` 把 JSON 写进 `/app/logs`（命名卷 `app-logs`，单件 100MB / 7 天 / 总量 1GB），外层 `AsyncAppender`；compose 全部服务 json-file 上限 10m x 3，重建容器不再丢日志。
- [x] 指标暴露：`/actuator/prometheus` 只在 compose 内网可达，prod 暴露 `health,info,prometheus`；去掉 `SystemMetricsAutoConfiguration` 的 exclude 以恢复 OS/磁盘指标（由容器演练验证）。
- [x] AI 链路埋点：`llm_chat_completions_total{outcome}`、`llm_chat_completion_duration_seconds`、`llm_circuit_breaker_open`、`rate_limit_rejected_total{path}`、`ai_review_pending_posts`、`ai_review_reconcile_repairs_total{kind}`、`ai_review_lease_attempts_exhausted_total`。
- [x] 告警闭环：Grafana 4 条规则（熔断打开、评审积压、5xx 比率、限流突增）→ `alert-bridge` → 飞书自定义机器人（HMAC-SHA256 加签）；webhook 与密钥只进 `.env`。
- [x] 监控栈（prometheus / grafana / alert-bridge）挂在 `monitoring` profile 下且不映射宿主端口；不开 profile 时 `docker compose up` 行为不变，公网面仍只有 nginx:80。
- [x] 健康语义：`DEGRADED` 显式映射 200，`/actuator/health` 只回答可服务性，细节在 `/actuator/health/deps`；nginx 对其余 actuator 路径显式 404（ADR-0007）。
- [x] 生产账号与种子：`DEMO_SEED_ENABLED=false` 时既不写样例账号也不写样例内容，`init.sql` 只留 schema + 频道/标签；`BOOTSTRAP_ADMIN_PASSWORD` 一次性引导 `admin`（ADR-0008）。
- [ ] traceId 贯穿：过滤器生成 16-hex 写 MDC 并回写 `X-Trace-Id`，异步池与定时任务继承/新建，5xx 响应体带 `traceId`，前端错误提示显示短编号。
- [ ] 部署前 `.env` 必填：`BOOTSTRAP_ADMIN_PASSWORD`、`FEISHU_ALERT_WEBHOOK`、`FEISHU_ALERT_SECRET`、`GRAFANA_ADMIN_PASSWORD`。
- [ ] 上线前跑 `benchmark/observability/drill.ps1` 并把结论写进 `docs/research/observability-drill-2026-09.md`。

## 待执行（需用户确认）

- [ ] 部署暂不执行，不 push；确认后再按 `deployment-and-blog-plan.md` 走提交、CI 与本机 Docker + Cloudflare Tunnel 上线。
