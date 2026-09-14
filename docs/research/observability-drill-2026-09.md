> 演练脚本：`benchmark/observability/drill.ps1`（当前 21 步；本文第一到第五节是 09-13 那一版，第六节是 09-14 的重跑与纠偏）
> 环境：Windows 宿主机 + Docker 29.5.3；独立 compose project `nexus-drill`、独立命名卷、公网侧发布在 18080；
> 镜像用本轮分支 `codex/production-readiness` 构建的 prod 形态（`SPRING_PROFILES_ACTIVE=prod`，`temurin:21-jre`）。
> 作为结论的一次：2026-09-14 05:25:04 → 05:35（约 10 分钟，含镜像构建）。
> 结论：**21/21 PASS**，逐条证据在第六节。上一版结论是 2026-09-13 14:17 的 16/16（12:41 的 15 步与 13:19 的 16 步同样全绿）；
> 第一到第五节保留那一版的原文与原文里的错，因为它描述的是当时那个版本 —— 本分支在那之后改了告警面与公网健康面，
> 改完之后的重跑与纠偏全部记在第六节。

# 可观测性故障演练报告

## 一、结论

> （第一到第五节是 2026-09-13 那一版的原文，含当时成立、现在已被本分支改掉的数字，例如"4 条规则"；
> 当前版本看第六节。）

这一轮要还的债是"功能完整但看不见"。演练把 LLM 真打死、把限流真打满，然后只看监控面有没有如实说出发生过什么。
答案是：说了。三层（结构化日志 / 指标 / 告警链路）在 prod 形态容器里全部被实证，且没有新增任何公网端口。

三条最重要的：

1. **降级不会杀容器。** LLM 全断期间 `/actuator/health` 返回 `200 DEGRADED`，Docker 自己的 healthcheck 连续判定
   `healthy`，`RestartCount=0`。这是 ADR-0007 的核心主张，之前只有单元测试，现在有真容器上的真证据。
2. **故障没有变成静默丢弃。** 3 篇帖子全部落到 `status=2 (PENDING_REVIEW) + ai_reviewed=3 (FAILED 待重试)`，
   `ai_review_pending_posts=3` 被 Prometheus 抓到，LLM 恢复后对账任务重跑（`repairs=6`）。
3. **告警规则不是装饰。** Grafana 的告警引擎报告装载了这 4 条规则（`for` 分别是 5m/15m/5m/5m），
   注册的 receiver 指向 `http://alert-bridge:8080/notify`，而规则表达式里出现的每个指标名都能在同一份
   scrape 里找到。最后这一点在本轮之前是不成立的（见第四节第 1 条）：规则会安静地永不触发。

## 二、演练矩阵

