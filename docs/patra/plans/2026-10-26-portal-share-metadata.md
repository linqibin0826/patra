# 门户「分享与页面信息」实施计划（PAP-115）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（本仓库约定内联执行，不派子代理）。Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 文献页、期刊页、公开收藏夹页带标题 / 描述 / canonical / OG；详情页有分享面板（复制链接、复制引用、导出这篇，HTTP 下降级复制）；窄屏页头方案 A；「我的状态」出错后页面内自愈；两账号冒烟连 mini 跑通。

**Architecture:** 元数据由 `src/lib/page-metadata.ts` 的纯函数从已取到的数据构造，三个页面的 `generateMetadata` 与页面共用 `cache()` 包过的取数函数；分享面板是 PAP-114 现成件（`CopyLinkField`、`CitationFormatTabs`、`CopyButton`、引用 / 导出转发路由）的拼装，抽出 `CitationPreview` 与导出格式表共用。

**Tech Stack:** Next.js 16 App Router（webpack）、React 19、TypeScript strict、Tailwind v4、base-ui、TanStack Query v5、Vitest + Testing Library、Playwright。

**Spec:** `docs/patra/specs/2026-10-26-portal-share-metadata-design.md`（页面与文案：`patra-portal/docs/design-briefs/v0.13/library.md` §3.3、§3.4、§3.8、§3.14、§3.18、§4.2）

## Global Constraints

- 所有命令在 `patra-portal/` 下跑：`pnpm test`、`pnpm typecheck`、`pnpm lint`（`pnpm exec biome check --write .` 自动修）。
- **不提交、不推送**：用户本地验收（桌面 1440×900 + 手机 375×812 截图）通过后才 commit，提交尾注 `Refs PAP-115`；推送与 PR 由规划会话做。各任务的收尾是「测试全绿」。
- 文案逐字照简报：`分享`、`链接`、`引用`、`导出`、`复制链接`、`复制引用`、`已复制`、`引用暂时生成不了`、`重试`、`RIS 可导入 Zotero、EndNote、NoteExpress`、`准备中…`、`已开始下载 <文件名>`、`导出没有成功，请稍后再试`。
- 标题后缀一律 ` · Patra`；描述截断按字素（`graphemes`），截断时加 `…`；文献页 160、收藏夹页 120。
- canonical / `og:url` / 复制链接都用 `publicUrl(path)` 的绝对地址，不用 `window.location`。
- 不放 `og:image`；不做二维码、系统分享、短链。
- 颜色只用语义 token 与现有色阶类；不写 inline `style`；RSC 里的 `try/catch` 先 `unstable_rethrow(error)`。
- 测试：单测 `// @vitest-environment node`；组件测试 `getByRole` 优先；禁止快照、禁止裸 `setTimeout`。

## Review Focus

1. **摘要里有内联标记（`<i>`、`<sup>`）或换行**：描述是去掉标记、空白压成一个的纯文本 → Task 1 `strips inline markup and collapses whitespace`。
2. **中文 / emoji 摘要恰好在截断边界**：按字素截，不劈开代理对，末尾 `…` → Task 1 `truncates by grapheme with ellipsis`。
3. **identity 在收藏夹页元数据阶段挂掉**：页面照常（主体有自己的降级），元数据 `noindex`，不进错误页 → Task 2 `falls back to noindex when identity throws`。
4. **文献既无摘要、又无期刊 / 日期 / 作者**：描述为空串时不输出 `description` 键（不出现空的 meta）→ Task 1 `omits description when nothing to say`。
5. **分享面板导出连点**：下载中四个按钮都禁用，不发第二个请求 → Task 4 `disables all formats while downloading`。

---

### Task 1: 元数据纯函数

**Files:**
- Create: `src/lib/page-metadata.ts`
- Test: `tests/lib/page-metadata.test.ts`

**Interfaces:**
- Produces:
  - `paperMetadata(paper: PaperDetail, canonical: string): Metadata`
  - `journalMetadata(venue: VenueDetail, canonical: string): Metadata`
  - `collectionMetadata(collection: PublicCollection, canonical: string, indexable: boolean): Metadata`

