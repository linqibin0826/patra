# 门户「有帮助」按钮与两处排序（PAP-134）工程 spec

> **Issue**：Linear PAP-134（GitHub #319），v0.15 Helpful 前端第一个；PAP-135（公开计数）在它之后，两者改同一批组件，串行。
> **产品定义**：release spec `docs/patra/release-specs/v0.15-helpful.md` §2.1.1、§2.1.2、决策 C、D，Done 判定 D4、D5。
> **页面、状态、文案**：设计简报 `patra-portal/docs/design-briefs/v0.15/helpful.md` §3.1–§3.3、§3.5、§3.8–§3.11、§3.13、§4.1、§4.2、§6；画布 <https://claude.ai/artifact/GzchLSeVWW1dnjnAHVhhDi>（`HelpfulStates` / `Helpful` / `Discussion` / `Journal` / `Post` / `Profile` 及其 `-M`）。本 spec 不重抄文案，实现时逐条对照简报的状态 ID。
> **接口**：后端工程 spec `docs/patra/specs/2026-10-29-reaction-helpful-design.md` 第 6 节（点 / 取消、错误码）与 `docs/patra/specs/2026-10-29-discussion-stats-design.md` 第 5–8 节（响应里的有帮助数与「我点过没有」、`sort=helpful`、`total`）。代码在 `feat/v0.15-helpful-api`（6261ed06b，PR #321，2026-10-30 尚未合并部署）。
> **基线**：main 已含 #321（squash 合并，6261ed06b 不会是祖先）且已部署到 mini 后，从 main 切出前端分支（编排会话通知里给分支名）。
> **创建日期**：2026-10-30

---

## 1. 要做什么

评论（根评论、楼内回复）、期刊页与主页的经验行、经验页上出现「有帮助」按钮：点了数字立刻 ±1，失败退回并在按钮旁行内提示；自己的内容只有数字没有按钮；未登录去登录；邮箱未验证前端直接拦。讨论区排序加第三项「最有帮助」，期刊页经验列表加「最新投稿 / 最有帮助」切换，两处都写进 `?sort=`，首屏由服务端按地址取。「最有帮助」翻页按 ID 去重。

## 2. 范围

**做**：

1. 调用层：评论、经验响应的 `helpfulCount` / `helpful`，期刊经验列表的 `total`；点 / 取消的 Server Action；讨论、经验列表的 `sort` 参数贯通到 Route Handler 与 RSC 首屏。
2. `HelpfulButton`（文字形、胶囊形、只读数字）与它的乐观更新、行内提示、写回缓存。
3. 四个位置：评论操作行、期刊页经验行、主页经验行、经验页正文下方。`ExperienceRow` 改结构（`article` + 铺满的标题链接 + 压在上面的底部行）。
4. 讨论区「最有帮助」与新根评论的插入规则；期刊页经验列表的工具行（总条数 + 排序切换）。
5. 两个列表的翻页按 ID 去重。
6. 单测 + Playwright（对 mini，1440×900 与 375×812）。
7. 共享笔记 `portal-PAP-134-handoff.md`（给 PAP-135：`HelpfulButton`、经验行底部行、`SortToggle`、`formatCount` 的落点与扩展位）。

**不做**（PAP-135）：楼内统计 `N 条回复 · 最后回复 …` 与「展开其余 N 条回复」（简报 §3.4）、讨论区标题的条数（§3.6）、经验行的回复数（§3.9 底部行的第二项）、文献页事实条与四处文献卡片的计数（§3.7、§3.12）、主页获赞（§3.11）。这些字段的 schema 也留给 PAP-135（zod 默认丢弃未声明的键，现在不声明不影响解析）。

**例外（我定的）**：期刊页工具行左边的 `N 条投稿经验` 放在本 Issue。简报 §2 把这一行与排序列在同一格（「PAP-134（按钮、排序）/ PAP-135（回复数）」），而且「只有 1 条或 0 条时不出分段控件」本身就要用这个总数。

