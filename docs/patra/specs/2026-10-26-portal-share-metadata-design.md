# 门户「分享与页面信息」工程设计（PAP-115）

> 版本：v0.13 Library · Issue：Linear PAP-115（GH #285）· 日期：2026-10-26
> 产品定义：`docs/patra/release-specs/v0.13-library.md` §2.1.4、决策 H / I、Done 判定 D10 / D11
> 页面、状态 ID 与文案：`patra-portal/docs/design-briefs/v0.13/library.md`（下称「简报」）§3.3、§3.4、§3.8、§3.14、§3.17、§3.18、§4.2；画布 <https://claude.ai/artifact/BoLjKpkaQhzotxdiNJdeP6>
> 前置：PAP-114 工程 spec `2026-10-25-library-portal-design.md`（下称「PAP-114 spec」）。本文只写 PAP-115 新增与改动的部分，PAP-114 做好的件直接复用。

---

## 1. 目标与范围

读者在文献详情页点「分享」，拿到这篇文献的规范链接、所选格式的引用文本和单篇导出文件；mini 只有 HTTP，剪贴板接口不可用时也能复制成功。文献页、期刊页、公开收藏夹页在 HTML 里带标题、描述、规范地址，公开收藏夹页按所有者的主页收录设置决定 `noindex`。

### 1.1 本 Issue 做

| 块 | 内容 |
|---|---|
| 页面信息 | 文献页、期刊页、公开收藏夹页的 `generateMetadata`：`title`、`description`、`alternates.canonical`、Open Graph 文字标签；收藏夹页 `noindex` 跟随所有者 |
| 分享面板 | 详情页页头「分享」按钮 + `SharePanel`（链接、引用、导出这篇三块） |
| 窄屏页头 | 详情页操作行 ≤ 540px 的方案 A（简报 §3.3 第 4 条） |
| 降级补齐 | 收藏夹页元数据遇 interaction / identity 故障不出错页；分享面板的引用失败态；「我的状态」出错后的页面内自愈（§5） |
| 冒烟 | `library.spec.ts` 的两账号用例扩展到「查看并复制 → 导出 RIS」；详情页分享面板用例；三类页面的元数据用例 |

### 1.2 不做

- 二维码海报、系统分享（`navigator.share`）、短链、分享图（`og:image`）——release spec §3 H。
- 公开收藏夹页的分享浮层改成 `SharePanel`：PAP-114 的链接浮层已经满足简报 §3.14（只有「链接」一块），不在本 Issue 的 AC 里，不动。
- 设置 `PATRA_PORTAL_PUBLIC_URL`：mini 上先不设，按请求的 `Host` 拼（2026-10-26 用户定，PAP-114 spec §3.6）。

### 1.3 反目标

- canonical 或「复制链接」用 `window.location.origin` 拼（本机、tailscale IP、主机名访问会出不同链接）。
- 元数据的取数让页面多打一次网关（`generateMetadata` 与页面必须共用一次请求）。
- identity 或 interaction 的故障经由 `generateMetadata` 把公开收藏夹页打成错误页（页面主体对这类故障本来有降级）。
- 分享面板依赖 interaction（简报 §3.4：链接是页面自己的，引用与导出来自 catalog，interaction 挂了照常可用）。

---

## 2. 页面信息

### 2.1 去重：取数函数包 `cache()`

`generateMetadata` 与页面各调一次取数函数。`gatewayFetch` 带超时信号，不在 Next 的 fetch 记忆化范围内，所以照 `fetchPublicProfile` 的写法用 React `cache()` 包：

- `fetchPublicationDetail`（`src/lib/portal-api/publications.ts`）
- `fetchVenueDetail`（`src/lib/portal-api/venues.ts`）
- `fetchPublicCollection`（`src/lib/interaction/collections.ts`）

### 2.2 纯函数：`src/lib/page-metadata.ts`

三个构造函数，入参是已经取到的数据与绝对规范地址，返回 `Metadata`，不碰网络，单测直接覆盖：

```ts
paperMetadata(paper: PaperDetail, canonical: string): Metadata
journalMetadata(venue: VenueDetail, canonical: string): Metadata
collectionMetadata(collection: PublicCollection, canonical: string, indexable: boolean): Metadata
```

