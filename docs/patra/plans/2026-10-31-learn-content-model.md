# 学习站内容模型与校验 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans（用户已定：当前会话内联执行，不走 subagent-driven）. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 patra-learn 换成「MDX 条目 + 路线 / 主题 / 标签 YAML」的内容模型，带全套内容校验、构建时索引、体检报告，并删除旧地铁图站。

**Architecture:** `src/lib/content/` 全是纯函数：`parse` 把原文变成 catalog，几组规则各自产出 `ContentIssue[]`，`validate` 汇总；只有 `load.ts` 碰 fs、`scripts/` 碰 git。组件登记表 `src/teaching/registry.ts` 只放 zod schema 与角色，`src/mdx-components.tsx` 放实现。页面在 `next build` 预渲染时调索引函数。

**Tech Stack:** Next.js 16（Turbopack）、`@next/mdx`、unified / remark-mdx、zod、yaml、Vitest（node 环境）、tsx、Biome。

**Spec:** `docs/patra/specs/2026-10-31-learn-content-model-design.md`

## Global Constraints

- 所有命令在 `patra-learn/` 下跑；Node 24、pnpm 10。
- **不提交、不推送**（编排会话约束）；每个任务结束跑本任务测试即可，没有 commit 步骤。
- 不碰 `content/curriculum.md`。`.design-sync/`、`patra-learn/.design-sync-assets/` 及 `.gitignore` 对应行在编排会话确认后删除。
- 加载器只读 `content/entries/*.mdx`、`content/routes/*.yaml`、`content/topics/*.yaml`、`content/tags.yaml`。
- slug 正则 `^[a-z0-9]+(-[a-z0-9]+)*$`；版本 `^v\d+\.\d+$`；issue `^PAP-\d+$`；SHA 40 位小写十六进制或字面量 `pending`。
- 规则 id 用 spec 第 6 节表格里的字符串，一字不改。
- 测试源码与 fixture 里不写任何真实 IP、主机名；假凭据运行时拼接（`"ghp_" + "a".repeat(36)`）。
- 代码注释用 `///`（沿用现有 learn 代码风格）、中文；JSX 中文不句中断行。
- 测试命名中文描述（沿用现有 learn 测试风格），`describe` / `it`。

## Review Focus

1. **frontmatter 后正文的定位**：`---` 之间是 CRLF 或文件只有 frontmatter 没正文时，诊断题 / 复述题规则应报 `diagnostic-missing` / `recap-missing`，不应崩溃 → Task 3 加「空正文」用例。
2. **组件嵌套**：诊断题写在某个组件里面（不是顶层第一块）应当报 `diagnostic-missing`；嵌套在组件 children 里的未知组件也要报 `component-unknown` → Task 3 加「嵌套未知组件」用例。
3. **同一篇自引用与长环**：`a → b → c → a` 要报环且只报一次（不是每个节点各报一次）→ Task 6 用例断言 `prerequisite-cycle` 条数为 1。
4. **公开边界误伤**：`192.0.2.10`、`10.0.0.0/8`、`token: ${API_TOKEN}`、`Mac mini` 不应报 → Task 4 正例用例。
5. **空内容构建**：`content/entries/` 为空时 `pnpm build` 必须成功、首页显示「内容建设中」→ Task 10 验证步骤。

---

### Task 1: 删除旧站、装依赖、建内容目录

**Files:**
- Delete: `src/content/`、`src/app/{archive,cheatsheet,glossary,lines}/`、`src/components/`（11 个旧组件全部）、`src/lib/content.ts`、`src/lib/content.test.ts`、`src/lib/progress.ts`、`src/lib/progress.test.ts`
- Modify: `src/app/layout.tsx`（去掉 TopBar）、`src/app/page.tsx`（暂时只放标题与「内容建设中」）、`tests/smoke.spec.ts`（只测首页 200 且有标题）、`package.json`、`tsconfig.json`（include 加 `scripts/**/*.ts`）
- Create: `content/entries/.gitkeep`、`content/routes/.gitkeep`、`content/topics/.gitkeep`、`content/tags.yaml`（spec 4.3 的 15 个标签，按 spec 顺序）

