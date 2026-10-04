# patra-learn

Patra 学习站 —— 把你的系统，一条线一条线学明白。

内部学习站（onboarding + 回顾）：把本仓库的 CI / CD / 巡检体系做成「地铁线路图」式课程——3 条开通线 13 站正文，配套词条图鉴、事故档案、运维小抄。纯静态站（SSG + localStorage 打卡进度），无后端依赖。

- **技术栈**：Next.js 16 App Router · React 19 · TypeScript strict · Tailwind v4 · Biome · Vitest · Playwright
- **Node**：24（`.nvmrc` 锁定）+ pnpm 10
- **访问**：部署在 Mac mini，仅 tailscale 内网 —— `http://linqibins-mac-mini:4001`

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
| `pnpm typecheck` | `tsc --noEmit` |
| `pnpm test` | `vitest run`（内容 integrity + 纯逻辑单测） |
| `pnpm test:e2e` | Playwright smoke（仅本地，不进 CI；基座用 :4010——本机 QQ 占 127.0.0.1:4001） |
| `pnpm lint` | `biome check .` |
| `pnpm format` | `biome format --write .` |

## 目录结构

```
src/
├── app/
│   ├── page.tsx                        # / 首页（MetroMap 学习网络图 + 继续通勤卡）
│   ├── lines/[line]/[station]/page.tsx # 课程文章页（SSG，13 站）
│   ├── glossary/page.tsx               # /glossary 词条图鉴（10 条，可搜索）
│   ├── archive/page.tsx                # /archive 事故档案（6 份）
│   ├── cheatsheet/page.tsx             # /cheatsheet 运维小抄（3 张操作卡）
│   ├── api/health/route.ts             # GET /api/health（容器健康检查）
│   ├── layout.tsx                      # 根布局：字体（正文系统字体 + JetBrains Mono）+ TopBar
│   └── globals.css                     # Tailwind v4 @theme 设计 token
├── content/                            # 内容层（内容即代码，无 CMS）
│   ├── types.ts                        # Line / Station / StationRef / GlossaryEntry / Incident / OpsCard
│   ├── lines.ts                        # LINES 拓扑 + TRANSFER_NODE（线路 5 色也在这）
│   ├── articles/                       # 13 站正文组件 + index.ts 静态注册表
│   ├── glossary.ts / incidents.ts / cheatsheet.ts
│   └── integrity.test.ts               # 内容防线：引用完整性 + 数量校验
├── components/                         # metro-map / article-layout / check-in-button / …
├── lib/
│   ├── content.ts                      # 拓扑查询（openStationRefs / adjacentStations / firstUnvisited）
│   └── progress.ts                     # localStorage 打卡进度（key: patra-learn.progress.v1）
└── types/globals.d.ts                  # ambient 声明

tests/smoke.spec.ts                     # Playwright e2e（仅本地）
```

## 内容模型

- **`Line`**（`lines.ts` 的 `LINES`）：3 条开通线——l1 质检线（CI，4 站）/ l2 上线线（CD，5 站）/ l3 守夜线（巡检，4 站），共 13 站；另有 2 条规划线（l4 架构线 / l5 数据线，`status: "planned"`，首页灰色虚线不可点）。
- **`Station`**：`id / name / summary`；每个开通站在 `articles/index.ts` 注册一篇文章组件，路由 `/lines/<line>/<station>`。
- **`StationRef`**（`` `${LineId}/${string}` ``）：全站互链的统一引用——词条的 `appearsAt`、档案的 `relatedStation`、小抄的 `lineId` 都指回站/线，`integrity.test.ts` 保证引用真实存在、注册表与拓扑一一对应。
- **换乘节点** `TRANSFER_NODE`：l1 终点「合并进 main」→ l2 起点的视觉连接，不计入 13 站、无文章（MetroMap 中坐标硬编码，改拓扑需同步 `metro-map.tsx`）。

## CI

learn 在 `.github/workflows/ci.yml` 有专属 job（`learn_changed` 门控，仅 learn 变更时触发）：lint → typecheck → test → build，已纳入 `required-check` 合并门禁（未触发时按 allowed-skips 放行）。e2e 不进 CI，只在本地跑（`pnpm test:e2e`）。

## 部署

- **镜像**：`patra-learn/Dockerfile` 三阶段构建（deps → `next build` → standalone runner），产物是 Next.js standalone Node server，监听 4001。
- **CD**：merge 到 main 且命中 `patra-learn/**` 等 paths → `.github/workflows/learn-cd.yml` 在 Mac mini runner 原生 arm64 `docker build` → `patra-infra/cd/deploy.sh` 部署（健康检查 `/api/health` + 部署后验证 + 不健康自动回滚）→ GHCR 归档推送（best-effort）。回滚：`workflow_dispatch` 填旧 sha 的 `image_tag`。
- **容器**：Mac mini `patra-apps` 栈，compose 服务名 `learn` / 容器 `patra-learn`，env 只读 `.env.learn`（纯静态站，不读 `.env.common`）。
- **Bootstrap 提示**：首次合入 main 后需等 learn-cd 首跑完成产出镜像；在那之前 Mac mini 整栈 `compose-all.sh up` 会因拉不到 learn 镜像报错，属预期——等首跑绿了再 up 即可。

## 设计文档

- spec：`docs/patra/specs/2026-08-27-patra-learn-design.html`
- plan：`docs/patra/plans/2026-08-27-patra-learn-site.html`
- 设计画布：Claude Design `e2bb7c08`（六类页面高保真稿 + Tokens / Components 页）