- [ ] **Step 1: 写失败的测试**（夹具用 `tests/fixtures` 里现有的 `PaperDetail` / `VenueDetail` 构造器，没有就在测试文件里写最小对象）
  - `paper title uses short main title`：`title: "Heart failure: a review."` → `title` 为 `Heart failure · Patra`、`openGraph.title` 为 `Heart failure`、`openGraph.type` 为 `article`、`openGraph.siteName` 为 `Patra`。
  - `uses structured abstract sections joined`：两段 `text` 为 `A.`、`B.` → 描述 `A. B.`。
  - `strips inline markup and collapses whitespace`：`abstractPlainText: "<i>E. coli</i>\n\n infection"` → `E. coli infection`。
  - `truncates by grapheme with ellipsis`：201 个「心」→ 描述 160 个「心」+ `…`；emoji 串同理（字素数 161）。
  - `falls back to journal, date and first three authors`：无摘要、`venueName: "Lancet"`、`publishedDate: "2019-03"`、4 位作者 → `Lancet · 2019 年 3 月 · A, B, C 等`。
  - `omits description when nothing to say`：无摘要、无期刊、无日期、无作者 → 结果对象没有 `description` 键。
  - `canonical and og url are the absolute address`：`alternates.canonical` 与 `openGraph.url` 都等于传入的 `http://h:4010/papers/1`。
  - `journal description with issn`：`title: "Lancet", issnL: "0140-6736"` → `Lancet（ISSN 0140-6736）`；无 `issnL` → `Lancet`；`openGraph.type` 为 `website`。
  - `collection title and description`：`name: "组会"`、昵称 `陈宇`、简介 null、`itemCount: 23` → 标题 `组会 · 陈宇的收藏夹 · Patra`、描述 `陈宇在 Patra 公开的收藏夹，共 23 篇文献。`；有简介时取前 120 字素；`owner: null` → `组会 · 收藏夹 · Patra` 与 `Patra 上公开的收藏夹，共 23 篇文献。`。
  - `collection noindex follows owner`：`indexable=false` → `robots: { index: false }`；`true` → 没有 `robots` 键。

- [ ] **Step 2: 跑 `pnpm test tests/lib/page-metadata.test.ts`，预期 FAIL（模块不存在）**

- [ ] **Step 3: 实现三个函数**：标题主干用 `shortTitle`；纯文本用 `parseInlineMarkup` + `inlinePlainText`，再 `.replace(/\s+/g, " ").trim()`；日期用 `formatPublishedDate` 回落 `publicationYear`；作者按 `order` 排序取前 3 个 `name`；描述为空串时不放 `description` 与 `openGraph.description`。

- [ ] **Step 4: 跑同一命令，预期 PASS**

### Task 2: 三个页面的 `generateMetadata` + `cache()` 去重

**Files:**
- Modify: `src/lib/portal-api/publications.ts`（`fetchPublicationDetail` 改为 `export const fetchPublicationDetail = cache(async (id: string) => …)`）
- Modify: `src/lib/portal-api/venues.ts`（`fetchVenueDetail` 同上）
- Modify: `src/lib/interaction/collections.ts`（`fetchPublicCollection` 同上）
- Modify: `src/app/papers/[id]/page.tsx`、`src/app/journals/[id]/page.tsx`、`src/app/collections/[id]/page.tsx`（加 `generateMetadata`；收藏夹页删掉注释里「由 PAP-115 加」）
- Test: `tests/app/papers-page.test.tsx`（已有，加用例）、`tests/app/journal-page.test.tsx`（新建）、`tests/app/collection-page.test.tsx`（已有，加用例）

**Interfaces:**
- Consumes: Task 1 的三个构造函数；`publicUrl(path)`；`fetchPublicProfile(username)`（`src/lib/identity/public-profile.ts`）。

- [ ] **Step 1: 写失败的测试**（mock 取数函数与 `@/lib/public-url`，照 `tests/app/user-page.test.tsx` 的写法调 `generateMetadata({ params: Promise.resolve({ id }) })`）
  - 文献页 `returns paper metadata with absolute canonical`：`publicUrl` 返回 `http://h/papers/1` → `alternates.canonical` 为它、标题以 ` · Patra` 结尾；`returns empty when paper missing`：取数返回 `null` → `{}`。
  - 期刊页同两条。
  - 收藏夹页：`empty on not-found` → `{}`；`unavailable` → `{ title: "收藏夹暂时打不开 · Patra", robots: { index: false } }`；`indexable owner` → `fetchPublicProfile` 返回 `{ kind: "found", profile: { indexable: true, … } }` → 无 `robots`；`noindex when owner hides from search`（`indexable: false`）；`noindex when owner deleted or null`（不调 `fetchPublicProfile`）；`falls back to noindex when identity throws`（`fetchPublicProfile` reject → 结果 `robots.index === false`，不抛）。

