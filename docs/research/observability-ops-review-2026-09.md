# 可观测性与运维配套：外部评审记录（2026-09-13）

方法：把一份外部梳理文档（写于 2026-09-13 02:32，不在本仓库内）的结论
"可观测性与运维配套仍停留在 Demo 级"拆成两个议题，
各交给 `~/.codex/agents` 里对口的角色做**只读**评审
（`engineering-sre.toml` 与 `engineering-devops-automator.toml`），
所有反驳与结论再由我在当前工作树逐条复验。角色给出的判断如果与代码冲突，以代码为准。
落地的任务清单见 [evidence-credibility.md](../tickets/evidence-credibility.md)。

## 一、裁决

| 原结论 | 裁决 | 准确表述 |
|---|---|---|
| 可观测性停留在 Demo 级 | **不成立**（就埋点而言） | 单实例可观测性已到"有埋点、有告警、有演练"的小团队级；真缺口变成了**这套东西从未被 CI 验证过**与**监控系统自身死亡无人知** |
| 运维配套停留在 Demo 级 | **成立** | 构建 7 / 测试 6 / 制品 0 / 发布 0 / 回滚 0 / 备份恢复 0 / 值守 3（角色打分，我复核后无异议） |
| 综合就绪度 60–65% | 偏低，但不可复用 | 数字来自 2026-09-13 02:32 快照，早于 T1–T7 提交；须按 E8 重测，不接受手工上调 |

## 二、我自己的两个错误结论（被角色指出并已由我复验推翻）

1. **"CI 红是因为 CI 上没有活的 LLM，本机的 11434 参与结果"** —— 错。
   [PostControllerIntegrationTest:49](../../src/test/java/com/nexus/campus/controller/PostControllerIntegrationTest.java:49)
   已有 `@MockBean LlmClient`，`:59` 显式打桩 `isHealthy -> true`，真实 client 不参与。
   真实链条是：**调度器在测试上下文里活着** + **健康判定有 5 分钟缓存**。
   `@EnableScheduling` 被声明了两次（`NexusCampusApplication:12`、`WebMvcConfig:25`），
   `AiReviewReconcileTask:90` 的 `0 3/5 * * * ?` 按挂钟触发；
   `agent/LlmHealthCache.java` 用 `volatile boolean` 缓存探测结果 5 分钟，
   而 `@MockBean` 在每个测试方法后被复位，于是落在打桩窗口之外的一次探测会拿到
   Mockito 对未打桩 `boolean` 的默认值 `false`，并把"LLM 不健康"**钉住 5 分钟**；
   这期间 `VibePostServiceImpl:637` 把每一个新帖按 ADR-0004 fail-closed 置为
   `PENDING_REVIEW`，`pinPost:506` 因 `status != 1` 返回 false，
   `PostController:45` 把它映射成 `code: 404` —— 正是 CI 在
   `PostControllerIntegrationTest:456` 看到的断言失败。
   这个机制解释了三个现象：只红一个用例、只改 markdown 的 commit 能从绿翻红、
   我本机连跑三次全绿（没跨过 5 分钟边界）。
   **我原本把它归因为"环境依赖"，实际是"挂钟与缓存时序依赖"，后者更难查。**
2. **"`ci-linux` profile CI 从来没调用过"** —— 错。
   [pom.xml:190-194](../../pom.xml:190) 是 `<activation><os><family>unix</family>`，
   ubuntu runner 裸跑 `mvn -B test` 会隐式命中它。真实问题是：名字撒谎（macOS 也命中）、
   隐式激活不可追，以及 `:202` 的 `<argLine>` 是覆盖语义，将来接 JaCoCo 会静默吃掉 `@{argLine}`。

记录这两条是因为它们本身就是本轮议题的答案：**评审者（含我自己）对"门禁为什么可信"
的检查强度，远低于对"功能有没有实现"的检查强度。**

## 三、复验为真的新发现（原评估文档不知道）

