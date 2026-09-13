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
- [x] 告警闭环：Grafana 6 条规则（熔断打开、评审积压、5xx 比率、限流突增、抓不到 target、99.9% 错误预算快烧）→ `alert-bridge` → 飞书自定义机器人（HMAC-SHA256 加签）；webhook 与密钥只进 `.env`。
- [x] 监控栈（prometheus / grafana / alert-bridge）挂在 `monitoring` profile 下且不映射宿主端口；不开 profile 时 `docker compose up` 行为不变，公网面仍只有 nginx:80。
- [x] 部署命令写死在这里，别让"要不要开监控"变成一次临场决定：
      `APP_TAG=<新值> docker compose --profile monitoring up -d --build`（默认 6 个服务 + prometheus/grafana/alert-bridge 3 个）。
      开 profile 就必须先填 `FEISHU_ALERT_WEBHOOK`：`alert-bridge` 在没有收件人时直接拒绝启动（`restart: unless-stopped` 会把它变成明显的重启循环），
      没有飞书机器人就整个去掉 `--profile monitoring`，别留一个绿色但没人收信的面板。
- [x] 健康语义：`DEGRADED` 显式映射 200，`/actuator/health` 只回答可服务性，细节在 `/actuator/health/deps`；nginx 对其余 actuator 路径显式 404（ADR-0007）。
- [x] 生产账号与种子：`DEMO_SEED_ENABLED=false` 时既不写样例账号也不写样例内容，`init.sql` 只留 schema + 频道/标签；`BOOTSTRAP_ADMIN_PASSWORD` 一次性引导 `admin`（ADR-0008）。
- [x] traceId 贯穿：过滤器生成 16-hex 写 MDC 并回写 `X-Trace-Id`，异步池与定时任务继承/新建，5xx 响应体带 `traceId`，前端错误 toast 显示前 8 位追踪号。
- [ ] 部署前 `.env` 必填：`BOOTSTRAP_ADMIN_PASSWORD`、`FEISHU_ALERT_WEBHOOK`、`FEISHU_ALERT_SECRET`、`GRAFANA_ADMIN_PASSWORD`。
- [x] 上线前跑 `benchmark/observability/drill.ps1` 并把结论写进 `docs/research/observability-drill-2026-09.md`：
      2026-09-13 13:19 那次 16 步全绿（真容器、真断流、真打满限流），演练脚本本身修掉 4 处，产品侧暴露并修掉 1 个真 bug
      （告警规则依赖的 `application` 指标标签缺失）。
- [x] 同一件事在本轮重做了一遍：E3/E6 之后演练是 21 步，且断言换了对象（告警规则要经 Grafana 引擎读回、
      告警要走到会验签的假收件人、回滚要读容器自己的镜像名）。2026-09-14 05:25 那次 **21/21 PASS**，
      逐条证据与本轮纠偏记录在 `docs/research/observability-drill-2026-09.md` 第六节；
      上面那句"16 步全绿"只描述 09-13 的那个版本，不能拿来证明这一版。
- [ ] 配好 `FEISHU_ALERT_WEBHOOK` / `FEISHU_ALERT_SECRET` 后，人工发一条测试告警到群里确认真的收得到——
      演练只能证明"告警到得了桥、桥失败时会喊出来"。
- [ ] 上线 24 小时后回看 `/app/logs`（卷 `app-logs`）：确认滚动按 100MB / 7 天 / 1GB 收口，`docker logs` 侧 10m x 3 也没漏。

## 备份与恢复（E5，2026-09-14，分支 `codex/production-readiness`）

- [x] `scripts/backup.ps1` 真跑通：对运行中的栈导出备份集，逐件校验明文 SHA-256 / gzip / dump 结束标记，`manifest.json` 记行数与卷清单（本机 8 个声明卷里 5 个存在）。
      过程里修掉两处会让脚本一步都跑不动的问题：`docker compose ls` 不接受 Go 模板（改 `--format json`），以及 `-p` 只管卷不管 `container_name`（新增 `docs/runbook/docker-compose.restore-test.yml` 把演练容器改名为 `nexus-restore-*`）。
