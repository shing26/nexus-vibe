# Nexus-Vibe 部署 + 技术博客计划

> 状态：代码与配置优化已完成，本地全栈 Docker Compose 已跑通（2026-08-15）；部署方式改为 Cloudflare Tunnel 自托管，部署仍未执行，等待用户确认。
> 说明：Oracle Cloud 注册未通过，改走本机 Docker Compose + Cloudflare Tunnel，不依赖公网 VPS。

## Summary

- 基线（优化前）：`mvn test` 已实测 167 全绿，前端构建已实测通过；部署前优化与体验报告修复后 `mvn test` 188 全绿，前端 build/lint 通过。
- 目标：修复全部部署阻断项和建议项，用 Docker Compose 部署到常开的 Windows 本机（Docker Desktop + WSL2），通过 Cloudflare Tunnel 公开 `https://nexus-vibe.shing26.is-a.dev`，无需公网 IP 也不开放入站端口；App 使用 Compose 内 Ollama（Windows CPU 推荐 `qwen2.5:3b`，算力足够再换 `qwen2.5:7b`）做 LLM 审核/评审。
- 交付物：可公开访问的线上 Demo、仓库内中文技术博客《给论坛接入 LLM 代码评审：结构化输出与注入防御实战》、README 可点击链接，并把仓库 homepage 指向线上域名。

## Key Changes

- 上传安全：`UploadController` 改为扩展名白名单（`jpg/jpeg/png/gif/webp`）+ 魔数嗅探，不再信任客户端 `Content-Type`；文件名一律 `UUID + 白名单扩展名`；伪造 `x.html` 且声明 `image/png` 必须返回 400。
- 上传可访问与持久化：nginx 增加 `location ^~ /uploads/ { proxy_pass http://nexus_app; }` 并加一年缓存头；compose 为 app 挂命名卷 `app_uploads:/app/uploads`。
- 暴露面收敛：compose 删除 MySQL/Redis/ES 的宿主端口映射，仅 web 保留 `${WEB_PORT:-8080}:80`；Cloudflare Tunnel 只建立出站连接，防火墙无需开放入站端口；prod 禁用 springdoc 与 Swagger，health `show-details: when-authorized`；`DemoShowcaseController` 加 `@Profile("!prod")`。
- CORS 与限流：CORS 通过 `campus.cors.allowed-origins` 注入，prod 默认 `https://nexus-vibe.shing26.is-a.dev`；`RateLimitInterceptor` 优先信任 nginx 写入的 `X-Real-IP`，XFF 只取最右段，防止客户端伪造。
- 配置与镜像：`.env.example` 改为 Ollama 内网默认（`http://ollama:11434/v1` / `qwen2.5:7b`），明确 `DEMO_PASSWORD`、`JWT_SECRET` 生产必填；保留 prod fail-fast；Dockerfile 统一 `SPRING_PROFILES_ACTIVE=prod`、`LABEL version=1.0.0`。
- 新增 Ollama 服务：compose 增加 `ollama/ollama` + 命名卷，不暴露宿主端口；App 通过 compose 内网调用，模型由 `docker compose exec ollama ollama pull qwen2.5:7b` 拉取。
- 文档与曝光：README 改为 188 tests、生产密码来自 `DEMO_PASSWORD`、补 Cloudflare Tunnel 自托管部署步骤，并加线上链接；新增 `docs/blog/llm-code-review-structured-output-injection-defense.md`。
- 体验报告修复：按 `Nexus-Campus-体验报告.md` 修复 H1-H3、M4-M10、L11-L15、D1-D10，并补充回归测试；同时修复本地浏览器验证发现的 CORS 403（`.env`/`.env.example` 允许源需含 `http://localhost:8080`）与 nginx CSP `font-src 'self' data:`。详见 `pre-deployment-checklist.md`。

## Deployment Steps

1. 按上述改动实现代码与配置，新增测试后运行 `mvn test`，确认 188 全绿；再运行 `cd frontend && npm run lint && npm run build`。
2. 提交（建议信息：`feat: production hardening for public demo`）并 `git push origin master`，确认 GitHub Actions 全绿。（此步仍需用户确认）
3. 本机准备：启动 Docker Desktop（已安装于 `D:\Docker`），运行 `docker info` 确认可用；`cloudflared` 已通过 Chocolatey 安装。
4. `git clone` 项目，写入 `.env`：强随机 `DB_PASSWORD`、`JWT_SECRET`，按需设置 `DEMO_SEED_ENABLED` / `DEMO_PASSWORD`；保持 `WEB_PORT=8080` 与 Ollama 默认值。
5. `docker compose up -d --build`，然后 `docker compose exec ollama ollama pull qwen2.5:3b`；验证 `http://localhost:8080` 与 `GET /actuator/health`。
6. Cloudflare Tunnel 初始化：`cloudflared tunnel login`（免费账号），`cloudflared tunnel create nexus-vibe` 记录 `TUNNEL_ID`。
7. DNS 路由：
   - 若 `nexus-vibe.shing26.is-a.dev` 的 DNS 已托管在用户自己的 Cloudflare zone：`cloudflared tunnel route dns nexus-vibe nexus-vibe.shing26.is-a.dev`；
   - 否则 Fork `is-a-dev/register`，为子域添加 CNAME：`nexus-vibe -> <TUNNEL_ID>.cfargotunnel.com`，PR 合并后生效。