- [ ] **Step 2: 跑这三个测试文件，预期 FAIL**

- [ ] **Step 3: 实现**：三个取数函数包 `cache()`（`import { cache } from "react"`）；收藏夹页私有函数 `ownerIndexable(owner: Owner | null): Promise<boolean>`，`catch` 里先 `unstable_rethrow(error)`，再 `logRequestError("/patra-identity/users/<username>", error)`，返回 `false`。

- [ ] **Step 4: 跑这三个测试文件与 `pnpm test tests/lib/portal-api tests/lib/interaction`，预期 PASS**

- [ ] **Step 5: 本地核对**：dev 服务（4010，连 mini）下 `curl -s localhost:4010/papers/<id> | grep -o '<title>[^<]*\|rel="canonical"[^>]*\|name="description" content="[^"]\{0,40\}'`，再加 `-A "Googlebot"` 各一次，记录标签在 `<head>` 还是 `<body>` 末尾；dev 日志里这页的 catalog 详情请求只有一次。

### Task 3: 抽出 `CitationPreview` 与导出格式表

**Files:**
- Create: `src/components/library/CitationPreview.tsx`、`src/lib/library/export-formats.ts`
- Modify: `src/components/library/CiteDialog.tsx`、`src/components/library/ExportDialog.tsx`
- Test: `tests/components/library/CitationPreview.test.tsx`；`tests/components/library/CiteDialog.test.tsx`、`ExportDialog.test.tsx` 不改、保持绿色

**Interfaces:**
- Produces:
  - `CitationPreview(props: { lines: string[]; state: "loading" | "error" | "ready"; onRetry: () => void; className?: string; ref?: Ref<HTMLDivElement> })`
  - `EXPORT_FORMATS: readonly { id: ExportFormat; name: string; ext: string; hint: string }[]`、`type ExportFormat = "ris" | "bibtex" | "csv" | "csl-json"`（值照 `ExportDialog` 现有常量原样搬过去）

- [ ] **Step 1: 写失败的测试**：`shows two skeleton lines while loading`（无文字、`aria-hidden` 骨架）；`shows error with retry`（`引用暂时生成不了`，点 `重试` 调 `onRetry`）；`renders one paragraph per line`。
- [ ] **Step 2: 跑，预期 FAIL**
- [ ] **Step 3: 实现并让 `CiteDialog`（预览框高 `max-h-90`，经 `className` 传入）、`ExportDialog` 改用它们**
- [ ] **Step 4: 跑 `pnpm test tests/components/library`，预期全部 PASS**

### Task 4: `SharePanel` 与 `ShareButton`

**Files:**
- Create: `src/components/library/SharePanel.tsx`（`SharePanel` 与 `ShareButton` 同文件，`ShareButton` 持有浮层开关）
- Test: `tests/components/library/SharePanel.test.tsx`

**Interfaces:**
- Consumes: `CopyLinkField`、`CitationFormatTabs` / `CITATION_STYLE_KEY` / `CITATION_STYLE_IDS`、`CitationPreview`、`CopyButton`、`EXPORT_FORMATS`、`saveResponseAsFile`、`readChoice` / `writeChoice`、`ResponsivePopover`、`FormAlert`。
- Produces: `ShareButton(props: { paperId: string; pmid: string | null; link: string; className?: string })`、`SharePanel(props: { paperId: string; pmid: string | null; link: string })`

- [ ] **Step 1: 写失败的测试**（mock `fetch`，照 `CiteDialog.test.tsx` 的 QueryClient 包装）
  - `share.open`：点 `分享` → 对话层里有 `链接` 输入框值为传入的 `link`；格式默认 `GB/T 7714-2015` 选中；请求体 `{ ids: ["1"], style: "gb-t-7714-2015" }`；预览出现 catalog 返回的 `text`。
  - `share.format`：选 `APA` → 新请求 `style: "apa-7"`，`localStorage["patra.citation-style"]` 为 `"apa-7"`；预存 `"ama-11"` 时打开即选中 AMA。
  - `share.cite.loading` / `share.cite.error`：未返回时骨架、`复制引用` 禁用；500 → `引用暂时生成不了`，点 `重试` 再请求一次。
  - `copy.ok`：`navigator.clipboard.writeText` resolve → `复制引用` 变 `已复制`，写入的是 `text`（不带 `label`）。
  - `copy.fallback`：`writeText` reject、`document.execCommand` 返回 true → `已复制`。
  - `share.export.downloading`：点 `RIS` → 该按钮 `准备中…`；`disables all formats while downloading`（另三个 `disabled`，再点不发请求）；完成 → toast `已开始下载 patra-31535829-2026-10-24.ris`（mock 响应头 `Content-Disposition`），请求体 `{ ids: ["1"], format: "ris", name: "31535829" }`；`pmid: null` 时 `name: "1"`。
  - `export failure`：500 → 面板顶部 `导出没有成功，请稍后再试`，按钮恢复。