- [ ] **Step 1:** 删除上述文件；`grep -rn "content/lines\|metro\|progress" src tests` 无结果。
- [ ] **Step 2:** `pnpm add @next/mdx @mdx-js/loader @mdx-js/react remark-frontmatter remark-gfm yaml zod`；`pnpm add -D @types/mdx unified remark-parse remark-mdx tsx`。
- [ ] **Step 3:** 验证 `pnpm lint && pnpm typecheck && pnpm test && pnpm build` 全绿（test 剩 fonts / globals 两个文件）。

### Task 2: schema 与解析

**Files:**
- Create: `src/lib/content/types.ts`、`src/lib/content/schema.ts`、`src/lib/content/parse.ts`
- Test: `src/lib/content/parse.test.ts`

**Interfaces:**
- Produces（`types.ts`）：
  - `EntryType = "concept" | "troubleshooting" | "decision" | "pitfall"`；`Difficulty = "intro" | "basic" | "advanced" | "practice"`；`DIFFICULTY_RANK: Record<Difficulty, number>`（0..3）
  - `Source { version: string; issue?: string; pr?: number }`
  - `EntryMeta { title; summary; type; difficulty; tags: string[]; prerequisites: string[]; topics: string[]; sources: Source[]; personalEnv: boolean; supersededBy?: string }`
  - `Entry { slug: string; file: string; meta: EntryMeta; body: string }`（`body` = 去掉 frontmatter 后的正文）
  - `Route { slug; file; title; summary; order: number; entries: string[] }`；`Topic { slug; file; title; summary }`；`Tag { slug: string; label: string }`
  - `ContentCatalog { entries: Entry[]; routes: Route[]; topics: Topic[]; tags: Tag[] }`
  - `RawFile { file: string; text: string }`（`file` 相对 patra-learn，如 `content/entries/outbox.mdx`）
  - `RawContent { entries: RawFile[]; routes: RawFile[]; topics: RawFile[]; tags: RawFile | null }`
  - `ContentIssue { rule: string; file: string; message: string }`
- Produces（`parse.ts`）：`splitFrontmatter(text: string): { data: string; body: string } | null`；`parseEntryFile(raw: RawFile): { entry?: Entry; issues: ContentIssue[] }`；`parseContent(raw: RawContent): { catalog: ContentCatalog; issues: ContentIssue[] }`。解析失败的文件不进 catalog。

- [ ] **Step 1: 写失败测试**（每条一个 `it`）：
  - 合法条目 → `catalog.entries[0]` 的 `slug === "outbox"`、`meta.topics` 缺省为 `[]`、`meta.personalEnv === false`、`body` 不含 `---`
  - 文件名 `Outbox_1.mdx` → issue `slug-invalid`
  - 无 frontmatter / YAML 语法错 / 缺 `title` / `difficulty: hard` / 多一个未知字段 `level` / `sources: [{ version: "0.16" }]` → 各得 `frontmatter-invalid`
  - CRLF 换行的 frontmatter 也能解析
  - 路线缺 `order`、主题 YAML 语法错、`tags.yaml` 项缺 `label` → `yaml-invalid`
  - `tags: null`（没有 tags.yaml）→ `yaml-invalid`，file 为 `content/tags.yaml`
- [ ] **Step 2:** `pnpm vitest run src/lib/content/parse.test.ts` → FAIL（模块不存在）
- [ ] **Step 3:** 实现。schema 全用 `z.strictObject`；`message` 用中文并带上 zod 的路径（`issues.map(i => i.path.join(".") + "：" + i.message)`）。
- [ ] **Step 4:** 同命令 → PASS

### Task 3: 组件登记表与正文规则

