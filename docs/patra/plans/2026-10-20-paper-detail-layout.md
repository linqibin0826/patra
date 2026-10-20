# 文献详情页排版重设计 Implementation Plan（PAP-89）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 按定稿画板重排文献详情页（长标题拆分、事实条、标签栏、插图按宽高比定宽、三线表、引用卡片、声明区、文献档案），并在 catalog 解析 PubMed 时拆开粘连的摘要标签。

**Architecture:** 两个 PR。PR 1 是后端：在 `CanonicalPublicationParsingStrategy` 里加一个纯 Java 的 `RunInAbstractLabelSplitter`，spec 和 plan 也随这个 PR 提交。PR 2 是前端：先写一批纯函数（标题拆分、插图分类、章节编号、数字列判定、阅读进度），再写一个标签栏布局组件 `RailRow`，然后逐区改组件。全文相关的数据仍然只请求一次，事实条、面包屑状态、目录各自包一层 Suspense 流式给出。

**Tech Stack:** 后端 Java 25 / JUnit 5 / AssertJ；前端 Next.js 16 / React 19 / TypeScript strict / Tailwind v4（容器查询）/ Base UI（Popover）/ vitest + Testing Library / Playwright。

**Spec:** `docs/patra/specs/2026-10-20-paper-detail-layout-design.md`（下文写「spec §x」）。执行时 spec 和本 plan 都要读。

## Global Constraints

- 只做格式转换、不改写原文（v0.11 决策 C）。图表标签显示原文 `Figure 1`，只去掉末尾句点；著录文本、图注、表注、声明正文照原文。
- 标签栏几何：标签格 128px，间距 32px（`gap-x-8`），正文最大 36em；`wide` 宽 772px；main 容器宽度小于 580px 时改成单列。
- 吸顶偏移不变：面包屑底 134px；正文锚点 `scroll-mt-[45px]`，参考文献 62px，引用 68px。
- 新增的锚点：`#declarations`、`#record`。已有的 `#fulltext`、`#references`、`#license`、`ft-<id>`、`ref-<id>`、`cite-N` 一律不改名。
- 颜色只用 `@theme` 的语义类（`text-fg-3`、`text-clay-700`、`border-border-subtle` 等），不写 `[var(--x)]`，有守护测试拦截。
- 前端测试名用中文描述句（同现有测试）；后端测试方法名用 snake_case 加 `@DisplayName`。
- 每个 Java 方法都要写 `///` JavaDoc；不写全类名；record 的集合字段要 `copyOf`。
- 提交信息用中文动词开头（commitlint 拒绝大写开头），结尾带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。只 `git add` 自己改的路径。
- 做完先停在本地给用户验收，用户确认后才开 PR（记忆：改动先本地验收再开 PR）。

## Review Focus

1. **标题里冒号出现在富文本标签内，或是 `Re:` 这类短前缀**：应该不拆，原样显示。Task 4 的 `splitTitle` 用例覆盖。
2. **图片宽高缺失或为 0**：图框按 `text` 类、不设 `aspect-ratio`，不能除零，也不能撑破布局。Task 4 和 Task 10 都有用例。
3. **一个引用号对应多条参考文献（`targets` 有 3 个）**：卡片列出全部 3 条；「跳到文末」去第一条。Task 13 有用例。
4. **没有全文的文献**（占大多数）：页面没有声明区、参考文献、目录、阅读进度，文献档案仍然在说明面板之后，面包屑只显示短标题。Task 8 和 Task 15 有用例。
5. **375px 宽下的超宽表格和 `wide` 图**：页面本身没有横向滚动，表格在框内横滑，图片按内容格满宽显示。Task 17 的 e2e 量 `scrollWidth`。

---

## PR 1：后端（分支 `feat/pap-89-abstract-labels-api`，从最新 main 切）

开工前把未提交的 spec 与本 plan 带到这个分支（它们现在是 `feat/pap-89-paper-detail-portal` 上的未跟踪文件）：`git switch -c feat/pap-89-abstract-labels-api origin/main`。

### Task 1: `RunInAbstractLabelSplitter`

**Files:**
- Create: `patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/parser/support/RunInAbstractLabelSplitter.java`
- Test: `patra-api/patra-catalog/patra-catalog-infra/src/test/java/dev/patra/catalog/infra/parser/parser/support/RunInAbstractLabelSplitterTest.java`

**Interfaces:**
- Produces: `final class RunInAbstractLabelSplitter`，私有构造器，只有一个静态方法 `static List<LabeledText> split(String text)`；嵌套 `record LabeledText(String label, String content)`（2 个参数，用 `of()` 工厂方法）。不满足拆分条件时返回 `List.of()`。

- [ ] **Step 1: Write the failing tests**

| 方法 | 输入 | 期望 |
|---|---|---|
| `should_split_glued_labels_of_pmid_41575322` | `"ObjectiveEsophageal cancer is rising.MethodsThe dataset was used.ResultsLightGBM won.ConclusionsThe model works."` | 4 段：`("OBJECTIVE","Esophageal cancer is rising.")`、`("METHODS","The dataset was used.")`、`("RESULTS","LightGBM won.")`、`("CONCLUSIONS","The model works.")` |
| `should_not_split_when_only_leading_label` | `"ObjectiveEsophageal cancer is rising and nothing else."` | 空 |
| `should_not_split_when_text_does_not_start_with_label` | `"Esophageal cancer.MethodsThe dataset."` | 空 |
| `should_not_split_when_space_after_label` | `"Objective Esophageal cancer.Methods The dataset."` | 空 |
| `should_prefer_longest_label` | `"ObjectivesTo test.ResultsIt worked."` | 第一段标签是 `OBJECTIVES`，内容是 `"To test."` |
| `should_split_after_closing_parenthesis` | `"BackgroundSome text (n = 3).MethodsWe did it."` 以及 `"BackgroundSome text (n = 3)MethodsWe did it."` | 都拆成两段，前一段以 `)` 或 `.` 结尾 |
| `should_not_split_label_word_inside_sentence` | `"BackgroundThe Results of trials vary.ConclusionsMore work."` | 两段（`Results` 前面不是句末标点，不切） |
| `should_return_empty_for_blank` | `null`、`""`、`"   "` | 空 |

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*RunInAbstractLabelSplitterTest*"`
Expected: 编译失败，`RunInAbstractLabelSplitter` 不存在

