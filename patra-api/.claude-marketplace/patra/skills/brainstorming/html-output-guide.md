# 设计文档 / 计划文档 HTML 产出指南

brainstorming 写的 spec、writing-plans 写的 plan，**最终产物都是 HTML**，不写 `.md`。

本文档是产出质量的"轻骨架"——只定义必须做到的语义元素与视觉规范，**不约束** CSS / 布局 / 风格。每篇文档根据主题自适配风格。

## 为什么用 HTML 而不是 Markdown

设计文档是 patra-api 项目所有者自看的关键决策载体。Markdown 在 IDE 里看长文档时**视觉层次薄弱**，关键决策淹没在文字墙里——长 spec / plan 写完就再不会被打开读。

HTML 让 callout / 风险 pill / 折叠章节 / 决策表格 / 颜色对比 / 状态标记 等视觉元素可用——让"扫一眼能看到该看的"。设计与计划文档重读自己的产出是高频行为，**渲染效果 > 编辑成本**。

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

写新 spec / plan 时**不必拷贝**这两份的 CSS——它们是"风格谱"的两个例证，你根据当前主题自由设计。

---

# 设计文档（spec）专用章节

由 brainstorming 产出。

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

---

# 实现计划（plan）专用章节

由 writing-plans 产出。

## 必须包含的元素

1. **文档头**（含 `<header>` 与给 AI 代理的执行提示 `<aside class="for-ai-worker">`）
2. **元数据**（`<dl class="meta">`：目标 / 架构 / 技术栈 / 关联 spec）
3. **范围检查结果**（若已确认无需拆分则一句话标注，否则给出拆分方案）
4. **文件结构清单**（先列文件再列任务）
5. **任务卡片列表**（每任务展开为 5 步 TDD 循环）
6. **自检结果**（规格覆盖度 / 占位符扫描 / 类型一致性 / HTML 结构完整性）

## 任务状态机

每个 `<article class="task">` 与 `<li class="step">` 都带 `data-status` 属性。状态机：

| 状态 | 渲染建议 | 含义 |
|---|---|---|
| `pending` | ☐ + 灰边框 | 默认初始状态 |
| `in-progress` | ▶ + 蓝高亮 | 当前正在做 |
| `done` | ☑ + 绿边框 + 灰底 | 已完成 |
| `blocked` | ✗ + 红边框 | 卡住，需要外部澄清 / 解决 |
| `skipped` | ⊘ + 划线 | 跳过（前置任务变更导致此任务不再需要） |

CSS 通过 `[data-status="done"]` 等属性选择器渲染，AI 代理只需要改一个 attribute 就能切换状态。

## 文档头部模板

写新 plan 时复制此骨架，填入项目特定内容。CSS 风格自由发挥（参考 spec 样例的 paper 风或 GitHub 风）。

```html
<!DOCTYPE html>
<html lang="zh-CN">
<head>
  <meta charset="UTF-8">
  <title>[功能名称] 实现计划</title>
  <style>
    /* inline CSS — 根据主题自由设计 */
    /* 必须定义 .task / .step 与 [data-status] 的渲染 */
  </style>
</head>
<body>
  <header class="plan-header">
    <h1>[功能名称] 实现计划</h1>

    <aside class="for-ai-worker">
      <strong>面向 AI 代理的工作者：</strong>
      必需子技能：使用 <code>patra:subagent-driven-development</code>（推荐）
      或 <code>patra:executing-plans</code> 逐任务实现此计划。
      通过修改任务卡片与步骤的 <code>data-status</code> 属性追踪进度
      （pending / in-progress / done / blocked / skipped）。
    </aside>

    <dl class="meta">
      <dt>目标</dt> <dd>[一句话描述要构建什么]</dd>
      <dt>架构</dt> <dd>[2-3 句话描述方案]</dd>
      <dt>技术栈</dt> <dd>[关键技术 / 库]</dd>
      <dt>关联 spec</dt> <dd><a href="../specs/YYYY-MM-DD-...-design.html">链接</a></dd>
    </dl>
  </header>

  <section class="file-structure">
    <h2>文件结构</h2>
    <!-- 列出所有创建 / 修改的文件 + 每个文件的职责 -->
  </section>

  <section class="tasks">
    <!-- 任务卡片列表，模板见下 -->
  </section>

  <section class="self-check">
    <h2>自检结果</h2>
    <!-- 规格覆盖度 / 占位符扫描 / 类型一致性 / HTML 结构完整性 -->
  </section>
</body>
</html>
```

