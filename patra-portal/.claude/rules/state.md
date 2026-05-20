# 状态管理边界

## 三层状态分工

| 类型 | 工具 | 例子 |
|------|------|------|
| 服务端状态（来自 API） | TanStack Query v5 | venue 列表、user info |
| 客户端 UI 状态 | Zustand | 侧边栏折叠、主题切换 |
| 表单状态 | React Hook Form + Zod | 编辑表单、搜索框 |

## TanStack Query 约定

- `queryKey` 集中管理在 `src/lib/query-keys.ts`
- 每个 endpoint 一个自定义 hook（`useVenueListQuery`、`useVenueDetailQuery`）
- mutation 后用 `queryClient.invalidateQueries({ queryKey })` 失效缓存

## Zustand 约定

- 每个 store 单一职责（不写"大 store"）
- store 文件放 `src/store/<name>.ts`
- 禁止把 API 数据塞进 Zustand

## RHF + Zod 约定

- 表单 schema 用 Zod 定义，通过 `zodResolver` 接入 RHF
- 表单状态不进 Zustand，不进 query cache
