# patra-learn

Patra 学习站：借 Patra 这个真实项目学后端，从「HTTP 请求是什么」读到「发件箱怎么保证消息不丢」。

条目是 MDX，平铺在 `content/entries/`，每篇标难度（入门 / 基础 / 进阶 / 实战）、写明前置；路线、主题、标签由 YAML 组织，构建时生成索引。纯静态站（SSG），无后端依赖。

- **技术栈**：Next.js 16 App Router · React 19 · MDX · zod · TypeScript strict · Tailwind v4 · Biome · Vitest · Playwright
- **Node**：24（`.nvmrc` 锁定）+ pnpm 10
- **访问**：https://learn.linqibin.dev（公网，不用登录）；部署在 Mac mini，容器端口 4001 只绑 mini 本机回环，经 Cloudflare Tunnel 对外

## 快速开始

```bash
pnpm install
pnpm dev          # http://localhost:4001
```

## 命令

| 命令 | 作用 |
|---|---|
| `pnpm dev` | 开发服务器（:4001） |
| `pnpm build` | 生产构建 |
| `pnpm typecheck` | `next typegen && tsc --noEmit` |
| `pnpm test` | `vitest run`：内容校验（真实内容零问题）+ 纯逻辑单测 |
| `pnpm content:check-file <路径…>` | 单篇校验（给维护技能的模板用） |
| `pnpm -s content:report` | 体检报告 JSON（校验问题 + 代码引用钉住后的状态） |
| `pnpm test:e2e` | Playwright smoke（仅本地，不进 CI；基座用 :4010——本机 QQ 占 127.0.0.1:4001） |
| `pnpm lint` | `biome check .` |
| `pnpm format` | `biome format --write .` |

## 目录结构

```
content/
├── entries/<slug>.mdx        # 条目（frontmatter + 正文）
├── routes/<slug>.yaml        # 路线：标题、简介、展示顺序、按阅读顺序排的条目
├── topics/<slug>.yaml        # 主题：标题、简介（成员由条目的 topics 决定）
└── tags.yaml                 # 标签表
scripts/
├── content-report.ts         # pnpm content:report
└── content-check-file.ts     # pnpm content:check-file
src/
├── app/
│   ├── page.tsx              # 首页（最小占位）
│   ├── entries/[slug]/       # 条目页：构建期用 @mdx-js/mdx 编译
│   ├── api/health/route.ts   # GET /api/health（容器健康检查）
│   ├── layout.tsx / globals.css
├── lib/content/              # 内容模型：类型、schema、解析、校验规则、索引、加载、体检报告
├── teaching/registry.ts      # 组件登记表（参数 schema、子内容、教学角色）
├── teaching/placeholders.tsx # 诊断题 / 复述题 / 迁移题占位实现
├── components/code-ref.tsx   # 代码引用 → GitHub 永久链接
└── mdx-components.tsx        # 组件名 → 实现
tests/smoke.spec.ts           # Playwright e2e（仅本地）
```

## 内容模型

- **条目类型**：概念 `concept`、排查 `troubleshooting`、决策 `decision`、坑 `pitfall`。
- **难度**：入门 `intro`、基础 `basic`、进阶 `advanced`、实战 `practice`；路线里不能倒排。
- **前置**：必须存在、不成环；同一路线里前置排在本篇之前。
- **教学结构**：正文开头是诊断题，结尾是复述题或迁移题。
- **代码引用**：`路径@提交SHA`，页面链到 GitHub 上那次提交；体检报告列出钉住之后改过的引用。
- **公开边界**：内容里不许出现凭据、tailnet / 内网地址与本机主机名。

完整格式与规则 id 见 `docs/patra/specs/2026-10-31-learn-content-model-design.md`。

## CI

learn 在 `.github/workflows/ci.yml` 有专属 job（`learn_changed` 门控，仅 learn 变更时触发）：lint → typecheck → test → build，已纳入 `required-check` 合并门禁（未触发时按 allowed-skips 放行）。e2e 不进 CI，只在本地跑（`pnpm test:e2e`）。

## 部署

- **镜像**：`patra-learn/Dockerfile` 三阶段构建（deps → `next build` → standalone runner），产物是 Next.js standalone Node server，监听 4001。
- **CD**：merge 到 main 且命中 `patra-learn/**` 等 paths → `.github/workflows/learn-cd.yml` 在 Mac mini runner 原生 arm64 `docker build` → `patra-infra/cd/deploy.sh` 部署（健康检查 `/api/health` + 部署后验证 + 不健康自动回滚）→ GHCR 归档推送（best-effort）。回滚：`workflow_dispatch` 填旧 sha 的 `image_tag`。
- **容器**：Mac mini `patra-apps` 栈，compose 服务名 `learn` / 容器 `patra-learn`，env 只读 `.env.learn`（纯静态站，不读 `.env.common`）。端口只绑 `127.0.0.1:4001`，tailnet 上不能直接访问。
- **公网入口**：门户那条 Cloudflare Tunnel 加了 `learn.linqibin.dev` → `http://localhost:4001`（见 `patra-infra/docker/README.md`「门户的公网入口」）。对线上的 Playwright 回归走这个域名。
- **Bootstrap 提示**：首次合入 main 后需等 learn-cd 首跑完成产出镜像；在那之前 Mac mini 整栈 `compose-all.sh up` 会因拉不到 learn 镜像报错，属预期——等首跑绿了再 up 即可。

## 设计文档

- 内容模型工程 spec：`docs/patra/specs/2026-10-31-learn-content-model-design.md`
- 实施计划：`docs/patra/plans/2026-10-31-learn-content-model.md`
- 版本 spec：`docs/patra/release-specs/v0.17-learn.md`
