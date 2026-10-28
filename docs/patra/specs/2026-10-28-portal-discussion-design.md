# 门户文献讨论区与编辑器（PAP-124）工程 spec

> **Issue**：Linear PAP-124（GitHub #301），v0.14 Discussion 前端第一个 Issue；PAP-125（投稿经验）、PAP-126（提醒）复用本 Issue 的组件与调用层。
> **产品定义**：release spec `docs/patra/release-specs/v0.14-discussion.md` 2.1.1、决策 C、F，Done 判定 D4、D9。
> **页面、状态、文案**：设计简报 `patra-portal/docs/design-briefs/v0.14/discussion.md`（§3.1–§3.12、§3.20–§3.22、§4.1–§4.2、§6），画布第 6 版 <https://claude.ai/artifact/DcyaGqN7xUMiXNbK3exaBN>（`Paper` / `Paper-M` / `Discussion` / `Composer` / `Md` / `Markdown`）。本 spec 不重抄文案，实现时逐条对照简报的状态 ID。
> **接口**：后端工程 spec `docs/patra/specs/2026-10-27-interaction-discussion-design.md` 第 5、6 节（已部署到 mini，2026-10-28 核对 `GET /patra-interaction/discussions/publication/{id}/threads` 返回 `{items:[],nextCursor:null}`）。
> **创建日期**：2026-10-28

---

## 1. 要做什么

文献页主栏最后加一个讨论区：匿名访客能读，登录且邮箱已验证的人能发根评论、回复、编辑、删除，能 @ 同行；评论有永久链接，打开是「单楼视图」。interaction 出问题时讨论区降级，文献页照常。

讨论区要做成一个可以挂在任何对象下的组件：PAP-125 的经验页 `/posts/{id}` 直接用它显示经验下的回复，PAP-126 的提醒点进来走同一个永久链接。

## 2. 范围

**做**：

1. 调用层：服务端读（RSC 用）、Route Handler（客户端读）、Server Action（写）、zod 响应体、queryKeys、用户切换时清缓存。
2. `Discussion` 组件族：列表（两层、排序、分页、展开更多回复）、条目（署名、「作者」、匿名、已注销、「回复 @X」、已编辑、占位、长评论折叠、`···` 菜单）、空状态、单楼视图、查看对话、修改历史、删除确认、降级。
3. 编辑器：编写 / 预览、Markdown 工具条、字素与字节计数、@ 联想（定位到光标）、资格与错误提示、放弃确认。
4. Markdown 子集渲染（评论、预览共用；PAP-125 的经验正文也用）。
5. 文献页接线：`?comment=`、`?sort=latest`、Suspense、降级。
6. `Avatar` 补中性头像与新尺寸，`formatRelative` 复用。
7. 单测 + Playwright（对 mini，1440×900 与 375×812）。
8. 共享笔记 `portal-PAP-124-handoff.md`（给 PAP-125 / 126 的用法）。

**不做**：投稿经验的任何页面（PAP-125）、铃铛与提醒页（PAP-126）、草稿保存（简报 §2.2：本版不存草稿）、楼层号与计数（spec 第 3 节 A）。

## 3. 简报与后端 spec 不一致的地方（按后端执行）

| 处 | 简报 | 后端 spec（已实现） | 门户做法 |
|---|---|---|---|
| 主页对访客不可见的人（封禁、冷静期、邮箱未验证、主页设为仅自己） | §3.20 封禁用户「`@用户名` 照常」 | 5.8：`profileVisible=false` 时 `username`、`avatarUrl` 为 null，`displayName` 照常；后端 spec 写明「由 PAP-124 同步」 | 署名只有昵称、默认头像（首字 + 色号）、不链主页、没有 `@用户名`；提及显示 `@昵称`、不链接；「回复 @X」写 `回复 @昵称`、不链接 |
| 字节上限 | §3.12「工程 spec 定上限」 | 5.3：UTF-8 ≤ 20,000 字节（字素上限 × 4） | 编辑器同时卡 5,000 字素与 20,000 字节 |
| 互动服务恢复 | §3.21「讨论区要用户点重试或刷新」 | — （release spec D9：「容器恢复后这几处自动恢复」） | 按 release spec（简报自己写明不一致时以 spec 为准）：见 6.5 |

## 4. 接口与调用层

### 4.1 后端接口（摘要，形状以后端 spec 6.1–6.5 为准）

网关前缀 `/patra-interaction`。`type` 是 `publication` / `post`，`key` 是门户地址里的 ID。

| 用途 | 后端 | 门户怎么调 |
|---|---|---|
| 列表一页 | `GET /discussions/{type}/{key}/threads?sort&cursor&size` | 首屏：RSC；之后：Route Handler `GET /api/discussions/[type]/[key]/threads` |
| 单楼 | `GET /discussions/{type}/{key}/comments/{id}/thread` | 首屏：RSC；「查看对话」底部「只看这一楼」之后：Route Handler `GET /api/discussions/[type]/[key]/comments/[id]/thread` |
| 展开更多回复 | `GET /comments/{rootId}/replies?cursor&size` | `GET /api/comments/[id]/replies` |
| 查看对话 | `GET /comments/{id}/conversation` | `GET /api/comments/[id]/conversation` |
| 修改历史 | `GET /comments/{id}/revisions` | `GET /api/comments/[id]/revisions` |
| 编辑原文 | `GET /comments/{id}/source` | `GET /api/comments/[id]/source` |
| @ 联想 | `GET /discussions/{type}/{key}/mention-suggestions?q` | `GET /api/discussions/[type]/[key]/mention-suggestions` |
| 发表 / 回复 | `POST /discussions/{type}/{key}/comments` | Server Action `publishComment` |
| 编辑 | `PUT /comments/{id}` | Server Action `editComment` |
| 删除 | `DELETE /comments/{id}` | Server Action `deleteComment` |

