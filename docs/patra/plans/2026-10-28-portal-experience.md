# 门户期刊投稿经验（PAP-125）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（Patra 约定：在当前会话内联执行，不用 subagent-driven）to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 期刊页经验区（汇总 + 列表 + 原地表单）、经验页 `/posts/[id]`（详情、编辑、删除、修改历史、回复区）、用户主页「投稿经验」标签。

**Architecture:** 照 PAP-124：RSC 取首屏（登录可选、令牌被拒按访客重取），`DiscussionCache` 只作初值放进 TanStack 缓存，客户端组件接管切换与翻页（Route Handler），写走 Server Action，写后先 `cancelQueries` 再失效。纯逻辑（时长写法、一致性规则、表单 schema、纯文本摘录）放 `lib/experience/`，先于组件完成。

**Tech Stack:** Next.js 16 App Router（webpack）/ React 19 / TypeScript strict / TanStack Query v5 / React Hook Form + Zod / Tailwind v4 + base-ui / Vitest + Testing Library / Playwright。

**Spec:** `docs/patra/specs/2026-10-28-portal-experience-design.md`（下称 spec）；文案与状态 ID 以设计简报 `patra-portal/docs/design-briefs/v0.14/discussion.md` 为准；接口以共享笔记 `interaction-PAP-122-handoff.md` 第 3 节为准。

## Global Constraints

- 工作目录 `patra-portal/`，命令都在这里跑；路径别名 `@/` = `src/`、`@tests/` = `tests/`，不写 `../../`。
- 不 commit、不 push：每个任务结束只跑验证；全部完成、用户本地验收同意后一次提交（只加自己的路径，中文 subject 以动词开头）。
- 共享文件 `src/lib/query-keys.ts`、`src/lib/query-client.ts`、`src/lib/identity/problem.ts` 只追加，改动集中在自己的小节（PAP-126 并行改前两个）。
- 对 PAP-124 文件只改一处：`readPublic` 从 `src/lib/interaction/discussion.ts` 挪到 `src/lib/interaction/read.ts` 并导出。
- 界面不出现「帖子」「通知」二字；不显示回复数、经验数以外的计数。
- 选填没填发 `null`，不发 `false` / `0`；`anonymous` 只在新建时发。
- 读超时 `READ_TIMEOUT_MS`（5 秒）；「今天」按 `Asia/Shanghai`；日期下限 `1990-01-01`。
- 设计系统守护测试的坑（PAP-124 交接第 5 节）：`animate-pulse` 加 `motion-safe:`；`text-fg-4` 只给占位符；不写 `focus-visible:shadow-none`；`<fieldset` 后第一个 className 带 `min-w-0`；文件名里别出现 `text-xxx`。
- 客户端 `fetch` 的地址写成以 `/` 开头、与 `fetch(` 同一行的模板字面量（`tests/lib/gateway-boundary.test.ts`）。
- 被 RSC 引用的 `lib/experience/*` 模块不 import `useSearchParams` 等客户端 hook（`next build` 才报）。
- 不用 JetBrains MCP（worktree 不是 IDEA 打开的检出）。

## Review Focus

