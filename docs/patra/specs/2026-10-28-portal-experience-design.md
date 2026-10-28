# 门户期刊投稿经验（PAP-125）工程 spec

> **Issue**：Linear PAP-125（GitHub #302），v0.14 Discussion 前端第二波；与 PAP-126（提醒）并行，依赖 PAP-124（讨论区）。
> **产品定义**：release spec `docs/patra/release-specs/v0.14-discussion.md` 2.1.2、决策 B、E、F、G，Done 判定 D5、D9；第 3 节 K（PubMed 审稿周期已撤出，期刊页只有同行经验汇总）。
> **页面、状态、文案**：设计简报 `patra-portal/docs/design-briefs/v0.14/discussion.md`（§2.1、§2.2、§3.13–§3.17、§3.20–§3.23、§4.3–§4.5、§6），画布第 6 版 <https://claude.ai/artifact/DcyaGqN7xUMiXNbK3exaBN>（`Journal` / `Journal-M` / `ExpForm` / `Post` / `Post-M` / `Profile` / `Profile-M`），离线快照 `docs/patra/design/snapshots/2026-10-27-discussion-hifi.zip`。本 spec 不重抄文案，实现时逐条对照简报的状态 ID。
> **接口**：后端工程 spec `docs/patra/specs/2026-10-27-interaction-post-design.md`；摘要见共享笔记 `interaction-PAP-122-handoff.md` 第 3 节（已部署到 mini，2026-10-28 核对 `GET /patra-interaction/journals/1/posts/summary` 返回 `count: 0, sufficient: false`）。
> **复用**：PAP-124 工程 spec `docs/patra/specs/2026-10-28-portal-discussion-design.md` 与交接笔记 `portal-PAP-124-handoff.md`（讨论区、Markdown 渲染与编辑器、署名、缓存规则）。
> **基线**：`feat/v0.14-discussion-portal` 的 `f3d486c72`（已含 PAP-124）。PAP-126 与本 Issue 并行，共享文件 `query-keys.ts`、`query-client.ts` 只追加。
> **创建日期**：2026-10-28

---

## 1. 要做什么

期刊页加第一个互动区块「投稿经验」：同行经验汇总（首轮用时中位数与 P25–P75、直接拒稿用时、结果分布、录用率，可切「近 3 年 / 全部」，不到 5 条只显示条数）和经验列表；登录且邮箱已验证的人在期刊页原地展开表单写一条经验。每条经验有独立页面 `/posts/{id}`：结构化字段、时间线、正文，下面是复用 PAP-124 的回复区；作者能原地编辑、删除。用户主页加「投稿经验」标签。interaction 出问题时经验区降级为「暂不可用」，期刊页主体照常。

## 2. 范围

**做**：

1. 调用层：zod 响应体、服务端读（RSC）、Route Handler（客户端读）、Server Action（写）、queryKeys、身份变化清缓存。
2. 期刊页经验区：`ExperienceSummary`、`ExperienceList` / `ExperienceRow`、区块标题与「写投稿经验」按钮的三种身份、单独 Suspense 与降级。
3. `ExperienceForm`：新建 / 编辑两用，必填 / 选填、日期校验、一致性联动、匿名选项、服务端错误。
4. 经验页 `/posts/[id]`：署名、时间线、定义表、正文、作者操作（编辑、删除、复制链接）、修改历史、侧栏期刊卡、回复区、已删除 / 404 / 不可用、页面信息。
5. 用户主页：`/u/[username]/experiences` 标签页；`ProfileTabs` 改成按可见标签列表渲染，三个主页页面共用一套判定。
6. 单测 + Playwright（对 mini，1440×900 与 375×812）。
7. 共享笔记 `portal-PAP-125-handoff.md`（经验页的组件与路由，给 PAP-126 的提醒跳转用）。

**不做**：讨论区本身（PAP-124）、提醒与铃铛（PAP-126）、草稿保存（简报 §2.2）、经验数 / 回复数等任何计数之外的数字（spec 第 3 节 A）、经验的 DOI 核实（第 3 节 J）。

## 3. 简报与后端不一致的地方（按后端执行）

| 处 | 简报 | 后端（已实现） | 门户做法 |
|---|---|---|---|
| 文章类型 | §3.15 画板示意 5 种 | 6 种：原创研究、综述、系统综述 / Meta 分析、病例报告、短篇通讯、其他 | 按后端 6 种 |
| 一致性规则 | §4.3 示意「直接拒稿最终只能是拒稿或撤稿」 | 直接拒稿、审后拒稿 → 最终只能是拒稿；首轮录用 → 只能是录用；另有「直接拒稿不填审稿轮数与审稿质量」「审后拒稿、首轮录用的审稿轮数只能是 1」 | 表单做成联动（第 6.3 节），不靠提交后报错；文案按后端规则重写 |
| 审稿轮数范围 | 「1–10，以工程 spec 为准」 | 1–10 | 1–10 |
| 主页对访客不可见的作者 | §3.20 封禁用户「`@用户名` 照常」 | `profileVisible=false` 时 `username`、`avatarUrl` 为 null | 同 PAP-124：只有昵称、默认头像、不链接（`authorView` 已处理） |
| 正文字节上限 | 未定 | ≤ 2,000 字素且 ≤ 8,000 字节 | 编辑器同时卡两者（`limits={{ graphemes: 2000, bytes: 8000 }}`） |
| 经验区恢复 | §3.21「要用户点重试或刷新」 | release spec D9「容器恢复后自动恢复」 | 同 PAP-124：错误态下切回页面自动重取，另有「重试」 |

## 4. 接口与调用层

### 4.1 后端接口（形状以 PAP-122 交接第 3 节为准）