读接口登录可选（`callInteraction(..., { auth: "optional" })`）；联想与编辑原文要登录。

### 4.2 文件落点

| 文件 | 内容 |
|---|---|
| `src/lib/interaction/discussion-schema.ts` | zod：`Card`（作者名片）、`Comment`、`Thread`、`ThreadPage`、`SingleThread`、`Replies`、`Conversation`、`Revisions`、`MentionSuggestion`、`DeleteResult`；`SubjectType = "publication" \| "post"`。不加 `server-only`：客户端组件要用类型 |
| `src/lib/interaction/discussion.ts`（`server-only`） | `fetchThreads(subject, sort, cursor?)`、`fetchSingleThread(subject, commentId)`、`fetchReplies`、`fetchConversation`、`fetchRevisions`、`fetchSource`、`fetchMentionSuggestions`，都返回 `Read<T>`，超时 `READ_TIMEOUT_MS`；参数先校验（`type` 在枚举里、`key` / `commentId` 是 `isEntityId`），不合格直接 `not-found`，不请求网关 |
| `src/lib/interaction/discussion-actions.ts`（`"use server"`） | 三个写操作，返回按 `reason` 区分的结果（4.3），不带文案；用现有 `mutate()` |
| `src/app/api/discussions/[type]/[key]/…`、`src/app/api/comments/[id]/…` | Route Handler，`readReply()` 统一回写；GET 不做同源校验（与现有读接口一致） |
| `src/lib/discussion/queries.ts`（客户端） | `useThreads`（`useInfiniteQuery`）、`useSingleThread`、`useConversation`、`useRevisions`、`useMentionSuggestions`、`fetchReplies`、`fetchSource` |
| `src/lib/discussion/cache.ts` | 纯函数：把写操作的返回并进缓存（4.5） |
| `src/lib/discussion/server.ts`（`server-only`） | `prefetchDiscussion(subject, view)`：照 `lib/library/server.ts` 的 `prefetchLibrary`，取到的放进临时 `QueryClient` 再脱水 |
| `src/lib/discussion/messages.ts` | 原因码 → 文案（简报 §4.2 原文） |
| `src/lib/query-keys.ts` | 新增 `discussion` 前缀（4.4） |
| `src/lib/query-client.ts` | 身份变化时 `removeQueries({ queryKey: queryKeys.discussion })` |

### 4.3 写操作的结果

```ts
type CommentFailure =
  | { ok: false; reason: "too-frequent" | "daily-limit"; retryAfterSeconds: number | null }  // 429 TOO_FREQUENT / DAILY_LIMIT_REACHED
  | { ok: false; reason: "email-unverified" | "account-restricted" }                         // 409 EMAIL_UNVERIFIED / ACCOUNT_RESTRICTED
  | { ok: false; reason: "reply-target-gone" }                                                // 409 REPLY_TARGET_GONE
  | { ok: false; reason: "subject-gone" }                                                     // 404 SUBJECT_UNAVAILABLE（经验已删除）
  | { ok: false; reason: "comment-gone" }                                                     // 404 COMMENT_NOT_FOUND（编辑、删除时评论已不在）
  | { ok: false; reason: "too-many-mentions" | "too-long" | "empty" | "invalid-character" }   // 422 errors[] body × …
  | { ok: false; reason: "forbidden" }                                                        // 403
  | CommonFailure;                                                                            // signed-out / unavailable
```

- `publishComment(subject, { body, parentId })` → `{ ok: true, comment }`
- `editComment(commentId, body)` → `{ ok: true, comment }`
- `deleteComment(commentId)` → `{ ok: true, placeholder, removedIds }`
- Server Action 入口先校验参数（类型、ID 格式、`body` 是字符串）；不合格回 `unavailable`（同 `library-actions.ts`）。
- 文案映射（`messages.ts`）：`too-frequent` → `发得太快了，请隔 10 秒再发。`；`daily-limit` → `今天已经发表了 100 条评论，明天再来吧。`；`account-restricted` → `账号状态异常，暂时不能发表。`；`email-unverified` → `验证邮箱后才能发表评论。` + 重新发送；`too-many-mentions` → `一条评论最多 @ 10 人。`；`too-long` → `内容太长了，请删减一些。`；`reply-target-gone` → `要回复的评论已被删除，内容还在输入框里。`；`subject-gone` → `这条投稿经验已被删除，不能再回复。`（文献页不会出现：文献对象不会被标为删除）；`unavailable` → `没有发表成功，请稍后再试。`（编辑：`没有保存成功，请稍后再试`；删除：`没有删除成功，请稍后再试`）。`empty`、`invalid-character`、`forbidden`、`comment-gone` 前端正常操作到不了，按 `unavailable` 的文案显示。
- `signed-out`：编辑器里有内容时原地确认 `登录已过期，去重新登录？已写的内容会丢失。` + `去登录` / `留在这里`；没内容直接去 `/login?next=<当前地址>`。

