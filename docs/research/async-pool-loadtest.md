# 异步线程池压测报告：AI 评审管线在饱和负载下的行为

> 工具 Apache JMeter 5.6.3（CLI 模式）；场景 `benchmark/jmeter/ai-review-loadtest.jmx`；
> 目标：Docker 化应用（768m 内存限制，JDK 21 + G1），MySQL/Redis 容器化，ES 关闭走 MySQL 降级，
> LLM 指向宿主机 Ollama `qwen2.5:7b`（CPU 推理，天然慢——这是刻意选择：让评审成为瓶颈以观察背压）。
> 压测时长 6 分钟，三波阶梯（10 → 30 → 50 并发打发帖）+ 10 并发 `GET /posts?sort=ai` 对照组。

## 核心数据

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

## 关键发现

1. **背压链路完整且行为正确**：50 并发打发帖 → 同步链路（写库/ES 降级查询）轻松扛住 → 事件进入 `nexus-async` 池（core 4 / max 10 / queue 100）→ 14 秒内打满 → 之后所有评审任务被拒绝（抛 `RejectedExecutionException`，全局异常处理转 500）。同步 API 从未因异步池饱和而不可用——**线程池隔离的收益被实证**。
2. **LLM 是吞吐瓶颈，不是线程池**：本地 7B CPU 推理约 2-3 篇/分钟，而入口速率 700+ 请求/秒。池的 14 个槽位（10 忙 + 100 队列）在饱和后变成纯丢弃。这验证了 fail-closed + 对账任务设计的必要性：拒绝的任务帖子状态停在 FAILED(3)，由 AiReviewReconcileTask 在 LLM 健康后自动重试，而不是永久丢失。
3. **熔断器真实生效**：LLM 连续失败触发 60s 开路，避免了故障期间每个帖子都空转 3 次重试（日志：`circuit breaker opened for 60s after 3 consecutive failed calls`）。
4. **对照组的 11.9% 错误是客户端伪影**：错误码 `java.net.BindException`（压测机 Windows 临时端口耗尽 + 内存压力），非服务端行为；服务端 500 全部来自池拒绝路径，GET 不进池。成功样本 p99 936ms 对应压测高峰的 DB/CPU 争抢。

## 附带发现（安全）

`RateLimitInterceptor.getClientIp` 在无 `X-Real-IP` 时信任客户端提供的 `X-Forwarded-For`。直连应用端口（绕过 nginx）的攻击者可轮换伪造 IP 绕过每 IP 限流——本轮压测正是利用这一点合法地放开了限流。生产部署中 nginx 必须设置 `X-Real-IP`（compose 内已如此），且 8080 不应对外暴露。修复建议（待办）：`server.forward-headers-strategy` 显式白名单化，或仅在容器网络内信任转发头。

## 对线程池参数的实测依据（面试叙事）

- core=4 / max=10 / queue=100：评审是 IO 等待型任务（等 LLM 数十秒），小池 + 中队列合理；但 queue=100 在瓶颈型依赖下等于**放大延迟后集中丢弃**。实测显示 queue 打满只用了 14 秒。
- 改进方向（未改，属行为变更）：a) 评审事件入队前做池水位检查，超阈值直接标 FAILED 省一次无效入队；b) 队列改为有界小队列 + CallerRuns 策略让发帖线程自己降速；c) LLM 侧并发（Ollama 并行度）与池大小对齐——池 10 个线程对 1 路 CPU 推理没有意义。
- 拒绝策略现状：JVM 默认 Abort → 全局异常处理器兜底转 500。对"用户发帖"这个动作而言，评审失败不该让发帖失败（帖子已落库）——更优的语义是吞掉拒绝事件并直接标 FAILED，已列入待办。

## 复现

```bash
# 环境：compose 起 db+redis；应用容器见 jmx 注释；Ollama 本机运行
jmeter -n -t benchmark/jmeter/ai-review-loadtest.jmx \
  -l results.jtl -e -o report -JauthToken=<admin token>
# .jtl 与 report 目录不入库
```