- [x] 按 `docs/runbook/restore.md` 把一次真实恢复演练做完（2026-09-14）：dump 哈希与 manifest 一致 → 导入退出码 0 → 10 张表行数逐项相等 → 中文标题可读 → `migrate-0005/6/7` 的对象都在 → 恢复出的 app 以 `200 70` 提供还原后的上传文件且三处哈希一致；演练后 6 个临时卷清干净、生产栈 `Up 2 days` 未被碰。
- [x] 演练暴露的两处文字坑就地改掉：section 2 的 `mysqladmin` 占位命令原本不可执行（容器 `healthy` 早于 TCP 可连，第一次导入死在 `ERROR 2003`），section 1 补上"容器名不是 project-scoped"这条前提。
- [ ] **换一块真正的异盘或异机副本**：本机是单块 NVMe 分区成 C/D/E，所谓"第二卷"和数据库同盘，盘坏即一起没。脚本现在会把这件事打印出来并写进 `manifest.json` -> `warnings`，但没人替你把副本搬走。
- [x] `es-data` 的全量重建索引写进恢复流程（E9 + `restore.md` 第 7 节）：索引不在任何 dump 里，恢复后必须调 `POST /api/v1/admin/search/reindex` 并核对 `requested/reindexed/failed/complete`，否则搜索静默返回空且不报错。
      顺带纠正一个本轮自己写进仓库的错误断言：文档曾称"代码里根本没有全量重建路径"，而该端点自 `9c4b002`（2026-08-14）就在；真正的缺陷是 `rebuildIndex` 返回它从 MySQL 读到的行数，ES 全拒或根本没起也照样报 `reindexed: 32`——现在计数来自 `_bulk` 响应里逐项 2xx，解析不了按 0 计（fail-closed）。
- [x] 第 7 节的两条 reindex 路径都在真集群上跑过（2026-09-14 第二遍，scratch 里补起 `elasticsearch`）：
      重建返回 `{"requested":32,"reindexed":32,"failed":0,"complete":true}`，`nexus_posts` 的 `docs.count` 从 0 变 32，
      用 dump 之前就存在的老帖子关键词 `RAG` 搜得回来，`_analyze` 对 `构建教程` 切出 `构建/建教/教程` 二元组（证明确实带上了 CJK mapping，不是自动建的默认索引）；
      再把集群中途 `docker stop`，同一个端点如实返回 `{"requested":32,"reindexed":0,"failed":32,"complete":false}` —— 被替换掉的实现在这一枪下报的是 `reindexed: 32`。
- [ ] 决定 `es-data` 的读时修复策略（E9 的遗留项）：ES 宕机期间写的帖子只在下一次成功写入时补索引，没有谁去扫差额；全量重建是把钝刀，要不要定时跑还没人拍板。
- [ ] 装上周计划任务并确认它真的在跑（`Get-ScheduledTask`），第一次触发后回看 `manifest.json` 的 `complete` 与 `warnings`。

## 发布与回滚（E4，2026-09-14，分支 `codex/production-readiness`）

- [x] compose 的 `app`/`web` 不再匿名：`image: nexus-vibe-app:${APP_TAG:?}` 与 `nexus-vibe-web:${APP_TAG:?}`（`alert-bridge` 本来就有 tag）。缺 `APP_TAG` 时 `docker compose config` 直接报错，而不是留下一个没有回滚目标的 latest。本机 `.env` 若还没有这个键，先补一行 `APP_TAG=dev`。
- [x] `.env.example` 写清 `APP_TAG` 语义：每个发布值唯一标识一次构建（git short SHA 或 `20260914-01`）；发布 = 改 tag 后带 `--build` 起，回滚 = 改回旧值后不带 build 起。
- [x] 内存上限补齐：`elasticsearch` 1g、`ollama` 8g（原先全栈只有 `app` 有 `mem_limit`）。这两个不自我封顶——app 的堆按容器上限自适应，ES 的堆钉死在 `ES_JAVA_OPTS`、Ollama 按模型体积增长；没有上限时，OOM killer 随机挑的受害者可能是 MySQL。
- [x] CI 在 master push 用 `actions/upload-artifact@v4` 落 `target/nexus-campus.jar` 与 `frontend/dist`（private registry 按票面 Rejected，先要一个可指认的对象）；同时后端 job 换到 JDK 21（运行时），镜像 job 对 `Dockerfile`、`frontend/Dockerfile`、`pom.xml`、`src/main/**` 的 PR 变更做构建验证，master push 构建后真 `docker run` 探 `/actuator/health`。
- [ ] 发布纪律：宿主机上始终保留最近两个 `APP_TAG` 的镜像；回滚窗口内禁止 `docker image prune -a` 和 `docker compose down --rmi all`（E4 票面的 Rejected 段已把 registry 出圈，旧 tag 不 prune 是回滚唯一还活着的前提）。
- [x] 回滚一条命令（A→B→A 已在演练里跑通：换 `APP_TAG` 后不带 `--build` 起，读容器自己的 `Config.Image` 确认换到的就是目标 tag，两侧 `/api/v1/posts` 都 200）：
      `APP_TAG=<上一个值> docker compose up -d app web`（不带 `--build`，直接用留在宿主机上的旧镜像），
      随后 `curl -s http://localhost:8080/api/v1/posts` 确认真的答回来了，再把该值写回 `.env`，防止下次 `up` 又漂回新版本。
- [ ] 发布与回滚都动 `app` + `web` 两个服务、共用同一个 `APP_TAG` 值：SPA 和 API 是一组，不拆开滚。

## 待执行（需用户确认）

- [ ] 部署暂不执行，不 push；确认后再按 `deployment-and-blog-plan.md` 走提交、CI 与本机 Docker + Cloudflare Tunnel 上线。