### 4.4 queryKeys

```ts
discussion: ["discussion"] as const,                                       // 公共前缀：身份变化时整体清掉
discussionThreads: (type, key, sort) => ["discussion", type, key, "threads", sort] as const,
discussionSingle: (type, key, commentId) => ["discussion", type, key, "single", commentId] as const,
discussionConversation: (commentId) => ["discussion", "conversation", commentId] as const,
discussionRevisions: (commentId) => ["discussion", "revisions", commentId] as const,
discussionMentions: (type, key, q) => ["discussion", type, key, "mentions", q] as const,
```

全部放在 `discussion` 前缀下：`mine`、联想里「排除自己」都按访客不同，身份一变整体清掉（`query-client.ts` 已有的订阅里加一行）。清掉之后，列表的观察者重新挂载时没有缓存，由 Route Handler 带新会话重取。首屏数据由 `DiscussionCache` 放进缓存，**只作初值**：挂载时放一次、只放缓存里还没有数据的查询，不用 `initialData`（清缓存后会被同一份旧 props 再灌回去，`mine` 不会刷新），也不用 `HydrationBoundary`（对已有的查询会在服务端那份时间戳更新时覆盖，见 12.1）。

### 4.5 写操作怎么并进缓存（纯函数，`lib/discussion/cache.ts`）

不乐观（简报 §6）：等服务端返回再改界面。

| 操作 | 列表（`discussionThreads`） | 单楼（`discussionSingle`） |
|---|---|---|
| 发表根评论 | `latest`：插到第一页最前；`asc` 且已加载到最后一页（`nextCursor` 为空）：接到最后一页末尾；`asc` 且还有下一页：不动，编辑器下方出「已发表。按正序排在最后，去看看」（链到新评论的单楼视图） | 不会发生（单楼视图里顶部是回复框） |
| 回复 | 接到该楼 `replies` 末尾；这一楼标记为「已全部展开」（回复框打开前已展开） | 同左 |
| 编辑 | 按 `commentId` 替换（根或回复都找） | 同左 |
| 删除，`placeholder=true` | 这一条换成占位（保留 `commentId`、`rootId`、`parentId`、`hasReplies=true`、`createdAt`） | 同左 |
| 删除，`removedIds` | 去掉这些 ID：根评论在其中就去掉整楼；否则从楼里去掉；被去掉那条的父评论 `hasReplies` 由后端级联判断，前端只按 `removedIds` 去 | 根评论在其中：切到 `disc.single.missing`；否则同左 |
| 展开更多回复 | 把取到的 `items` 接到该楼 `replies` 后，更新 `repliesCursor` / `hasMoreReplies` | — |

**删除后对账**：删除接口只回 `{ placeholder, removedIds }`，楼里别的条目上由它派生的字段（子回复的 `replyTo` 变成 `{ deleted: true }`、父评论的 `hasReplies`）前端推不全（楼可能折叠着）。所以删除成功后先按上表本地改（界面立即变），再用单楼接口按根评论 ID 重取这一楼、整楼替换（这一楼随之完整展开）；根评论已在 `removedIds` 里就不重取。重取失败保留本地结果，不报错。

排序切换、展开回复是乐观的（简报 §6），本来也不涉及写。

## 5. Markdown 子集渲染 `MarkdownBody`

### 5.1 依赖与做法

用 **react-markdown**（unified / remark 生态，门户里第一个 Markdown 依赖）。白名单在**解析层**做：用 micromark 的 `disable` 扩展关掉不放行的构造，它们的原文就按普通文字显示——这是简报 §3.7「`# 标题`、HTML、表格、代码块按原文显示为普通文字」要的效果，解析后再剔元素做不到（会丢掉 `#`、吞掉标签）。

| 构造 | 处理 |
|---|---|
| 段落、强调（`*` `_` `**`）、引用、列表、行内代码、链接 `[文字](网址)`、反斜杠转义 | 放行 |
| ATX / setext 标题 | 照常解析，渲染成同字号的粗体段落、去掉 `#`（2026-10-28 用户定，原为按原文显示） |
| 缩进代码、围栏代码、HTML（块级与行内）、分隔线、链接定义（引用式链接） | `disable.null` 关掉，原文是文字 |
| 图片 `![x](y)` | 关掉 `labelStartImage` 会变成 `!` + 链接，不是原文；改为保留解析，渲染时 `components.img` 按节点的 `position` 从原文切回 `![x](y)` 显示成文字，不发请求 |
| 嵌套列表 | 简报：不支持嵌套，缩进的子项按普通行显示。remark 插件把 `listItem` 里的子 `list` 换成按 `position` 切回的原文段落 |
| 单个换行 | `remark-breaks`：保留为换行；空行分段 |
| 表格、删除线、任务列表、脚注 | 不加 GFM，自然不解析 |