**Files:**
- Create: `src/teaching/registry.ts`、`src/lib/content/mdx-rules.ts`
- Test: `src/lib/content/mdx-rules.test.ts`

**Interfaces:**
- Produces（`registry.ts`）：`ComponentRole`、`ComponentSpec { props: z.ZodObject; children: "none" | "optional" | "required"; role?: ComponentRole }`、`componentRegistry`（`CodeRef`、`Diagnose`、`Recap`、`Transfer`，参数按 spec 第 5 节；`CodeRef.at` 只校验是非空字符串，格式归 Task 5）、`type ComponentName = keyof typeof componentRegistry`
- Produces（`mdx-rules.ts`）：`CodeRefUse { entry: string; file: string; at: string; lines?: string }`；`checkEntryBody(input: { slug: string; file: string; body: string }, registry: Record<string, ComponentSpec>): { issues: ContentIssue[]; codeRefs: CodeRefUse[] }`
- 解析：`unified().use(remarkParse).use(remarkMdx).use(remarkGfm).parse(body)`；属性值字面量求值支持 Literal、ArrayExpression、ObjectExpression（非计算键）、`-数字`、无插值 TemplateLiteral；`mdxFlowExpression` / `mdxTextExpression` 的 estree 无语句（只有注释）时放行。

- [ ] **Step 1: 写失败测试**（测试里用一个只含 `Diagnose`、`Recap`、`Transfer`、`CodeRef` 的登记表；合法正文 = `<Diagnose question="…" />` 开头、`<Recap prompt="…" />` 结尾）：
  - 合法正文 → `issues` 为空
  - `import X from "x"` → `mdx-esm`；`{1 + 1}` → `mdx-expression`；`{/* 占位 */}` → 无 issue
  - `<Foo />`、`<div>`、嵌套在 `Recap` 里的 `<Foo />` → 各 `component-unknown`
  - `<Diagnose question={q} />`、`<Diagnose {...p} />` → `component-prop-dynamic`
  - `<Diagnose />`（缺必填）、`<Diagnose question={3} />`、`<Diagnose question="?" extra="x" />` → `component-props-invalid`
  - `<CodeRef at="a@pending">文字</CodeRef>` → `component-children`
  - 第一块是段落 → `diagnostic-missing`；`Diagnose` 只出现在段落里或嵌套在组件里 → `diagnostic-missing`；最后一块是段落 → `recap-missing`；`Transfer` 结尾 → 无 issue
  - 空正文 → 同时 `diagnostic-missing` 与 `recap-missing`
  - `<Diagnose question="` 未闭合 → 只有 `mdx-syntax`
  - 正文含 `<CodeRef at="p/a.ts@pending" lines="3-5" />` → `codeRefs` 等于 `[{ entry, file, at: "p/a.ts@pending", lines: "3-5" }]`
  - GFM 表格解析为表格不报错
  - 首尾判断跳过注释表达式
- [ ] **Step 2:** `pnpm vitest run src/lib/content/mdx-rules.test.ts` → FAIL
- [ ] **Step 3:** 实现 `registry.ts` 与 `mdx-rules.ts`（`unist-util-visit` 若未直接安装，用手写递归遍历 `children`）。
- [ ] **Step 4:** 同命令 → PASS

### Task 4: 公开边界

**Files:**
- Create: `src/lib/content/public-boundary.ts`
- Test: `src/lib/content/public-boundary.test.ts`

**Interfaces:**
- Produces：`checkPublicBoundary(raw: RawFile): ContentIssue[]`，每处命中一条，`message` 带行号与命中类别（不回显完整凭据值，只回显前 4 位 + `…`）。