| 页面 | `title` | `description` |
|---|---|---|
| 文献页 | `<shortTitle(title)> · Patra` | 摘要纯文本前 160 字素；没有摘要时 `<期刊> · <按精度的出版日期> · <前 3 位作者>` 中有的部分，用 ` · ` 连接 |
| 期刊页 | `<刊名> · Patra` | `<刊名>（ISSN <issnL>）`；没有 `issnL` 时只有刊名 |
| 公开收藏夹页 | `<名称> · <昵称>的收藏夹 · Patra`；没有昵称（已注销、本人视角 identity 不可用）时 `<名称> · 收藏夹 · Patra` | 简介前 120 字素；没有简介时 `<昵称>在 Patra 公开的收藏夹，共 <N> 篇文献。`（没有昵称时 `Patra 上公开的收藏夹，共 <N> 篇文献。`） |

- **摘要纯文本**：有结构化段落时把各段 `text` 按顺序用空格连起来；否则用 `abstractPlainText`。文本里可能有内联标记（`<i>`、`<sup>` 等），先 `parseInlineMarkup` + `inlinePlainText` 去掉，再把连续空白压成一个空格。截断按字素（`graphemes`），被截断时末尾加 `…`。
- **出版日期**：`formatPublishedDate(publishedDate)`，回落 `publicationYear`。
- **作者**：`authors` 按 `order` 的前 3 位 `name`，用 `, ` 连接，超过 3 位加 ` 等`。
- **期刊页的出版者**：`VenueDetail` 没有出版者字段，简报模板「中有的部分」按刊名 + `issnL` 落地（我定的，不去 `identifiers` 里猜）。
- **canonical 与 `og:url`** 都传 `publicUrl(path)` 的绝对地址：项目没设 `metadataBase`，相对地址会被解析到默认的 localhost。
- **Open Graph**（我定的：派发本 Issue 时要求带上，Issue 原文只写了 canonical）：`og:title`（不带 ` · Patra`）、`og:description`、`og:url`、`og:site_name` = `Patra`、`og:type`（文献页 `article`，其余 `website`）。不放 `og:image`。
- 公开收藏夹页 `indexable` 为假时加 `robots: { index: false }`。

### 2.3 三个 `generateMetadata`

- **文献页** `/papers/[id]`：`fetchPublicationDetail(id)`，`null` 时返回 `{}`（`not-found.tsx` 有自己的元数据）；抛错不拦，与页面一样交给错误页（页面本身也会抛）。
- **期刊页** `/journals/[id]`：同上。
- **公开收藏夹页** `/collections/[id]`：
  - `fetchPublicCollection(id)`：`not-found` → `{}`；`unavailable` → `{ title: "收藏夹暂时打不开 · Patra", robots: { index: false } }`。
  - `ok` 时算 `indexable`（`ownerIndexable(owner)`，同文件内私有函数）：`owner` 为 null、`deleted`、没有 `username` → `false`；否则 `fetchPublicProfile(owner.username)`，`found` 时取 `profile.indexable`，其余（`not-found`、旧名跳转）为 `false`。`fetchPublicProfile` 在 identity 失败时抛错，这里 `try/catch`，`catch` 里先 `unstable_rethrow`，再记日志、按 `false`（取不到就不让收录，是安全的一侧）。
  - 这次 `fetchPublicProfile` 只在元数据里调；所有者能看到这个收藏夹，说明他的主页对这位访客可见，查询结果与访客视角一致。

### 2.4 元数据在 HTML 里的位置（我定的）

Next 16 对动态页面默认流式输出元数据：识别为「只读 HTML 的爬虫」的 UA（默认名单含 Bingbot、Twitterbot、Slackbot、facebookexternalhit 等）拿到的标签在 `<head>`；其余 UA（浏览器、curl，以及会执行 JS 的 Googlebot）拿到的标签追加在 `<body>` 末尾，浏览器会把它们挂到 `<head>`。2026-10-26 本地实测：默认 UA 与 Googlebot 在 `<body>`，`facebookexternalhit` 在 `<head>`。本版不改 `htmlLimitedBots`：AC 要求「curl 取到的 HTML 里标题、描述、canonical 正确」，标签在不在 `<head>` 不影响这一条；全量改成阻塞输出会拖慢每个页面的首字节。验收时用默认 UA 与 `facebookexternalhit` 各 curl 一次，记录标签位置。

