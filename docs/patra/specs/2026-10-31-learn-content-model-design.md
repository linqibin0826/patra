# 学习站内容模型与校验（PAP-155）

> **Issue**：PAP-155（v0.17 Learn，Milestone ① Foundation Ready）
> **上游**：release spec `docs/patra/release-specs/v0.17-learn.md` 第 2.1 节、决策 B / C / D / E / F / H
> **下游**：PAP-153（大纲）、PAP-154（维护技能）、PAP-157（组件库）、PAP-158～161（写内容）、PAP-162（页面）都照本文的格式写
> **日期**：2026-10-31

标「**我的判断**」的是 release spec 没写死、由本 Issue 定下的；其余是 release spec 已定的决策落地。

**用户确认（2026-10-31，经编排会话转达，原话「都按建议」）**：本文全部「我的判断」均已确认，其中与 release spec 字面不同的 6 处——路线 / 主题各一个文件、主题按出处版本排序、标签固定清单、内网地址拦截规则、两条新路线规则（本路线前置在前；个人环境与已被取代的条目不进路线）、不放示例条目——由编排会话同步改 release spec。

## 1. 目的

学习站从「TS/TSX 写死的地铁图课程」换成「Agent 持续往里加 MDX 条目」的内容站。内容会由多个会话并行写、以后还会跟着每一版增长，所以要有：

1. 一套固定的条目格式（元数据 + 正文写法），写内容的 Agent 照着写不用猜；
2. 一道测试防线，把 release spec 定下的规矩变成「不满足就红」：前置、路线难度、组件、公开边界、代码引用、诊断题与复述 / 迁移题；
3. 页面需要的四种索引（路线 / 主题 / 标签 / 版本），构建时从内容算出来；
4. 一份体检报告，列出钉住的代码之后又被改过的引用，交给维护技能处理。

**不做**：页面设计与实现（PAP-162）、教学组件本身（PAP-157，本 Issue 只留登记表接口和占位组件）、条目内容（PAP-158～161）、篇目清单（PAP-153）。

## 2. 目录

```
patra-learn/
├── content/
│   ├── entries/<slug>.mdx      # 条目，平铺；文件名就是 slug
│   ├── routes/<slug>.yaml      # 一条路线一个文件
│   ├── topics/<slug>.yaml      # 一个主题一个文件
│   ├── tags.yaml               # 标签表（全站一份）
│   └── curriculum.md           # PAP-153 的大纲，不归内容模型：加载器不读、不当条目、不删
├── scripts/
│   ├── content-report.ts       # pnpm content:report → 体检报告（JSON，stdout）
│   └── content-check-file.ts   # pnpm content:check-file <路径…> → 只做单篇规则（给技能模板用）
└── src/
    ├── lib/content/            # 内容模型：解析、校验、索引（纯函数 + 一个 fs 读取入口）
    ├── teaching/registry.ts    # 组件登记表（名字 → 参数 schema / 子内容 / 角色）
    ├── mdx-components.tsx      # 名字 → React 实现（条目页编译 MDX 时整份传入）
    └── app/                    # 最小首页 + 最小条目页（PAP-162 重做）
```

- **加载器只读** `content/entries/*.mdx`、`content/routes/*.yaml`、`content/topics/*.yaml`、`content/tags.yaml`，`content/` 下其他文件一律不碰。
- **slug**：`^[a-z0-9]+(-[a-z0-9]+)*$`，只从文件名来，frontmatter 里不写 slug。路线、主题同理。
- **一条路线 / 一个主题一个文件**（**我的判断**）：release spec 写「路线与主题各一份 YAML」，这里理解为每条路线、每个主题各一份。这样 PAP-158～160 并行写不同路线时不会改到同一个文件，与决策 C「按路线分工不改同一个文件」一致。

## 3. 条目格式

### 3.1 frontmatter

条目开头是 YAML frontmatter（**我的判断**：不用 `export const metadata`。校验和索引都要在不编译 MDX 的前提下读到元数据，写内容的 Agent 写 YAML 也更不容易错）。字段名和取值一律英文常量，页面展示的中文名在代码里映射（**我的判断**）。

