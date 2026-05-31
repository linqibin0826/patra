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

## patra plugin 自动加载

`patra:` 与 `patra-backend:` 命名空间下的流程方法论与后端专属 skill（brainstorming / writing-plans / TDD / debugging / code review / hexagonal / jpa / events / troubleshooter 等）已放在**仓库根** `.claude/skills/patra/` 与 `.claude/skills/patra-backend/`。

Claude Code 的项目级 `.claude/skills` 会从启动 cwd **向上递归查找到仓库根**，因此从 portal 子目录（`patra/patra-portal/`）启动 Claude Code 时，仓库根的这两个 plugin 会**自动加载**，无需任何 `/plugin marketplace add` 或 `install` 操作 —— 接受 workspace trust 后即可调用 `patra:brainstorming` 等。

skill 内示例当前以 Java 为主，但 LLM 可自动跨技术栈映射 —— FE 工作时把 Java 示例理解为 TS/React 等价物即可。若实际使用 cognitive friction 显著超预期，再独立开「plugin 去技术栈化」PR（回归上游 obra/superpowers 风格 + 保留中文翻译）。