- [ ] **Step 1: 写失败测试**：
  - `private-address`：`100.64.1.2`、`100.127.0.9`、`foo.tail1234.ts.net`、`10.1.2.3`、`172.20.0.5`、`192.168.1.10`、`alice-mac-mini`、`bob-macbook-pro`
  - 不报：`100.63.0.1`、`100.128.0.1`、`127.0.0.1`、`localhost`、`10.0.0.0/8`、`192.168.0.0/16`、`192.0.2.10`、`198.51.100.7`、`203.0.113.5`、`部署在 Mac mini 上`、`版本 v1.10.2.3`（四段版本号不在内网段时不报；`10.2.3.4` 这类会报，接受）
  - `secret`：私钥头 `"-----BEGIN " + "RSA PRIVATE KEY-----"`、`"ghp_" + "a".repeat(36)`、`"AKIA" + "A".repeat(16)`、`"sk-ant-" + "b".repeat(30)`、`"xoxb-" + "1".repeat(12)`、三段 JWT（`"eyJ" + "x".repeat(12) + "." + …`）、`password:` 后跟 12 位以上字母数字混合的值、`API_KEY=` 后跟同类值（测试里运行时拼接）
  - 不报：`token: xxxxxxxx`、`password: ${DB_PASSWORD}`、`secret: <你的密钥>`、`password: changeme`（无数字或不足 12 位）
  - 命中行号正确（第 3 行的命中 message 含 `第 3 行`）
- [ ] **Step 2:** `pnpm vitest run src/lib/content/public-boundary.test.ts` → FAIL
- [ ] **Step 3:** 实现：IPv4 先用 `\b(\d{1,3})\.(\d{1,3})\.(\d{1,3})\.(\d{1,3})\b(?!\/\d)` 抓出再按网段数值判断；凭据赋值规则值须 ≥12 位且同时含字母与数字。
- [ ] **Step 4:** 同命令 → PASS

### Task 5: 代码引用

**Files:**
- Create: `src/lib/content/code-refs.ts`
- Test: `src/lib/content/code-refs.test.ts`

**Interfaces:**
- Consumes：Task 3 的 `CodeRefUse`
- Produces：`ParsedCodeRef { path: string; sha: string | "pending" }`；`parseCodeRefAt(at: string): ParsedCodeRef | null`（`@` 取最后一个；路径不能以 `/`、`./`、`../` 开头，不能含空白）；`checkCodeRefs(refs: CodeRefUse[], fileExists: (repoPath: string) => boolean): ContentIssue[]`；`GITHUB_REPO_URL = "https://github.com/linqibin0826/patra"`；`codeRefUrl(ref: ParsedCodeRef, lines?: string): string | null`（`pending` 返回 null；`lines="12-30"` → `#L12-L30`，`"12"` → `#L12`）

- [ ] **Step 1: 写失败测试**：
  - `parseCodeRefAt("patra-api/a/B.java@" + "a".repeat(40))` → `{ path: "patra-api/a/B.java", sha: "a".repeat(40) }`；`"x@pending"` → sha `pending`
  - 返回 null：缺 `@`、短 SHA（7 位）、大写 SHA、`/abs@…`、`../x@…`、路径含空格
  - `checkCodeRefs`：格式错 → `code-ref-invalid`；`lines="abc"` → `code-ref-invalid`；`fileExists` 返回 false → `code-ref-missing`（`pending` 也查存在）；都对 → `[]`
  - `codeRefUrl` 三种 lines 情形与 `pending`
- [ ] **Step 2:** `pnpm vitest run src/lib/content/code-refs.test.ts` → FAIL
- [ ] **Step 3:** 实现
- [ ] **Step 4:** 同命令 → PASS

### Task 6: 跨篇规则

**Files:**
- Create: `src/lib/content/catalog-rules.ts`
- Test: `src/lib/content/catalog-rules.test.ts`（测试内用一个 `entry(slug, overrides)` 小工厂构造 catalog）

**Interfaces:**
- Produces：`checkEntryTags(entry: Entry, tags: Tag[]): ContentIssue[]`（单篇规则，Task 8 的 check-file 也用）；`checkCatalog(catalog: ContentCatalog): ContentIssue[]`（含 `checkEntryTags` 以外的全部跨篇规则）