8. 编写 `%USERPROFILE%\.cloudflared\config.yml`：ingress 先 `hostname: nexus-vibe.shing26.is-a.dev, service: http://localhost:8080`，兜底 `service: http_status:404`；前台 `cloudflared tunnel run nexus-vibe` 验证，稳定后 `cloudflared service install` 设为 Windows 服务。
9. 部署验收：HTTPS 首页可访问、注册/登录/发帖可用、发帖能触发 Ollama AI 评审、上传图片经 `/uploads/` 正常显示、demo/springdoc 在 prod 不可达、MySQL/Redis/ES 不监听宿主端口。
10. `gh repo edit shing26/nexus-vibe --homepage "https://nexus-vibe.shing26.is-a.dev"`；博客已在 README 顶部提供链接。

## Local Verification (2026-08-15)

- 本机已实测：Docker Desktop 运行中，`docker compose up -d --build` 全栈启动；nginx 首页 `200`，`/actuator/health` 返回 `{"status":"UP"}`，`GET /api/v1/channels` 返回正确 UTF-8 中文。
- `nexus-app` 运行时从 `eclipse-temurin:18-jre` 切到 `eclipse-temurin:21-jre`：JDK 18 在 Docker/WSL2 cgroup v2 下会被 `TomcatMetricsBinder` 触发 `CgroupV2Subsystem` NPE，切换后不再复现。
- MySQL JDBC URL 修正为 `characterEncoding=UTF-8&connectionCollation=utf8mb4_unicode_ci`：旧值 `characterEncoding=utf8mb4` 不是 Java 字符集，Connector/J 8 会直接拒绝连接。
- `application.yml` / `application-prod.yml` 增加 `spring.elasticsearch.uris=${ES_URI:http://localhost:9200}`：Spring Boot 自带的 ES HealthIndicator 之前连 `localhost:9200`，在 Compose 内网里一直 Connection refused。
- `docker/mysql/init.sql` 增加 `SET NAMES utf8mb4;`，并重建本地 `db-data` 卷重新初始化；此前 init 以 latin1 会话导入导致中文被双重编码，API 返回 mojibake。
- `RedisConfig` 的两个 ObjectMapper 统一注册 `JavaTimeModule` 并禁用 `WRITE_DATES_AS_TIMESTAMPS`：之前缓存含 `LocalDateTime` 的实体（如 channels）时 Redis put 抛 `jackson-datatype-jsr310` 缺失错误，缓存一直不生效。
- `mvn test` 复跑 188 全绿；`npm run lint` 与 `npm run build` 通过；Playwright 实测登录、Agent Logs、首页真实计数、375px 无横向溢出、Dashboard 与 Prompt Playground 折叠/变量替换正常。
- Cloudflare Tunnel 尚未验证：`cloudflared tunnel list` 因本机无 `cert.pem` 返回未登录，需用户先完成 `cloudflared tunnel login`；`%USERPROFILE%\.cloudflared\config.yml` 尚不存在。
- Ollama `qwen2.5:3b` 拉取在当前网络约 600 KB/s、预计 50 分钟以上，已暂时中断；未拉取前若 .env 仍指向 OpenAI-compatible 端点，AI 评审走外部 API。

## Test Plan

- 新增 `UploadController` 测试：合法 PNG 成功；HTML 伪装 `image/png` 被拒；扩展名白名单外被拒；魔数与扩展名不一致被拒；超 5MB 被拒。
- 扩展 `RateLimitInterceptorTest`：伪造 `X-Forwarded-For` 首段时仍以 `X-Real-IP`/最右段计限流。
- 回归：`mvn test` 全绿且测试数 188；前端 `npm run build` 与 `npm run lint` 通过。
- 部署验收：HTTPS 首页可访问、注册/登录/发帖可用、发帖能触发 Ollama AI 评审、上传图片经 `/uploads/` 正常显示、demo/springdoc 在 prod 不可达、MySQL/Redis/ES 不监听宿主端口。

## Assumptions

- 不使用 Oracle Cloud；改为 Windows 本机 Docker Desktop + Cloudflare Tunnel，二者本机均已安装。机器需保持开机且网络稳定；不满足时备选国内轻量服务器（阿里云/腾讯云，付费）或第三方 PaaS + 外部 OpenAI-compatible API。
- Cloudflare 免费账号可注册；`is-a.dev` 子域通过 CNAME 指向 `<TUNNEL_ID>.cfargotunnel.com`，不需要 A 记录与 `proxied` 开关。
- Windows CPU 推理推荐 `qwen2.5:3b`，7B 模型只在算力充足的机器使用；MySQL 8、Redis 7、ES 7.17.24、Ollama 均选用官方多架构镜像。
- 若 ES 镜像在 Docker Desktop 上内存占用过高，允许临时从 compose 注释掉 ES 服务及其 `depends_on`，搜索自动降级到 MySQL，不阻塞部署。
- 不在仓库或对话中写入真实 `.env` 值；`.env` 只存在于本机，`/uploads` 目录不入 git。