- [ ] **Step 3: Implement `split`**

开头标签的词表照 spec §5.17 抄成常量，按长度降序拼成一个交替组。两个正则（最终规则，见 spec §5.17）：
- 开头：`^(LABELS)(?=[\p{Lu}\p{Nd}])`，标签后紧跟大写字母或数字
- 中间：不限词表，句末标点后紧贴一个首字母大写的短语，最多 6 个词，可用 `and` / `or` / `of` / `&` / 逗号连接，短语后紧跟大写字母或数字：`(?<=[.!?)\]])(\p{Lu}\p{Ll}+(?:(?:,? (?:and|or|of|&) |, | & | )\p{L}\p{Ll}+){0,5})(?=[\p{Lu}\p{Nd}])`

测试清单另加三条：多词中间标签（`Main Outcome MeasuresScores`、`Intervention or ExposuresEight`）、标签后紧跟数字（`Participants266`）、6 个词能切开而 7 个词不切。

先匹配开头，不中就返回空。再从开头标签之后找中间标签，一个都找不到也返回空。按匹配位置切段，`label` 取 `toUpperCase(Locale.ROOT)`，`content` 取 `strip()`。

- [ ] **Step 4: Run tests to verify they pass**

Run: 同 Step 2。Expected: 8 个全 PASS

- [ ] **Step 5: Commit**

```bash
git add patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/parser/support/RunInAbstractLabelSplitter.java patra-api/patra-catalog/patra-catalog-infra/src/test/java/dev/patra/catalog/infra/parser/parser/support/RunInAbstractLabelSplitterTest.java
git commit -m "feat(catalog): 识别 PubMed 摘要里粘在正文前的段落标签 (PAP-89)"
```

### Task 2: 解析时拆开，提交 spec 与 plan

**Files:**
- Modify: `patra-api/patra-catalog/patra-catalog-infra/src/main/java/dev/patra/catalog/infra/parser/strategy/CanonicalPublicationParsingStrategy.java`（`buildAbstract` 约 1010 行）
- Test: `.../parser/parser/strategy/CanonicalPublicationParsingStrategyTest.java`（`AbstractParsing` 嵌套类）
- Add: `docs/patra/specs/2026-10-20-paper-detail-layout-design.md`、`docs/patra/plans/2026-10-20-paper-detail-layout.md`

**Interfaces:**
- Consumes: `RunInAbstractLabelSplitter.split(String)`（Task 1）

- [ ] **Step 1: Write the failing tests**（放在 `AbstractParsing` 里）
  - `should_split_run_in_labels_of_single_unlabeled_abstract_text`：`<AbstractText>ObjectiveA rises.MethodsB was used.ResultsC won.ConclusionsD works.</AbstractText>` → `getSections()` 有 4 段，标签依次是 `OBJECTIVE`、`METHODS`、`RESULTS`、`CONCLUSIONS`，第一段内容 `"A rises."`
  - `should_keep_labeled_abstract_untouched`：一段 `Label="BACKGROUND"` 加一段无 Label、内容为 `"MethodsThe x."` → 两段原样
  - `should_keep_multiple_unlabeled_abstract_texts_untouched`：两个无 Label 的 `AbstractText`，第一个以 `ObjectiveA.MethodsB.` 开头 → 两段原样，标签都是 `null`
- [ ] **Step 2: Run to verify they fail**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*CanonicalPublicationParsingStrategyTest*"`
Expected: 第一条 FAIL（只有 1 段），另两条 PASS

- [ ] **Step 3: Implement**

在 `buildAbstract` 里，`fields.abstractSections` 只有一段且 `label == null` 时，调用 `split(content)`。结果不为空，就用它替换原来的段落列表。另写一个私有方法 `splitRunInLabels(List<ParsedAbstractSection>)`，写上 `///` 注释。

- [ ] **Step 4: Run tests, then the module check**

Run: `./gradlew :patra-api:patra-catalog:patra-catalog-infra:test --tests "*CanonicalPublicationParsingStrategyTest*"`，然后跑 `./gradlew spotlessApply :patra-api:patra-catalog:patra-catalog-infra:check`
Expected: 全部 PASS，check 通过

- [ ] **Step 5: Commit**（spec、plan 一起提交）

```bash
git add patra-api/patra-catalog/patra-catalog-infra/src docs/patra/specs/2026-10-20-paper-detail-layout-design.md docs/patra/plans/2026-10-20-paper-detail-layout.md
git commit -m "fix(catalog): 解析 PubMed 摘要时拆开粘连的段落标签，存为结构化摘要 (PAP-89)"
```

### Task 3: 全量门控、本地验收、PR、mini 重导