`allowedElements` 再兜一层（`p`、`strong`、`em`、`blockquote`、`ul`、`ol`、`li`、`code`、`a`、`br`、`span`），`skipHtml`。

### 5.2 提及、PMID、DOI、裸网址

- **提及 `<@数字>`**：写一个 micromark 行内构造（在 `<` 上触发，只认 `<@` + 规范十进制正整数 + `>`），配一个 mdast 扩展产出 `mention` 节点，remark-rehype 的 handler 把它变成带用户 ID 的 `span`，组件按条目的 `mentions` 渲染。这样**转义的 `\<@42>`（反斜杠转义先于它处理）与行内代码里的不会被认成提及**，与后端 5.4.1、5.4.3 的边界一致——在文本节点上用正则替换做不到这一点（转义后的文字和标记在 mdast 里一模一样）。
  - 渲染：`username` 有值 → `@用户名`，链 `/u/<用户名>`（`link` 色、中粗）；`deleted` 或在 `mentions` 里找不到 → `@已注销用户`（`fg-3`，不链接）；`username` 为 null → `@昵称`，不链接（第 3 节）。
- **PMID、DOI、裸网址**：remark 插件在文本节点上查找替换（`mdast-util-find-and-replace`），跳过 `link`、`inlineCode` 子树：
  - PMID：`PMID 27940189`、`PMID: 27940189`、`PMID:27940189`（`PMID` 不分大小写，前面不是字母数字）→ 整段链 `https://pubmed.ncbi.nlm.nih.gov/<数字>/`。
  - DOI：`10.` + 4–9 位数字 + `/` + 非空白，去掉末尾的 `.,;:!?)]}。，；：！？）」』` 等标点；`doi:` 前缀一起算进链接文字；链 `https://doi.org/<DOI>`。`https://doi.org/…` 本身是裸网址，按网址处理。
  - 裸网址：`https?://` 到空白或 `<`、`>` 为止，同样去掉末尾标点（与后端 5.4.1 的网址边界一致）。
- **链接一律**：只放行 `http:` / `https:`（其余协议按文字显示），`target="_blank"`、`rel="nofollow ugc noopener"`，末尾 `ArrowUpRight` 12px（`aria-hidden`），读屏名称后加 `（新窗口打开）`；裸网址显示超过 48 个字符时中间省略（`title` 是完整网址）。

### 5.3 组件

`MarkdownBody({ source, mentions, variant })`：`variant` = `comment`（15px `leading-relaxed`）/ `small` / `reading`（PAP-125 的经验正文，衬线 `text-lg` `leading-reading`）。服务端、客户端都能渲染（没有 hooks）。样式照简报 §3.7（引用 2px `ink-300` 竖线、行内代码 `paper-200` 底等）。

## 6. 讨论区

### 6.1 组件

放 `src/components/discussion/`（`components/portal/Composer.tsx` 已是检索框，编辑器不叫 `Composer`）。

| 组件 | 类型 | 说明 |
|---|---|---|
| `Discussion` | RSC（async） | 入口：`subject`、`view`（`{ mode: "list", sort }` / `{ mode: "single", commentId }`）、`copy`（标题、空状态、占位文案）。读会话（`lookupSession`，React `cache`，与导航栏共用）、预取首屏、`HydrationBoundary` 包住 `DiscussionView` |
| `DiscussionSection` | RSC | 外壳：`<section id="discussion">` + 标题；`Suspense`（fallback 是 `disc.loading` 骨架）包住 `Discussion`。文献页、经验页都用它 |
| `DiscussionView` | 客户端 | 排序、分页、单楼 / 列表切换、空状态、降级、回复框的「同一时间只开一个」 |
| `CommentThread` / `CommentItem` | 客户端 | 一楼与一条（简报 §3.2、§3.3） |
| `CommentByline` | 客户端 | 署名行：头像、名字、`@用户名`、「作者」、时间链接、「已编辑」、「回复 @X」 |
| `CommentComposer` | 客户端 | 发表 / 回复 / 编辑三用（简报 §3.5），内含 `MarkdownEditor` |
| `MarkdownEditor` | 客户端 | 编写 / 预览、工具条、计数、@ 联想开关；PAP-125 经验正文直接用它（关掉 @、上限 2,000） |
| `MentionSuggest` | 客户端 | 定位到光标的联想弹层（6.3） |
| `ConversationDialog` / `RevisionHistoryDialog` / `DeleteCommentDialog` | 客户端 | 都用现有 `Dialog`（≤ 880px 自动是底部抽屉） |
| `MarkdownBody` | 通用 | 第 5 节 |

### 6.2 首屏、排序、单楼的地址

