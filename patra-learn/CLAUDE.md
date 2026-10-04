# CLAUDE.md

## 定位

Patra **内部学习站**（onboarding + 回顾）——把本仓库的 CI / CD / 巡检体系做成「地铁线路图」式课程。仅 tailscale 内网访问，端口 **4001**；部署走 `.github/workflows/learn-cd.yml`（deploy-only：pnpm 构建全在 `patra-learn/Dockerfile` 内完成，CD 只做 docker build + `deploy.sh`）。

技术栈：Next.js 16 App Router + React 19（SSG，纯静态站无后端依赖）· TypeScript strict · Tailwind v4 · Biome · Vitest · Playwright。

## 内容即代码（扩展流程）

内容全部是仓库内 TS/TSX 数据与组件，没有 CMS：

- **加一站**：`src/content/lines.ts` 对应线加 `Station` → `src/content/articles/<line>/<id>.tsx` 建文章组件 → `src/content/articles/index.ts` 注册。三步缺一，`integrity.test.ts` 会红到你补齐；三步做完后，integrity 的站数断言（`[4, 5, 4]` / 13 站）也要同步改测试期望值。
- **加词条 / 事故档案 / 操作卡**：改 `src/content/glossary.ts` / `incidents.ts` / `cheatsheet.ts`，同样有 integrity 校验兜底（数量、编号、`StationRef` 引用必须真实存在）。
- **开新线**：`LINES` 加条目（或把规划线 `status: "planned"` 改 `"open"`），integrity 的开通线断言同步更新；MetroMap 硬编码注意点见下条。
- **MetroMap 无测试兜底（静默失败面）**：`src/components/metro-map.tsx` 的画布宽度 `width = X0 + STEP * 5 + 60` 不随最长线站数变化，换乘节点坐标（l1 末站 x = `X0 + STEP * 3`、l1/l2 行号）同样写死——给最长线加站或改线路拓扑（l1 站数、线序、换乘关系）必须同步这些公式，且没有任何测试会红，只能肉眼验证首页。

## 设计系统来源

源头是 Claude Design 画布 `e2bb7c08`（Tokens 页）。落地分工：

- **线路 5 色**在 `src/content/lines.ts` 数据里（`color` / `softColor`），经内联 `style` 使用；
- **中性 / 语义色**在 `src/app/globals.css` 的 `@theme` token（每个 token 必须真被 utility 消费，防死类）；
- **字体**：正文用系统字体（西文 `system-ui`，中文系统黑体 `"PingFang SC", "Microsoft YaHei", "Noto Sans CJK SC"`，与 portal 同一组）；代码用 JetBrains Mono，随 npm 包 `@fontsource-variable/jetbrains-mono` 安装、由 `layout.tsx` 引入。构建时不联网下载字体，所以能用 Next 16 默认的 Turbopack（它遇到任何一个字体请求失败都会让构建失败）。不要用 `next/font/google` 引入字体，也不要加中文网页字体：每个中文字族有 101 个切片，首次访问每页要多下载约 1 MB（`fonts.test.ts` 兜底）。

## 测试约定

- `src/content/integrity.test.ts` 是**内容防线**：引用完整性 + 数量校验（13 站 3 线、词条/档案/操作卡数）——加内容后跑 `pnpm test`，按红项补齐。
- `src/lib/*.test.ts`（progress / content）是纯逻辑单测，node 环境（见 `vitest.config.ts`），不渲染组件。
- **e2e 只在本地跑，不进 CI**：`pnpm test:e2e`（Playwright smoke 3 条）。本机 QQ 客户端占用 `127.0.0.1:4001`（仅 IPv4），故 e2e 基座独占 **4010** 口（见 `playwright.config.ts` 注释），生产端口 4001 不变。

## 事实纪律

1. 文章讲的是**本仓库的真实系统**：技术事实以仓库代码为准（`cd.yml` / `learn-cd.yml` / `deploy.sh` / `runner-watchdog.yml` 等）。改基建后，相关课程文章要同步更新——不要让学习站讲一个已经不存在的系统。
2. JSX 中文文本**不要句中断行**：JSX 内换行会渲染成可见空格，中文句子中间会出现异常空格。长句写一行，交给编辑器软换行。

<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->
