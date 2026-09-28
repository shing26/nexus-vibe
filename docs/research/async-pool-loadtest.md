# 异步线程池压测报告：AI 评审管线在饱和负载下的行为

> 工具 Apache JMeter 5.6.3（CLI 模式）；场景 `benchmark/jmeter/ai-review-loadtest.jmx`；
> 目标：Docker 化应用（768m 内存限制，JDK 21 + G1），MySQL/Redis 容器化，ES 关闭走 MySQL 降级，
> LLM 指向宿主机 Ollama `qwen2.5:7b`（CPU 推理，天然慢——这是刻意选择：让评审成为瓶颈以观察背压）。
> 压测时长 6 分钟，三波阶梯（10 → 30 → 50 并发打发帖）+ 10 并发 `GET /posts?sort=ai` 对照组。

> **2026-09-21 起，数字由脚本产出而不是手抄。** `benchmark/jmeter/run-loadtest.ps1` 驱动同一个
> `.jmx`，把 `.jtl` 解析成 `benchmark/jmeter/results/loadtest-<日期>.json|md`。下面先给可复现的那一次，
> 再给 2026-09-14 的历史值——**两者不可比**，原因在"为什么两次跑出的形状不同"一节。

## 核心数据（2026-09-21，脚本产物）

来源：`benchmark/jmeter/results/loadtest-20260921.md` 与同名 `.json`，由
`pwsh -File benchmark/jmeter/run-loadtest.ps1 -BasePort 18080 -ComposeProject nexus-loadtest` 落盘。
目标是一次性 compose 项目（`benchmark/jmeter/docker-compose.loadtest.yml`），LLM 指向本机 Ollama；
本机只装了 `qwen2.5:3b`，不是历史那次的 7B。速率限制器在这一次运行里是**关闭**的（该 override 排除了
Redis 自动配置），否则 `POST /posts` 会被封顶在 10 次/分钟/IP，量到的就不是线程池。

| 指标 | 数值 |
|---|---|
| 总样本 | 25,204 |
| 错误 | 13（0.1%） |
| 延迟 p50 / p90 / p99（全体） | 37 / 1,770 / 3,353 ms |
| `POST /posts` 请求 | 6,288，其中 13 个返回 500（**归因未定**，见下方"更正"），成功样本 p50 1,684ms / p99 4,293ms |
| 对照组 `GET /posts?sort=ai` | 18,915，错误率 0%，成功样本 p50 29ms / p99 890ms |
| 429（限流） | 0 |
| 落库终态 | 发帖 6,279 篇：6,231 FAILED(3)、36 NOT_REVIEWED(0)、10 REVIEWING(1)、2 已评审(2) |
| 服务端崩溃 | 0 |

> **更正（2026-09-23）。** 这份摘要最初把上面那 13 个 500 记成"池拒绝路径"。那是错的，而且
> 是这一轮自己抓出来的：在一次性 compose 项目上重跑，日志里出现数千条 `AI review queue saturated`
> 拒绝、**每一条都回了 200**——因为 `PostAgentEventPublisher` 捕获拒绝、把帖子标成 FAILED 交给对账，
> 不会把异常抛回 HTTP 层。两次更短的复跑（2,405 与 17,310 样本）都是 **0 个 500**，所以这 13 个
> 至今没有复现、也没有归因；机器可读摘要里的 `serverErrors.note` 记了同样的话。那两次复跑的产物是
> `benchmark/jmeter/results/diagnose-20260923.{json,md}` 与 `diagnose2-20260923.{json,md}`。
>
> 同一次复跑抓到了另一个**真实**缺陷：agent 池打满时，`AiReviewReconcileTask` 的重触发循环会把
> `TaskRejectedException` 抛出方法，整个对账批次随之中断（日志里表现为 `Unexpected error occurred in
> scheduled task`）。已修，见 `docs/tickets/like-count-convergence.md`。

## 核心数据（2026-09-14，手工全量跑，**没有提交产物**）

> 这一节的数字来自当时终端里的手工统计，没有对应的 `.jtl` 或摘要文件入库。保留它是为了记录问题的
> 演化，**不要把它当作当前构建可复现的性能指标**；理由见下一节。