| 步骤 | 断言 | 实测 |
|---|---|---|
| stack-up | 五个服务起得来 | app / prometheus / grafana / alert-bridge / llm-mock 全 Up |
| first-install-is-empty-and-administrable | 生产形态首启是空的且可管理（T5） | 0 篇帖子、7 个频道、`admin` 用 `BOOTSTRAP_ADMIN_PASSWORD` 登录后 role=ADMIN |
| prometheus-scrapes-app | 内网抓取通 | `app:8080` target `health=up` |
| grafana-provisioning-loaded | 面板与告警在仓库里版本化**且被引擎真的加载**（T2/T3） | 5 个文件挂载；provisioning API 回 4 条规则，`for` 分别是 5m/15m/5m/5m；receiver 注册在 `http://alert-bridge:8080/notify` |
| metrics-registered | 指标面存在且分桶到位 | `jvm_memory_used_bytes`、`http_server_requests_seconds_count`、`llm_circuit_breaker_open`、`ai_review_pending_posts`、`llm_chat_completion_duration_seconds*`（含 `le="30.0"`） |
| os-metrics-in-prod-container | 去掉 exclude 后资源指标回来且 JVM 不崩（T2） | `system_cpu_usage`、`disk_free_bytes`、`process_uptime_seconds` 在 scrape 里；无 CgroupV2 NPE |
| structured-json-log-lands-on-the-volume | 日志真的以 JSON 落在命名卷上，且带 MDC（T1+T6） | `/app/logs/nexus-vibe.json` 出现 `traceId=e52d0a8884779595`，与响应头 `X-Trace-Id` 一致 |
| llm-outage-parks-and-degrades | 断流时 fail-closed 生效且看得见（T3+T4） | 3 篇 parked、`llm_circuit_breaker_open=1`、`outcome=circuit_open` 2 次、health `200 DEGRADED` |
| container-not-killed-while-degraded | 降级不被 Docker 判死（ADR-0007） | `healthy`，`RestartCount=0` |
| rate-limit-rejects-and-counts | 限流拒绝被计数（T3） | 13 次登录：10×200 + 3×429，`rate_limit_rejected_total{path="/api/v1/auth/login"}=3` |
| alert-rules-select-real-metrics | 规则选的指标真存在（T3） | 4 条规则、4 个表达式，全部带 `application="nexus-vibe"` 选择器 |
| bridge-fails-loudly-without-feishu | 没配 webhook 时告警不会静默消失（T3） | 按引擎注册的 URL（`/notify`）投一条真告警，桥回 `502` 且响应体写明 `FEISHU_ALERT_WEBHOOK is not set` |
| reconcile-repairs-after-llm-returns | LLM 回来后对账真的重跑（T3） | `ai_review_reconcile_repairs_total=6`（failed 3 + safety 3）、熔断回 0 |
| deps-group-names-every-component | 内网能看到依赖细节（T4） | `/actuator/health/deps` 含 db / redis / elasticsearch / llm 四个具名组件与详情 |
| public-nginx-denies-actuator | 公网只留必要的（T4） | `/actuator/health`=200，`prometheus`/`health/deps`/`env` 全 404 |
| trace-id-visible-to-a-user | 用户拿得到追踪号（T6） | `a06388112bb9044e` 同时出现在响应头与应用日志 |

## 三、关键证据（原文）

降级期间的顶层健康，只有依赖层出事时才有的这句话：

```
200 {"description":"dependency unavailable, still serving","status":"DEGRADED","groups":["deps"]}
```

JSON 日志的一行（异步 appender 落到命名卷，MDC 跟着进来）：

```
{"@timestamp":"2026-09-13T14:18:09.513164879+08:00","@version":"1","message":"[NEXUS] ==== Request ====","logger_name":"com.nexus.campus.aspect.LogAspect","thread_name":"http-nio-8080-exec-3","level":"INFO","level_value":20000,"traceId":"e52d0a8884779595","app":"nexus-vibe"}
```

限流被打满的那 13 次登录与它的计数（窗口是每 IP 每路径 10 次/分钟，所以第 11 次开始拒）：

```
codes: 200,200,200,200,200,200,200,200,200,200,429,429,429
rate_limit_rejected_total{application="nexus-vibe",path="/api/v1/auth/login"} 3.0
```

公网侧（穿过 nginx，端口 18080）：

```
/actuator/health=200  /actuator/prometheus=404  /actuator/health/deps=404  /actuator/env=404
```

Grafana 的告警引擎确实把仓库里的规则装进去了（不是"文件挂载了所以大概生效"）：

```
$ GET /api/v1/provisioning/alert-rules
count: 4
nexus-llm-breaker-open      | for=5m  noData=OK err=OK
nexus-ai-review-backlog     | for=15m noData=OK err=OK
nexus-http-5xx-ratio        | for=5m  noData=OK err=OK
nexus-rate-limit-spike      | for=5m  noData=OK err=OK

$ GET /api/v1/provisioning/contact-points
{"uid":"nexus-feishu-bridge","type":"webhook","settings":{"url":"http://alert-bridge:8080/notify"},"provenance":"file"}
```