- [ ] **Step 1: 写失败测试**：
  - `tag-unknown`、`topic-unknown`、`topic-needs-version`（挂主题但 `sources` 为空）
  - `prerequisite-missing`（不存在 / 指向自己）；`a→b→c→a` 只报 1 条 `prerequisite-cycle`，message 含 `a → b → c → a` 这样的环路；两个不相交的环报 2 条
  - `superseded-missing`（不存在 / 自己）
  - 路线：`route-entry-missing`、`route-entry-duplicate`、两条路线同 `order` → `route-order-duplicate`、`basic` 之后接 `intro` → `route-difficulty-descending`（同档相邻不报）、路线 `[b, a]` 且 `b` 的前置是 `a` → `route-prerequisite-after`（前置不在本路线不报）、`route-personal-entry`、`route-superseded-entry`
  - 全合法 catalog → `[]`
- [ ] **Step 2:** `pnpm vitest run src/lib/content/catalog-rules.test.ts` → FAIL
- [ ] **Step 3:** 实现；成环用 DFS 三色标记，环按「最小 slug 起头」规范化后去重。
- [ ] **Step 4:** 同命令 → PASS

### Task 7: 索引

**Files:**
- Create: `src/lib/content/indexes.ts`
- Test: `src/lib/content/indexes.test.ts`

**Interfaces:**
- Produces：`compareVersions(a: string, b: string): number`；`RouteView { slug; title; summary; order; entries: Entry[] }`、`buildRouteIndex(catalog): RouteView[]`（按 `order` 升序，跳过不存在的 slug）；`TopicView { slug; title; summary; entries: Entry[] }`、`buildTopicIndex(catalog): TopicView[]`（主题按 slug；条目按最早出处版本升序、同版本按 slug；排除 `personalEnv`）；`TagView { slug; label; count: number; entries: Entry[] }`、`buildTagIndex(catalog): TagView[]`（标签表顺序，含个人环境条目，`count === entries.length`）；`VersionView { version: string; entries: Entry[] }`、`buildVersionIndex(catalog): VersionView[]`（版本降序；一篇有多个版本就在多处出现、同版本只出现一次；排除 `personalEnv`）；`findEntry(catalog, slug): Entry | undefined`

- [ ] **Step 1: 写失败测试**：`compareVersions("v0.9","v0.16") < 0`；四个索引各一组断言（排序、个人环境排除 / 保留、被取代条目保留、零计数标签也出现）
- [ ] **Step 2:** `pnpm vitest run src/lib/content/indexes.test.ts` → FAIL
- [ ] **Step 3:** 实现
- [ ] **Step 4:** 同命令 → PASS

### Task 8: 汇总校验、加载器、真实内容测试

**Files:**
- Create: `src/lib/content/validate.ts`、`src/lib/content/load.ts`、`src/lib/content/catalog.ts`
- Test: `src/lib/content/validate.test.ts`、`src/lib/content/content.test.ts`（真实内容）

**Interfaces:**
- Consumes：Task 2～6 的函数、`componentRegistry`
- Produces：
  - `ValidationDeps { fileExists: (repoPath: string) => boolean }`
  - `validateContent(raw: RawContent, deps: ValidationDeps): { catalog: ContentCatalog; issues: ContentIssue[]; codeRefs: CodeRefUse[] }`（解析 + 每篇正文规则 + 公开边界（所有 RawFile）+ 代码引用 + `checkEntryTags` + `checkCatalog`；issues 按 file、rule 排序）
  - `validateEntryFiles(files: RawFile[], tags: Tag[], deps: ValidationDeps): ContentIssue[]`（单篇规则：解析、`checkEntryTags`、正文规则、公开边界、代码引用；跳过 `checkCatalog`；slug 规则也跳过，模板文件名不受限）
  - `readContent(learnRoot: string): RawContent`（`load.ts`，只读四类路径，按文件名排序）；`repoFileExists(learnRoot: string): (repoPath: string) => boolean`（仓库根 = `learnRoot/..`）
  - `getCatalog(): ContentCatalog`（`catalog.ts`，读 `process.cwd()`，模块级缓存；解析有 issue 时抛错，错误消息列出全部 issue）