## 3. 简报与后端的对照

| 处 | 简报 | 后端（已实现） | 门户做法 |
|---|---|---|---|
| 错误区分 | §3.2：网络、每日上限、账号异常、未验证、目标已删除、给自己点、401 | 401；404 `HELPFUL_TARGET_NOT_FOUND`；403 `SELF_HELPFUL`；409 `ACCOUNT_RESTRICTED` / `EMAIL_UNVERIFIED`；429 `DAILY_LIMIT_REACHED` | 一一对应（第 5.3 节）；403 按网络失败处理（简报 `helpful.error.self`） |
| 取消的资格 | 未写 | 取消只要登录，不查资格、不查目标（目标删了是 no-op，200） | 取消失败只会是网络 / 401；「已删除」只在点的时候出现 |
| 重复点 | 按成功处理 | 已点再 PUT 返回 200 与当前数 | 无特殊处理：用返回值对账 |
| 「我点过没有」 | 有会话时返回 | 评论、经验的 `helpful: boolean \| null`，没有会话或占位为 `null` | `null` 当作未登录（第 5.1 节） |
| 占位 | 不显示数字、不能点 | 占位 `helpfulCount = 0`、`helpful = null` | 占位分支本来就不渲染操作行，不变 |
| 期刊列表 `total` | 总条数 | Java `long`（基本类型）；仓库的 Long → String 转换器只注册了 `Long.class` | zod 写 `z.coerce.number()`，数字、数字字符串都收；部署后用一次 curl 核对（计划 Task 0） |
| 经验列表默认排序 | 最新投稿（不写进地址） | `sort` 可不传，默认 `latest` | 门户总是显式传 `sort`，地址里 `latest` 不写 |

## 4. 接口与调用层

### 4.1 后端接口（前缀 `/patra-interaction`）

| 用途 | 后端 | 门户怎么调 |
|---|---|---|
| 点 / 取消评论的有帮助 | `PUT` / `DELETE /comments/{id}/helpful` → `{ helpful, helpfulCount }` | Server Action `setHelpful({ type: "comment", id }, on)` |
| 点 / 取消经验的有帮助 | `PUT` / `DELETE /posts/{id}/helpful` | 同上，`type: "post"` |
| 讨论列表 | `GET /discussions/{type}/{key}/threads?sort=asc\|latest\|helpful&cursor=` | 现有 `fetchThreads` / Route Handler，`sort` 多一个取值 |
| 期刊经验列表 | `GET /journals/{id}/posts?limit=10&sort=latest\|helpful&cursor=` → 多了 `total` | `fetchJournalExperiences(journalId, sort, cursor)`；Route Handler 转发 `sort` |
| 其余读（单楼、回复、对话、经验详情、主页列表） | 评论 / 经验多了两项 | 只改 schema |

### 4.2 Schema

- `commentSchema` 加 `helpfulCount: z.number().int().nonnegative()`、`helpful: z.boolean().nullable()`。`toPlaceholder`（`lib/discussion/cache.ts`）同步置 `0` / `null`。
- `experienceSchema` 加同样两项（`replyCount` 留给 PAP-135）。
- `experiencePageSchema`（只给期刊列表用）加 `total: z.coerce.number().int().nonnegative()`。主页列表 `userExperiencePageSchema` 不变。
- `DiscussionSort` 改为 `"asc" | "latest" | "helpful"`；新增 `ExperienceSort = "latest" | "helpful"`（`experience-schema.ts`）。
- 新文件 `src/lib/interaction/helpful-schema.ts`：`helpfulTargetSchema`（`{ type: "comment" | "post", id }`）、`helpfulResultSchema`（`{ helpful: boolean, helpfulCount: number }`）。不加 `server-only`：客户端组件用类型。

### 4.3 Server Action（`src/lib/interaction/helpful-actions.ts`，`"use server"`）

