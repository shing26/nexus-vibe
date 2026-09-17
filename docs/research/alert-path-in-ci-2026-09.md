> 现场：本机 Docker 29.5.3，2026-09-17。CI 侧加在 `.github/workflows/maven.yml` 的 `docker` job 里，
> 跑的是 `benchmark/observability/drill.ps1` 的**同一步骤体**，只是用新的 `-Only` 筛出三步。
> 结论：**演练里三步进了 CI**（限流打满 → 逐条规则 no-data 策略 → 规则选的指标在本 build 里真的存在），
> 两次人为破坏规则都让 CI 变红。**回滚那一步没有进 CI**，原因写在第五节，没有用"换个 label 造两个
> 不同 image id"之类的手法糊过去。

# 告警链路进 CI

## 一、进了哪三步，为什么是这三步

OPS-3 要的是"不需要真 LLM、不需要长等待"的那几步。按这条筛下来剩三步，**还必须按这个顺序跑**：

| # | 步骤 | 需要的容器 | 耗时量级 |
|---|------|------------|----------|
| 1 | `rate-limit-rejects-and-counts` | app、redis | 秒级（13 次登录） |
| 2 | `alert-no-data-policy-is-per-rule` | 无（只读 `rules.yaml`） | 毫秒级 |
| 3 | `alert-rules-select-real-metrics` | app | 秒级 |

第 3 步是唯一一件**单测做不到的事**：它把 app 自己的 `/actuator/prometheus` 读回来，逐条检查
`rules.yaml` 的表达式选中的指标名在这个 build 里真的存在。这条断言抓到过真 bug —— `post.created`
按 Prometheus 命名约定导出成了 `post_total`，四条单测全绿也看不见，因为 `SimpleMeterRegistry`
原样保留 meter 名。

## 二、撞出来的前置条件（第 1 步不是赠品）

只跑第 3 步会红：

```
$ pwsh -File benchmark/observability/drill.ps1 -SkipBuild -Keep -Only alert-no-data-policy-is-per-rule,alert-rules-select-real-metrics
[FAIL] alert-rules-select-real-metrics
    alert expressions select on metrics that do not exist: rate_limit_rejected_total
```

不是规则错了，是**这个计数器在没有人被拒之前根本没有序列**。在完整演练里，
`rate-limit-rejects-and-counts` 排在它前面，所以这条从不显形。加上第 1 步之后：

```
23 steps, 3 run (20 skipped by -Only), 0 failed
```

所以 `-Only` 的取值列表里第 1 步是**前置条件**。这跟"跑三步看起来更全"无关：少了它，
第 3 步会在一个健康的栈上失败，而那种失败最容易被人用"把这条注释掉"修掉。

## 三、这套门禁真的会红吗（两次人为破坏）

**破坏一：把规则表达式里的指标名改错。** `rules.yaml` 里
`rate_limit_rejected_total` → `rate_limit_rejected_typo`：

```
    FAIL  alert expressions select on metrics that do not exist: rate_limit_rejected_typo
[FAIL] alert-rules-select-real-metrics
23 steps, 2 run (21 skipped by -Only), 1 failed
exit=1
```

**破坏二：把某条规则的 no-data 策略改回"KPI 上好看"的那个值。**
`nexus-llm-breaker-open` 的 `noDataState: Alerting` → `OK`（这条规则看的是一个进程启动时就注册的
gauge，"取不到数"就是"进程没了"）：

```
    FAIL  nexus-llm-breaker-open reports noDataState OK, expected Alerting
[FAIL] alert-no-data-policy-is-per-rule
23 steps, 1 run (22 skipped by -Only), 1 failed
exit=1
```

两次都恢复原状（`git status` 干净）后才继续。`-Only` 收到一个不存在的步骤名时会 `exit 2` 而不是
静默跑零条断言 —— 否则步骤改名会让门禁变成一个"全绿的、什么都没跑"的东西。

## 四、CI 里起的是哪些容器

`-Only` 不含 `stack-up` 时，演练**既不 build 也不 up**，把这两件事交给调用方：这正是让 CI 只起
`db` + `redis` + `app` 的办法（`--no-deps` 跳过 app 声明的 Elasticsearch / Ollama 依赖，
ADR-0007 让它们的缺席是 DEGRADED 而不是致命）。三个容器就够，因为第 3 步是从 **app 容器内部**
读它自己的指标端点，不需要 Prometheus。

本机按同一条路径验过：`up -d --no-deps db redis app` 之后 app 在第 2 次探测就答了
`/actuator/health` 200。

`.env` 不在 git 里，而 app 声明了 `env_file: .env`，所以 CI 那一步由 `.env.example` 复制一份再改
四个值。**这顺带变成一条断言：`.env.example` 必须仍然是一份能用的配置。**

`docker-changes` 的路径过滤器补了三个目录（`benchmark/observability/**`、
`docker/observability/grafana/provisioning/**`、`docker/observability/alert-bridge/**`）。不补的话，
改一条告警规则的 PR 会**跳过整个 job**，然后因为"什么都没跑"而显绿 —— 这个 workflow 里已经出现过
两次的失败形状。

## 五、这次没有证明的

- **回滚那一步没有进 CI。** `rollback-swaps-between-two-real-image-tags` 自己就拒绝两个 tag 指向
  同一个 image id，理由写在它的注释里：那只能证明"compose 会做字符串插值"。在 CI 里造出两个**真的**
  不同构建，意味着再 checkout 一次基线提交、再跑一遍 Maven 阶段、再起一套 compose，这是另一笔预算；
  用改 label 或改无关 ARG 的办法凑出两个不同 id，等于把这条断言要防的东西重新放回去。
  所以它保持手工，理由是"没做"，不是"做不了"。
- **本机测不了 CI 那一段的镜像构建**：这台机器取不到 `registry-1.docker.io`（
  `maven:3.9-eclipse-temurin-21` manifest 超时），所以本机验的是"compose 起三个容器 + 筛选后的
  三步"，镜像构建那一段由 CI 自己证明。这与仓库对 app / web 镜像的既有口径一致。
- 这里跑的是**没有 ES / Ollama 的形态**。第 3 步只读指标名，不依赖那两个依赖，但"完整 prod 形态下的
  这一步"仍然只有本地演练在跑。

## 六、第一次真跑（2026-09-17，run 35175845366）

这一段本来写的是"耗时还没实测"，PR 打开后就成了实测值，所以把它换掉而不是留着一句过时的免责：

```
drill app answered /actuator/health on attempt 3
    PASS  3 of 13 refused, rate_limit_rejected_total=3
    PASS  6 rules, 6 expressions, metrics named: llm_circuit_breaker_open, ai_review_pending_posts,
          http_server_requests_seconds_count, rate_limit_rejected_total
    PASS  nexus-llm-breaker-open=Alerting  nexus-ai-review-backlog=Alerting  nexus-http-5xx-ratio=OK
          nexus-rate-limit-spike=OK  nexus-prometheus-scrape-failed=Alerting  nexus-availability-999-fast-burn=OK
23 steps, 3 run (20 skipped by -Only), 0 failed
```

三个容器从 `compose up` 到 app 答话用了 3 次探测（约 25 秒），三步断言本身 **20 秒**，
整个 `docker` job **2 分 34 秒** —— 与加这一段之前的 2 分 39 秒 / 2 分 59 秒 / 3 分 02 秒同量级，
也就是说这段挤进的是缓存命中的空隙，不是十分钟预算的边角。
