# patra-portal

Patra C 端前端 —— 医学出版物发现与浏览门户。

- **技术栈**：Next.js 15 App Router · React 19 · TypeScript 5/6 strict · Tailwind v4 · shadcn/ui · TanStack Query · Zustand · React Hook Form + Zod
- **工具链**：pnpm · Biome · Vitest + Testing Library · Husky + lint-staged
- **Node**：24 LTS（由 `.nvmrc` / `.tool-versions` 锁定）

## 快速开始

```bash
pnpm install
pnpm dev          # http://localhost:3000
```

## 命令

| 命令 | 作用 |
|---|---|
| `pnpm dev` | 开发服务器（默认 :3000） |
| `pnpm build` | 生产构建 |
| `pnpm start` | 运行生产构建产物 |
| `pnpm test` | `vitest run`（CI 模式） |
| `pnpm test:watch` | `vitest`（watch 模式） |
| `pnpm lint` | `biome check .` |
| `pnpm format` | `biome format --write .` |
| `pnpm typecheck` | `tsc --noEmit` |

## 目录结构

```
src/
├── app/
│   ├── layout.tsx              # 根布局，包裹 Providers
│   ├── page.tsx                # / 落地页
│   ├── error.tsx               # 路由错误边界
│   ├── not-found.tsx           # 404 占位
│   ├── globals.css             # Tailwind v4 + shadcn token
│   └── api/health/route.ts     # GET /api/health
├── components/ui/              # shadcn/ui 落点（按需添加）
├── lib/
│   ├── utils.ts                # cn() — shadcn 标配
│   └── query-client.ts         # TanStack Query 工厂
├── providers/
│   └── query-provider.tsx      # 'use client' Provider 包装
├── store/                      # Zustand stores（按需添加）
└── types/                      # ambient .d.ts

tests/                          # Vitest + Testing Library
docs/patra/                     # spec 与 plan
```

## 设计文档

- spec：`docs/patra/specs/2026-05-18-patra-portal-init-design.html`
- plan：`docs/patra/plans/2026-05-18-patra-portal-init.html`

## CI

Portal CI 由 `.github/workflows/portal-ci.yml` 驱动，监听 `patra-portal/**` 路径变更自动触发：

| Job | 命令 | 说明 |
|-----|------|------|
| `lint` | `pnpm lint` | Biome 静态检查 |
| `typecheck` | `pnpm typecheck` | TypeScript 类型检查（`tsc --noEmit`） |
| `test` | `pnpm test` | Vitest 单元测试 |

`portal-ci-required-check` 聚合三个 job，已在 main 分支保护中设为 Required Status Check。
故意推入 lint/type 错误即可验证 CI fail 阻断 PR merge。

## 提交规范

- 提交触发 Husky `pre-commit`：`lint-staged`（对 staged 文件跑 `biome check --write`） + 仓库级 `pnpm typecheck`
- 任一失败则阻止 commit
- commit message 使用中文，遵循 conventional commits 前缀（feat/chore/fix/docs/test/refactor）