- [ ] **Step 2: 跑，预期 FAIL**
- [ ] **Step 3: 实现**：版式照简报 §3.4（三块小标题 `font-mono text-2xs tracking-caps text-fg-3 uppercase`，同 `PublicCollectionActions`；浮层 `w-100`、`align="start"`；预览 `max-h-36`）；`ShareButton` 是 `btnSecondary` + `Share2`（15px）+ `分享`，`data-open` 时 `bg-paper-200`。
- [ ] **Step 4: 跑，预期 PASS**

### Task 5: 详情页操作行：分享按钮 + 窄屏方案 A

**Files:**
- Modify: `src/components/portal/paper-detail/FullTextActions.tsx`（拆成 `FullTextPrimaryAction` 与 `FullTextPdfAction` 两个导出，删原 `FullTextActions`，调用方一起改）
- Modify: `src/components/portal/paper-detail/PaperHeader.tsx`（加 `shareLink: string` prop；操作行结构见 spec §4）
- Modify: `src/components/portal/paper-detail/PublicationDetailView.tsx`、`src/app/papers/[id]/page.tsx`（页面 `await publicUrl(\`/papers/${id}\`)` 往下传 `shareLink`）
- Modify: `src/components/library/SaveButton.tsx`、`src/components/portal/fulltext/PdfDownloadLink.tsx`（加可选 `className`；`PdfDownloadLink` 的大小在 `max-[540px]:hidden`）
- Test: `tests/components/portal/paper-detail/PaperHeader.test.tsx`、`PublicationDetailView.test.tsx`（补 `shareLink`）、其他引用 `FullTextActions` 的测试

**Interfaces:**
- Consumes: Task 4 的 `ShareButton`。
- Produces: `FullTextPrimaryAction({ paper })`、`FullTextPdfAction({ paper, className? })`；`PaperHeader({ paper, fullTextFacts, shareLink })`；`PublicationDetailView` 多一个 `shareLink: string` prop。

- [ ] **Step 1: 写失败的测试**（`PaperHeader.test.tsx`）
  - `has share button`：`getByRole("button", { name: "分享" })`。
  - `secondary group with pdf`：全文 + PDF → 名为 `次要操作` 的 `group` 里依次是 `下载 PDF`、收藏、`分享`；主按钮 `阅读全文` 不在组里。
  - `secondary group without pdf`：全文无 PDF、只有 PDF、无全文三种 → 组里只有收藏、`分享`；只有 PDF 时 `下载 PDF` 是组外主按钮。
  - `pmc link after group`：PMC 链接在 DOM 顺序上位于组之后。
- [ ] **Step 2: 跑，预期 FAIL**
- [ ] **Step 3: 实现**：次按钮组 `<div role="group" aria-label="次要操作" className="contents max-[540px]:grid max-[540px]:grid-flow-col max-[540px]:auto-cols-fr max-[540px]:gap-2">`；三个次按钮传同一串紧凑类 `max-[540px]:h-12 max-[540px]:flex-col max-[540px]:gap-1 max-[540px]:px-2 max-[540px]:text-xs`；主按钮在 ≤ 540px 整行（现有 `max-[540px]:flex-col max-[540px]:items-stretch` 保留）。
- [ ] **Step 4: 跑 `pnpm test tests/components/portal tests/app`，预期 PASS**

### Task 6: 「我的状态」出错后页面内自愈

**Files:**
- Modify: `src/components/library/LibraryStatusProvider.tsx`
- Test: `tests/components/library/LibraryStatusProvider.test.tsx`

（D11 口径 2026-10-26 已确认：停在原页面、服务恢复后按钮自己恢复。）

