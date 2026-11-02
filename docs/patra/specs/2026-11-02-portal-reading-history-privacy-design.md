# 门户阅读历史、阅读记录设置与隐私政策设计

> **Issue**：PAP-174 的一部分（v0.18 Analytics，Milestone FE Ready）
> **日期**：2026-11-02
> **依据**：设计简报 `patra-portal/docs/design-briefs/v0.18/analytics.md`（下称「简报」）§3.7～§3.11、§4.4～§4.7、§6、附录 A 第 8～13 条；画板快照 `docs/patra/design/snapshots/2026-11-01-analytics-hifi.zip` 的 `History`、`Privacy`、`Policy`、`Nav`；[阅读历史设计](2026-11-02-analytics-reading-history-design.md) §6（接口）；[门户埋点设计](2026-11-02-portal-analytics-tracking-design.md)（Cookie 名）
> **范围**：本份只覆盖 PAP-174 里**不依赖 PAP-170** 的部分。计数、热读标签与标记、看过还看了等 PAP-170 接口定了再另写一份

---

## 1. 要做什么

1. **阅读历史页 `/history`**：登录后可见，按天分组、一页 30 条游标翻页、单条删除、清空确认、三种空状态、记录已关闭提示条、取不到时的降级（简报 §3.7）。
2. **入口**：头像菜单与窄屏菜单在「我的文献库」与「提醒」之间加「阅读历史」（简报 §3.10）。
3. **设置 › 隐私第四张卡片「阅读记录」**：开关即时单独保存、查看与清空入口、加载与降级（简报 §3.8）。
4. **隐私政策页 `/privacy`** 与页脚两处链接（简报 §3.9）。

页面版式、文案、状态全部以简报与画板为准，本文只定实现做法与简报留给前端 spec 的选择。

**不做**：卡片与文献页计数、读者一行、首页「热读」、检索「近期热读」、看过还看了 / 同主题文献（等 PAP-170 接口）；阅读历史的导出、筛选、搜索、撤销删除（简报 §7）。

## 2. 接口与数据流

### 2.1 后端接口（PAP-169，已定）

都经网关 `/patra-analytics/me/reading-history…`，要登录（带会话令牌，没登录 401）：

| 用途 | 请求 | 响应 |
|---|---|---|
| 一页历史 | `GET /me/reading-history?cursor=…` | `{ recordingEnabled, items: [{ articleId, lastReadAt, fulltextRead }], nextCursor }`，30 条，`nextCursor` 为 `null` 到底 |
| 删一条 | `DELETE /me/reading-history/{articleId}` | 204（本来不在也是 204） |
| 清空 | `DELETE /me/reading-history` | 204 |
| 读开关 | `GET /me/reading-history/recording` | `{ enabled }` |
| 写开关 | `PUT /me/reading-history/recording`，`{ enabled }` | `{ enabled }` |

### 2.2 门户这边

- 新增 `lib/analytics/request.ts` 的 `callAnalytics(path, init)`，照 `lib/notification/request.ts` 的写法：读会话令牌、带浏览器来源、回 `ok` / `problem` / `no-session` / `error` 四种。只给这几个接口用。
- **列表走 Route Handler + TanStack Query**（同提醒页 `NotificationInbox`）：`GET /api/me/reading-history?cursor=…`。它调 analytics 拿一页，再用现有的 `fetchPublicationCards(ids)`（catalog 批量取卡片，≤ 100）配上标题、刊名、年份，返回：

  ```ts
  { recordingEnabled: boolean;
    items: { articleId: string; lastReadAt: string; fulltextRead: boolean;
             paper: { title: string; journal: string | null; year: number | null } | null }[];
    nextCursor: string | null }
  ```

  `paper: null` 表示 catalog 没回这一篇（已下架或合并），页面显示「这篇文献已不在 Patra 上」。analytics 或 catalog 任一取不到 → 502，页面显示「阅读历史暂时打不开」。未登录 → 401，页面跳登录（与文献库同一套处理）。
