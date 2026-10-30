# 门户「有帮助」按钮与两处排序（PAP-134）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（Patra 约定：在当前会话内联执行，不用 subagent-driven）to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 评论、经验行、经验页的「有帮助」按钮（乐观更新、失败回滚、行内提示），讨论区「最有帮助」，期刊页经验列表「最新投稿 / 最有帮助」，两处排序写进 `?sort=`、首屏服务端按地址取、翻页按 ID 去重。

**Architecture:** 按钮自带身份判断与乐观状态机（`useHelpfulToggle`），成功后用 `setQueriesData` 按目标 ID 写回所有已缓存的讨论 / 经验副本并标过期（`refetchType: "none"`，不重排）；经验页的 props 来自 RSC，按钮靠「点击时的服务端值做基准」的本地规则显示返回值。排序沿用讨论区「地址为准、RSC 首屏只作初值、客户端 `replaceState`」的链路，经验列表照搬一条。

**Tech Stack:** Next.js 16 App Router（webpack）/ React 19 / TypeScript strict / TanStack Query v5 / Tailwind v4 + base-ui / Vitest + Testing Library / Playwright。

**Spec:** `docs/patra/specs/2026-10-30-portal-helpful-design.md`（下称 spec）；文案与状态 ID 以设计简报 `patra-portal/docs/design-briefs/v0.15/helpful.md` 为准；接口以 `docs/patra/specs/2026-10-29-reaction-helpful-design.md` 第 6 节、`2026-10-29-discussion-stats-design.md` 第 5–8 节为准。

## Global Constraints

- 开工条件：后端 PR #321 已合进 main 并部署到 mini；从 main 切编排会话通知里给的前端分支。
- 工作目录 `patra-portal/`，命令都在这里跑（worktree 内一律绝对路径，不 `cd` 出 worktree）；路径别名 `@/` = `src/`、`@tests/` = `tests/`，不写 `../../`。
- 不 commit、不 push：每个任务结束只跑验证；全部完成、用户本地验收同意后一次提交（只加自己的路径，中文 subject 以动词开头，类名 / Issue key 不放 subject 开头）。
- 界面用词只有「有帮助」「最有帮助」「最新投稿」，不出现「点赞」「赞同」「投票」「热度」。
- 计数为 0 不写数字；千分位 `1,204`，不缩写。
- 失败只用按钮旁行内提示（`role="status"`，4 秒），不用 toast；toast 只报成功。
- 不显示 PAP-135 的计数（楼内统计、讨论区条数、经验行回复数、事实条、卡片、获赞）。
- 设计系统守护测试的坑：`animate-pulse` 加 `motion-safe:`；`text-fg-4` 只给占位符（自己的内容图标用 `text-fg-4` 前先跑 `tests/design-system`，不过就改 `text-fg-3`）；不写 `focus-visible:shadow-none`；`<fieldset` 后第一个 className 带 `min-w-0`；语义色用 `text-fg-3` 等标准类，不写 `[var(--x)]`；accent 写 `clay-500`。
- 客户端 `fetch` 的地址写成以 `/` 开头、与 `fetch(` 同一行的模板字面量（`tests/lib/gateway-boundary.test.ts`）。
- 被 RSC 引用的 `lib/*` 模块不 import 客户端 hook（`next build` 才报）；`location.ts`、`experience-schema.ts` 保持服务端可用。
- vitest 负载高时随机超时（iCloud）：loadavg 降到 12 以下再跑全量，不改超时。

## Review Focus

1. **身份在页面上变化**（另一个标签页退出 / 换号）后，按钮不能留着上一位的高亮或「自己的内容」。→ Task 5 用例「props 换成新访客的值后显示 props，不显示上一次的返回值」。
2. **请求在途时列表重取落地**（切回已缓存排序触发的重取、删除对账）：按钮不翻回去；请求结束后以较新的 props 为准。→ Task 5 用例「挂起期间 rerender 新 props 仍显示乐观值，resolve 后显示返回值」。
3. **同一条在两页里各出现一次**（「最有帮助」翻页期间数字变了）：只渲染一次；点它时两个副本都被写回，去重后看到的那份数字正确。→ Task 4 用例「重复的楼两份都改」+ Task 7 用例「两页有同一楼只渲染一次」。
4. **经验行里点按钮不触发整行链接**，键盘 Tab 先到链接再到按钮。→ Task 8 用例「点按钮不导航」「Tab 顺序」。
5. **快速连点**：第二次点击在请求结束前被忽略，不出现 +2 或乱序回滚。→ Task 5 用例「busy 时再点不调 action」。