```ts
export type HelpfulFailure =
  | { ok: false; reason: "email-unverified" | "account-restricted" | "daily-limit" | "gone" | "self" }
  | CommonFailure; // signed-out | unavailable
export type HelpfulActionResult = { ok: true; result: HelpfulResult } | HelpfulFailure;
export async function setHelpful(target: HelpfulTarget, on: boolean): Promise<HelpfulActionResult>;
```

照 `discussion-actions.ts`：入参不合格（类型不在两种之内、ID 不是实体 ID、`on` 不是布尔）直接 `UNAVAILABLE`、不打网关；走 `mutate`，`classify` 按 `status` + `traits[]` 归类（429 → `daily-limit`；409 → `email-unverified` / `account-restricted`；404 + `HELPFUL_TARGET_NOT_FOUND` → `gone`；403 → `self`）；认不出的由 `mutate` 记日志按 `unavailable`。一个函数管点与取消（`on` 决定 `PUT` / `DELETE`），两边返回同一形状。

### 4.4 文件落点

| 新增 | 用途 |
|---|---|
| `src/lib/format-count.ts` | `formatCount(n)`：千分位（`1,204`），0 返回 `""`（简报 §5；PAP-135 复用） |
| `src/lib/unique-by.ts` | `uniqueBy(items, keyOf)`：保留第一次出现的 |
| `src/lib/write-query.ts` | 从 `use-discussion-location.ts` 挪出 `writeQuery`（改地址查询参数、保留路径与片段、不触发服务端渲染），讨论与经验共用 |
| `src/lib/interaction/helpful-schema.ts` / `helpful-actions.ts` | 第 4.2、4.3 节 |
| `src/lib/helpful/messages.ts` | 失败文案（第 5.3 节） |
| `src/lib/helpful/cache.ts` | 写回缓存与标过期（第 6 节） |
| `src/lib/helpful/use-helpful-toggle.ts` | 乐观状态机（第 5.2 节） |
| `src/lib/experience/use-experience-sort.ts` | 期刊页经验排序以地址为准（第 8.1 节） |
| `src/components/helpful/HelpfulButton.tsx` | 第 5 节 |
| `src/components/portal/SortToggle.tsx` | 从 `DiscussionHeader` 抽出的分段控件（`fieldset` + `legend` + `aria-pressed` 按钮），讨论区三项、经验列表两项共用 |

| 改动 | 内容 |
|---|---|
| `lib/discussion/location.ts` | `parseDiscussionSort(value)`：`latest` / `helpful` 认，其余 `asc`；`viewOf` 与 threads Route Handler 都用它（现在两处各写一遍映射） |
| `lib/discussion/use-discussion-location.ts` | `setSort`：`asc` 删参数，其余写入 |
| `lib/discussion/cache.ts` | `insertRoot` 的 `helpful` 与 `latest` 相同（插到最前）；文件头「不乐观」的注释改为「写操作不乐观；有帮助例外，见 `lib/helpful/cache.ts`」 |
| `components/discussion/discussion-parts.tsx` | `DiscussionHeader` 改用 `SortToggle`，三项 `正序 / 最新 / 最有帮助` |
| `components/discussion/CommentItem.tsx` | 操作行最前放 `HelpfulButton`；`CommentActions` 加 `onHelpfulGone` |
| `components/discussion/DiscussionView.tsx`、`SingleThreadView.tsx` | `onHelpfulGone` 的对账；列表按 `commentId` 去重后渲染 |
| `components/experience/ExperienceRow.tsx` | 改结构（第 7.2 节） |
| `components/experience/ExperienceArea.tsx` | 工具行、排序、去重（第 8 节） |
| `components/experience/PostView.tsx` | 胶囊形按钮 |
| `lib/interaction/experience.ts`、`lib/experience/{queries,server}.ts`、`app/api/journals/[id]/posts/route.ts`、`app/journals/[id]/page.tsx`、`components/experience/{ExperienceSection,Experiences}.tsx` | `sort` 贯通（第 8.1 节） |
| `lib/query-keys.ts` | `experienceList(journalId, sort)`，加前缀 `experienceListAll(journalId)` |