```yaml
---
title: 发件箱：先写库，再发消息
summary: 业务数据和「要发的消息」写进同一个事务，提交后再由后台投递，消息就不会因为服务崩溃而丢。
type: concept            # concept 概念 | troubleshooting 排查 | decision 决策 | pitfall 坑
difficulty: advanced     # intro 入门 | basic 基础 | advanced 进阶 | practice 实战
tags: [backend, messaging]
prerequisites: [message-queue, transaction]
topics: [message-delivery]
sources:
  - version: v0.16
    issue: PAP-140
    pr: 343
personalEnv: false
supersededBy: outbox-with-modulith
---
```

| 字段 | 必填 | 规则 |
|---|---|---|
| `title` | 是 | 非空字符串 |
| `summary` | 是 | 非空字符串，一两句话；列表、搜索、「今天读这一条」卡片用 |
| `type` | 是 | `concept` / `troubleshooting` / `decision` / `pitfall` |
| `difficulty` | 是 | `intro` / `basic` / `advanced` / `practice`，顺序即难度高低 |
| `tags` | 是 | 至少 1 个，每个都必须在 `content/tags.yaml` 里，不重复 |
| `prerequisites` | 是 | 条目 slug 列表，可以是 `[]`；每个都必须存在，不能指向自己，整体不成环 |
| `topics` | 否 | 主题 slug 列表，默认 `[]`；每个都必须在 `content/topics/` 里 |
| `sources` | 否 | 出处列表，默认 `[]`。每项 `version` 必填（`v0.16` 这种 `^v\d+\.\d+$`），`issue`（`^PAP-\d+$`）与 `pr`（正整数）可选 |
| `personalEnv` | 否 | 默认 `false`。`true` 表示只跟作者本机环境有关（决策 E） |
| `supersededBy` | 否 | 决策被推翻时，指向取代它的条目 slug；必须存在、不能指向自己（PAP-154「改旧条目」用） |

未知字段直接报错（schema 是 strict 的），防止拼错字段名后被静默忽略。

**字段名对齐**：PAP-154 交接笔记里的占位名 `difficulty`、`personalEnv`、`supersededBy` 原样采用；它笔记里的 `codeRefs` 不进 frontmatter，代码引用写在正文里（见 3.3），因为页面要在讲到那段代码的地方给出链接。

### 3.2 正文规则（MDX）

正文是 Markdown（含 GFM：表格、删除线、任务列表，**我的判断**：排查与决策条目几乎都要用表格）加登记表里的组件。组件标签与内容写在同一行（`<Recap prompt="…">答案</Recap>`）时，MDX 会把它解析成段落里的行内组件；首尾判断把「只含这一个组件的段落」当组件本身。为了让「组件与参数合法」能静态校验，也为了守住决策 D（交互只从组件库来），正文有以下限制（**我的判断**，都是 D 的推论）：

1. 不许 `import` / `export`：组件全部由 `mdx-components.tsx` 注入。
2. JSX 只能是登记表里的组件；小写 HTML 标签（`<div>`、`<details>` 等）也不行，排版用 Markdown。
3. 组件属性只能写字面量：`question="…"`、`step={3}`、`options={["A", "B"]}`、`{ … }` 对象字面量、布尔简写 `open`；不许变量、函数、展开 `{...x}`。
4. 除注释 `{/* … */}` 外不许写 `{表达式}`。注释放行，技能模板里的占位说明就写成注释。
5. **开头是诊断题，结尾是复述题或迁移题**：正文第一个块（跳过注释）必须是登记表里角色为 `diagnostic` 的组件；最后一个块必须是角色为 `recap` 或 `transfer` 的组件。对应 2.1 节「开头一个诊断问题……结尾一道复述题或迁移题」。

### 3.3 代码引用