---

### Task 0: 准备

- [ ] **Step 1:** `git log --oneline -1` 对照通知里的基线 SHA；`git ls-files patra-api/patra-interaction/patra-interaction-adapter/src/main/java/dev/patra/interaction/adapter/reaction/rest/HelpfulController.java` 有输出（#321 是 squash 合并，`6261ed06b` 不会是祖先，不要用 `merge-base` 判断）。本 spec 与 plan 若不在新分支上（新 worktree 从 main 切出），从本会话 worktree 拷过来。
- [ ] **Step 2:** `pnpm install --frozen-lockfile`；从主检出拷 `/Users/linqibin/Projects/Products/patra/.claude/launch.json` 到本 worktree 的 `.claude/launch.json`（保留剥代理变量与网关指 mini）。
- [ ] **Step 3:** 核对 mini 已部署：`curl -s "http://100.103.73.27:9528/patra-interaction/journals/<任一有经验的期刊 ID>/posts?limit=1&sort=helpful"` 返回带 `total` 与每条 `helpfulCount`；记下 `total` 是数字还是字符串（spec 第 3 节，schema 两种都收）。`curl -s -X PUT .../comments/1/helpful` 无会话返回 401。
- [ ] **Step 4:** `pnpm typecheck && pnpm test` 记下基线（预期全过）。

### Task 1: 小工具（`formatCount`、`uniqueBy`、`writeQuery`）

**Files:** Create `src/lib/format-count.ts`、`src/lib/unique-by.ts`、`src/lib/write-query.ts`；Modify `src/lib/discussion/use-discussion-location.ts`；Test `tests/lib/format-count.test.ts`、`tests/lib/unique-by.test.ts`

**Interfaces — Produces:**
- `formatCount(n: number): string`：`0` → `""`，`3` → `"3"`，`1204` → `"1,204"`（`toLocaleString("en-US")`）。
- `uniqueBy<T>(items: readonly T[], keyOf: (item: T) => string): T[]`：保留第一次出现的，顺序不变。
- `writeQuery(change: (query: URLSearchParams) => void, how: "push" | "replace"): void`：原样从 `use-discussion-location.ts` 挪出并导出。

- [ ] **Step 1:** 失败测试：`formatCount` 上面三例 + `1000000` → `"1,000,000"`；`uniqueBy([{id:"1",v:1},{id:"2"},{id:"1",v:2}], x => x.id)` 得 `[{id:"1",v:1},{id:"2"}]`；空数组返回空数组。
- [ ] **Step 2:** `pnpm vitest run tests/lib/format-count.test.ts tests/lib/unique-by.test.ts` → FAIL（模块不存在）。
- [ ] **Step 3:** 实现；挪 `writeQuery`，`use-discussion-location.ts` 改为 import。
- [ ] **Step 4:** 同一命令 → PASS；`pnpm vitest run tests/lib/discussion/use-discussion-location.test.tsx` 仍 PASS。

### Task 2: Schema 与 fixtures

**Files:** Modify `src/lib/interaction/discussion-schema.ts`、`src/lib/interaction/experience-schema.ts`、`src/lib/discussion/cache.ts`（`toPlaceholder`）、`tests/fixtures/discussion.ts`、`tests/fixtures/experience.ts`；Create `src/lib/interaction/helpful-schema.ts`；Test `tests/lib/interaction/schema.test.ts`（追加）、`tests/lib/discussion/cache.test.ts`（追加）

**Interfaces — Produces:**
- `Comment` 多 `helpfulCount: number`、`helpful: boolean | null`；`Experience` 同。
- `ExperiencePage` 多 `total: number`（`z.coerce.number().int().nonnegative()`）。
- `DiscussionSort = "asc" | "latest" | "helpful"`；`ExperienceSort = "latest" | "helpful"`（`experience-schema.ts` 导出类型与 `experienceSortSchema = z.enum(["latest", "helpful"])`）。
- `helpful-schema.ts`：`HelpfulTarget = { type: "comment" | "post"; id: string }`（`helpfulTargetSchema`）、`HelpfulResult = { helpful: boolean; helpfulCount: number }`（`helpfulResultSchema`）。
- fixtures：`makeComment` 默认 `helpfulCount: 0, helpful: false`；`makeExperience` 同；新增 `makeExperiencePage(items, overrides?)` 默认 `total = items.length`、`nextCursor: null`。