- **写操作走 Server Action**（同文献库 `library-actions.ts`）：`deleteHistoryEntry(articleId)`、`clearHistory()`、`setReadingRecording(enabled)`，放 `lib/analytics/history-actions.ts`，失败归类沿用 `CommonFailure`（`signed-out` / `unavailable`）。
- **删除与清空后不重新拉列表**：删一条从查询缓存里去掉这一行（游标翻页时重新拉会让已加载的页错位）；清空把缓存置成空列表并标记「刚清空」，用于选空状态。
- 页面需要的「开关关着」从列表响应的 `recordingEnabled` 取，不另请求（简报附录 A 第 11 条）。

### 2.3 设置卡片的数据

`/settings/privacy` 的服务端页面在取 identity 隐私设置的同时，并行取一次开关（`GET /me/reading-history/recording`），结果是 `{ enabled }` 或 `unavailable`，传给卡片。取不到时卡片显示降级与「重试」（`router.refresh()`），上面三张卡片不受影响。开关保存不跟 identity 的整份 PUT 一起（简报 §3.8）。

## 3. 阅读历史页

- `app/history/page.tsx`：`requireUser("/history")`（未登录去登录页再回来），`metadata`：标题 `阅读历史 · Patra`、`robots: { index: false }`。布局 `TopNav` + 单栏 880px + `Footer`。
- `components/history/`：`ReadingHistoryView`（客户端，`useInfiniteQuery`）、`HistoryDayGroup`、`HistoryRow`、`HistoryEmpty`、`ClearHistoryDialog`（与设置卡片共用）。
- **按天分组**：纯函数 `groupHistoryByDay(items, now)`，北京时间自然日；标题 `今天 · 11 月 1 日`、`昨天 · 10 月 31 日`、更早 `10 月 30 日 周五`，不在今年的写年份 `2025 年 12 月 30 日 周二`。在全部已加载的条目上算，加载更多后并入已有分组。
- **单条删除**：行变淡、按钮不可再点 → 成功后行消失（组空了组标题也消失），焦点移到下一行的删除按钮，没有下一行到上一行，再没有到「清空历史」；失败时行恢复并显示红字 `没能删除，请重试。`（`role="alert"`）。不确认、不撤销。
- **清空**：`ClearHistoryDialog`（`alertdialog`，焦点默认在「取消」，关闭后回到触发按钮）；记录关闭时说明句换一句；进行中两个按钮都不可点、文案 `清空中…`；成功关闭对话框、页面变「阅读历史已清空」；失败对话框留着、显示红色提示条。
- **空状态三种 + 加载中 + 取不到**，文案照简报 §3.7 第 8、9 条。「刚清空」只在本次页面里清空过才显示，刷新后按「开着、没有记录」显示。
- **翻页**：底部 `加载更早的记录`，到底写 `没有更早的了。超过 12 个月的记录会自动清理。`

## 4. 设置卡片「阅读记录」

- `components/settings/privacy/ReadingRecordCard.tsx`，挂在 `PrivacyForm` 的三张卡片之后、`查看我的主页` 之前，锚点 `#reading`。用 `SettingsCard` + `SaveStatus` + `SWITCH_INPUT` 的现有写法。
- 改开关立即调 `setReadingRecording`；保存中 / `✓ 已保存`（2 秒淡出）/ 失败（开关退回原值，`没有保存成功，请重试`）。快速连点按顺序串行提交，界面停在最后一次点的值（与 `PrivacyForm` 同一套规则）。
- 关闭时多一行说明；最后一行链到 `/privacy`。
- 底部 `查看阅读历史 →` 与 `清空阅读历史`（打开 `ClearHistoryDialog`，成功后卡片右上角 `✓ 已清空` 2 秒）。

## 5. 隐私政策页与入口

