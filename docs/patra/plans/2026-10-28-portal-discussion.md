# 门户文献讨论区与编辑器（PAP-124）Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（本会话内联执行，用户 10/21 定：不用 subagent-driven）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 文献页主栏最后的讨论区（两层展示、排序、单楼视图与永久链接、编辑器与 @ 联想、修改历史、删除、降级），组件可被经验页 `/posts/{id}` 复用。

**Architecture:** 服务端 RSC 预取首屏放进 `HydrationBoundary`，客户端 `DiscussionView` 以地址（`?comment=`、`?sort=`）为准、用 TanStack Query 管数据，写操作走 Server Action、结果用纯函数并进缓存。Markdown 用 react-markdown，白名单在 micromark 解析层（`disable` + 自写提及构造），PMID / DOI / 网址在 mdast 文本节点上替换。

**Tech Stack:** Next 16 App Router · React 19 · TanStack Query v5 · react-markdown 10.1.0 · remark-breaks 4.0.0 · mdast-util-find-and-replace 3.0.2 · diff 9.0.0 · Vitest + Testing Library · Playwright

**Spec:** `docs/patra/specs/2026-10-28-portal-discussion-design.md`（产品与文案另见设计简报 `patra-portal/docs/design-briefs/v0.14/discussion.md`、后端 `docs/patra/specs/2026-10-27-interaction-discussion-design.md` 第 5、6 节）

## Global Constraints

- 文案逐字照简报 §3–§4 与 spec 4.3；界面不出现「帖子」「通知」「私信」，不出现任何计数（讨论数、回复数）。
- 评论上限：5,000 字素且 20,000 UTF-8 字节；接近上限阈值 = 上限 × 80%；@ 去重后最多 10 人。
- 链接只放行 `http:` / `https:`，`target="_blank"` + `rel="nofollow ugc noopener"`；PMID → `https://pubmed.ncbi.nlm.nih.gov/<n>/`，DOI → `https://doi.org/<doi>`。
- 读接口超时 `READ_TIMEOUT_MS`（5,000）；读接口登录可选，令牌被拒时按访客重取。
- 主页不可见（`username == null`）的人：只显示昵称 + 默认头像，不链接、不显示 `@用户名`。
- 所有 queryKey 在 `discussion` 前缀下；身份变化整体清掉。
- 路径别名 `@/`，不写 `../../`；`///` 注释风格同仓库；Biome 是唯一格式权威；禁止 `any`。
- 组件入参不叫 `key`：一律用 `subject: { type: SubjectType; key: string }`。
- **不 commit**：每个任务做完只跑测试，不提交；全部做完停在本地请用户验收，用户同意后一次提交（只加自己的路径，commit 信息中文、subject 中文动词开头）。
- 不 `cd`；命令一律 `pnpm -C /Users/linqibin/Projects/Products/patra/.claude/worktrees/v014-notification-120745/patra-portal …`（下文简写 `pnpm -C $P`）。

## Review Focus

1. **超长单词与网址撑破布局（375px）**：正文里 200 字符的网址、无空格英文串在窄屏不横向溢出 → Task 2 测 `break-words` / `[overflow-wrap:anywhere]` 类在正文容器上。
2. **IME 输入中文时 @ 联想与 `Enter`**：输入法组字（`isComposing`）时 `Enter` 不能选中候选、`⌘Enter` 不能提交 → Task 10、Task 11 各加一条 `compositionstart` 期间按键不生效的测试。
3. **双击提交 / 重复发表**：提交中再次 `⌘Enter` 或点按钮不发第二次请求 → Task 11 测 action 只被调用一次。
4. **评论里出现 `<@数字>` 但 `mentions` 里没有该 ID**（后端省略）：显示 `@已注销用户`，不显示原始标记 → Task 1 测。
5. **单楼视图的目标在列表里已被删除后又点「查看全部讨论」再后退**：地址回到 `?comment=` 时仍按单楼取、显示 missing 而不是空白 → Task 13 测 `popstate` 后视图跟随地址。

---

### Task 1: Markdown 子集解析与 `MarkdownBody`（提及、原文回退）

**Files:**
- Create: `patra-portal/src/lib/markdown/subset.ts`（micromark 扩展、mdast 扩展、`remarkSubset` 插件、`remarkLiteralFallback` 插件、mdast 类型扩充）
- Create: `patra-portal/src/components/discussion/MarkdownBody.tsx`
- Test: `patra-portal/tests/components/discussion/MarkdownBody.test.tsx`

**Interfaces:**
- Produces: `MarkdownBody({ source: string; mentions: readonly Mention[]; variant?: "comment" | "small" | "reading"; className?: string }): JSX.Element`（无 hooks，RSC 与客户端都能用）；`Mention` 类型来自 Task 4 的 `discussion-schema.ts`——本任务先在 `subset.ts` 旁按 `{ userId: string; username: string | null; displayName: string | null; deleted: boolean }` 定义，Task 4 改为从 schema 导出同名类型、这里改 import。
- Produces: `remarkSubset`（unified 插件：往 `this.data("micromarkExtensions")` 推 `{ disable: { null: ["headingAtx","setextUnderline","codeIndented","codeFenced","htmlFlow","htmlText","thematicBreak","definition","autolink"] } }` 和提及构造 `{ text: { 60: mention } }`，往 `fromMarkdownExtensions` 推产出 `{ type: "mention", userId }` 的扩展）；`remarkLiteralFallback`（按 `position` 从 `String(file)` 切原文：`image` → `text`；`listItem` 里的子 `list` → 一个 `paragraph` 含原文 `text`；源文以三个以上反引号开头且含换行的 `inlineCode` → `text`）。

- [ ] **Step 1: 写失败的测试**（`render(<MarkdownBody source=… mentions=… />)`）
  - `renders_whitelisted_formats`：`**粗**` → `strong`；`*斜*`、`_斜_` → `em`；`> 引\n> 用` → 一个 `blockquote`；`- a\n- b` → `ul > li` ×2；`1. a\n2. b` → `ol`；`` `x` `` → `code`；`[NEJM](https://www.nejm.org/)` → `getByRole("link", { name: /NEJM（新窗口打开）/ })` 有 `href`、`target="_blank"`、`rel="nofollow ugc noopener"`。
  - `keeps_disallowed_syntax_as_literal_text`（`it.each`）：`# 标题`、`标题\n===`、`<b>粗</b>`、`| a | b |\n|---|---|`、`` ```\ncode\n``` ``、`    indented`、`---`、`[x]: https://a.b` → 原文（去掉缩进代码的前导空格后）出现在 `container.textContent`；`container.querySelector("h1,h2,b,table,pre,hr")` 为 null。
  - `never_renders_images`：`![图](https://example.com/a.png)` → `container.querySelectorAll("img").length === 0`，文字含 `![图](https://example.com/a.png)`。
  - `flattens_nested_lists`：`- a\n  - b` → 只有一个 `ul`，文字含 `- b`。
  - `renders_mentions_from_entities`：`同意 <@42>`、mentions `[{userId:"42",username:"zhou-min",displayName:"周敏",deleted:false}]` → 链接 `@zhou-min` 指向 `/u/zhou-min`；`username:null,displayName:"周敏"` → 文字 `@周敏` 且不是链接；`deleted:true` → `@已注销用户` 非链接；mentions 里没有 42 → `@已注销用户`。
  - `does_not_treat_escaped_or_code_markers_as_mentions`：`\<@42>` → 文字 `<@42>`、无链接；`` `<@42>` `` → `code` 内文字 `<@42>`；`<@042>`、`<@>` 原样。
  - `keeps_single_line_breaks`：`a\nb` → 一个 `p` 里有 `br`；`a\n\nb` → 两个 `p`。
  - `escapes_show_literal`：`\*不是斜体\*` → 无 `em`，文字 `*不是斜体*`。
  - `rejects_non_http_links`：`[x](javascript:alert(1))`、`[x](mailto:a@b.c)` → 无链接元素，文字含 `x`。

