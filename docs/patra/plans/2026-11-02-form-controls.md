# 表单控件替换原生控件 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 用 shadcn / base-ui 的 Select、Calendar、NumberField 做三个门户原语，替换门户 7 处浏览器原生控件（PAP-177）。

**Architecture:** 新原语放 `patra-portal/src/components/ui/`，样式全部走 token，照画布 VtzM2PxTBdz4GJuMSCAWY3。日期输入的解析放在纯函数 `src/lib/date-input.ts`，错误只由表单 schema 给出。表单通过 RHF `Controller` 接入，表单值的类型不变。

**Tech Stack:** Next.js 16 / React 19 / TS strict / Tailwind v4 / `@base-ui/react` 1.8 / react-day-picker v9（新增，带 date-fns）/ Vitest + Testing Library / Playwright。

**Spec:** `docs/patra/specs/2026-11-02-form-controls-design.md`

## Global Constraints

- 所有命令在 `patra-portal/` 下运行；单测 `pnpm test -- <path> --maxWorkers=2`，全量 `pnpm test -- --maxWorkers=2`。
- 颜色、圆角、阴影只用 token 类（`bg-bg-elevated`、`shadow-lg`、`text-fg-3`、`clay-*` 等），不写 `[var(--x)]`，不写十六进制。
- 不能出现原生 `<select>`、`type="date"`、`type="number"`；日历的年、月下拉也要换成门户 Select。
- 表单值类型不变：投稿经验日期 `""` 或 `YYYY-MM-DD`，审稿轮数是字符串，身份是 `Occupation | null`。
- 「今天」一律用 `todayInShanghai(Date.now())`。
- 窄屏断点 `(max-width: 880px)`，用 `useMediaQuery`（`src/lib/use-media-query.ts`）。
- shadcn CLI 不许覆盖已有的 `ui/button.tsx`。
- 提交信息用中文动词开头（commitlint），每个任务一个提交，带 `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`。

## Review Focus

1. 在日期框里敲完直接点「提交」（不先失焦）：值必须先规范化再校验，否则会被当成格式错误。→ Task 4 测试「敲完直接回车提交」。
2. 改了投稿日期以后，首轮决定日期的日历要跟着换参照日，之前选的日期变成非法时只报错、不清空。→ Task 5 测试「投稿日期改晚后首轮日期报错」。
3. 跨年翻月：日历在 1 月点「上个月」进入上一年 12 月，年份下拉同步。→ Task 4 测试。
4. 日历打开时按 Esc：只关日历，不关外层对话框（投稿经验在发帖对话框里也会用到）。→ Task 4 测试。
5. 键盘用户只用 Tab 和方向键能完成选择：Select 的触发器是 `combobox`，选项是 `option`。→ Task 2 测试。

---

### Task 1: 日期输入解析与 schema 格式校验

**Files:**
- Create: `src/lib/date-input.ts`
- Modify: `src/lib/experience/messages.ts`（`DATE_MESSAGES` 加 `format`）、`src/lib/experience/form-schema.ts`（`dateField`）
- Test: `tests/lib/date-input.test.ts`、`tests/lib/experience/form-schema.test.ts`（已有则追加）

**Interfaces:**
- Produces: `parseDateInput(text: string): string | null`：认得出且日期存在时返回 `YYYY-MM-DD`，否则 `null`；空白返回 `null`。`daysBetween(from: string, to: string): number`（两个 `YYYY-MM-DD` 相差天数，按 UTC 计算）。`DATE_MESSAGES.format = "日期写成 2026-08-14 这样"`。

- [ ] **Step 1: 写失败测试**：`parseDateInput` 对 `"2026-08-14"`、`"2026/8/14"`、`"2026.8.14"`、`"20260814"`、`"2026年8月14日"`、`" 2026-8-4 "` 分别返回 `"2026-08-14"` 或 `"2026-08-04"`；对 `"2026/9/31"`、`"2026-13-01"`、`"abc"`、`""` 返回 `null`；`daysBetween("2026-09-15", "2026-09-28") === 13`。schema 测试：`submittedOn: "2026/9/31"` 时报 `DATE_MESSAGES.format`。
- [ ] **Step 2: 运行确认失败**：`pnpm test -- tests/lib/date-input.test.ts --maxWorkers=2`，预期报找不到模块。
- [ ] **Step 3: 实现**：正则 `^(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})\s*日?$` 或 `^(\d{4})(\d{2})(\d{2})$`，再用 `Date.UTC` 回算核对日月。`dateField` 在非空分支最先检查：值不符合 `^\d{4}-\d{2}-\d{2}$`，或 `parseDateInput(value) !== value` 时，报 `format` 并返回。
- [ ] **Step 4: 运行通过**，同时跑 `tests/components/experience` 确认现有测试仍绿。
- [ ] **Step 5: 提交** `新增日期输入解析与格式校验 (PAP-177)`。

