# CLAUDE.md

## 定位

Patra **学习站**：借 Patra 这个真实项目学后端，四档难度（入门 / 基础 / 进阶 / 实战），8 条路线，条目由 Agent 按维护技能 `patra:learn` 写。公网地址 `https://learn.linqibin.dev`（经门户那条 Cloudflare Tunnel；容器端口 **4001** 只绑 mini 本机回环，tailnet 上不能直接访问）；部署走 `.github/workflows/learn-cd.yml`（deploy-only：pnpm 构建全在 `patra-learn/Dockerfile` 内完成，CD 只做 docker build + `deploy.sh`）。

技术栈：Next.js 16 App Router + React 19（SSG，纯静态站无后端依赖）· MDX（`@mdx-js/mdx` 构建期编译）· zod · TypeScript strict · Tailwind v4 · Biome · Vitest · Playwright。

## 内容模型

格式全文见工程 spec `docs/patra/specs/2026-10-31-learn-content-model-design.md`，这里只列怎么做：

- **条目**：`content/entries/<slug>.mdx`，平铺，文件名即 slug。开头 YAML frontmatter（`title` `summary` `type` `difficulty` `tags` `prerequisites`，可选 `topics` `sources` `personalEnv` `supersededBy`），取值用英文常量。
- **正文**：Markdown + GFM + 登记表里的组件。不许 import / export、HTML 标签、`{表达式}`（注释 `{/* */}` 可以），组件属性只写字面量。第一块必须是 `<Diagnose>`，最后一块必须是 `<Recap>` 或 `<Transfer>`。
- **代码引用**：`<CodeRef at="路径@40位提交SHA" lines="12-30" />`，路径相对仓库根；代码还没进 main 写 `路径@pending`。
- **路线** `content/routes/<slug>.yaml`、**主题** `content/topics/<slug>.yaml`、**标签表** `content/tags.yaml`。路线决定成员与顺序；主题成员由条目 `topics` 决定、按出处版本排序。
- **公开边界**：不写凭据、tailnet / 内网地址、本机主机名，用 `<mini 的地址>` 这类占位（校验拦）。个人环境条目（`personalEnv: true`）不进路线、版本索引与首页；可以挂主题，主题下全是个人环境条目时索引标 `personalOnly`，页面不把它列进主题列表。
- **加教学组件**：`src/teaching/registry.ts` 登记（zod 参数 schema、子内容、角色）→ `src/mdx-components.tsx` 加同名实现，两边名字由 `registry.test.ts` 守。
- `content/curriculum.md` 是课程大纲，不归内容模型，加载器不读。

代码位置：`src/lib/content/`（解析、校验规则、索引、加载）、`scripts/`（体检与单篇校验）、`src/app/`（页面）。

## 页面与读者进度

页面清单、进度存储与「今天读这一条」的规则见工程 spec `docs/patra/specs/2026-10-31-learn-pages-design.md`，这里只列怎么做：

- **页面逻辑是纯函数**：`src/lib/site/`（条目卡片、各页视图、今天读这一条、搜索），构建时从内容算出可序列化的数据交给页面；改展示规则先改这里的单测。
- **进度只在浏览器**：`src/lib/progress/`，`localStorage` 一个键 `patra-learn.progress`，读写都 try/catch；页面用 `useProgress()`，服务端与水合时是空进度，所以服务端 HTML 就是新读者的样子。
- **服务端组件不能从 `"use client"` 模块取值**（只能渲染它导出的组件）：页面共用的类名与徽章在 `src/components/ui.tsx`（不带 `"use client"`），中文名在 `src/lib/site/labels.ts`。
- 个人环境条目不进路线、首页、搜索结果与普通标签页，只在 `/tags/personal-env` 下。

## 设计系统来源

源头是 PAP-156 hi-fi 画布（花园方向，`project/learn.css` 顶部 `:root`），设计简报 `docs/design-briefs/v0.17/learn-site.md`。落地分工：

- **颜色、字号、圆角、阴影、缓动**在 `src/app/globals.css` 的 `@theme` token（每个 token 必须真被 utility 消费，防死类；用到 `learn.css` 里还没落的再补）；
- **动效时长与缓动**由 GSAP 用，定义在 `src/teaching/motion.ts`（160 / 280 / 560 ms）；不跟随系统「减少动态效果」（决策 D）；
- **字体**（决策 G）：标题 `font-display`、正文 `font-body` 首选 Source Serif 4（西文与数字），中文落到系统宋体 / 系统黑体；控件 `font-ui` 用系统 UI 字体；代码 `font-mono` 用 JetBrains Mono。两个西文字体随 npm 包 `@fontsource-variable/source-serif-4`、`@fontsource-variable/jetbrains-mono` 安装、由 `layout.tsx` 引入。构建时不联网下载字体，所以能用 Next 16 默认的 Turbopack（它遇到任何一个字体请求失败都会让构建失败）。不要用 `next/font/google` 引入字体，也不要加中文网页字体：每个中文字族有 101 个切片，首次访问每页要多下载约 1 MB（`fonts.test.ts` 兜底）。

## 教学组件

条目里的交互全部来自 `src/teaching/`（决策 D），写法文档是同目录的 `README.md`（写内容只读它），设计见工程 spec `docs/patra/specs/2026-10-31-learn-teaching-components-design.md`。加组件：`registry.ts` 登记 zod schema → 写实现与 jsdom 组件测试 → `mdx-components.tsx` 注册 → README 加一节带 mdx 示例（`readme.test.ts` 会跑它）。图示类组件只收数据（节点、边、步骤、状态），布局与动效在组件里算。

## 测试与命令

- **`pnpm test` 是内容防线**：`src/lib/content/content.test.ts` 读真实 `content/` 跑全部规则，必须零问题；失败时列出每条问题的规则 id、文件与修法。写内容后跑它，不用改测试。各规则的单测在 `src/lib/content/*.test.ts`，纯逻辑、node 环境；教学组件的 `*.test.tsx` 在文件头切到 jsdom。
- **`pnpm content:check-file <路径…>`**：只跑单篇规则（不查文件名与前置 / 路线 / 主题），给维护技能的条目模板用。
- **`pnpm -s content:report`**：体检报告 JSON（全部问题 + 每个代码引用钉住之后的状态），要在完整克隆里跑；维护技能的体检只读它。
- **e2e 只在本地跑，不进 CI**：`pnpm test:e2e`（Playwright smoke，先 `next build` 再 `next start`，跑在生产构建上）。本机 QQ 客户端占用 `127.0.0.1:4001`（仅 IPv4），故 e2e 基座独占 **4010** 口（见 `playwright.config.ts` 注释），生产端口 4001 不变。

## 事实纪律

1. 条目讲的是**本仓库的真实系统**：技术事实以仓库代码为准，用 `CodeRef` 钉住出处。体检报告里 `changed` / `missing` 的引用要更新条目——不要让学习站讲一个已经不存在的系统。
2. JSX 中文文本**不要句中断行**：JSX 内换行会渲染成可见空格，中文句子中间会出现异常空格。长句写一行，交给编辑器软换行。

<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->
