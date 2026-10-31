# 学习站教学组件库设计（PAP-157）

> **Issue**：[PAP-157](https://linear.app/papertrace/issue/PAP-157)　**release spec**：`docs/patra/release-specs/v0.17-learn.md` 第 2.1 节、决策 D、G
> **依赖**：内容模型 `docs/patra/specs/2026-10-31-learn-content-model-design.md`（PAP-155）；hi-fi 画布与设计简报（PAP-156）第 2、6、8 节
> **写法文档**：`patra-learn/src/teaching/README.md`（写内容的人只读它；其中每个 mdx 示例由 `readme.test.ts` 跑内容校验）
> **日期**：2026-10-31

## 1. 组件清单

| 组件 | 角色 | 子内容 | 画板 |
|---|---|---|---|
| `Diagnose` | diagnostic（第一块） | none | C-Diagnose |
| `Recap` / `Transfer` | recap / transfer（最后一块） | optional | C-Transfer |
| `Quiz` | — | none | C-Quiz |
| `Structure` | — | none | C-Structure |
| `Sequence` | — | none | C-Sequence |
| `StateMachine` | — | none | C-StateMachine |
| `Stepper` | — | none | C-Stepper |
| `Params` | — | none | C-Params |
| `Compare` | — | none | C-Compare |
| `Callout` | — | required | 条目页的一句话 / 打个比方 / 顺带想通的 / 小心 / 代价 |
| `DebugTrail` | — | none | 排查条目「一路怎么查过来的」 |
| `DecisionOptions` | — | none | 决策条目「三个选项」 |
| `CodeRef` | — | none | PAP-155 已有 |
| `Mastery` | 不进登记表 | — | C-Mastery，页面级，由 PAP-162 接存储 |

参数 schema 全部在 `src/teaching/registry.ts`（zod），实现的 props 类型取 `PropsOf<名字>`（`z.input`，因为 MDX 运行时不经 zod 解析，默认值由实现补）。跨字段约束（引用的 id 存在、id 唯一、正确项在选项范围内、状态机同一状态同一事件只去一处、`Params` 最后一个 case 是兜底、`Compare` 的崩溃位置在两步之间、`DebugTrail` 以找到根因结束、`DecisionOptions` 恰有一个选中）用 `superRefine` 写在 schema 里，内容校验自动报出。

## 2. 接口设计原则

1. **作者只写数据，不写代码，不写坐标**。图示类组件的节点放在网格里（`col` / `row`，从 1 数），连线由端点 id 推出，布局由组件算；状态机里「这个状态下不会发生」的说明由转移表推出，作者不写。
2. **选择题一个形状**：`question` + `options`（2～4）+ `answer`（字母）+ `explain`，`Diagnose`、`Quiz`、`Stepper.guesses`、`Params.guess` 共用。字母而不是下标，因为读者看到的是 A、B、C。
3. **结果与判定一个形状**：结果行 `{ label, value, tone }`（`value` 可以是一串徽章），判定 `{ tone: ok | bad, head?, text }`，`Params` 与 `Compare` 共用。
4. **字符串只认行内代码**：反引号渲染成 `<code>`，其余 Markdown 不解析，避免作者以为能写链接或加粗。`Params` 的文字可写 `{滑块id}` 占位。
5. **组件名与角色沿用 PAP-155**：`Diagnose` 的 `question`、`Recap` / `Transfer` 的 `prompt` 不改名，校验的提示文字照旧成立。

## 3. 编排期自定（用户授权「按建议直接执行」）

1. **诊断题改成单选**：`Diagnose` 必填 `options` / `answer` / `ifRight` / `ifWrong`，不再接受子内容。理由：画板的诊断题是单选、点选项即判，答错推前置；开放题读者没法判断自己答没答对，前置引导就失效了。可选 `suggest` 指定答错时推荐哪篇前置（不写取第一篇）。
2. **复述 / 迁移题必须带参考要点**：`points` 2～5 条，读者逐条打勾自评（画板 C-Transfer）。子内容保留为可选的补充参考答案。
3. **新增三个排版类组件 `Callout`、`DebugTrail`、`DecisionOptions`**：画板四类条目页里都有，且排查路径、决策选项是交互（点开才看到），按决策 D 只能来自组件库；强调块是 Markdown 表达不了的版式。不新增其他版式组件（「下次碰到类似的」用 Markdown 列表，命令对比用 Markdown 表格）。
4. **掌握度做成受控组件，不进登记表**：`Mastery` 只负责三档卡片、选中上浮、「记下了」提示 2.6 秒消失与取消标记，值与上次标记时间由页面传入、变化回调给页面；浏览器存储由 PAP-162 实现。
5. **逐步动画的节点排成一行**（2～5 个），不支持二维布局：画板只有一行，一步只变一件事时一行足够；需要二维关系用 `Structure`。
6. **`Params` 用 case 表而不是公式**：每个 case 是对控件取值的部分匹配，第一个匹配生效，最后一个必须是兜底。不支持算术（画板里「一小时重发 n 次」改写成「每 {interval} 重发一次」），避免在条目里写代码。

## 4. 对其他交付物的影响

维护技能的条目模板（`.claude/skills/patra/skills/learn/templates/*.mdx`）里 `<Diagnose question="…">答案</Diagnose>` 与 `<Transfer prompt="…">参考答案</Transfer>` 按新接口不再合法（缺 `options` / `answer` / `ifRight` / `ifWrong`、缺 `points`），模板里「待对齐 PAP-157」的注释也可以换成 README 的组件。由技能的维护方同步（本 Issue 不改技能文件）。

PAP-155 的内容规则单测（`mdx-rules.test.ts`、`validate.test.ts`）里的诊断题与复述题字面量已按新参数写全，测的仍是首尾结构；「标签与内容写在同一行」一例改用复述题，因为诊断题不再有子内容。