- [ ] **Step 1:** 在 Linear 的 Patra team 建一个 `[BE] PubMed 摘要粘连标签拆成结构化段落` Issue（标签 `area:api`、`path:full`，关联 PAP-89），记下编号 `PAP-xx`。
- [ ] **Step 2:** 跑 `./gradlew check`（含 integrationTest），Expected: BUILD SUCCESSFUL。
- [ ] **Step 3:** 停下给用户验收：贴出 Task 2 的测试和本篇的拆分结果。用户确认后再继续。
- [ ] **Step 4:** push，开 draft PR，描述里写 `Closes PAP-xx`（不写 PAP-89）。按根 CLAUDE.md 和记忆「开 PR 后用 ccd_pr 盯 PR」处理评审，处理完转 ready。合并要等用户逐个授权。
- [ ] **Step 5:** 合并、CD 部署后，在 mini 上重跑 PubMed 导入 Job（jobId 和调用方式见记忆「用 API 调度 XXL-Job」）。
  - 先查清楚重导时内容哈希没变的记录会不会被跳过。会跳过的话，用现有的强制重导参数。
  - 验证：`curl http://100.103.73.27:9528/patra-catalog/portal/publications/352303012807630907` 返回的 `abstractType` 是 `STRUCTURED`，`abstractSections` 有 4 段。在 mini 的 PG 上重跑 spec §2 那条计数 SQL，粘连数应该变成 0。

---

## PR 2：前端（分支 `feat/pap-89-paper-detail-portal`）

PR 1 合入后执行 `git fetch origin main && git rebase origin/main`。所有命令都在 `patra-portal/` 下运行。单测命令是 `pnpm vitest run <路径>`。

### Task 4: 标题、插图、数字列三个纯函数

**Files:**
- Create: `src/lib/portal-api/title-derive.ts`，测试 `tests/lib/portal-api/title-derive.test.ts`
- Modify: `src/lib/portal-api/fulltext-derive.ts`，测试 `tests/lib/portal-api/fulltext-derive.test.ts`

**Interfaces:**
- Produces:
  - `interface TitleParts { main: string; sub: string | null; size: "lg" | "md" }`，`splitTitle(title: string): TitleParts`
  - `type FigureLayout = "text" | "wide" | "wide-zoom" | "portrait"`，`figureLayout(width: number | null, height: number | null): FigureLayout`
  - `numericColumns(head: FullTextTableCell[][], body: FullTextTableCell[][]): boolean[]`
  - `figureLabel(label: string | null): string | null`：去掉末尾的一个 `.`

- [ ] **Step 1: Write the failing tests**
  - `splitTitle`：
    - 本篇全文 → `main` 为 `"Explainable machine learning for early diagnosis of esophageal cancer"`，`sub` 以 `"A feature-enriched"` 开头、以 `"interpretations"` 结尾（末尾没有句点），`size` 为 `"lg"`。
    - `"Global trends in esophageal cancer."` → `main` 为 `"Global trends in esophageal cancer"`，`sub` 为 `null`。
    - `"Re: a letter about trials"` → 不拆。
    - 末尾是 `?` 的标题，`?` 保留。
    - 全角冒号 `"中文主题：副题"` 能拆开。
    - 一条 121 字符、没有冒号的标题 → `size` 为 `"md"`；120 字符 → `"lg"`。
    - `"<i>BRCA1</i>: a review of mutations"` 能拆，`main` 为 `"<i>BRCA1</i>"`。
    - 冒号在标签里面的 `"<i>a: b</i> study"` → 不拆。
  - `figureLayout`：
    - `(733,558)` → `text`
    - `(750,420)` → `wide`
    - `(750,280)` → `wide-zoom`
    - `(752,952)` → `portrait`
    - 边界：`(80,100)` 即 0.8 → `text`；`(150,100)` 即 1.5 → `wide`；`(240,100)` 即 2.4 → `wide`；`(241,100)` → `wide-zoom`
    - `(null,100)`、`(100,0)` → `text`
  - `numericColumns`：一张 3 列表，列值分别是 `["Tsai et al.","Nopour (2024)"]`、`["91%","93.43%"]`、`["—","0.9239"]` → `[false,true,true]`。另测：`"12.3 ± 4.5"`、`"0.82 (0.71–0.93)"`、`"−1.2"` 判为数字；跨列格不参与计数；空表返回 `[]`。
  - `figureLabel("Figure 1.")` → `"Figure 1"`；`figureLabel(null)` → `null`。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/lib/portal-api/title-derive.test.ts tests/lib/portal-api/fulltext-derive.test.ts`。Expected: 新用例 FAIL（函数不存在）。
- [ ] **Step 3:** 实现。`splitTitle` 先用 `inlineText` 同样的思路剥掉标签，在纯文本上找冒号；再把这个位置映射回原字符串，只有冒号在标签外面时才切。`numericColumns` 的正则：`/^[−–-]?\d[\d.,]*\s?%?(\s?±\s?\d[\d.,]*)?(\s?\(.*\))?$|^[—–-]$/`，一列里超过一半的单元格匹配才算数字列。
- [ ] **Step 4:** 运行 Step 2 的命令。Expected: 全 PASS。
- [ ] **Step 5: Commit** `feat(portal): 加入标题拆分、插图宽度分类与数字列判定 (PAP-89)`

### Task 5: 章节编号、目录、引用统计的纯函数

**Files:**
- Modify: `src/lib/portal-api/fulltext-derive.ts`
- Test: `tests/lib/portal-api/fulltext-derive.test.ts`

**Interfaces:**
- Produces:
  - `interface SectionHeading { label: string; title: FullTextInline[] }`
  - `numberSections(sections: FullTextSectionNode[]): Record<string, SectionHeading>`：键是章节 id，只包含一、二级章节
  - `TocEntry` 加字段 `number: string | null`；`deriveToc(body)` 不再收 `back`
  - `deriveTocTail(body: FullTextBody): { declarations: number; references: number }`
  - `isCitationSup(node: FullTextInline): boolean`
  - `splitBack(back: FullTextSectionNode[]): { declarations: FullTextSectionNode[]; untitled: FullTextSectionNode[]; appendices: FullTextSectionNode[] }`

- [ ] **Step 1: Write the failing tests**
  - `numberSections`：
    - 5 个无序号的一级章节，第 4 个下面有 5 个二级章节 → 标签依次 `01` 到 `05`，第 4 个的子章节是 `4.1` 到 `4.5`，`title` 原样。
    - 所有一、二级标题都以序号开头（`"1. Introduction"`、`"2.1 Data"`）→ 标签 `01`、`2.1`，`title` 文字变成 `"Introduction"`、`"Data"`。
    - 只有部分带序号 → 按位置编号，`title` 原样（仍是 `"1. Introduction"`）。
    - 无标题章节、补充材料不进结果，也不占序号。
  - `deriveToc`：带上 `number`；`back` 节不出现。
  - `deriveTocTail`：8 个有标题的 `back` 节加 1 个附录、56 条参考文献 → `{ declarations: 9, references: 56 }`。
  - `isCitationSup`：`sup` 里只有 bibr xref，或 bibr 之间夹着 `", "`、`"–"` → `true`。`sup` 里有 fig xref 或普通文字 → `false`。不是 `sup` → `false`。
  - `splitBack`：有标题且不含图表 → `declarations`；无标题 → `untitled`；含 `figure` 或 `table` 块（含子节里的）→ `appendices`。补充材料一律排除。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/lib/portal-api/fulltext-derive.test.ts`。Expected: FAIL。
