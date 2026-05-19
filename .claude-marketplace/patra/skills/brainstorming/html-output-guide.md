# 设计文档 / 计划文档 HTML 产出指南

brainstorming 写的 spec、writing-plans 写的 plan，**最终产物都是 HTML**，不写 `.md`。

本文档是产出质量的"轻骨架"——只定义必须做到的语义元素与视觉规范，**不约束** CSS / 布局 / 风格。每篇文档根据主题自适配风格。

## 为什么用 HTML 而不是 Markdown

设计文档是 patra-api 项目所有者自看的关键决策载体。Markdown 在 IDE 里看长文档时**视觉层次薄弱**，关键决策淹没在文字墙里——长 spec 写完就再不会被打开读。

HTML 让 callout / 风险 pill / 折叠章节 / 决策表格 / 颜色对比 等视觉元素可用——让"扫一眼能看到该看的"。设计阶段重读自己的 spec 是高频行为，**渲染效果 > 编辑成本**。

## 必须包含的语义元素

| # | 元素 | 用途 |
|---|---|---|
| 1 | **文档头** | `<header>` 内含标题 + 日期 + 状态（draft / approved / archived）+ 关联文档（plan / 上下游 spec）超链接 |
| 2 | **关键决策摘要** | 顶部 callout / 卡片，3-5 句话回答"这份文档做了什么决定"——读者扫一眼就能抓重点 |
| 3 | **范围边界** | in scope / out of scope 对照——并排 callout 或表格，明确写出"这份 spec **不**做什么" |
| 4 | **风险标记**（如有） | 高/中/低风险 pill（红 / 黄 / 绿）放在所在段落上，紧跟缓解方案描述 |
| 5 | **核心方案** | 文字 + 表格 / 图 / 代码块结合，避免纯段落堆砌 |
| 6 | **决策交代** | 选这个方案的原因 + 至少 2 个备选方案 + 为什么没选 |
| 7 | **TODO / 待澄清** | 警示色 callout 块明显标出未定项，便于后续回头查漏 |

## 视觉规范（强约束）

- **单文件自包含**：CSS / JS 必须 `inline`，禁止外部样式表/脚本依赖；字体可引 Google Fonts
- **明确视觉层次**：标题 / 正文 / callout / pill 在颜色、字号、间距上有**显著**区分。如果看上去跟 markdown 渲染没区别 = 改造失败
- **每章节至少一个视觉锚点**：callout / 表格 / 图 / 高亮代码块——禁止纯文字段落堆砌
- **充分使用 HTML5 语义标签**：
  - `<aside>` 用作 callout
  - `<details>` `<summary>` 折叠次要详情（默认 open 关键决策，默认 close 长附录）
  - `<table>` 表达决策矩阵 / 对比 / 范围边界
  - `<mark>` 高亮关键术语
  - `<nav>` 章节锚点导航（长文档 > 1000 字时推荐）
- **风格自由**：根据主题适配（serious / playful / archival 都 OK），不强制全 plugin 一致 CSS

## 禁止

- ❌ 把 markdown 思维直接搬过来（一连串 `<h2>` `<p>` 标题段落堆砌，无视觉锚点）
- ❌ 外部 CDN 依赖（除字体）
- ❌ 用 markdown-to-html 直接转换的产物——失去了 HTML 的视觉表达力，写 HTML 应该用 HTML 的语言思考
- ❌ 任何"如果时间允许我们再补 callout"的拖延——视觉锚点是骨架必含项，不是 nice-to-have

## 文件命名与路径

- spec（brainstorming 产出）：`docs/patra/specs/YYYY-MM-DD-<topic>-design.html`
- plan（writing-plans 产出）：`docs/patra/plans/YYYY-MM-DD-<feature-name>.html`
- `<topic>` / `<feature-name>` 用 kebab-case（小写、连字符）

## 参考样例

仓库内已有 2 份风格完全不同的 HTML 产出，**证明"轻骨架"原则成立**：

| 文件 | 风格 | 适合场景 |
|---|---|---|
| `docs/patra/2026-05-16-refactor-completion-report.html` | 暖色 paper 杂志风（Fraunces 衬线 + paper 配色 + JetBrains Mono） | 复盘报告 / 长篇叙事 |
| `docs/patra/specs/2026-05-15-rename-package-to-dev-linqibin-design.html` | GitHub/Stripe 蓝紫风 + risk pills（红黄绿）+ sidebar TOC | 工程 spec / 含风险评估的设计 |

写新 spec 时**不必拷贝**这两份的 CSS——它们是"风格谱"的两个例证，你根据当前主题自由设计。