| 指标 | 数值 |
|---|---|
| 总样本 | 201,880 |
| POST /posts 请求 | 146,691（成功 1,472） |
| 对照组 GET /posts?sort=ai | 55,188，错误率 11.9%（见下文归因），成功样本 p50=19ms、p99=936ms |
| 成功发帖延迟 | mean 154ms / p50 121ms / p99 624ms |
| 池拒绝（ExecutorService rejected） | **121,202 次**，全部发生在 pool=10 + queue=100 打满之后 |
| 熔断器 | LLM 连续失败 3 次 → 开路 60s，日志可见 |
| 落库终态 | 发帖 1,522 篇：1,515 篇 FAILED(3)、3 篇已评审、4 篇评审中——与 LLM 吞吐匹配 |
| 服务端崩溃 | 0（拒绝即返回 500，HTTP 层保持响应） |

## 为什么两次跑出的形状不同

两次都证明同一件事——池饱和后评审任务被拒绝、HTTP 层保持响应、同步 API 不受异步池拖累——但绝对数字**不能互相比**，因为三个环境变量同时变了：

1. **入口限流**。2026-09-14 那次 700+ 请求/秒的入口，靠的是压测脚本旋转 `CF-Connecting-IP`（nginx 当时优先信任该头），让每个请求落进一个新的限流桶。这个头在 2026-09-17 的审计里被判定为可伪造并修复（见"附带发现"一节），所以**同样的旋转今天不再有效**，121,202 次池拒绝无法原样复现。2026-09-21 这次换了个更干净的做法：一次性 compose 项目直接排除 Redis 自动配置（`benchmark/jmeter/docker-compose.loadtest.yml`），限流器整体不生效——这是写在实验设定里的条件，不是被绕过的生产配置。
2. **模型**。历史那次是 `qwen2.5:7b`；本机现在只装了 `qwen2.5:3b`。评审吞吐不同，落库终态的比例自然不同。
3. **同步 POST 延迟**。历史那次成功发帖 mean 154ms；2026-09-21 这次 `POST /posts` 成功样本 p50 就是 1,684ms。阶梯是**固定线程数**（10 → 30 → 50），每个线程能发出的请求数正比于 1/延迟，延迟差一个数量级，请求总数就差一个数量级（146,691 vs 6,288）——这一条与"限流是否打开"无关。

结论：2026-09-21 的产物是可复现的**当前**基线；2026-09-14 的表格只作为问题演化记录，引用时必须带上"未提交产物、依赖已修复的绕过手法"这两个限定，不能拿来当性能指标。

## 关键发现

下面 1/2/4 的**机制**在两次运行里都成立；带具体数值的句子都标了它来自哪一次，两次的数字不可互换（见上一节）。池参数 core 4 / max 10 / queue 100 来自 `src/main/resources/application.yml:20-22`，两次相同。

1. **背压链路完整且行为正确**：50 并发打发帖 → 同步链路（写库/ES 降级查询）扛住 → 事件进入 `nexus-async` 池 → 打满 → 之后的评审任务被拒绝。发布点捕获这个拒绝（`PostAgentEventPublisher` 把帖子标 FAILED 交给对账，安全侧 fail-closed 到 PENDING_REVIEW），所以**发帖本身不会被异步池的饱和拖成 500**——2026-09-23 的复跑里数千次拒绝全部回了 200，这一条从"推断"变成了"量到"。摘要里那 13 个 500 与池拒绝无关，见上方更正。
2. **LLM 是吞吐瓶颈，不是线程池**：2026-09-14 那次量到本地 7B CPU 推理约 2-3 篇/分钟、入口速率 700+ 请求/秒（均取自本文件的历史表，未提交产物）；2026-09-21 用 3b 模型时，6,288 个 POST 里仍有 6,231 篇停在 FAILED(3)，说明瓶颈依旧在评审吞吐而非池本身。池的槽位（10 忙 + 100 队列）在饱和后变成纯丢弃，这验证了 fail-closed + 对账任务设计的必要性：拒绝的帖子状态停在 FAILED(3)，由 `AiReviewReconcileTask` 在 LLM 健康后自动重试，而不是永久丢失。
3. **熔断器真实生效**（2026-09-14 记录）：LLM 连续失败触发 60s 开路，避免了故障期间每个帖子都空转 3 次重试（日志：`circuit breaker opened for 60s after 3 consecutive failed calls`）。
4. **对照组的错误是客户端伪影**（2026-09-14 记录）：当时 11.9% 的错误码是 `java.net.BindException`（压测机 Windows 临时端口耗尽 + 内存压力），非服务端行为；服务端 500 全部来自池拒绝路径，GET 不进池。2026-09-21 这次对照组干净得多——`GET /posts?sort=ai` 18,915 样本 0 错误、p99 890ms（见同名摘要 `byLabel`），没有复现端口耗尽。