---

## 3. 分享面板 `SharePanel`

### 3.1 入口

`PaperHeader` 操作行在收藏按钮后面加 `ShareButton`（胶囊次按钮，图标 `Share2`，文字 `分享`）。点开 `ResponsivePopover`（桌面锚在按钮下、`align="start"`、宽 400px；≤ 880px 底部抽屉，与 `SavePopover` 一致）。分享地址由 RSC 用 `publicUrl("/papers/<id>")` 拼好、经 `PublicationDetailView` → `PaperHeader` 作为 prop 传下去。

### 3.2 三块

`src/components/library/SharePanel.tsx`（客户端），props：`{ paperId, pmid, link }`。

1. **链接**：小标题 `链接` + `CopyLinkField`（PAP-114 已有：只读等宽输入框 + `复制链接`）。
2. **引用**：小标题 `引用` + `CitationFormatTabs`（读写 `CITATION_STYLE_KEY`，与复制引用对话框共用上次的格式，默认 `gb-t-7714-2015`）+ `CitationPreview` + `复制引用`（次按钮，`CopyButton`，只复制 `text`，不带序号）。预览最多 6 行（`max-h-36`）滚动。
   - 数据：`useQuery(["citations", style, [paperId]])` → `POST /api/publications/citations { ids: [paperId], style }`，`retry: false`；面板打开时才请求。
   - 加载：两行骨架；失败：`引用暂时生成不了` + `重试`（`refetch()`），`复制引用` 禁用。
3. **导出**：小标题 `导出` + 四个文字按钮 `RIS` · `BibTeX` · `CSV` · `CSL-JSON` + 一行 `fg-3` `RIS 可导入 Zotero、EndNote、NoteExpress`。点按钮：该按钮变 `准备中…`、四个都禁用 → `POST /api/publications/export { ids: [paperId], format, name: pmid ?? paperId }` → `saveResponseAsFile` → toast `已开始下载 <文件名>`；失败时面板顶部 `FormAlert` `导出没有成功，请稍后再试`，按钮恢复。单篇导出不记「上次的导出格式」（四个按钮平铺，没有默认项）。

复制成功、降级都走 `useCopy`（PAP-114，简报 §3.8）：`navigator.clipboard.writeText` → 选中后 `execCommand("copy")` → 保持选中并提示快捷键。

### 3.3 抽出 `CitationPreview`

PAP-114 spec 提到的 `CitationPreview` 实际内联在 `CiteDialog` 里。本 Issue 抽成 `src/components/library/CitationPreview.tsx`，`CiteDialog` 与 `SharePanel` 共用：

```ts
CitationPreview({ lines, state, onRetry, className, ref })
// state: "loading" | "error" | "ready"；ref 指向预览框（降级时要选中它）
```

`CiteDialog` 原有测试保持绿色就是这次重构的保护。导出格式表 `FORMATS`（`ExportDialog` 内部常量）挪到 `src/lib/library/export-formats.ts` 导出，两处共用。

---

## 4. 窄屏页头方案 A

简报 §3.3 第 4 条，断点沿用详情页的 540px。

```
> 540px：[阅读全文 ↓] [下载 PDF] [收藏] [分享]  在 PMC 查看原文 ↗     （一行，flex-wrap，同现状）

≤ 540px：
[          阅读全文 ↓          ]        ← 主按钮整行
[ 下载PDF ][  收藏  ][  分享  ]        ← 次按钮等分，48px 高，图标在上、文字在下，text-xs
在 PMC 查看原文 ↗                        ← 左对齐文字链
```