```mdx
<CodeRef at="patra-api/patra-tool/patra-tool-events/src/main/java/dev/patra/tool/events/OutboxRelay.java@4a1328748c1e9f0a6c3b2d5e7f8091a2b3c4d5e6" lines="40-72" />
```

- `at` 写成 `路径@提交SHA`（决策 F 的写法）。路径相对仓库根；SHA 写完整 40 位（**我的判断**：短 SHA 会随仓库变大产生歧义）。
- 代码还没进 main 时写 `路径@pending`，校验放行，体检报告把它列为 `pending`，提醒合并后补钉（回应 PAP-154 交接笔记第 2 条）。钉的提交应当是 main 上的提交：功能分支 squash 合并后，分支上的 SHA 不在 main 里。
- `lines` 可选，`12` 或 `12-30`。
- 页面渲染成 GitHub 永久链接 `https://github.com/linqibin0826/patra/blob/<sha>/<路径>#L12-L30`。
- `CodeRef` 是内容模型自己的组件，不等 PAP-157。

## 4. 路线、主题、标签

### 4.1 路线 `content/routes/<slug>.yaml`

```yaml
title: 消息与事件
summary: 从「为什么要异步」一直读到发件箱、去重与死信。
order: 4
entries:
  - why-async
  - message-queue
  - exchange-queue-binding
  - outbox
```

- `order`：路线在站内的展示顺序，正整数，全站不重复。
- `entries`：按阅读顺序排的条目 slug，至少 1 个，不重复，每个都必须存在。
- 一篇可以出现在多条路线里（决策 C）；路线成员只由路线文件决定，条目 frontmatter 不写路线。

路线规则：

| 规则 | 来源 |
|---|---|
| 难度不倒排：后一篇的 `difficulty` 不低于前一篇 | Issue AC |
| 本路线里的前置排在本篇之前：条目的某个前置如果也在这条路线里，它必须排在前面 | 验证场景 1「每一篇都不需要先懂后面的内容」（**我的判断**） |
| `personalEnv: true` 的条目不能进路线 | 决策 E |
| 已被取代（有 `supersededBy`）的条目不能进路线，路线该换成取代它的那篇 | 路线是给人顺着学的，不该教已被推翻的做法（**我的判断**） |

### 4.2 主题 `content/topics/<slug>.yaml`

```yaml
title: 消息怎么才不丢
summary: 从发件箱到至少一次投递、消费端去重，Patra 在不同版本里一次次碰到同一个问题。
```

- 主题成员由条目 frontmatter 的 `topics` 决定，主题文件只放标题与简介（**我的判断**）。release spec 写的是「列篇目顺序」，但主题页要「按时间展示它怎么演进」，顺序可以从出处版本算出来；在两处都写成员会让 Agent 每次改两个地方、还要校验两边一致。
- 主题页的顺序：按条目 `sources` 里最早的版本号升序（`v0.9` < `v0.16`，按数字比较），同版本按 slug。挂了主题的条目至少要有一条带版本的出处，否则无法排序，校验报错。

### 4.3 标签 `content/tags.yaml`

```yaml
- slug: backend
  label: 后端
- slug: network
  label: 网络
```

- 受控标签表（**我的判断**）：条目只能用表里的标签，防止「数据库 / DB / database」各写各的；标签页 URL 用英文 slug。
- 初始表：按 release spec 2.1 节的后端 backend、网络 network、数据库 database、消息 messaging、部署 deployment、测试 testing、前端 frontend、DDD ddd、Java java、Spring spring，加 PAP-153 提议、用户确认的安全 security、Redis redis、Git git、Docker docker、CI/CD ci-cd。以后增删改这一个文件即可。
- 表里的顺序就是标签页上的展示顺序。

## 5. 组件登记表（给 PAP-157 的接口）

`src/teaching/registry.ts` 是组件的唯一登记处，只放「校验需要知道的东西」，不 import React，所以校验在 node 环境里能直接跑：