- [ ] **Step 3:** 实现。序号前缀的正则是 `/^(\d+(?:\.\d+)*)\.?\s+/`，只从标题的第一个 text 节点开头剥。
- [ ] **Step 4:** 运行 Step 2 的命令。Expected: PASS。现有的 `FullTextToc` 和 `TocList` 测试可能因为 `back` 不再进目录而失败，先记下来，留到 Task 14 修。
- [ ] **Step 5: Commit** `feat(portal): 加入章节编号、目录尾项与声明区分组的派生 (PAP-89)`

### Task 6: `RailRow` 与阅读进度

**Files:**
- Create: `src/components/portal/RailRow.tsx`，测试 `tests/components/portal/RailRow.test.tsx`
- Create: `src/lib/use-reading-progress.ts`，测试 `tests/lib/use-reading-progress.test.tsx`

**Interfaces:**
- Produces:
  - `RailRow(props: { label?: ReactNode; wide?: boolean; as?: "div" | "li" | "header"; className?: string; labelClassName?: string; children: ReactNode })`。使用 `RailRow` 的祖先节点要带 `@container/main`。
  - `progressIn(top: number, bottom: number, line: number): number`：返回值夹在 0–1
  - `remainingMinutes(readMin: number, bodyProgress: number): number`：`Math.ceil(readMin * (1 - p))`
  - `useReadingProgress(): { body: number; whole: number; started: boolean }`。`whole` 量 `#fulltext` 顶部到 `#license` 底部；`body` 量 `[data-ft-body]` 顶部到 `#declarations` 顶部（没有声明区就到 `#references`，再没有就到 `[data-ft-body]` 底部）；`line` = 134；`started` 表示 `body` 区间的顶部已越过 line

- [ ] **Step 1: Write the failing tests**
  - `RailRow`：
    - 有 label 时渲染两格，标签格带 `w-32`（128px）语义类；无 label 时标签格仍在，且带 `aria-hidden`。
    - `wide` 时内容格横跨两列，最大宽度 772px。
    - 断言容器查询类 `@max-[580px]/main:grid-cols-1` 存在（jsdom 不算布局，真实布局留给 e2e）。
  - `progressIn(100, 300, 200)` → 0.5；`(300, 500, 200)` → 0；`(0, 100, 200)` → 1。
  - `remainingMinutes(34, 0.62)` → 13；`(34, 1)` → 0。
  - `useReadingProgress`：mock `getBoundingClientRect`，触发 scroll，断言 `body`、`whole` 两个值，以及没有声明区时的回退。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/components/portal/RailRow.test.tsx tests/lib/use-reading-progress.test.tsx`。Expected: FAIL。
- [ ] **Step 3:** 实现。`RailRow` 用 `grid grid-cols-[128px_minmax(0,1fr)] gap-x-8 gap-y-2 @max-[580px]/main:grid-cols-1`。`useReadingProgress` 用被动 scroll 和 resize 监听，`requestAnimationFrame` 节流。
- [ ] **Step 4:** 运行 Step 2 的命令。Expected: PASS。
- [ ] **Step 5: Commit** `feat(portal): 加入标签栏布局与阅读进度 (PAP-89)`

### Task 7: 页头（标题、事实条、操作行）

**Files:**
- Modify: `src/components/portal/paper-detail/PaperHeader.tsx`、`FullTextActions.tsx`
- Create: `src/components/portal/paper-detail/PaperFacts.tsx`（服务端组件 `PaperFacts` + `FullTextFacts` + `FactCell`）
- Test: `tests/components/portal/paper-detail/PaperHeader.test.tsx`；新建 `PaperFacts.test.tsx`

**Interfaces:**
- Consumes: `splitTitle`（Task 4）、`deriveFullTextStats`、`fetchPublicationFullText`
- Produces:
  - `PaperHeader({ paper, fullTextFacts }: { paper: PaperDetail; fullTextFacts?: ReactNode })`，根元素是 `<section id="masthead">`
  - `FullTextFacts({ paper }): Promise<ReactNode>`，渲染 0–3 个 `FactCell`
  - `FactCellsSkeleton()`
  - `FullTextActions({ paper })`：删掉 `block` 参数

- [ ] **Step 1: Write the failing tests**
  - `PaperHeader`：
    - 本篇标题 → `h1` 的文本是主标题，副标题 `p` 紧跟在后，两者都没有末尾句点。
    - 121 字符无冒号的标题 → `h1` 带 `md` 档的类。
    - 不再渲染 `EvidenceBadge`。
    - 操作行里有「阅读全文」「下载 PDF」「收藏」和「在 PMC 查看原文 ↗」（状态是 `full` 且有 PMCID 时）。
  - `PaperFacts`：
    - 渲染证据等级、被引、收藏、来源、原文阅读五项，`dt` 和 `dd` 文本照 spec §5.2。
    - `estimatedReadMin` 为 `null` 时不出「原文阅读」。
  - `FullTextFacts`：mock `fetchPublicationFullText`，返回 `wordCount: 7633`，加上 11 图 10 表 56 条参考文献 → 三格依次是「篇幅 7,633 词」「图表 11 图 · 10 表」「参考文献 56 篇」。0 图 0 表时不出「图表」。全文请求失败时什么都不渲染。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/components/portal/paper-detail/PaperHeader.test.tsx tests/components/portal/paper-detail/PaperFacts.test.tsx`。Expected: FAIL。