- `FullTextActions` 拆成两个导出：`FullTextPrimaryAction`（主按钮：阅读全文 / 只有 PDF 时的下载 PDF 主按钮 / 外链 / 禁用）与 `FullTextPdfAction`（有全文且有 PDF 时的次按钮「下载 PDF」，其余返回 null）。
- 操作行结构：`主按钮` + `<div 次按钮组>`（`PDF?` · `收藏` · `分享`）+ `PMC 链接?`。次按钮组在桌面是 `contents`（不改现有一行排布），≤ 540px 是 `grid grid-flow-col auto-cols-fr gap-2`：有几个就几等分（三等分或两等分），「收藏暂不可用」变长也不挤动其他按钮（简报 §3.3 第 5 条）。
- 紧凑竖排形态由各按钮自己的 `className` 入口给：`SaveButton`、`ShareButton`、`PdfDownloadLink` 加可选 `className`，`PaperHeader` 传同一串 `max-[540px]:h-12 max-[540px]:flex-col max-[540px]:gap-1 max-[540px]:px-2 max-[540px]:text-xs`。`PdfDownloadLink` 的文件大小在 ≤ 540px 隐藏（竖排放不下三行）。不用父级 `*:` 选择器去改子元素（`SaveButton` 还渲染读屏用的 `sr-only` 播报节点）。
- 540 ~ 880px 之间是「桌面一行 + 底部抽屉」，与 PAP-114 已验收的 `SavePopover` 一致，不改。

---

## 5. 降级补齐（决策 H、简报 §3.17、D11）

PAP-114 已把简报 §3.17 表里的位置都做了降级。本 Issue 补三处：

1. **公开收藏夹页元数据**：见 §2.3，interaction 不可用、identity 不可用都不抛错。
2. **分享面板**：引用失败态（§3.2）；面板不调用 interaction。
3. **「我的状态」出错后的页面内自愈**：现在状态查询 `retry: false`、全局 `refetchOnWindowFocus: false`，出错后只有换页重新挂载才会重取，停在同一页时 interaction 恢复了按钮仍是「收藏暂不可用」。改为状态查询在**出错时**回到窗口（`refetchOnWindowFocus`）和网络恢复（`refetchOnReconnect`）时重取一次，正常时不因聚焦重取（不增加平时的请求）。`LibraryStatusProvider` 用函数形式：`(query) => query.state.status === "error"`，不另写轮询。这就是 D11「容器恢复后收藏区块自动恢复」的含义：服务恢复后用户停在原页面不动，按钮自己恢复，不需要换页或重新进入页面（2026-10-26 规划会话确认；「下次进页面恢复」不满足 D11）。

---

## 6. 测试

前端 TDD：每个任务先写失败的测试。

- **单元**（Vitest，`tests/` 镜像路径）：
  - `page-metadata`：三个构造函数的标题、描述（结构化 / 纯文本 / 无摘要回落、内联标记去除、字素截断加 `…`）、canonical、OG、`noindex`。
  - 三个页面的 `generateMetadata`（mock 取数与 `publicUrl`）：文献 / 期刊 `null` → `{}`；收藏夹 `not-found`、`unavailable`、所有者 null / 已注销 / 主页不允许收录 / identity 抛错（`noindex`，不抛）。
  - `cache()` 去重不写单测（React 的客户端构建里 `cache` 不做记忆化，测不出来），在本地 dev 的网关请求日志里核对一页只取一次。
- **组件**（Testing Library）：
  - `SharePanel`：`share.open`（默认 GB/T 7714-2015、链接框内容）、`share.format.*`（切格式重取、记住上次）、`share.cite.loading` / `share.cite.error` + 重试、`copy.ok`、`copy.fallback`、`share.export.downloading` + toast、单篇导出失败提示条、文件名用 PMID / 没有 PMID 用文献 ID。
  - `CitationPreview`：三态；`CiteDialog` 现有测试不改、保持绿色。
  - `PaperHeader`：有分享按钮；次按钮组三种组合（全文 + PDF：PDF · 收藏 · 分享；全文无 PDF / 只有 PDF / 无全文：收藏 · 分享）；PMC 链接在次按钮组之后。
  - `LibraryStatusProvider`：出错后窗口聚焦重取一次、成功后按钮恢复；正常时聚焦不重取。