```ts
import { z } from "zod";

export type ComponentRole = "diagnostic" | "recap" | "transfer";

export interface ComponentSpec {
  /// 参数 schema：必须是 z.strictObject，未登记的参数直接报错
  readonly props: z.ZodObject;
  /// 组件标签之间能不能、要不要写内容
  readonly children: "none" | "optional" | "required";
  /// 教学角色：校验「开头诊断题、结尾复述 / 迁移题」靠它识别
  readonly role?: ComponentRole;
}

export const componentRegistry = {
  CodeRef: { … },
  Diagnose: { … },
  Recap: { … },
  Transfer: { … },
} as const satisfies Record<string, ComponentSpec>;
```

`src/mdx-components.tsx` 导出同名 React 实现的映射 `mdxComponents`，条目页编译 MDX 时整份传给 `components`；有一个测试断言两边名字一一对应，登记了没实现、实现了没登记都会红。

**页面注入的参数**：作者写的参数由登记表校验；页面另外可以通过 MDX 的 `components` 覆盖给组件注入作者不写的参数（**我的判断**）。目前只有一处：条目页给 `Diagnose` 注入 `prerequisites: { slug: string; title: string }[]`（本篇的前置），组件据此渲染「答不上来？先读：」和链接。所以「先读前置」由诊断题组件自己画，条目页负责把前置递进去。参考答案折叠展开等交互是 PAP-157 的事。

本 Issue 提供的占位组件（PAP-157 可以加参数、改样式，**名字和角色请沿用**，写内容的会话照这个写）：

| 组件 | 角色 | 参数 | 子内容 | 用法 |
|---|---|---|---|---|
| `Diagnose` | `diagnostic` | `question: string` | 可选：答案 / 提示 | 正文第一块。页面在下面给「答不上来？先读：」+ 前置篇链接 |
| `Recap` | `recap` | `prompt: string` | 可选：参考答案 | 复述题，放正文最后 |
| `Transfer` | `transfer` | `prompt: string` | 可选：参考答案 | 迁移题，放正文最后（和 Recap 二选一） |
| `CodeRef` | — | `at: string`（`路径@40位SHA` 或 `路径@pending`），`lines?: string` | 不许 | 见 3.3 |

PAP-157 新增组件（图示、逐步动画、滑块、对比切换、你猜会怎样……）时，在登记表加一条、在 `mdx-components.tsx` 加实现即可，校验自动生效。

## 6. 校验规则

所有规则都是纯函数，输入是解析后的内容集合，输出是 `ContentIssue[]`：

```ts
interface ContentIssue {
  rule: string;   // 下表的规则 id，稳定不改名
  file: string;   // 相对 patra-learn 的路径，如 content/entries/outbox.mdx
  message: string; // 中文说明，说清怎么修
}
```

| 规则 id | 说明 | 对应 AC |
|---|---|---|
| `slug-invalid` | 文件名不符合 slug 规则 | — |
| `frontmatter-invalid` | 缺 frontmatter、YAML 解析失败、字段不合 schema | — |
| `yaml-invalid` | 路线 / 主题 / 标签文件解析失败或不合 schema | — |
| `tag-unknown` | 条目用了标签表里没有的标签 | — |
| `topic-unknown` | 条目挂了不存在的主题 | — |
| `topic-needs-version` | 挂了主题的条目没有带版本的出处 | — |
| `prerequisite-missing` | 前置指向不存在的条目或指向自己 | AC1 |
| `prerequisite-cycle` | 前置成环（报出环上的条目） | AC1 |
| `superseded-missing` | `supersededBy` 指向不存在的条目或自己 | — |
| `route-entry-missing` | 路线里的条目不存在 | — |
| `route-entry-duplicate` | 同一路线里重复出现 | — |
| `route-order-duplicate` | 两条路线 `order` 相同 | — |
| `route-difficulty-descending` | 路线难度倒排 | AC2 |
| `route-prerequisite-after` | 本路线里的前置排在本篇后面 | — |
| `route-personal-entry` | 个人环境条目进了路线 | — |
| `route-superseded-entry` | 已被取代的条目进了路线 | — |
| `mdx-syntax` | MDX 解析失败 | — |
| `mdx-esm` | 正文写了 import / export | AC3 |
| `mdx-expression` | 正文写了注释以外的 `{表达式}` | AC3 |
| `component-unknown` | 用了登记表里没有的组件或 HTML 标签 | AC3 |
| `component-prop-dynamic` | 属性不是字面量 | AC3 |
| `component-props-invalid` | 参数不合 schema（缺必填、类型错、多余参数） | AC3 |
| `component-children` | 子内容不合登记（不许有却有、必须有却没有） | AC3 |
| `secret` | 出现凭据 | AC4 |
| `private-address` | 出现 tailnet IP、`.ts.net` 名字、内网 IP 或本机主机名 | AC4 |
| `code-ref-invalid` | `at` 写法不对 | AC5 |
| `code-ref-missing` | 引用的文件在当前工作树里不存在 | AC5 |
| `diagnostic-missing` | 正文第一块不是诊断题 | AC6 |
| `recap-missing` | 正文最后一块不是复述题或迁移题 | AC6 |

