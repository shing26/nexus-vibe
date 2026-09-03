# 容器 JVM GC 分析：768m 限制 + MaxRAMPercentage=75 下的压测表现

> 采集于异步管线压测期间（见 [async-pool-loadtest.md](async-pool-loadtest.md)），负载：50 并发发帖 +
> 10 并发列表查询，持续约 6 分钟，JDK 21 / G1GC。
> JVM 配置：`-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags:filecount=5,filesize=10m`，
> 容器 `mem_limit: 768m`（堆上限 ≈ 576m）。

## GC 数据（压测全程 3.9 万行日志）

| 指标 | 数值 |
|---|---|
| GC 事件 | 2,477 次 Young GC，**0 次 Full GC** |
| 总停顿 | 12.5 秒（占 361s 压测时长的 3.5%） |
| Young GC 停顿 | mean 5.0ms / p90 6.7ms / p99 21.8ms / max 358ms |
| 堆形态 | G1 自适应堆在 13M–189M 间伸缩，压测期典型水位 170M（远低于 576m 上限） |
| 混合/并发标记 | 无（堆未达到 IHOP 阈值，G1 只做 Young 回收） |

## 结论

1. **内存不是本负载的瓶颈**：同步链路（发帖/查询）在 60 线程下堆水位稳定在 ~170M，GC 占用 CPU 3.5%，停顿 p99 22ms 对 API 延迟无感。压测观察到的一切饱和都来自 LLM 吞吐与线程池，与内存无关。
2. **MaxRAMPercentage=75 的依据**：容器 768m 中堆占 576m（75%），余量留给 metaspace（~80M）、线程栈（60 线程 × ~1M）、直接缓冲与 JIT 代码缓存。压测证实堆实际用量远低于上限——这个配置**保守但正确**，为流量增长留了 3 倍堆余量而无需改动。
3. **唯一一次 358ms 停顿**发生在队列打满、万级请求并发解析的瞬间，属正常演化停顿范畴；若未来出现规律性长停顿，下一步是开启 `-XX:+PrintStringDeduplication` 与 `gc+heap=info` 级日志定位区域，而不是调堆。
4. **无需进一步调优**——如实记录。这个结论本身就是分析产出：参数不是"越多越好"，而是"有依据的最小集合"。

## GC 日志配置说明

`filecount=5, filesize=10m` 旋转保留 50MB 日志，防止长期运行写满容器可写层。日志文件在容器内 `/tmp/gc.log*`，采集：`docker cp nexus-app-lt:/tmp/gc.log .`。

## 复现

```bash
# compose 的 app 服务已内置 JAVA_OPTS 与 mem_limit
docker compose up -d db redis && docker compose run -d --name app-lt ... # 见压测报告
# 压测后取日志
docker cp app-lt:/tmp/gc.log . && grep -c "Pause Young" gc.log
```