- 文献页 `page.tsx` 加 `searchParams`；`comment`（`isEntityId`）在就进单楼视图，否则列表；`sort=latest` 是「最新」，其余都是正序。在 Suspense 里的 `Discussion` 内 await，不拖慢文献页主体。
- **当前视图以地址为准**：`DiscussionView` 用 `useSearchParams()` 推出视图（`comment`、`sort`），服务端的 `view` 只决定首屏预取哪一份。视图切换都只改地址，组件跟着地址走，不靠重挂。
- **排序切换**：地址用 `history.replaceState` 改成 `?sort=latest`（正序时去掉），客户端换查询（key 带 `sort`），不触发服务端重新渲染文献页。Next 16 会把原生 `replaceState` 同步进 `useSearchParams`。
- **永久链接**（时间链接、「只看这一楼」）：`history.pushState` 到 `?comment=<评论 id>`（可以后退），客户端取单楼（有缓存直接用）；整页打开、刷新、从提醒点进来时由服务端预取首屏。打开后滚到目标（`scroll-margin-top` = 导航栏 57 + 面包屑 45 + 24 = 126px）。链接的 `href` 仍是完整的永久地址（新标签页打开、复制都对）。
- **「查看全部讨论」**：`replaceState` 去掉 `?comment=`（保留 `sort`），客户端切到列表视图（没有缓存时显示骨架再取），滚到讨论区顶部。
- **单楼 404**（`COMMENT_NOT_FOUND`）→ `disc.single.missing`；不是整块降级。
- `canonical` 已经是 `/papers/<id>`（`paperMetadata`），带参数的地址不用改元数据（简报 §3.10、§3.23），实现时核对一次。

### 6.3 编辑器与 @ 联想

- **计数**：`graphemeCount`（已有）+ `TextEncoder` 字节数，与后端口径一致（换行统一成 `\n`、去首尾空白后算）。`≥ 80%` 上限 `amber-700`，超过 `status-danger` + 主按钮禁用；字素不超、字节超写 `内容太长了，请删减一些`。
- **@ 人数粗算**：按后端 5.4.1 的候选语法从原文里抠 `@用户名`（跳过行内代码、链接、网址，`@` 前面不是字母数字 `_` `@`），不分大小写去重，> 10 时 `一条评论最多 @ 10 人` + 禁用；以服务端为准。
- **工具条**：有选区包住，没选区插入成对标记、光标放中间；列表、引用按行前缀；用 `setRangeText` 改值后补发 `input` 事件，保留撤销栈尽量不丢（浏览器不保证，接受）。
- **联想触发**：光标前是 `@` + `[A-Za-z0-9_\-一-鿿]*`，且 `@` 前面是开头、空白或标点。查询词 = `@` 后面那段。关闭：空格、标点、`Esc`、光标离开这个词、失焦；没有结果且查询词超过 6 个字自动收起。选中后替换成 `@用户名 `。
- **定位**：镜像 div 量光标坐标（复制 textarea 的字体、内边距、宽度、换行规则，量 `@` 所在位置的 span），弹层锚在光标行下方、左对齐 `@`，宽 296px，超出右边界向左收；≤ 880px 占满编辑框宽、贴当前行下方。自己写，不引依赖（约 60 行，量法是公开的做法）。
- **请求**：`useMentionSuggestions(subject, q)`，200ms 防抖，`staleTime` 30 秒；已有的参与者结果先显示、查询中在底部转圈。接口失败显示 `暂时查不到用户，输入完整的用户名也能 @ 到对方。`。
- **读屏**：textarea `role="combobox"` `aria-autocomplete="list"` `aria-expanded` `aria-controls` `aria-activedescendant`；列表 `role="listbox"`，项 `role="option"`。
- **资格从客户端的当前用户读**（`useCurrentUser`，与导航栏同一个查询），服务端读到的会话只作首屏的初值：退出登录（`useLogout` 不刷新页面）、别的标签页验证了邮箱之后，编辑器跟着变，与身份变化清讨论缓存一致。
- **资格**：没登录 → `登录后参与讨论`（链 `/login?next=<当前地址，含 ?comment=>`）+ `还没有账号？注册`；`emailVerified=false` → 编辑器禁用 + `amber` 提示（复用 `ResendVerificationButton`，文案里带邮箱）。冷静期账号整站被带去取消注销页，封禁账号没有会话，这两种只靠服务端的 409。
- **提交**：`⌘/Ctrl Enter`；提交中只读；成功后清空收起，焦点到新评论的时间链接，`role="status"` 播报 `已发表`；新条目高亮 2 秒（`clay-50` 底淡出，尊重减少动态效果）。

### 6.4 其余交互要点（细节照简报）

- 每楼默认前 3 条（后端给的）；`展开更多回复` 不带条数，`aria-expanded`。
- 回复框：开在这一楼最后；先把这一楼完整展开；同一时间只开一个，另一个有内容时先确认 `放弃正在写的回复？`。回复目标已删除（409）时回复框上方改成 `回复这一楼`，`parentId` 换成根评论 ID。
- 长评论：正文渲染后超过 12 行折叠到 8 行（`ResizeObserver` 量高度），渐隐 + `展开全文` / `收起`。
- `···`：`复制链接`（`useCopy`，复制完整的公开地址，toast `已复制链接`）；本人另有 `编辑`、`删除`。编辑前取 `source`（后端把标记换回当前用户名）；取不到时编辑器下方提示 `没有保存成功，请稍后再试`。保存内容与原文相同就直接收起。
- 修改历史：对话框里每个版本显示 Markdown 原文（`pre-wrap`），与上一版（时间上更早的那版）比较标增删：新增 `moss-50` 底 + 下划线，删掉 `rust-50` 底 + 删除线；最初版本不标。原文里的 `<@ID>` 按 `mentions` 换成 `@用户名` / `@昵称` / `@已注销用户` 再比较、再显示。比较用 **`diff`**（jsdiff）的 `diffArrays`，输入是 `Intl.Segmenter("zh", { granularity: "word" })` 切出的词（中文按词、英文按单词，比按字符可读）。
- 删除：确认文案按条目的 `hasReplies` 选两种；成功 toast `评论已删除`，焦点到下一条（没有则到区块标题）。
- 时间：复用 `formatRelative`（多一档「N 天前」，与门户其他地方一致）；`title` 写 `formatDateTime`。首屏服务端给 `now`，客户端水合后每分钟刷新一次。
- 署名规则（第 3 节 + 后端 5.7）：

