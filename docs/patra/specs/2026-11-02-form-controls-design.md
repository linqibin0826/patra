# 表单控件替换浏览器原生控件（PAP-177）

- 日期：2026-11-02
- Issue：PAP-177（v0.18 Analytics）
- 画布（定稿）：https://claude.ai/artifact/VtzM2PxTBdz4GJuMSCAWY3
- 分支：`feat/v0.18-analytics-web`
- 状态：已实施（2026-11-02）

## 为什么做

门户有 7 处直接用浏览器原生控件。它们的样子由操作系统和浏览器决定：macOS 的下拉是系统菜单，Chrome 的日期框是英文日历、只能逐格敲 `dd/mm/yyyy`。这和站点「纸张 + 陶土色」的风格对不上，也不好用。

| 位置 | 原生控件 | 文件 |
|------|---------|------|
| 文献库工具条「按 加入时间」 | `<select>` | `src/components/library/SortSelect.tsx` |
| 投稿经验「文章类型」 | `<select>` | `src/components/experience/ExperienceForm.tsx` |
| 个人资料「身份」 | `<select>` | `src/components/settings/profile/ProfileForm.tsx` |
| 投稿经验「投稿日期」「首轮决定日期」「最终决定日期」 | `<input type="date">` ×3 | `ExperienceForm.tsx` |
| 投稿经验「审稿轮数」 | `<input type="number">` | `ExperienceForm.tsx` |

## 目标

1. 新增三个原语：`ui/select.tsx`、`ui/date-picker.tsx`（含 `ui/calendar.tsx`）、`ui/number-field.tsx`，外观照画布。
2. 替换上表 7 处，表单的值、校验、联动（禁用、提示、用时计算、URL 参数）行为不变。
3. 桌面与手机（≤ 880px）都按画布。

## 不做

- 胶囊单选（`ChoiceGroup`）与 1–5 评分方块：它们不是原生控件，样式不动。
- 不改表单 schema：投稿经验的日期仍是 `""` 或 `YYYY-MM-DD` 字符串，审稿轮数仍是字符串，身份仍是 `Occupation | null`。
- 不自写交互逻辑：键盘、焦点、弹层定位、读屏都交给组件库。

## 反目标（做成这样就算失败）

- 任何一处弹出系统原生菜单。日历自带的年、月下拉默认是原生 `<select>`，必须换掉。
- 现有表单测试与 e2e 为了迁就新控件而放宽断言。

## 已定的做法

### 组件来源

- **下拉**：shadcn 的 base 版 Select（`@base-ui/react/select`，已装），用 shadcn CLI 加到 `src/components/ui/select.tsx`，再按 token 改样式。这和现有 `dropdown-menu.tsx`、`combobox.tsx` 的做法一样。
- **日历**：shadcn 的 base 版 Calendar（react-day-picker v9，新依赖），中文用 `react-day-picker/locale` 的 `zhCN`，周一起始。
- **日期弹层**：base-ui Popover（已装）。
- **数字**：base-ui NumberField（已装，shadcn 没有现成封装），直接写薄封装。

### 下拉选择

- 弹层出现在触发器下方 6px，宽度与触发器相同，不盖在触发器上（`alignItemWithTrigger={false}`）。
- 弹层样式与 `combobox` / `dropdown-menu` 一致：`bg-elevated`、`shadow-lg`、`radius-lg`、`p-1.5`。选项 36 高，已选项左侧打陶土色勾、字重 500，高亮项用 `bg-sunken` 底色。
- 两种触发器：
  - **表单**：外框与 `FIELD_BOX` 同一套（描边、内阴影、悬停加深、聚焦或展开时描边变陶土色并加光晕、出错变红、禁用凹陷底），高度跟随所在表单（投稿经验 44、个人资料 48）。空值显示占位文字（`请选择`、`不填写`），颜色用 `fg-4`。
  - **紧凑**：文献库工具条用，32 高、`radius-md`、`paper-50` 底、字号 `text-sm` 字重 500。
- 「身份」的「不填写」是一个真选项（值为空），后面跟一条分隔线。
- 长选项文字在触发器里截断加省略号。

### 日期选择