- `app/privacy/page.tsx`，服务端组件，正文写成 TSX（内容固定、要从代码里读 Cookie 名，MDX 拿不到），照画板 `Policy` 的九节与「一分钟看完」摘要照抄。公开、可收录，标题 `隐私政策 · Patra`。
- **Cookie 表从代码取名字**：三条 Cookie 的名字由 `session-cookie.ts` 与埋点的 Cookie 函数（PAP-173 的 `lib/analytics/server/identity-cookies.ts`，本份在它之后做，直接导入）按当前 Secure 开关算出（mini 现在 `PATRA_SESSION_COOKIE_SECURE=false`，实际名字是 `patra_session`、`patra_device`、`patra_sid`；以后开 Secure 自动变成 `__Host-` 开头）。会话标识一行：`把一次访问里的浏览串起来，30 分钟没有活动就换新` / `关闭浏览器时失效`。
- **动态渲染**：页面用 `await connection()` 声明在请求时渲染。内容固定的页面 Next 默认在构建时预渲染，构建机上没有 `PATRA_SESSION_COOKIE_SECURE` 时会按默认值把 `__Host-` 名写进 HTML，与 mini 运行时不符。代价是没有静态缓存，这一页很轻，可以接受。
- **正文与功能同一个 PR 上线**：政策里写的热读、看过还看了等由同版 PAP-170 的前端部分提供，前端只有一个 PR，合并时它们都在，所以正文照画板全写，不按功能删减。
- **目录**：左侧吸顶、当前节陶土短竖线，用现有 `use-active-heading`；窄屏移到正文上方、不吸顶。三列表格用 grid，窄屏压成上下两行（简报 §6）。
- **占位**：网站域名填 `https://patra.linqibin.dev`；**联系邮箱、运营者名称、服务器所在地**用户还没给，先保留方括号占位；版本 `第 1 版`，生效日期与最近更新集中在一个常量里，上线当天改。
- **页脚**：「项目」一列在「数据源」后加 `隐私政策`；底栏在字标与版权之间加同一个链接。
- **头像菜单**：`UserMenu` 与 `MobileAccount` 在「我的文献库」与「提醒」之间加 `阅读历史`（lucide `History`）。

## 6. 测试

TDD，按简报 §4 的状态矩阵，每个状态一条用例（Testing Library，`getByRole` 优先）：

| 对象 | 用例 |
|---|---|
| `groupHistoryByDay` | 今天、昨天、今年更早（带周几）、跨年；北京时间零点边界；加载更多后并入已有分组 |
| Route Handler | 拼卡片；catalog 没回的条目 `paper: null`；analytics 401 → 401、5xx / 超时 → 502；catalog 失败 → 502 |
| `ReadingHistoryView` | `history.list`（含读过全文胶囊、已不在 Patra 上）、`more` / `end`、`delete` / `delete.failed`（含焦点移动）、`clear.confirm` / `clearing` / `failed` / `done`、`off.banner`、`empty` / `off.empty`、`loading` / `unavailable` + 重试 |
| `ReadingRecordCard` | `on` / `off`、`saving` / `saved` / `failed`（退回）、`clear`、`loading` / `unavailable` |
| 三个 Server Action | 路径与方法；204 / 200 成功；401 → `signed-out` 并清 Cookie；其他 → `unavailable` |
| 隐私政策页 | 九节与目录都在；Cookie 表的名字随 Secure 开关变化；占位文字存在 |
| 页脚与菜单 | 两处「隐私政策」链接到 `/privacy`；菜单顺序含「阅读历史」 |

Playwright（连 mini 网关，等后端部署后跑）：C 关闭阅读记录后打开一篇文献，历史页没有这条；清空后历史页为空；页脚能到隐私政策；375 宽下历史页与政策页不横向溢出。

## 7. 我的决定（等你审）

1. **列表用 Route Handler + TanStack Query、写操作用 Server Action**（简报 §7 把选择留给前端 spec；与提醒页、文献库一致）。
2. **删除、清空后改本地缓存，不重新拉列表**（§2.2）。
3. **「刚清空」空状态只在本次页面里出现**，刷新后按「开着、没有记录」显示（后端不记「清空过」给前端看）。
4. **隐私政策正文用 TSX**，Cookie 名从代码算，不写死 `__Host-`（§5）。
5. **网站域名填 `patra.linqibin.dev`**；其余三个占位等用户给，**合并前必须填上**（门户对公网开放，方括号会直接显示）。
6. **隐私政策页请求时渲染**（§5），不静态预渲染。
7. 其余小处：设置卡片改开关或清空后让 `/history` 的查询缓存失效；历史列表关闭窗口聚焦重取；按 `Asia/Shanghai` 分组；删除按钮的 `aria-label` 标题按码点截 40 个字；analytics 回 422（坏游标）按 502 处理。