## 5. `HelpfulButton`

### 5.1 输入与渲染

```ts
interface HelpfulButtonProps {
  target: HelpfulTarget;          // { type: "comment" | "post", id }
  helpful: boolean | null;        // 服务端的「我点过没有」；null = 这次请求没有会话
  count: number;                  // 服务端的有帮助数
  own: boolean;                   // comment.mine / experience.mine（含匿名经验的作者、作者在自己匿名经验下的评论）
  variant: "text" | "pill";       // 评论操作行、经验行 / 经验页
  onGone?: () => void;            // 点的时候目标已删除：提示结束后调用，由所在位置对账
}
```

| 条件 | 渲染（简报 §3.1 状态 ID） |
|---|---|
| `own` 且显示数为 0 | 不渲染（`helpful.own.zero`） |
| `own` | 纯文本 `N 人觉得有帮助`，不是按钮（`helpful.own`）；胶囊形同样是纯文本、`text-sm` |
| 其余 | `<button aria-pressed>`：`有帮助` + 数字（为 0 不写，`helpful.idle.zero` / `idle`）；已点（`helpful.on`）陶土色填充、图标放大 1.1 倍（`motion-safe:`） |

- 读屏名：未点 0 `有帮助`；有数 `有帮助，3 人觉得有帮助`（数字 `aria-hidden`，另放一段 `sr-only`）；`aria-pressed` 表示已点。
- 请求期间 `aria-busy="true"`，再点忽略（不排队）。
- 数字用 `formatCount`，等宽 `tabular-nums`；图标 lucide `ThumbsUp`（文字形 14px、胶囊形 16px）。
- 「查看对话」对话框里的评论只读（`readOnly`），不渲染操作行，按钮自然不出现；原地编辑时同理。

### 5.2 点击流程（`useHelpfulToggle`）

身份判断都在按钮里，不经组件层层传访客（`CommentItem`、`ExperienceRow`、`ProfileExperiences` 现在都拿不到 `ViewerSeed`）：

1. `helpful === null`（服务端说这次没有会话）或 `useCurrentUser()` 为匿名 → `useLoginRedirect()` 去 `/login?next=<当前地址>`，回来后不自动点（`helpful.anon`）。
2. 当前用户已取回且 `emailVerified === false` → 不发请求、不改数字，行内琥珀色提示 + `ResendVerificationButton`（`helpful.unverified`）。当前用户还没取回时照常发，服务端 409 `EMAIL_UNVERIFIED` 走同一条提示。
3. 其余：立刻显示 `!helpful`、数字 ±1，调 `setHelpful(target, !helpful)`。
   - 成功：显示返回值；`patchHelpful` 写回所有含这条的缓存，`markHelpfulStale` 标过期（第 6 节）。
   - `signed-out`：退回，去登录。
   - 其余失败：退回点击前，行内提示 4 秒（第 5.3 节）；`gone` 在提示结束时调 `onGone`。

**本地状态（我定的）**：hook 存 `pending`（请求中的乐观值）与 `settled = { base, value }`（`base` 是点击时的服务端值，`value` 是返回值）。显示规则：请求中显示 `pending`；否则 `settled` 存在且当前 props 的值与 `base` 相同时显示 `settled.value`；否则显示 props。这样：

- 列表里写回缓存后 props 变成返回值、不再等于 `base`，自动以缓存为准；
- 经验页的 props 来自 RSC、不会变，按钮一直显示返回值，直到页面刷新带来新的 props；
- 请求期间有一次重取落地（props 变了）不会把按钮翻回去；请求结束后若 props 已不等于 `base`，以 props 为准；
- 身份变化：讨论、经验缓存被清、重取后的 props 带新访客的 `helpful`，不等于 `base`，不会留着上一位的高亮。

