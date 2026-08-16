# Nexus-Vibe Frontend

React 19 + Vite + TypeScript + Tailwind CSS 单页应用，负责 Nexus-Vibe 社区的全部用户界面。

## Scripts

```bash
npm install
npm run dev        # 开发服务器，/api 代理到 http://localhost:8081
npm run build      # 类型检查 + 生产构建
npm run lint       # oxlint
npm run preview    # 预览生产构建
```

## Structure

```text
src/
├── api/           # Axios 客户端与 TanStack Query hooks
├── components/    # 通用 UI、布局、AI 终端等组件
├── pages/         # 路由页面
├── stores/        # Zustand（认证、主题、Toast）
└── types/         # 与后端 DTO 对应的 TypeScript 类型
```

认证路由（发帖、编辑、设置、消息、草稿）由 `AuthGuard` 保护；管理页面由
`AdminRouteGuard` 保护。后端接口必须通过登录后的 JWT。