- [ ] **Step 1: 写失败测试**：
  - `validate.test.ts`：一个各规则都合法的内存 `RawContent` → `issues` 为空；往里加一篇缺诊断题且含 tailnet IP 的条目 → 同时得到 `diagnostic-missing` 与 `private-address`；`validateEntryFiles` 对前置为占位 slug 的模板不报 `prerequisite-missing`、对未知标签报 `tag-unknown`
  - `content.test.ts`：`validateContent(readContent(learnRoot), { fileExists: repoFileExists(learnRoot) })` 的 `issues` 等于 `[]`（`expect(issues, format(issues)).toEqual([])`，失败时打印全部问题）；`readContent` 不读 `content/curriculum.md`（断言所有 `file` 都匹配四类路径）
- [ ] **Step 2:** `pnpm vitest run src/lib/content` → 新测试 FAIL
- [ ] **Step 3:** 实现
- [ ] **Step 4:** `pnpm test` → PASS

### Task 9: 体检报告与单篇校验脚本

**Files:**
- Create: `src/lib/content/report.ts`、`scripts/content-report.ts`、`scripts/content-check-file.ts`
- Modify: `package.json`（`"content:report": "tsx scripts/content-report.ts"`、`"content:check-file": "tsx scripts/content-check-file.ts"`）
- Test: `src/lib/content/report.test.ts`

**Interfaces:**
- Produces（`report.ts`）：
  - `GitPort { head(): string; hasCommit(sha: string): boolean; hasPath(rev: string, path: string): boolean; commitsTouching(sha: string, path: string): string[] /* sha..HEAD，新到旧 */ }`
  - `CodeRefStatus = "unchanged" | "changed" | "missing" | "not-in-commit" | "unknown-sha" | "pending"`
  - `CodeRefReport { entry; path; sha; lines?; status: CodeRefStatus; commitsSince?: number; lastChangedBy?: string }`
  - `HealthReport { generatedAt: string; head: string; issues: ContentIssue[]; codeRefs: CodeRefReport[] }`
  - `codeRefStatus(ref: ParsedCodeRef, git: GitPort): Pick<CodeRefReport, "status" | "commitsSince" | "lastChangedBy">`
  - `buildReport(input: { issues: ContentIssue[]; codeRefs: CodeRefUse[]; git: GitPort; now: Date }): HealthReport`（格式错的引用不进 `codeRefs`，它已在 issues 里）
- 判断顺序：`pending` → `unknown-sha`（`!hasCommit`）→ `not-in-commit`（`!hasPath(sha)`）→ `missing`（`!hasPath("HEAD")`）→ `commitsTouching` 为空 `unchanged`（`commitsSince: 0`）否则 `changed`。
- 脚本：`content-report.ts` 用 `execFileSync("git", …)` 实现 GitPort（`rev-parse HEAD`、`cat-file -e <sha>^{commit}`、`cat-file -e <rev>:<path>`、`log --format=%H <sha>..HEAD -- <path>`），`JSON.stringify(report, null, 2)` 打到 stdout，`issues.length > 0` 时 `process.exitCode = 1`；`content-check-file.ts` 读参数里的文件与 `content/tags.yaml`，按行打印 `${rule}\t${file}\t${message}`，有问题退出码 1、无问题打印「通过」。
- [ ] **Step 1: 写失败测试**：六种状态各一个用例（假 GitPort）；`changed` 时 `commitsSince === 2`、`lastChangedBy` 是最新那条；`buildReport` 透传 issues、`generatedAt === now.toISOString()`
- [ ] **Step 2:** `pnpm vitest run src/lib/content/report.test.ts` → FAIL
- [ ] **Step 3:** 实现 `report.ts` 与两个脚本
- [ ] **Step 4:** PASS；再跑 `pnpm content:report`（空内容）→ 输出 JSON、`issues: []`、退出码 0；用一篇临时 MDX 跑 `pnpm content:check-file /tmp 外的临时路径`（放 scratchpad）→ 打印预期规则、退出码 1