### 5.3 失败提示（行内，`role="status"`，4 秒，不抢焦点，不用 toast）

| reason | 状态 ID | 文案 | 颜色 |
|---|---|---|---|
| `unavailable` / `self` | `helpful.error.network` | 点：`没有标记成功，请稍后再试`；取消：`没有取消成功，请稍后再试` | `rust-500` |
| `daily-limit` | `helpful.error.daily` | `今天已经点了 300 次有帮助，明天再来吧` | `rust-500` |
| `account-restricted` | `helpful.error.ineligible` | `账号状态异常，暂时不能点有帮助` | `rust-500` |
| `email-unverified` | `helpful.error.unverified` | `验证邮箱后才能点有帮助` + `重新发送验证邮件` | `amber-700` |
| `gone` | `helpful.error.gone` | 评论：`这条评论已被删除`；经验：`这条投稿经验已被删除` | `rust-500` |

播报区（`aria-live="polite"`）常驻、不隐藏，提示出现时才在里面挂上 `role="status"`：一页有很多按钮，空的 status 会和区块自己的播报冲突。同一按钮上新的一次点击会替换还在显示的提示并重新计时。卸载时清定时器。前端拦下的未验证提示（5.2 第 2 步）同样 4 秒，但指针或焦点在提示里（正在点「重新发送验证邮件」）时暂停计时，离开后重新计 4 秒（我定的）：否则重发按钮自己的「已发送」反馈会随提示一起消失。

## 6. 缓存：写回与过期（决策 D）

### 6.1 写回（`patchHelpful(client, target, value)`）

照 `patchItemStatus`（`lib/library/cache.ts`）用 `setQueriesData` 按目标 ID 改所有已缓存的副本，纯函数做数据变换，不改入参：

| 目标 | 查询（谓词） | 数据形状 |
|---|---|---|
| 评论 | `["discussion", type, key, "threads", sort]`（`queryKey[3] === "threads"`）与 `[..., "single", id]` | `ThreadsData` / `SingleData`：根评论与回复里 `commentId` 相同的（同一条在去重前可能出现两次，都改） |
| 经验 | `["experience", "journal", id, "list", sort]` 与 `["experience", "user", userId]` | 期刊列表 `InfiniteData<ExperiencePage>`、主页 `InfiniteData<UserExperiencePage>` |

不先 `cancelQueries`（我定的）：现有写操作的 `update()` 会取消在途请求，对 ±1 来说会把用户正在点的「加载更多」打断。代价是一次恰好在途的旧响应可能把数字盖回旧值，下次重取对齐——计数「晚几秒对得上不影响使用」（决策 D）。

### 6.2 标过期（`markHelpfulStale(client, target)`）

- 评论：`invalidateQueries({ queryKey: queryKeys.discussion, predicate: threads 或 single, refetchType: "none" })`——正在看的列表不重取（点有帮助不重排，简报 §3.5），切排序、重新挂载时按新数字取。
- 经验：期刊列表与主页列表同样 `refetchType: "none"`。经验页是 RSC，没有缓存要改；它的按钮成功后也标期刊与主页列表过期，回到列表时重取。
- 不轮询（决策 D）。

### 6.3 目标已删除（`onGone`）

| 位置 | 对账 |
|---|---|
| 讨论列表 | 同 `onDeleted` 的后半段：`fetchThreadOf` 重取这一楼 → `replaceThread`，整楼没了就 `applyDeletion` 去掉；再 `markDiscussionStale` |
| 单楼视图 | `invalidateQueries` 单楼 key（正在看，立刻重取） |
| 期刊页 / 主页经验行 | `invalidateQueries` 这份列表（立刻重取，这条从列表消失） |
| 经验页 | `router.refresh()`（变成「该经验已删除」） |

### 6.4 身份变化（2026-10-30 终审发现）

