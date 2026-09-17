> 现场：本机 Docker 29.5.3 上的常开栈 `nexus-vibe`，`alert-bridge` 已按 README 的「接通飞书告警」配好真实
> 飞书 webhook（`docker logs` 里转发目标是 `https://open.feishu.cn/open-apis/bot/v2/hook/***`）。
> 结论：桥对 Grafana 的 `values` 字段假定了错误的形状，**`values.A` 非 0 的告警一律投不出去**；
> 而失败原因只写进 502 响应体，日志里只剩一个状态码，所以它看起来像"偶尔丢一条"。

# 告警桥 `values` 形状事故

## 一、现象

`nexus-prometheus-scrape-failed`（`min_over_time(up{job="nexus-vibe"}[2m])`，`lt 0.5`）在 01:23 触发时投递成功，
01:28 恢复时的 RESOLVED 通知连续三次 502，间隔正好 5 分钟 —— Grafana 的退避重试。

命令：`docker logs -t nexus-alert-bridge | Select-Object -Last 12`

| 时间 | 事件 | 桥的应答 | notify 行 → 应答行 |
|------|------|----------|--------------------|
| 01:22:01 | 手工投递测试（README 第 6 步） | 200 | 2.7 s |
| 01:23:46 | FIRING（真实规则） | 200 | 462 ms |
| 01:28:46 | RESOLVED | 502 | 0.065 ms |
| 01:33:46 | RESOLVED | 502 | 0.081 ms |
| 01:38:46 | RESOLVED | 502 | 0.156 ms |

那一列毫秒数是这条线索的全部价值：一次真的飞书往返要几百毫秒（同一张表里 FIRING 是 462 ms），
所以 502 发生在**任何网络调用之前**。不是飞书拒绝，是桥自己抛了。

Grafana 那侧只留下这一行，`docker logs nexus-grafana` 里没有更多：

```
logger=ngalert.notifier.alertmanager ... msg="Notify for alerts failed"
err="nexus-feishu-bridge/webhook[0]: notify retry canceled due to unrecoverable error after 1
attempts: webhook response status 502 Bad Gateway"
```

原因写在响应体里，而 Grafana 不记录响应体。

## 二、根因

`render_text` 里的一行：

```python
value = ((alert.get("values") or {}).get("A") or {}).get("value")
```

它假定 `values` 是 `{"A": {"value": N}}`。Grafana 的 webhook 实际发的是 **refId → 纯数字**，
取自官方文档正文里的示例载荷（2026-09-17 读的
`grafana.com/docs/grafana/latest/alerting/configure-notifications/manage-contact-points/integrations/webhook-notifier/`）：

```json
"values": {
  "B": 44.23943737541908,
  "C": 1
}
```

于是 `(1 or {})` 得到 `1`，再 `.get("value")` 就是
`AttributeError: 'int' object has no attribute 'get'`，在 `send()` 之前抛出。

**`0` 是唯一能活下来的值**：`(0 or {})` 得到 `{}`，于是被读成"没有读数"，不报错。而这条规则的 firing 值
恰好是 0（`up == 0`），resolved 是 1。所以症状看起来像"恢复通知丢了"，实际上是"`values.A` 非 0 的告警
全都丢" —— 5xx 比率、评审积压、限流速率这几条规则一旦真的触发，A 值都非 0。

不需要飞书就能复现判定逻辑：

```bash
cd docker/observability/alert-bridge
python -c "import alert_bridge as a; print(a.render_text({'state':'ok','title':'t','alerts':[{'labels':{'alertname':'A','severity':'critical'},'values':{'A':1}}]}))"
# AttributeError: 'int' object has no attribute 'get'
```

## 三、三个测试面各自漏掉了什么

1. **单测的载荷是编的。** `GRAFANA_PAYLOAD` 用的是 `{"A": {"value": 1}}`，那是别的发送方的形状，
   不是 Grafana 的。17 条测试因此全绿。
2. **名字最该抓住它的那条测试没碰到出问题的那一行。**
   `test_resolved_state_survives_the_translation` 传的是 `alerts: []`：它断言的是状态前缀，
   而空列表根本不进循环。一条以失败命名的测试够不着失败，比没有测试更糟 —— 它读起来像覆盖。
3. **演练的载荷里没有 `values`。** `drill.ps1` 的 `alert-delivers-to-the-far-side` 自己拼了一个
   Grafana 形状的载荷，但没有这个字段，于是 `alert.get("values") or {}` 拿到 `{}`，一路无事。

三个面都自称覆盖了这条路径，三个面都没有用真实的线格式。

## 四、修法与回归

- `alert_value()` 同时接受两种形状，并且 `0` 返回 `0` 而不是 `None`（0 是读数，不是缺失）；
- 502 分支把原因打进日志。这段代码的注释一直写着"unlogged 502 是告警消失的方式"，
   而它自己就是那个未记录的 502；
- fixture 换成 Grafana 的真形状；新增 `ValueShapeTest` 钉住两种形状、`0`、缺失、未知形状；
- 新增 `RefusalLogTest`：起一个真的 HTTP 服务，断言 502 的原因进了 stdout；
- 演练载荷补上 `values = @{ A = 1 }`，让这一步以后能看见这个类别的回归。

验证：`cd docker/observability/alert-bridge && python -m unittest test_alert_bridge` → 23 条通过。

## 五、这次没有证明的

- **修完之后的真实飞书送达**。本文记录的是判定逻辑与日志，不是"RESOLVED 到了群里"。要确认只能等下一次
  告警恢复，或者按 README 第 6 步手工投一条；本文写到这里时，01:28 那次 RESOLVED 仍未送达。
- 其余规则只从表达式推断"`values.A` 非 0 时走同一条崩溃路径"，没有逐条构造真实载荷跑过。
- `values` 里除 `A` 以外的 refId 仍然不展示。这是有意保留的旧口径，没有证据说明该改成 B 或 C。
- 桥在这一天里有没有丢过别的告警：日志没有留存 01:21 之前的记录，也没有别的地方记录过 502 的次数。