### Task 10: 渲染、占位组件、最小页面

**Files:**
- Modify: `next.config.ts`（`createMDX({ options: { remarkPlugins: ["remark-frontmatter", "remark-gfm"] } })` 包裹现有配置，保留 `output: "standalone"`、`reactStrictMode`）、`src/app/page.tsx`
- Create: `src/mdx-components.tsx`、`src/teaching/placeholders.tsx`（`Diagnose`、`Recap`、`Transfer` 占位实现）、`src/components/code-ref.tsx`、`src/app/entries/[slug]/page.tsx`
- Test: `src/teaching/registry.test.ts`

**Interfaces:**
- Consumes：`componentRegistry`、`getCatalog`、`buildRouteIndex`、`findEntry`、`parseCodeRefAt`、`codeRefUrl`
- Produces：`mdxComponents`（`src/mdx-components.tsx` 导出常量 + `useMDXComponents()`）；`Diagnose` 接收作者参数外加页面注入的 `prerequisites?: { slug: string; title: string }[]`
- 条目页：`generateStaticParams` 返回 `getCatalog().entries.map(({ slug }) => ({ slug }))`、`dynamicParams = false`、`import(\`../../../../content/entries/${slug}.mdx\`)` 动态加载，`components={{ Diagnose: (p) => <Diagnose {...p} prerequisites={…} /> }}` 注入前置。
- [ ] **Step 1: 写失败测试** `registry.test.ts`：`Object.keys(mdxComponents).sort()` 等于 `Object.keys(componentRegistry).sort()`（`mdx-components.tsx` 只导出登记表里的组件，不放 HTML 标签覆盖）
- [ ] **Step 2:** `pnpm vitest run src/teaching` → FAIL
- [ ] **Step 3:** 实现组件、页面、配置
- [ ] **Step 4:** `pnpm test` → PASS
- [ ] **Step 5:** 空内容 `pnpm build` → 成功；`.next/server/app/index.html` 含「内容建设中」
- [ ] **Step 6:** 临时加一篇合法条目（含 GFM 表格、`CodeRef`、frontmatter）+ 一条路线 + 一个主题，`pnpm test && pnpm build` → 成功，构建日志里 `/entries/[slug]` 是 SSG（●），生成的 HTML 不含 frontmatter 文本、含表格 `<table>` 与 GitHub 永久链接；验证完删除临时文件，再跑一次 `pnpm test && pnpm build`

### Task 11: 文档与收尾验证

**Files:**
- Modify: `patra-learn/CLAUDE.md`、`patra-learn/README.md`、`tests/smoke.spec.ts`（若 Task 1 已改则核对）

- [ ] **Step 1:** CLAUDE.md 重写「内容即代码」「测试约定」两节：条目 / 路线 / 主题 / 标签的位置与格式要点、组件登记表加组件两步、`pnpm test` / `content:check-file` / `content:report` 各干什么、指向工程 spec；删掉 MetroMap 一条；部署与端口描述不动（PAP-162 改）。README 改目录结构、内容模型、命令表，去掉写着本机主机名的访问地址（改成「仅 tailnet 内网」）。
- [ ] **Step 2:** `pnpm lint && pnpm typecheck && pnpm test && pnpm build` 全绿；`pnpm test:e2e` 本地跑通首页 smoke。
- [ ] **Step 3:** `git status` 核对改动只在 `patra-learn/` 与 `docs/patra/{specs,plans}/`；`grep -rn "content/curriculum" patra-learn/src` 无结果。