这一步顺带纠了演练自己的错：桥原本被投到 `/alert`，而引擎注册的是 `/notify`——桥对路径不敏感所以
     旧断言也是绿的，但那不是在测真实链路。现在 URL 从 contact point 里读，两边不可能再漂移。

熔断既开路又甩请求：

```
llm_chat_completions_total{application="nexus-vibe",outcome="failure"} 4.0
llm_chat_completions_total{application="nexus-vibe",outcome="circuit_open"} 2.0
```

恢复后的对账（一次 sweep 修 3 条评审 + 3 次安全重检）：

```
14:28:00 [35b3cb4c2a3f9b26] [AI-RECONCILE] Re-triggering FAILED review for post 2099019851222310913
14:28:00 [35b3cb4c2a3f9b26] [AI-RECONCILE] Re-triggered 3 stale reviews
```

注意 `35b3cb4c2a3f9b26`：定时任务自己起一个 runId 型 traceId，所以一次 sweep 的所有行能被一个号串起来（T6）。
桥那一头的日志同样是本次真实链路的形状——URL 是从引擎注册的 contact point 里读出来的：

```
[alert-bridge] "POST /notify HTTP/1.1" 502 -
{"error": "FEISHU_ALERT_WEBHOOK is not set"}
```

还有一处值得写下来的不对称：`repairs=6` 之后，那 3 篇帖子的行仍是 `status=2 / ai_reviewed=3`，
`ai_review_pending_posts` 也还是 3。这不是对账没生效——对账的产物是"重新派发"，而那 3 次派发撞上的是
mock：`benchmark/observability/llm-mock/mock_llm.py` 按设计只回一句 `OK`，它不是合法评审体，于是帖子
又回到待重试。演练证明的是**重新派发确实发生**（`ai_review_reconcile_repairs_total=6` 与
`[AI-RECONCILE] Re-triggering` 日志），不是"评审成功"；评审输出的质量由 LLM 侧的解析与修复测试管，
不在这一步的范围内。

## 四、演练暴露并已修掉的问题

1. **告警规则原来永远不会触发。** Spring Boot 3.3 不会自动给指标加 `application` 标签，而 4 条规则全部
   `select ...{application="nexus-vibe"}`。缺标签时规则不报错，只是安静地匹配不到任何序列。
   修法是显式 `management.metrics.tags.application: ${spring.application.name}`，并在
   `ActuatorMetricsTest` 里断言该标签（commit `202bbef`）。演练的 `metrics-registered` 步骤现在把这条当成硬门。
2. **cgroup V2 那个坑在 prod 容器里不成立。** 当初 `SystemMetricsAutoConfiguration` 被 exclude 掉，是因为
   CI 的 `ci-linux` profile 给 surefire 传了 `-XX:-UseContainerSupport`——那才是崩溃根因。
   本轮在 `temurin:21-jre` 里去掉 exclude，资源指标正常、JVM 不崩，所以 exclude 保持删除，T2 不是受阻项。
3. **一次故障太短，scrape 抓不到。** 熔断开路是 60 秒，Prometheus 15 秒抓一次，单篇帖子的失败窗口在采样
   之间闪过去是可能的。演练因此连发 3 篇，让开路状态持续覆盖若干个抓取周期——这也解释了告警规则为什么写
   `for: 5m`：**瞬时抖动本来就不该报警**，运营侧要接受这个延迟。
4. **fail-closed 有两条路径，断言不能靠日志措辞。** 缓存健康探针落在哪一刻，决定这次是
   `VibePostServiceImpl`（"failed closed to PENDING_REVIEW"）还是 `AiSafetyCheckListener`
   （"failing closed to PENDING_REVIEW"）写库。脚本第一版 grep 了其中一种措辞，于是把一个正确的行为判成失败。
   现在读 `vibe_post` 的 `status + ai_reviewed` 两列。