| 用途 | 后端（前缀 `/patra-interaction`） | 门户怎么调 |
|---|---|---|
| 期刊的经验列表一页 | `GET /journals/{journalId}/posts?cursor=&limit=10` | 首屏：RSC；「加载更多」：Route Handler `GET /api/journals/[id]/posts?cursor=` |
| 期刊汇总 | `GET /journals/{journalId}/posts/summary?window=3y\|all` | 首屏 `3y`：RSC；切到 `all` 与写后重取：Route Handler `GET /api/journals/[id]/posts/summary?window=` |
| 经验详情 | `GET /posts/{id}` | RSC（`cache()`，元数据与页面共用） |
| 修改历史 | `GET /posts/{id}/revisions` | Route Handler `GET /api/posts/[id]/revisions` |
| 用户的经验 | `GET /users/{userId}/posts?cursor=&limit=` | 首屏：RSC（`limit=20`）；「关于」页判定：RSC（`limit=1`）；「加载更多」：Route Handler `GET /api/users/[id]/posts?cursor=` |
| 写 | `POST /journals/{journalId}/posts` | Server Action `publishExperience` |
| 改 | `PUT /posts/{id}` | Server Action `editExperience` |
| 删 | `DELETE /posts/{id}` | Server Action `deleteExperience` |

读接口都是登录可选：有会话就带（拿 `mine`、本人的匿名经验、作者主页可见性），令牌被网关拒（401）时按访客重取一次、不降级——与 PAP-124 的 `readPublic` 相同。期刊卡片（ISSN、OA）用现有的 `fetchVenueDetail`（第 8.4 节）。

### 4.2 文件落点

| 文件 | 内容 |
|---|---|
| `src/lib/interaction/experience-schema.ts` | zod：`Experience`、`PostDetail`（`deleted` 为真时 `experience` 为 null）、`Summary`、`ExperiencePage`、`Revision`、`UserExperience`、`UserExperiencePage`；枚举 `FirstDecision`、`FinalOutcome`、`ArticleType`、`SummaryWindow`。作者对象直接用 PAP-124 的 `cardSchema`（同形）。不加 `server-only`（客户端组件要类型） |
| `src/lib/interaction/experience.ts`（`server-only`） | `fetchJournalExperiences(journalId, cursor?)`、`fetchExperienceSummary(journalId, window)`、`fetchPost(id)`（`cache()`）、`fetchPostRevisions(id)`、`fetchUserExperiences(userId, { cursor?, limit })`；都返回 `Read<T>`，超时 `READ_TIMEOUT_MS`（5 秒）；参数不合格（ID 不是 `isEntityId`、`window` 不在枚举里）直接 `not-found`，不请求网关 |
| `src/lib/interaction/experience-actions.ts`（`"use server"`） | 三个写操作，结果按 `reason` 区分（4.3），不带文案；用现有 `mutate()` |
| `src/app/api/journals/[id]/posts/route.ts`、`…/posts/summary/route.ts`、`src/app/api/posts/[id]/revisions/route.ts`、`src/app/api/users/[id]/posts/route.ts` | Route Handler，照 PAP-124 的写法（`readReply()` 回写；客户端 `fetch` 的地址写成以 `/` 开头、与 `fetch(` 同一行的模板字面量，守 `tests/lib/gateway-boundary.test.ts`） |
| `src/lib/experience/labels.ts` | 枚举 → 中文（首轮结果、最终结果、文章类型、APC、转投） |
| `src/lib/experience/format.ts` | 时长（`42 天`、`约 6 周`）、投稿月份（`2026 年 3 月投稿`）、日期（`2026 年 3 月 2 日`）、结果行（`首轮 42 天 · 大修  →  录用`）、百分比、汇总口径行；纯函数 |
| `src/lib/experience/rules.ts` | 一致性规则（第 6.3 节），表单联动与 zod 校验共用；北京时间的「今天」 |
| `src/lib/experience/form-schema.ts` | 表单的 zod schema（字符串形态的表单值 → 请求体），含 `toRequest()`：选填没填发 `null` |
| `src/lib/experience/messages.ts` | 字段错误码、写操作 `reason` → 文案（简报 §4.3 原文；一致性文案按第 3 节重写） |
| `src/lib/experience/queries.ts`（客户端） | `useExperienceSummary`、`useJournalExperiences`（`useInfiniteQuery`）、`useUserExperiences`、`usePostRevisions`；写后并入缓存的函数（第 5.2 节） |
| `src/lib/experience/server.ts`（`server-only`） | `prefetchJournalExperiences(journalId)`：汇总（`3y`）与列表第一页并行取，取到的放临时 `QueryClient` 脱水（照 `lib/discussion/server.ts`） |
| `src/lib/markdown/plain.ts` | Markdown 原文 → 纯文本摘录（列表两行摘录、页面 `description` 用）：去掉强调、引用、列表、链接标记，保留链接文字，空白折叠 |
| `src/lib/query-keys.ts` | 新增 `experience` 前缀一节（第 5.1 节），只追加 |
| `src/lib/query-client.ts` | 身份变化订阅里追加一行 `removeQueries({ queryKey: queryKeys.experience })`，只追加 |
| `src/lib/identity/problem.ts` | `Problem` 追加 `existingPostId: string \| null`（409 `DUPLICATE_SUBMISSION` 的响应体字段；字符串才取），只追加 |

**对 PAP-124 文件的改动只有一处**：`src/lib/interaction/discussion.ts` 里的 `readPublic` 挪到 `src/lib/interaction/read.ts` 并导出（讨论与经验共用「登录可选 + 令牌被拒按访客重取」），`discussion.ts` 改为引用它。`DiscussionCache` 的行为本来就是通用的（只放缓存里还没有数据的查询），直接复用，不改名。

### 4.3 写操作的结果

```ts
type ExperienceFailure =
  | { ok: false; reason: "invalid-fields"; fieldErrors: FieldError[] }        // 422 errors[]
  | { ok: false; reason: "duplicate"; existingPostId: string | null }          // 409 DUPLICATE_SUBMISSION
  | { ok: false; reason: "daily-limit"; retryAfterSeconds: number | null }     // 429 DAILY_LIMIT_REACHED
  | { ok: false; reason: "email-unverified" | "account-restricted" }           // 409
  | { ok: false; reason: "journal-gone" }                                      // 写：404（期刊不存在 / 已失效）
  | { ok: false; reason: "post-gone" }                                         // 改：404（经验不存在 / 已删除）
  | { ok: false; reason: "forbidden" }                                         // 403（不是作者）
  | CommonFailure;                                                             // signed-out / unavailable（含 503 DEP_UNAVAILABLE）
```