- [ ] **Step 1:** 失败测试：`commentSchema` 解析带 `helpfulCount: 3, helpful: null` 的条目得到这两项；缺 `helpfulCount` 解析失败；`experiencePageSchema` 对 `total: 18` 与 `total: "18"` 都得 `18`；`helpfulResultSchema` 解析 `{ helpful: true, helpfulCount: 4 }`；`toPlaceholder(makeComment({ helpfulCount: 5, helpful: true }))` 得 `helpfulCount: 0, helpful: null`。
- [ ] **Step 2:** `pnpm vitest run tests/lib/interaction/schema.test.ts tests/lib/discussion/cache.test.ts` → FAIL。
- [ ] **Step 3:** 实现 schema、`toPlaceholder`、fixtures。
- [ ] **Step 4:** 同一命令 → PASS；`pnpm typecheck` 通过（手写的 `Comment` / `Experience` 字面量补字段）。
- [ ] **Step 5:** `pnpm test` 全量：Route Handler、`discussion.test.ts`、`experience.test.ts`、页面测试里手写的响应体 JSON（不经 fixtures）缺新字段会解析失败、typecheck 看不出，在这一步一次补完。

### Task 3: Server Action 与失败文案

**Files:** Create `src/lib/interaction/helpful-actions.ts`、`src/lib/helpful/messages.ts`；Test `tests/lib/interaction/helpful-actions.test.ts`、`tests/lib/helpful/messages.test.ts`

**Interfaces — Consumes:** `mutate`、`UNAVAILABLE`、`CommonFailure`（`action-failure.ts`）；`helpfulResultSchema`、`HelpfulTarget`（Task 2）。
**Produces:**
- `setHelpful(target: HelpfulTarget, on: boolean): Promise<HelpfulActionResult>`，类型见 spec 4.3（`reason`：`email-unverified` / `account-restricted` / `daily-limit` / `gone` / `self` / `signed-out` / `unavailable`）。
- `helpfulFailureMessage(reason: HelpfulFailure["reason"], context: { on: boolean; type: HelpfulTarget["type"] }): string`，文案照 spec 5.3 表（`email-unverified` 返回 `验证邮箱后才能点有帮助`，重发链接由组件另放）。

- [ ] **Step 1:** 失败测试（照 `discussion-actions.test.ts` 的桩：`node` 环境、cookie-jar、`fetchMock`）：
  - `setHelpful({type:"comment",id:"5"}, true)` → `PUT http://gw.test/patra-interaction/comments/5/helpful`，200 `{helpful:true,helpfulCount:3}` → `{ ok: true, result: { helpful: true, helpfulCount: 3 } }`；`on=false` → `DELETE`；`type:"post"` → `/posts/5/helpful`。
  - 429 + `DAILY_LIMIT_REACHED` → `daily-limit`；409 + `EMAIL_UNVERIFIED` → `email-unverified`；409 + `ACCOUNT_RESTRICTED` → `account-restricted`；404 + `HELPFUL_TARGET_NOT_FOUND` → `gone`；403 + `SELF_HELPFUL` → `self`；401 → `signed-out` 且清 Cookie；500 → `unavailable`。
  - `type: "publication"`、`id: "x"`、`on: "yes"` → `unavailable`，`fetchMock` 未调用。
  - messages：七个 reason × 点 / 取消 × 评论 / 经验取 spec 5.3 的文案（`unavailable` 点 `没有标记成功，请稍后再试`、取消 `没有取消成功，请稍后再试`；`gone` 经验 `这条投稿经验已被删除`）。
- [ ] **Step 2:** `pnpm vitest run tests/lib/interaction/helpful-actions.test.ts tests/lib/helpful/messages.test.ts` → FAIL。
- [ ] **Step 3:** 实现。`classify` 只认 spec 4.3 列的组合，其余返回 null 交给 `mutate`。
- [ ] **Step 4:** 同一命令 → PASS。

### Task 4: 缓存写回与标过期

**Files:** Create `src/lib/helpful/cache.ts`；Modify `src/lib/query-keys.ts`（只追加 / 改经验列表两项，见 Task 10，先在这里加 `experienceListAll` 与带 `sort` 的 `experienceList`）、`src/lib/discussion/cache.ts`（文件头注释）；Test `tests/lib/helpful/cache.test.ts`