- [ ] **Step 2: 跑测试确认失败**
  Run: `pnpm -C $P exec vitest run tests/components/discussion/MarkdownBody.test.tsx`
  Expected: FAIL（模块不存在）

- [ ] **Step 3: 实现 `subset.ts` 与 `MarkdownBody`**
  提及构造：在 `<`（60）上进 `mention`，要求 `@`（64）、首位 `1-9`、其后 `0-9`*、`>`（62）结束，否则 `nok`（spike 已验证此写法与转义、行内代码互不干扰）。mdast 扩展：`enter.mention` 建节点，`exit.mentionId` 用 `sliceSerialize` 写 `userId`。在 `subset.ts` 里 `declare module "mdast" { interface PhrasingContentMap { mention: Mention… } }`。
  `MarkdownBody`：`<Markdown remarkPlugins={[remarkSubset, remarkLiteralFallback, remarkBreaks]} remarkRehypeOptions={{ handlers: { mention } }} allowedElements={[…第 5.1 节清单]} skipHtml components={{ a, span }} />`；`mention` handler 产出 `span` 带 `dataMentionId`；`components.span` 按 `data-mention-id` 查 `mentions` 渲染；`components.a` 只放行 http(s)（否则渲染子节点文字），带 `ArrowUpRight` 与 `<span className="sr-only">（新窗口打开）</span>`。样式按简报 §3.7（引用 `border-l-2 border-ink-300 pl-3.5 text-fg-2`，行内代码 `rounded-sm bg-paper-200 px-1 font-mono text-sm`，列表 `pl-5.5 space-y-1`，段距 `space-y-2.5`），三个 `variant` 的字号见 spec 5.3；容器加 `break-words [overflow-wrap:anywhere]`（Review Focus 1）。

- [ ] **Step 4: 跑测试确认通过**（同上命令，全绿）

### Task 2: PMID、DOI、裸网址自动链接

**Files:**
- Create: `patra-portal/src/lib/markdown/autolink.ts`（`remarkAutolink` 插件 + 导出的纯函数 `trimTrailingPunctuation`）
- Modify: `patra-portal/src/components/discussion/MarkdownBody.tsx`（插件链加 `remarkAutolink`，放在 `remarkLiteralFallback` 之后、`remarkBreaks` 之前；裸网址显示省略）
- Test: `patra-portal/tests/lib/markdown/autolink.test.ts`、`tests/components/discussion/MarkdownBody.test.tsx`（追加）

**Interfaces:**
- Produces: `remarkAutolink`；被替换的 `link` 节点带 `data.hProperties["data-autolink"] = "pmid" | "doi" | "url"`。
- Produces: `trimTrailingPunctuation(value: string): string`。

- [ ] **Step 1: 写失败的测试**
  - `autolink.test.ts`：`trimTrailingPunctuation` 对 `10.1/abc.`、`x），`、`x。`、`x」`、`x)`（无左括号时去掉）、`(x)`（成对保留）。
  - MarkdownBody 追加 `it.each`：`PMID 27940189`、`PMID: 27940189`、`PMID:27940189`、`pmid 27940189` → 链接文字即原文、`href` `https://pubmed.ncbi.nlm.nih.gov/27940189/`；`aPMID 1` 不链接。
  - `10.1001/jama.2019.22176。` → 链接 `https://doi.org/10.1001/jama.2019.22176`，句号不在链接文字里；`doi:10.1001/x` → 链接文字含 `doi:`；`https://doi.org/10.1/x` 只出一个链接。
  - 裸网址 `https://www.ncbi.nlm.nih.gov/pmc/articles/PMC5629227/` → 一个链接；显示文字超过 48 个字符时中间是 `…`，`title` 是完整网址；`见 https://a.b/c。` 句号不进链接。
  - `[文字](https://a.b) PMID 1` 链接里的不重复处理；`` `PMID 1` `` 不链接。

- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/lib/markdown tests/components/discussion/MarkdownBody.test.tsx` → FAIL

- [ ] **Step 3: 实现** `findAndReplace(tree, [[PMID_RE, …], [DOI_RE, …], [URL_RE, …]], { ignore: ["link", "inlineCode"] })`。正则：`PMID_RE = /(?<![A-Za-z0-9])PMID:?\s?(\d{1,9})(?!\d)/gi`；`DOI_RE = /(?<![A-Za-z0-9])(?:doi:\s?)?10\.\d{4,9}\/[^\s<>]+/gi`；`URL_RE = /https?:\/\/[^\s<>]+/g`。DOI 与网址的匹配先经 `trimTrailingPunctuation`，剩下的放回文本。网址先于 DOI 匹配（`https://doi.org/…` 不再被 DOI 规则拆开）。

- [ ] **Step 4: 跑测试确认通过**

### Task 3: 计数与 @ 粗算

**Files:**
- Create: `patra-portal/src/lib/discussion/text-metrics.ts`
- Test: `patra-portal/tests/lib/discussion/text-metrics.test.ts`

**Interfaces:**
- Produces:
  - `COMMENT_LIMITS = { graphemes: 5000, bytes: 20000, mentions: 10 } as const`
  - `normalizeForCount(text: string): string`（`\r\n`/`\r` → `\n`、`normalize("NFC")`、去首尾空白）
  - `measure(text: string): { graphemes: number; bytes: number }`（用已有 `graphemeCount` + `TextEncoder`）
  - `type LimitState = "ok" | "near" | "over-graphemes" | "over-bytes"`；`limitState(m: { graphemes: number; bytes: number }, limits: { graphemes: number; bytes: number }): LimitState`（`near` = `graphemes >= limits.graphemes * 0.8`；`over-graphemes` 优先于 `over-bytes`）
  - `mentionCandidates(text: string): string[]`（按后端 5.4.1：跳过行内代码、`[..](..)`、`https?://` 段；`@` 前不是字母数字 `_` `@`；贪婪 `[A-Za-z0-9_-]+` 去末尾 `_` `-`；3–30 位、`^[A-Za-z0-9]([A-Za-z0-9]|[_-][A-Za-z0-9])*$`、不全数字；小写去重，保持首次顺序）

