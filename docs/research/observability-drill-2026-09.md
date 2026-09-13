> 演练脚本：`benchmark/observability/drill.ps1`（16 步）
> 环境：Windows 宿主机 + Docker 29.5.3；独立 compose project `nexus-drill`、独立命名卷、公网侧发布在 18080；
> 镜像用本轮分支 `codex/production-readiness` 构建的 prod 形态（`SPRING_PROFILES_ACTIVE=prod`，`temurin:21-jre`）。
> 干净通过的一次：2026-09-13 13:19:32 → 13:28:28（约 9 分钟，`-SkipBuild`；含构建约 15–20 分钟）。
> 结论：16/16 PASS。同日 12:41 那次（当时 15 步）也全绿，脚本改断言方式后重跑结果一致。

# 可观测性故障演练报告

## 一、结论

这一轮要还的债是"功能完整但看不见"。演练把 LLM 真打死、把限流真打满，然后只看监控面有没有如实说出发生过什么。
答案是：说了。三层（结构化日志 / 指标 / 告警链路）在 prod 形态容器里全部被实证，且没有新增任何公网端口。

三条最重要的：

1. **降级不会杀容器。** LLM 全断期间 `/actuator/health` 返回 `200 DEGRADED`，Docker 自己的 healthcheck 连续判定
   `healthy`，`RestartCount=0`。这是 ADR-0007 的核心主张，之前只有单元测试，现在有真容器上的真证据。
2. **故障没有变成静默丢弃。** 3 篇帖子全部落到 `status=2 (PENDING_REVIEW) + ai_reviewed=3 (FAILED 待重试)`，
   `ai_review_pending_posts=3` 被 Prometheus 抓到，LLM 恢复后对账任务重跑（`repairs=6`）。
3. **告警规则不是装饰。** 4 条规则的表达式里出现的每个指标名，都能在同一份 scrape 里找到；
   而这一点本轮之前是不成立的（见第四节第 1 条）。

## 二、演练矩阵

| 步骤 | 断言 | 实测 |
|---|---|---|
| stack-up | 五个服务起得来 | app / prometheus / grafana / alert-bridge / llm-mock 全 Up |
| first-install-is-empty-and-administrable | 生产形态首启是空的且可管理（T5） | 0 篇帖子、7 个频道、`admin` 用 `BOOTSTRAP_ADMIN_PASSWORD` 登录后 role=ADMIN |
| prometheus-scrapes-app | 内网抓取通 | `app:8080` target `health=up` |
| grafana-provisioning-loaded | 面板与告警在仓库里版本化（T2/T3） | datasource、2 个 dashboard、rules/contact-points/policies 全部挂载 |
| metrics-registered | 指标面存在且分桶到位 | `jvm_memory_used_bytes`、`http_server_requests_seconds_count`、`llm_circuit_breaker_open`、`ai_review_pending_posts`、`llm_chat_completion_duration_seconds*`（含 `le="30.0"`） |
| os-metrics-in-prod-container | 去掉 exclude 后资源指标回来且 JVM 不崩（T2） | `system_cpu_usage`、`disk_free_bytes`、`process_uptime_seconds` 在 scrape 里；无 CgroupV2 NPE |
| structured-json-log-lands-on-the-volume | 日志真的以 JSON 落在命名卷上，且带 MDC（T1+T6） | `/app/logs/nexus-vibe.json` 出现 `traceId=53a7e31830dad504`，与响应头 `X-Trace-Id` 一致 |
| llm-outage-parks-and-degrades | 断流时 fail-closed 生效且看得见（T3+T4） | 3 篇 parked、`llm_circuit_breaker_open=1`、`outcome=circuit_open` 2 次、health `200 DEGRADED` |
| container-not-killed-while-degraded | 降级不被 Docker 判死（ADR-0007） | `healthy`，`RestartCount=0` |
| rate-limit-rejects-and-counts | 限流拒绝被计数（T3） | 13 次登录：10×200 + 3×429，`rate_limit_rejected_total{path="/api/v1/auth/login"}=3` |
| alert-rules-select-real-metrics | 规则选的指标真存在（T3） | 4 条规则、4 个表达式，全部带 `application="nexus-vibe"` 选择器 |
| bridge-fails-loudly-without-feishu | 没配 webhook 时告警不会静默消失（T3） | 桥回 `502` 且响应体写明 `FEISHU_ALERT_WEBHOOK is not set` |
| reconcile-repairs-after-llm-returns | LLM 回来后对账真的重跑（T3） | `ai_review_reconcile_repairs_total=6`（failed 3 + safety 3）、熔断回 0 |
| deps-group-names-every-component | 内网能看到依赖细节（T4） | `/actuator/health/deps` 含 db / redis / elasticsearch / llm 四个具名组件与详情 |
| public-nginx-denies-actuator | 公网只留必要的（T4） | `/actuator/health`=200，`prometheus`/`health/deps`/`env` 全 404 |
| trace-id-visible-to-a-user | 用户拿得到追踪号（T6） | `2f4d842341ac85d0` 同时出现在响应头与应用日志 |