**公开边界（`secret`、`private-address`）**扫加载器读的所有文件（条目、路线、主题、标签表）的原文，包括 frontmatter 和代码块（代码块最容易漏）。用模式匹配，测试与源码里都不写任何真实地址或主机名（这些文件本身也公开）：

- tailnet IP：`100.64.0.0/10`（100.64～100.127 开头）；MagicDNS 名：`*.ts.net`
- 内网 IP（**我的判断**，决策 E 写的是「内网地址」）：`10.0.0.0/8`、`172.16.0.0/12`、`192.168.0.0/16` 里的具体主机地址。放行：带前缀长度的网段写法（`10.0.0.0/8`、`192.168.0.0/16`，讲网段时要用）、`127.0.0.1`、`localhost`、文档专用地址段 `192.0.2.0/24`、`198.51.100.0/24`、`203.0.113.0/24`（本来就不是内网段）。教学例子要写具体主机地址时用文档专用段，或 `<容器网桥地址>` 这类占位。不设行级豁免，免得豁免被滥用
- 本机主机名：`<名字>-mac-mini`、`<名字>-macbook…` 这类连字符主机名（`Mac mini` 作为普通词放行）
- 凭据：私钥头、GitHub / AWS / Slack / `sk-` 类 API key、JWT，以及 `password / secret / token / api_key / access_key` 后面跟 `:` 或 `=` 和一个像真值的值：12 位以上、同时含字母和数字（**我的判断**：`token: xxxxxxxx`、`password: ${DB_PASSWORD}`、`<你的密码>` 这类示意值放行）
- 负例测试里的假凭据在运行时拼出来（`"ghp_" + "a".repeat(36)`），不写成整串字面量，免得 gitleaks 拦提交。gitleaks 照旧兜底密钥。

**代码引用**：Issue AC 要求「文件不存在时测试失败」，所以 `code-ref-missing` 是硬规则，也出现在体检报告的 `issues` 里。已知局限：CI 的 learn job 只在 `patra-learn/` 有改动时才跑，后端删了或改名了被钉的文件，要等下一个学习站 PR 才红；版本收尾的体检会更早发现（报告里该引用是 `missing`）。测试只查「文件在当前工作树里还在不在」（`fs`，仓库根 = `patra-learn/..`）。不在测试里跑 git：CI 的 checkout 是浅克隆，钉住的旧提交在 CI 里查不到。「钉住之后有没有改过」交给体检报告（第 8 节）。

**单篇规则与全站规则**：上表从 `frontmatter-invalid` 到 `recap-missing` 里，凡是只看一篇自身的（frontmatter schema、标签、MDX、组件、公开边界、代码引用、诊断 / 复述）是单篇规则；涉及其他条目的（前置存在与成环、主题、`supersededBy`、路线全部）是全站规则。`content:check-file` 只跑单篇规则，给 PAP-154 的模板用（模板里的前置是占位 slug）。