- [ ] **Step 1: 写失败的测试**：`refetches on focus after an error`：第一次 502 → `unavailable`；第二次 200 → 触发 `focusManager.setFocused(true)`（从 `@tanstack/react-query` 导入）后 `findBy` 到 `ready`；`does not refetch on focus when healthy`：成功后聚焦，`fetch` 调用次数不变。
- [ ] **Step 2: 跑，预期 FAIL**
- [ ] **Step 3: 实现**：`useQuery` 加 `refetchOnWindowFocus: (query) => query.state.status === "error"`、`refetchOnReconnect` 同。
- [ ] **Step 4: 跑，预期 PASS**

### Task 7: e2e 冒烟

**Files:**
- Modify: `tests/e2e/library.spec.ts`（扩展「公开收藏夹」用例；新增「详情页分享面板」用例）
- Modify: `tests/e2e/paper-detail.spec.ts` 或新建 `tests/e2e/page-metadata.spec.ts`（文献页、期刊页元数据）
- Modify: `tests/e2e/support/library.ts`（加 `simulateInsecureClipboard(context)` 与 `readCopied(page)`）

**Interfaces:**
- Produces: `simulateInsecureClipboard(context: BrowserContext): Promise<void>`（`addInitScript`：`navigator.clipboard.writeText` 改为 reject `NotAllowedError`；监听 `copy` 事件，把 `getSelection()` 的文字（输入框取 `value.slice(selectionStart, selectionEnd)`）写进 `window.__patraCopied`）；`readCopied(page: Page): Promise<string>`。

- [ ] **Step 1: 扩展两账号用例**：B 的上下文先 `simulateInsecureClipboard`；B 打开公开页后 → `分享` → `复制链接` → `已复制`，`readCopied` 等于 `${baseURL}/collections/${id}`；`复制引用` 对话框 → `复制 1 条引用` → `已复制`，`readCopied` 非空；`导出` → `下载 .ris` → `waitForEvent("download")`，读文件以 `TY  - ` 开头；`request.get(/collections/${id})` 的 HTML 含 `<title>${name} · ` 、`rel="canonical" href="${baseURL}/collections/${id}"`。其后照旧复制为我的收藏夹、关公开、404、收尾。
- [ ] **Step 2: 新增「详情页分享面板」**（匿名、`simulateInsecureClipboard`）：从检索结果进第一篇详情 → `分享` → 链接框值为 `${baseURL}/papers/<id>`、`复制链接` → `已复制` 且 `readCopied` 相同；选 `APA` → `复制引用` → `已复制`；点 `RIS` → 下载以 `TY  - ` 开头。
- [ ] **Step 3: 新增页面信息用例**：检索结果第一篇文献与其期刊 → `request.get` 的 HTML 含 ` · Patra</title>`、`name="description"`、`rel="canonical" href="${baseURL}/papers/<id>"` / `/journals/<id>`。
- [ ] **Step 4: 跑**：`set -a; source ~/.patra/secrets/e2e.env; set +a; env -u HTTP_PROXY -u HTTPS_PROXY -u ALL_PROXY -u http_proxy -u https_proxy -u all_proxy PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm exec playwright test tests/e2e/library.spec.ts tests/e2e/page-metadata.spec.ts --workers=1`（baseURL 指 worktree 的 4010 dev 服务，见 Task 8），预期全部 PASS、无 skip（除 catalog 不可达）。

### Task 8: 门禁、真 HTTP 核对与验收材料

- [ ] **Step 1**：`pnpm lint`、`pnpm typecheck`、`pnpm test` 全绿。
- [ ] **Step 2**：`pnpm build`（剥代理）通过，确认没有动态渲染信号被吞的报错。
- [ ] **Step 3: 真 HTTP**：`next start -p 4011 -H 0.0.0.0`（连 mini），Playwright 以 `http://100.70.109.37:4011` 为 baseURL 跑 Task 7 Step 2（不加 `simulateInsecureClipboard`），确认 `已复制`。
- [ ] **Step 4: 截图**：1440×900 与 375×812 的详情页页头、分享面板（含 `已复制`）、窄屏三种操作行组合，交给用户验收。
- [ ] **Step 5（验收通过后）**：`git add` 本 Issue 的文件、`git diff --cached` 核对，提交 `feat(portal): 分享面板、页面信息与窄屏页头` + `Refs PAP-115`。