## 三、关键证据（原文）

降级期间的顶层健康，只有依赖层出事时才有的这句话：

```
200 {"description":"dependency unavailable, still serving","status":"DEGRADED","groups":["deps"]}
```

JSON 日志的一行（异步 appender 落到命名卷，MDC 跟着进来）：

```
{"@timestamp":"2026-09-13T13:20:15.499208622+08:00","@version":"1","message":"[NEXUS] ==== Request ====","logger_name":"com.nexus.campus.aspect.LogAspect","thread_name":"http-nio-8080-exec-2","level":"INFO","level_value":20000,"traceId":"53a7e31830dad504","app":"nexus-vibe"}
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

熔断既开路又甩请求：

```
llm_chat_completions_total{application="nexus-vibe",outcome="failure"} 4.0
llm_chat_completions_total{application="nexus-vibe",outcome="circuit_open"} 2.0
```

恢复后的对账（一次 sweep 修 3 条评审 + 3 次安全重检）：

```
13:28:00 [3696fe1d8c04daae] [AI-RECONCILE] Re-triggered 3 stale reviews
13:28:00 [3696fe1d8c04daae] [AI-RECONCILE] Re-running safety check for post 2099005283819819009
```

注意 `3696fe1d8c04daae`：定时任务自己起一个 runId 型 traceId，所以一次 sweep 的所有行能被一个号串起来（T6）。

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

5. **要说清楚：这一节里只有第 1 条是产品的 bug。** 为了拿到 16/16，演练脚本真跑了 8 次：3 次被我主动
   中断（断言还挂在日志措辞上，或 `le=` 因标签顺序取错值），2 次各红 1 到 3 个 Step 且全是脚本自己的错
   （一个漏提交的 helper、一处重复的 `param` 行、`$_.Name` 取了不存在的属性），只有 2 次全绿。
   断言红了先查断言、再查产品，否则演练报告会变成噪声，下次谁也不会再跑它。产品行为在那两次全绿运行里
   逐项一致：3 篇 parked、`429` 3 次、`repairs=6`、公网三探全 404。

## 五、演练没有覆盖的（诚实清单）

- **真实飞书送达**：需要一个可用 webhook。桥的 5 个 Python 单测固定了加签算法与请求体，演练只证明了
  "告警到得了桥、桥失败时会喊出来"。上线前请人工发一条测试告警到群里确认。
- **Prometheus 真的把规则推进 pending/firing**：只核对了规则里用的指标名与标签选择器；规则要 5m/15m
  的持续条件，演练窗口不够。
- **Grafana 界面**：只验证 provisioning 文件挂载与 `/api/health`，没有看面板渲染。
- **`ai_review_lease_attempts_exhausted_total`**：要把一篇帖子的重试预算耗到 5 次才会出现，未演练。
- **日志滚动的量化上限**（单件 100MB / 7 天 / 总量 1GB）：appender 图由
  `LogbackStructuredOutputTest` 断言，本轮没有真的写满 1GB。
- 其它故障形态（磁盘满、OOM、MySQL 主从抖动）不在本轮范围。
- 前端把短编号显示在错误提示里，属于人工验收。

## 六、怎么重跑

```bash
pwsh -File benchmark/observability/drill.ps1              # 建镜像 + 全流程，15–20 分钟
pwsh -File benchmark/observability/drill.ps1 -SkipBuild   # 复用镜像，约 9 分钟
pwsh -File benchmark/observability/drill.ps1 -Keep        # 跑完别拆，留着手查
```

它以 `-p nexus-drill` 独立 project 运行，容器名全部是 `nexus-drill-*`，卷也是自己的一套，
所以能和开发者正在跑的 `nexus-vibe` 栈并存；结束时 `down -v` 只清自己的卷。
每步的原始回答写进 `benchmark/observability/evidence/drill-<时间戳>.log`（该目录已 gitignore），
汇总写进同目录的 `-summary.md`，退出码 0 代表 16 步全过。

脚本里有一个坑值得记：所有 docker 参数都必须以数组形式传入。`Invoke-Compose up -d` 里的 `-d` 会被
PowerShell 的公共参数 `-Debug` 吃掉，于是 `compose up -d` 变成前台运行的 `compose up`，永不返回；
`-v` 同理，被 `-Verbose` 吞掉。