| 发现 | 证据 | 归类 |
|---|---|---|
| 4 条告警规则全部 `noDataState: OK`：app 或 Prometheus 自己死了 = 报健康 | `rules.yaml:52/99/147/194` | 上一轮 P0-3"静默退化"在其修复方案里复现 |
| 监控三件套在 `profiles: ["monitoring"]` 后，默认 `up` 不启动；飞书 webhook 默认空 | `docker-compose.yml:176-238` | 演练时开、平时不开 |
| `app` / `web` 只有 `build:` 没有 `image:` 标签，物理上不可回滚（只有 alert-bridge `:228` 有 tag） | `docker-compose.yml:110/155` | 交付链断层 |
| 全仓无任何 backup / restore / rollback 痕迹（`git grep -i 'mysqldump\|backup'` 空），检查清单也不提数据 | 命令输出 | 单人单盘单实例，唯一毁灭级风险 |
| 公网 `/actuator/health` 与容器 HEALTHCHECK 用同一个 URL、同一套 degraded→200 语义 | `docker/nginx/nginx.conf:77`、`Dockerfile:38` | 一个状态码服务两种问题；LLM 全停时外部拨测报正常 |
| 基线 exposure 改掉 `metrics` 顺手拿走 dev 调试能力，且被测试固化 | `application.yml:181`、`ActuatorMetricsTest:61` | 零收益回退 |
| 门禁验 JDK 18，线上跑 JDK 21 | `pom.xml:22`、workflow `java-version: "18"`、`Dockerfile:17` | 绿标与实际进程不是同一 JVM |
| `mem_limit: 768m` 只套在 app 上 | `docker-compose.yml:144` | 宿主 OOM kill 无信号 |
| `@EnableScheduling` 重复声明 | `NexusCampusApplication:12`、`WebMvcConfig:25` | E1 根因之一 |

## 四、两个角色的分歧，以及我的取舍

- **JDK 收敛到哪。** DevOps 建议 pom 的 `java.version` 与 CI 一起上 21。
  我只把**门禁的 JVM**换到 21，字节码目标仍留 18（E7）。
  决定性事实：本机 JDK 是 18.0.2.1，把 `java.version` 提到 21 会让本地构建直接失败，
  而"CI 跑在真正发布所用的运行时上"不需要改字节码目标就能拿到。
- **非 hermetic 测试修在哪一层。** SRE 主张关测试期调度（`campus.scheduling.enabled`），
  DevOps 主张给 mock 打 safe 裁决或让断言不依赖异步结果。采前者（用户拍板）：
  一次消除"定时任务在测试上下文里跑"这一整类污染，而不是这一条用例。
- **drill 该不该进 CI。** 两边都反对全量搬（SRE：工业界会只留两条不变量；
  DevOps：它依赖本机 daemon，进 CI 只会天天绿）。故不进 CI，E7 里留"最多挑两步"的口子。
- **日志要不要上检索层。** SRE 建议显式接受 1GB/7 天并写进决策记录，而不是补 Loki。采纳（E8 第 3 条）。
- **自造 traceId 是否该被 OTel 替换。** 两边都认为单服务无跨进程跳时是仪式而非能力，暂不换。维持现状 + E2 收尾。
- **404 语义。** `pinPost` 对处于审核中状态的帖返回 404 是不体面的（应为 409），
  但它属于契约/错误码那一轮，混进 E1 只会把红着的门禁再拖一周。明确不做，记在 E8 后的出局表。

## 五、这套改造还没解决的结构性问题（留给下一轮）

错误码体系与 `BusinessException`；集中式 `@RequiresRole`；Flyway 化迁移
（`docker/mysql/migrate-0007-add-review-lease.sql` 至今靠人记得跑过哪个库）；
容器非 root（DevOps 的排序理由：真风险在 rootful 的 ES/ollama 与家机单用户，
一行 `USER` 很便宜但不是本周期的关键路径）；前端崩溃上报；统一重试抽象。

## 六、可对外复述的现状表述

> 全栈交付：9 服务 compose、多阶段镜像、指标/告警/21 步故障演练（`drill-20260914-052504`：21/21 PASS）；
> 发布有具名镜像标签且演练里做过 A→B→A，备份脚本在、恢复尚未彩排。本周期的优先级是把"门禁可信"
> 排在"信号更多"之前——这个顺序由演练暴露的四个自身缺陷验证了：多出来的步骤如果断言错东西，只会多给出错误的绿。