- `publishExperience(journalId, request)` → `{ ok: true, detail }`；`editExperience(postId, request)` → `{ ok: true, detail }`；`deleteExperience(postId)` → `{ ok: true }`（幂等，删已删的也是成功）。
- Server Action 入口先用同一份请求体 zod schema 校验参数（ID 格式、类型），不合格回 `unavailable`（同 `library-actions.ts`）。
- 文案（`messages.ts`）：`duplicate` → `你已经写过 <投稿日期> 投这本期刊的经验，同一天的投稿只能写一条。` + `查看那条经验`（→ `/posts/<existingPostId>`，没有 ID 时不出链接）；`daily-limit` → `今天已经发布了 10 条投稿经验，明天再来吧。`；`account-restricted` → `账号状态异常，暂时不能发布。`（编辑：`…暂时不能保存。`）；`email-unverified` → `验证邮箱后才能写投稿经验。` + `重新发送验证邮件`；`journal-gone` → `这本期刊已不在 Patra 的索引里，不能再写投稿经验。`；`post-gone` → `这条投稿经验已被删除。`；`unavailable` / `forbidden` → `没有发布成功，请稍后再试。`（编辑：`没有保存成功，请稍后再试。`；删除：`没有删除成功，请稍后再试`）。
- `signed-out`：表单有内容时原地确认 `登录已过期，去重新登录？已写的内容会丢失。` + `去登录` / `留在这里`（同 PAP-124 的编辑器）；没内容直接去 `/login?next=<当前路径>`。
- `invalid-fields`：逐条 `setError` 到对应字段（字段码 → 文案见 6.4）；认不出的字段码按 `unavailable` 的提示条处理。

## 5. 缓存

### 5.1 queryKeys

```ts
experience: ["experience"] as const,                                         // 公共前缀：身份变化时整体清掉
experienceSummary: (journalId, window) => ["experience", "journal", journalId, "summary", window] as const,
experienceList: (journalId) => ["experience", "journal", journalId, "list"] as const,
experienceJournal: (journalId) => ["experience", "journal", journalId] as const,   // 汇总 + 列表，写后整体处理
experienceUser: (userId) => ["experience", "user", userId] as const,
experienceRevisions: (postId) => ["experience", "revisions", postId] as const,
```

都在 `experience` 前缀下：`mine`、本人的匿名经验按访客不同，身份一变整体清掉（`query-client.ts` 已有订阅里加一行）。

### 5.2 两条缓存规则（PAP-124 交接第 5 节）

1. **首屏数据只作初值**：期刊页经验区的首屏（汇总 `3y` + 列表第一页）由 `DiscussionCache` 放进缓存，只放缓存里还没有数据的查询；不用 `HydrationBoundary`、不用 `initialData`。经验页的详情不进查询缓存（见 8.2），没有这个问题。
2. **写后先取消再动缓存**：
   - 发布（期刊页）：`cancelQueries(experienceJournal(id))` → 汇总 `invalidateQueries`（两个窗口都重取）、列表 `resetQueries`（无限列表回到只有第一页再取，不把已加载的几页逐页重取）。不在本地插入新条目：列表按投稿日期排，新经验不一定在第一页，重取最简单也最准。
   - 编辑、删除（经验页）：`cancelQueries` 后 `invalidateQueries` 这本期刊的 `experienceJournal` 与作者的 `experienceUser`、这条的 `experienceRevisions`（下次去期刊页、主页、开修改历史时重取）。

### 5.3 汇总变化的高亮

简报 §3.13「变化的数字高亮 2 秒」：`ExperienceSummary` 记住上一份成功的汇总（同一窗口），新数据到达时逐项比较（条数、中位数、P25、P75、直接拒稿、录用率、四个结果数），变了的那几项加 `clay-50` 底，2 秒淡出（`motion-safe:`，减少动态效果时只换底色不做过渡）。只在写后重取时比较（切窗口不高亮）。

## 6. 期刊页经验区

### 6.1 组件

放 `src/components/experience/`。

| 组件 | 类型 | 说明 |
|---|---|---|
| `ExperienceSection` | RSC | 外壳：`<section id="experiences" aria-labelledby="experiences-title">` + 标题；`Suspense`（fallback 是 `exp.summary.loading` 的两栏骨架，标题照常、按钮不出）包住 `Experiences` |
| `Experiences` | RSC（async） | 并行取首屏（`prefetchJournalExperiences`）与会话（`lookupSession`），`DiscussionCache` 包住 `ExperienceArea` |
| `ExperienceArea` | 客户端 | 区块标题行（「写投稿经验」按钮三种身份，`useViewer`）、表单展开 / 收起、汇总、列表；任何一个查询在没有数据时失败 → 整块 `exp.summary.unavailable`（按钮隐藏、列表不渲染） |
| `ExperienceSummary` | 客户端 | 简报 §3.13 六种状态 + `近 3 年 / 全部` 分段控件（`CitationFormatTabs` 的胶囊分段样式，`role="radiogroup"`）；切换只换汇总查询的 `window` |
| `ExperienceList` / `ExperienceRow` | 客户端 | 简报 §3.14；行与主页标签共用（`variant: "journal" \| "profile"`） |
| `ExperienceForm` | 客户端 | 第 6.3 节；期刊页新建、经验页编辑两用 |

### 6.2 位置与身份

- `JournalDetailView` 加 `experiences` 插槽，放在「影响力速览」之后、`JournalPositioning` 之前；期刊页 `page.tsx` 传 `<ExperienceSection journalId={id} journalTitle={venue.title} />`。Suspense 里读会话会让期刊页变成动态渲染（文献页已经这样），实现后跑一次 `pnpm build` 查动态渲染信号。
- 访客身份用 PAP-124 的 `useViewer(seed)`（服务端会话作初值，水合后以当前用户查询为准）：
  - 匿名：`登录后写投稿经验`（次按钮），链 `/login?next=/journals/<id>`；空状态里的 `写第一条投稿经验` 同样去登录。
  - 邮箱未验证：按钮禁用 + 下一行 `amber-700` `验证邮箱后才能写投稿经验` + `ResendVerificationButton`。
  - 已验证：`写投稿经验`（主按钮小号，`PenLine`）。冷静期、封禁只靠服务端 409（同 PAP-124）。
