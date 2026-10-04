# patra-portal

Patra C 端前端 —— 医学出版物发现与浏览门户。

- **技术栈**：Next.js 16 App Router · React 19 · TypeScript 5/6 strict · Tailwind v4 · shadcn/ui · TanStack Query · Zustand · React Hook Form + Zod
- **工具链**：pnpm · Biome · Vitest + Testing Library · Playwright
- **Node**：24 LTS（由 `.nvmrc` / `.tool-versions` 锁定）

## 快速开始

```bash
pnpm install
pnpm dev          # http://localhost:4000
```

## 命令

| 命令 | 作用 |
|---|---|
| `pnpm dev` | 开发服务器（:4000） |
| `pnpm build` | 生产构建 |
| `pnpm start` | 运行生产构建产物 |
| `pnpm test` | `vitest run`（CI 模式） |
| `pnpm test:watch` | `vitest`（watch 模式） |
| `pnpm test:e2e` | Playwright 端到端测试（复用已在运行的 dev server，没有则自动启动） |
| `pnpm lint` | `biome check .` |
| `pnpm format` | `biome format --write .` |
| `pnpm typecheck` | `next typegen && tsc --noEmit`（先生成 Next 类型，再做类型检查） |

## 目录结构

```
src/
├── app/                        # App Router 路由
│   ├── layout.tsx              # 根布局：字体、Providers
│   ├── page.tsx                # / 首页
│   ├── papers/(list)/          # /papers 文献检索
│   ├── papers/[id]/            # /papers/:id 文献详情
│   ├── journals/(list)/        # /journals 期刊浏览
│   ├── journals/[id]/          # /journals/:id 期刊详情
│   ├── missing/                # 详情不存在时由 proxy 改写到这里，返回真正的 404
│   ├── api/                    # health、期刊联想
│   ├── error.tsx               # 路由错误边界
│   ├── not-found.tsx           # 全局 404
│   └── globals.css             # Tailwind v4 @theme 映射
├── proxy.ts                    # 请求拦截层：直接打开详情页时先向后端确认记录存在
├── components/
│   ├── portal/                 # 业务组件（按页面 / 功能分目录）
│   └── ui/                     # shadcn/ui 原语
├── lib/
│   ├── portal-api/             # 服务端向网关取数、解析检索参数
│   └── …                       # 工具函数、TanStack Query 工厂与 queryKey
├── data/                       # 静态内容（示例检索、主题等）
├── providers/                  # 'use client' Provider 包装
├── store/                      # Zustand stores
├── styles/tokens.css           # 设计 token
└── types/                      # 共享类型与 ambient .d.ts

tests/                          # Vitest 单测（含 design-system/ 守护测试）与 e2e/
docs/                           # 设计简报，以及项目初始化时的 spec 与 plan
```

## 设计文档

- spec：`docs/patra/specs/2026-05-18-patra-portal-init-design.html`
- plan：`docs/patra/plans/2026-05-18-patra-portal-init.html`

## CI

由仓库统一的 `.github/workflows/ci.yml` 驱动。portal 有改动时运行其中的 `portal` job，依次执行：

| 步骤 | 命令 | 说明 |
|-----|------|------|
| Lint | `pnpm lint` | Biome 静态检查 |
| 类型检查 | `pnpm typecheck` | `next typegen && tsc --noEmit` |
| 单元测试 | `pnpm test` | Vitest |
| 构建 | `pnpm build` | Next.js standalone 生产构建 |
| E2E | `pnpm test:e2e` | Playwright；PR 上失败不阻塞，夜间任务里阻塞 |

main 分支保护唯一的必需检查是 `required-check`，它汇总各 job 的结果（没有触发的 job 视为通过）。

## 提交规范

- git 钩子由仓库根目录的 pre-commit 框架管理（`.pre-commit-config.yaml`）：通用文件检查、gitleaks 密钥扫描、commitlint 校验提交信息
- 提交时不会自动运行 Biome 和类型检查，提交前自己执行 `pnpm lint && pnpm typecheck`；CI 会把关
- commit message 使用中文，遵循 conventional commits 前缀（feat / fix / docs / refactor / test / chore / perf / style / ci / build）