## 7. 索引（构建时生成）

纯函数，从内容集合算出（页面在 `next build` 预渲染时调用；纯静态站，运行时不读 `content/`，standalone 镜像里也没有它）：

| 索引 | 内容 | 个人环境条目 |
|---|---|---|
| 路线 | 按 `order` 排的路线，每条带按顺序解析好的条目 | 校验已保证不在 |
| 主题 | 主题 → 条目，按最早出处版本升序；每个主题带 `personalOnly` | 列。主题下全是个人环境条目时 `personalOnly: true`，页面不把它放进主题列表、不上首页（用户 2026-10-31 确认） |
| 标签 | 标签表顺序，每个标签带条目与计数 | 列 |
| 版本 | 出处版本 → 条目，版本号降序 | 不列 |

个人环境条目因此在标签页与它挂的主题页可见，不进路线、版本索引与首页推荐。

另外导出 `findEntry(catalog, slug)` 与全部条目列表（带 `personalEnv`、`supersededBy`），首页推荐、搜索由 PAP-162 自己按需过滤。被取代的条目照常进主题、标签、版本索引（主题页正要展示演进），页面据 `supersededBy` 标「已被取代」。

## 8. 体检报告

`pnpm -s content:report` 向 stdout 输出一份 JSON（必须带 `-s`，否则 pnpm 先往 stdout 打一行脚本横幅），PAP-154 的体检只读这份报告：

```json
{
  "generatedAt": "2026-10-31T12:00:00.000Z",
  "head": "cc2956951…（40 位）",
  "issues": [
    { "rule": "prerequisite-missing", "file": "content/entries/outbox.mdx", "message": "前置 transaction 不存在" }
  ],
  "codeRefs": [
    {
      "entry": "outbox",
      "path": "patra-api/…/OutboxRelay.java",
      "sha": "4a1328748c1e…（40 位）",
      "lines": "40-72",
      "status": "changed",
      "commitsSince": 3,
      "lastChangedBy": "5b1d855a5…（40 位）"
    }
  ]
}
```

- `issues`：全部校验问题，和 `pnpm test` 里内容测试报的一样。
- `codeRefs`：每个代码引用一条，`status` 取值：

| status | 含义 | 体检该怎么做 |
|---|---|---|
| `unchanged` | 钉住的提交到 HEAD 之间这个文件没改 | 不用管 |
| `changed` | 改过；`commitsSince` 是改动它的提交数，`lastChangedBy` 是最近一次 | 读 diff，更新条目讲法并重新钉 |
| `missing` | HEAD 里文件不存在了（删了或改名） | 找新位置重新钉，或改写条目 |
| `not-in-commit` | 钉住的提交里没有这个路径（永久链接是坏的） | 改正路径或 SHA |
| `unknown-sha` | 本地仓库里没有这个提交（浅克隆或 SHA 写错） | 先 `git fetch --unshallow` 再跑 |
| `pending` | 写的是 `@pending` | 代码合进 main 后补钉 |

- 只有 `changed` / `unchanged` 才有 `commitsSince`、`lastChangedBy` 字段。
- 退出码：`issues` 为空返回 0，否则 1；`codeRefs` 的状态不影响退出码。
- 要在完整克隆里跑（本机主检出即可）。

`pnpm content:check-file <路径…>` 对给定的 MDX 文件跑单篇规则，按行打印 `规则 id  文件  说明`，有问题退出码 1。

## 9. 渲染