- 登录回跳丢掉 `#experiences`：`safeNextPath` 的既有行为，接受（同 PAP-124 第 7 节）。

### 6.3 表单 `ExperienceForm`

**形态**照简报 §3.15：期刊页上点 `写投稿经验`，表单出现在区块标题下、汇总卡上方，焦点进投稿日期；桌面两列排短字段，≤ 880px 单列。RHF + zod（`zodResolver`），表单值全是字符串形态（日期 `yyyy-MM-dd`、单选为枚举字符串或空串），提交时 `toRequest()` 转成请求体。

**字段控件**：

| 字段 | 控件 | 备注 |
|---|---|---|
| 投稿日期 *、首轮决定日期 *、最终决定日期 | 原生 `type="date"`（`.pt-box` 样式），`min=1990-01-01`、`max=今天` | 「今天」按北京时间（与后端一致） |
| 首轮结果 *、最终结果 * | 胶囊单选组（真正的 `radio`，高 36px，≤ 880px 44px） | |
| 文章类型 * | `select` 原语，6 种 | |
| 审稿轮数 | 数字输入，1–10 | |
| 审稿质量、编辑沟通 | 1–5 方块单选，两端注 `1 很差 · 5 很好` | 选填单选可再点一次取消；另有读屏可见的 `清除选择` 按钮 |
| 是否付了 APC、是否转投过 | 胶囊单选（付了 / 没付；是 / 否） | 同上，可取消；转投的提示 `投本刊之前投过别的期刊，被拒后改投过来` |
| 经历 | PAP-124 `MarkdownEditor`，`mentions={false}`、`limits={{ graphemes: 2000, bytes: 8000 }}`、`limitLabel="正文"` | `useEditorMetrics(...).blocked` 为真时禁用提交 |
| 匿名发布 | 复选框 + 两行说明 + `amber-700` 的 `发布后不能更改匿名设置。` | 编辑模式换成只读一行（简报 §3.15） |

**一致性联动**（`rules.ts`，与后端规则一一对应）：

| 首轮结果 | 最终结果 | 最终决定日期 | 审稿轮数 | 审稿质量 |
|---|---|---|---|---|
| 直接拒稿 | 自动选「拒稿」，其余选项禁用 | 禁用并清空 | 禁用并清空 | 禁用并清空 |
| 审后拒稿 | 自动选「拒稿」，其余选项禁用 | 禁用并清空 | 固定为 1（禁用） | 可填 |
| 首轮录用 | 自动选「录用」，其余选项禁用 | 禁用并清空 | 固定为 1（禁用） | 可填 |
| 大修 / 小修 | 四个都可选 | 最终结果为「进行中」时禁用并清空，其余可填 | 1–10 | 可填 |

- 禁用的字段下面一行 `fg-3` 小字说明原因（`首轮已是最终结果，不用填最终决定日期`、`稿件还在进行中，不用填最终决定日期`（简报原文）、`没有送审，不用填审稿轮数和审稿质量`、`首轮即结束，审稿只有 1 轮`）。
- 首轮结果从终局改回大修 / 小修时，解开最终结果、清掉自动选的值（让人重新选），审稿轮数清空。
- 后端仍按规则校验，联动只是让人填不出违规的组合；`INCONSISTENT` / `NOT_APPLICABLE` 若仍回来（比如两个标签页），显示在对应字段下。

**校验时机与文案**：字段失焦校验该字段，提交时全部校验，焦点去第一个出错的字段；错误在字段下方。必填缺失、日期不晚于今天、首轮不早于投稿、最终不早于首轮、审稿轮数范围、正文超长的文案照简报 §4.3；`TOO_EARLY` → `日期不能早于 1990 年`。

**算出的用时**：两个日期都有效时，「投稿与审稿」组末尾 `首轮用时 42 天 · 投稿到最终决定 91 天`（`fg-3`，随填随更新；没有最终日期时只写首轮）。

**提交**：`发布中…` / `保存中…`，禁用整个表单（`fieldset disabled`）。成功：

- 新建：表单收起、按 5.2 重取、汇总高亮，toast `投稿经验已发布` + `查看`（→ `/posts/<新 id>`），焦点回 `写投稿经验` 按钮。
- 编辑：回到经验页展示态（8.2），`router.refresh()` 拿新详情。

失败：表单底部 `FormAlert`，内容保留；`duplicate` 的焦点回投稿日期。

**取消**：没填任何东西直接收起；填了东西时确认对话框 `放弃这条投稿经验？`（简报 §3.15）。编辑模式比较的是与原值是否不同。

### 6.4 字段错误码 → 文案

`REQUIRED`（按字段：`请填写投稿日期` / `请填写首轮决定日期` / `请选择首轮结果` / `请选择最终结果` / `请选择文章类型`）、`FUTURE` → `日期不能晚于今天`、`TOO_EARLY` → `日期不能早于 1990 年`、`BEFORE_SUBMISSION` → `首轮决定日期不能早于投稿日期`、`BEFORE_FIRST_DECISION` → `最终决定日期不能早于首轮决定日期`、`NOT_APPLICABLE` / `INCONSISTENT` → 联动说明的同一句、`OUT_OF_RANGE` → `审稿轮数填 1 到 10 之间的整数`（评分：`评分是 1 到 5`）、`TOO_LONG` → `正文最多 2,000 字`、`INVALID` / `INVALID_CHARACTER` → `这一项的内容不对，请重新填写`、`DUPLICATE`（`submittedOn`）→ 不在字段下重复，走底部提示条。

### 6.5 汇总与列表的细节

