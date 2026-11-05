# 阅读历史、阅读记录设置与隐私政策 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 登录用户能在 `/history` 翻看、逐条删除、清空自己的阅读历史，在设置 › 隐私开关阅读记录；全站有可从页脚到达的隐私政策页。

**Architecture:** 读走 Route Handler（analytics 取一页 ID + catalog 批量配卡片）+ TanStack Query 无限查询；写走 Server Action（经新的 `callAnalytics`）；隐私政策是请求时渲染的服务端页面，Cookie 名从代码算。版式、文案、状态照设计简报 §3.7～§3.10 与画板快照 `History` / `Privacy` / `Policy` / `Nav`。

**Tech Stack:** Next 16.3 App Router、React 19、TanStack Query v5、zod、Vitest + Testing Library、lucide。

**Spec:** `docs/patra/specs/2026-11-02-portal-reading-history-privacy-design.md`（设计简报 `patra-portal/docs/design-briefs/v0.18/analytics.md`；后端接口 `docs/patra/specs/2026-11-02-analytics-reading-history-design.md` §6）

## Global Constraints

- 每个任务不单独提交：用户本地验收后统一提交。任务收尾跑该任务测试 + `pnpm lint`。
- vitest 一律 `pnpm test --maxWorkers=2 <路径>`。
- 后端接口（经网关、要登录）：`GET /patra-analytics/me/reading-history?cursor=`、`DELETE /patra-analytics/me/reading-history/{articleId}`、`DELETE /patra-analytics/me/reading-history`、`GET|PUT /patra-analytics/me/reading-history/recording`。一页 30 条；`nextCursor` 为 null 到底；ID 都是字符串。
- 文案一字不差照简报 §3.7、§3.8、§3.9、§4.7；不说「浏览量」「阅读量」「足迹」「匿名」。
- 时间按北京时间（`Asia/Shanghai`）。
- 用现有原语：`Dialog`（`components/ui/dialog`）、`SettingsCard`、`SaveStatus`、`SWITCH_INPUT`、`btnDanger` / `btnSecondary`、`FormAlert`、`useActiveHeading`、`formatCount`；颜色只用现有 token。
- 隐私政策三处占位 `[联系邮箱]`、`[运营者名称]`、`[服务器所在地]` 原样保留（等用户给）；网站域名写 `https://patra.linqibin.dev`；生效日期与最近更新集中在一个常量 `POLICY_DATES`，值 `[上线当天]`。

## Review Focus

1. **加载更多之后删一条**：删的是第 2 页里的条目，分组与焦点仍正确，已加载的页不重排（Task 5 用例）。
2. **连点删除**：同一行删除中再点按钮无效，不发第二次（Task 5 用例）。
3. **会话在页面开着时过期**：删除 / 清空 / 开关返回 `signed-out` 时去登录页再回来，不卡在失败态（Task 3、Task 5 用例）。
4. **标题极长或含内联标签**：`aria-label` 截前 40 个字（按码点，不切坏 emoji），不带 `<i>` 等标签（Task 5 用例）。
5. **设置页开关保存中又点一次**：串行提交，界面停在最后一次的值；最后一次失败退回服务端确认值（Task 6 用例）。

---

### Task 1: analytics 请求与阅读历史读接口

**Files:**
- Create: `patra-portal/src/lib/analytics/request.ts`、`patra-portal/src/lib/analytics/history-schema.ts`、`patra-portal/src/lib/analytics/history.ts`
- Create: `patra-portal/src/app/api/me/reading-history/route.ts`
- Test: `patra-portal/tests/lib/analytics/history.test.ts`、`patra-portal/tests/app/api/reading-history.test.ts`

**Interfaces:**
- Produces:
  - `callAnalytics(path: string, init?: Omit<GatewayRequestInit, "token" | "client">): Promise<AnalyticsReply>`（照 `callNotification`：读会话令牌，没有回 `no-session`；`AnalyticsReply = IdentityReply`）。
  - `history-schema.ts`：`readingHistoryPageSchema`（`{ recordingEnabled: boolean; items: { articleId: string; lastReadAt: string; fulltextRead: boolean }[]; nextCursor: string | null }`）、`recordingSchema`（`{ enabled: boolean }`）；类型 `HistoryEntry = { articleId: string; lastReadAt: string; fulltextRead: boolean; paper: { title: string; journal: string | null; year: number | null } | null }`、`HistoryPage = { recordingEnabled: boolean; items: HistoryEntry[]; nextCursor: string | null }`。
  - `fetchReadingHistory(cursor?: string): Promise<Read<HistoryPage>>`：analytics 一页 → `fetchPublicationCards(ids)` 配卡片（`year` 用现有 `publishedYear(paper)`）；`no-session` / 401 → `{ kind: "signed-out" }`（照 `Read` 现有的 kind 名）；analytics 其他失败、422、catalog 失败 → `unavailable`。
  - `fetchReadingRecording(): Promise<Read<{ enabled: boolean }>>`。
  - `GET /api/me/reading-history?cursor=` → `readReply(await fetchReadingHistory(cursor))`。