- 换排序时留着上一份的 `placeholderData` 只在上一份的查询仍在缓存里时生效（`lib/keep-previous.ts`，讨论与经验列表共用）：当前用户一变，按用户区分的缓存被清掉，不再拿上一位访客的列表（他的「我点过没有」「这条是我的」）当占位。
- 按钮在请求在途时换了访客：回来的结果不写缓存、不显示、不提示；成功后的返回值记着是谁点的，换了访客就不再显示。
- 成功后的返回值在 props 第一次变化时就丢掉（不只比值）：props 变了又碰巧回到点击时的值，不会误显示旧结果。

## 7. 各位置落地

### 7.1 评论

`CommentItem` 非占位分支的操作行：`[HelpfulButton text] [回复] [查看对话]`。`own = comment.mine`，`onGone = () => actions.onHelpfulGone(comment)`。占位、`readOnly`、`editing` 三种情况本来就不渲染操作行。

### 7.2 经验行（`ExperienceRow`，期刊页与主页两种变体）

现在整条是一个 `<Link>`，按钮放不进去。改为：

- 外层 `<article className="relative …">`；标题行的文字（期刊页是结果行、主页是期刊名）是 `<Link href="/posts/<id>" className="link-draw …">`，用 `after:absolute after:inset-0` 铺满整行，整行照样可点、悬停整行时标题下划线照样绘出。焦点环沿用全局 `a:focus-visible`，画在标题链接上（我定的：设计系统守护测试禁止组件自带焦点环类，画不到伪元素上）。
- 其余行（月份、详情、摘录、署名）留在 `article` 里，是普通文本。
- 新增底部行 `relative z-1`：本 Issue 只放 `HelpfulButton text`（`own = experience.mine`；主页本人视角全是自己的内容）。按钮不渲染时（自己的内容且为 0）整行不出。PAP-135 在这一行追加回复数。
- 链接的读屏名从「整行文字」缩成标题行；现有单测（`getByRole("link")` 取整行）与 e2e 的行定位（`tests/e2e/support/experience.ts` 按月份文字找 link）要随之改成先找 `article` 再取链接。

### 7.3 经验页

`PostView` 展示态在时间线、定义表、正文之后放胶囊形按钮（`own = experience.mine`）。编辑态不显示。`onGone = router.refresh`。已删除的经验页由 `PostDeleted` 渲染，本来就没有按钮。

### 7.4 主页

`ProfileExperiences` 不改，经验行自带按钮。

## 8. 两处排序

### 8.1 地址 → 首屏 → 客户端

**讨论区**：已有整条链路，只加取值。`parseDiscussionView`（RSC）与 `useDiscussionLocation`（客户端）共用 `viewOf`；threads Route Handler 改用 `parseDiscussionSort`。

**期刊页经验列表**（新链路）：

```
JournalDetailPage(searchParams) ─ parseExperienceSort(search.sort) ─▶ ExperienceSection(sort) ─▶ Experiences(sort)
  └▶ prefetchJournalExperiences(journalId, sort) ─▶ queryKeys.experienceList(journalId, sort) 放进缓存（只作初值）
ExperienceArea ─ useExperienceSort()（useSearchParams）─▶ useJournalExperiences(journalId, sort)
  └▶ fetch(`/api/journals/${id}/posts?sort=…&cursor=…`) ─▶ Route Handler ─▶ fetchJournalExperiences(id, sort, cursor)
```

- `parseExperienceSort`：`helpful` 认，其余 `latest`；同名参数出现多次不认（同 `parseDiscussionView`）。
- `useExperienceSort().setSort`：`latest` 删参数、`helpful` 写入，`replaceState`（`writeQuery`），片段 `#experiences` 保留，不触发服务端渲染。
- 期刊页只有经验区用 `?sort=`，文献页、经验页只有讨论区用，不冲突。
- `afterPublish` 原来按精确 key 重置列表，改为按前缀 `experienceListAll(journalId)` 重置两种排序。

### 8.2 讨论区「最有帮助」