- 汇总的六种状态、数字写法、口径行照简报 §3.13、§3.22；`window-empty` 的判定是 `count == 0 && allCount > 0`（后端约定），`empty` 是 `allCount == 0`（不出切换控件）。`近 3 年 / 全部` 不改地址。
- 录用率：`acceptance` 为 null（已完结 0 条）时写 `—`，下一行不写「已完结的 N 条」。`firstRound` / `deskReject` 为 null 时对应格写 `—`。
- 列表每页 10 条；`加载更多投稿经验`（整宽次按钮）；行的写法照简报 §3.14（结果行在首轮即最终时不写箭头；`进行中` 写 `→ 进行中`；没填的选填项不出现；正文两行摘录用 `plain.ts`，`line-clamp-2`）；作者行用 PAP-124 的 `AuthorAvatar` / `AuthorName`（`authorView({ author, anonymous, mine })`），本人加 `· 你写的`，本人的匿名经验另加 `匿名` 胶囊；整行链 `/posts/<id>`（作者名在行内不是链接，避免嵌套链接；作者主页链接在经验页上）。
- 空列表（`exp.list.empty`）与 `exp.summary.empty` 同时出现。

### 6.6 降级与恢复（D9）

- 服务端首屏取不到（超时 5 秒、5xx、形状不对）：不放进缓存，客户端挂载后经 Route Handler 再取一次；再失败才显示 `exp.summary.unavailable`（`同行经验暂不可用` + `重试`），区块标题照常、`写投稿经验` 隐藏、列表不渲染。
- 自动恢复同 PAP-124：只对处于错误态的经验查询开 `refetchOnWindowFocus: "always"`；`重试` 重取两个查询。
- 已有数据时翻页 / 重取失败：保留已显示的内容，「加载更多」下面就地提示 `没有加载成功，请稍后再试`。

## 7. 用户主页「投稿经验」标签

### 7.1 标签判定（`src/lib/profile/tabs.ts`，`server-only`）

`visibleProfileTabs(profile, self, current)` → `("about" | "collections" | "experiences")[]`：

- `about` 总在。
- 当前所在的标签总在。
- 其余：本人视角总在；别人视角按各自条件——`collections`：至少一个对他可见的公开收藏夹（现有条件）；`experiences`：`fetchUserExperiences(userId, { limit: 1 })` 返回非空。两次读并行；失败或空都不出。
- 结果只有 `about` 时不渲染标签栏。

`ProfileTabs` 改为接收 `tabs` 与 `current`，按列表渲染（顺序固定：关于、收藏夹、投稿经验）；`ProfileTabsGate`（「关于」页，Suspense 里）改为调用 `visibleProfileTabs(…, "about")`；`/collections` 页与新的 `/experiences` 页也用它（不再无条件渲染全部标签）。

### 7.2 `/u/[username]/experiences`

- 页面结构照 `/u/[username]/collections`：取主页与会话、旧名 307 到 `/u/<新名>/experiences`、不存在 404；`generateMetadata` 标题 `陈宇 (@chen-yu) 的投稿经验 · Patra`，`indexable` 为假时 `noindex`，`canonical` `/u/<用户名>/experiences`。
- 内容 `ProfileExperiences`（客户端，首屏服务端取第一页 `limit=20` 作初值，「加载更多」走 Route Handler）：行用 `ExperienceRow variant="profile"`（第一行是期刊名，取 `journal.title` 快照，`journal.abbreviation` 不用；第二行 `2026 年 3 月投稿 · 原创研究 · 首轮 42 天 · 大修 → 录用`；第三行 `发布于 6 月 3 日`）。
- 本人视角：列表上方说明行；匿名条目末尾 `匿名 · 仅你可见` 胶囊。空 / 失败照简报 §3.17、§4.5。
- 这页的数据不需要身份变化以外的写后同步：经验页编辑、删除时已经失效 `experienceUser`。

## 8. 经验页 `/posts/[id]`

### 8.1 页面与读状态

`src/app/posts/[id]/page.tsx`，`searchParams` 解析回复区视图（`parseDiscussionView`）。`fetchPost(id)`（`cache()`）与 `generateMetadata` 共用：

| `fetchPost` 结果 | 页面 |
|---|---|
| `not-found` | `notFound()` → `src/app/posts/[id]/not-found.tsx`：`NotFoundState kind="post"`（新增：`没有找到这条投稿经验` / `地址可能有误。` / `返回首页` · `浏览期刊 →`） |
| `ok` 且 `deleted` | `post.deleted`：面包屑、眉题、期刊名、`该经验已删除` 与说明、`看这本期刊的其他投稿经验 →`；**不挂回复区**；`robots: noindex` |
| `ok` | 正常页（8.2），下面挂 `<DiscussionSection subject={{ type: "post", key: id }} view={view} copy={POST_REPLIES_COPY} basePath={"/posts/" + id} />` |
| `unavailable` / `signed-out`（不会出现：登录可选读已按访客重取） | `post.unavailable`：导航、面包屑照常，主栏 `InteractionUnavailable`（`投稿经验暂时打不开` + `请稍后再试。` + `重试`），**不是 404**；`重试` 是 `router.refresh()` |

面包屑：`Patra / 期刊 / <期刊缩写或全称>（→ /journals/<key>#experiences）/ 投稿经验`。

**期刊已失效**（`journal.state != "ACTIVE"`，或 `fetchVenueDetail` 返回 404）：期刊页已不存在，经验页上所有指向它的链接（面包屑那一节、标题、侧栏两个链接、已删除态的「看这本期刊的其他投稿经验」）都改成纯文字；删除成功后跳 `/journals`（toast 照常）。

**404 状态码**：照文献页、期刊页的做法——`/posts/[id]` 有 `loading.tsx`（站内跳转先出骨架），`src/proxy.ts` 的详情页预检加 `posts`（地址栏直接打开时 `HEAD /patra-interaction/posts/<id>`，404 改写到新增的 `/missing/post`，由那里的 `notFound()` 回真正的 404；已删除是 200，不拦）。`DETAIL` 表从「catalog 端点名」改成「完整网关路径」以容纳 interaction。

### 8.2 正常页的组件