**Interfaces — Consumes:** `ThreadsData`、`SingleData`（`lib/discussion/cache.ts`）、`ExperiencePage`、`UserExperiencePage`、`HelpfulTarget`、`HelpfulResult`。
**Produces:**
- `queryKeys.experienceListAll(journalId) = ["experience","journal",journalId,"list"]`；`queryKeys.experienceList(journalId, sort) = [...experienceListAll(journalId), sort]`。
- 纯函数：`withCommentHelpful<T extends ThreadsData | SingleData>(data: T, commentId: string, value: HelpfulResult): T`；`withPostHelpful(data, postId, value)` 分别处理 `InfiniteData<ExperiencePage>` 与 `InfiniteData<UserExperiencePage>`（两个具名函数 `withJournalPostHelpful` / `withUserPostHelpful`）。
- `patchHelpful(client: QueryClient, target: HelpfulTarget, value: HelpfulResult): void`、`markHelpfulStale(client: QueryClient, target: HelpfulTarget): void`，谓词与 key 形状照 spec 6.1、6.2。

- [ ] **Step 1:** 失败测试：
  - `withCommentHelpful`：根评论、回复各命中一次；同一楼在两页各一份时两份都改；没命中的返回结构相等的数据；`SingleData` 的 `{ missing: true }` 原样返回；入参未被修改（`structuredClone` 前后比较）。
  - `patchHelpful`：预置 `discussionThreads("publication","1","asc")`、`("…","helpful")`、`discussionSingle(...)`、`discussionConversation("5")`（数组形状）后，评论 5 的三份 key 里的值都变、对话缓存不变；经验目标改 `experienceList("9","latest")`、`experienceList("9","helpful")`、`experienceUser("u1")` 里 postId 相同的那条，`experienceSummary` 不变。
  - `markHelpfulStale`：上面这些 query 的 `isInvalidated` 为真，且没有发起 fetch（`queryFn` 桩未被调用）；`experienceSummary` 未失效。
- [ ] **Step 2:** `pnpm vitest run tests/lib/helpful/cache.test.ts` → FAIL。
- [ ] **Step 3:** 实现；`experienceList` 改签名后 `pnpm typecheck` 会指出 `lib/experience/{queries,server}.ts` 的调用点——本任务先给它们传 `"latest"`，Task 10 再贯通。
- [ ] **Step 4:** 同一命令 → PASS；`pnpm vitest run tests/lib/experience tests/components/experience` 仍 PASS。

### Task 5: `useHelpfulToggle` 与 `HelpfulButton`

**Files:** Create `src/lib/helpful/use-helpful-toggle.ts`、`src/components/helpful/HelpfulButton.tsx`；Test `tests/components/helpful/HelpfulButton.test.tsx`

**Interfaces — Consumes:** `setHelpful`、`helpfulFailureMessage`（Task 3）；`patchHelpful`、`markHelpfulStale`（Task 4）；`formatCount`（Task 1）；`useCurrentUser`、`useLoginRedirect`、`ResendVerificationButton`。
**Produces:** 具名导出 `HelpfulButton(props: HelpfulButtonProps)`（props 照 spec 5.1）与 `HELPFUL_HINT_MS = 4000`。

测试用 `renderWithQuery`，`vi.mock("@/lib/interaction/helpful-actions")`、`vi.mock("@/lib/auth/current-user-query")`（返回可切换的 `useCurrentUser`）、`vi.mock("@/lib/library/use-login-redirect")`。

- [ ] **Step 1:** 失败测试（一次写一条，红绿后再下一条；名称即断言）：
  - 渲染：`count=0` 按钮名 `有帮助`、`aria-pressed=false`、没有数字；`count=3` 名 `有帮助，3 人觉得有帮助`、可见 `3`；`helpful=true` 时 `aria-pressed=true`；`count=1204` 显示 `1,204`；`own` 且 `count=3` → 没有 button、文本 `3 人觉得有帮助`；`own` 且 `count=0` → 什么都不渲染；`variant="pill"` 同样的名字与状态。
  - 未登录：`helpful=null` 点击 → `redirectToLogin` 调用一次、action 未调用；`helpful=false` 但当前用户为匿名 → 同上。
  - 未验证：当前用户 `emailVerified:false` → action 未调用、数字不变、出现 `role="status"` 文本 `验证邮箱后才能点有帮助` 与按钮 `重新发送验证邮件`；推进 4,000 ms 后提示消失；焦点在「重新发送验证邮件」上时推进 4,000 ms 提示仍在，焦点离开后再推进 4,000 ms 才消失。
  - 乐观：action 返回挂起的 promise → 立刻 `aria-pressed=true`、显示 `4`、`aria-busy=true`；此时再点 action 仍只调用一次（Review Focus 5）；resolve `{ok:true,result:{helpful:true,helpfulCount:5}}` → 显示 `5`，`patchHelpful` 与 `markHelpfulStale` 各调用一次（spy 模块）。
  - 取消：`helpful=true,count=4` 点 → `setHelpful(target,false)`，立刻显示 `3`。
  - 失败回滚：`unavailable` → 退回 `aria-pressed=false`、`3`，提示 `没有标记成功，请稍后再试`；取消时失败提示 `没有取消成功，请稍后再试`；`daily-limit`、`account-restricted`、`self` 各自文案（`self` 同网络）；4,000 ms 后消失。
  - `signed-out` → 退回并 `redirectToLogin`。
  - `gone` → 提示 `这条评论已被删除`；`onGone` 在 4,000 ms 后才调用（之前未调用）。
  - 基准规则（Review Focus 1、2）：挂起期间 `rerender` 新 props `{helpful:false,count:9}` 仍显示乐观值；resolve 后 props 已不等于点击时的 `{false,3}` → 显示 props `9`；成功后 props 不变（经验页场景）→ 一直显示返回值；之后 `rerender` 成另一组值 → 显示 props。
  - 新一次点击替换还在显示的提示并重新计时；卸载后推进定时器不报错、`onGone` 不调用。