- 输入框加右端日历按钮。输入框可直接敲，失焦或回车时规范化：认 `2026-08-14`、`2026/8/14`、`2026.8.14`、`20260814`、`2026年8月14日`，统一成 `2026-08-14`，用等宽字体。认不出或日期不存在（如 9 月 31 日）时提示「日期写成 2026-08-14 这样」；早于可选范围、晚于今天也各有提示。
- 日历按钮，或在输入框里按 ↓，打开日历浮层（宽 312，锚在输入框下方左对齐）。
- 日历标题是「年」「月」两个下拉（`captionLayout="dropdown"`），这两个下拉通过 react-day-picker 的 `components.Dropdown` 换成门户 Select；右侧是上月、下月两个箭头。年份从今年倒排到最早可选年。
- 日格：选中项 `action-primary` 实底；今天在数字下方加 4px 陶土色圆点；其他月份的日子与不可选日子用 `fg-4`。
- 可选范围：最早 `MIN_DATE`（1990-01-01）或参照日中较晚的一个，最晚今天。
- **参照日**：「首轮决定日期」「最终决定日期」以投稿日期为参照。参照日格子用 `clay-50` 底、`clay-700` 字，参照日之前不可选；页脚左侧显示「距投稿 N 天」，未选时显示「投稿日 MM-DD 起可选」。
- 页脚右侧「清除」「今天」两个文字按钮。今天不可选或已选中时「今天」置灰。
- 禁用时（最终结果是「进行中」等）输入框与按钮都禁用，下方显示现有的联动提示。

### 数字步进

- 值靠左、等宽字体；右端「−」「+」两个 32px 按钮，中间一道发丝线。
- 输入框里按 ↑ / ↓ 加减一；到上下限的按钮置灰；按钮不进 Tab 顺序（base-ui 默认）。
- 审稿轮数范围 1–10，只收整数。

### 手机（≤ 880px）

- 下拉与日历都改成底部抽屉，做法同现有 `ResponsivePopover`：遮罩、顶部把手、标题行带关闭按钮，高度随内容、最高 80vh。
- 下拉抽屉里选项 48 高，点选即选中并收起。日历抽屉里日格 44 高，点日子即选中并收起。
- 手机上输入框照样能直接敲日期。

### 和 React Hook Form 的接法

- 投稿经验表单：三个新控件都是受控组件，用 `Controller` 接入，值的类型不变（见「不做」）。原来 `register` 带来的 `onBlur` 校验时机要保留。
- 个人资料表单：同样用 `Controller`；「不填写」对应 `null`。
- 文献库排序不在表单里：选中后照旧 `router.push(libraryHref(...))`。

### 实施前定下的细节（我的决定，依据现有代码）

1. **错误提示只有一个来源：表单 schema。** 日期组件自己不显示错误。失焦或回车时，认得出就把 `YYYY-MM-DD` 交给表单，认不出就把原文交给表单、原文留在输入框。`experienceFormSchema` 的 `dateField` 加一条格式检查，文案「日期写成 2026-08-14 这样」（`DATE_MESSAGES.format`）。范围类错误沿用现有文案（「日期不能晚于今天」「日期不能早于 1990 年」「首轮决定日期不能早于投稿日期」「最终决定日期不能早于首轮决定日期」）。画布里组件自带的报错文案以此为准。
2. **参照日跟随现有校验规则。** 首轮决定日期以投稿日期为参照，页脚「距投稿 N 天」。最终决定日期以首轮决定日期为参照，页脚「距首轮决定 N 天」。参照日改了、导致后面的日期失效时，不清空，只由 schema 报错（现有行为）。
3. **手机上年、月下拉不再套抽屉。** 日历抽屉里的年、月下拉一律用浮层模式，只有表单字段级别的下拉才在窄屏变抽屉。抽屉标题用字段名（「文章类型」「首轮决定日期」）。
4. **审稿轮数不夹取。** NumberField 开 `allowOutOfRange`：直接敲 0 或 11 时原样保留，由 schema 报 `ROUNDS_MESSAGE`。按钮和方向键只在 1–10 内加减（base-ui 行为）。表单值仍是字符串，空串对应 NumberField 的 `null`。
5. **「今天」按北京时间。** 统一用表单已有的 `todayInShanghai`，作为日历的今天、可选上限和「今天」按钮的取值，不用浏览器本地日期。
6. **测试改法。** 查原生控件的断言（`selectOptions`、`toHaveValue(1)`、`min` / `max` 属性、e2e 的 `selectOption`）改成按角色操作新控件，并用等价的行为断言替代，例如超出范围的日子不可选、敲超范围日期出现 schema 文案。这不算放宽断言。

## 留给实施者

- 弹层与抽屉怎么在同一个原语里切换（比照 `ResponsivePopover` 用 `useMediaQuery`，或用 CSS），由实施时定。
- `ui/` 原语的具体 props 命名、紧凑触发器做成 variant 还是单独组件。
- 测试按 portal 规范：原语与日期解析函数写 Vitest + Testing Library 单测（查询优先 `getByRole`）；现有表单测试里查原生控件的地方，改成查新控件的角色。
- 设计系统 Artifact 与 `tests/design-system/` 守护测试是否需要同步，由实施时检查后决定。
