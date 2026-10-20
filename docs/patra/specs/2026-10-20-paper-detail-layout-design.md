# 文献详情页排版重设计 工程 spec（PAP-89）

> **Issue**：[PAP-89](https://linear.app/papertrace/issue/PAP-89)（不挂版本）
> **设计稿**：Design 画布「Patra 文献详情页重设计」https://claude.ai/artifact/PwmifxFVFZwrEmq1E5WUBJ ，5 张画板：01 首屏、02 正文、03 文末、04 长标题规则、05 插图宽度规则（2026-10-20 定稿）
> **前置设计**：v0.11 设计简报 [fulltext-reader.md](../../../patra-portal/docs/design-briefs/v0.11/fulltext-reader.md)、[v0.11 门户 plan](../plans/2026-10-19-fulltext-reader-portal.md)
> **日期**：2026-10-20
> **状态**：草稿，待评审

**画板与本 spec 不一致时以本 spec 为准**。有意偏离画板的地方：图表标签照原文（§5.9）、许可与出处保留全部内容（§5.14）、不做「本文」行高亮与「在 Patra 打开」（§3.2）。

## 1. 要解决的问题

v0.11 上线全文阅读后，在 PMID 41575322（线上 id `352303012807630907`）这类长标题、带全文的文献上，详情页有七个排版问题：

1. 长标题占 7 行。标题里冒号后面那半句和主标题一样大，首屏几乎全是标题。
2. 「阅读全文 / 下载 PDF / 收藏」在页头、侧栏操作卡、窄屏落下来的侧栏里各出现一次。
3. 摘要显示成「ObjectiveEsophageal cancer is…」。
4. 图片撑满主栏，正文却只有 36em，两边右缘对不齐，正文右侧空着一大块。
5. 引用号和前面的字之间隔着一个空格。
6. 参考文献作者名是「LiJ XuJ ZhengY」。
7. 「深度数据」折叠区压在正文前面；文末 8 个声明各占一个一级标题，目录很长。

## 2. 查实的事实（决定了方案边界）

- **问题 3 是源数据的问题。** PubMed 原始 XML 里这篇的 `<AbstractText>` 只有一段，没有 `Label` 属性，正文就是 `ObjectiveEsophageal…Methods The…`，标签和正文之间没有任何分隔，库里存的是 `UNSTRUCTURED` 纯文本。mini 上 10,558 条非结构化摘要里，有 97 条以这种粘连标签开头（约 0.9%）。
- **问题 6 已经有修复。** 分支 `fix/catalog-reference-authors`（24262cace，还没合入 main）在 JATS 解析里补上了人名的空格和逗号，并把 PMC 补进著录文本的 PMID / PMCID 去掉，`PARSER_VERSION` 升到 2，已入库的全文会重新解析。本 spec 不再处理作者名。
- **图片宽高后端已经有了。** 抓取任务用 `ImageIoDimensionAdapter` 读出宽高，全文接口的 `figure.width / height` 有值。这篇 11 张图宽都在 700–753px，宽高比 0.79–2.68，五类宽度全都用得上。
- **引用号本身在 `<sup>` 里。** 正文结构是 `text("smoking. ") → style(sup) → xref(bibr, "8")`，空格在 `sup` 外面的文本节点末尾。
- **v0.11 决策 C 仍然有效。** 只做格式转换，不改写原文：图表标签（`Figure 1.`）、图注、著录文本都照原文。画板上写的「图 1」「表 10」是中文示意，落地时显示原文标签，见 §5.9。
- **事实条一半的数据在全文接口里。** 详情接口有证据等级、被引、收藏、来源、阅读时长；篇幅（词数）、图表数、参考文献数只能从全文接口来（详情的 `numberOfReferences` 这篇是 0）。页面已经把全文放在 Suspense 里流式给出，事实条的这几项也要跟着流式给出。

## 3. 范围

### 3.1 做

画板 01–05 上的所有改动，具体见 §5，改 `patra-portal`，一个前端 PR。

### 3.2 不做

- 超宽图通栏 1184px（画板 05 的 C2 方案）。
- 参考文献的「在 Patra 打开」和标题旁的「在 Patra 收录 n 篇」：要后端把参考文献的 PMID 对到站内文献，是新能力。
- 表格里「本文」一行的浅色高亮：画板上那一行是针对这篇内容画的，没有通用规则能认出哪一行是本文。
- 作者名格式（由 `fix/catalog-reference-authors` 处理）。

### 3.3 后端部分

问题 3 在 catalog 的 PubMed 解析里修（§5.17、§8），单独一个后端 PR。

## 4. 页面结构

```text
DetailCrumbs（吸顶；滚过页头后显示短标题 + 当前章节号 + 已读 %）
Masthead（整宽，max-w-page）
  ├─ 刊头行：文献类型 · 开放获取 · 已撤稿
  ├─ 主标题 h1 + 副标题（§5.1）
  ├─ 作者 · 刊名 · 年份
  ├─ 事实条 PaperFacts（§5.2）
  └─ 操作行：阅读全文 / 下载 PDF / 收藏 / 在 PMC 查看原文 ↗（全页唯一一组）
Body（max-w-page，两栏：main + 340px aside）
  main（@container）
    ├─ 摘要（标签栏，§5.4）
    ├─ MeSH · 关键词（标签栏，§5.5）
    └─ #fulltext
         ├─ 区块头：「全文 · FULL TEXT」+ 许可 · 来源 · PMCID · 抓取日期 · 许可与出处 ↓
         ├─ 正文（标签栏，§5.6 – §5.10）
         ├─ 声明与附注（§5.11）
         ├─ 参考文献（§5.12）
         ├─ 文献档案（§5.13）
         └─ 许可与出处（§5.14）
  aside
    ├─ 标识卡：DOI / PMID / PMCID + 复制按钮（随页面滚走）
    ├─ AI 速读卡（随页面滚走）
    └─ 目录卡（吸顶，§5.15）
```

没有可读全文时，`#fulltext` 里显示现有的说明面板（`FullTextUnavailable`），后面照样跟文献档案；声明、参考文献、许可三块不出现。

**文献档案放在 `#fulltext` 里面渲染。** 画板上的顺序是参考文献 → 文献档案 → 许可与出处，而许可要用全文接口的数据，所以页面把文献档案作为一个 `ReactNode` 传给 `FullTextSection`，由它排在参考文献和许可之间。没有可读全文时，`FullTextSection` 把它排在说明面板后面。

## 5. 各区规则

### 5.1 标题（画板 04）

纯函数 `splitTitle(title: string): { main: string; sub: string | null; size: "lg" | "md" }`：

1. 去掉末尾的一个英文句点（PubMed 题名惯例加句点；`?`、`!` 保留）。
2. 在第一个「冒号 + 空格」处拆开（`: ` 或全角 `：`）。冒号前面是主标题，后面是副标题。冒号前面少于 2 个词时不拆，避免 `Re: …` 这类拆错。
3. 没有拆开、且整体超过 120 个字符时，`size = "md"`，否则 `"lg"`。

| 档 | 主标题 | 副标题 |
|---|---|---|
| `lg` | serif 500，`clamp(30px, 1.4rem + 1.6vw, 46px)`，行高 1.1，字距 −0.02em，最大宽度 20em，`text-wrap: balance` | serif 400，`clamp(19px, 1rem + 0.5vw, 24px)`，行高 1.38，`fg-2`，最大宽度 34em，`text-wrap: pretty` |
| `md` | serif 500，最大 36px，行高 1.15，最大宽度 24em，balance | — |

标题里有 `<i>` / `<sub>` 等富文本时照旧交给 `RichInlineText`。拆分只按纯文本找冒号的位置，找到后在 `RichInlineText` 渲染前切开原始字符串。如果冒号落在标签里面（极少见），就不拆。`originalTitle`（非英文原题）照旧显示在副标题下方。

### 5.2 事实条

标题、作者行下方的一条 `<dl>`，`grid-template-columns: repeat(auto-fit, minmax(128px, 1fr))`，上下各一条 `border-default` 细线，格与格之间用 `border-subtle` 竖线分隔。`dt` 用等宽 10px `fg-3`，`dd` 用 sans 15px 500，数字等宽。

| 项 | 来源 | 何时显示 |
|---|---|---|
| 证据等级 | `deriveEvidence(...).label` | 总是显示 |
| 被引 | `citationCount ?? 0` | 总是显示 |
| 收藏 | `bookmarks` | 总是显示 |
| 来源 | `source`，前面加一个色点 | 总是显示 |
| 篇幅 | 全文 `wordCount` →「7,633 词」 | 有全文 |
| 原文阅读 | `estimatedReadMin` →「≈ 34 分钟」 | 有值 |
| 图表 | `deriveFullTextStats` →「11 图 · 10 表」，为 0 的一项省掉 | 有全文且不全为 0 |
| 参考文献 | `body.references.length` →「56 篇」 | 有全文且大于 0 |

全文的三项包在 Suspense 里（复用页面已有的那一次全文请求，`fetchPublicationFullText` 已用 `cache` 去重）。fallback 是三个同样大小的 shimmer 格，只在 `paper.hasFullText` 时出现。

原「速览」卡删除。证据徽章（`EvidenceBadge`）从页头删掉，等级已经在事实条里。

### 5.3 操作行

`FullTextActions` + `BookmarkButton` + 「在 PMC 查看原文 ↗」文字链（从侧栏操作卡移过来，条件不变：状态是 `full` 或 `pdf-only` 且有 PMCID）。按钮高 48px。侧栏操作卡删除，`FullTextActions` 的 `block` 形态随之没有调用方，删除。

目录卡底部的「下载 PDF」保留（画板 02 / 03 都有），这是读到一半时的便捷入口，不算重复。

### 5.4 标签栏与摘要

**标签栏几何**（全页共用）：每一行左边是 128px 标签格，右边是内容格，两格间距 32px；文字内容最大 36em（17px 下约 612px）。实现为一个布局组件 `RailRow({ label, children, wide? })`：

- 默认：`grid-cols-[128px_minmax(0,1fr)] gap-x-8`，内容格里的正文段落自己限 36em。
- `wide`：内容横跨标签格和内容格，起点是标签格左缘，宽 `128 + 32 + 612 = 772px`。
- main 的容器宽度小于 580px（`128 + 32 + 420`）时，用 Tailwind 的 `@container` 断点改成单列：标签在上，内容在下，间距 8px。用容器查询而不是视口断点，是因为 main 的宽度还受侧栏影响（980px 以下侧栏落到下面）。

**摘要**：

- 结构化摘要：每段一个 `RailRow`。标签格上面一行是原标签，等宽 11px 500 大写、`clay-700`；下面一行是中文译名，12px `fg-3`，从固定映射表取：BACKGROUND 背景、OBJECTIVE / OBJECTIVES 目的、AIM / AIMS 目的、PURPOSE 目的、METHODS 方法、RESULTS 结果、CONCLUSION / CONCLUSIONS 结论、INTRODUCTION 引言、DISCUSSION 讨论。不在表里的标签只显示原文。段与段之间用 `border-subtle` 分隔，间距 24px。摘要是 PubMed 元数据，不受决策 C 约束，可以加译名。
- 纯文本摘要：一个 `RailRow`，标签格留空。
- 标签粘连的摘要：后端解析时已拆成结构化段落（§5.17），前端不做特殊处理。

区块标题统一改成「摘要 · ABSTRACT」「全文 · FULL TEXT」的形式：4×14px 陶土竖条 + sans 11px 600 字距 0.08em `fg-3`，替换现在的 `SectionEyebrow` 用法（`SectionEyebrow` 组件本身如果还有别处用就保留）。

### 5.5 MeSH · 关键词

从「深度数据」里拿出来，放在摘要后面，一个 `RailRow`，标签是「MESH · 关键词」。

- 先列 MeSH 主题词，主要主题词排在前面，带 ★，再列关键词。都用 32px 高的胶囊样式（`Pill` 加一个 `md` 尺寸，或者直接写一个链接胶囊；现在没有按主题词检索的入口，胶囊不做成链接，用 `<span>`）。
- 默认显示前 8 项，后面是一个虚线边框按钮「另 N 项」，点开后显示全部，不再收起。
- 两类都为空时整行不出现。

### 5.6 正文章节

- 一级章节：标签格是两位序号 `01`、`02`（等宽 13px 500 `clay-700`），标题 serif 30px 500、行高 1.12、字距 −0.02em。上间距 96px（第一个章节 48px）。
- 二级章节：标签格是 `4.5` 这样的序号（等宽 12px `fg-3`），标题 serif 24px 500。上间距 48px。
- 三级及以下：不编号，标题 sans 15px 600（同现在），放在内容格里。
- **序号的来源**：纯函数 `numberSections(sections)`，只编正文 `sections` 里有标题、不是补充材料的一级和二级章节，不编 `back`。
  - 这些章节**全部**以 `1`、`1.`、`2.1`、`2.1.` 这类序号开头时（JATS 里常见），用原文序号：把序号从标题文字里拿掉，放到标签格。这只是把序号移到标签栏，属于格式转换。
  - 只要有一个没带序号，就全部按位置编号（一级 1、2、3…，二级 `4.1`、`4.2`…），标题文字一律不动，原文自带的序号留在标题里。
  - 一级序号显示时补成两位（`1` → `01`，10 以上照写）；二级照写（`4.5`）。
- 正文段落：连续的段落、列表、引文框放在同一个 `RailRow` 里，标签格留空。段距改为 1.1em（画板值）。
- 目录用同样的序号（§5.15）。

### 5.7 引用号

- 正文里 `sup` 里只有参考文献引用（`xref bibr`，以及它们之间的逗号、连字符、空白）时，去掉紧挨在这个 `sup` 前面的文本节点末尾的空白。只处理这一种结构，原文用方括号或作者-年份写法的引用照原文显示，不动。
- 引用链接样式：`sup` 里的引用用等宽 11px 500 `clay-700`；不在 `sup` 里的引用（方括号、作者-年份）保持正文字号，只把颜色设为 `clay-700`。两种都没有下划线，hover 或聚焦时显示 `link-draw`。弹出卡片对所有 bibr 引用都生效。
- **点击弹出参考文献卡片**，不再直接跳到文末：
  - 卡片宽 340px，白底、`border-default`、圆角 12px、阴影，定位在引用号下方，碰到视口边缘时自动翻转、平移。用 Base UI 的 `Popover`。页面上只挂一个 `CitationPopover` 客户端组件，沿用 `ReferenceList` 现有的事件委托（`a[data-cite-ref]`）打开它，不给每个引用号单独包一层客户端组件。
  - 卡片内容：「参考文献 N」（等宽 10px）、著录文本（照原文，serif 15px）、底部一行「DOI ↗」「PubMed ↗」，右侧「跳到文末」。点「跳到文末」时走现在的逻辑：先展开参考文献列表，记下来源引用，再跳转并让目标条目亮一下。
  - 键盘：引用号是链接，按 Enter 打开卡片，焦点移进卡片，Esc 关闭后焦点回到引用号。
  - 没有 JS 时，引用号仍是指向 `#ref-…` 的普通链接。
  - 一个引用号对应多条参考文献（`targets` 有多个，比如「3–5」）时，卡片里列出每一条。
  - 只做点击，不做 hover 弹出，触屏和鼠标行为一致。
- 卡片需要的参考文献数据（著录文本 `ReactNode`、DOI、PMID、标签）由 `ReadableFullText` 一次性传给 `CitationPopover`。

### 5.8 插图宽度（画板 05）

纯函数 `figureLayout(width, height): "text" | "wide" | "wide-zoom" | "portrait"`，`r = width / height`：

| r | 类 | 图框宽度 | 说明 |
|---|---|---|---|
| < 0.8 | `portrait` | 内容格内居中，图片最高 640px | 竖图 |
| 0.8 – 1.5 | `text` | 612px（36em），和正文左右对齐 | |
| 1.5 – 2.4 | `wide` | 772px（`RailRow wide`） | |
| > 2.4 | `wide-zoom` | 772px，图注右侧常显「放大查看」 | 超宽，小字看不清 |
| 宽或高缺失 | `text` | | 兜底 |

区间取左闭右开：0.8 算 `text`，1.5 算 `wide`，2.4 算 `wide`。

- 图框：白底 `paper-50`、`border-subtle`、圆角 8px、内边距 12px。图框用 `aspect-ratio: width / height` 占位，图片加载前后高度不变；加载完淡入（保留现有 shimmer）。
- 图片显示宽度 = `min(图框内宽, 原图宽)`，居中，不放大超过原图。
- 窄屏（容器单列时）：所有类都是内容格满宽，图片 `max-height` 按现在的规则（`max-sm:max-h-[360px]`）。
- 图注：一个 `RailRow`。标签格是原文标签，去掉末尾的句点（`Figure 1.` → `Figure 1`），等宽 11px 500 `clay-700`。内容格最大 612px：左边是图题，serif 15px 400 `fg-2`，**不再加粗**；后面是图注其余段落，sans 13px `fg-2`；右边是「放大查看 · 原图 ↗」（等宽 11px），`text` 和 `wide` 类在 hover 或聚焦图框时才显示，`wide-zoom` 类常显。
- 点图片照旧打开放大层（`FigureLightbox` 不变）。「放大查看」是打开同一个放大层的按钮，「原图 ↗」在新标签页打开图片地址。

### 5.9 图表标签照原文

图、表、公式的标签照原文（`Figure 1`、`Table 10`），不翻成「图 1」「表 10」，遵守 v0.11 决策 C。画板上的中文是示意，以本条为准。去掉末尾句点属于格式转换。

### 5.10 数据表

- 表题在表的上方，一个 `RailRow`：标签格放原文标签，内容格放表题（serif 15px 400 `fg-2`，不加粗）。
- 表格横跨内容格（宽 = 内容格宽），上下各一条 1px `ink-900` 粗线（三线表风格），表头下一条 `border-strong`，行与行之间 `border-subtle`。去掉现在的圆角外框和表头灰底。
- 表头：等宽 10px 500 大写，字距 0.06em，`fg-3`。
- 数字列右对齐：一个单元格的纯文本符合 `^[−–-]?[\d.,]+\s?%?$`、`^[—–-]$`，或「数字 ± 数字」「数字 (区间)」这类形式时，判为数字格。一列里除表头外超过一半是数字格，这一列就右对齐（表头跟着右对齐）。判定写成纯函数 `numericColumns(head, body): boolean[]`，跨列的单元格不参与判定。
- 首列吸左、窄屏的横滑提示、长表折叠（> 12 行显示前 10 行）照旧。宽表在框内横向滚动，页面本身不出现横向滚动条。
- 表注在表下方，放在内容格里。

### 5.11 声明与附注（画板 03）

正文的 `back`（致谢、作者贡献、利益声明、伦理、资助、数据可用性、ORCID 等）不再一节一个二级标题，合成一个区块：

- 区块头是一个 `RailRow`：标签格「文末」，内容格放标题「声明与附注」（serif 24px）和计数「8 项」（等宽 `fg-3`）。
- 下面是一个 `<dl>`，每节一行：`dt` 是节标题（sans 13px 600 `fg-1`），`dd` 是节内容，按现在的 `back` 字级（serif 15px）渲染，节里有子节或图表也照常渲染。`dt` 放在标签格（128px，长标题换行），`dd` 放在内容格。容器单列时，`dt` 在上、`dd` 在下。
- 行与行之间用 `border-subtle` 分隔。
- **无标题的 `back` 节**（脚注组等）：内容照常渲染，放在 `<dl>` 后面，每节一个标签格留空的 `RailRow`，不丢内容。
- **含图或表的 `back` 节**（附录）：不进 `<dl>`，在声明区之后按普通章节渲染：标题 serif 24px 放在内容格，标签格留空、不编号，图表按 §5.8 / §5.10 的宽度规则。它们在目录里不单列，计入「声明与附注 N 项」的 N。
- 区块锚点是 `#declarations`。各节原来的锚点（`ft-<id>`）保留在 `dt` 上，正文里指向它们的交叉引用仍然有效。
- 没有带标题的 `back` 节时整块不出现。

### 5.12 参考文献

照原文一段显示（§8 第 2 条）：

- 区块头 `RailRow`：标签格「REFERENCES」，内容格放「参考文献」+ 条数。
- 每条一个 `RailRow`：标签格是两位序号（`01`；原文标签不是数字时照原文），等宽 12px `fg-3`；内容格是著录文本，serif 15px、行高 1.55、`fg-1`。下面一行等宽 11px 的链接：「DOI ↗」「PubMed {pmid} ↗」，最后是「↩ 正文 N 处」（N 是这条在正文里被引用的次数，渲染时数出来）。只被引一次时写「↩ 正文」。没被引用时不显示。点击回到刚才点过的引用，没点过就回到第一次引用的位置（同现在）。
- 超过 8 条时只显示前 5 条，下面是「展开其余 N 条」（N = 总数 − 5），展开后不再收起。
- 目标条目亮起、hash 直达时自动展开，这些行为都不变。

### 5.13 文献档案

现在的「深度数据 · 按需展开」整块移到这里，改名「文献档案」：

- 区块头 `RailRow`：标签格「RECORD」，内容格「文献档案」。区块锚点 `#record`。
- 折叠项沿用 `DisclosureSection`，样式改成画板上的样子：标签格里是等宽计数或类别（「2 位」「ID」「DATES」），内容格是标题，右侧是展开箭头。第一项「完整作者与机构」默认展开，其余默认收起。
- 内容：完整作者与机构；其他标识符（PMCID、PII，以及原来「关键标识」区里的 DOI、PMID）；各类日期；资助信息（有才显示）；参考文献数与利益冲突（有才显示）。原来正文前面的「关键标识」区删除，DOI / PMID / PMCID 改到侧栏的标识卡（§4）。

### 5.14 许可与出处

`LicenseColophon` 的**每一行内容都保留**（许可、作者、出处、DOI、PMC、数据来源、版本、抓取日期，连同作者手稿说明、「可能不是最新版本，以 PMC 原文为准」和「查看 PMC 最新版 ↗」）。这些是 v0.11 决策 D / E 定下的转载义务，画板 03 只画了四行，是示意不全，以本条为准。

只改排版：一个区块头 `RailRow`（标签格留空，内容格「许可与出处」serif 24px），下面是 `<dl>`，`dt` 放在标签格（等宽 11px `fg-3`），`dd` 放在内容格，行间 `border-subtle`。锚点 `#license` 不变。正文开头出处条上的「作者手稿」胶囊也保留。

### 5.15 侧栏与目录

- **标识卡**：DOI、PMID、PMCID 三行，每行左边是等宽类型名，中间是值（链接到 doi.org / PubMed / PMC），右边是 44×44 的「复制」按钮（`aria-label="复制 DOI"`）。复制成功后图标短暂换成对勾，并给读屏一个 `aria-live` 提示「已复制」。值为空的行不显示，三行都空时整卡不出现。
- **AI 速读卡**：保留，文案换成画板的「AI 速读即将上线，上线后也只作线索，不能替代阅读原文。」，按钮仍然禁用。
- **目录卡**：
  - 一级章节前加序号（`01`），当前章节下面展开二级章节，二级前加 `4.5` 这样的序号。
  - 虚线下面固定三项：「声明与附注 N 项」（有声明时）、「参考文献 56」（有参考文献时）、「文献档案与许可 ↓」。原来列在一级章节后面的 `back` 节不再进目录。
  - 右上角：还没进入正文时显示「≈ 34 分钟」；读到正文后显示「剩 ≈ N 分钟」，N = `ceil(readMin × (1 − 正文进度))`；正文进度到 100% 后显示「正文已读完」。
  - 「声明与附注」「参考文献」「文献档案与许可」三项也参与当前位置高亮（锚点分别是 `#declarations`、`#references`、`#record`）。
  - 卡底的下载 PDF 和许可 · 抓取日期不变。
- 吸顶偏移与现在一致（134px）。

### 5.16 面包屑

- 页面滚过 Masthead 之后，面包屑中间出现短标题（`splitTitle` 的主标题，单行省略）。滚回 Masthead 后隐藏。没有全文的页面也这样。
- 有可读全文时，右边再加「{位置} · 已读 {p}%」：
  - 位置：在正文里是当前章节序号（`4.5`，没进二级就是 `04`）；在文末区块里是区块名「声明与附注」「参考文献」「文献档案」；进入正文前不显示位置。
  - 已读比例 p 和目录卡的正文进度是**两个量**，都由 `useReadingProgress()` 给出：
    - **正文进度**：分母是正文的高度，从第一个章节开始，到声明区（没有声明区就到参考文献）为止。目录卡用它算剩余分钟和「正文已读完」。
    - **整篇进度 p**：分母是整个 `#fulltext`，从区块头到许可与出处。面包屑显示它。所以画板 03 那一屏，面包屑显示 91%，目录同时显示「正文已读完」。
  - 两个量都是视口顶部吸顶线（134px）在对应区间里的位置，结果夹在 0–100。
- 面包屑这块内容是客户端组件，放进 `DetailCrumbs` 新增的 `status` 插槽（期刊详情页不传，不受影响）。它要的章节序号表由全文那次 Suspense 请求给出；全文还没到时只显示短标题。
- 底边 2px 陶土进度线保留现在的 CSS scroll-driven 实现（整页滚动），与 p 不必相等。
- 窄屏（≤ 720px）不显示短标题，只显示进度，以及右端的「目录」按钮（现有）。

### 5.17 后端：拆开粘连的摘要标签

**位置**：`patra-catalog-infra` 的 PubMed 解析。`CanonicalPublicationParsingStrategy.parseAbstract` 读出各个 `AbstractText` 之后、`buildAbstract` 组装之前处理。PubMed 出版方把标签写进正文是 PubMed 数据的特点，这件事属于解析，不进领域层。

**新类** `RunInAbstractLabelSplitter`（`infra/parser/support/`，纯 Java，无状态）：

- 输入：一段没有 `Label` 的摘要文本。输出：带标签的段落列表；不满足条件时返回空列表，调用方保持原样。
- **只在下面三个条件都满足时拆**：
  1. 这篇摘要只有一个 `AbstractText`，而且它没有 `Label`（有任何一段带 `Label` 时整篇不动）。
  2. 文本以标签词开头，标签词后面紧跟一个大写字母，中间没有空格或标点，比如 `ObjectiveEsophageal`。
  3. 后面还能再找到至少一个标签：位置在句末标点（`. ! ? ) ]`）之后，紧跟一个首字母大写的短语（最多 6 个词，可用 `and` / `or` / `of` / `&` / 逗号连接，如 `Main Outcome Measures`、`Intervention or Exposures`），短语末尾同样紧跟大写字母或数字，比如 `models.MethodsThe`、`clinic.Participants266`。整篇至少要切出两段。中间标签不限词表：正常行文的句末标点后总有空格，不会命中；只用词表的话，多词标签会漏切，那一段就只拆了一半（实测 97 篇里有 8 篇）。
- **开头标签的词表**（来自 mini 上那 97 篇的实际统计）：Background、Objective(s)、Aim(s)、Purpose、Introduction、Importance、Context、Relevance、Design、Setting、Participants、Subjects、Sample、Intervention(s)、Method(s)、Methodology、Measures、Analysis、Results、Findings、Discussion、Conclusion(s)、Interpretation、Application。按最长匹配（`Objectives` 优先于 `Objective`），后面紧跟大写字母或数字。
- **实测**：按此规则模拟这 97 篇，全部完整拆开，没有残留的粘连标签。
- **切分结果**：
  - 每段的 `label` 是标签词的大写形式（`OBJECTIVE`、`CONCLUSIONS`），与 PubMed `Label` 属性的写法一致。
  - `content` 是标签后面到下一个标签之前的文本，去掉首尾空白，原文一个字不改。
  - 句末标点留在上一段末尾。
- 拆出来的段落交给现有的 `buildAbstract`，下游 `PubmedArticleItemProcessor.buildPublicationAbstract` 看到带 `Label` 的段落，就自然判成 `STRUCTURED`。`plainText` 按现有规则拼成 `LABEL: text`，所以检索列表的摘要片段也随之变对。

**数据**：dev 数据可丢弃，合入后在 mini 上重跑 PubMed 导入 Job，让已入库的摘要按新规则重新解析。实施时要实测：重跑时已有记录会不会因为内容哈希没变而被跳过；如果会跳过，就按现有的强制重导方式重跑。

**测试**：`RunInAbstractLabelSplitterTest` 覆盖本篇原文、单个标签不拆、开头不是标签不拆、标签后有空格不拆、`Objectives` 最长匹配、句末是 `)` 的情况。`CanonicalPublicationParsingStrategyTest` 加一条端到端用例：一段 `ObjectiveEsophageal…` 的 `AbstractText` 解析出 4 个带标签的段落。

## 6. 窄屏

- 980px 以下侧栏落到正文下方（现有行为）：标识卡和 AI 速读卡跟着落下去，目录改由面包屑的「目录」按钮打开底部面板（现有）。落下去的侧栏里不再有操作卡，问题 2 自然消失。
- main 容器宽度小于 580px 时标签栏改成单列（§5.4）。
- 事实条按 `auto-fit` 自动折成多行。
- 操作行按钮自动换行；540px 以下按钮撑满一行（沿用现有 `max-[540px]` 断点的写法）。
- 页面在 375px 宽下不出现横向滚动条（e2e 验收）。

## 7. 改动清单

| 文件 | 改动 |
|---|---|
| `lib/portal-api/title-derive.ts`（新） | `splitTitle` |
| `lib/portal-api/fulltext-derive.ts` | `figureLayout`、`numberSections`、`numericColumns`、引用次数统计；`deriveToc` 改为带序号、不收 `back` |
| `lib/use-reading-progress.ts`（新） | 已读比例、剩余分钟 |
| `components/portal/RailRow.tsx`（新） | 标签栏行 |
| `paper-detail/PublicationDetailView.tsx` | 重排为 Masthead + Body（§4）；「关键标识」「深度数据」移走 |
| `paper-detail/PaperHeader.tsx` | 标题拆分、事实条、操作行；删 `EvidenceBadge` |
| `paper-detail/PaperFacts.tsx`（新） | 事实条，含 Suspense 的全文三项 |
| `paper-detail/PaperRail.tsx` | 改为标识卡 + AI 速读卡；删操作卡、速览卡 |
| `paper-detail/IdentifierCard.tsx`（新） | 标识卡与复制按钮（客户端） |
| `paper-detail/AbstractBlock.tsx` | 标签栏、中文译名 |
| `paper-detail/KeywordRow.tsx`（新） | MeSH · 关键词 |
| `paper-detail/RecordSection.tsx`（新） | 文献档案 |
| `paper-detail/FullTextActions.tsx` | 删 `block` 形态 |
| `fulltext/FullTextBody.tsx` | 标签栏、章节序号、引用前去空白、`back` 改为声明区、引用次数 |
| `fulltext/FullTextSection.tsx` | 区块头、接收文献档案节点、排序 |
| `fulltext/FigureBlock.tsx` | 宽度分类、图框、图注 |
| `fulltext/TableBlock.tsx` + `globals.css` 的 `.ft-table` | 三线表、数字列右对齐、表题在标签栏 |
| `fulltext/ReferenceList.tsx` | 标签栏、序号、「↩ 正文 N 处」、展开文案 |
| `fulltext/CitationPopover.tsx`（新） | 引用卡片 |
| `fulltext/LicenseColophon.tsx` | `<dl>` 排版 |
| `fulltext/FullTextToc.tsx`、`TocList.tsx`、`FullTextTocSheet.tsx` | 序号、固定三项、剩余时间 |
| `DetailCrumbs.tsx` | 短标题与进度（通过 `action` 之外的新插槽，期刊详情页不受影响） |
| `app/papers/[id]/loading.tsx`、`FullTextSkeleton.tsx` | 骨架跟着新版式改 |

后端（§5.17）：

| 文件 | 改动 |
|---|---|
| `patra-catalog-infra/.../parser/support/RunInAbstractLabelSplitter.java`（新） | 拆分规则 |
| `patra-catalog-infra/.../parser/strategy/CanonicalPublicationParsingStrategy.java` | `parseAbstract` 之后调用拆分 |

**PR 划分**：两个 PR。后端 PR 先合（只有一个解析类，体量小），spec 和 plan 随它提交；前端 PR 在后端合入 main 之后，从最新 main 继续。后端部分另建一个 `[BE]` Issue 跟踪，前端 PR 关闭 PAP-89。

## 8. 已定的两条（2026-10-20 用户拍板）

1. **摘要标签粘连在后端修**（§5.17）。不在前端做切分。
2. **参考文献照原文一段显示**（§5.12），不拆三行，不加 PMC 链接，纯前端。

## 9. 测试策略

按 TDD，先写失败的测试：

- **纯函数（vitest）**：`splitTitle`（短、中、带冒号、超长带冒号、超长无冒号、`Re:`、末尾 `?`、全角冒号）、`figureLayout`（四类、三个边界值、宽或高缺失）、`numberSections`（自编号、标题自带序号、无标题章节、补充材料）、`numericColumns`（百分数、小数、`—`、± 区间、文字列、跨列）、引用前去空白（只去 `sup` 里全是 bibr 的情况；方括号写法不动）、引用次数统计、`useReadingProgress` 的比例与剩余分钟换算。
- **组件（vitest + Testing Library）**：更新现有 `PaperHeader`、`PaperRail`、`PublicationDetailView`、`AbstractBlock`、`FigureBlock`、`TableBlock`、`ReferenceList`、`FullTextBody`、`FullTextToc` 的测试；新增 `CitationPopover`（点击打开、Esc 关闭后焦点回来、多目标、「跳到文末」展开并跳转）、`IdentifierCard`（复制、读屏提示）、`KeywordRow`（前 8 项、「另 N 项」）、`RecordSection`。断言「全页只有一组全文操作」：渲染整页时，「阅读全文」链接只有一个。
- **e2e（Playwright，网关指 mini）**：`paper-detail.spec.ts`、`fulltext-reader.spec.ts`、`detail-rail-sticky.spec.ts` 更新；新增：
  - PMID 41575322：主标题 2 行、副标题 3 行（1440 宽，量 `h1` 的行盒）；
  - 四类图片的图框宽度（612 / 772）；
  - 宽表在框内滚动，`document.scrollingElement.scrollWidth === clientWidth`（1440 与 375 两种宽度）；
  - 引用号点击出卡片，「跳到文末」落到条目；
  - 375 宽下标签栏变单列、无横向滚动。
- **门控**：`pnpm` 的 lint、typecheck、test 全过，e2e 全过（`--workers=1`）。做完先停在本地给用户验收（1440 / 980 / 375 三档截图），确认后再开 PR。

## 10. 已知代价

- 事实条的全文三项比上面五项晚一点出现（同一次流式请求），首屏会有一次很小的格子增加。
- 目录不再列出各个声明节，想跳到「Funding」要先到「声明与附注」再往下看。
- 引用号点击从「直接跳」变成「先出卡片」，看完整列表要多点一下「跳到文末」。
- 数字列判定是启发式，个别表格（比如数字和文字混排的列）可能判错，判错只是对齐方式不对，不影响内容。

## 11. 要同步改的文档

- `patra-portal/docs/design-briefs/v0.11/fulltext-reader.md`：§3.4 图、表、引用、参考文献的呈现规则，以及侧栏、目录的描述，改成本 spec 的规则；注明出处 PAP-89。
- `docs/patra/design/snapshots/`：画布源文件存一份快照 `2026-10-20-paper-detail-layout-hifi`，README 补一行。
- `patra-portal/README.md` 中详情页结构的描述（如果有）。
