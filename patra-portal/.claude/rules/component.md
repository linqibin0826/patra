# 组件规范

## shadcn/ui 优先

- UI primitives（Button、Dialog、Input 等）一律用 shadcn/ui，安装到 `src/components/ui/`
- 禁止从 npm 直接装无 headless 实现的组件库（如 Ant Design、MUI）

## 业务组件组织

- 业务组件放 `src/components/<feature>/`
- 单文件单组件：每个 `.tsx` 默认导出一个组件
- 复合组件（如 `Card.Header`、`Card.Body`）允许同一文件内多个 named export

## Tailwind 习惯

- Class 顺序由 Biome 插件自动排，不手动调整
- 禁止 inline `style` 属性，必须用 Tailwind class 或 CSS variables
- 条件 class 用 `cn`（封装在 `@/lib/utils`）

## Server Components 优先

- 默认 RSC（无 `"use client"` 指令）
- 仅在需要浏览器 API、事件交互、`useState`/`useEffect` 时声明 `"use client"`