5. **一次 docker CLI 崩溃不该变成产品失败。** 14:05 那次有两个 Step 红了，原因都在宿主机：`docker exec`
   自己吐了一段 Go runtime 崩溃栈（exit 2），另一次 docker 打了 usage（exit 125）。这两步在前后两次运行里
   都是绿的。脚本现在把"命令行自己崩了"和"断言没过"分开：前者退避重试最多 3 次，后者照旧立刻红。
   没有这条区分，演练结论的可用性取决于宿主机当天的运气，那就没人会信 16/16 这个数字了。

6. **要说清楚：这一节里只有第 1 条是产品的 bug。** 为了拿到可信的 16/16，演练真跑了 10 次：3 次全绿
   （12:41 的 15 步、13:19 与 14:17 的 16 步），4 次被我主动中断（断言还挂在日志措辞上，或 `le=` 因
   标签顺序取错值），2 次红在脚本自己的错上（一个漏提交的 helper、一处重复的 `param` 行、`$_.Name`
   取了不存在的属性），1 次红在宿主机的 docker CLI 崩溃上（第 5 条）。断言红了先查断言、再查产品，
   否则演练报告会变成噪声，下次谁也不会再跑它。产品行为在三次全绿运行里逐项一致：3 篇 parked、
   `429` 3 次、`repairs=6`、公网三探全 404、`/actuator/health/deps` 四组件齐名。

## 五、演练没有覆盖的（诚实清单）

- **真实飞书送达**：需要一个可用 webhook。桥的 17 个 Python 单测固定了加签算法与请求体，演练（第六节）
  证明了告警能一路走到一个会验签的假收件人并被接受，但那不是飞书自己的服务器。上线前请人工发一条测试告警到群里确认。
- **Prometheus 真的把规则推进 pending/firing**：演练用 provisioning API 证明了 4 条规则被引擎装载、
  且表达式里的指标名在同一份 scrape 里找得到；但规则要 5m/15m 的持续条件，演练窗口不够，所以
  "会响"这一步仍未验证。
- **Grafana 界面**：只验证 provisioning 文件挂载、`/api/health`、以及 provisioning API 里的规则与
  contact point，没有看面板渲染——dashboard 的两个 JSON 是否真画得出图，要人打开看一眼。
- **`ai_review_lease_attempts_exhausted_total`**：要把一篇帖子的重试预算耗到 5 次才会出现，未演练。
- **日志滚动的量化上限**（单件 100MB / 7 天 / 总量 1GB）：appender 图由
  `LogbackStructuredOutputTest` 断言，本轮没有真的写满 1GB。
- 其它故障形态（磁盘满、OOM、MySQL 主从抖动）不在本轮范围。
- 前端把短编号显示在错误提示里，属于人工验收。

两条关于这套告警面的边界，运营时要记住（第一条在 E3 之后只剩一半，另一半正是这一轮改掉的）：

- **规则是拉模式的**：应用进程或抓取本身没了，抓不到 target 这件事现在由 `nexus-prometheus-scrape-failed`
  负责喊（`noDataState` 与 `execErrState` 都是 `Alerting`），这是第六节里 `app-death-is-not-reported-as-health`
  与 `alert-no-data-policy-is-per-rule` 两步在证的事。其余比率类规则仍是 `noDataState: OK`：它们表达式末端有
  流量 guard，空结果通常意味着"太安静"而不是"坏了"。
- **`for: 5m` / `15m` 的持续条件意味着最短 5 分钟延迟**：瞬时抖动不会吵到人，这是有意的（见第四节第 3 条）。

## 六、2026-09-14 重跑（E 轮，21 步全绿）

E3（告警面）与 E6（公网健康面）改完之后，第一到第五节描述的那一版已经不成立了，所以在 05:25:04 重跑了一次完整演练：
**21 步，0 失败**，证据 `benchmark/observability/evidence/drill-20260914-052504.log`。