### Task 2: Select 原语

**Files:**
- Create: `src/components/ui/select.tsx`
- Test: `tests/components/ui/select.test.tsx`

**Interfaces:**
- Consumes: `@base-ui/react/select`、`useMediaQuery`。
- Produces（具名导出）：
  - `Select`（= `SelectPrimitive.Root`）
  - `SelectTrigger({ variant?: "field" | "compact", size?: "md" | "lg", className, ...Trigger.Props })`：`field` 自带 `FIELD_BOX` 那套外框，高 44（`md`）或 48（`lg`），右端 `ChevronDown`，展开时箭头转 180°。`compact` 高 32、`radius-md`、`paper-50` 底、`text-sm font-medium`。
  - `SelectValue({ placeholder?: string })`：空值时显示占位，用 `text-fg-4`。
  - `SelectContent({ sheetTitle?: string, className, children })`：桌面在触发器下方 `sideOffset=6`、`alignItemWithTrigger={false}`、`align="start"`。`field` 时宽度等于触发器，`compact` 时最小 184。窄屏且给了 `sheetTitle` 时变成底部抽屉：`Select.Backdrop` 遮罩 `bg-bg-scrim`，Positioner 被样式钉到底部，`rounded-t-xl bg-paper-50`，顶部把手加标题加关闭按钮。不给 `sheetTitle` 时窄屏仍是浮层（日历里的年月下拉用这种）。
  - `SelectItem({ value, children, className })`：36 高（窄屏抽屉内 48），左侧 16px 勾选位（`ItemIndicator` 里放 `Check`，`text-clay-600`），选中项 `font-medium`，`data-highlighted` 时 `bg-bg-sunken`。
  - `SelectSeparator`：`-mx-1.5 my-1.5 h-px bg-border-subtle`。
- 先运行 `pnpm dlx shadcn@latest add select --view` 取上游源码作底稿，手工写入文件，不运行 add。

- [ ] **Step 1: 写失败测试**：渲染 `Select items={…}`（三项「加入时间 / 出版日期 / 期刊」，`defaultValue="journal"`，`SelectTrigger aria-label="排序"`）：
  - `getByRole("combobox", { name: "排序" })` 显示「期刊」。
  - 点开后 `findByRole("listbox")` 里有 3 个 `option`，「期刊」是 `aria-selected="true"`。
  - 点「出版日期」后 `onValueChange` 收到 `"published"`，listbox 消失。
  - 键盘：focus 触发器，按 `ArrowDown` 打开，再按 `ArrowDown` 和 `Enter` 选中下一项。
  - 空值时显示占位「请选择」。
- [ ] **Step 2: 运行确认失败**。
- [ ] **Step 3: 实现** `select.tsx`，接口见上。
- [ ] **Step 4: 运行通过**，再跑 `tests/design-system` 确认守护测试仍绿。
- [ ] **Step 5: 提交** `新增下拉选择原语 (PAP-177)`。

### Task 3: NumberField 原语

**Files:**
- Create: `src/components/ui/number-field.tsx`
- Test: `tests/components/ui/number-field.test.tsx`

**Interfaces:**
- Produces: `NumberField({ id, value: number | null, onValueChange(value: number | null), min, max, disabled?, invalid?, describedBy?, inputRef?, onBlur? })`。base-ui `NumberField.Root` 开 `allowOutOfRange`；`Group` 用 `FIELD_BOX` 外框（44 高、`rounded-lg`），`Input` 用等宽字体，右端 `Decrement`（`Minus`）、1px 发丝线、`Increment`（`Plus`），按钮 32px，`aria-label` 分别为「减一」「加一」。

- [ ] **Step 1: 写失败测试**：
  - `getByRole("textbox", { name: "审稿轮数" })` 存在；值 2 时点「加一」收到 3。
  - 值 10 时「加一」`toBeDisabled()`，值 1 时「减一」禁用。
  - 敲 `11` 后失焦，`onValueChange` 收到 11（不夹取）。
  - `disabled` 时输入框和两个按钮都禁用。
- [ ] **Step 2: 运行确认失败**。
- [ ] **Step 3: 实现**。base-ui 的 Input 角色若是 `textbox`，测试就按 `textbox` 查；不手动加 `role`。
- [ ] **Step 4: 运行通过**。
- [ ] **Step 5: 提交** `新增数字步进原语 (PAP-177)`。

### Task 4: Calendar 与 DatePicker 原语