- [ ] **Step 3:** 实现，字号和间距照 spec §5.1 – §5.3。按钮高度用 `h-12`。
- [ ] **Step 4:** 运行 Step 2 的命令。Expected: PASS。
- [ ] **Step 5: Commit** `feat(portal): 详情页头拆分长标题，加入事实条与唯一一组操作 (PAP-89)`

### Task 8: 主栏、侧栏重排（摘要、关键词、标识卡、文献档案）

**Files:**
- Modify: `src/components/portal/paper-detail/PublicationDetailView.tsx`、`AbstractBlock.tsx`、`PaperRail.tsx`、`src/app/papers/[id]/page.tsx`
- Create: `src/components/portal/paper-detail/KeywordRow.tsx`、`IdentifierCard.tsx`（`"use client"`）、`RecordSection.tsx`、`DetailSectionTitle.tsx`
- Test: `tests/components/portal/paper-detail/` 下的 `PublicationDetailView`、`AbstractBlock`、`PaperRail` 测试；新建 `KeywordRow.test.tsx`、`IdentifierCard.test.tsx`、`RecordSection.test.tsx`

**Interfaces:**
- Consumes: `RailRow`（Task 6）、`PaperHeader` 和 `FullTextFacts`（Task 7）
- Produces:
  - `PublicationDetailView({ paper, fullTextFacts, fullText, record, tocCard, tocButton })`。`record` 暂时渲染在 `fullText` 之后，Task 14 改为由 `FullTextSection` 排进去后删掉这个参数；Task 15 再加 `crumbStatus`
  - `RecordSection({ paper }: { paper: PaperDetail })`，根元素 `<section id="record">`
  - `DetailSectionTitle({ en, zh }: { en: string; zh: string })`：陶土竖条 +「摘要 · ABSTRACT」
  - `ABSTRACT_LABEL_ZH: Record<string, string>`（导出自 `AbstractBlock.tsx`，用于测试）

- [ ] **Step 1: Write the failing tests**
  - `AbstractBlock`：
    - 结构化摘要 `label: "OBJECTIVE"` → 标签格里有 `OBJECTIVE` 和「目的」，内容在另一格。
    - `label: "HYPOTHESIS"` → 只有原文标签。
    - 纯文本摘要 → 标签格为空。
  - `KeywordRow`：
    - 12 个 MeSH（第 3 个是 major）加 6 个关键词 → 默认 8 项，major 的排在第一，按钮文案是「另 10 项」；点开后 18 项都在，按钮消失。
    - 两类都为空 → 不渲染。
  - `IdentifierCard`：
    - 三行链接，地址分别是 `https://doi.org/…`、`https://pubmed.ncbi.nlm.nih.gov/…/`、`https://pmc.ncbi.nlm.nih.gov/articles/…/`。
    - 点「复制 DOI」→ `navigator.clipboard.writeText` 被调用，参数是 DOI；`role="status"` 区域出现「已复制」。
    - 三个值都为空 → 不渲染。
  - `PaperRail`：只剩标识卡和 AI 速读卡，速读文案照 spec §5.15；不再出现「操作」「速览」。
  - `RecordSection`：
    - 标题「文献档案」。
    - 「完整作者与机构」默认展开，其余收起。
    - DOI、PMID 出现在「其他标识符」里。
    - 有 `funding` 时有「资助信息」。
  - `PublicationDetailView`：
    - 整页只有一个「阅读全文」链接（`getAllByRole("link", { name: /阅读全文/ })` 长度为 1）。
    - 正文前不再有「深度数据」「关键标识」。
    - main 带 `@container/main`。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/components/portal/paper-detail`。Expected: FAIL。
- [ ] **Step 3:** 实现。`page.tsx` 的接法：
  - `fullTextFacts` 传 `paper.hasFullText && <Suspense fallback={<FactCellsSkeleton/>}><FullTextFacts paper={paper}/></Suspense>`。
  - `record` 传 `<RecordSection paper={paper}/>`，`fullText` 不变。
  - 侧栏的吸顶规则不变。
- [ ] **Step 4:** 运行 Step 2 的命令。Expected: PASS。
- [ ] **Step 5: Commit** `feat(portal): 详情主栏改为标签栏，深度数据移到文末文献档案，侧栏改为标识卡 (PAP-89)`

### Task 9: 正文渲染（标签栏、章节号、引用号、声明区）

**Files:**
- Modify: `src/components/portal/fulltext/FullTextBody.tsx`
- Test: `tests/components/portal/fulltext/FullTextBody.test.tsx`

**Interfaces:**
- Consumes: `RailRow`、`numberSections`、`isCitationSup`、`splitBack`（Task 5 / 6）
- Produces: `RenderedFullTextBody` 改为下面这组字段：
  - `content: ReactNode`（正文，外层是 `<div data-ft-body>`）
  - `declarations: ReactNode | null`
  - `appendices: ReactNode | null`
  - `references: ReactNode[]`
  - `firstCiteByRef: Record<string, string>`
  - `citeCountByRef: Record<string, number>`

  bibr 引用链接带 `data-cite-ref`（第一个目标）和 `data-cite-refs`（全部目标，空格分隔）。

- [ ] **Step 1: Write the failing tests**
  - 一级章节标题前有 `01`，二级有 `4.5`；三级没有编号。
  - `text("smoking. ") + sup(xref bibr "8")` → 渲染出的文本里没有 `"smoking. 8"`，只有 `"smoking.8"`（上标紧贴）。`text("as shown [") + xref bibr "3" + text("]")` → 原样。
  - 两处引用同一条参考文献 → `citeCountByRef` 里它的值是 2。
  - 一个 xref 指向 3 条参考文献 → `data-cite-refs` 里有 3 个 id。
  - `back` 里 8 个有标题的节 → `declarations` 里是一个 `<dl>`，8 个 `dt`，每个 `dt` 带 `id="ft-<id>"`；外层带 `id="declarations"`，标题是「声明与附注」加「8 项」。
  - 无标题的 `back` 节 → 内容出现在 `<dl>` 之后。
  - 含 `table` 的附录 → 出现在 `appendices` 里，有自己的 `h2`。
  - 连续的 3 个段落放在同一个 `RailRow` 里。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/components/portal/fulltext/FullTextBody.test.tsx`。Expected: FAIL。