| 条目 | 头像 | 名字 | 链接 |
|---|---|---|---|
| `anonymous && mine` | 中性 | `匿名作者（你）` | 无 |
| `anonymous` | 中性 | `匿名作者` | 无 |
| `author == null` 或 `author.state == DELETED` | 已注销（`ink-200` 底、无字） | `已注销用户` | 无 |
| `author.username == null` | 默认头像（首字 + 色号） | 昵称 | 无，没有 `@用户名` |
| 其余 | 头像或默认头像 | 昵称 + `@用户名` | `/u/<用户名>` |

  `isOwner` 时名字后加 `作者` 胶囊（匿名也加）。

### 6.5 降级与恢复（D9）

- 服务端首屏取不到（超时 5 秒 `READ_TIMEOUT_MS`、5xx、形状不对）：不放进脱水缓存，客户端挂载后经 Route Handler 再取一次（与 v0.13 文献库一样）；再失败才显示 `讨论暂不可用` + `文献内容不受影响，请稍后再试。` + `重试`（重取查询）。区块标题照常，文献页其余照常。
- **自动恢复**（按 release spec D9）：错误态下，窗口重新获得焦点、页面从后台切回时自动重取（只对处于错误态的讨论查询开 `refetchOnWindowFocus`，正常时不开）；刷新页面也会恢复（`gatewayFetch` 不缓存，`cache()` 只在一次请求内）。不做定时轮询。
- 写操作失败：就地错误，不关编辑器。
- `InteractionUnavailable` 加紧凑版（上下内边距 40px，标题用 `text-lg`），讨论区用它。

## 7. 文献页接线

- `PublicationDetailView` 加 `discussion` 插槽，放在主栏 `{fullText}` 之后。
- `page.tsx`：`searchParams` 解析出 `view`，`discussion={<DiscussionSection subject={{ type: "publication", key: id }} view={view} copy={PAPER_DISCUSSION_COPY} />}`。
- `next` 参数：登录后回到原地址（路径 + 查询串，含 `?comment=`、`?sort=`）；沿用 `safeNextPath`，它丢掉 `#` 片段，不改它（所有登录跳转共用的安全函数）。列表视图回来时停在页顶，单楼视图回来时照常滚到目标；简报 §2.2 第 1 条本来就是「回来后不自动打开编辑器」，接受。
- **会话已失效但 Cookie 还在**：讨论区的读接口登录可选，令牌被网关拒（401）时按访客再取一次（不带令牌），不显示降级；Route Handler 同理。Cookie 由导航栏的当前用户查询照常处理。

## 8. `Avatar` 的改动

- 尺寸加 22、28、32（简报 §3.2：根评论 32 / 移动 28，回复 24 / 移动 22）。响应式尺寸用两个 `Avatar` 加 `max-[880px]:` 切显示会重复图片请求；改为 `size` 收一个桌面档 + 可选 `mobileSize`，用 CSS 切直径与字号。
- 新增形态 `NeutralAvatar({ kind: "anonymous" | "deleted", size })`：`ink-200` 底；匿名中间 `UserRound` 线性图标（`fg-3`），已注销无字。PAP-126 的提醒行也用。
- `AvatarIdentity.username` 改为 `string | null`（`avatarInitial` 只用昵称首字时不需要用户名；核对现有调用方）。

## 9. 测试

### 9.1 单测（Vitest，先写，TDD）

| 对象 | 要点 |
|---|---|
| `MarkdownBody` | 简报 §3.7 对照表逐行：粗体、斜体（两种）、引用、三种列表、行内代码、链接（http(s) 才链、`javascript:` 是文字、`rel`、新窗口）、裸网址省略；**图片、ATX 标题、setext 标题、HTML 标签、表格、围栏代码、缩进代码、嵌套列表各断言原文可见且没有 `img` / `h1` 等元素**；转义显示字面；单换行保留 |
| 提及构造 | `<@42>` 三种渲染；`\<@42>` 是字面 `<@42>`；行内代码里的不是提及；`<@042>`、`<@>` 不是 |
| PMID / DOI | 三种 PMID 写法；`doi:` 前缀、裸 DOI、末尾中英文标点不进链接；链接里、行内代码里的不处理 |
| 计数与 @ 粗算 | 字素（emoji 组合、汉字）、字节、阈值；@ 候选语法边界（邮箱、`你好@chen`、末尾 `-`、全数字、大小写去重、代码与链接里的） |
| `cache.ts` | 4.5 表每一行 |
| 原因码映射 | 4.3 每个 `reason` 与文案；`retryAfterSeconds` 读取 |
| Route Handler / RSC 读 | 参数不合格不请求网关；`toRead` 各分支 |
| 组件 | 署名五种、「作者」、「回复 @X」五种、占位、已编辑按钮、`···` 本人 / 别人、空状态三种身份、单楼高亮与 missing、降级块、编辑器：禁用条件、预览空、计数状态、提交中、各错误提示、放弃确认、`⌘Enter`；联想：触发与关闭规则、键盘、选中替换、6 字收起（不测像素位置） |
| 修改历史 | 增删标记、最初版本不标、提及替换后再比较 |
| `query-client.ts` | 身份变化清掉 `discussion` 前缀 |