**Files:**
- Modify: `package.json` / `pnpm-lock.yaml`（`pnpm add react-day-picker date-fns`）
- Create: `src/components/ui/calendar.tsx`、`src/components/ui/date-picker.tsx`
- Test: `tests/components/ui/date-picker.test.tsx`

**Interfaces:**
- Consumes: Task 1 的 `parseDateInput`、`daysBetween`；Task 2 的 `Select*`（用于年月下拉，不给 `sheetTitle`）。
- Produces:
  - `Calendar(props: DayPicker props)`：shadcn base 版 Calendar 作底稿（`pnpm dlx shadcn@latest add calendar --view`，不运行 add，免得覆盖 `button.tsx`）。默认 `locale={zhCN}`、`weekStartsOn={1}`、`captionLayout="dropdown"`、`showOutsideDays`。`components.Dropdown` 换成用门户 Select 渲染的 `CalendarDropdown`（`compact` 触发器，读 `options` 与 `value`，选中后调用 react-day-picker 传入的 `onChange`，把值包成 `{ target: { value } }` 事件）。日格 38 高（抽屉内 44）；选中项 `bg-action-primary text-fg-on-clay`；今天加 `after:` 4px `bg-clay-500` 圆点；`outside` 与 `disabled` 用 `text-fg-4`；修饰符 `reference` 用 `bg-clay-50 text-clay-700`。
  - `DatePicker({ id, value: string, onChange(value: string), onBlur?, today: string, min: string, reference?: { date: string; label: string }, disabled?, invalid?, describedBy?, sheetTitle: string, inputRef? })`：
    - 输入框受控于本地文本，`value` 变化时同步。失焦或回车时 `onChange(parseDateInput(text) ?? text.trim())`，然后调用 `onBlur`。
    - 右端日历按钮 `aria-label="打开日历"`；在输入框里按 `ArrowDown` 也能打开。
    - 桌面用 base-ui Popover（锚在输入框外框，`align="start"`、`sideOffset=6`、宽 312），窄屏用底部抽屉（同 `ResponsivePopover` 的 Dialog 写法，标题 `sheetTitle`）。
    - 日历的可选范围是 `startMonth` / `endMonth` 加上 `disabled={[{ before: max(min, reference.date) }, { after: today }]}`；`today` 传给 DayPicker 的 `today`。
    - 页脚左侧：有参照日且已选时写「距{label} N 天」，有参照日未选时写「{label}日 MM-DD 起可选」。右侧「清除」（无值时禁用）、「今天」（今天不可选或已选中时禁用）。
    - 点日子：`onChange(iso)`，关闭，焦点回到输入框。
- [ ] **Step 1: 写失败测试**（`today="2026-11-02"`、`min="1990-01-01"`）：
  - 敲 `2026/8/14` 后失焦：`onChange("2026-08-14")`，输入框显示 `2026-08-14`。
  - 敲 `2026/9/31` 后失焦：`onChange("2026/9/31")`，原文保留。
  - 敲完直接回车：同样规范化并触发 `onChange`（Review Focus 1）。
  - 点「打开日历」出现 `role="grid"`，标题里有年份下拉显示「2026年」、月份下拉显示「9月」（值 `2026-09-28`；文案以 zhCN 实际输出为准，断言用正则 `/2026/`、`/9/`）。
  - `reference={{ date: "2026-09-15", label: "投稿" }}`、值 `2026-09-28`：14 日的按钮 `toBeDisabled()`，页脚有「距投稿 13 天」。
  - 点 30 日：`onChange("2026-09-30")`，grid 消失。
  - 值 `2026-01-10` 时点「上个月」：年份下拉变 2025、月份变 12（Review Focus 3）。
  - 打开后按 `Escape`：grid 消失，外层 `onOpenChange` 没被调用（用一个外层 Dialog 包裹验证，Review Focus 4）。
  - `disabled` 时输入框和日历按钮都禁用。
- [ ] **Step 2: 运行确认失败**。
- [ ] **Step 3: 安装依赖并实现** `calendar.tsx`、`date-picker.tsx`。
- [ ] **Step 4: 运行通过**，再跑 `tests/design-system`。
- [ ] **Step 5: 提交** `新增日历与日期选择原语 (PAP-177)`。

### Task 5: 投稿经验表单换上新控件

**Files:**
- Modify: `src/components/experience/ExperienceForm.tsx`（三个日期、文章类型、审稿轮数改用 `Controller`）、`src/components/experience/form-fields.tsx`（`BoxField` 不再给新控件包外框，新增不带外框的字段壳或给 `BoxField` 加 `bare` 选项；删掉不再使用的 `SelectChevron`）
- Modify tests: `tests/components/experience/ExperienceForm.test.tsx`、`tests/components/experience/ExperienceArea.test.tsx`、`tests/e2e/support/experience.ts`