- [ ] **Step 3:** 实现。标题字号照 spec §5.6。`renderInlines` 遇到 `isCitationSup(next)` 为真时，把前一个 text 节点去掉末尾空白（`trimEnd`）。段落字级改为 `mb-[1.1em]`。
- [ ] **Step 4:** 运行 Step 2 的命令。Expected: PASS。
- [ ] **Step 5: Commit** `feat(portal): 正文改为标签栏排版，章节编号、引用号紧贴、文末声明合为一组 (PAP-89)`

### Task 10: 插图

**Files:**
- Modify: `src/components/portal/fulltext/FigureBlock.tsx`、`FullTextBody.tsx`（只改 figure 分支的传参）
- Test: `tests/components/portal/fulltext/FigureBlock.test.tsx`

**Interfaces:**
- Consumes: `figureLayout`、`figureLabel`（Task 4）、`RailRow`
- Produces: `FigureBlock` 新增参数 `layout: FigureLayout`；`label` 由调用方传入 `figureLabel(...)` 的结果

- [ ] **Step 1: Write the failing tests**
  - `layout="text"` → 图框最大宽度 612px；`wide` → 772px；`portrait` → 图片 `max-h-[640px]`。
  - 有宽高时图框的 `style.aspectRatio` 是 `"750 / 280"`；宽高缺失时没有 `aspectRatio`。
  - 标签格文本是 `Figure 2`，没有末尾句点。图题不再带 `font-medium`。
  - `wide-zoom` 时「放大查看」按钮常显，点它打开放大层。`text` 时同一个按钮带 `opacity-0` 和 `group-hover`、`group-focus-within` 显示类。
  - 「原图 ↗」链接指向 `src`，`target="_blank"`。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/components/portal/fulltext/FigureBlock.test.tsx`。Expected: FAIL。
- [ ] **Step 3:** 实现。图片用 `max-w-full w-auto mx-auto`，不放大超过原图。窄屏规则照 spec §5.8。
- [ ] **Step 4:** 运行 Step 2 的命令。Expected: PASS。
- [ ] **Step 5: Commit** `feat(portal): 插图按宽高比选宽度，图注移到标签栏 (PAP-89)`

### Task 11: 数据表

**Files:**
- Modify: `src/components/portal/fulltext/TableBlock.tsx`、`src/app/globals.css`（`.ft-table` 那一段）、`FullTextBody.tsx`（传 `numeric`）
- Test: `tests/components/portal/fulltext/TableBlock.test.tsx`

**Interfaces:**
- Consumes: `numericColumns`、`figureLabel`（Task 4）、`RailRow`
- Produces: `TableBlock` 新增参数 `numeric: boolean[]`

- [ ] **Step 1: Write the failing tests**
  - `numeric=[false,true]` → 第二列的 `th` 和 `td` 都带 `data-numeric`。
  - 表题在标签栏：标签格是 `Table 10`，表题不带 `font-medium`。
  - 外框不再有 `rounded-lg border`。
  - 现有的折叠、横滑提示用例仍然通过。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/components/portal/fulltext/TableBlock.test.tsx`。Expected: FAIL。
- [ ] **Step 3:** 实现。CSS 部分：
  - `.ft-table` 上下各一条 `1px solid var(--ink-900)`。
  - `thead th` 去掉灰底，改为 mono 10px 500 大写，字距 0.06em，颜色 `--fg-3`，下边框 `--border-strong`。
  - `[data-numeric]` 右对齐。
  - 首列吸左时背景跟随 `--bg-page`。
- [ ] **Step 4:** 运行 Step 2 的命令。Expected: PASS。
- [ ] **Step 5: Commit** `feat(portal): 数据表改为三线表，数字列右对齐 (PAP-89)`

### Task 12: 参考文献与许可

**Files:**
- Modify: `src/components/portal/fulltext/ReferenceList.tsx`、`LicenseColophon.tsx`
- Test: `tests/components/portal/fulltext/ReferenceList.test.tsx`、`LicenseColophon.test.tsx`

**Interfaces:**
- Consumes: `citeCountByRef`（Task 9）、`RailRow`
- Produces:
  - `ReferenceListItem` 新增 `citeCount: number`
  - `ReferenceList` 的点击委托跳过 `event.defaultPrevented` 的点击；回到正文的来源锚点取 `link.dataset.citeFrom ?? link.id`