- `SortToggle` 三项 `正序 / 最新 / 最有帮助`，375 宽一行放下（e2e 量）；标题与控件之间允许换行兜底（现在的 `justify-between` 外层加 `flex-wrap`）。
- 根评论顺序由后端排；楼内回复仍正序（后端保证，门户不排）。
- 点有帮助不重排：缓存里只改数字（第 6 节）。
- 新发表的根评论：`insertRoot(…, "helpful")` 插到第一页最前并高亮 2 秒，与「最新」相同；不出「已发表，去看看」。
- 切换排序沿用 `keepPreviousData`：另一种排序若已缓存且过期，先显示缓存再按新数字重取。

### 8.3 期刊页工具行与「最有帮助」

- 汇总卡与列表之间一行：左 `N 条投稿经验`（`text-sm fg-2`，取第一页的 `total`），右 `SortToggle` 两项 `最新投稿 / 最有帮助`。`total ≤ 1` 不出控件，`total = 0` 整行不出（空状态不变），降级时不出。
- 换排序 = 从第一页重新取（简报 §3.8）：`setSort` 先 `removeQueries` 目标排序的缓存（精确 key）再改地址；取回前留着当前列表，不闪空（`placeholderData`，见 6.4）。
- 换到另一种排序取不到时不整块降级（整块降级只认首屏的排序）：工具行用上一次取到的总条数照常显示，列表处就地提示 `没有加载成功，请稍后再试` 与「重试」，可以切回原来的排序（2026-10-30 终审发现）。
- 点有帮助不重排（同上）。

### 8.4 翻页去重（决策 D）

两个列表都在展开页面时 `uniqueBy`：讨论按 `root.commentId`、经验按 `postId`，保留第一次出现的。两种排序都做（键集分页在 `asc` / `latest` 下本来不会重复，做了也无害，省一个分支）。缓存数据不去重：写回、展开回复等纯函数照旧作用于每个副本。

保留第一次出现的是有意的：位置以用户已经看到的为准，不让已显示的一楼在翻页后跳走；后一页里那份的数字若更新，页面显示的是先加载那份的数字（本人点过的已写回，两份一致；别人点的要等下次重取），属于决策 D 的「晚几秒对得上」。

## 9. 测试（对照 AC）

| AC | 测试 |
|---|---|
| 点击后数字立刻变化，刷新后保持；取消减 1；接口失败回滚并提示 | `tests/components/helpful/HelpfulButton.test.tsx`（挂起的 action 期间断言乐观值与 `aria-busy`；成功显示返回值；各失败退回 + 文案；4 秒后提示消失，用假定时器）；`tests/lib/helpful/cache.test.ts`（写回与标过期）；e2e 刷新后保持 |
| 自己的内容没有可点的按钮，只显示数字；未登录点击跳登录；邮箱未验证有提示 | `HelpfulButton.test.tsx`（`own` 有数 / 为 0、`helpful = null` 跳登录、当前用户未验证不调 action）；e2e 三种身份 |
| 「该评论已删除」占位不显示数字、不能点 | `CommentItem.test.tsx`；e2e 删成占位后断言 |
| 讨论区切「最有帮助」后根评论按数字降序、楼内回复仍按时间；经验列表能切两种排序；刷新与分享链接后排序保持 | `tests/lib/discussion/{server,use-discussion-location,cache}.test`；threads 与 posts 两个 Route Handler 的测试；`DiscussionView.test.tsx`（三项、地址、插入、去重）；`ExperienceArea.test.tsx`（工具行、切换写地址、从第一页取、去重）；`tests/app/journal-experiences.test.tsx`（首屏按地址取）；e2e 刷新与新标签页打开同一地址 |
| 单测、类型检查、lint 通过；Playwright 两种尺寸通过 | `pnpm typecheck && pnpm lint && pnpm test`；`pnpm test:e2e tests/e2e/helpful.spec.ts`（第 10 节） |

## 10. Playwright（`tests/e2e/helpful.spec.ts`）