1. **两个标签页 / 别的设备上同时写**：表单联动挡不住服务端仍回 `INCONSISTENT` / `NOT_APPLICABLE` / `DUPLICATE`，应落到对应字段或底部提示条，不能吞掉成「请稍后再试」。→ Task 6 加用例「服务端回 finalOutcome INCONSISTENT 时显示在最终结果下」。
2. **身份在页面上变化**（另一个标签页退出 / 换号）：期刊页列表里「你写的」「匿名」胶囊与按钮身份要跟着变，不能留上一位的 `mine`。→ Task 5 的 `query-client` 用例 + Task 7 用例「当前用户变为匿名后按钮变成登录后写投稿经验」。
3. **发布时恰好有一次在途的列表 / 汇总请求**：旧响应晚到不能把新经验冲掉。→ Task 7 用例「挂起一次汇总重取后发布，最终显示新条数」（照 `tests/components/discussion/cache-races.test.tsx`）。
4. **作者已注销 / 主页对访客不可见 / 期刊已失效**：署名不链接、期刊链接变纯文字，不出现 `/u/null`、`/journals/null`。→ Task 9、Task 10 各有用例。
5. **很长的正文、全是 Markdown 标记的正文**：列表两行摘录与页面 `description` 不出现 `**`、`>`、`[`](`，空正文不渲染块。→ Task 2 `plain.ts` 用例 + Task 8 元数据用例。

---

### Task 0: 准备 worktree

- [ ] **Step 1:** `pnpm install --frozen-lockfile`（worktree 里还没有 `node_modules`）。
- [ ] **Step 2:** 从主检出拷 `/Users/linqibin/Projects/Products/patra/.claude/launch.json` 到本 worktree 的 `.claude/launch.json`，端口改成 4002（4000 可能被 PAP-126 占用），保留剥代理变量与网关指 mini 的配置。
- [ ] **Step 3:** `pnpm typecheck && pnpm test` 记下基线（预期全过，约 2,700 条）。

### Task 1: 时长、日期、标签的写法（`labels.ts`、`format.ts`）

**Files:** Create `src/lib/experience/labels.ts`、`src/lib/experience/format.ts`；Test `tests/lib/experience/format.test.ts`、`tests/lib/experience/labels.test.ts`

**Interfaces — Produces:**
- `FIRST_DECISION_LABELS: Record<FirstDecision, string>`（直接拒稿 / 审后拒稿 / 大修 / 小修 / 录用）、`FINAL_OUTCOME_LABELS`（录用 / 拒稿 / 撤稿 / 进行中）、`ARTICLE_TYPE_LABELS`（原创研究 / 综述 / 系统综述 / Meta 分析 / 病例报告 / 短篇通讯 / 其他）、`ARTICLE_TYPES`（按这个顺序的数组）。枚举类型先在本文件用字面量联合定义：`FirstDecision`、`FinalOutcome`、`ArticleType`，Task 3 的 zod 枚举从它们派生（`z.enum(FIRST_DECISIONS)`）。
- `formatDays(days: number): string` → `42 天`；`formatWeeksHint(days: number): string | null` → `days >= 28` 时 `约 ${Math.round(days / 7)} 周`，否则 null。
- `formatDateCn("2026-03-02")` → `2026 年 3 月 2 日`；`formatSubmittedMonth("2026-03-02")` → `2026 年 3 月投稿`。
- `formatOutcomeLine(e: Pick<Experience, "firstRoundDays" | "firstDecision" | "finalOutcome">): string`：终局首轮（直接拒稿、审后拒稿、录用）→ `首轮 9 天 · 直接拒稿`；否则 `首轮 42 天 · 大修  →  录用`（箭头两侧各两个空格；进行中 `→ 进行中`）。
- `formatPercent(0.5)` → `50%`；`formatRange(28, 63)` → `28–63 天`。
- `summaryScopeLine(count: number, window: "3y" | "all"): string` → `15 条经验 · 近 3 年（按投稿日期）· 首轮用时不含直接拒稿` / `20 条经验 · 全部 · 首轮用时不含直接拒稿`。

- [ ] **Step 1:** 写失败测试：上面每个函数各取 spec / 简报 §3.22 的示例值断言；`formatWeeksHint(27)` 为 null、`formatWeeksHint(28)` 为 `约 4 周`、`formatWeeksHint(42)` 为 `约 6 周`；`formatOutcomeLine` 五种首轮 × 代表性最终结果；三个 label 表覆盖每个枚举值。
- [ ] **Step 2:** `pnpm vitest run tests/lib/experience` → FAIL（模块不存在）。
- [ ] **Step 3:** 实现。日期一律按字符串拆分，不经 `Date`（避免时区漂移）。
- [ ] **Step 4:** 同一命令 → PASS。

### Task 2: 纯文本摘录（`plain.ts`）

**Files:** Create `src/lib/markdown/plain.ts`；Test `tests/lib/markdown/plain.test.ts`

**Interfaces — Produces:** `markdownToPlain(source: string): string`（去标记、空白折叠成单个空格、去首尾空白）；`excerpt(source: string, max: number): string`（`markdownToPlain` 后按字素截断，超出加 `…`）。

- [ ] **Step 1:** 失败测试：`**粗** _斜_` → `粗 斜`；`> 引用\n- 列表\n1. 有序` → `引用 列表 有序`；`` `code` `` → `code`；`[文字](https://x.y)` → `文字`；`![图](u)` → `图`；`# 标题` → `标题`；连续空行 → 单空格；`excerpt("一二三四五", 3)` → `一二三…`；emoji 组合不被截半（用 `graphemeCount` 同口径）。
- [ ] **Step 2:** 运行 → FAIL。
- [ ] **Step 3:** 实现：用已装的 `mdast-util-from-markdown` 解析后取 `toString`（`mdast-util-to-string` 已作为传递依赖存在则加为直接依赖，否则遍历文本节点拼接）；不用正则硬剥。
- [ ] **Step 4:** 运行 → PASS。

### Task 3: 一致性规则与表单 schema（`rules.ts`、`form-schema.ts`、`messages.ts`）

**Files:** Create `src/lib/experience/rules.ts`、`src/lib/experience/form-schema.ts`、`src/lib/experience/messages.ts`；Test `tests/lib/experience/rules.test.ts`、`tests/lib/experience/form-schema.test.ts`、`tests/lib/experience/messages.test.ts`

**Interfaces — Produces:**
- `todayInShanghai(now: number): string`（`yyyy-MM-dd`）；`MIN_DATE = "1990-01-01"`。
- `constraintsFor(firstDecision: FirstDecision | "", finalOutcome: FinalOutcome | ""): FormConstraints`，其中
  ```ts
  interface FormConstraints {
    lockedFinalOutcome: FinalOutcome | null;      // 终局首轮时自动选定的最终结果
    finalDecisionOn: "enabled" | "terminal" | "in-progress";   // 后两种禁用并清空
    reviewRounds: "enabled" | "none" | "one";    // none：直接拒稿禁用并清空；one：固定为 1
    reviewQuality: "enabled" | "none";
  }
  ```
- `ExperienceFormValues`（全字符串：`submittedOn`、`firstDecisionOn`、`finalDecisionOn`、`firstDecision`、`finalOutcome`、`articleType`、`reviewRounds`、`reviewQuality`、`editorCommunication`、`apcPaid`（`"" | "yes" | "no"`）、`previouslyRejected`（同）、`body`、`anonymous: boolean`）；`EMPTY_FORM`；`fromExperience(e: Experience): ExperienceFormValues`。
- `experienceFormSchema(today: string)`：zod，错误消息就是界面文案（spec 6.4 / 简报 §4.3）；跨字段规则用 `superRefine`，问题挂在后一个字段上（`firstDecisionOn` 挂 `BEFORE_SUBMISSION` 文案等）。
- `toRequest(values, mode: "create" | "edit"): ExperienceRequest`：空串 → `null`；`reviewRounds` 等转数字；`apcPaid` `yes/no` → `true/false`；`body` 去首尾空白后为空 → `null`；`mode === "edit"` 时不带 `anonymous`。
- `messages.ts`：`fieldErrorMessage(field: string, code: string | null): string | null`（spec 6.4；认不出返回 null）；`experienceFailureMessage(failure, mode: "create" | "edit" | "delete"): string`（spec 4.3）；`CONSTRAINT_HINTS`（四句联动说明，spec 6.3）。

- [ ] **Step 1:** 失败测试 `rules.test.ts`：spec 6.3 联动表五行各一个用例（`DESK_REJECT` → `{ lockedFinalOutcome: "REJECTED", finalDecisionOn: "terminal", reviewRounds: "none", reviewQuality: "none" }`；`REJECT_AFTER_REVIEW` → `REJECTED` / `terminal` / `one` / `enabled`；`ACCEPT` → `ACCEPTED` / `terminal` / `one` / `enabled`；`MAJOR_REVISION` + `IN_PROGRESS` → `null` / `in-progress` / `enabled` / `enabled`；`MINOR_REVISION` + `ACCEPTED` → 全 enabled）；`todayInShanghai(Date.UTC(2026, 9, 27, 16, 30))` → `2026-10-28`。
- [ ] **Step 2:** 失败测试 `form-schema.test.ts`：空表 → 五个必填文案；`firstDecisionOn < submittedOn` → `首轮决定日期不能早于投稿日期`；未来日期 → `日期不能晚于今天`；`1989-12-31` → `日期不能早于 1990 年`；`reviewRounds: "11"` → `审稿轮数填 1 到 10 之间的整数`；正文 2,001 字素 → `正文最多 2,000 字`；`toRequest` 选填全空 → 各项严格等于 `null`（`toBeNull()`，不是 `false`）、`edit` 模式没有 `anonymous` 键；`fromExperience` 往返 `toRequest` 不变。
- [ ] **Step 3:** 失败测试 `messages.test.ts`：spec 4.3 每个 `reason` × 三种 mode 的文案；`duplicate` 文案里带格式化后的投稿日期（`formatDateCn`）；`fieldErrorMessage("finalOutcome", "INCONSISTENT")` 返回联动说明；未知码返回 null。
- [ ] **Step 4:** 运行三组 → FAIL。
- [ ] **Step 5:** 实现三个模块（表单 schema 的规则调用 `constraintsFor`，不重写一遍）。
- [ ] **Step 6:** 运行 → PASS。

### Task 4: 调用层（schema、服务端读、Route Handler、写操作、`Problem`）

**Files:**
- Create `src/lib/interaction/experience-schema.ts`、`src/lib/interaction/experience.ts`、`src/lib/interaction/experience-actions.ts`
- Create `src/app/api/journals/[id]/posts/route.ts`、`src/app/api/journals/[id]/posts/summary/route.ts`、`src/app/api/posts/[id]/revisions/route.ts`、`src/app/api/users/[id]/posts/route.ts`
- Modify `src/lib/interaction/read.ts`（接收 `readPublic`）、`src/lib/interaction/discussion.ts`（改为 import）、`src/lib/identity/problem.ts`（追加 `existingPostId`）
- Test `tests/lib/interaction/experience.test.ts`、`tests/lib/interaction/experience-actions.test.ts`、`tests/app/api/experience-routes.test.ts`、`tests/lib/identity/problem.test.ts`（追加用例）

**Interfaces — Produces:**
- schema 类型：`Experience`、`PostDetail`（`{ postId; deleted: boolean; journal: PostJournal; experience: Experience | null }`，`PostJournal = { key: string | null; state: string; title: string; abbreviation: string | null }`）、`Summary`、`ExperiencePage`（`{ items: Experience[]; nextCursor: string | null }`）、`Revision`、`UserExperience`（`{ journal: PostJournal; experience: Experience }`）、`UserExperiencePage`、`ExperienceRequest`、`SummaryWindow = "3y" | "all"`；zod schema 同名加 `Schema` 后缀。作者用 `cardSchema.nullable()`。
- `readPublic<S>(path, schema): Promise<Read<z.infer<S>>>`（从 `discussion.ts` 原样挪来）。
- `fetchJournalExperiences(journalId: string, cursor?: string): Promise<Read<ExperiencePage>>`（`limit=10`）、`fetchExperienceSummary(journalId: string, window: SummaryWindow): Promise<Read<Summary>>`、`fetchPost = cache((id: string) => Promise<Read<PostDetail>>)`、`fetchPostRevisions(id: string): Promise<Read<{ items: Revision[] }>>`、`fetchUserExperiences(userId: string, opts: { cursor?: string; limit: number }): Promise<Read<UserExperiencePage>>`。
- `publishExperience(journalId: string, request: ExperienceRequest): Promise<ExperienceResult>`、`editExperience(postId: string, request: ExperienceRequest): Promise<ExperienceResult>`、`deleteExperience(postId: string): Promise<{ ok: true } | ExperienceFailure>`；`ExperienceResult = { ok: true; detail: PostDetail } | ExperienceFailure`（spec 4.3）。
- Route Handler：`GET /api/journals/[id]/posts?cursor=`、`GET /api/journals/[id]/posts/summary?window=`（缺省 `3y`，非法 422）、`GET /api/posts/[id]/revisions`、`GET /api/users/[id]/posts?cursor=`（`limit=20`）。都 `export const dynamic = "force-dynamic"`，`readReply()` 回写。

- [ ] **Step 1:** 失败测试 `experience.test.ts`（照 `tests/lib/interaction/discussion.test.ts` 的桩写法）：每个读函数的路径与查询串；ID 非 `isEntityId`、`window` 非法时不请求、回 `not-found`；401 后按访客重取一次；schema：`deleted: true` 时 `experience: null` 能通过、`sufficient: false` 时四项为 null 能通过。
- [ ] **Step 2:** 失败测试 `experience-actions.test.ts`：spec 4.3 每一行的 HTTP + `traits` → `reason`（含 `existingPostId: "77"` 透传、`retryAfterSeconds`、422 带 `errors[]` → `invalid-fields`、写的 404 → `journal-gone`、改的 404 → `post-gone`、503 → `unavailable`、401 清 Cookie → `signed-out`）；参数不合格（`journalId: "abc"`、`request.submittedOn: 1`）不请求、回 `unavailable`；`deleteExperience` 204 → `{ ok: true }`。
- [ ] **Step 3:** 失败测试：`problem.test.ts` 追加 `existingPostId` 取字符串、非字符串为 null；`experience-routes.test.ts` 四个 Route Handler 的参数校验与透传。
- [ ] **Step 4:** 运行 → FAIL。
- [ ] **Step 5:** 实现；`readPublic` 挪动后 `pnpm vitest run tests/lib/interaction/discussion.test.ts` 仍全过。`classify` 的 404 区分写 / 改由调用处传入的 `notFoundReason` 决定。
- [ ] **Step 6:** 运行本任务全部测试 + `pnpm typecheck` → PASS。

### Task 5: 缓存层（queryKeys、身份清缓存、客户端查询、首屏预取）

**Files:** Modify `src/lib/query-keys.ts`、`src/lib/query-client.ts`；Create `src/lib/experience/queries.ts`、`src/lib/experience/server.ts`；Test `tests/lib/experience/queries.test.tsx`、`tests/lib/experience/server.test.ts`、`tests/lib/query-client.test.ts`（追加）

**Interfaces — Produces:**
- `queryKeys.experience*`（spec 5.1 原样）。
- `useExperienceSummary(journalId, window)`、`useJournalExperiences(journalId)`（`useInfiniteQuery`，`initialPageParam: null`、`getNextPageParam: (p) => p.nextCursor`）、`useUserExperiences(userId)`、`usePostRevisions(postId, enabled)`；错误态 `refetchOnWindowFocus: (q) => (q.state.status === "error" ? "always" : false)`（照 `lib/discussion/queries.ts`）。
- `afterPublish(client: QueryClient, journalId: string): Promise<void>`：先 `cancelQueries(experienceJournal)`，再汇总 `invalidateQueries`、列表 `resetQueries`。
- `afterEditOrDelete(client, { journalId, authorUserId, postId }): Promise<void>`：`cancelQueries` + `invalidateQueries` 三个 key（`authorUserId` 为 null 时跳过主页）。
- `prefetchJournalExperiences(journalId: string): Promise<DehydratedState>`：汇总 `3y` 与列表第一页并行，`ok` 的才 `setQueryData`。

- [ ] **Step 1:** 失败测试：`query-client` 身份变化后 `experience` 前缀的查询被移除；`afterPublish` 调用顺序（`cancelQueries` 先于 invalidate / reset，用 spy 记录）；挂起一次汇总请求后调 `afterPublish`，旧响应回来不覆盖新数据；`prefetchJournalExperiences` 一个失败一个成功时只放成功的那份。
- [ ] **Step 2:** 运行 → FAIL。
- [ ] **Step 3:** 实现（`query-keys.ts`、`query-client.ts` 各加一个带注释的小节）。
- [ ] **Step 4:** 运行 → PASS。

### Task 6: 表单 `ExperienceForm`

**Files:** Create `src/components/experience/ExperienceForm.tsx`、`src/components/experience/form-fields.tsx`（胶囊单选组、评分方块、日期字段，带 `清除选择`）；Test `tests/components/experience/ExperienceForm.test.tsx`

**Interfaces — Consumes:** Task 1、3、4。**Produces:**
```ts
type ExperienceFormProps =
  | { mode: "create"; journalId: string; journalTitle: string; onDone: (detail: PostDetail) => void; onCancel: () => void }
  | { mode: "edit"; postId: string; journalTitle: string; initial: Experience; anonymous: boolean; onDone: (detail: PostDetail) => void; onCancel: () => void };
```
提交调用 Task 4 的 action；`onDone` 由调用方决定收起、toast、缓存处理。

- [ ] **Step 1:** 失败测试（`getByRole` 优先，`now` 用固定时钟）：
  - 打开后焦点在 `投稿日期`；`带 * 的必填` 说明可见；文章类型下拉有 6 项。
  - spec 6.3 联动：选 `直接拒稿` → `最终结果` 的 `拒稿` 被选中、其余三项 disabled，`最终决定日期`、`审稿轮数`、审稿质量五个方块 disabled 且有对应说明；改回 `大修` → 最终结果全部可选且未选中、审稿轮数为空；`审后拒稿` → 审稿轮数值为 `1` 且 disabled；`大修` + `进行中` → 最终决定日期 disabled + `稿件还在进行中，不用填最终决定日期`。
  - 失焦校验单个字段；空表提交 → 五条必填文案、焦点在 `投稿日期`。
  - 两个日期有效时出现 `首轮用时 42 天`（`2026-03-02` → `2026-04-13`）；再填最终 `2026-06-01` → `· 投稿到最终决定 91 天`。
  - 选填单选再点一次取消；`清除选择` 按钮可用。
  - 提交成功 → action 收到的请求选填全为 `null`（只填必填时）、`anonymous: false`；按钮 `发布中…` 时整个 `fieldset` disabled。
  - 服务端回 `invalid-fields`（`finalOutcome` / `INCONSISTENT`）→ 文案显示在最终结果下（Review Focus 1）。
  - `duplicate`（`existingPostId: "77"`）→ 底部提示含 `你已经写过 2026 年 3 月 2 日投这本期刊的经验` 与链到 `/posts/77` 的 `查看那条经验`，焦点回 `投稿日期`。
  - `daily-limit`、`account-restricted`、`email-unverified`（含 `重新发送验证邮件`）、`unavailable` 各自文案；`signed-out` 且已填 → 确认 `登录已过期，去重新登录？已写的内容会丢失。`。
  - 取消：未填直接 `onCancel`；已填 → 对话框 `放弃这条投稿经验？`，`继续填写` 留下、`放弃` 调 `onCancel`。
  - 编辑模式：标题 `编辑投稿经验`、字段带原值、只读一行（匿名 / 实名两句）、按钮 `保存修改`；没改直接取消不弹确认；请求里没有 `anonymous`。
  - 正文超 2,000 字素 → 计数变红、提交 disabled（`MarkdownEditor` 自带，断言按钮 disabled 即可）。
- [ ] **Step 2:** 运行 → FAIL。
- [ ] **Step 3:** 实现：RHF `useForm({ resolver: zodResolver(experienceFormSchema(today)), mode: "onBlur" })`；联动用 `watch` 两个首轮 / 最终字段 + `constraintsFor`，在 `useEffect` 里 `setValue`（清空 / 置 1 / 选定）；布局照画板 `ExpForm`（桌面两列、≤ 880px 单列、胶囊高 36 / 44px）。
- [ ] **Step 4:** 运行 → PASS。

### Task 7: 期刊页经验区（汇总、列表、区块、接线）

**Files:**
- Create `src/components/experience/ExperienceSummary.tsx`、`ExperienceRow.tsx`、`ExperienceList.tsx`、`ExperienceArea.tsx`、`ExperienceSection.tsx`、`Experiences.tsx`
- Modify `src/components/portal/journal-detail/JournalDetailView.tsx`（加 `experiences?: ReactNode` 插槽，位置 spec 6.2）、`src/app/journals/[id]/page.tsx`
- Test `tests/components/experience/ExperienceSummary.test.tsx`、`ExperienceRow.test.tsx`、`ExperienceArea.test.tsx`、`tests/app/journal-experiences.test.tsx`

**Interfaces — Produces:** `ExperienceRow({ experience, variant: "journal" } | { item: UserExperience, variant: "profile" })`（Task 10 用）；`ExperienceSection({ journalId, journalTitle })`。

- [ ] **Step 1:** 失败测试 `ExperienceSummary`：简报 §3.13 六种状态（数据用后端交接的 `Summary` 示例：`median 42 / p25 28 / p75 63`、`deskReject 9`、`rate 0.5 / decidedCount 12`）→ `42 天`、`约 6 周 · P25–P75 28–63 天`、`9 天`、`50%`、`已完结的 12 条：录用 6、拒稿 6`、`录用 6 · 拒稿 6 · 撤稿 1 · 进行中 2`、口径行；`few`（`count: 3`）→ `3 条` + `近 3 年有 3 条投稿经验，满 5 条后显示统计。`；`window-empty` → `看全部 12 条` 点后汇总查询的 `window` 变 `all`；`empty` 没有分段控件；`acceptance: null` → `—`；分段控件是 `radiogroup`；写后数据变化的项带高亮标记（`data-changed`），切窗口不带。
- [ ] **Step 2:** 失败测试 `ExperienceRow`：简报 §3.14 五行写法；`exp.item.anonymous`（别人看：`匿名作者`、行内无 `/u/` 链接）、`.mine`（`· 你写的`）、本人匿名（`匿名` 胶囊）、`.user-deleted`（`已注销用户`）、`.in-progress`（`→ 进行中`）、`.desk-reject`（无箭头）；只有必填时第三行不渲染；正文摘录不含 `**`；整行链 `/posts/<id>`；`profile` 变体第一行是期刊名、第二行合并、匿名胶囊 `匿名 · 仅你可见`。
- [ ] **Step 3:** 失败测试 `ExperienceArea`：按钮三种身份（匿名 → 链 `/login?next=/journals/1`；未验证 → disabled + `验证邮箱后才能写投稿经验` + 重发；已验证 → `写投稿经验`）；当前用户变为匿名后按钮跟着变（Review Focus 2）；点开表单后按钮隐藏、表单在汇总上方；发布成功 → 表单收起、toast `投稿经验已发布` + `查看`、`afterPublish` 被调用；挂起一次汇总请求后发布、最终显示新条数（Review Focus 3）；两个查询都没数据且失败 → `同行经验暂不可用` + `重试`、无按钮、无列表；列表 `加载更多投稿经验`；`exp.list.empty` 的 `写第一条投稿经验`。
- [ ] **Step 4:** 失败测试 `journal-experiences.test.tsx`：期刊页把 `ExperienceSection` 放在「影响力速览」之后、「期刊定位」之前（按 DOM 顺序断言 section 标题）。
- [ ] **Step 5:** 运行 → FAIL。
- [ ] **Step 6:** 实现，视觉照画板 `Journal` / `Journal-M`（汇总卡两栏 2 : 3、≤ 880px 上下排；列表行用 `border-subtle` 分隔）。`ExperienceSection` 的 Suspense fallback 是两栏骨架（`motion-safe:animate-pulse`）。
- [ ] **Step 7:** 运行 → PASS；`pnpm typecheck`。

### Task 8: 经验页骨架（路由、读状态、元数据、404、期刊卡）

**Files:**
- Create `src/app/posts/[id]/page.tsx`、`loading.tsx`、`not-found.tsx`；`src/app/missing/post/page.tsx`、`not-found.tsx`；`src/components/experience/PostJournalCard.tsx`；`src/components/experience/PostDeleted.tsx`
- Modify `src/proxy.ts`（`DETAIL` 改为完整网关路径，加 `posts`）、`src/components/portal/status/NotFoundState.tsx`（`kind="post"`）
- Test `tests/app/post-page.test.tsx`、`tests/proxy.test.ts`（追加）、`tests/components/portal/NotFoundState.test.tsx`（追加或新建）

**Interfaces — Produces:** `postMetadata(detail: PostDetail, canonical: string): Metadata`（放 `src/lib/page-metadata.ts` 追加）；页面把 `detail`、`journalCard`、`view` 交给 Task 9 的 `PostView`。

- [ ] **Step 1:** 失败测试 `post-page.test.tsx`：`not-found` → 调 `notFound()`；`deleted` → `该经验已删除`、没有 `#discussion`、元数据 `robots.index === false`、标题 `该经验已删除 · Patra`；`unavailable` → `投稿经验暂时打不开`（不是 404）；`ok` → 挂 `DiscussionSection`（subject `{ type: "post", key }`）；元数据标题 `J Am Heart Assoc 投稿经验 · 2026 年 3 月投稿 · Patra`、`description` 以 `首轮 42 天 · 大修 → 录用。` 开头且正文部分 ≤ 100 字、不含 Markdown 标记（Review Focus 5）、不含作者的用户名与 ID；期刊失效（`journal.state: "DELETED"`）时面包屑那一节与侧栏是纯文字、页面没有 `/journals/` 链接（Review Focus 4）；`fetchVenueDetail` 抛错时侧栏退回快照刊名。
- [ ] **Step 2:** 失败测试 `proxy.test.ts`：`/posts/123` 直接打开且 `HEAD /patra-interaction/posts/123` 回 404 → 改写 `/missing/post`；回 200 放行；`/posts/abc` → 改写；`sec-fetch-dest: empty` 放行；原有 papers / journals 用例不变。
- [ ] **Step 3:** 运行 → FAIL。
- [ ] **Step 4:** 实现；`NotFoundState` 加 `post`（`没有找到这条投稿经验` / `地址可能有误。` / 浏览入口 `浏览期刊` → `/journals`）。
- [ ] **Step 5:** 运行 → PASS。

### Task 9: 经验页主体（署名、时间线、定义表、正文、操作、编辑、删除）

**Files:** Create `src/components/experience/PostView.tsx`、`PostByline.tsx`、`ExperienceTimeline.tsx`、`ExperienceFacts.tsx`、`PostActions.tsx`、`DeletePostDialog.tsx`；Test `tests/components/experience/PostView.test.tsx`、`ExperienceTimeline.test.tsx`

**Interfaces — Consumes:** Task 6 `ExperienceForm`（edit）、Task 5 `afterEditOrDelete`、PAP-124 `authorView` / `AuthorAvatar` / `AuthorName` / `MarkdownBody`、`useCopy`、`publicUrl`。

- [ ] **Step 1:** 失败测试 `ExperienceTimeline`：三节点（`42 天`、`49 天` 写在节点文字里，`<ol>` 三个 `<li>`）；进行中（最后节点 `进行中`、无日期）；直接拒稿只有两个节点；有最终结果没填日期 → `未填日期`。
- [ ] **Step 2:** 失败测试 `PostView`：别人看实名（`陈宇 @chen-yu` 链 `/u/chen-yu`，只有 `···` → `复制链接`）；本人（`编辑` + `···` 含 `删除`）；`post.anonymous`（中性头像、`匿名作者`、页面无 `/u/` 链接）；`post.anonymous.self`（`匿名作者（你）` + `匿名发布 · 只有你知道是你写的`）；`post.deleted-user`；主页不可见的作者（只有昵称、不链接，Review Focus 4）；定义表只列填了的；没有正文不渲染正文块；`已编辑` 是按钮；`编辑` → 表单替换展示区、`保存修改` 成功后调 `router.refresh()` 与 `afterEditOrDelete`；删除确认文案（简报 §3.16）→ 成功后 `router.push("/journals/100#experiences")` + toast `投稿经验已删除`；期刊失效时跳 `/journals`；失败 `没有删除成功，请稍后再试`、对话框不关；`复制链接` 复制完整公开地址、toast `已复制链接`。
- [ ] **Step 3:** 运行 → FAIL。
- [ ] **Step 4:** 实现，视觉照画板 `Post` / `Post-M`（时间线 ≤ 640px 竖排；侧栏 ≤ 980px 由页面放到正文与回复区之间）。
- [ ] **Step 5:** 运行 → PASS。

### Task 10: 修改历史对话框

**Files:** Create `src/components/experience/PostRevisionDialog.tsx`、`src/lib/experience/revision-fields.ts`；Test `tests/lib/experience/revision-fields.test.ts`、`tests/components/experience/PostRevisionDialog.test.tsx`

**Interfaces — Produces:** `fieldChanges(previous: Revision, current: Revision): { label: string; from: string; to: string }[]`（11 个字段，标签 `投稿日期` / `首轮决定日期` / `首轮结果` / `最终结果` / `最终决定日期` / `文章类型` / `审稿轮数` / `审稿质量` / `编辑沟通` / `APC` / `转投`，没填写 `未填`）。

- [ ] **Step 1:** 失败测试：`fieldChanges` 改首轮结果 → `{ label: "首轮结果", from: "大修", to: "小修" }`；`apcPaid null → true` → `未填 → 付了`；没变的不出现。对话框：当前版本在前；最初版本只列值不标改动；正文变化用 `diffVersions` 标增删；`post-gone`（404）→ `这条投稿经验已被删除。`；失败 → `修改历史暂时打不开` + `重试`。
- [ ] **Step 2:** 运行 → FAIL。
- [ ] **Step 3:** 实现（`Dialog` ≤ 880px 是底部抽屉，现成）。
- [ ] **Step 4:** 运行 → PASS。

### Task 11: 用户主页「投稿经验」标签

**Files:**
- Create `src/lib/profile/tabs.ts`、`src/app/u/[username]/experiences/page.tsx`、`src/components/profile/ProfileExperiences.tsx`
- Modify `src/components/profile/ProfileTabs.tsx`、`src/app/u/[username]/collections/page.tsx`
- Test `tests/lib/profile/tabs.test.ts`、`tests/components/profile/ProfileTabs.test.tsx`（改）、`tests/components/profile/ProfileExperiences.test.tsx`、`tests/app/user-experiences-page.test.tsx`

**Interfaces — Produces:** `type ProfileTab = "about" | "collections" | "experiences"`；`visibleProfileTabs(profile: PublicProfile, self: boolean, current: ProfileTab): Promise<ProfileTab[]>`；`ProfileTabs({ username, tabs, current })`（`tabs.length <= 1` 返回 null）。

- [ ] **Step 1:** 失败测试 `tabs.test.ts`：本人 → 三项、不请求；别人：收藏夹有 + 经验有 → 三项；只有经验 → `about, experiences`；都没有 → `about`；读失败当空；当前标签总在（别人、经验为空、current `experiences` → 含 `experiences`）；两次读并行（两个桩同时挂起时都已被调用）。
- [ ] **Step 2:** 失败测试 `ProfileExperiences` / 页面：本人说明行 `这里列出你写过的投稿经验，匿名发布的只有你看得到。`；匿名条目 `匿名 · 仅你可见`；本人空 `还没有写过投稿经验` + `去期刊列表` 链 `/journals`；别人空 `还没有公开的投稿经验`；失败 `投稿经验暂时打不开` + `重试`；`加载更多`；旧名 307 到 `/u/<新名>/experiences`；标题 `陈宇 (@chen-yu) 的投稿经验 · Patra`；`/collections` 页对别人且其无经验时不出「投稿经验」标签。
- [ ] **Step 3:** 运行 → FAIL。
- [ ] **Step 4:** 实现。
- [ ] **Step 5:** 运行 → PASS。

### Task 12: 本地验证与门禁

- [ ] **Step 1:** `pnpm lint && pnpm typecheck && pnpm test` 全过（有 `biome check --write` 能修的先修）。
- [ ] **Step 2:** `pnpm build`：没有动态渲染信号报错、没有 `useSearchParams` 进 RSC 的报错。
- [ ] **Step 3:** `preview_start` 起 dev（端口 4002），桌面 1440 与 375 手看：期刊页四种汇总状态（用 mini 上已有数据 + 自己写 ≤ 2 条，记下用掉的额度）、表单联动、经验页三种身份、主页标签；对照画板逐块核对视觉。手写的经验看完删掉。
- [ ] **Step 4:** D9 手测（spec 9.3）：停 mini 上 interaction 前先确认 PAP-126 会话不在跑 e2e；结果写进 spec 收尾节。

### Task 13: Playwright（对 mini）

**Files:** Create `tests/e2e/experience.spec.ts`、`tests/e2e/support/experience.ts`（`pickEmptyJournal(request)`、`openExperiences(page, journalId)`、`fillExperience(form, values)`、`publish(...)`、`deletePost(page, postId)`、`expectedSummary(dates)`：线性插值同 `percentile_cont`、半进位）

- [ ] **Step 1:** 按 spec 9.2 的 13 步写一个 serial describe；`afterAll` 无条件清理（先删 V 的回复，再删经验；经门户页面操作，失败的那条记日志不抛）。
- [ ] **Step 2:** `pnpm exec tsc --noEmit -p tests/e2e`（或项目的 e2e 类型检查方式）通过；**运行前确认当天 V 账号的经验额度至少剩 5 条**（spec 9.2 写入预算）。
- [ ] **Step 3:** `set -a; . ~/.patra/secrets/e2e.env; set +a; PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm test:e2e tests/e2e/experience.spec.ts --workers=1` → 全过。失败时先看 trace、在本地复现，修好后最多再跑一次。
- [ ] **Step 4:** 跑完到 mini 上确认 V 没有残留的测试经验（`GET /patra-interaction/users/<V>/posts` 带会话）。

### Task 14: 收尾文档

- [ ] **Step 1:** spec 末尾加「12. 实施记录」：与设计的偏离、验证结果（单测条数、build、e2e 用时、D9 手测）。
- [ ] **Step 2:** `patra-portal/README.md` 的互动部分补一段投稿经验（照 PAP-124 加的段落写法）。
- [ ] **Step 3:** 写共享笔记 `portal-PAP-125-handoff.md`（spec 第 10 节：经验页组件与路由、永久链接、已删除是 200、`format` / `plain` 可复用、queryKeys 前缀）。
- [ ] **Step 4:** 停在本地，请用户在浏览器验收（桌面 1440、手机 375），列出验收入口与用掉的经验额度；用户同意后再提交。
