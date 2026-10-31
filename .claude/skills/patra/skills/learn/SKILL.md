---
name: learn
description: 维护公开学习站 patra-learn 时使用：版本收尾时把本版学到的东西写成学习条目；棘手问题查清之后；用户说「记一下」指的是学到的知识、排查结论或做过的取舍（指待办 / bug / 想法时用 patra:create-issue）；要改学习站的条目、路线、主题、版式，或跑学习站体检、处理体检报告时。
---

# patra:learn：维护学习站

学习站（`patra-learn/`）公开在互联网上，读者是想借 Patra 学后端的人，起点可以是没写过后端。条目由 Agent 写：项目每做完一版、每查清一个棘手问题，就把学到的东西写成条目。

**开始时宣布：**「我正在使用 patra:learn 技能维护学习站。」

> 文中 `〔待对齐 PAP-157：…〕` 是教学组件库定稿前留的空位，定稿后换成真实组件名。

## 内容放在哪

| 内容 | 位置 |
|---|---|
| 条目 | `patra-learn/content/entries/<slug>.mdx`，slug 即文件名，小写字母数字加连字符 |
| 路线 | `patra-learn/content/routes/<slug>.yaml`：`title`、`summary`、`order`、`entries`（阅读顺序） |
| 主题 | `patra-learn/content/topics/<slug>.yaml`：`title`、`summary`；成员由条目的 `topics` 决定 |
| 标签清单 | `patra-learn/content/tags.yaml` |
| 组件登记表 | `patra-learn/src/teaching/registry.ts`；实现 `patra-learn/src/mdx-components.tsx` |
| 内容模型、校验、索引 | `patra-learn/src/lib/content/`；脚本 `patra-learn/scripts/` |

命令都在 `patra-learn/` 下跑：

| 命令 | 用途 |
|---|---|
| `pnpm content:check-file <路径…>` | 单篇校验（不查前置、路线、主题这类跨篇规则），按行打印 `规则id  文件  说明` |
| `pnpm test` | 全站校验，含跨篇规则 |
| `pnpm content:report` | 体检报告，stdout 输出 JSON；要在完整克隆里跑（主检出即可） |

## 每块都守的规矩

1. **公开边界**：条目文件在公开仓库里，任何人都能读。
   - 不写凭据、令牌、密码；不写 tailnet IP、`*.ts.net` 名字、内网主机的具体地址、`xxx-mac-mini` 这类主机名、本机用户路径、代理端口与节点。网段写法（`10.0.0.0/8`、`192.168.*`）照常写；要举具体主机地址时用文档专用段 `192.0.2.x`，或 `<mini 的地址>`、`<本机代理端口>` 这类占位。
   - **个人环境**：读者要装上作者同款工具、同款机器才会碰到的，标 `personalEnv: true`，例：代理工具劫持 Mac mini 的域名解析。这类条目不进路线、不进首页推荐，只在标签页可见。机制本身通用的不标，例：JVM 的代理例外列表不认 CIDR；正文写通用机制，作者的工具组合只在背景里用一句话带过。
   - 校验的 `secret`、`private-address` 规则会扫全文（含代码块）；gitleaks 兜底密钥。两者都是兜底，写的时候就要守住。
2. **事实只来自出处**：排查、决策、坑写的是真实发生过的事，每一步都要能在出处（PR、Issue、提交、release spec、Agent 记忆、本次会话）里找到。为讲清楚而补的通用知识写成讲解（「JVM 一般是这样读代理的」），不写成「当时查了 X」。
3. **代码引用钉在提交上**：正文写 `<CodeRef at="<路径>@<SHA>" lines="40-72" />`，路径相对仓库根，`lines` 可选，SHA 写满 40 位，取 main 上最后改过该文件的提交：
   ```bash
   git fetch origin main && git log -1 --format=%H origin/main -- <路径>
   ```
   代码还没合进 main（功能分支上写的条目），写 `<路径>@pending`，代码进 main 后重钉。只引用讲清道理所需的那几行。
4. **交互只用教学组件库**：正文只能用 Markdown（含 GFM 表格）和登记表里的组件；不许 import / export、小写 HTML 标签、`{表达式}`（`{/* 注释 */}` 除外），属性只写字面量。要的交互库里没有，先在登记表和 `mdx-components.tsx` 里加一个通用组件（含测试），再在条目里用。
5. **教学写法**：照 [teaching.md](teaching.md) 写。正文第一块必须是 `Diagnose`，最后一块必须是 `Recap` 或 `Transfer`。