- [ ] **Step 1: 写失败的测试**：`measure("👨‍👩‍👧汉")` → `{ graphemes: 2, bytes: 21 }`；`measure("  a\r\n ")` → graphemes 1；`limitState` 四态边界（3999 ok、4000 near、5001 over-graphemes、5000 字素但 20001 字节 over-bytes）；`mentionCandidates`：`"@chen-yu 和 @Chen-Yu"` → `["chen-yu"]`、`"a@b.com"` → `[]`、`"你好@chen"` → `[]`、`"(@chen-yu)"` → `["chen-yu"]`、`"@chen-"` → `["chen"]`、`"@12345"` → `[]`、`"@ab"` → `[]`、`` "`@chen-yu`" `` → `[]`、`"[x](https://a/@chen)"` → `[]`、`"https://a/@chen"` → `[]`。
- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/lib/discussion/text-metrics.test.ts`
- [ ] **Step 3: 实现**（`mentionCandidates` 先用正则把行内代码、链接、网址替换成等长空格再扫描）
- [ ] **Step 4: 跑测试确认通过**

### Task 4: 响应体 schema、服务端读与 Route Handler

**Files:**
- Create: `patra-portal/src/lib/interaction/discussion-schema.ts`
- Create: `patra-portal/src/lib/interaction/discussion.ts`（`server-only`）
- Create: `patra-portal/src/app/api/discussions/[type]/[key]/threads/route.ts`、`…/[key]/comments/[id]/thread/route.ts`、`…/[key]/mention-suggestions/route.ts`、`src/app/api/comments/[id]/replies/route.ts`、`…/conversation/route.ts`、`…/revisions/route.ts`、`…/source/route.ts`
- Modify: `patra-portal/src/lib/markdown/subset.ts`、`MarkdownBody.tsx`（`Mention` 改从 schema 导入）
- Test: `patra-portal/tests/lib/interaction/discussion.test.ts`、`tests/app/api/discussion-routes.test.ts`、`tests/fixtures/discussion.ts`

**Interfaces:**
- Produces（schema，全部 `z.object`，ID 是字符串）：`subjectTypeSchema = z.enum(["publication","post"])`、`type SubjectType`、`interface Subject { type: SubjectType; key: string }`；`cardSchema`（`userId`、`username: string|null`、`displayName: string|null`、`avatarUrl: string|null`、`avatarPalette: number`、`state: enum ACTIVE|BANNED|PENDING_DELETION|DELETED`、`profileVisible: boolean`）；`mentionSchema`；`replyToSchema`（`deleted`、`anonymous`、`author: card|null`，三者都有默认值）；`commentSchema`（spec 后端 6.4 全字段）；`threadSchema`（`root`、`replies`、`hasMoreReplies`、`repliesCursor: string|null`）；`threadPageSchema`（`items`、`nextCursor: string|null`）；`singleThreadSchema`（`targetId`、`thread`）；`repliesSchema`（`items: comment[]`、`nextCursor`）；`conversationSchema`（`targetId`、`items`）；`revisionsSchema`（`versions: {body, at}[]`、`mentions`）；`sourceSchema`（`body`）；`mentionSuggestionSchema`（`userId`、`username`、`displayName`、`avatarUrl`、`avatarPalette`、`group: PARTICIPANT|OTHER`）；`deleteResultSchema`（`placeholder`、`removedIds: string[]`）。类型同名去掉 `Schema` 后缀（`Comment`、`Thread`…）。`type DiscussionSort = "asc" | "latest"`。
- Produces（`discussion.ts`，都返回 `Promise<Read<T>>`）：`fetchThreads(subject: Subject, sort: DiscussionSort, cursor?: string)`、`fetchSingleThread(subject: Subject, commentId: string)`、`fetchReplies(rootId: string, cursor?: string)`、`fetchConversation(commentId: string)`、`fetchRevisions(commentId: string)`、`fetchSource(commentId: string)`（`auth: "required"`）、`fetchMentionSuggestions(subject: Subject, q: string)`（`auth: "required"`）；`isSubject(type: unknown, key: unknown): boolean`。
- Route Handler：成功 200 原样回体（`readReply`），参数不合格 404，`signed-out` 401。

- [ ] **Step 1: 写失败的测试**（`// @vitest-environment node`，套用 `tests/lib/interaction/library-actions.test.ts` 的 `fetchMock` + cookie-jar 写法）
  - `fetchThreads`：请求 `http://gw.test/patra-interaction/discussions/publication/11/threads?sort=latest`（有 cursor 时带 `&cursor=`，经 `encodeURIComponent`）、带 `Authorization`、`ok` 时数据过 schema；
  - `optional_read_retries_anonymously_when_token_rejected`：第一次 401 → 第二次不带 `Authorization`、返回 `ok`；
  - 无会话 Cookie 时不带 `Authorization`；
  - `rejects_bad_params_without_calling_gateway`：`type: "journal"`、`key: "abc"`、`commentId: "0"` → `not-found` 且 `fetchMock` 未调用；
  - `fetchSingleThread` 404 → `not-found`；503 → `unavailable`；形状不对 → `unavailable`；
  - `fetchSource` 无会话 → `signed-out`、不请求；
  - Route Handler（`discussion-routes.test.ts`，mock `@/lib/interaction/discussion`）：各路由把路径参数与 `sort` / `cursor` / `q` 传给对应函数；`unavailable` → 502、`not-found` → 404；`sort` 不是 `latest` 时按 `asc`。
- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/lib/interaction/discussion.test.ts tests/app/api/discussion-routes.test.ts`
- [ ] **Step 3: 实现**：每个函数 `callInteraction(path, { timeoutMs: READ_TIMEOUT_MS }, { auth: "optional" })`；`problem.status === 401` 时再用一个 `{ anonymous: true }` 调一次——给 `callInteraction` 的 `options` 加 `auth: "none"`（不读 Cookie、不带令牌），这是对 `request.ts` 的唯一改动，补一条 `tests/lib/interaction/request.test.ts` 用例。fixture `makeComment(overrides)`、`makeThread(overrides)`、`makeCard(overrides)` 放 `tests/fixtures/discussion.ts`。
- [ ] **Step 4: 跑测试确认通过**

### Task 5: 写操作 Server Action 与文案

**Files:**
- Create: `patra-portal/src/lib/interaction/discussion-actions.ts`（`"use server"`）
- Create: `patra-portal/src/lib/discussion/messages.ts`
- Test: `patra-portal/tests/lib/interaction/discussion-actions.test.ts`、`tests/lib/discussion/messages.test.ts`

**Interfaces:**
- Produces: `type CommentFailure`（spec 4.3 原样）；`publishComment(subject: Subject, input: { body: string; parentId: string | null }): Promise<{ ok: true; comment: Comment } | CommentFailure>`；`editComment(commentId: string, body: string): Promise<{ ok: true; comment: Comment } | CommentFailure>`；`deleteComment(commentId: string): Promise<{ ok: true; placeholder: boolean; removedIds: string[] } | CommentFailure>`。
- Produces: `commentFailureMessage(reason: CommentFailure["reason"], action: "publish" | "edit" | "delete"): string`；`COMPOSER_COPY`（简报 §4.2 其余固定文案：`没有可以预览的内容`、`放弃正在写的内容？`、`登录已过期，去重新登录？已写的内容会丢失。` 等）。

- [ ] **Step 1: 写失败的测试**
  - `publishComment`：POST `…/discussions/publication/11/comments`、`json` `{ body, parentId: null }`、201 → `{ ok: true, comment }`；
  - `it.each` 原因码：`429 {traits:["QUOTA_EXCEEDED","TOO_FREQUENT"]}` + `Retry-After: 7` → `{ reason: "too-frequent", retryAfterSeconds: 7 }`；`DAILY_LIMIT_REACHED` → `daily-limit`；`409 EMAIL_UNVERIFIED` / `ACCOUNT_RESTRICTED` / `REPLY_TARGET_GONE`；`404 {traits:["NOT_FOUND","SUBJECT_UNAVAILABLE"]}` → `subject-gone`；`404 COMMENT_NOT_FOUND` → `comment-gone`；`422 {errors:[{field:"body",code:"TOO_MANY_MENTIONS"}]}` / `TOO_LONG` / `REQUIRED`(→`empty`) / `INVALID_CHARACTER`；`403` → `forbidden`；`401` → `signed-out` 且清 Cookie；`500` → `unavailable`；
  - 参数不合格（`parentId: "x"`、`body: 1`）→ `unavailable`、不请求；
  - `editComment` PUT `/comments/5` `{ body }`；`deleteComment` DELETE `/comments/5` → `{ ok: true, placeholder: false, removedIds: ["5","3"] }`；
  - `messages.test.ts`：每个 `reason` × `action` 的文案与 spec 4.3 逐字一致（`unavailable` 三种动作三种文案）。
- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/lib/interaction/discussion-actions.test.ts tests/lib/discussion/messages.test.ts`
- [ ] **Step 3: 实现**（用 `mutate()`；`classify` 先看 `traits`、再看 `errors[0].code`、再看状态码；`retryAfterSeconds` 取 `problem.retryAfterSeconds`）
- [ ] **Step 4: 跑测试确认通过**

### Task 6: queryKeys、清缓存、客户端查询与缓存合并

**Files:**
- Modify: `patra-portal/src/lib/query-keys.ts`（spec 4.4 的 6 个 key）
- Modify: `patra-portal/src/lib/query-client.ts`（`removeQueries({ queryKey: queryKeys.discussion })`）
- Create: `patra-portal/src/lib/discussion/queries.ts`、`patra-portal/src/lib/discussion/cache.ts`
- Test: `tests/lib/query-client.test.ts`（追加）、`tests/lib/discussion/cache.test.ts`、`tests/lib/discussion/queries.test.tsx`

**Interfaces:**
- Produces（`queries.ts`）：`useThreads(subject, sort)`（`useInfiniteQuery`，`initialPageParam: null`，`getNextPageParam: p => p.nextCursor`，`retry: false`，`refetchOnWindowFocus: (q) => q.state.status === "error"`）；`useSingleThread(subject, commentId | null)`（404 时数据为 `{ missing: true }` 而不是错误）；`useConversation(commentId | null)`、`useRevisions(commentId | null)`；`useMentionSuggestions(subject, q: string | null, enabled: boolean)`（`staleTime: 30_000`，`q === null` 不取）；`fetchRepliesPage(rootId, cursor): Promise<Replies>`；`fetchCommentSource(commentId): Promise<string>`。类型 `type ThreadsData = InfiniteData<ThreadPage, string | null>`；`type SingleData = SingleThread | { missing: true }`。
- Produces（`cache.ts`，纯函数，不改入参）：`insertRoot(data: ThreadsData, comment: Comment, sort: DiscussionSort): { data: ThreadsData; placed: "top" | "bottom" | "offpage" }`；`appendReply(data, rootId: string, comment: Comment)`；`replaceComment(data, comment: Comment)`；`applyDeletion(data, commentId: string, result: { placeholder: boolean; removedIds: string[] })`；`appendReplies(data, rootId, page: Replies)`；`replaceThread(data, thread: Thread)`；以及单楼的同名四个 `…Single(data: SingleData, …)`。`toPlaceholder(comment: Comment): Comment`。

- [ ] **Step 1: 写失败的测试**
  - `query-client.test.ts`：身份从用户 A 变成 null 后 `["discussion","publication","1","threads","asc"]` 被清掉；第一次取回当前用户之前不清。
  - `cache.test.ts`（spec 4.5 表每行一条）：`insertRoot` 三种 `placed`；`appendReply` 接在末尾、其他楼不变；`replaceComment` 能替换根与回复；`applyDeletion` 占位（保留 `hasReplies: true`、`body: null`、`author: null`、`deleted: true`）、`removedIds` 含根 → 整楼消失、含回复 → 回复消失；`appendReplies` 更新 `repliesCursor` / `hasMoreReplies`；单楼：删根 → `{ missing: true }`；所有函数不改入参（`Object.freeze` 后调用不抛）。
  - `queries.test.tsx`（`renderWithQuery`，mock `fetch`）：`useThreads` 请求 `/api/discussions/publication/1/threads?sort=asc`，下一页带 `cursor`；`useSingleThread` 404 → `{ missing: true }`。
- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/lib/query-client.test.ts tests/lib/discussion`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**

### Task 7: 头像形态与降级块紧凑版

**Files:**
- Modify: `patra-portal/src/components/user/Avatar.tsx`（尺寸加 22 / 28 / 32；`mobileSize?: AvatarSize`；`AvatarIdentity.username: string | null`）
- Modify: `patra-portal/src/lib/identity/avatar.ts`（`avatarInitial(displayName: string, username: string | null)`）
- Create: `patra-portal/src/components/user/NeutralAvatar.tsx`
- Modify: `patra-portal/src/components/library/InteractionUnavailable.tsx`（`compact?: boolean`：`py-10`、标题 `text-lg`）
- Test: `tests/components/user/Avatar.test.tsx`（追加）、`tests/components/user/NeutralAvatar.test.tsx`、`tests/components/library/InteractionUnavailable.test.tsx`（追加）

**Interfaces:**
- Produces: `NeutralAvatar({ kind: "anonymous" | "deleted"; size: AvatarSize; mobileSize?: AvatarSize })`——`bg-ink-200`，`anonymous` 中间 `UserRound`（`text-fg-3`、`aria-hidden`），`deleted` 无字。
- 核对现有 `Avatar` 调用方（`grep -rn "<Avatar" src`）在 `username` 可空后仍通过 `pnpm typecheck`。

- [ ] **Step 1: 写失败的测试**：`size={32} mobileSize={28}` 的类名含 `size-8` 与 `max-[880px]:size-7`；`username: null` 时默认头像首字取昵称；`NeutralAvatar` 两种 kind 的内容；`compact` 的标题字号类。
- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/components/user tests/components/library/InteractionUnavailable.test.tsx`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**；`pnpm -C $P typecheck` 通过

