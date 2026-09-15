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
- [x] 面板真的被打开看过（E10）：`check_panels.py` 逐条表达式过 datasource 代理，`render_panels.py`
      用无头浏览器渲染两张 dashboard。scratch 栈上的结果 `ok=22 / empty=2`（那两条是没 LLM 流量的
      LLM 面板，故意不置零）、Overview 9/9 canvas 全画、AI Pipeline 5/5 全画、`console_errors=0`。
      这一步不是形式：修之前 `Latency p50/p95/p99` 查的 `http_server_requests_seconds_bucket` 根本不存在，
      面板从提交起就没画过图，而 21 步演练一直是绿的。渲染栈只把 Grafana 绑在 `127.0.0.1:3000`，
      部署形态依旧不映射任何监控端口。
      带着这项改动重跑演练：`drill-20260914-093857`，**21/21**。
- [ ] 上线 24 小时后回看 `/app/logs`（卷 `app-logs`）：确认滚动按 100MB / 7 天 / 1GB 收口，`docker logs` 侧 10m x 3 也没漏。
- [ ] 同一次回看顺手看一眼延迟面板上的 p99：scratch 栈里唯一超过 2s 的请求是 `/actuator/health`
      （max `3.16s`），而它是 healthcheck 每 30s 调的 URL、`--timeout=10s`。真负载下这个余量是变小还是
      吃掉一半，现在看得见，也就必须有人看。

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
- [ ] **回滚只在数据兼容窗口内成立**（2026-09-16 演练撞出来的边界）。演练把 `APP_TAG` 换成上一轮的构建后，第一次探活是 200，隔一会儿再请求就成了"连接被拒"：Tomcat 在 `CommandLineRunner` 跑完之前就开始监听，而旧构建的 `DataPreloader` 坚持要插一个用户名为 `admin` 的样例账号，撞上 `BootstrapAdminInitializer` 已经写进库里的那一行，runner 抛异常 → Spring 关掉上下文。**"换完镜像 health 变绿"不等于回滚成功**，要看下一个请求。因此：回滚目标必须是仍能在当前库上启动的构建（本轮没有改表结构，所以前后两个 round-six 构建之间可滚；pre-ADR-0008 的构建不可），演练的 `rollback-swaps-between-two-real-image-tags` 现在用 `-RollbackTag` 显式指定目标、比对两个 tag 背后的 image id、并对每侧连探两次（间隔 12 秒）。真实的一次"滚到坏版本"仍然没有样本。
- [ ] 发布与回滚都动 `app` + `web` 两个服务、共用同一个 `APP_TAG` 值：SPA 和 API 是一组，不拆开滚。

## 部署执行记录（2026-09-16，本机全栈）

> 范围：把 master（`538dd9e`，PR #2 + PR #3 都已合并）真正起成本机部署形态，并逐条验收。
> 结论：**应用层部署形态已验证通过；公网可达与飞书告警送达仍未完成**，原因都是外部条件而不是代码。

- [x] 发布 tag 定为 `20260916-01`，`.env` 里 `APP_TAG` 与两个镜像 tag 一致；`app`/`web` 容器读到的 `Config.Image` 就是这两个 tag。
- [x] 本机 Docker 多阶段构建仍然卡在容器内依赖下载（buildx CPU 长时间不动，与 09-15 记录的同一条环境问题一致），所以镜像由宿主机构建的构件装配：`mvn -DskipTests package`（BUILD SUCCESS，24.5s）+ `npm run build`（6.0s），运行时形态与提交的 Dockerfile 第二段一致（temurin 21 JRE + curl + uid 10001 + 同一条 entrypoint）。**这条不能当成"提交的 Dockerfile 已验证"**，CI 的 Docker image build job 才是那份证据。
- [x] 一次性卷属主迁移已执行：`nexus-vibe_app-uploads` / `nexus-vibe_app-logs` 从 root 改为 `10001:10001`，`docker compose exec app id -u` 出 `10001`，`touch /app/uploads/probe` 成功，`/app/logs/nexus-vibe.json` 由 `appuser` 写入。
- [x] 日志：prod 走 JSON 行，字段含 `@timestamp`/`level`/`logger_name`/`thread_name`/`traceId`/`app`；实测一行示例 `{"...","message":"Business rejection 404: Post not found.","traceId":"523c98ccb8a874a1","app":"nexus-vibe"}`。
- [x] 公网面收敛：`/actuator/health` → 200 `{"status":"UP"}`；`/actuator/prometheus`、`/actuator/health/deps`、`/actuator/info` → 全部 404。
- [x] 指标：`jvm_memory_used_bytes`、`http_server_requests_seconds_count`、`process_cpu_usage`、`system_cpu_usage`、`disk_free_bytes` 在 prod 容器里都有序列；`CgroupV2Subsystem` NPE 未复现，`SystemMetricsAutoConfiguration` 的 exclude 可以保持删除状态。
- [x] Prometheus 抓取 `app:8080/actuator/prometheus`，`up{job="nexus-vibe"}=1`；Grafana 11.1.4 健康，三张 dashboard（Overview / AI Pipeline / Product Loop）与 6 条 alert rule 都已 provisioning。
- [x] 产品闭环真跑一遍：注册（带 nickname/email）→ 登录 → 在「代码急诊室」发帖 → 异步 AI 评审写回评分 8，日志里事件监听线程 `agent-llm-1` 与请求线程共享同一个 `traceId`。
- [x] 埋点随之递增：`post_submitted_total{status="published"}=1`、`llm_chat_completions_total{outcome="success"}=2`、`llm_chat_completion_duration_seconds_count=2`、`ai_review_pending_posts=0`、`funnel_activation_ratio` 有值。
- [x] 契约抽查：404 与 400 响应体都是 `{code,message,data}`，`X-Trace-Id` 响应头存在，body 不带 `traceId`（只有 5xx 才带，符合 R2/T6）。
- [x] `alert-bridge` 在 `FEISHU_ALERT_WEBHOOK` 为空时按设计拒绝启动并打印原因，`docker compose ps` 显示 `Restarting`。
- [ ] **飞书告警送达**：需要在 `.env` 填 `FEISHU_ALERT_WEBHOOK`（有加签再填 `FEISHU_ALERT_SECRET`），然后人工触发一条告警确认群里真的收到。
- [ ] **公网可达**：本机 `cert.pem` 与 `~/.cloudflared/config.yml` 都不存在，`cloudflared tunnel login` / `tunnel create` / DNS 路由这三步必须由域名持有者本人完成。
- [ ] **生产数据基线**：当前库还是开发库，里面有 demo 账号 `shing`/`alice`/`bob`/`testuser` 和旧 `admin`（id=1，密码不来自本轮 `BOOTSTRAP_ADMIN_PASSWORD`）。公网发布前要么清库让 `BootstrapAdminInitializer` 从 `.env` 建唯一管理员，要么明确保留这份数据。**未执行任何删除。**
- [ ] `GRAFANA_ADMIN_PASSWORD` 目前与 `BOOTSTRAP_ADMIN_PASSWORD` 是同一个值，上线前应各自轮换。