## 选哪一块

| 情形 | 做哪块 |
|---|---|
| 版本收尾 | 先「1. 写新条目」写本版学到的，再「4. 体检」，体检报告摘要附进收尾记录 |
| 棘手问题刚查清 / 用户说「记一下」 | 1. 写新条目 |
| 代码重构了、设计被推翻了 | 2. 改旧条目 |
| 有重复条目，或某主题攒够了篇目 | 3. 合并与归位 |
| 跑体检、体检报告有问题 | 4. 体检 |
| 加条目类型、页面、组件，或要发布 | 5. 改版式与发布 |

## 1. 写新条目

1. **定类型**，复制对应模板：

   | 类型 | `type` | 什么时候用 | 模板 |
   |---|---|---|---|
   | 概念 | `concept` | 讲一个机制或术语：它是什么、为什么这样 | [templates/concept.mdx](templates/concept.mdx) |
   | 排查 | `troubleshooting` | 现象 → 怎么查 → 根因 → 修法，查的过程本身值得学 | [templates/troubleshooting.mdx](templates/troubleshooting.mdx) |
   | 决策 | `decision` | 在几个选项里选了一个，写背景、选项、选择、代价 | [templates/decision.mdx](templates/decision.mdx) |
   | 坑 | `pitfall` | 一句话说得清：「做 A 时会 B，要 C」；一坑一页 | [templates/pitfall.mdx](templates/pitfall.mdx) |

   出处里只有结论、没有查的经过 → 坑；查的路径能教人怎么查 → 排查。一件事里既有概念又有排查 → 拆成两篇，排查篇把概念篇列为前置。
2. **查重**：在 `content/entries/` 和 `content/topics/` 里搜关键词。已有讲同一件事的 → 转「2. 改旧条目」；同一件事在新版本又碰到 → 新条目挂到同一主题。
3. **定 frontmatter**（未知字段会报错）：
   - `title`、`summary`（一两句话，列表、搜索、「今天读这一条」用）。
   - `difficulty`：`intro` 入门（没写过后端）/ `basic` 基础（会写简单接口）/ `advanced` 进阶（做过项目、开始碰到真实问题）/ `practice` 实战（Patra 里真实发生过的事）。排查与坑通常是 `practice`。
   - `prerequisites`：只列读懂本篇必须先懂的直接前置，1～3 篇，页面会在诊断题下列出「答不上来？先读：」。每篇前置必须已存在；不存在就先写那篇（多半是入门或基础的概念篇），本次写不了就留空并在汇报里列出缺口，不写不存在的 slug。前置留空只该出现在入门篇。
   - `tags`：至少 1 个，从 `content/tags.yaml` 里选；都不贴切再往清单里加一个。
   - `topics`：可选；挂主题的条目必须有带 `version` 的出处。
   - `sources`：每条 `version`（必填，如 `v0.16`）、`issue`、`pr`。
   - `personalEnv`：见规矩 1，默认 `false`。
4. **补入门篇**：本版引入了新技术（新中间件、新协议、新框架），检查对应路线的入门、基础层有没有讲它的概念篇；没有就补写，并把它列为本篇的前置。
5. **写正文**：按模板的节和 [teaching.md](teaching.md)。
6. **归位**：按难度插进路线 YAML 的 `entries`（难度不倒排，本路线里的前置排在前面），需要就挂进主题；`personalEnv: true` 的不进路线。
7. **校验**：`pnpm content:check-file content/entries/<slug>.mdx` 改到无输出，再 `pnpm test` 全绿。

## 2. 改旧条目

- **代码重构**（体检报告里引用状态是 `changed`）：读钉住的提交到现在的 diff。正文说法仍然对 → 只重钉 SHA；不对 → 改正文再重钉。
- **设计被推翻**（决策条目）：旧篇不删、不改写当时的背景与理由。
  1. 新写一篇决策条目，背景里写清为什么推翻、新代价是什么，正文链回旧篇。
  2. 旧篇 frontmatter 加 `supersededBy: <新篇 slug>`，页面据此显示「已被某篇取代」。
  3. 两篇挂同一主题；路线里新篇顶替旧篇的位置（被取代的条目不能留在路线里）。
  4. 以旧篇为前置的条目改指新篇；它们需要的正是旧篇那段历史时保留旧篇。
- **概念、排查、坑过时**（修法变了、机制变了）：直接改正文，`sources` 补上新出处。

## 3. 合并与归位