- [ ] **Step 2:** `pnpm vitest run tests/components/helpful` → FAIL。
- [ ] **Step 3:** 实现 hook（`pending`、`settled = { base, value }`、`hint`、定时器；比较按值 `helpful` + `count`）与组件：文字形沿用 `CommentItem` 的 `TextAction` 样式（`text-xs font-medium text-fg-3 hover:text-fg-1 max-[880px]:min-h-8`），图标 `ThumbsUp` 14px `strokeWidth 1.75`；已点 `fill-clay-500 text-clay-600`、文字 `text-clay-700`、`motion-safe:scale-110`；胶囊形 `h-9 max-[880px]:h-11 rounded-full border border-border-default bg-paper-50 text-sm`，已点 `bg-clay-50 border-clay-200 text-clay-700`；提示 `text-xs`，网络等 `text-rust-500`、未验证 `text-amber-700`。
- [ ] **Step 4:** 同一命令 → PASS；`pnpm vitest run tests/design-system` PASS。

### Task 6: 评论接入

**Files:** Modify `src/components/discussion/CommentItem.tsx`、`src/components/discussion/DiscussionView.tsx`、`src/components/discussion/SingleThreadView.tsx`；Test `tests/components/discussion/CommentItem.test.tsx`、`DiscussionView.test.tsx`、`SingleThreadView.test.tsx`（追加）

**Interfaces — Consumes:** `HelpfulButton`（Task 5）。**Produces:** `CommentActions.onHelpfulGone: (comment: Comment) => void`。

- [ ] **Step 1:** 失败测试：
  - `CommentItem`：操作行第一个按钮是有帮助（`getAllByRole("button")` 顺序：有帮助在「回复」前）；`mine` 且 `helpfulCount=2` → 文本 `2 人觉得有帮助`、没有有帮助按钮；占位（`deleted`）没有有帮助、没有数字；`readOnly` 与 `editing` 时没有。
  - `DiscussionView`（`sort=helpful` 预置两楼，第二楼 `helpfulCount=0`）：点第二楼的有帮助、action resolve 为 `{true,1}` → 第二楼显示 `1`、两楼顺序不变（不重排）；缓存里 `discussionThreads(…,"helpful")` 的这条为 `helpfulCount:1`。
  - `DiscussionView` gone：action 回 `gone`，推进 4,000 ms → `fetch` 调了 `/api/discussions/publication/1/comments/<id>/thread`，返回的楼替换进列表（根评论变占位）。
  - `SingleThreadView` gone：推进后单楼查询重取。
- [ ] **Step 2:** `pnpm vitest run tests/components/discussion` → FAIL（新用例）。
- [ ] **Step 3:** 实现：`CommentItem` 操作行最前加按钮；`ThreadList` 的 `onHelpfulGone` 复用 `onDeleted` 里「重取这一楼 → `replaceThread` / `applyDeletion`」那段（抽成同文件内的 `reconcileThread(rootId)`），再 `stale()`；`SingleThreadView` 的 `onHelpfulGone` 为 `client.invalidateQueries({ queryKey: key })`。
- [ ] **Step 4:** 同一命令 → PASS。

### Task 7: 讨论区「最有帮助」、`SortToggle`、去重

**Files:** Create `src/components/portal/SortToggle.tsx`；Modify `src/lib/discussion/location.ts`、`src/lib/discussion/use-discussion-location.ts`、`src/lib/discussion/cache.ts`（`insertRoot`）、`src/app/api/discussions/[type]/[key]/threads/route.ts`、`src/components/discussion/discussion-parts.tsx`、`src/components/discussion/DiscussionView.tsx`；Test `tests/lib/discussion/{server,use-discussion-location,cache}.test.ts(x)`、`tests/app/api/discussion-routes.test.ts`、`tests/components/discussion/DiscussionView.test.tsx`（追加）