### 9.2 Playwright（对 mini，`tests/e2e/discussion.spec.ts`）

`PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 PATRA_MAILPIT_BASE_URL=http://100.103.73.27:8025 pnpm test:e2e tests/e2e/discussion.spec.ts --workers=1`，缺环境变量就跳过。1440×900 与 375×812 两个视口各跑一遍。

- **账号**：每个视口注册 3 个新账号并经 Mailpit 验证邮箱（A、B、C），再注册 1 个不验证的（U）；交错发表，避开每人 10 秒间隔。
- **文献**：每次从 catalog 列表里取一篇（不用固定 ID，避免讨论越积越多）。
- **步骤**（一个 serial describe）：
  1. A 发根评论（正文含粗体、引用、列表、行内代码、链接、`PMID 27940189`、`10.1001/jama.2019.22176`、`![x](https://example.com/x.png)`、`<b>粗</b>`）→ 断言各格式、PMID / DOI 链接、`img` 为 0、`<b>粗</b>` 原文可见。
  2. A 立刻再发一条 → `发得太快了，请隔 10 秒再发。`。
  3. B 回复 A；C 回复 B → C 那条显示 `回复 @<B 的用户名>`，「查看对话」列出三条。
  4. 时间链接 → 单楼视图高亮；`复制链接` 拿到的地址打开后同样高亮。
  5. B 删除自己那条（有 C 的回复）→ 占位 `该评论已删除`，C 的回复还在；C 那条的「回复」署名变成 `回复已删除的评论`。
  6. U 打开文献页 → 编辑器禁用 + 验证邮箱提示。
  7. 访客能读、看到 `登录后参与讨论`。
  8. **编辑与修改历史**：发布 5 分钟内的修改不记版本，所以放在最后：A 在第 1 步那条发布满 5 分钟后编辑 → `已编辑` → 修改历史两个版本、有增删标记。
- 每视口一次约 6 分钟（含等 5 分钟），总计约 12 分钟。
- **D9 不进 Playwright**：停 mini 上的 interaction 容器会影响别的会话，改为手测（交接笔记里写步骤），结果记到收尾。

### 9.3 门禁

`pnpm lint`、`pnpm typecheck`、`pnpm test` 全过；改了 RSC 取数，跑一次 `pnpm build` 查动态渲染信号（记忆里 v0.12 的坑）。

## 10. 给 PAP-125 / PAP-126 的接口（写进 `portal-PAP-124-handoff.md`）

- 经验页回复区：`<DiscussionSection subject={{ type: "post", key: postId }} view={view} copy={POST_REPLIES_COPY} />`。对象所有者相关的显示（「作者」、匿名）后端已在条目上算好，组件只按条目画，PAP-125 不用传任何所有者信息；空状态、占位、区块标题走 `copy`；`subject-gone`（经验已删除）的提示组件已处理。
- 经验正文：`<MarkdownBody variant="reading" source={body} mentions={[]} />`；表单正文：`<MarkdownEditor maxGraphemes={2000} mentions={false} … />`。
- 署名：`CommentByline` 拆出的 `AuthorName` / `AuthorAvatar` 接受同形的作者对象（后端 PAP-122 的作者对象与评论同形），经验页署名直接用。
- 提醒：永久链接 = `/papers/<id>?comment=<评论 id>`、`/posts/<id>?comment=<评论 id>`；`formatRelative`、`NeutralAvatar` 可直接用；身份变化清缓存的写法见 `query-client.ts`。

## 11. 由我定的点

1. Markdown 用 react-markdown + micromark `disable` 扩展（解析层白名单）+ 自写的提及构造；不选 markdown-it：两者都主流，react-markdown 直接出 React 元素、图片能按源位置切回原文。新依赖：`react-markdown`、`remark-breaks`、`mdast-util-find-and-replace`、`diff`（及类型包）。
2. 光标坐标自己量（镜像 div），不引 `textarea-caret`。
3. 排序与「查看全部讨论」走 `history.replaceState` + 客户端查询；永久链接走路由导航（服务端首屏）。
4. 首屏用 `HydrationBoundary`（不用 `initialData`），身份变化整体清 `discussion` 前缀。
5. 降级后的自动恢复：错误态下窗口聚焦 / 切回页面时重取，不轮询（按 release spec D9）。
6. 时间沿用 `formatRelative`（多「N 天前」一档）。
7. 修改历史按词比较（`Intl.Segmenter` 词粒度 + `diffArrays`）。
8. 计数「接近上限」阈值统一为上限的 80%（评论 4,000，PAP-125 的 2,000 字正文是 1,600）。
9. 编辑与修改历史的 e2e 放在最后一步，等满 5 分钟。
10. D9 手测，不进 Playwright。
11. 删除后先本地改、再按单楼接口重取这一楼对账（子回复的「回复已删除的评论」、父评论的 `hasReplies` 由服务端算）。
12. 当前视图以地址为准（`useSearchParams`），永久链接用 `pushState` 在客户端切换；资格从客户端的当前用户读。
13. 登录回跳不带 `#discussion`，不改 `safeNextPath`。
14. 读接口遇到失效令牌时按访客重取，不降级。