- [ ] **Step 1: 失败测试 `history.test.ts`**（stub `fetch`、`next/headers` 用 cookie-jar，带会话 Cookie）：请求 `http://gw.test/patra-analytics/me/reading-history?cursor=abc` 带 `Authorization`；随后请求 catalog `publications/batch?id=…`，按历史顺序配卡片；catalog 没回的条目 `paper: null`；空页不请求 catalog；没有会话 Cookie → `signed-out` 且不请求；analytics 401 → `signed-out`；500 / 422 / 超时 → `unavailable`；catalog 500 → `unavailable`；`fetchReadingRecording` 200 → `{ enabled: false }`、503 → `unavailable`。
- [ ] **Step 2: 失败测试 `reading-history.test.ts`**：200 回拼好的页；`signed-out` → 401；`unavailable` → 502；`cursor` 原样转给后端（URL 编码）。
- [ ] **Step 3: 跑 → FAIL；实现；跑 → PASS。**

### Task 2: 按天分组

**Files:**
- Create: `patra-portal/src/lib/history/groups.ts`
- Test: `patra-portal/tests/lib/history/groups.test.ts`

**Interfaces:**
- Produces: `groupHistoryByDay(items: readonly HistoryEntry[], now: number): { key: string; label: string; items: HistoryEntry[] }[]`（`key` 是北京时间的 `YYYY-MM-DD`，保持传入顺序）；`historyTime(iso: string): string`（`15:42`）。

- [ ] **Step 1: 失败测试**（`now` 固定为北京时间 2026-11-01 16:00）：今天 → `今天 · 11 月 1 日`；昨天 → `昨天 · 10 月 31 日`；更早 → `10 月 30 日 周五`；去年 → `2025 年 12 月 30 日 周二`；UTC 10-31 16:30（北京 11-01 00:30）算今天；同一天多条在同一组、顺序不变；空数组 → `[]`；`historyTime("2026-11-01T07:42:10.177Z") === "15:42"`。
- [ ] **Step 2: 跑 → FAIL；实现（`Intl.DateTimeFormat("zh-CN", { timeZone: "Asia/Shanghai", … })` 取年月日与周几）；跑 → PASS。**

### Task 3: 写操作

**Files:**
- Create: `patra-portal/src/lib/analytics/history-actions.ts`
- Modify: `patra-portal/src/lib/query-keys.ts`（`readingHistory: ["reading-history"] as const`）
- Test: `patra-portal/tests/lib/analytics/history-actions.test.ts`

**Interfaces:**
- Consumes: `callAnalytics`（Task 1）、`CommonFailure`、`UNAVAILABLE`、`isSessionRejected`、`clearSessionCookie`。
- Produces（都是 `"use server"`）：`deleteHistoryEntry(articleId: string): Promise<{ ok: true } | CommonFailure>`；`clearHistory(): Promise<{ ok: true } | CommonFailure>`；`setReadingRecording(enabled: boolean): Promise<{ ok: true; enabled: boolean } | CommonFailure>`。

- [ ] **Step 1: 失败测试**：三个的路径与方法（`DELETE …/me/reading-history/11`、`DELETE …/me/reading-history`、`PUT …/recording` 体 `{ enabled: false }`）；204 / 200 → `ok`；没有会话 → `signed-out` 不请求；401（`GW-0401`）→ `signed-out` 并清会话 Cookie；500 / 网络错误 → `unavailable`；`deleteHistoryEntry("01")`、`deleteHistoryEntry("x")` → `unavailable` 不请求；`setReadingRecording` 非布尔 → `unavailable`。
- [ ] **Step 2: 跑 → FAIL；实现；跑 → PASS。**

### Task 4: 清空确认对话框

**Files:**
- Create: `patra-portal/src/components/history/ClearHistoryDialog.tsx`
- Test: `patra-portal/tests/components/history/ClearHistoryDialog.test.tsx`