## 附带发现（安全）

2026-09-17 的上线前审计进一步证明，这条当时的“nginx 已缓解”结论并不成立：nginx 曾优先信任客户端可伪造的
`CF-Connecting-IP`，在 ngrok 入口上旋转该头即可让每次请求落入新的限流桶。修复已完成：nginx 不再读取该头，
`X-Real-IP` 与 `X-Forwarded-For` 都改为写入 `$remote_addr`；`RateLimitInterceptor` 在有可信代理时也只接受
nginx 覆盖后的 `X-Real-IP`，缺少它时回退 socket 地址，不再信任任何客户端可追加的 XFF 段。对应的头轮换回归测试
位于 `RateLimitInterceptorTest`。这里保留原始压测结论作为问题演化记录，但它不再描述当前生产配置。

## 对线程池参数的实测依据

- core=4 / max=10 / queue=100：评审是 IO 等待型任务（等 LLM 数十秒），小池 + 中队列合理；但 queue=100 在瓶颈型依赖下等于**放大延迟后集中丢弃**。2026-09-14 那次实测显示 queue 打满只用了 14 秒（历史记录，未提交产物）。
- 改进方向（未改，属行为变更）：a) 评审事件入队前做池水位检查，超阈值直接标 FAILED 省一次无效入队；b) 队列改为有界小队列 + CallerRuns 策略让发帖线程自己降速；c) LLM 侧并发（Ollama 并行度）与池大小对齐——池 10 个线程对 1 路 CPU 推理没有意义。
- 拒绝策略现状：JVM 默认 Abort，但**异常不会到达全局处理器**——`PostAgentEventPublisher` 在发布点就捕获它（评审侧标 FAILED 交对账，安全侧 fail-closed 到 PENDING_REVIEW），所以帖子已落库就不会因为评审入队失败而回 500。这一条曾是"待办"（旧文写"更优的语义是吞掉拒绝事件并直接标 FAILED"），现在代码就是这么做的，2026-09-23 的复跑量到数千次拒绝全部 200。真正缺的是**对账任务自己的**发布点，它当时没有这层捕获，会把整个批次抛出方法——已修。

## 复现

一条命令产出 2026-09-21 那份摘要：`benchmark/jmeter/run-loadtest.ps1` 起一次性 compose 项目（`nexus-loadtest`，排除限流器），跑 `ai-review-loadtest.jmx`，把 `.jtl` 解析成 `benchmark/jmeter/results/loadtest-<日期>.json|md`，再读一遍落库终态。

```powershell
# 前提：本机 Docker 在跑，且本机 Ollama 提供 LLM。六分钟，不进 CI。
pwsh -File benchmark/jmeter/run-loadtest.ps1 `
  -BasePort 18080 -ComposeProject nexus-loadtest `
  -AdminPassword $env:BOOTSTRAP_ADMIN_PASSWORD

# 只做一次 90 秒的预演，不上完整阶梯：
pwsh -File benchmark/jmeter/run-loadtest.ps1 -Wave1Seconds 60 -Wave2Seconds 30 `
  -Wave3Seconds 30 -ListSeconds 90 -Wave2Delay 20 -Wave3Delay 40 -Tag rehearsal
```

`.jtl` 与 JMeter 的 HTML report 留在本机（`.gitignore` 挡掉 `benchmark/jmeter/**/*.jtl` 与 `*-report/`）；`results/` 下的两个小文件才是入库的产物，它们各自写明了产生它的命令与日期。裸跑 `jmeter -n -t benchmark/jmeter/ai-review-loadtest.jmx -l results.jtl -e -o report` 仍然可用，但那样得到的数字不会自动带来源。