- 连 mini 上的后端（本机门户 + `PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528`，同 discussion / experience spec），`--workers=1`。
- 账号：A = `PATRA_E2E_VERIFIED_*`、B = `PATRA_E2E_VERIFIED_B_*`（都已验证）；不依赖 C（项目记忆记录 C 已注销）。邮箱未验证用现注册的新账号；未登录用无会话的上下文。
- 两个尺寸各跑一遍完整流程（照 `discussion.spec.ts` 循环 `VIEWPORTS`，release spec D4 要求两种尺寸都跑通；各自准备、清理数据）：A 在一篇没有讨论的文献下先后发两条根评论（第一条带 A 自己的一条回复）；B 给第一条根评论与那条回复点 → 立刻 `有帮助 1`、刷新保持；「最新」下第二条在前、切「最有帮助」后第一条在前，楼内回复顺序不变；地址带 `sort=helpful`，新页面打开同一地址仍是该排序；B 取消回复的 → 回到 `有帮助`；A 视角根评论是 `1 人觉得有帮助`、没有按钮；访客点 → 跳 `/login?next=`；未验证账号点 → 琥珀提示、数字不变；A 删掉第一条根评论（有回复，变占位）→ 占位没有按钮与数字。经验：A 在一本没有经验的期刊先后写两条（投稿日期不同，较早的一条投稿日期也较早，避开「重复投稿日期」规则），出现 `2 条投稿经验` 与排序控件（`total ≥ 2`）；B 在期刊页给较早的那条点 → `有帮助 1`；「最新投稿」下它在第二、切「最有帮助」后在第一，地址带 `sort=helpful`、刷新保持；经验页胶囊形按钮为已点；A 的主页经验行是 `1 人觉得有帮助`。
- 375×812 另外量：三项排序一行放下、文字形按钮高度 ≥ 32px、胶囊形 ≥ 44px；两个尺寸都断言经验行底部按钮点了 URL 不变。
- 收尾（`afterAll`，无论成败）：B 取消全部有帮助，A 删评论与经验。
- 每天 300 次、账号状态异常不在 e2e 里测（单测覆盖文案，后端 IT 覆盖规则）。

## 11. 我定的与待拍板

**我定的**（读的时候请核对）：

1. `N 条投稿经验` 放本 Issue（第 2 节例外）。
2. 访客与邮箱验证状态由按钮自己读（`helpful === null` + `useCurrentUser()`），不经组件传 `ViewerSeed`（5.2）。
3. 本地状态用「点击时的服务端值做基准」的规则，经验页靠它显示返回值，不为它加缓存（5.2）。
4. 写回不取消在途请求（6.1）。
5. 目标已删除时先显示 4 秒提示，再由所在位置对账（6.3）。
6. 去重两种排序都做，只在渲染时做（8.4）。
7. 经验列表换排序时清掉目标排序的缓存、从第一页取；讨论区沿用现有行为（切回已缓存的排序先显示缓存再重取）（8.2、8.3）。
8. `total` 用 `z.coerce.number()`；`helpful` 按后端 spec 写 `.nullable()`（JSON 里是 `null`，不是缺字段）（第 3 节）。
9. `writeQuery` 挪到 `src/lib/write-query.ts`，`SortToggle` 从 `DiscussionHeader` 抽出（4.4）。
10. 未验证提示在指针 / 焦点停留时暂停计时（5.3）。
11. 翻页去重保留先加载的那份（8.4）。
12. e2e 的期刊写两条经验，排序控件才出现（第 10 节）。
13. 自己的内容的图标用 `fg-3`，不用简报的 `fg-4`（守护测试只许占位符用 `fg-4`）；经验行焦点环画在标题链接上（7.2）。
14. 经验行的「目标已删除」对账重取全部期刊页与主页的经验列表（`refetchPostLists`），不只当前那份。

**待拍板**：无。简报 §10 的十条与决策 C、D 已覆盖所有会改变做法的选择。