**Interfaces — Produces:**
- `parseDiscussionSort(value: string | null): DiscussionSort`（`location.ts`）。
- `SortToggle<T extends string>({ value, options, onChange, legend }: { value: T; options: readonly { value: T; label: string }[]; onChange: (value: T) => void; legend: string })`：`"use client"`（`components/portal/` 多数是 RSC 安全的，这个有点击事件）；`fieldset`（`min-w-0`）+ `sr-only legend` + `aria-pressed` 按钮，样式照现在 `DiscussionHeader` 的分段控件原样搬。

- [ ] **Step 1:** 失败测试：
  - `parseDiscussionView({ sort: "helpful" })` → `{ mode: "list", sort: "helpful" }`；`{ sort: ["helpful","asc"] }` → `asc`；`prefetchDiscussion(PAPER, { mode: "list", sort: "helpful" })` 请求 `…/threads?sort=helpful`、放进 `discussionThreads(…,"helpful")`。
  - `useDiscussionLocation`：`sort=helpful` 读成 helpful；`setSort("helpful")` 地址写 `sort=helpful`，`setSort("asc")` 删参数，`#discussion` 片段保留。
  - threads Route Handler：`?sort=helpful` 转发为 `sort=helpful`；`?sort=bogus` 为 `asc`。
  - `insertRoot(data, comment, "helpful")` → `placed: "top"`、在第一页最前。
  - `DiscussionView`：分段控件三项名 `正序` / `最新` / `最有帮助`，`sort=helpful` 时「最有帮助」`aria-pressed=true`；点「最有帮助」后地址带 `sort=helpful`、请求 `…?sort=helpful`；`sort=helpful` 下发表根评论 → 出现在最前、没有「去看看」行；两页里有同一个 `root.commentId` 时只渲染一个 `#comment-<id>`（Review Focus 3）。
- [ ] **Step 2:** `pnpm vitest run tests/lib/discussion tests/app/api/discussion-routes.test.ts tests/components/discussion` → FAIL（新用例）。
- [ ] **Step 3:** 实现；`DiscussionHeader` 外层加 `flex-wrap`，用 `SortToggle`；`ThreadList` 的 `threads` 用 `uniqueBy(…, (t) => t.root.commentId)`。
- [ ] **Step 4:** 同一命令 → PASS。

### Task 8: `ExperienceRow` 改结构与按钮

**Files:** Modify `src/components/experience/ExperienceRow.tsx`、`tests/e2e/support/experience.ts`（行定位）、`tests/e2e/experience.spec.ts`（用到 `getByRole("link", { name: /…月投稿/ })` 的几处）；Test `tests/components/experience/ExperienceRow.test.tsx`（改 + 追加）

**Interfaces — Consumes:** `HelpfulButton`（Task 5）。

- [ ] **Step 1:** 先改现有用例的取法（`getByRole("article")` 取整行、`within(row).getByRole("link")` 取链接），再加失败用例：
  - 期刊变体：唯一的链接名是结果行（如 `首轮 42 天 · 大修 → 录用`），`href=/posts/<id>`；月份、摘录、署名在 `article` 里、不在链接里。主页变体：链接名是期刊名。
  - 底部行：别人的经验 `helpfulCount=2` → 按钮 `有帮助，2 人觉得有帮助`；`mine` 且 `2` → 文本 `2 人觉得有帮助`；`mine` 且 `0` → 没有底部行（`article` 里没有「有帮助」文字）。
  - 按钮不在链接里（`link.contains(button) === false`），点按钮调用 action（Review Focus 4；点了 URL 不变由 Task 12 的 e2e 量）。
  - Tab 顺序：先链接、后有帮助按钮（Review Focus 4）。
- [ ] **Step 2:** `pnpm vitest run tests/components/experience/ExperienceRow.test.tsx` → FAIL。
- [ ] **Step 3:** 实现 spec 7.2：`article.group.relative`，标题 `Link` 加 `after:absolute after:inset-0` 与 `focus-visible:outline-none focus-visible:after:…` 焦点环（照 `tests/design-system/focus.test.ts` 允许的写法），底部行 `relative z-1`。`ExperienceRow` 只在客户端组件（`ExperienceArea`、`ProfileExperiences`）里渲染，引入客户端的 `HelpfulButton` 不需要给它加 `"use client"`。
- [ ] **Step 4:** 同一命令 → PASS；`pnpm vitest run tests/components/experience tests/components/profile tests/app/user-experiences-page.test.tsx` PASS。