- [ ] **Step 1: Write the failing tests**
  - 序号是两位（`01`）；原文标签是 `"a"` 时照原文。
  - `citeCount=2` → 「↩ 正文 2 处」；`citeCount=1` → 「↩ 正文」；`citeCount=0` → 没有这个链接。
  - PubMed 链接文本是 `PubMed 34815628 ↗`。
  - 56 条 → 按钮文案「展开其余 51 条」。
  - 点击事件被 `preventDefault()` 时不展开、不高亮。
  - 带 `data-cite-from="cite-7"` 的链接被点后，「↩」指向 `#cite-7`。
  - `LicenseColophon`：现有用例全部保留（每一行内容都在），结构断言改成 `dl > dt` 加 `dd`。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/components/portal/fulltext/ReferenceList.test.tsx tests/components/portal/fulltext/LicenseColophon.test.tsx`。Expected: FAIL。
- [ ] **Step 3:** 实现，字号照 spec §5.12 和 §5.14。
- [ ] **Step 4:** 运行 Step 2 的命令。Expected: PASS。
- [ ] **Step 5: Commit** `feat(portal): 参考文献与许可改为标签栏排版，标出正文引用次数 (PAP-89)`

### Task 13: 引用卡片

**Files:**
- Create: `src/components/portal/fulltext/CitationPopover.tsx`（`"use client"`）
- Test: `tests/components/portal/fulltext/CitationPopover.test.tsx`

**Interfaces:**
- Consumes: `ReferenceListItem[]`（Task 12）
- Produces: `CitationPopover({ items }: { items: ReferenceListItem[] })`。它在 `document` 上用**捕获阶段**监听点击，只处理 `[data-ft-body] a[data-cite-ref]`；处理时调用 `preventDefault()`，以引用号为锚点打开卡片（Base UI `Popover`，用受控 `open` 加 `Positioner` 的 `anchor`）。卡片里的「跳到文末」是 `<a href="#ref-…" data-cite-ref={refId} data-cite-from={citeId}>`，交给 `ReferenceList` 原有的委托处理。

- [ ] **Step 1: Write the failing tests**（测试里渲染一段带 `data-ft-body` 的正文、`ReferenceList` 和 `CitationPopover`）
  - 点 `cite-1` → 出现 `role="dialog"`，里面有「参考文献 1」、著录文本、DOI、PubMed、「跳到文末」；地址没变（`location.hash` 为空）。
  - 按 Esc → 卡片关闭，焦点回到 `cite-1`。
  - 键盘聚焦 `cite-1` 后按 Enter → 打开。
  - `data-cite-refs` 有 3 个 → 卡片里列出 3 条。
  - 点「跳到文末」→ 卡片关闭，第 7 条（在收起区）被展开，`location.hash` 是 `#ref-…`，条目的「↩」指向 `#cite-1`。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/components/portal/fulltext/CitationPopover.test.tsx`。Expected: FAIL。
- [ ] **Step 3:** 实现，卡片样式照 spec §5.7。
- [ ] **Step 4:** 运行 Step 2 的命令。Expected: PASS。
- [ ] **Step 5: Commit** `feat(portal): 点击引用号弹出参考文献卡片 (PAP-89)`

### Task 14: 全文区块编排与目录

**Files:**
- Modify: `src/components/portal/fulltext/FullTextSection.tsx`、`FullTextToc.tsx`、`TocList.tsx`、`FullTextTocSheet.tsx`、`FullTextNav.tsx`
- Test: `tests/components/portal/fulltext/` 下的 `FullTextSection`、`FullTextToc`、`FullTextTocSheet`、`FullTextNav` 测试

**Interfaces:**
- Consumes: Task 5 的 `deriveToc` / `deriveTocTail`、Task 6 的 `useReadingProgress` / `remainingMinutes`、Task 9 的 `RenderedFullTextBody`、Task 12 / 13
- Produces:
  - `FullTextSection({ paper, record }: { paper: PaperDetail; record: ReactNode })`
  - `FullTextTocProps` 用 `declarationCount: number; referenceCount: number` 替换 `hasReferences`

