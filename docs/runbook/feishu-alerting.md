# 接通飞书告警

Moved here verbatim from `README.md` during the front-door rewrite
([docs/tickets/readme-open-source.md](../tickets/readme-open-source.md), S4). The content,
the commands and the expected outputs are unchanged; only the location moved, so the README
can stay a front door and this page can stay a runbook.

只动 `.env` 和飞书客户端，不动代码。每一步的预期输出都列出来了，因为这条链路上"HTTP 200"并不
代表消息送达；六步做完会真在群里出现一条消息。

1. 飞书群里 **设置 → 群机器人 → 添加机器人 → 自定义机器人**，填名称后添加，复制弹出的 webhook 地址
   （形如 `https://open.feishu.cn/open-apis/bot/v2/hook/xxxxxxxxx`）。
2. 点群名右侧的机器人图标进详情页 → **安全设置 → 签名校验** → 复制密钥 → **保存**。
   **只开这一个**：桥接只实现签名校验，配成"自定义关键词"会被 `19024` 拒、"IP 白名单"会被 `19022` 拒。
3. `.env` 里填两个值，`=` 两边不留空格、值不加引号：

   ```bash
   FEISHU_ALERT_WEBHOOK=https://open.feishu.cn/open-apis/bot/v2/hook/<token>
   FEISHU_ALERT_SECRET=<签名密钥>
   ```

4. 重建。改完 `.env` 用 `docker restart` 不会重读环境变量，必须 `up -d`：

   ```bash
   docker compose --profile monitoring up -d alert-bridge
   docker logs --tail 20 nexus-alert-bridge
   # 期望：listening on 8080, forwarding to https://open.feishu.cn/...***
   ```

5. 先只验配置，不发消息：

   ```bash
   docker exec nexus-alert-bridge python -c "import urllib.request;print(urllib.request.urlopen('http://localhost:8080/').read().decode())"
   # {"status": "ok", "forwarding": true}
   ```

6. 真发一条，走完整签名路径（从 `app` 容器打，顺带证明跨容器网络通）：

   ```bash
   docker exec nexus-app curl -sS -X POST http://alert-bridge:8080/notify \
     -H 'Content-Type: application/json' \
     --data-binary '{"state":"alerting","title":"Nexus-Vibe delivery check","alerts":[{"labels":{"alertname":"ManualDeliveryTest","severity":"warning"},"values":{"A":1},"annotations":{"summary":"signing and delivery"}}]}'
   ```

   桥接把飞书的**原始应答**原样回传，所以判定看响应体里的 `code`、不看 HTTP 状态：
   `{"code":0,"msg":"success",...}` 才算送达，同时群里应出现
   `[Nexus-Vibe] alerting: Nexus-Vibe delivery check`。

   这条示例刻意只用 ASCII。Windows PowerShell 5.1（`powershell.exe`）会把原生命令参数按 ANSI 代码页
   编码，正文里的中文到群里会变成 `?`——2026-09-17 的一次演练就在日志里留下过 `Nexus-Vibe ??????`。
   要发中文就用 PowerShell 7（`pwsh`）或别的 UTF-8 shell。`values` 是有意带上的：它是一个非 0 的读数，
   正是 2026-09-17 那次投递失败的形状，见
   [alert-bridge-values-shape-2026-09.md](../research/alert-bridge-values-shape-2026-09.md)。

收件人本身在 `docker/observability/grafana/provisioning/alerting/contact-points.yaml` 里版本化，
下面这条查的是告警引擎**真正加载**的内容（provisioning API），不是文件列表：

```powershell
$pw = (Select-String -Path .env -Pattern '^GRAFANA_ADMIN_PASSWORD=(.*)$').Matches.Groups[1].Value
$auth = 'Basic ' + [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes("admin:$pw"))
docker exec nexus-grafana wget -qO- "--header=Authorization: $auth" http://localhost:3000/api/v1/provisioning/contact-points
# 应含 "name":"nexus-feishu-bridge" 与 "url":"http://alert-bridge:8080/notify"
```

出问题时先看容器日志和响应体里的 `code`：

| 现象 | 含义 | 处理 |
|------|------|------|
| 容器持续 `Restarting`，日志 `FEISHU_ALERT_WEBHOOK is not set` | 配置没生效 | 确认改的是仓库根 `.env`、命令在仓库根执行、用的是 `up -d` 而不是 `restart` |
| `code=19021 sign match fail or timestamp is not within one hour` | 密钥错，或容器时钟偏离真实时间超 1 小时 | 先重抄密钥；再 `docker restart nexus-alert-bridge`（Docker Desktop 宿主机休眠唤醒后常踩这条） |
| `code=19024 Key Words Not Found` | 群机器人配的是"自定义关键词" | 改成"签名校验"，或把 `Nexus-Vibe` 加进关键词 |
| `code=19022 Ip Not Allowed` | 群机器人配的是 IP 白名单 | 把出口 IP 加进白名单，或关掉该项 |
| `code=9499 Bad Request` | 请求体超 20 KB 或格式错 | 收敛告警条数；contact point 已把 `maxAlerts` 限到 10 |
| `code=11232` | 触发限流（100 次/分、5 次/秒） | 整点与半点更容易撞，收敛或加抑制 |
| 502 且正文 `forward to ... failed` | 解析出来的地址**全部**连不上（DNS / 代理 / 断网） | 在容器内确认 `open.feishu.cn` 可达。单个地址不通不会走到这里：桥接逐个地址重连，`2026-09-17` 起 20 个 A 记录里死掉一个不会再丢告警，见 [alert-bridge-address-fallback-2026-09.md](../research/alert-bridge-address-fallback-2026-09.md) |
| HTTP 200 但群里没消息 | 只看了状态码，没看应答体 | 读返回体的 `code`——这正是桥接存在的理由 |

安全设置的取舍、上表的错误码，以及"时间戳 1 小时内 / 请求体 20 KB / 100 次每分"这三条限制，
都来自飞书官方文档[自定义机器人使用指南](https://open.feishu.cn/document/client-docs/bot-v3/add-custom-bot)
（该页有 `.md` 后缀的纯文本版）。第 6 步之前的每一步都能在容器里自证；唯一自证不了的是"消息真的
到了飞书群"，那只能人工看。