**Interfaces:**
- Consumes: Task 2、3、4 的原语；`todayInShanghai`、`MIN_DATE`。
- 参照日：首轮决定日期 `reference={{ date: submittedOn, label: "投稿" }}`，最终决定日期 `reference={{ date: firstDecisionOn, label: "首轮决定" }}`；参照值不是合法日期时不传。
- 审稿轮数：`value={field.value === "" ? null : Number(field.value)}`，`onValueChange={(n) => field.onChange(n === null ? "" : String(n))}`。
- 保留：投稿日期 `autoFocus`；`register` 原本提供的 `onBlur` 校验时机；`setFocus("submittedOn")` 能聚焦（`Controller` 的 `field.ref` 传给 `inputRef`）。

- [ ] **Step 1: 先改测试**：
  - `selectOptions(文章类型)` 改为点 `combobox` 再点 `option`；第 93 行「文章类型 6 种」改为展开后数 `option`。
  - 第 98–99 行的 `max` / `min` 属性断言，改为「敲 `2026-10-29` 失焦后出现『日期不能晚于今天』」与「敲 `1989-12-31` 失焦后出现『日期不能早于 1990 年』」。
  - 审稿轮数 `toHaveValue(1)` / `toHaveValue(null)` 改为 `toHaveValue("1")` / `toHaveValue("")`。
  - 新增：敲 `2026/9/31` 失焦出现「日期写成 2026-08-14 这样」；投稿日期改成晚于首轮日期后出现「首轮决定日期不能早于投稿日期」，首轮日期的值不清空（Review Focus 2）。
  - e2e `fillExperience`：日期仍用 `fill`（文本框），文章类型改为点 combobox 再点 option。
- [ ] **Step 2: 运行确认失败**：`pnpm test -- tests/components/experience --maxWorkers=2`。
- [ ] **Step 3: 改 `ExperienceForm.tsx` 与 `form-fields.tsx`**。
- [ ] **Step 4: 运行通过**（`tests/components/experience` 全绿）。
- [ ] **Step 5: 提交** `投稿经验表单换上新的日期、下拉与数字控件 (PAP-177)`。

### Task 6: 个人资料「身份」与文献库排序

**Files:**
- Modify: `src/components/settings/profile/ProfileForm.tsx`、`src/components/library/SortSelect.tsx`
- Modify tests: `tests/components/settings/profile/ProfileForm.test.tsx`、`tests/components/library/LibraryView.test.tsx`

**Interfaces:**
- 身份：`Controller`；Select 的值空串对应 `null`；选项「不填写」后面跟 `SelectSeparator`；`variant="field" size="lg"`；`sheetTitle="身份"`。
- 排序：`variant="compact"`，`aria-label="排序"`，`sheetTitle="排序"`；选中后 `router.push(libraryHref(pathname, value, 1))`；「按」字保留在触发器左边。

- [ ] **Step 1: 先改测试**：
  - 身份：点开 `combobox`「身份」，`option` 文本依次为 `["不填写", "医师", …, "其他"]`，触发器显示「医师」；选「不填写」后保存请求里 `occupation` 为 `null`。
  - 排序：`selectOptions` 改为点 `combobox`「排序」再点「出版日期」，断言跳转地址不变。
- [ ] **Step 2: 运行确认失败**。
- [ ] **Step 3: 改两个组件**。
- [ ] **Step 4: 运行通过**。
- [ ] **Step 5: 提交** `个人资料身份与文献库排序换上新下拉 (PAP-177)`。

### Task 7: 收尾验证

- [ ] **Step 1**：`rg -n '<select|type="date"|type="number"' src` 没有结果。
- [ ] **Step 2**：`pnpm lint && pnpm typecheck && pnpm test -- --maxWorkers=2` 全绿。
- [ ] **Step 3**：用 `preview_start` 起 `portal-dev`，在桌面和手机（375 宽）宽度下走一遍：投稿经验写一条（敲日期、用日历选日期、年月下拉跳到 2023 年、选文章类型、加减审稿轮数），个人资料改身份，文献库换排序。截图对照画布。
- [ ] **Step 4**：e2e 相关两条用例：`pnpm test:e2e -- tests/e2e/experience*.spec.ts --workers=1`（连 mini 网关，见 `reference_portal_e2e_real_data_via_macmini`）。连不上时如实记录。
- [ ] **Step 5**：spec 状态行改为「已实施」，与计划一起提交 `标记表单控件 spec 已实施 (PAP-177)`。