## 待执行（需用户确认）

- [x] 部署已按上面这一节在本机执行（应用层）；仍需用户提供飞书 webhook 并完成 Cloudflare Tunnel 登录后，公网发布才算完成。

## 容器与升级路径（R5，2026-09-15，分支 `codex/http-contract-and-product-loop`）

- [x] `Dockerfile` 运行时阶段建 `appuser`（uid/gid 10001，`--no-create-home`、`nologin`），jar 用 `--chown` 落盘，`/app`、`/app/uploads`、`/app/logs` 一并 `chown`，最后 `USER appuser`。8080 不是特权端口，非 root 不需要任何额外放行。
- [x] `docker/nginx/nginx.conf` 去掉 `upstream { server app:8080; }`：那串名字是在**配置加载期**解析的，冷启动时 app 容器还没建出来，nginx 就以 `host not found in upstream` 退出，全靠 `restart: unless-stopped` 把自己救回来——静态站跟着一起消失。改成 `resolver 127.0.0.11 valid=10s` + 变量 `proxy_pass`，解析发生在请求期。代价写进了配置文件：OSS nginx 对变量后端用不了 `keepalive`，每个代理请求新建一条到 app 的连接。
- [ ] **一次性卷属主迁移（升级必做，全新安装不需要）**：Docker 只在命名卷**为空**时用镜像里的属主初始化它。开发机与任何已部署主机上的 `nexus-vibe_app-uploads` / `nexus-vibe_app-logs` 都是 root:root，换镜像不会改它们。后果在 2026-09-16 被演练量出来，比这一条原先写的严重：JVM 打不开 `/app/logs/nexus-vibe.json` 时，Spring Boot 会把 logback 记录下来的失败升级成 `IllegalStateException`，在 `prepareEnvironment` 阶段退出——容器不是降级，是四分钟重启十次、`/actuator/health` 一次都没答过、公网整站消失。现在的镜像在 entry point 里先探一次目录：可写就用 `/app/logs`，不可写就退回 `/tmp/nexus-logs` 并在 stderr 打一条 `WARN: /app/logs is not writable by uid 10001 …`，站点照常服务，代价是那段时间的 JSON 日志不跨容器存活；上传目录不可写仍然是运行时 500。所以这一步不是可选清理，而是升级之前应该先跑的那一步。在项目所在主机上跑一次，`<project>` 是 compose 项目名（默认目录名 `nexus-vibe`，演练里是 `nexus-drill`）：
      ```
      docker compose stop app
      docker run --rm \
        -v <project>_app-uploads:/uploads \
        -v <project>_app-logs:/logs \
        alpine sh -c 'chown -R 10001:10001 /uploads /logs'
      docker compose up -d app
      ```
      验证：`docker compose exec -T app id -u` 出 `10001`；`docker compose exec -T app touch /app/uploads/probe && docker compose exec -T app rm /app/uploads/probe` 不报错；发一条请求后 `docker compose exec -T app tail -n 1 /app/logs/nexus-vibe.json` 有带 `traceId` 的 JSON 行。
      这一段走的是 `benchmark/observability/drill.ps1` 的 `non-root-app-and-the-root-owned-volume-upgrade` 真路径：它先把两个卷 chown 回 root（并留一个哨兵文件证明没修错卷），再按上面的命令修回来，另外断言「退到 `/tmp` 的日志真的在写」和「重启后 `/app/logs` 的 mtime 前进」。该步骤第一次执行（2026-09-16）是红的，红在 crash loop 那一处；entry point 的目录探测是看完现场之后才加的。
- [ ] `web` 的 `depends_on` 保持 `service_started`，**不要**改 `service_healthy`：那也能消掉 nginx 的启动竞态，但代价是 app 真死的时候连静态页一起起不来，正是 ADR-0007 要避免的失败形态。
- [ ] 本轮不动 `nginx-unprivileged`：它要换监听端口（80→8080）、compose 映射、日志路径三处，值得单独一轮带验证的改动，而不是顺路捎带。`web` 容器仍以 root 运行 nginx master（worker 是 `nginx` 用户），这一条如实记为未完成。