### Task 8: 评论条目展示 `CommentByline` / `CommentItem`

**Files:**
- Create: `patra-portal/src/components/discussion/AuthorIdentity.tsx`（`AuthorAvatar`、`AuthorName`：PAP-125 经验页署名复用）
- Create: `patra-portal/src/components/discussion/CommentByline.tsx`、`CommentItem.tsx`、`CommentMenu.tsx`
- Create: `patra-portal/src/lib/discussion/author.ts`（纯函数）
- Test: `tests/lib/discussion/author.test.ts`、`tests/components/discussion/CommentItem.test.tsx`

**Interfaces:**
- Consumes: Task 1 `MarkdownBody`、Task 4 `Comment` / `Card`、Task 7 `Avatar` / `NeutralAvatar`、`formatRelative` / `formatDateTime`。
- Produces（`author.ts`）：`type AuthorView = { kind: "anonymous"; self: boolean } | { kind: "deleted" } | { kind: "hidden"; card: Card } | { kind: "visible"; card: Card }`；`authorView(input: { author: Card | null; anonymous: boolean; mine: boolean }): AuthorView`（spec 6.4 署名表）；`replyToLabel(replyTo: Comment["replyTo"]): { text: string; href: string | null } | null`（`回复已删除的评论` / `回复 匿名作者` / `回复 已注销用户` / `回复 @用户名`（链 `/u/…`）/ `回复 @昵称`（不链））。
- Produces（组件）：`CommentItem({ comment, level: "root" | "reply", permalink: string, now: number, highlighted?: boolean, current?: boolean, actions: CommentActions })`；`interface CommentActions { onReply(c: Comment): void; onShowConversation(c: Comment): void; onShowHistory(c: Comment): void; onEdit(c: Comment): void; onDelete(c: Comment): void; onCopyLink(c: Comment, target: HTMLElement | null): void; onOpenPermalink(c: Comment): void }`；`editing?: ReactNode`（原地编辑时代替正文）。

- [ ] **Step 1: 写失败的测试**
  - `author.test.ts`：五种署名 × `authorView`；`replyToLabel` 五种。
  - `CommentItem.test.tsx`：可见作者 → 昵称链接 `/u/chen-yu` + `@chen-yu`；hidden → 昵称非链接、无 `@`；匿名本人 → `匿名作者（你）`；`isOwner` → `作者` 胶囊；时间链接 `href` = `permalink`、`title` 是 `2026-10-24 14:20`（北京时间）；`edited` → `已编辑` 按钮调用 `onShowHistory`；`replyTo` 时出现 `查看对话`；占位 → 只有 `该评论已删除`、无操作；`mine` 菜单有 `复制链接`、`编辑`、`删除`，否则只有 `复制链接`；`highlighted` → `aria-current="true"`；`level: "reply"` 头像 24 / 22。
  - 长评论：mock `ResizeObserver` 回报高度 > 12 行（`lineHeight × 12`）时出现 `展开全文`，点后 `收起`。
- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/lib/discussion/author.test.ts tests/components/discussion/CommentItem.test.tsx`
- [ ] **Step 3: 实现**（菜单用 `src/components/ui/dropdown-menu.tsx`；样式对照 `components-v014.css` 的 `.pt-cmt` 等与简报 §3.2）
- [ ] **Step 4: 跑测试确认通过**

### Task 9: `MarkdownEditor`（编写 / 预览、工具条、计数）

**Files:**
- Create: `patra-portal/src/components/discussion/MarkdownEditor.tsx`
- Create: `patra-portal/src/lib/discussion/toolbar.ts`（纯函数）
- Test: `tests/lib/discussion/toolbar.test.ts`、`tests/components/discussion/MarkdownEditor.test.tsx`

**Interfaces:**
- Consumes: Task 1 `MarkdownBody`、Task 3 `measure` / `limitState` / `mentionCandidates`。
- Produces（`toolbar.ts`）：`type ToolbarAction = "bold" | "italic" | "quote" | "bullet" | "ordered" | "code" | "link" | "mention"`；`applyToolbar(value: string, selection: { start: number; end: number }, action: ToolbarAction): { value: string; selection: { start: number; end: number } }`（有选区包住；无选区插成对标记、光标居中；`quote` / `bullet` / `ordered` 给选中各行加前缀 `> ` / `- ` / `1. 2. …`；`link` → `[选区](https://)` 并选中 `https://`；`mention` 插 `@`）。
- Produces（组件）：`MarkdownEditor({ value, onChange, textareaRef, placeholder, limits: { graphemes; bytes }, mentions: MentionConfig | false, disabled?: boolean, readOnly?: boolean, autoFocus?: boolean, footerHint?: "comment" | "experience", onSubmitShortcut?: () => void, errorSlot?: ReactNode, mentionEntities?: readonly Mention[] })`；`interface MentionConfig { subject: Subject }`（Task 10 用）；导出 `useEditorMetrics(value, limits): { graphemes; bytes; state: LimitState; mentionCount: number; mentionOver: boolean }`。

- [ ] **Step 1: 写失败的测试**
  - `toolbar.test.ts`：每个 action 的有选区 / 无选区结果与新选区。
  - 组件：`编写` / `预览` 两个 `tab`；预览空 → `没有可以预览的内容`；预览渲染 `**粗**` 为 `strong`；计数显示 `186 / 5,000`；`near` 时计数类 `text-amber-700`；`over-graphemes` 显示 `评论最多 5,000 字`；`over-bytes` 显示 `内容太长了，请删减一些`；11 个不同 `@用户名` → `一条评论最多 @ 10 人`；`mentions={false}` 时没有 `@` 工具按钮；`⌘Enter` 调 `onSubmitShortcut`，`compositionstart` 后 `⌘Enter` 不调（Review Focus 2）；工具按钮 `aria-label` 全称（`粗体`、`斜体`、`引用`、`无序列表`、`有序列表`、`行内代码`、`链接`、`提及`）。
- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/lib/discussion/toolbar.test.ts tests/components/discussion/MarkdownEditor.test.tsx`
- [ ] **Step 3: 实现**（文本框 4 行起、自动增高到 16 行；工具条只在「编写」可用；窄屏底栏 `支持 Markdown`；改值用 `setRangeText` 后 `onChange`）
- [ ] **Step 4: 跑测试确认通过**

### Task 10: @ 联想 `MentionSuggest`

**Files:**
- Create: `patra-portal/src/lib/discussion/mention-query.ts`（纯函数）、`patra-portal/src/lib/discussion/caret.ts`（镜像 div 量坐标）
- Create: `patra-portal/src/components/discussion/MentionSuggest.tsx`
- Modify: `patra-portal/src/components/discussion/MarkdownEditor.tsx`（`mentions` 不为 false 时挂联想、键盘转发、`aria-*`）
- Test: `tests/lib/discussion/mention-query.test.ts`、`tests/components/discussion/MentionSuggest.test.tsx`

**Interfaces:**
- Produces: `findMentionQuery(value: string, caret: number): { start: number; query: string } | null`（`start` 是 `@` 的下标；触发规则 spec 6.3）；`replaceMention(value, query: { start; query }, username: string): { value: string; caret: number }`（换成 `@用户名 `）；`shouldAutoClose(query: string, resultCount: number): boolean`（`resultCount === 0 && graphemeCount(query) > 6`）；`caretCoordinates(textarea: HTMLTextAreaElement, index: number): { top: number; left: number; height: number }`。
- Produces: `MentionSuggest({ subject, query: string, anchor: { top; left; height } | null, activeIndex, onActiveIndexChange, onPick(s: MentionSuggestion): void, onResultsChange(count: number): void, listId: string })`。

- [ ] **Step 1: 写失败的测试**
  - `mention-query.test.ts`：`"hi @ch|"` → `{start:3, query:"ch"}`；行首 `@` 与 `"(@ch"` 触发；`"a@ch"` 不触发；`"@ch en"`（光标在空格后）→ null；`"@陈"` → query `陈`；`"@ch,"` 光标在逗号后 → null；`replaceMention` 结果与 caret；`shouldAutoClose("abcdefg", 0)` true、`("abcdef", 0)` false、`("abcdefg", 1)` false。
  - 组件（mock `fetch`，`renderWithQuery`）：query 为空 → 小标题 `本讨论的参与者`；无参与者 → `输入用户名或昵称查找同行`；两组之间分隔、`其他用户` 小标题；无结果 → `没有找到「chen-yuh」。输入完整的用户名也能 @ 到对方。`；接口 502 → `暂时查不到用户，输入完整的用户名也能 @ 到对方。`；查询中 `正在查找…`；挂在 `MarkdownEditor` 里：输入 `@zh` → `listbox` 出现，`ArrowDown` + `Enter` 后值变成 `@zhou-min `、listbox 消失；`Esc` 关闭；输入法组字中 `Enter` 不选中（Review Focus 2）；文本框 `role="combobox"`、`aria-expanded`、`aria-activedescendant` 跟随。
- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/lib/discussion/mention-query.test.ts tests/components/discussion/MentionSuggest.test.tsx`
- [ ] **Step 3: 实现**（请求 200ms 防抖；弹层 `fixed` 定位在 `textarea.getBoundingClientRect()` + 坐标，宽 296、右侧越界左收；≤ 880px `left: 0; right: 0` 相对编辑框；不测像素）
- [ ] **Step 4: 跑测试确认通过**

### Task 11: `CommentComposer`（发表 / 回复 / 编辑）

**Files:**
- Create: `patra-portal/src/components/discussion/CommentComposer.tsx`
- Test: `tests/components/discussion/CommentComposer.test.tsx`

**Interfaces:**
- Consumes: Task 5 三个 action 与 `commentFailureMessage`、Task 9 `MarkdownEditor`、`useCurrentUser`、`ResendVerificationButton`、`FormAlert`（`grep -rn "export function FormAlert" src` 确认位置）、`useRouter`。
- Produces: `CommentComposer({ subject, mode: { kind: "root" } | { kind: "reply"; root: Comment; target: Comment } | { kind: "edit"; comment: Comment; source: string }, collapsible?: boolean, placeholder: string, viewer: ViewerSeed, onDone(result: { comment: Comment } ): void, onCancel(): void, onTargetGone?(): void })`；`interface ViewerSeed { status: "anonymous" } | { status: "authenticated"; emailVerified: boolean; email: string; avatar: AvatarIdentity }`（服务端首屏的初值；水合后用 `useCurrentUser`，`pending` 时用初值）；导出 `useHasDraft()` 不需要——放弃确认由组件内部状态完成，父组件通过 `onDirtyChange?(dirty: boolean)` 知道有没有内容。

- [ ] **Step 1: 写失败的测试**（mock `@/lib/interaction/discussion-actions`）
  - 匿名 → `登录后参与讨论` 链接 `href` 为 `/login?next=%2Fpapers%2F1%3Fcomment%3D5`（当前地址含查询串）+ `注册`；
  - 未验证 → 文本框 `disabled`、提示 `验证邮箱后才能发表评论。验证邮件已发到 a@b.c。` + `重新发送验证邮件`；
  - 空白内容主按钮禁用；收起态点击展开并聚焦；
  - 提交：按钮 `发表中…`、文本框只读；成功调 `onDone`、清空；回复模式按钮 `回复` / `回复中…`，编辑 `保存` / `保存中…`；
  - 提交中再按 `⌘Enter`、再点按钮 → action 只调用一次（Review Focus 3）；
  - 原因码提示：`too-frequent` → `发得太快了，请隔 10 秒再发。`、内容保留；`reply-target-gone` → 提示文案 + 调 `onTargetGone`；`email-unverified` → 提示 + 重新发送按钮；
  - `signed-out` 且有内容 → `登录已过期，去重新登录？已写的内容会丢失。` + `去登录`（`href` 带 `next`）/ `留在这里`；
  - 取消：无内容直接 `onCancel`；有内容 → `放弃正在写的内容？` + `放弃` / `继续写`；
  - 编辑：内容与 `source` 相同保存 → 不调 action、直接 `onCancel`；
  - 回复框上方：`回复 @zhou-min` + `×`（点 `×` 改为 `回复 陈宇 的评论`）；回复根评论时 `回复 陈宇 的评论`、没有 `×`。
- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/components/discussion/CommentComposer.test.tsx`
- [ ] **Step 3: 实现**
- [ ] **Step 4: 跑测试确认通过**

### Task 12: `DiscussionView` 列表视图

**Files:**
- Create: `patra-portal/src/components/discussion/DiscussionView.tsx`、`CommentThread.tsx`、`DiscussionEmpty.tsx`、`DiscussionSkeleton.tsx`
- Create: `patra-portal/src/lib/discussion/copy.ts`（`interface DiscussionCopy { title: string; emptyTitle: string; emptyBody: ReactNode; composerPlaceholder: string; emptyPlaceholder: string; unavailableTitle: string; unavailableBody: string }`；`PAPER_DISCUSSION_COPY`、`POST_REPLIES_COPY`（简报 §3.1、§3.5、§3.11、§3.21 原文））
- Create: `patra-portal/src/lib/discussion/use-discussion-location.ts`
- Test: `tests/components/discussion/DiscussionView.test.tsx`、`tests/lib/discussion/use-discussion-location.test.tsx`

**Interfaces:**
- Consumes: Task 6 查询与 `cache.ts`、Task 8 `CommentItem`、Task 11 `CommentComposer`。
- Produces: `useDiscussionLocation(): { view: { mode: "list"; sort: DiscussionSort } | { mode: "single"; commentId: string }; setSort(s: DiscussionSort): void; openSingle(commentId: string): void; showAll(): void }`（读 `useSearchParams()`；`setSort`、`showAll` 用 `history.replaceState`，`openSingle` 用 `history.pushState`，保留其他查询参数与路径）。
- Produces: `DiscussionView({ subject, copy, viewer: ViewerSeed, now: number, basePath: string, shareBase: string })`（`basePath` 如 `/papers/1`，拼站内永久链接；`shareBase` 是 `publicUrl(basePath)` 的完整公开地址，「复制链接」用）；`CommentThread({ thread, expanded, … })`。

- [ ] **Step 1: 写失败的测试**（`renderWithQuery` 预置 `queryKeys.discussionThreads(...)` 数据；mock `next/navigation` 的 `useSearchParams`）
  - 有评论：根评论 + 前 3 条回复 + `展开更多回复`（`aria-expanded="false"`）；点后请求 `/api/comments/<root>/replies?cursor=c1`，接上回复、按钮消失；
  - 排序分段控件 `正序` / `最新`（`aria-pressed`）；点 `最新` → `history.replaceState` 的地址带 `sort=latest`、请求 `sort=latest`；切换不清空编辑器内容；没有评论时不出排序控件；
  - `加载更多讨论` → 请求带 `cursor`，加载中 `加载中…`；
  - 空状态：已验证用户 → `这篇文献还没有讨论`、三行提示、编辑器展开态占位 `例如：每组只有 47 例，而且是回顾性前后对照……`；匿名 → `登录后参与讨论`；
  - 发表根评论 `latest` → 出现在最前；`asc` 且有下一页 → `已发表。按正序排在最后，` + `去看看`；`role="status"` 播报 `已发表`；
  - 回复：点某楼 `回复` → 回复框出现在这一楼最后；另一个回复框有内容时先出 `放弃正在写的回复？`；
  - 降级：查询失败 → `讨论暂不可用` + `文献内容不受影响，请稍后再试。` + `重试`（点后重新请求）；`window` 触发 `focus` 时自动重新请求；
  - `use-discussion-location.test.tsx`：三个方法写出的地址；`popstate` 后 `view` 跟随（Review Focus 5 的一半）。
- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/components/discussion/DiscussionView.test.tsx tests/lib/discussion/use-discussion-location.test.tsx`
- [ ] **Step 3: 实现**（新条目高亮 2 秒：加 `data-fresh` 再移除，CSS 过渡 `dur-slow`，`motion-reduce` 只保留颜色；焦点到新评论的时间链接）
- [ ] **Step 4: 跑测试确认通过**

### Task 13: 单楼视图、编辑、删除、查看对话、修改历史、复制链接

**Files:**
- Create: `patra-portal/src/components/discussion/SingleThreadView.tsx`、`ConversationDialog.tsx`、`RevisionHistoryDialog.tsx`、`DeleteCommentDialog.tsx`
- Create: `patra-portal/src/lib/discussion/revision-diff.ts`
- Modify: `patra-portal/src/components/discussion/DiscussionView.tsx`（按 `view.mode` 切单楼；接上编辑、删除、对话框、复制链接）
- Test: `tests/lib/discussion/revision-diff.test.ts`、`tests/components/discussion/SingleThreadView.test.tsx`、`tests/components/discussion/dialogs.test.tsx`

**Interfaces:**
- Consumes: Task 6 `useSingleThread` / `useConversation` / `useRevisions` / `fetchCommentSource` / `applyDeletion…` / `replaceThread`、Task 5 `deleteComment`、`useCopy`、`toast`（sonner）、`Dialog`。
- Produces: `type DiffPart = { kind: "same" | "added" | "removed"; text: string }`；`resolveMentionMarkers(body: string, mentions: readonly Mention[]): string`（`<@ID>` → `@用户名` / `@昵称` / `@已注销用户`，转义与行内代码里的不换——复用 Task 1 的解析：用 `fromMarkdown` + `remarkSubset` 的扩展找 `mention` 节点位置再替换）；`diffVersions(previous: string, current: string): DiffPart[]`（`Intl.Segmenter("zh", { granularity: "word" })` 切词 + `diffArrays`）。

- [ ] **Step 1: 写失败的测试**
  - `revision-diff.test.ts`：`diffVersions("样本量太小", "样本量明显太小")` 含 `{kind:"added", text:"明显"}`；英文按单词；`resolveMentionMarkers("同意 <@42> 与 \\<@43>", …)` → `同意 @zhou-min 与 \\<@43>`。
  - `SingleThreadView`：顶部 `你正在看讨论中的一楼。` + `查看全部讨论`；整楼全部回复展开；目标条目 `aria-current="true"` 且有高亮类；不出排序控件；顶部是回复框收起态，占位 `回复这一楼…`；`{ missing: true }` → `这条评论已删除，或者不在这里的讨论中。` + `查看全部讨论`、无列表；目标是占位 → 照常显示一楼、占位高亮；`popstate` 回到 `?comment=` 时仍按单楼渲染（Review Focus 5）。
  - 对话框：`查看对话` → 标题 `对话`，列出链上评论、当前条目高亮，底部 `只看这一楼 →` 调 `openSingle`；`已编辑` → `修改历史`、副标题 `发布 5 分钟后的修改会留下记录，所有人都能看到。`、版本标签 `当前版本 · 10 月 26 日 18:40 修改` / `最初版本 · 10 月 25 日 21:03 发表`、图例、新增用 `ins`、删除用 `del`、最初版本无标记；失败 → `修改历史暂时打不开` + `重试`；删除：有回复 → `这条评论下已经有回复。删除后会留下「该评论已删除」，回复照常保留。`，无回复 → `删除后不能恢复。`；按钮 `删除评论` / `删除中…`；成功 toast `评论已删除`、调用单楼接口对账；失败 → `没有删除成功，请稍后再试`。
  - 编辑：`编辑` → 请求 `/api/comments/5/source`，编辑器内容是返回的原文、按钮 `保存`；保存成功替换条目。
  - 复制链接：`复制链接` 复制的是 `<公开地址>/papers/1?comment=5`，成功 toast `已复制链接`。
- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/lib/discussion/revision-diff.test.ts tests/components/discussion/SingleThreadView.test.tsx tests/components/discussion/dialogs.test.tsx`
- [ ] **Step 3: 实现**（公开地址：`Discussion` 服务端用 `publicUrl(basePath)` 算好传 `shareBase` 下来；滚动到目标用 `scrollIntoView({ block: "start" })` + `scroll-mt-[126px]`）
- [ ] **Step 4: 跑测试确认通过**

### Task 14: 服务端入口与文献页接线

**Files:**
- Create: `patra-portal/src/lib/discussion/server.ts`（`prefetchDiscussion`）
- Create: `patra-portal/src/components/discussion/Discussion.tsx`（async RSC）、`DiscussionSection.tsx`（RSC：`section` + 标题 + `Suspense`）
- Modify: `patra-portal/src/components/portal/paper-detail/PublicationDetailView.tsx`（`discussion?: ReactNode`，放在 `{fullText}` 后）
- Modify: `patra-portal/src/app/papers/[id]/page.tsx`（`searchParams`）
- Test: `tests/lib/discussion/server.test.ts`、`tests/app/paper-discussion.test.tsx`

**Interfaces:**
- Consumes: Task 4 读函数、Task 12 / 13 视图、`lookupSession`、`publicUrl`。
- Produces: `prefetchDiscussion(subject: Subject, view: DiscussionLocationView): Promise<DehydratedState>`（列表：`fetchThreads` 成功时 `setQueryData(discussionThreads, { pages: [data], pageParams: [null] })`；单楼：成功放 `discussionSingle`，`not-found` 放 `{ missing: true }`；失败不放）；`parseDiscussionView(searchParams: Record<string, string | string[] | undefined>): DiscussionLocationView`（`comment` 过 `isEntityId`，`sort` 只认 `latest`）；`DiscussionSection({ subject, view, copy, basePath })`。

- [ ] **Step 1: 写失败的测试**
  - `server.test.ts`：列表成功 → 脱水状态里有 `discussionThreads` key；失败 → 没有；单楼 404 → `{ missing: true }`；`parseDiscussionView({ comment: "5", sort: "latest" })` → 单楼；`{ comment: "x" }` → 列表正序；`{ sort: ["latest","asc"] }` → 正序。
  - `paper-discussion.test.tsx`：`PublicationDetailView` 收到 `discussion` 插槽时它在主栏最后（`fullText` 之后，DOM 顺序）；`DiscussionSection` 渲染 `section#discussion` 且 `aria-labelledby` 指向 `讨论 · Discussion`。
- [ ] **Step 2: 跑测试确认失败**：`pnpm -C $P exec vitest run tests/lib/discussion/server.test.ts tests/app/paper-discussion.test.tsx`
- [ ] **Step 3: 实现**（`Discussion` 里 `lookupSession()` → `ViewerSeed`；`now = Date.now()` 传下去；`HydrationBoundary` 包 `DiscussionView`；`DiscussionSection` 的 Suspense fallback 是 `DiscussionSkeleton`）
- [ ] **Step 4: 跑测试确认通过**
- [ ] **Step 5: 全量门禁**：`pnpm -C $P lint`、`pnpm -C $P typecheck`、`pnpm -C $P test`、`pnpm -C $P build` 全过（build 查 RSC 动态渲染信号）

### Task 15: 浏览器走查与 Playwright（对 mini）

**Files:**
- Create: `patra-portal/tests/e2e/discussion.spec.ts`
- Create: `patra-portal/tests/e2e/support/discussion.ts`（`verifiedAccount(page)`：注册 + Mailpit 验证；`pickPaper(page)`；`composer(page)`；`postComment(page, body)`）

**Interfaces:**
- Consumes: `tests/e2e/support/account.ts`、`mailpit.ts`（`latestToken`）。

- [ ] **Step 1: 起开发服务器走查**：`preview_start { name: "portal-dev" }`（worktree 的 `.claude/launch.json` 已指向本 worktree 的 portal）；在内置浏览器打开一篇文献，桌面与 375 宽各看一遍：空状态、发表、回复、单楼、编辑器预览与联想；`read_console_messages` 无报错。发现问题回到对应任务改。
- [ ] **Step 2: 写 e2e**（spec 9.2 的 8 步；`test.describe.configure({ mode: "serial" })`，`for (const viewport of [{ width: 1440, height: 900 }, { width: 375, height: 812 }])` 各一组；每个账号连续两次发表之间至少 10 秒，或换账号；第 8 步前 `await page.waitForTimeout(剩余到 5 分 10 秒)`；`test.setTimeout(9 * 60_000)` 给最后一步；缺 `PATRA_GATEWAY_BASE_URL` / `PATRA_MAILPIT_BASE_URL` 时 `test.skip`；每次操作前按记忆等 `__reactProps$` 接管）
- [ ] **Step 3: 跑 e2e**
  Run: `PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 PATRA_MAILPIT_BASE_URL=http://100.103.73.27:8025 pnpm -C $P exec playwright test tests/e2e/discussion.spec.ts --workers=1`（后台跑，约 12 分钟）
  Expected: 两个视口全部 passed
- [ ] **Step 4: D9 手测步骤**：不擅自停 mini 的容器（会影响别的会话）。手测步骤写进交接笔记并在验收时交给用户：停 mini 上的 interaction 容器 → 打开文献页，讨论区显示「讨论暂不可用」、文献主体正常 → 启动容器 → 切回页面，讨论区自动恢复。由用户决定何时执行，结果记到收尾。

### Task 16: 交接笔记与文档

**Files:**
- Create: `/Users/linqibin/Library/Application Support/Claude/side-session-notes/local_05d5a37f-fa2d-4775-824c-b51aadd73808/portal-PAP-124-handoff.md`
- Modify: `patra-portal/README.md`（目录结构里加 `components/discussion/`、`lib/discussion/`、`lib/markdown/`；「讨论区」一节：Markdown 白名单在解析层、e2e 命令）
- Modify: `docs/patra/specs/2026-10-28-portal-discussion-design.md`（末尾「实施记录」：与本计划的偏离、验证结果）

- [ ] **Step 1: 写交接笔记**（spec 第 10 节：`DiscussionSection` 用法与 `POST_REPLIES_COPY`、`MarkdownBody variant="reading"`、`MarkdownEditor mentions={false} limits={{graphemes:2000,bytes:8000}}`、`AuthorAvatar` / `AuthorName`、`NeutralAvatar`、`formatRelative`、永久链接格式、`queryKeys.discussion` 前缀与清缓存、D9 手测步骤）
- [ ] **Step 2: 更新 README 与 spec 实施记录**
- [ ] **Step 3: 最终门禁**：`pnpm -C $P lint && pnpm -C $P typecheck && pnpm -C $P test` 全过；`git -C <worktree> status` 只含本 Issue 的路径
- [ ] **Step 4: 停在本地**，请用户在浏览器里验收（1440、375），同意后再提交