这一步真正新增的，不是"又多几步绿"，而是三类以前只能声称的东西拿到了证据：

| 步骤 | 证的是哪句声称 | 实测 |
|---|---|---|
| grafana-provisioning-loaded | "规则在仓库里版本化"不等于"规则生效" | 引擎读回 6 条规则、`for` 分别 5m/15m/5m/5m/1m/2m，且 `noDataState` 计数 `Alerting=3 OK=3` 是**引擎的回答**而不是文件的字 |
| alert-no-data-policy-is-per-rule | 缺数据到底算不算事故，每条规则自己决定 | 三条 `Alerting`（熔断、积压、抓不到 target），三条比率 `OK` |
| bridge-refuses-to-start-without-a-target | 没有收件人的监控面不能看起来是绿的 | 同一镜像在清空 `FEISHU_ALERT_WEBHOOK` 后 exit 2，并把变量名说出口 |
| alert-delivers-to-the-far-side | "告警送出去了" | 按引擎注册的 URL 投一条真通知 → 桥加签转发 → `webhook-sink` 用同一把密钥重算 HMAC：`[sink] feishu msg_type=text signature=ok`，回 `{"code":0}` |
| public-nginx-denies-actuator（加了 metrics） | 公网与 prod 进程都不该有指标浏览 | 边缘五探 `health=200`、`prometheus`/`health/deps`/`env`/`metrics` 全 404；prod 容器自己的 8080 上 `/actuator/metrics` 也是 404 |
| public-health-answers-only-servability | 同一个 URL 回答两个问题这件事被拆开了 | 公网 `{"status":"UP"}`，内网 `/actuator/health/deps` 四组件带详情，互不越界 |
| app-death-is-not-reported-as-health | 应用没了要说"没了" | `up{job="nexus-vibe"}=0` 被抓到，公网 `/actuator/health` 回 502 |
| rollback-swaps-between-two-real-image-tags | 有名字的标签=能回滚 | `prerelease-20260913 → latest → prerelease-20260913`，每次换完都读容器自己的 `Config.Image`，两侧 `/api/v1/posts` 都是 200 |

顺带说清楚两件事：`latest` 与 `prerelease-20260913` 是**两个真实不同的构建**（相差 18 小时，digest 不同），
所以这一步不是在证明 compose 会拼字符串；而它也不能证明回滚在语义上正确——两侧代码只差这一轮的可观测性改动，
一次真正的"回滚到一个坏版本"仍然没有样本。

### 这一轮演练自己错的三处（比上面那张表更值得记）

1. **`noDataState: ALERTING` 把整个监控面弄崩了。** 这是产品 bug，不是断言 bug：Grafana 只认
   `Alerting | NoData | OK | KeepState`，装载失败即退出，`restart: unless-stopped` 变成重启循环——
   没有面板、没有通知、没有规则。发现它的是 `grafana-provisioning-loaded` 去问 `/api/health`；
   而同样读这个文件的 `alert-no-data-policy-is-per-rule` 在那次红跑里是**绿的**：它比对的是文件的字，
   不是引擎的解析结果。现在两侧都在：Java 侧 `GrafanaAlertProvisioningTest` 0.3 秒内拒掉错误拼法
   （把 `ALERTING` 放回去会红四条），演练侧从引擎 API 数 `noDataState`。
2. **断言读的是字节而不是文本。** `public-health-answers-only-servability` 报 "public health carries no status"，
   而证据文件里躺着一串 `123 34 115 116 97 116 117 115 34 58 34 85 80 34 125` —— 那正是 `{"status":"UP"}`。
   PowerShell 7 对 actuator 的媒体类型返回 `byte[]`，`.Content -notmatch '"status"'` 于是永远为真。
   产品是对的，断言是错的；修法是 `Get-PlainText` 解码，而不是把断言放宽成只信状态码。
