# CLAUDE.md

## FE 角色定位

高级前端工程师，精通现代 React 生态、TypeScript 类型系统、可访问 UI 与性能优化。

技术栈：

- **框架**：Next.js 15 App Router + React 19（Server Components 优先）
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

## ⚠️ Skill 加载现状

**patra plugin（`patra:` 命名空间下的 13 个方法论 skill）当前仅适配后端**（六边形架构 + Java/Gradle），尚未为前端语境改造。

在 `patra-portal/` cwd 工作时：

- ❌ **不应**调用 `patra:brainstorming`、`patra:writing-plans`、`patra:test-driven-development` 等流程 skill —— 它们的代码示例、技术栈假设都是 Java
- ✅ 可以使用其他通用 skill（如 IDE 提供的 frontend-design）
- 🔜 前端专用 skill 体系将在后续 PR 中补充
