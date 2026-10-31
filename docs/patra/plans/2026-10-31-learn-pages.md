# 学习站页面、本地进度与公网开放 实现计划（PAP-162）

> 工程 spec：`docs/patra/specs/2026-10-31-learn-pages-design.md`
> 分支：`feat/v0.17-learn`（本会话在 worktree 分支上提交，由编排会话摘到版本分支）
> 执行方式：当前会话内联执行，纯逻辑按 Red-Green-Refactor，页面由 Playwright 冒烟兜底

## Task 1：进度存储（`src/lib/progress/`）

1. 写 `progress.test.ts`：解析容错（null、坏 JSON、非对象、字段类型不对）→ 实现 `parseProgress`。
2. 测 `withMastery` / `withVisit`（标记、取消保留 `readAt`、不改原对象）→ 实现。
3. 测 `loadProgress` / `saveProgress` / `clearProgress` 在存储抛错时不抛、写失败退到内存 → 实现。
4. `use-progress.ts`：`useSyncExternalStore` 包装（服务端快照为空、`storage` 事件订阅）。

验证：`pnpm test src/lib/progress`。

## Task 2：条目摘要与回顾题（`src/lib/site/views.ts`）

1. 测阅读时长估算 → 实现 `readingMinutes`。
2. 测从正文取 `Diagnose` 参数（`mdx-rules.ts` 导出 `readComponentProps`）、答案讲解去掉「答案是 X。」→ 实现 `reviewOf`。
3. 测 `entrySummaries`：所在路线与位置、缺失条目跳过、被取代 / 个人环境标记 → 实现。
4. 测路线 / 主题 / 标签 / 条目侧栏视图（个人环境排除、主题按版本分组、反向前置、上一篇下一篇、正在长的主题、最近写的）→ 实现。

## Task 3：今天读这一条（`src/lib/site/today.ts`）

测候选过滤、三档排序、同档按最近看到从旧到新、理由文字 → 实现 `rankReview`、`reviewReason`。

## Task 4：搜索（`src/lib/site/search.ts`）

测分词 AND 匹配、不区分大小写、标题命中优先、类型与难度筛选、个人环境只计数、高亮区间 → 实现 `searchEntries`。

## Task 5：页面与样式

1. `globals.css` 补用到的 token（moss-line、类型色底、代码块、字号、悬停阴影、高亮底）。
2. 顶栏 + `/` 快捷键；徽章、卡片、正文 Markdown 元素。
3. 首页、条目页、路线页、主题页、标签页、搜索页，按画板。
4. 本地 `pnpm build` + 桌面 / 手机截图自查。

## Task 6：公网开放（仓库部分）

compose 端口绑回环；`patra-infra/docker/README.md`、`patra-infra/CLAUDE.md`、`patra-learn/CLAUDE.md`、`patra-learn/README.md` 改说法。

## Task 7：冒烟与全量验证

`playwright.config.ts` 改跑生产构建；`tests/smoke.spec.ts` 覆盖首页、条目页、路线页、标签页、搜索、进度刷新仍在与清空。跑 `pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm build`、`pnpm test:e2e`。

## 提交

1. 进度、页面逻辑与单测 + spec / plan
2. 页面、样式与冒烟
3. 公网开放的 compose 与文档
