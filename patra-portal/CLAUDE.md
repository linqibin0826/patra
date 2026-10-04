# CLAUDE.md

## FE 角色定位

高级前端工程师，精通现代 React 生态、TypeScript 类型系统、可访问 UI 与性能优化。

技术栈：

- **框架**：Next.js 16 App Router + React 19（Server Components 优先）
- **打包**：webpack（`dev` / `build` 脚本带 `--webpack`），不用 Next 16 默认的 Turbopack。三个西文字体在构建时仍由 `next/font` 从 Google 下载（28 个文件）：Turbopack 下任何一个请求失败都会让构建失败，webpack 会重试 3 次（Mac mini 的历史构建里约三成出现过这类重试）
- **字体**：西文 Inter / Newsreader / IBM Plex Mono 走 `next/font/google`；中文正文用系统黑体（`--patra-font-cjk-sans`）；中文衬线 Noto Serif SC 随 npm 包 `@fontsource-variable/noto-serif-sc` 安装。不要用 `next/font/google` 引入中文字体：每个字族要在构建时下载 101 个切片
- **类型**：TypeScript 5 strict 模式
- **样式**：Tailwind v4 + shadcn/ui（组件原语在 `src/components/ui/`）
- **状态**：TanStack Query v5（服务端状态）+ Zustand（客户端 UI 状态）
- **表单**：React Hook Form + Zod
- **测试**：Vitest + Testing Library
- **工具**：Biome（格式化 + lint）+ pnpm + Husky/lint-staged

## FE 工作原则

1. **类型严格**：禁止 `any`、禁止 `// @ts-ignore`；用 `@ts-expect-error` 必须带注释说明原因
2. **服务端优先**：默认使用 Server Components（无 `"use client"`），仅在需要交互或浏览器 API 时声明客户端
3. **状态边界**：API 数据→TanStack Query；UI 偏好 / 本地暂存→Zustand；表单状态→RHF
4. **可访问性**：优先使用语义化 HTML 与 ARIA；Testing Library 查询优先 `getByRole`

详细规范见 `.claude/rules/` 下的具体规则文件（Claude Code 自动加载）。

## 页面设计策略：服务全体用户 + 渐进式披露

Patra 门户面向**所有类型用户**（临床医生、科研人员、研究生、投稿作者……），不为某一类用户裁剪页面。统一采用**渐进式披露（progressive disclosure）**：

- **默认层**：每个页面默认只呈现"绝大多数用户都需要"的核心信息，保持干净、低密度、快速可读。
- **深数据层**：专业 / 重度信息（完整指标、明细、可选维度）默认收起，由用户**主动展开**（折叠区 / Tab / 抽屉，按页选定）。

**为什么**：访客类型差异极大，一刀切要么信息过载、要么信息不足。渐进式披露让普通访客看概览即走、重度用户按需深挖，同一页面同时服务两端。

**落地约束**：

1. 每次页面设计（Claude Design 简报 + FE 实现）必须显式区分「默认层 / 深数据层」，并点名各模块归属。
2. **BE 连带含义**：详情类端点应一次返回**完整数据集（含深数据层）**供前端按需披露；不要因为"默认页用不到"就在端点裁掉深层字段。

## patra plugin 自动加载

`patra:` 与 `patra-backend:` 命名空间下的流程方法论与后端专属 skill（brainstorming / writing-plans / TDD / debugging / code review / hexagonal / jpa / events / troubleshooter 等）已放在**仓库根** `.claude/skills/patra/` 与 `.claude/skills/patra-backend/`。

Claude Code 的项目级 `.claude/skills` 会从启动 cwd **向上递归查找到仓库根**，因此从 portal 子目录（`patra/patra-portal/`）启动 Claude Code 时，仓库根的这两个 plugin 会**自动加载**，无需任何 `/plugin marketplace add` 或 `install` 操作 —— 接受 workspace trust 后即可调用 `patra:brainstorming` 等。

skill 内示例当前以 Java 为主，但 LLM 可自动跨技术栈映射 —— FE 工作时把 Java 示例理解为 TS/React 等价物即可。若实际使用 cognitive friction 显著超预期，再独立开「plugin 去技术栈化」PR（回归上游 obra/superpowers 风格 + 保留中文翻译）。

<!-- BEGIN:nextjs-agent-rules -->

# This is NOT the Next.js you know

This version has breaking changes — APIs, conventions, and file structure may all differ from your training data. Read the relevant guide in `node_modules/next/dist/docs/` (resolved from this file's directory; in monorepos the `next` package may not be visible from the repo root) before writing any code. Heed deprecation notices.

This block is written and re-added by `next dev` — verify at `node_modules/next/dist/server/lib/generate-agent-files.js`. Removing it from a diff only re-creates the uncommitted change; committing it with your work keeps the tree clean.

<!-- END:nextjs-agent-rules -->