**Interfaces:**
- Consumes: `clearHistory`（Task 3）、`useCommonFailureRedirect`。
- Produces: `<ClearHistoryDialog open: boolean; recordingEnabled: boolean; onOpenChange(open: boolean): void; onCleared(): void>`。

- [ ] **Step 1: 失败测试**（mock `clearHistory`）：`alertdialog` 名为 `清空全部阅读历史？`；开着时说明含 `之后打开的文献会继续记录。`，关着时换成 `阅读记录现在是关闭的，清空后这里会一直是空的。`，两种都含 `文献页上的浏览数是汇总后的人数，不受影响。`；打开时焦点在「取消」；点「清空」→ 按钮变 `清空中…`、两个都禁用；成功 → `onCleared` 且关闭；失败 → 对话框留着，`role="alert"` 显示 `暂时没能清空，请稍后再试。`；`signed-out` → 走登录跳转。
- [ ] **Step 2: 跑 → FAIL；实现；跑 → PASS。**

### Task 5: 阅读历史页

**Files:**
- Create: `patra-portal/src/lib/history/queries.ts`、`patra-portal/src/components/history/ReadingHistoryView.tsx`、`HistoryRow.tsx`、`HistoryEmpty.tsx`、`HistorySkeleton.tsx`
- Create: `patra-portal/src/app/history/page.tsx`
- Test: `patra-portal/tests/components/history/ReadingHistoryView.test.tsx`、`patra-portal/tests/app/history-page.test.tsx`

**Interfaces:**
- Consumes: Task 1 的路由与类型、Task 2、Task 3、Task 4。
- Produces: `useReadingHistory()`（`useInfiniteQuery`，`queryKeys.readingHistory`，`refetchOnWindowFocus: false`，`retry: false`）；`removeHistoryEntry(client, articleId)`、`markHistoryCleared(client)` 改缓存。

- [ ] **Step 1: 失败测试 `ReadingHistoryView`**（按简报 §4.4 每个状态一条，fetch stub 回路由的 JSON）：
  - `history.list`：组标题、时间 `15:42`、标题链到 `/papers/{id}`、刊名斜体 · 年份、`读过全文` 胶囊；`paper: null` 的行写 `这篇文献已不在 Patra 上`、不是链接、仍有删除按钮。
  - `history.more` / `history.end`：`加载更早的记录` 取下一页并并入已有分组；到底写 `没有更早的了。超过 12 个月的记录会自动清理。`
  - `history.delete`：点 X（`aria-label` = `从阅读历史删除：<标题前 40 字>`，按码点截、去掉 `<i>` 等标签）→ 行变淡、按钮禁用（再点不发第二次）→ 成功后行消失，组空了组标题也消失，焦点到下一行的删除按钮 / 上一行 / 「清空历史」；删的是第 2 页的条目时同样正确。
  - `history.delete.failed`：行恢复，`role="alert"` 写 `没能删除，请重试。`
  - `history.clear.*`：「清空历史」打开对话框；成功后页面变 `阅读历史已清空`；没有记录时「清空历史」禁用。
  - `history.off.banner`：`recordingEnabled: false` 且有记录 → 提示条 `阅读记录已关闭：现在打开的文献不会记到这里，下面是关闭之前的记录。` + 链接 `去设置里开启` → `/settings/privacy#reading`。
  - `history.empty` / `history.off.empty`：三种空状态的标题、说明、按钮（简报 §3.7 第 8 条）。
  - `history.loading` / `history.unavailable`：骨架；取不到时 `阅读历史暂时打不开` + `重试`（点了重新请求）。
  - 401 → 去登录页（`next=/history`）。
- [ ] **Step 2: 失败测试 `history-page`**：`requireUser("/history")`；`metadata` 标题 `阅读历史 · Patra`、`robots.index === false`；页头 h1 `阅读历史`、说明句、`阅读记录设置` 链接到 `/settings/privacy#reading`。
- [ ] **Step 3: 跑 → FAIL；实现（版式照画板 `History`：880px 单栏、三列行 56px · 正文 · 32px，组标题样式同提醒页；窄屏页头按钮换行、时间列 44px）；跑 → PASS。**

### Task 6: 设置 › 隐私「阅读记录」卡片

**Files:**
- Create: `patra-portal/src/components/settings/privacy/ReadingRecordCard.tsx`
- Modify: `patra-portal/src/components/settings/privacy/PrivacyForm.tsx`（三张卡片之后、`查看我的主页` 之前插入 `readingCard` 插槽）、`patra-portal/src/app/settings/privacy/page.tsx`（与 `fetchMyPrivacy` 并行 `fetchReadingRecording()`）
- Test: `patra-portal/tests/components/settings/ReadingRecordCard.test.tsx`、`patra-portal/tests/app/settings-pages.test.tsx` 加用例

