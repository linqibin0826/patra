# 全文阅读门户 Implementation Plan（PAP-88）

**Goal:** 文献详情页 `/papers/[id]` 直接阅读 PMC 开放获取全文（目录、图、表、公式、引用跳转、参考文献、许可与出处），能经门户下载原版 PDF；没有全文时说清原因、保留去原文的出口。

**依据:** 设计简报 `patra-portal/docs/design-briefs/v0.11/fulltext-reader.md`（下称简报，版式以 §3.4 为准）；后端 spec `docs/patra/specs/2026-10-19-fulltext-reader-backend-design.md` §6.2（正文结构）、§7（接口）；release spec D4 / D5 / D6 / D8。

**Architecture:**

- 详情页 RSC 先取详情，按 `hasFullText` / `hasPdf` / `pmcid` / 文献类型派生全文状态（`deriveFullTextState`）。题录头、侧栏、关键标识按状态换按钮。
- 全文走独立接口，`fetchPublicationFullText` 用 `React.cache` 去重（全文区块、桌面目录卡、移动端目录按钮三处共用一次请求），zod 校验正文结构。三处各包一个 `Suspense`，详情先出、全文后到；全文请求失败只影响全文区块（「全文没能加载」），不打到全局错误页。
- 正文渲染是纯函数（`renderFullTextBody`）：按白名单把 Block / Inline 转成 React 元素，同一遍里给每个参考文献引用编号（`cite-N`），记下每条参考文献第一次被引用的位置，供「↩ 回到正文」使用。交叉引用的目标不在本篇时降级为普通文字。
- MathML 字符串由 `parseMathml` 解析：标签白名单（mathml-tags.txt 的 21 个 + `mroot` / `menclose` / `mphantom` / `mmultiscripts` / `mprescripts` / `none` / `semantics`）、属性白名单，`mfenced` 改写为 `mrow` + `mo`，`annotation` 丢弃；摘要的 `RichInlineText` 共用同一份标签白名单。
- 交互（客户端组件）：图片放大层、长表展开、参考文献展开 + 目标高亮 + 回到正文、目录当前章节（IntersectionObserver）、移动端目录面板。共享状态（参考文献展开、高亮目标、回到正文的来源锚点）放 Zustand `src/store/fulltext-reader.ts`。
- PDF 与图片：同源 Route Handler `/api/papers/[id]/pdf`、`/api/papers/[id]/figures/[file]` → `gatewayStream`（只对等到响应头计时，正文流式转发，浏览器断开时取消上游）→ 网关 → catalog；透传 `Content-Type` / `Content-Length` / `ETag` / `Cache-Control` / `Content-Disposition`，`If-None-Match` 原样带上。

## 任务

1. **契约与派生**：`PaperDetail` 加 `hasFullText` / `hasPdf` / `pdfSizeBytes`；`src/types/fulltext.ts` + zod schema；`deriveFullTextState`、`formatFileSize`、`formatFetchedDate`、`isSupplementarySection`、目录派生 `deriveToc`、统计行派生。测试：`tests/lib/portal-api/fulltext-derive.test.ts`。
2. **取数**：`fetchPublicationFullText`（404 → `null`，其余失败抛出，zod 失败抛出）。测试：`tests/lib/portal-api/fulltext.test.ts`。
3. **二进制转发**：`gatewayStream` + 两个 Route Handler。测试：`tests/lib/gateway.test.ts`、`tests/app/api/papers-fulltext-files.test.ts`（非法 id / 文件名 404、头透传、304、上游 5xx → 502、请求带 `If-None-Match`）。
4. **MathML 白名单**：`src/lib/mathml.ts`（解析、`mfenced` 改写）+ `MathMl` 组件；`rich-inline-text.ts` 改用共享标签集。测试：`tests/lib/mathml.test.ts`、`tests/components/portal/fulltext/MathMl.test.tsx`。
5. **正文渲染**：`renderFullTextBody`（段落、标题层级、列表、引文框、文本框、代码、图、表、公式、行内节点、xref 降级、补充材料整节跳过）。测试：`tests/components/portal/fulltext/FullTextBody.test.tsx`。
6. **图与表的交互**：`FigureBlock` + `FigureLightbox`、`TableBlock` 长表展开 / 收起。测试：各自组件测试。
7. **参考文献与版权页**：`ReferenceList`（前 5 条 / ≤ 8 条全显示、展开全部、高亮、回到正文）、`LicenseColophon`。测试：组件测试。
8. **全文区块与状态面板**：`FullTextSection`（区块头、统计行、出处条、正文、参考文献、版权页）、`FullTextUnavailable`（三种原因 + 只有 PDF + 加载失败）、`FullTextSkeleton`。测试：组件测试。
9. **目录**：`FullTextToc`（桌面吸顶卡、当前章节）、`FullTextTocSheet`（面包屑「目录」按钮 + 底部面板）。测试：组件测试。
10. **接入详情页**：题录头按钮、撤稿胶囊、侧栏（操作卡、速览、目录卡、吸顶方式）、关键标识 PMCID、`DetailCrumbs` 右端插槽、`Suspense` 编排。测试：`tests/components/portal/paper-detail/*.test.tsx`。
11. **e2e**：`tests/e2e/fulltext-reader.spec.ts`（有全文：阅读全文 → 目录跳转 → 引用跳参考文献 → 回到正文 → 图片放大层 → 下载 PDF；无全文：说明面板 + 去全文；移动端目录面板）；`detail-rail-sticky.spec.ts` 补有全文时目录卡吸顶不晃。真实数据走 mini 网关。
12. **验收**：`pnpm lint`、`pnpm typecheck`、`pnpm test`、Playwright；mini 数据两种尺寸逐场景截图核对；最大 PDF 经门户下载核 md5（D6）。

## mini 样例（2026-10-19 查库）

| 场景 | publicationId | PMCID |
|------|---------------|-------|
| 有全文 + PDF（221 图） | 352303012816019646 | PMC12858081 |
| 有全文无 PDF | 352303012811825373 | PMC12831157 |
| PMC 收录但不能转载 | 352303012828603189 | PMC12879293 |
| 不在开放获取子集 | 352303012803436544 | — |
| 公式 + 矩阵 | 352303012807630861 | — |
| `mfenced` + 文本框 | 352303012807630907 | — |
| 代码块 | 352303012807631056 | — |
| 公式图片 | 352303012828603253 | — |
| 正文前导块 | 352303012807630925 | — |
| 不分节 | 352303012807631017 | — |
| 作者手稿 | 352303072500966077 | PMC12884591 |
| 许可为空 | 352303094936298457 | PMC8168327 |
| 最大 PDF（155.7 MB，md5 `fdbf75f9…`） | 352303052032763159 | PMC12833634 |

库里还没有「有 PDF 无正文」（`NO_BODY`）和带 `Retracted Publication` 类型的文献，这两个状态由组件测试覆盖。