3. **演练结果取决于它是被怎么启动的。** 一次 detached 启动里 `first-install-is-empty-and-administrable`
   死在 `ConvertFrom-Json`（`data[1].description` 后面有非法字符）：频道描述是中文，宿主机控制台不是 UTF-8
   代码页时 `docker exec` 的字节就被解码成乱码。同一个断言在交互式启动里一直是绿的。脚本现在自己把控制台设成
   UTF-8——"这条断言只在某些人正确启动 shell 时才成立"是演练不该有的性质。
   同一类竞态还有第二处：`grafana-provisioning-loaded` 只探一次 `/api/health`，而 Grafana 冷卷启动要几十秒
   （sqlite 迁移 + 一次 `database is locked` 重试），该服务又没有 healthcheck，于是这一步在和自己的启动赛跑。

还有一处是流程上的：所有演练服务都钉了 `container_name`，两次并发运行不会得到两套栈，只会在 `up -d` 中途
撞出一句 `Conflict. The container name "/nexus-drill-db" is already in use`——这个现场我踩了一次，
白烧十二分钟。脚本现在开机先看有没有别的 `nexus-drill-*` 容器，有就把命令说清楚再退出。

## 七、怎么重跑

```bash
pwsh -File benchmark/observability/drill.ps1              # 建镜像 + 全流程，约 10–20 分钟
pwsh -File benchmark/observability/drill.ps1 -SkipBuild   # 复用镜像（大头是对账那一步要等 cron）
pwsh -File benchmark/observability/drill.ps1 -Keep        # 跑完别拆，留着手查
```

它以 `-p nexus-drill` 独立 project 运行，容器名全部是 `nexus-drill-*`，卷也是自己的一套，
所以能和开发者正在跑的 `nexus-vibe` 栈并存；结束时 `down -v` 只清自己的卷。
每步的原始回答写进 `benchmark/observability/evidence/drill-<时间戳>.log`（该目录已 gitignore），
汇总写进同目录的 `-summary.md`，退出码 0 代表 21 步全过。

脚本里有一个坑值得记：所有 docker 参数都必须以数组形式传入。`Invoke-Compose up -d` 里的 `-d` 会被
PowerShell 的公共参数 `-Debug` 吃掉，于是 `compose up -d` 变成前台运行的 `compose up`，永不返回；
`-v` 同理，被 `-Verbose` 吞掉。

## 八、E9 之后再跑一遍（2026-09-14 08:13，`drill-20260914-081339`：21 步 0 失败）

E9 改了 ES 客户端与 admin 端点的返回形状，所以整套重跑一次，不挑步骤。结论仍然是 21/21，
`pwsh -File benchmark/observability/drill.ps1`（带建镜像），head `bde8a80`。

这一遍有一条不是计划里的：`rollback-swaps-between-two-real-image-tags` 换的两个 tag 里，
`e9-reindex-drill` 是我在恢复演练第 7 节现场搭 scratch 栈时留下的那个镜像，也就是**带着 E9 新代码的那个**。
于是这一步顺手证掉了此前没人证过的事——改了 `_bulk` 计数与索引就绪判定的镜像，能在真容器里起来、
过健康检查、并在回滚两端都答 `200`。它不在断言列表里，是环境自己送上门的证据，记在这里以免被当成"下次也会这样"。

顺带把第 7 节的 scratch 演练也接上了这一遍的口径：那个 tag 现在既是恢复演练的产物，也是回滚演练的产物，
`docker images` 里还在，别 prune 掉——它是目前唯一同时被两条路径用过的镜像。

这一遍没有暴露新的脚本缺陷，也没有暴露产品缺陷；`05a266b` 与 `bde8a80` 两次 markdown-only push 的 CI 都是
四个 job 全绿（`34791146413`、`34791584974`），这是 E1 那个门禁自反性断言的第 3、4 次重复——仍然只是"更多数据"，
不是"证明消失了"。