### Task 9: 经验页胶囊形按钮

**Files:** Modify `src/components/experience/PostView.tsx`；Test `tests/components/experience/PostView.test.tsx`（追加）

- [ ] **Step 1:** 失败测试：别人的经验 `helpfulCount=5,helpful=true` → 胶囊按钮 `aria-pressed=true`、名 `有帮助，5 人觉得有帮助`，位置在正文之后（`compareDocumentPosition`）；`mine` → 文本 `5 人觉得有帮助`；`mine` 且 `0` → 没有；编辑态没有；点击成功后（props 不变）显示返回值；`gone` 推进 4,000 ms → `router.refresh` 调用。
- [ ] **Step 2:** `pnpm vitest run tests/components/experience/PostView.test.tsx` → FAIL。
- [ ] **Step 3:** 实现：展示态末尾 `<HelpfulButton variant="pill" target={{ type: "post", id: shown.postId }} … onGone={() => router.refresh()} />`。
- [ ] **Step 4:** 同一命令 → PASS。

### Task 10: 期刊页经验排序链路（数据）

**Files:** Modify `src/lib/interaction/experience.ts`、`src/lib/experience/queries.ts`、`src/lib/experience/server.ts`、`src/app/api/journals/[id]/posts/route.ts`、`src/app/journals/[id]/page.tsx`、`src/components/experience/ExperienceSection.tsx`、`src/components/experience/Experiences.tsx`；Create `src/lib/experience/use-experience-sort.ts`；Test `tests/lib/interaction/experience.test.ts`、`tests/lib/experience/queries.test.tsx`（若无则新建）、`tests/app/api/experience-routes.test.ts`、`tests/app/journal-experiences.test.tsx`、`tests/lib/experience/use-experience-sort.test.tsx`

**Interfaces — Produces:**
- `parseExperienceSort(value: unknown): ExperienceSort`，放 `src/lib/experience/sort.ts`（不 import 客户端 hook，RSC 与 Route Handler 共用）：只有字符串 `"helpful"` 认，其余 `"latest"`。
- `fetchJournalExperiences(journalId: string, sort: ExperienceSort, cursor?: string)`：路径 `/journals/${id}/posts?limit=10&sort=${sort}`（+ `&cursor=`）。
- `prefetchJournalExperiences(journalId: string, sort: ExperienceSort)`：放进 `experienceList(journalId, sort)`。
- `useJournalExperiences(journalId: string, sort: ExperienceSort)`：请求 `` `/api/journals/${journalId}/posts?${query}` ``（`sort` + 可选 `cursor`），`placeholderData: keepPreviousData`。
- `useExperienceSort(): { sort: ExperienceSort; setSort: (sort: ExperienceSort) => void }`：读 `useSearchParams`，`setSort` 用 `writeQuery(…, "replace")`（`latest` 删参数）。
- `afterPublish` 改为 `resetQueries({ queryKey: experienceListAll(journalId) })`。
- `ExperienceSection` / `Experiences` 新增 prop `sort: ExperienceSort`；`JournalDetailPage` 收 `searchParams` 并 `parseExperienceSort(search.sort)`。

- [ ] **Step 1:** 失败测试：`fetchJournalExperiences("9","helpful","c1")` 请求 `…/journals/9/posts?limit=10&sort=helpful&cursor=c1`；Route Handler `?sort=helpful&cursor=c1` 转发、`?sort=x` 为 `latest`；`prefetchJournalExperiences("9","helpful")` 的脱水状态 key 为 `experienceList("9","helpful")`；期刊页 `searchParams = { sort: "helpful" }` 时首屏请求带 `sort=helpful`（`journal-experiences.test.tsx` 照现有页面测试的桩）；`useExperienceSort`：`sort=helpful` 读出、`setSort("latest")` 删参数且保留 `#experiences`；`afterPublish` 重置两种排序的列表。
- [ ] **Step 2:** 运行上述测试文件 → FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 同一批 → PASS；`pnpm typecheck` 通过。

### Task 11: 期刊页工具行与切换（UI）

**Files:** Modify `src/components/experience/ExperienceArea.tsx`；Test `tests/components/experience/ExperienceArea.test.tsx`（追加）

**Interfaces — Consumes:** `useExperienceSort`、`useJournalExperiences(journalId, sort)`（Task 10）、`SortToggle`（Task 7）、`uniqueBy`（Task 1）。