| 组件 | 类型 | 说明 |
|---|---|---|
| `PostView` | 客户端 | 主栏：眉题、期刊名（衬线 display-3，链期刊页）、署名行、操作；展示态 / 编辑态切换（编辑态是 `ExperienceForm mode="edit"`，替换时间线、定义表与正文）。详情来自 RSC 的 props，编辑成功后 `router.refresh()` 取新详情，不进查询缓存 |
| `PostByline` | 客户端 | `authorView` + `AuthorAvatar`（28）/ `AuthorName`；`发布于 2026 年 6 月 3 日`；`已编辑`（按钮，开修改历史）；本人的匿名经验另有 `匿名发布 · 只有你知道是你写的` 提示条（`slate-50`） |
| `ExperienceTimeline` | RSC 可用 | `<ol>`，三种形态（简报 §3.16）：进行中最后节点空心 + 虚线；首轮即最终只有两个节点；有最终结果没填日期写 `未填日期`、连线不标用时。用时写进节点文字（不只在线上）。≤ 640px 竖排 |
| `ExperienceFacts` | RSC 可用 | 两列定义表（≤ 880px 单列），只列填了的选填项；文章类型总在 |
| 正文 | — | `<MarkdownBody source={body} mentions={[]} variant="reading" />`，没有正文不渲染 |
| `PostActions` | 客户端 | 本人：`编辑`（次按钮小号）+ `···`（`复制链接`、分隔、`删除`）；别人：`···`（`复制链接`）。`复制链接` 用 `useCopy`，复制 `publicUrl("/posts/<id>")`，toast `已复制链接` |
| `DeletePostDialog` | 客户端 | 简报 §3.16 文案；成功 → 5.2 的失效 → `router.push("/journals/<key>#experiences")` + toast `投稿经验已删除`；失败就地 `没有删除成功，请稍后再试` |
| `PostRevisionDialog` | 客户端 | 第 8.3 节 |
| `PostJournalCard` | RSC | 侧栏：期刊名、ISSN、OA 标记 + `期刊详情 →` + `这本期刊的其他投稿经验 →`；≤ 980px 移到正文与回复区之间 |

布局与文献详情同一网格（主栏 + 340px 侧栏），断点照简报 §6。

### 8.3 修改历史

`已编辑` 打开对话框（现有 `Dialog`，≤ 880px 底部抽屉），`usePostRevisions(id)` 取 `GET /api/posts/[id]/revisions`。每一版（当前版本在前，后面按时间倒序）：时间（`formatDateTime`）+ 与上一版（时间上更早的那版）相比的改动：

- 结构化字段：逐项比较 11 个字段，变了的列成 `字段：旧值 → 新值`（值用 `labels.ts` / `format.ts` 的写法，没填写 `未填`）；
- 正文：变了时用 PAP-124 的 `revision-diff`（按词标增删），显示原文（`pre-wrap`）；
- 最初版本只列它的值，不标改动。
- 经验已删除（404 `SUBJECT_UNAVAILABLE`）时对话框里写 `这条投稿经验已被删除。`；失败写 `修改历史暂时打不开` + `重试`。

### 8.4 期刊卡片

经验页只有一本期刊：用现有 `fetchVenueDetail(journal.key)`（已有 `cache()`）取 ISSN（`issnL`）、`isOpenAccess`；失败（`try/catch` + `unstable_rethrow`）或 404（期刊已失效）时退回 `PostDetail.journal` 的快照（只显示刊名，不出 ISSN、OA，链接照常）。

### 8.5 页面信息

| 状态 | `title` | `description` | 其他 |
|---|---|---|---|
| 正常 | `<缩写或全称> 投稿经验 · 2026 年 3 月投稿 · Patra` | `首轮 42 天 · 大修 → 录用。` + 正文纯文本前 100 字 | `canonical` `/posts/<id>`；**不出现作者**（匿名、实名都不写，简报只要求匿名，统一不写更简单） |
| 已删除 | `该经验已删除 · Patra` | — | `noindex` |
| 不存在 | `not-found.tsx` 的元数据 | — | |
| 不可用 | 不设（沿用站点默认） | — | |

### 8.6 匿名在经验页的表现（D5、Linear AC 3）

- 别人看匿名经验：署名中性头像 + `匿名作者`，没有 `@用户名`、没有任何 `/u/` 链接；回复区里作者的回复由后端标为匿名，组件已按条目画（PAP-124）。
- 本人看：`匿名作者（你）` + 提示条；编辑模式只读一行 `这条经验以匿名发布，不能更改。`。
- 页面上任何地方（署名、元数据、侧栏、复制的链接）不出现作者的用户名或 ID。

## 9. 测试

### 9.1 单测（Vitest，TDD）

| 对象 | 要点 |
|---|---|
| `format.ts` | 时长 `<28` 天只写天、`≥28` 补 `约 N 周`（四舍五入）；投稿月份、日期、结果行三种（有箭头 / 首轮即最终 / 进行中）；百分比取整；口径行两种窗口 |
| `rules.ts` | 第 6.3 节联动表每一行；首轮从终局改回时的清空；北京时间「今天」（用固定时钟） |
| `form-schema.ts` | 必填缺失、日期先后、未来日期、1990 年前、轮数与评分范围、正文超长；`toRequest()`：选填没填是 `null`（不是 `false` / `0`）、`anonymous` 只在新建时带 |
| `plain.ts` | 强调、引用、列表、链接、行内代码去标记；空白折叠；截断 |
| `experience-actions.ts` | 每种 `reason` 的归类（422 字段、409 三种、429、404 两种、403、503、401 清 Cookie）；`existingPostId` 透传；参数不合格不请求 |
| `experience.ts` / Route Handler | 参数不合格不请求网关；401 按访客重取；`toRead` 各分支 |
| `problem.ts` | `existingPostId` 只在字符串时取 |
| `ExperienceSummary` | `full` / `few` / `window-empty`（`看全部 12 条` 切窗口）/ `empty` / `loading` / `unavailable`（重试）；切窗口换查询；写后变化项高亮、切窗口不高亮 |
| `ExperienceRow` | 匿名（别人看无链接）、本人（`你写的`）、本人匿名（`匿名` 胶囊）、已注销、进行中、直接拒稿、只有必填、主页变体 |
| `ExperienceArea` | 按钮三种身份；表单展开 / 收起与焦点；发布成功后收起、toast、两个查询被取消后重取（断言 `cancelQueries` 先于重取）；降级时按钮隐藏、列表不渲染 |
| `ExperienceForm` | 联动表每一行；校验时机；服务端字段错误落位；`duplicate` 提示与链接、焦点回投稿日期；每日上限、资格、不可用、会话失效确认；放弃确认；编辑模式的只读匿名行与「保存修改」 |
| `PostView` 及部件 | 时间线三种形态（`<ol>`、用时在文字里）；定义表只列填了的；本人 / 别人的操作；匿名两种视角；删除确认、成功跳转、失败；修改历史的字段改动与正文增删 |
| 经验页 `page.tsx` | 四种读状态（含已删除不挂回复区）；元数据（不含作者、已删除 noindex） |
| `visibleProfileTabs` / `ProfileTabs` | 本人三项；别人按两个条件组合四种；失败不出；只有「关于」不渲染；当前标签总在 |
| `query-client.ts` | 身份变化清掉 `experience` 前缀 |