- **e2e**（Playwright，连 mini：内联 `PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528`，`--workers=1`）：
  1. **两账号冒烟**（扩展 `library.spec.ts`「公开收藏夹」那条，不新建第二条动真实账号的用例）：A（已验证）收藏 → 建夹 → 设公开；B（新注册，`delivered+…@resend.dev`）打开公开页 → 分享里复制链接、复制引用 → 导出 RIS（`waitForEvent("download")`，读文件断言以 `TY  - ` 开头）→ 复制为我的收藏夹；同一条里用 `request.get()` 取公开页 HTML，断言标题、描述、canonical。B 的上下文用 `addInitScript` 让 `navigator.clipboard.writeText` 拒绝，逼出 `execCommand` 那一步（模拟 HTTP），并记录 `copy` 事件里被选中的文字，断言复制出的是规范地址 / 引用文本。原有的关公开 → 404 与 `finally` 收尾不变。
  2. **详情页分享面板**（匿名，同样模拟 HTTP）：打开分享 → 链接框是 `http://<host>/papers/<id>`、复制链接 → `已复制`；切 APA → 复制引用 → `已复制`；点 RIS → 下载以 `TY  - ` 开头。
  3. **页面信息**：`request.get()` 取一篇文献页、一个期刊页的 HTML，断言 `<title>`、`<meta name="description">`、`<link rel="canonical">` 指向本页绝对地址。
  4. 两种尺寸（1440×900、375×812）截图核对详情页页头与分享面板（本地验收用）。
- **真 HTTP 的证据**：Playwright 的 `localhost` 是安全上下文，剪贴板接口直接成功，e2e 只能模拟。本地验收再做一次真 HTTP：`next build` + `next start` 后，用本机的 tailscale 地址（`http://100.70.109.37:<port>`，非安全上下文）跑 e2e 2 一次，截图写进验收报告。合并部署后 mini 上的 D10 复核由版本收尾做。
- **门禁**：`pnpm lint`、`pnpm typecheck`、`pnpm test` 全绿；`pnpm build` 跑一次（`generateMetadata` 里有 `try/catch`，只有 build 会暴露被吞的动态渲染信号）。

---

## 7. 我定的设计点（看到不对就推翻）

1. 带 Open Graph 文字标签，不带 `og:image`（§2.2）。
2. 期刊页描述只用刊名 + `issnL`（§2.2）。
3. 收藏夹页 `noindex` 的判定：取不到所有者的收录设置时一律 `noindex`（§2.3）。
4. 不改 `htmlLimitedBots`，元数据按 Next 默认流式输出（§2.4）。
5. 单篇导出不记上次的格式（§3.2）。
6. 公开收藏夹页的分享浮层不改成 `SharePanel`（§1.2）。
7. 抽出 `CitationPreview` 与导出格式表，`CiteDialog`、`ExportDialog`、`SharePanel` 共用（§3.3）。
8. 收藏夹标题没有昵称时写 `<名称> · 收藏夹 · Patra`（§2.2）。

## 8. 风险

- **元数据流式输出**：curl 默认 UA 拿到的标签在 `<body>` 末尾。只读 HTML、又不在 Next 默认名单里的预览爬虫（例如部分国内聊天软件的链接预览）会读不到。门户现在只在 tailnet 内，没有公开用户，本版接受。
- **收藏夹页元数据多一次 identity 调用**：只在 `generateMetadata` 里、只取一次（`fetchPublicProfile` 已有 `cache()`），在 tailnet 内可接受。

## 9. 已确认的口径

1. **D11「自动恢复」**（2026-10-26 规划会话确认）：服务恢复后停在原页面不动，按钮自己恢复，按 §5 第 3 条实现；「下次进页面恢复」不满足 D11。

## 10. 验收对照（PAP-115 AC）

| AC | 证据 |
|---|---|
| 用 curl 取三类页面的 HTML，标题、描述、canonical 正确 | e2e 1、3；本地 curl 记录（默认 UA 与 `facebookexternalhit`） |
| 在 mini（HTTP）上复制链接与复制引用都能成功 | e2e 1、2（模拟 HTTP）；本机 tailscale 地址真 HTTP 跑一次；合并部署后 D10 复核 |
| 冒烟用例在 mini 门户上通过 | e2e 1 连 mini 跑通 |
