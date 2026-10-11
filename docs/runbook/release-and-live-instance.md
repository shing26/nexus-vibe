# Release, Rollback, Backup — and the Live Instance

Moved here verbatim from `README.md` during the front-door rewrite
([docs/tickets/readme-open-source.md](../tickets/readme-open-source.md), S4). The content,
the commands and the measured statuses are unchanged; only the location moved, so the README
can stay a front door and this page can stay a runbook.

## Public Deployment (Docker Compose + Cloudflare Tunnel)

不依赖公网 VPS：一台常开的机器（Docker Desktop + cloudflared）即可，无需
公网 IP 或入站端口。

1. 根目录写入 `.env`（参考 `.env.example`）：强随机 `DB_PASSWORD`、`MYSQL_ROOT_PASSWORD`、
   `JWT_SECRET`，以及一个每次发布都变的 `APP_TAG`（`app`/`web` 两个镜像靠它才有可回滚的名字）。
2. `docker compose up -d --build`，拉取模型 `docker compose exec ollama ollama pull qwen2.5:3b`。
3. `cloudflared tunnel login` → `cloudflared tunnel create nexus-vibe` → DNS 路由
   （自有 zone 用 `cloudflared tunnel route dns`，否则 Fork `is-a-dev/register` 加
   CNAME `nexus-vibe -> <TUNNEL_ID>.cfargotunnel.com`）。
4. `cloudflared service install` 注册为 Windows 服务；HTTPS 生效后把仓库
   homepage 指向线上域名。

## Release, Rollback, Backup

| 事项 | 做法 | 状态 |
|------|------|------|
| 发布 | 改 `.env` 的 `APP_TAG` → `docker compose up -d --build` | 演练里真起过两个标签的镜像，A→B→A 两侧 `/api/v1/posts` 都 200（多阶段构建本身由 CI 的 image job 代证，本机演练用的是宿主机产物+同一套运行时层）；没证的是"这套流程在真实公网入口上跑过一次完整发布" |
| 回滚 | `APP_TAG` 改回上一个值 → `docker compose up -d --no-build` | A→B→A 已在演练里跑通：比对两个 tag 背后的 image id，换完读容器自身的 `Config.Image`，`/api/v1/posts` 每侧间隔 12 秒连探两次。2026-09-16 演练量出一条边界——Tomcat 在 `CommandLineRunner` 之前就开始监听，旧构建的样例账号又会在启动器里撞上本轮的 bootstrap `admin`，于是"探活 200、下一个请求连接被拒"；回滚只在数据兼容窗口内成立，而"滚回去救一个坏版本"仍然没有样本 |
| 备份 | `pwsh scripts/backup.ps1 -Destination <第二块盘>`（周计划任务）：mysqldump + uploads 打包，逐件校验明文 SHA-256、gzip 与 dump 结束标记，留 `-Keep` 份 | 2026-09-14 对运行中的栈真跑过，产出可恢复的备份集；**但该"第二卷"与数据库在同一块物理盘上**（本机单 NVMe 分区为 C/D/E），脚本现在会打印并记录 `same-physical-disk` 告警，异地/异盘副本仍未做 |
| 恢复 | [docs/runbook/restore.md](restore.md)，另起 `-p nexus-restore-test` 项目（配 `docs/runbook/docker-compose.restore-test.yml` 改名容器），不碰生产卷 | **已演练**（2026-09-14）：dump 校验和与 manifest 一致、导入退出码 0、10 张表行数逐项相等（32 帖 / 10 用户 / 957 评审日志）、中文标题无乱码、恢复出的应用以 `200` 提供还原后的上传文件且哈希一致。仍未证的是异盘副本；`es-data` 不在任何 dump 里，恢复后必须跑 runbook 第 7 节的全量重建索引（`POST /api/v1/admin/search/reindex`），否则搜索静默返回空 |

## Privacy & Security Notes

- Demo 账号与样例内容同属演示数据：`DEMO_SEED_ENABLED=false`（生产默认）时两者都不写入，库里只有功能性的 `AiAgent(999)` 与你引导出的 `admin`。历史做法是写入随机不可恢复密码的幽灵账号，见 ADR-0008。
- Never commit real credentials: `.env` is git-ignored; `.env.example` ships placeholders only.
- 生产 `/api/demo/**` 默认不可达（`DEMO_ENDPOINTS_ENABLED=false`）。
- 上传只接受 JPG/PNG/GIF/WebP 魔数，扩展名由服务端生成。
- 转发头（X-Real-IP / X-Forwarded-For）仅在有可信反代时被信任（`TRUST_FORWARDED_HEADERS`，prod 默认 true 且 nginx 负责设置 X-Real-IP）。
- This repository intentionally contains no personal data, API keys, or private tokens.

## Running the live instance

The public demo is this repository's `docker-compose.yml` plus an ngrok tunnel. Nothing about
the deployed shape is special-cased: `SPRING_PROFILES_ACTIVE=prod`, MySQL/Redis/Elasticsearch
unpublished, and only nginx reachable from outside.

```bash
# 1. the stack (needs .env: DB_PASSWORD, MYSQL_ROOT_PASSWORD, JWT_SECRET, BOOTSTRAP_ADMIN_PASSWORD, APP_TAG, CORS_ALLOWED_ORIGINS)
docker compose up -d

# 2. the tunnel; scripts/tunnel-ngrok.ps1 refuses to start a second copy for the same domain
pwsh scripts/tunnel-ngrok.ps1
```

**The address and CORS are a pair.** The backend only answers requests whose `Origin` is on
`campus.cors.allowed-origins`, so a new tunnel domain must be added to
`CORS_ALLOWED_ORIGINS` in `.env` and the app restarted, or the SPA will load and every API
call behind it will return 403. That failure mode is silent in the browser console and looks
like a broken site, which is why it is written down here rather than discovered later.

Two hand-offs that are not automated yet:

- **Tunnel at logon.** `scripts/tunnel-ngrok.ps1` runs in the foreground. Making it survive a
  reboot needs a scheduled task, which requires an elevated shell:
  `schtasks /create /tn Nexus-Vibe-Tunnel /sc onlogon /rl highest /tr "ngrok http 8080 --url qualifier-discuss-marry.ngrok-free.dev"`.
- **Log-volume ownership.** A host that already ran this stack before uid 10001 has
  root-owned named volumes. The one-time `chown` is in
  [docs/plans/pre-deployment-checklist.md](../plans/pre-deployment-checklist.md); the image
  falls back to `/tmp` logging and prints a warning if it is skipped.

The database behind the live demo is the long-lived development database, so it contains the
earlier load-test posts and a handful of demo accounts. Clearing it would delete that corpus;
the decision is recorded in the same checklist rather than made implicitly by a script.