- **重复条目**：合并要删文件，先在汇报里列出「A 并入 B，理由」，用户确认后再动。合并时把 A 独有的内容并进 B，引用 A 的前置、路线、`supersededBy` 全部改指 B，再删 A，跑 `pnpm test`。
- **插进已有路线**：按难度和前置关系找位置，插入后整条路线从头顺读一遍前置是否都在前面。
- **开新路线**：一个主题或标签下的条目能从入门顺读到进阶时，起草路线 YAML（`order` 全站不重复），问用户后再加——路线像一门小课，开了就要维护。
- **孤立条目**（不在任何路线、主题）：归位；确属个人环境的，确认已标 `personalEnv: true`。

## 4. 体检

1. 跑 `pnpm content:report`。体检只读这份报告，不另写检查脚本、不手工 grep 代替。
2. 处理 `issues`：每条有 `rule`、`file`、`message`，按 `message` 修。常见的：

   | `rule` | 处理 |
   |---|---|
   | `prerequisite-missing` / `prerequisite-cycle` | 改前置；缺的篇目补写或列为缺口 |
   | `route-difficulty-descending` / `route-prerequisite-after` | 调整路线顺序 |
   | `route-personal-entry` / `route-superseded-entry` | 移出路线；被取代的换成取代它的新篇 |
   | `secret` / `private-address` | 立即改成占位 |
   | `code-ref-missing` / `code-ref-invalid` | 按下表 `missing` 处理，或改正写法 |
   | `diagnostic-missing` / `recap-missing` | 按 teaching.md 补诊断题或结尾题 |
   | `component-*` / `mdx-*` | 按登记表里组件的现有接口改 |

3. 处理 `codeRefs`，按 `status`：

   | `status` | 处理 |
   |---|---|
   | `unchanged` | 不用管 |
   | `changed` | 走「2. 改旧条目 · 代码重构」 |
   | `missing` | `git log --follow -- <旧路径>` 找新位置重钉；代码已删 → 改写条目 |
   | `not-in-commit` | 改正路径或 SHA |
   | `unknown-sha` | `git fetch --unshallow` 后重跑 |
   | `pending` | 代码已合进 main 就按规矩 3 重钉 |

4. 修完重跑到 `issues` 为空。改不了的（要用户拍板、要整篇重写、依据已经找不到）列给用户：条目、问题、建议。
5. 版本收尾时，把最后一次报告的摘要（问题数、各 `status` 的引用数、改了哪些条目、遗留项）附进收尾记录。

## 5. 改版式与发布

- **加条目类型、改元数据、加页面**：改 `src/lib/content/` 里的内容模型、校验与索引，同时改本技能的类型表、frontmatter 说明和 `templates/`。
- **加组件**：登记表 `src/teaching/registry.ts` 加一条、`src/mdx-components.tsx` 加实现、补测试；校验自动认得新组件。
- **改模板**：改完跑 `pnpm content:check-file ../.claude/skills/patra/skills/learn/templates/*.mdx`，无输出才算过。学习站的测试不扫模板目录。
- **本地验证**（在 `patra-learn/` 下）：`pnpm test`、`pnpm typecheck`、`pnpm lint`、`pnpm build` 全绿；动了页面就 `pnpm dev` 看桌面布局，手机宽度只查正文能读、不横向溢出；`pnpm test:e2e` 只在本地跑。
- **发布**：合进 main 后 `.github/workflows/learn-cd.yml` 自动构建并部署（`patra-learn/**` 下的改动触发，`.md` 不触发，`.mdx` 触发），合并即对公网生效，没有手动部署步骤。回滚：手动触发该 workflow，`image_tag` 填旧提交的 SHA。
- **提交方式**：版本开发期间的学习站改动随版本 PR；版本收尾时写的条目和 release spec 收尾一样直推 main，不开 PR。

## 常见错误

| 错误 | 改成 |
|---|---|
| 排查条目里补了出处里没有的排查步骤 | 只写真实发生的；补的知识写成讲解 |
| 前置写了还不存在的 slug | 先写前置篇，或留空并在汇报里列缺口 |
| 照抄记忆里的 IP、主机名、代理端口 | 换成文档专用段或占位 |
| 推翻的决策直接改写或删掉 | 新写一篇，旧篇加 `supersededBy` |
| 用功能分支上的提交钉代码引用 | 钉 main 上的提交，未合并的写 `@pending` |
| 条目里写一次性交互 | 先进组件库，再在条目里用 |