### 9.2 Playwright（对 mini，`tests/e2e/experience.spec.ts`）

`set -a; . ~/.patra/secrets/e2e.env; set +a; PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm test:e2e tests/e2e/experience.spec.ts --workers=1`，缺环境变量就跳过。

- **账号**：作者 V = mini 上的已验证测试账号 B（`PATRA_E2E_VERIFIED_B_*`，2026-10-28 用户定，不占真实账号的额度）；U = 每次新注册的未验证账号（`freshAccount()`，能登录、不能发表）；访客 = 未登录的 context。
- **期刊**：从 catalog 期刊列表随机取，直到汇总 `allCount == 0`（保证统计只来自本次写的经验）。
- **写入预算**：每次运行 V 写 5 条（同一期刊、5 个不同投稿日期，第 5 条匿名），用例最后全部删除。**删除不退当日额度（每人每天 10 条，后端 spec「删除不退还当天额度」）**：同一天最多完整跑两次；中途失败的那次也算。开发期靠单测与本地页面手看；手看时 V 写的经验与 e2e 共用这 10 条。e2e 放在最后写，跑的那天不手写经验，留一次失败重跑的余量。
- **步骤**（一个 serial describe，桌面 1440×900 先跑写入与断言，再用 375×812 的 context 看同一批数据，最后清理）：
  1. 访客打开期刊页：`登录后写投稿经验`、`exp.summary.empty`、`exp.list.empty`。
  2. U 登录：按钮禁用 + 验证邮箱提示。
  3. V 登录，展开表单：空提交 → 五个必填文案；首轮决定日期早于投稿日期 → 文案；首轮选「直接拒稿」→ 最终结果锁定为拒稿、最终决定日期与审稿轮数禁用。
  4. V 发布第 1 条（大修 → 录用，填全部选填与正文）→ toast `投稿经验已发布`、汇总 `1 条`（`few`）、列表出现该行。
  5. 用同一投稿日期再发 → 底部 `你已经写过…` + `查看那条经验` 链到第 1 条。
  6. V 再发 3 条（含直接拒稿、审后拒稿、进行中）→ 仍是 `few`（4 条）；发第 5 条（匿名）→ `full`：首轮用时中位数、P25–P75、直接拒稿用时、录用率与 `已完结的 N 条` 等于测试按选定日期用线性插值算出的值（测试里写同一个算法）。
  7. 匿名，访客视角：期刊页那一行与经验页署名是 `匿名作者`、没有 `/u/` 链接；直接请求网关 `GET /patra-interaction/posts/<匿名 id>` 与期刊列表，响应体里不含 V 的用户 ID（从 V 的 `/auth/me` 取）。
  8. 匿名的回复：V 在匿名经验下回复 → V 看到 `匿名作者（你）` + `作者`；访客看到 `匿名作者` + `作者`、没有 `/u/` 链接。
  9. @ 联想：U 用自己的会话请求 `GET /api/discussions/post/<匿名 id>/mention-suggestions?q=` → 结果里没有 V；对照：V 在第 1 条（实名）下回复后，U 请求同一接口 → 有 V（证明参与者逻辑在起作用，不是空结果碰巧通过）。
  10. 主页：访客打开 `/u/<V>/experiences` → 4 条实名、没有匿名那条；V 打开 → 5 条，匿名那条带 `匿名 · 仅你可见`。
  11. 经验页：V 编辑第 1 条（改首轮结果 / 日期）→ 展示态更新；期刊页汇总随之变化。
  12. 375×812：期刊页汇总上下排、表单单列（展开再取消）、经验页时间线竖排、侧栏在正文与回复之间、主页标签可用。
  13. 清理：V 在经验页删除第 1 条（走 UI，断言跳回期刊页 + toast + 汇总变化）；其余 4 条在 `afterAll` 里经门户的删除操作清掉（无论前面是否失败）。V 写的回复随经验删除不再显示，也一并删掉。
- 修改历史要等发布满 5 分钟才有第二版，本 spec 不在 e2e 里等（D5 不要求）；字段改动的显示由单测覆盖。

### 9.3 D9 手测（不进 Playwright：停 mini 的容器会影响别的会话）

1. 在 mini 上停掉 interaction 容器。2. 打开任意期刊页：期刊主体正常，经验区标题照常、`同行经验暂不可用` + `重试`、没有 `写投稿经验`、没有列表；打开任意 `/posts/<id>`：`投稿经验暂时打不开`。3. 启动容器，等它注册进 Nacos。4. 切到别的标签页再切回来（或点 `重试`）：经验区自动恢复。结果记到收尾。

### 9.4 门禁

`pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm build`（查动态渲染信号与 `useSearchParams` 进 RSC 的问题）全过；设计系统守护测试（`tests/design-system/`）的规则见 PAP-124 交接第 5 节。

## 10. 给 PAP-126 的接口（写进 `portal-PAP-125-handoff.md`）