- [ ] **Step 1:** 失败测试：
  - 第一页 `total=18` → 汇总卡与列表之间有文本 `18 条投稿经验` 与分段控件 `最新投稿` / `最有帮助`（`legend` 为 `排序`）；`total=1` → 有文字、没有控件；`total=0`（空状态）→ 整行没有；降级时没有。
  - 地址 `sort=helpful` → 「最有帮助」`aria-pressed=true`，请求带 `sort=helpful`。
  - 点「最有帮助」：地址写 `sort=helpful`；目标排序已有多页缓存时先被清掉，新请求不带 `cursor`（从第一页取）；取回前旧列表仍在（`keepPreviousData`）。
  - 两页里有同一 `postId` → 只渲染一个 `article`（Review Focus 3）。
  - 发布后两种排序的列表都被重置（`afterPublish`）。
- [ ] **Step 2:** `pnpm vitest run tests/components/experience/ExperienceArea.test.tsx` → FAIL。
- [ ] **Step 3:** 实现 spec 8.3：工具行 `flex flex-wrap items-center justify-between gap-3`，左 `text-sm text-fg-2` 写 `` `${formatCount(total)} 条投稿经验` ``；`setSort` 先 `client.removeQueries({ queryKey: queryKeys.experienceList(journalId, next), exact: true })` 再写地址；`items` 用 `uniqueBy(…, (e) => e.postId)`。
- [ ] **Step 4:** 同一命令 → PASS。

### Task 12: Playwright（`tests/e2e/helpful.spec.ts`）

**Files:** Create `tests/e2e/helpful.spec.ts`、`tests/e2e/support/helpful.ts`（`helpfulButton(scope)`、`ownHelpfulText(scope)`、`unmarkAll` 等）

场景、账号与收尾照 spec 第 10 节（经验部分 A 写两条，`total ≥ 2` 才有排序控件；投稿日期用 `2026-01-…` 与 `2026-02-…` 这类不同月份，较早那条被 B 点）。账号 A = `PATRA_E2E_VERIFIED_EMAIL/PASSWORD`、B = `PATRA_E2E_VERIFIED_B_EMAIL/PASSWORD`；缺任一则 `test.skip` 并写明来源 `~/.patra/secrets/e2e.env`。发两条根评论之间等 `PUBLISH_GAP_MS`（`waitSince`）。

- [ ] **Step 1:** 照 `discussion.spec.ts` 用 `VIEWPORTS` 循环两个尺寸，各跑一遍完整串行流程（讨论 → 经验 → 收尾，各自准备与清理数据），断言用 `getByRole("button", { name: /^有帮助/ })` 与 `aria-pressed`；先等 `waitForTakeover` 再点（Next 16 dev 冷编译坑）。
- [ ] **Step 2:** 两个尺寸都断言：点经验行底部按钮后 URL 不变；375 另断言分段控件三个按钮的 `boundingBox().y` 相同、文字形按钮高 ≥ 32、胶囊形 ≥ 44。
- [ ] **Step 3:** 起本机门户（`preview_start` 用 launch.json 的 portal 配置，网关指 mini），`source ~/.patra/secrets/e2e.env && PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm test:e2e tests/e2e/helpful.spec.ts --workers=1` → 两个尺寸全过。单 worker 下偶发一条冷编译超时：单独重跑那条，不改超时。
- [ ] **Step 4:** 同样跑 `tests/e2e/discussion.spec.ts tests/e2e/experience.spec.ts`（`ExperienceRow` 改结构与新按钮不破坏旧冒烟）。

### Task 13: 全量验证与交接

- [ ] **Step 1:** `pnpm typecheck && pnpm lint && pnpm test` 全过（loadavg 高时先等）。
- [ ] **Step 2:** `pnpm build` 通过（RSC 误引客户端 hook 只有 build 暴露）。
- [ ] **Step 3:** 本机门户走查 1440 / 375：评论、楼内回复、期刊页经验行、主页经验行、经验页五处按钮的各状态，`?sort=helpful` 刷新与新标签页打开保持；截图交编排会话转用户本地验收。
- [ ] **Step 4:** 写共享笔记 `portal-PAP-134-handoff.md`（spec 第 2 节第 7 条：`HelpfulButton` props、经验行底部行留给回复数的位置、`SortToggle`、`formatCount`、经验 schema 里 PAP-135 要补的字段、`total` 实测是数字还是字符串）。
- [ ] **Step 5:** 停在本地等验收；同意后一次提交（附 spec、plan；只加自己的路径，`git diff --cached` 核对），按编排会话的分支说明开 draft PR。