- 条目页 `src/app/entries/[slug]/page.tsx` 在构建期用 `@mdx-js/mdx` 的 `evaluate` 编译 `entry.body`（frontmatter 已由 `splitFrontmatter` 剥掉），`remarkPlugins: [remarkGfm]`，组件全部经 `components` 传入。`generateStaticParams` 列全部条目、`dynamicParams = false`，每篇都是构建时预渲染的静态页。URL `/entries/<slug>` 是临时的，PAP-162 可以改。
- **实现时的调整**（原定 `@next/mdx` + 动态 `import()`）：Turbopack 把 `` import(`…/entries/${slug}.mdx`) `` 展开成目录 glob，`content/entries/` 里一个 `.mdx` 都没有时构建直接失败，撑不住「空内容也能构建」。改成 `evaluate` 后还有一个好处：渲染与校验是同一套 remark 解析（remark-mdx + remark-gfm），不会出现「校验过了、渲染却解析成别的样子」。这也是 `next-mdx-remote` 的做法，属于 MDX 官方 API。
- 首页：列出路线和条目链接的最小页面；没有内容时显示一句「内容建设中」。
- **不放种子内容**（**我的判断**）：`content/` 里只有 `tags.yaml` 和空的 `entries/`、`routes/`、`topics/`（各放 `.gitkeep`）。实现时用临时条目在本地验证过构建、dev 模式、GFM 表格、永久链接与体检报告的 `changed` 状态，验证后已删除。

## 10. 删除旧站（决策 H）

- 删：`src/content/` 全部；`src/app/{archive,cheatsheet,glossary,lines}/`；`src/components/` 下全部旧组件（metro-map、article-layout、top-bar 等 11 个）；`src/lib/content.ts`、`src/lib/progress.ts` 及其测试；`tests/smoke.spec.ts` 改成只测新首页能打开。
- 留：`src/app/api/health/route.ts`（容器健康检查）、`globals.css`（token 由 PAP-162 按画板换）、`fonts.test.ts`、`globals.test.ts`、`layout.tsx`（去掉 TopBar）、部署相关文件。
- 改：`patra-learn/CLAUDE.md` 与 `README.md` 重写内容模型、扩展流程、测试约定；顺带去掉 README 里写着的本机主机名（学习站要公开）。部署、端口等运维描述 PAP-162 改公网入口时再改。
- 删：仓库根的 `.design-sync/` 与 `patra-learn/.design-sync-assets/`（给旧组件做设计同步，旧组件删除后已失效；PAP-156 确认不再用 design-sync，用户同意删除），以及 `.gitignore` 里对应的几行。

## 11. 依赖变动

| 包 | 类型 | 用途 |
|---|---|---|
| `@mdx-js/mdx` | dependencies | 条目页构建期编译 MDX |
| `remark-gfm` | dependencies | 表格等 GFM 语法；渲染与校验两边都挂 |
| `yaml` | dependencies | 读 frontmatter 与路线 / 主题 / 标签文件 |
| `zod` | dependencies | 元数据、YAML、组件参数的 schema |
| `@types/mdx` | devDependencies | `MDXComponents` 等类型 |
| `unified`、`remark-parse`、`remark-mdx` | devDependencies | 校验时把正文解析成语法树 |
| `tsx` | devDependencies | 跑 `scripts/*.ts`（解析 tsconfig 的 `@/` 别名） |
| `clsx` | 移除 | 只有旧组件在用 |

与用户批准的清单相比，实现时删掉了 `@next/mdx`、`@mdx-js/loader`、`@mdx-js/react`、`remark-frontmatter`，改加 `@mdx-js/mdx`，原因见第 9 节。

## 12. 测试

- 每条规则一个负例测试（在内存里构造内容集合，不建 fixture 目录），外加正例；索引、报告状态判断各有单测。
- 一个「真实内容」测试：读 `patra-learn/content/` 跑全部规则，断言零问题，失败时把全部问题列出来。写内容的会话只要跑 `pnpm test`，不用改测试。
- 登记表与 `mdx-components.tsx` 名字一致的测试。
- 体检报告的 git 部分：状态判断写成吃「git 端口」的纯函数，单测用假端口；真实 git 只在脚本里用。
- vitest 的 `include` 扩到 `scripts/**/*.test.ts`（如有）；仍是 node 环境。

## 13. 留给实现时决定

- `src/lib/content/` 内部怎么分文件、函数命名。
- 凭据与内网地址的具体正则写法（满足第 6 节的覆盖面即可）。
- 首页与条目页的最小样式。