- [ ] **Step 1: Write the failing tests**
  - `FullTextSection`：
    - 区块顺序是正文 → `#declarations` → 附录 → `#references` → `#record` → `#license`。
    - 区块头是「全文 · FULL TEXT」加一行许可 · 来源 · PMCID · 抓取日期，出处条的「作者手稿」胶囊还在。
    - 渲染了 `CitationPopover`。
    - 没有可读全文时：说明面板后面紧跟 `#record`，没有 `#declarations` 和 `#references`。
  - `TocList`：
    - 一级项前有 `01`；当前在 4 时展开 `4.1` 到 `4.5`。
    - 虚线下是「声明与附注 9 项」「参考文献 56」「文献档案与许可 ↓」；`declarationCount=0` 时没有第一项。
    - `aria-current` 能落在 `#declarations`、`#references` 和 `#record` 上。
  - `FullTextToc`：
    - 读之前显示「≈ 34 分钟」；mock `useReadingProgress` 返回 `{ body: 0.62, started: true }` 时显示「剩 ≈ 13 分钟」；`body` 为 1 时显示「正文已读完」。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/components/portal/fulltext`。Expected: FAIL。
- [ ] **Step 3:** 实现。`page.tsx` 改成把 `<RecordSection paper={paper}/>` 作为 `record` 传给 `FullTextSection`；删掉 `PublicationDetailView` 的 `record` 参数，同步改它的测试。
- [ ] **Step 4:** 运行 `pnpm vitest run`（全量单测）。Expected: 全 PASS。
- [ ] **Step 5: Commit** `feat(portal): 全文区块按新顺序编排，目录加序号与固定尾项 (PAP-89)`

### Task 15: 面包屑状态

**Files:**
- Modify: `src/components/portal/DetailCrumbs.tsx`、`src/app/papers/[id]/page.tsx`
- Create: `src/components/portal/paper-detail/CrumbStatus.tsx`（`"use client"`）、`src/components/portal/fulltext/FullTextCrumbStatus.tsx`（服务端，取全文后渲染 `CrumbStatus`）
- Test: 新建 `tests/components/portal/paper-detail/CrumbStatus.test.tsx`；`tests/components/portal/DetailCrumbs.test.tsx`（如果没有就新建）

**Interfaces:**
- Consumes: `splitTitle`、`deriveToc`、`useReadingProgress`、`useActiveHeading`
- Produces:
  - `DetailCrumbs` 新增 `status?: ReactNode`，渲染在 `current` 和 `action` 之间；`PublicationDetailView` 新增 `crumbStatus?: ReactNode` 透传给它
  - `positions` 由 `FullTextCrumbStatus` 从 `deriveToc` 拼出：每个目录项 `{ id, label: number }`，再加上 `{ id: "declarations", label: "声明与附注" }`、`{ id: "references", label: "参考文献" }`、`{ id: "record", label: "文献档案" }`（只加页面上存在的）
  - `CrumbStatus({ shortTitle, positions }: { shortTitle: string; positions: { id: string; label: string }[] })`

- [ ] **Step 1: Write the failing tests**
  - `#masthead` 还在视口内时（mock `IntersectionObserver`）→ 不显示短标题；离开后显示。
  - `positions` 为空（没有全文）→ 只显示短标题，没有「已读」。
  - 当前位置是 `ft-sec4-5`，`whole` 为 0.62 → 显示「4.5 · 已读 62%」；当前位置是 `#references` → 显示「参考文献 · 已读 91%」。
  - 期刊详情页不传 `status` → DOM 与改之前一致。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/components/portal/paper-detail/CrumbStatus.test.tsx`。Expected: FAIL。
- [ ] **Step 3:** 实现。`page.tsx` 传 `crumbStatus={<Suspense fallback={<CrumbStatus shortTitle={...} positions={[]}/>}><FullTextCrumbStatus paper={paper}/></Suspense>}`。宽度 ≤ 720px 时隐藏短标题。
- [ ] **Step 4:** 运行 Step 2 的命令。Expected: PASS。
- [ ] **Step 5: Commit** `feat(portal): 面包屑滚过页头后显示短标题、当前章节与已读比例 (PAP-89)`

### Task 16: 骨架屏

**Files:**
- Modify: `src/app/papers/[id]/loading.tsx`、`src/components/portal/fulltext/FullTextSkeleton.tsx`
- Test: `tests/components/portal/fulltext/FullTextSkeleton.test.tsx`

- [ ] **Step 1:** 改测试。骨架的结构要和新版式一致：
  - `loading.tsx`：整宽的页头、事实条、两栏。
  - `FullTextSkeleton`：标签栏加正文行。
  - `TocCardSkeleton` 保留。

  断言骨架里有 `@container/main` 和标签栏占位。
- [ ] **Step 2:** 运行 `pnpm vitest run tests/components/portal/fulltext/FullTextSkeleton.test.tsx`。Expected: FAIL。
- [ ] **Step 3:** 实现。
- [ ] **Step 4:** 再跑一遍。Expected: PASS。
- [ ] **Step 5: Commit** `feat(portal): 详情页骨架跟随新版式 (PAP-89)`

### Task 17: e2e、文档同步、本地验收

**Files:**
- Modify: `tests/e2e/paper-detail.spec.ts`、`fulltext-reader.spec.ts`、`detail-rail-sticky.spec.ts`
- Modify: `patra-portal/docs/design-briefs/v0.11/fulltext-reader.md`
- Add: `docs/patra/design/snapshots/2026-10-20-paper-detail-layout-hifi/`（画布 `project/` 下的 5 个 `.dc.html` 和 `canvas.json`），并在 `docs/patra/design/snapshots/README.md` 补一行

- [ ] **Step 1: 改 e2e**，用例用线上 id `352303012807630907`，网关指 mini：
  - 1440×900：`h1` 高度 ÷ 行高 = 2，副标题 3 行。
  - 1440×900：`figure` 图框宽度。Figure 1 是 612±2px，Figure 2 是 772±2px，Figure 3（1.79）是 772±2px。
  - 1440 和 375 两种宽度：`document.scrollingElement.scrollWidth === clientWidth`。
  - 点第一个引用号 → 出现卡片 →「跳到文末」→ URL 带 `#ref-`，目标条目可见。
  - 375×812：摘要标签格在正文上方，两者的 `boundingBox().y` 不同、`x` 相同。
  - 有全文时目录卡吸顶不晃（`detail-rail-sticky` 现有断言保留，按侧栏的新结构调整选择器）。
- [ ] **Step 2: 运行 e2e**

Run: `PATRA_GATEWAY_BASE_URL=http://100.103.73.27:9528 pnpm test:e2e --workers=1`
Expected: 全 PASS（个别用例冷编译超时的话，单独重跑那一条）

- [ ] **Step 3: 全量门控**

Run: `pnpm lint && pnpm typecheck && pnpm vitest run`
Expected: 全过

- [ ] **Step 4: 同步文档**
  - 简报 §3.4（图、表、引用、参考文献）和侧栏、目录的描述，改成 spec §5 的规则，注明「PAP-89 起」。
  - 存画布快照，README 补一行。
- [ ] **Step 5: 本地验收**

用 `preview_start` 起 `portal-dev`（网关指 mini），对本篇和另外两篇各截 1440、980、375 三档截图：
- 另一篇无全文：`352303012803436544`
- 另一篇多公式：`352303012807630861`

把截图交给用户验收。用户确认后才 commit 文档、push、开 draft PR，描述里写 `Closes PAP-89`。之后按根 CLAUDE.md 的评审流程走。

- [ ] **Step 6: Commit**（文档部分，在用户验收之后）

```bash
git add patra-portal/tests/e2e patra-portal/docs/design-briefs/v0.11/fulltext-reader.md docs/patra/design/snapshots
git commit -m "docs(portal): 设计简报同步 PAP-89 的详情页排版规则并存画布快照 (PAP-89)"
```