## 12. 实施记录（2026-10-28）

### 12.1 与本设计、计划的偏离

- 类型依赖另加 devDependencies：`@types/mdast`、`@types/hast`、`micromark-util-types`、`mdast-util-from-markdown`、`mdast-util-to-hast`、`unified`（版本对齐 react-markdown 已装的）。pnpm 严格模式下传递依赖的类型不能 import。`mdast-util-find-and-replace` 取 3.0.2（3.0.4 发布不足两周）。
- 原文回退用自定义的 `sourceText` 节点（不是 `text`）：否则图片原文里的网址会被自动链接。网址、DOI 的边界另外止于中文字符与全角标点（中文紧贴网址时不吞后面的字）。
- 永久链接（时间链接、「只看这一楼」）在客户端 `pushState`，当前视图以地址为准（`useSearchParams`）；不是第 6.2 节初稿写的「路由导航 + 重挂」。
- `viewOf` 与视图类型放纯模块 `lib/discussion/location.ts`：`server.ts` 经 `use-discussion-location.ts` 引入 `useSearchParams` 时 `next build` 报错。
- `text-metrics` 改名 `limits`：设计系统守护测试把 `text-metrics` 当成颜色类。
- 简报 §3.6 的示例「没有找到「chen-yuh」」有 8 个字，与同节「超过 6 个字自动收起」冲突，按规则执行。
- 终审（opus）后补：写操作成功后把这个对象下别的视图（另一种排序、单楼）与对话、修改历史标为过期（`markDiscussionStale`）；删除时评论已不在按已删除处理；Safari 用 Enter 提交组字（`keyCode 229`）不选联想、不提交；只在没有任何数据时整块降级（翻页、重取失败保留已显示的内容）；切换排序时保留上一份（`keepPreviousData`）；相对时间每分钟刷新（`useNow`）；删除后焦点到下一条评论、取消回复后焦点回到「回复」按钮（`Dialog` 加 `finalFocus`）。
- 2026-10-28 用户验收后改：标题降成同字号的粗体段落（不再按原文显示 `#`）；自己回复自己（被回复的与这条是同一个作者，匿名作者也算）当作补充，不显示「回复 @X」与「查看对话」。e2e 只有一个已验证账号，楼中楼是自己回复自己，回复别人时的「回复 @X」与「查看对话」由单测覆盖（用户定）。
- 2026-10-28 排查「刚发表的根评论从列表消失、退回空状态」（e2e 第二次跑出现一次，之后的 e2e 与带请求记录的探针都没复现）：按代码逐一列出写这份查询缓存的路径（`DiscussionCache` / `HydrationBoundary` 水合、查询自己的请求、各处 `setQueryData`、身份变化清缓存、标为过期、GC），其中两条能把发表前的列表写回去，都已用单测复现并修复（`tests/components/discussion/cache-races.test.tsx`）：
  1. **在路上的请求冲掉写结果**：一次在发表之前发出、之后才回来的列表请求，会用发表前的列表覆盖刚并进缓存的新评论（单测里人为挂起一次重取再发表，复现出列表退回空状态、播报仍是「已发表」，与 e2e 的快照一致）。修复：写结果并进缓存前先 `cancelQueries` 同一个查询（列表、单楼都是）。
  2. **首屏数据再次下发时覆盖**：`HydrationBoundary` 对缓存里已有的查询，在服务端那份时间戳更新时用它覆盖；一次在发表之前开始、之后才到的服务端渲染正是这样。修复：换成 `DiscussionCache`，首屏数据只作初值、不覆盖已有缓存。
  e2e 那次具体是哪条路径触发的没有证据（那次没开 trace），结论是「复现了两条可能路径并修复」，不是「复现了那次失败」。
- 终审的 7 条 Minor 暂不改：编辑没改内容点取消也弹确认；Esc 关掉联想后同一位置重新输入 @ 不再打开；回复框状态跨排序残留；预览里手打的字面 `<@数字>` 显示成 `@已注销用户`；长评论折叠阈值量的是外层行高；联想弹层窗口缩放后不重新定位；列表→单楼→查看全部后历史里有两条相同记录。

### 12.2 验证

- `pnpm lint`、`pnpm typecheck`、`pnpm test`（281 个文件、2,686 条）、`pnpm build` 全过。
- Playwright 对 mini（`tests/e2e/discussion.spec.ts`，1440×900 与 375×812）：4 条全过（11.3 分钟）。第二次跑时桌面一条在回复一步挂住：快照里已发表的根评论从列表里消失、回到了空状态；之后两次重跑都没复现，原因未定位。
- D9（停 interaction 容器）未执行，手测步骤在交接笔记 `portal-PAP-124-handoff.md` 第 6 节。