- 经验页：`/posts/<id>`；回复的永久链接 `/posts/<id>?comment=<评论 id>`（单楼视图，PAP-124）。经验已删除时页面是「该经验已删除」状态（200，不是 404），不挂回复区。
- 时长、日期、投稿月份写法：`@/lib/experience/format`（提醒行的上下文 `你的投稿经验 · J Am Heart Assoc · 2026 年 3 月投稿` 可用 `formatSubmittedMonth`）。
- Markdown 摘录：`@/lib/markdown/plain`（提醒行的评论摘录可用）。
- queryKeys `experience` 前缀；身份变化已清。

## 11. 由我定的点

1. 期刊页首屏服务端取汇总（`3y`）与列表第一页，客户端接管（切窗口、加载更多、写后重取）；汇总切窗口不改地址。
2. 发布后不在本地插入新条目，取消在途请求后整体重取汇总与列表（列表按投稿日期排，新经验不一定在第一页）。
3. 经验页的详情由 RSC 取、作为 props，编辑后 `router.refresh()`，不进查询缓存（只有这一处用，且元数据也要它）。
4. 一致性规则做成联动（自动选最终结果、禁用并清空不适用的字段、审稿轮数固定为 1），而不是只在提交后报错。
5. 「今天」按北京时间（与后端一致），日期输入 `min=1990-01-01`。
6. 期刊卡片用现有 `fetchVenueDetail`，不为一本期刊新建批量卡片客户端；主页标签行只用快照刊名。
7. `readPublic` 挪到 `read.ts` 共用；`DiscussionCache` 直接复用不改名；`Problem` 追加 `existingPostId`。
8. 主页标签判定统一成 `visibleProfileTabs`，`/collections` 页也改用它（原来无条件渲染两个标签）。
9. 列表行整行是一个链接，作者名不单独链接（避免嵌套链接）；作者主页链接只在经验页署名上。
10. 页面元数据一律不写作者（匿名、实名都不写）。
11. 列表正文摘录用纯文本（去 Markdown 标记）两行截断，不渲染 Markdown。
12. 修改历史：结构化字段列「字段：旧值 → 新值」，正文复用 PAP-124 的按词比较。
13. e2e 每次运行写 5 条、全部删除；满 5 条的统计在桌面写入后两个视口都看同一批数据；@ 联想的匿名排除用未验证账号的会话直接请求联想接口验证，并以实名经验作对照。
14. 汇总高亮只在写后重取时比较，切窗口不高亮。
15. 期刊已失效时，经验页上指向期刊页的链接都改成纯文字，删除后跳 `/journals`。
16. 经验页照详情页的做法加 `loading.tsx` 与 proxy 预检，保证不存在时回真正的 404。
17. 发布后列表用 `resetQueries`（回到第一页），汇总用 `invalidateQueries`。

## 12. 实施记录（2026-10-28）

### 12.1 与本设计、计划的偏离

- `mdast-util-from-markdown` 从 devDependencies 挪到 dependencies（同版本 2.1.0）：纯文本摘录要在运行时解析；摘录自己遍历节点，没有引入 `mdast-util-to-string`。HTML 标签不进摘录。
- 「近 3 年 / 全部」照画板做成 `aria-pressed` 的按钮组（`fieldset` + 读屏可见的 `legend`），不是 radiogroup。切窗口时保留上一份数据（`keepPreviousData`）；整块降级只看「近 3 年」汇总与列表，「全部」取不到只在卡里提示。
- `journalHref` 放在纯模块 `lib/experience/links.ts`：RSC 不能调用 `"use client"` 模块的导出（终审 C1，单测因为 mock 了它没发现）。
- 经验页的作者视角（「编辑」、本人的匿名提示）与主页「投稿经验」的本人视角由服务端按会话算好，当前用户在页面上变化时 `router.refresh()`（`use-identity-refresh.ts`）。
- 期刊已失效时面包屑省掉刊名一节（`DetailCrumbs` 只接受带链接的层级）；不可用态的重试是重新进入地址（`retryHref`）。
- 用户经验的 Route Handler 是 `/api/users/[userId]/posts`（与已有的 `[userId]/avatar` 同名，dev 拒绝同级不同 slug）。
- 列表行：作者名不单独链接（整行是链接）；转投为否写「未转投」；发布日期今年写月日、往年写年月日。
- 收藏夹页在别人视角会多取一次公开收藏夹（标签判定统一由 `visibleProfileTabs` 做）。
- 主页「投稿经验」页加了 canonical（简报 §3.23）。

### 12.2 终审（opus）

已修：C1（上条）；I1 正文、APC、转投的服务端字段错误不显示，正文超限没有禁用提交；I2（上面的身份刷新）与删除遇到会话失效去登录；I3（上面的切窗口）；M4（HTML 标签进摘录，属 Review Focus 5，升为重要）。每条先写复现测试再修。

暂不改的 Minor：写后 2 秒内切窗口高亮可能卡住；首轮 / 最终结果单选组没有注册 ref，提交时焦点进不了出错的单选组；Server Action 转发的是原始请求对象（多余键会原样转发）；「清除选择」按钮在 radiogroup 里。

### 12.3 验证

- `pnpm lint`、`pnpm typecheck`、`pnpm test`（2,973 条）、`pnpm build` 全过。
- 本地开发服务器：期刊页经验区空状态、表单展开与「直接拒稿」联动（桌面 1440）；`/posts/1` 返回真正的 404。
- Playwright（`tests/e2e/experience.spec.ts`，7 条）：前两次用测试账号 B，第 1、2 条通过，第 3 条因测试自身的写法失败（联想接口返回数组、`/api/auth/me` 返回 `{ user }`），B 当天额度用完。随后在 mini 注册测试账号 C（只把这一行标成已验证），先对 e2e 用到的每个接口打一次真实请求核对返回形状，再对生产构建（`next start -p 4002`，避免开发模式热重载打断用例）整套跑：**7 条全过（25.5 秒）**。C 的主页对访客可见，他人视角的主页标签与 @ 联想对照都实际断言了。测试写的经验与回复全部删除（访客视角 C 的经验数为 0）。
- 真实运行环境：生产构建下正常显示的经验页（e2e 第 3、5、6 条）与已删除的经验页都正常，服务端调用 `journalHref` 不再报错（终审 C1 的修复生效）。
- D9：未执行（停 mini 的 interaction 容器会影响并行的 PAP-126）。