## 任务卡片模板

每个任务严格按此结构展开（patra-api 是 Java 项目，代码示例用 Java，其他语言项目自适配）：

```html
<article class="task" id="task-1" data-status="pending">
  <header>
    <h2>任务 1：[组件名称]</h2>
    <dl class="files">
      <dt>创建</dt><dd><code>exact/path/to/File.java</code></dd>
      <dt>修改</dt><dd><code>exact/path/to/Existing.java:123-145</code></dd>
      <dt>测试</dt><dd><code>tests/exact/path/to/Test.java</code></dd>
    </dl>
  </header>

  <ol class="steps">
    <li class="step" data-status="pending">
      <strong>步骤 1：编写失败的测试</strong>
      <pre><code class="lang-java">@Test
void should_do_specific_behavior() {
    var result = component.method(input);
    assertThat(result).isEqualTo(expected);
}</code></pre>
    </li>

    <li class="step" data-status="pending">
      <strong>步骤 2：运行测试验证失败</strong>
      <p>运行：<code>./gradlew :module:test --tests "*should_do_specific_behavior"</code></p>
      <p>预期：FAIL，报错 "method not defined"</p>
    </li>

    <li class="step" data-status="pending">
      <strong>步骤 3：编写最少实现代码</strong>
      <pre><code class="lang-java">// 完整代码块，禁止占位
public Output method(Input input) {
    // ...
}</code></pre>
    </li>

    <li class="step" data-status="pending">
      <strong>步骤 4：运行测试验证通过</strong>
      <p>运行：<code>./gradlew :module:test --tests "*should_do_specific_behavior"</code></p>
      <p>预期：PASS</p>
    </li>

    <li class="step" data-status="pending">
      <strong>步骤 5：Commit</strong>
      <pre><code class="lang-bash">git add tests/.../Test.java src/.../File.java
git commit -m "feat: add specific feature"</code></pre>
    </li>
  </ol>
</article>
```

## AI 代理状态切换约定

执行计划时，**唯一**的进度跟踪方式是修改 `data-status` 属性：

| 时机 | 操作 |
|---|---|
| 开始做任务 N | 找到 `<article ... id="task-N" data-status="pending">`，把 `data-status="pending"` 改成 `data-status="in-progress"` |
| 开始做步骤 M | 找到该任务下第 M 个 `<li class="step" ... data-status="pending">`，改为 `data-status="in-progress"` |
| 步骤完成 | 改为 `data-status="done"` |
| 任务全部步骤完成 | 整个 `<article>` 的 `data-status` 改为 `done` |
| 遇到阻塞 | 改为 `data-status="blocked"`，并在卡片内插入 `<aside class="blocker">说明阻塞原因 + 需要的澄清</aside>` |
| 任务被跳过 | 改为 `data-status="skipped"`，并在卡片内插入 `<aside class="skip-reason">说明跳过原因</aside>` |

CSS 选择器示例（写 plan 时 inline 到 `<style>`，颜色可自由调整）：

```css
.task[data-status="done"]        { border-left: 4px solid #10b981; background: #f3f4f6; }
.task[data-status="in-progress"] { border-left: 4px solid #3b82f6; background: #eff6ff; }
.task[data-status="blocked"]     { border-left: 4px solid #ef4444; background: #fef2f2; }
.task[data-status="skipped"]     { opacity: 0.5; text-decoration: line-through; }

.step::before { content: "☐ "; }
.step[data-status="done"]::before        { content: "☑ "; color: #10b981; }
.step[data-status="in-progress"]::before { content: "▶ "; color: #3b82f6; }
.step[data-status="blocked"]::before     { content: "✗ "; color: #ef4444; }
.step[data-status="skipped"]::before     { content: "⊘ "; opacity: 0.5; }
```

## 禁止（plan 特有）

- ❌ 不要保留 markdown checkbox `- [ ]` 语法——已被 `data-status` 取代
- ❌ 不要在任务里写"待定 / TODO / 后续实现"——禁止占位符（详见 writing-plans SKILL.md 的"禁止占位符"章节）
- ❌ 不要让多个任务共用同一个 `id`——破坏状态机精确定位
- ❌ 不要省略 `data-status="pending"`——初始属性必须显式标注，否则 AI 代理找不到锚点
