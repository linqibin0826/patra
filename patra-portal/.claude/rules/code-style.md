# 代码风格

## Biome 是唯一权威

- 格式化与 lint 全部交给 Biome（`biome.json`）
- 禁止与 Biome 配置冲突的手动格式化
- pre-commit 通过 Husky + lint-staged 自动跑 Biome

## TypeScript strict 模式

- 禁止 `any`（用 `unknown` + 类型守卫代替）
- 禁止 `// @ts-ignore`，使用 `// @ts-expect-error <原因>`
- 函数返回类型对公开 API 必须显式声明（除明显的简单函数）

## 命名

| 类别 | 约定 | 示例 |
|------|------|------|
| 组件 | PascalCase | `VenueCard.tsx` |
| Hook | `useXxx` camelCase | `useVenueQuery` |
| 纯函数 / 变量 | camelCase | `formatDate` |
| 常量 | SCREAMING_SNAKE_CASE | `MAX_RETRY_COUNT` |
| 类型 / 接口 | PascalCase | `VenueDTO` |

## 路径别名

- `@/` 指向 `src/`
- 禁止使用 `../../` 跨目录引用，统一用 `@/`