**Interfaces:**
- Consumes: `fetchReadingRecording`（Task 1）、`setReadingRecording`（Task 3）、`ClearHistoryDialog`（Task 4）、`queryKeys.readingHistory`。
- Produces: `<ReadingRecordCard initial: { enabled: boolean } | "unavailable">`。

- [ ] **Step 1: 失败测试**（简报 §4.5）：`on` / `off`（关时多一行 `已关闭：现在打开的文献不会记录。…`）；开关 `role="switch"` 名 `记录我的阅读历史`；点了 → `保存中…` → `✓ 已保存`；失败 → 开关退回、`没有保存成功，请重试`；保存中连点两次 → 串行两次请求、界面停在最后一次的值；`clear` → 对话框 → 成功后右上角 `✓ 已清空`；`unavailable` → 只剩标题 + `阅读记录设置暂时打不开，请稍后再试。上面的设置不受影响。` + `重试`；`查看阅读历史 →` 链到 `/history`；最后一行「隐私政策」链到 `/privacy`；保存或清空后 `readingHistory` 查询失效；卡片锚点 `id="reading"`。设置页用例：analytics 取不到时其余三张卡片照常。
- [ ] **Step 2: 跑 → FAIL；实现；跑 → PASS。**

### Task 7: 隐私政策页与入口

**Files:**
- Create: `patra-portal/src/app/privacy/page.tsx`、`patra-portal/src/components/privacy/PolicyToc.tsx`（客户端，`useActiveHeading`）、`patra-portal/src/components/privacy/PolicyTable.tsx`
- Modify: `patra-portal/src/components/portal/Footer.tsx`、`patra-portal/src/components/portal/UserMenu.tsx`、`patra-portal/src/components/portal/MobileAccount.tsx`
- Test: `patra-portal/tests/app/privacy-page.test.tsx`、Footer / UserMenu / MobileAccount 现有测试加用例

**Interfaces:**
- Consumes: `sessionCookieName()`（在 `session-cookie.ts` 新导出，返回会话 Cookie 名）、`deviceCookie().name`、`sessionTrackingCookie().name`（PAP-173）。

- [ ] **Step 1: 失败测试 `privacy-page`**：`await connection()` 被调用（请求时渲染）；h1 `隐私政策`、`第 1 版`、「一分钟看完」5 条、九节标题（01 适用范围 … 09 联系我们与政策变更）、目录 9 个链接指向各节锚点；Cookie 表：`PATRA_SESSION_COOKIE_SECURE=false` 时三行名字是 `patra_session`、`patra_device`、`patra_sid`，不设时带 `__Host-`；会话标识一行写 `关闭浏览器时失效`；页面含 `https://patra.linqibin.dev` 与三处方括号占位；`metadata` 标题 `隐私政策 · Patra`、可收录。正文照快照 `Policy.dc.html` 逐字。
- [ ] **Step 2: 失败测试入口**：页脚「项目」一列在「数据源」后有 `隐私政策` → `/privacy`，底栏也有一个；头像菜单顺序 `个人主页 · 我的文献库 · 阅读历史 · 提醒 · 设置 · 退出登录`，「阅读历史」链到 `/history`；窄屏菜单同样有「阅读历史」。
- [ ] **Step 3: 跑 → FAIL；实现；跑 → PASS。**

### Task 8: 冒烟用例与全量验证

**Files:**
- Create: `patra-portal/tests/e2e/reading-history.spec.ts`

- [ ] **Step 1: 写 Playwright 用例**（不在本地跑，等后端部署 mini）：C 在设置里关闭阅读记录 → 打开一篇文献 → `/history` 没有这条 → 打开阅读记录；C 清空历史 → 页面 `阅读历史已清空`；访客从首页页脚点「隐私政策」到 `/privacy`；375 宽下 `/history` 与 `/privacy` 不横向溢出（`document.documentElement.scrollWidth <= 375`）。
- [ ] **Step 2: 全量**：`pnpm lint`、`pnpm typecheck`、`pnpm test --maxWorkers=2` 全绿。
- [ ] **Step 3: 本地起门户**（`portal-dev-v018`）：`/privacy` 桌面与 375 宽截图对照画板；`/history` 在 analytics 未部署时显示「阅读历史暂时打不开」；设置页第四张卡片显示降级态，其余三张照常。
