# 正文转纯文本（PAP-128）实施计划

> **For agentic workers:** 本计划在当前会话内联执行（superpowers:executing-plans），按 TDD 一条用例一个红绿循环。提交等编排会话验收后一次完成，各任务末尾不单独提交。

**Goal:** 把 Markdown 转纯文本的转换器从 interaction 的 `notification` 子域搬到底座 `subject`，按定稿规则重写，并让现有提醒摘录改用它。

**Architecture:** 底座 domain 定义端口 `PlainTextPort` 与值对象 `PlainText`；infra 用 commonmark-java 0.30.1 配成门户的 Markdown 子集，走语法树收集文字。`notification` 子域的监听器改用底座端口，`ExcerptText` 从 `PlainText` 转来，`ExcerptRules` 截断时不切开提及。

**Tech Stack:** Java 25、commonmark-java 0.30.1、JUnit 5 + AssertJ + Mockito、Spring Boot 4 流程测试（Testcontainers PG）

**Spec:** `docs/patra/specs/2026-10-30-plaintext-conversion-design.md`

## Global Constraints

- 所有方法（任何访问级别）写 `///` Markdown Javadoc，中文；`///` 行不超过 100 列（gjf 会把超长的折成 `//`）
- 不写全类名，用 import；空集合 `List.of()` / `Set.of()`
- 测试方法 snake_case：`should_…` 或 `…_when_…`；单测 JUnit 5 + AssertJ + Mockito
- 提及正则：`<@([1-9][0-9]{0,18})>`；断开字符：U+2060
- 列表前缀：无序 `• `，有序 `编号. `（编号 = 列表起始编号 + 序号）；引用块外加 `“` `”`
- 站内摘录：存储上限 300 单位不变，渲染上限 120 字素不变
- 先跑 `./gradlew spotlessApply`，再跑 `./gradlew check`（含集成测试，后台运行）

## Review Focus

1. 整条评论只有一个提及 `<@42>`：去首尾空白不能吃掉标记 → 输出 `<@42>`（Task 2 测试 `should_keep_lone_mention`）
2. 字面同形文字紧挨真提及：`` `<@5>`<@5> ``（5 在名单里）→ `<⁠@5><@5>`（Task 2 测试 `should_break_lookalike_next_to_real_mention`）
3. 整条只有图片或只有引用 → 输出非空（Task 2 测试 `should_keep_image_as_source`、引用块用例）
4. 空正文、只有空白 → `""`（Task 2 测试 `should_return_empty_for_blank`）
5. 截断上限正好落在提及之后、或提及在开头放不下 → 不出半个名字、去掉末尾空白（Task 3 `ExcerptRulesTest`）

---

### Task 1: 底座的端口与值对象

**Files:**
- Create: `patra-api/patra-interaction/patra-interaction-domain/src/main/java/dev/patra/interaction/domain/subject/model/vo/PlainText.java`
- Create: `patra-api/patra-interaction/patra-interaction-domain/src/main/java/dev/patra/interaction/domain/subject/port/markdown/PlainTextPort.java`
- Test: `patra-api/patra-interaction/patra-interaction-domain/src/test/java/dev/patra/interaction/domain/subject/model/vo/PlainTextTest.java`

**Interfaces:**
- Produces: `record PlainText(String value)`，`public static final Pattern MENTION`，`public static final char WORD_JOINER = '⁠'`，`static PlainText of(String value)`（`null` 拒绝）；`interface PlainTextPort { PlainText toPlainText(String markdown, Set<Long> mentionedUserIds); }`

- [x] **Step 1: 写失败测试** `PlainTextTest`
  - `should_match_canonical_mention_token`：`MENTION` 匹配 `<@42>`、`<@9999999999999999999>`（19 位），不匹配 `<@0>`、`<@042>`、`<@>`、`<⁠@42>`
  - `should_reject_null_value`：`PlainText.of(null)` 抛 `NullPointerException`
- [x] **Step 2: 跑测试确认失败**：`./gradlew :patra-api:patra-interaction:patra-interaction-domain:test --tests "*PlainTextTest*"`，编译失败
- [x] **Step 3: 实现** `PlainText`（类注释写第 4 节约定）与 `PlainTextPort`（方法注释写名单语义：评论传 `mentionedUserIds`，投稿经验传空集合）
- [x] **Step 4: 跑测试确认通过**

### Task 2: commonmark 转换器（infra.subject.markdown）

**Files:**
- Create: `patra-api/patra-interaction/patra-interaction-infra/src/main/java/dev/patra/interaction/infra/subject/markdown/CommonmarkPlainTextAdapter.java`
- Create: `…/infra/subject/markdown/AngleBracketInlineParser.java`（含内部 `Factory`）
- Create: `…/infra/subject/markdown/MentionNode.java`
- Test: `patra-api/patra-interaction/patra-interaction-infra/src/test/java/dev/patra/interaction/infra/subject/markdown/CommonmarkPlainTextAdapterTest.java`

**Interfaces:**
- Consumes: Task 1 的 `PlainText`、`PlainTextPort`
- Produces: `@Component class CommonmarkPlainTextAdapter implements PlainTextPort`；`final class MentionNode extends CustomNode`（`String digits()`）；`final class AngleBracketInlineParser implements InlineContentParser`，`static final class Factory implements InlineContentParserFactory`（触发字符 `<`）

解析器配置：`enabledBlockTypes(Set.of(Heading.class, BlockQuote.class, ListBlock.class))`、`customInlineContentParserFactory(new AngleBracketInlineParser.Factory())`、`includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)`。`AngleBracketInlineParser`：`<@` + 首位 1–9 的数字串 + `>` 产出 `MentionNode`；否则只吃掉 `<`，产出 `Text("<")`。

收集器的算法（签名与测试不决定的部分）：

```
字面文字攒在 pending；真提及（MentionNode 且数字能转 long 且在名单里）到来时：
  out += 断开(pending)；pending 清空；out += "<@" + id + ">"
非提及的 MentionNode：pending += "<@" + digits + ">"
断开(s) = s 里每处 MENTION 匹配，在 '<' 后插入 U+2060
块（段落、标题）：前后各 pending += "\n"
软换行、硬换行：pending += "\n"
引用块：子收集器转好 inner（已整理空白）；inner 非空时 out 另起一行写 "“" + inner + "”"
列表项：子收集器转好 inner（直接子节点是列表的取原文）；inner 非空时另起一行写 前缀 + inner
图片、跨行三反引号行内代码、链接定义、引用式链接（源位置的原文以 "]" 结尾）：pending += 源位置原文（多段用 "\n" 拼）
结束：out += 断开(pending)；整理空白（按 "\n" 分行，(?U)\s+ → " "，strip，去空行，"\n" 拼回）
```

逐条红绿（每条先写测试、确认失败、实现、确认通过；测试方法名与断言如下，`m(…)` = `adapter.toPlainText(…, Set.of(42L)).value()`，`p(…)` 用空名单）：

- [x] `should_keep_listed_mention`：`m("问 <@42> 一下")` = `问 <@42> 一下`
- [x] `should_keep_lone_mention`：`m("<@42>")` = `<@42>`
- [x] `should_break_unlisted_token`：`m("<@7> 看")` = `<⁠@7> 看`
- [x] `should_break_token_in_inline_code`：`m("`<@42>`")` = `<⁠@42>`
- [x] `should_break_escaped_token`：`m("\\<@42>")` = `<⁠@42>`
- [x] `should_break_out_of_range_token`：`p("<@9999999999999999999>")` = `<⁠@9999999999999999999>`
- [x] `should_break_token_assembled_across_nodes`：`p("<@*5*>")` = `<⁠@5>`
- [x] `should_break_lookalike_next_to_real_mention`：`adapter.toPlainText("`<@5>`<@5>", Set.of(5L))` = `<⁠@5><@5>`
- [x] `should_break_every_token_in_post_body`：`p("见 <@42>")` = `见 <⁠@42>`
- [x] `should_keep_pmid_doi_and_url_as_typed`：`p("PMID: 27940189，doi:10.1038/nature12373，https://doi.org/10.1/abc")` 原样
- [x] `should_keep_link_text_only`：`p("见 [这篇](https://x.org)")` = `见 这篇`
- [x] `should_keep_angle_bracket_url_as_source`：`p("<https://x.org>")` = `<https://x.org>`
- [x] `should_keep_reference_link_and_definition_as_source`：`p("看 [x]\n\n[x]: https://y.org")` = `看 [x]\n[x]: https://y.org`
- [x] `should_keep_image_as_source`（含整条只有图片）：`p("图 ![说明](u.png)")` = `图 ![说明](u.png)`；`p("![说明](u.png)")` = `![说明](u.png)`
- [x] `should_keep_inline_code_content`：`p("`a*b`")` = `a*b`
- [x] `should_keep_multiline_fence_as_source`：`p("```java\nint a;\n```")` = "```java\nint a;\n```"
- [x] `should_treat_indented_lines_as_text`：`m("看\n\n    问 <@42>\n\n完")` = `看\n问 <@42>\n完`；`p("段落\n    续行")` = `段落\n续行`
- [x] `should_treat_indented_lines_in_list_item_as_text`：`p("- a\n\n      b")` = `• a\nb`
- [x] `should_wrap_quote_in_quotation_marks`：`p("> 文中说 OR=1.8\n\n我觉得不够")` = `“文中说 OR=1.8”\n我觉得不够`；多行引用 `p("> 一\n> 二")` = `“一\n二”`；多层 `p("> 外\n>> 内")` = `“外\n“内””`；空引用 `p(">\n\n正文")` = `正文`
- [x] `should_prefix_bullet_items`：`p("- 一\n- 二")` = `• 一\n• 二`
- [x] `should_number_ordered_items_from_start`：`p("3. 甲\n4. 乙")` = `3. 甲\n4. 乙`
- [x] `should_keep_nested_list_as_source`：`p("- a\n  - b")` = `• a\n- b`
- [x] `should_keep_heading_text_on_its_own_line`：`p("# 结论\n## 方法\n正文")` = `结论\n方法\n正文`；`p("结论\n---")` = `结论`
- [x] `should_strip_emphasis_but_keep_unpaired_stars`：`p("**粗** *斜*")` = `粗 斜`；`p("2 * 3 = 6")` 原样；`p("p*0.05 与 q*2")` = `p0.05 与 q2`
- [x] `should_keep_html_as_text`：`p("<b>x</b>")` = `<b>x</b>`；`p("<div>\nx\n</div>")` = `<div>\nx\n</div>`
- [x] `should_keep_thematic_break_as_text`：`p("上\n\n***\n\n下")` = `上\n***\n下`
- [x] `should_turn_breaks_and_paragraphs_into_newlines`：`p("一\n二  \n三\\\n四\n\n五")` = `一\n二\n三\n四\n五`
- [x] `should_collapse_whitespace_and_drop_blank_lines`：`p("  a  \t b　　c d  \n\n\n  e  ")` = `a b c d\ne`
- [x] `should_return_empty_for_blank`：`p("")` = `""`；`p("  \n ")` = `""`
- [x] `should_handle_image_and_mention_inside_quote`：`m("> 看 ![图](u.png)\n> 问 <@42>")` = `“看 ![图](u.png)\n问 <@42>”`
- [x] `should_resolve_escapes_and_entities`：`p("\\*不是强调\\* &amp; &lt;b&gt;")` = `*不是强调* & <b>`；`m("&lt;@42&gt;")` = `<\u2060@42>`

### Task 3: notification 子域改用底座转换器

**Files:**
- Modify: `…/domain/notification/model/vo/ExcerptText.java`（加 `fromPlainText`）
- Modify: `…/domain/notification/policy/ExcerptRules.java`（`render` 截断规则）
- Modify: `…/app/notification/listener/CommentNotificationListener.java`（注入 `PlainTextPort`（底座））
- Delete: `…/domain/notification/port/excerpt/PlainTextPort.java`、`…/infra/notification/excerpt/CommonmarkPlainTextAdapter.java`、`…/infra/notification/excerpt/CommonmarkPlainTextAdapterTest.java`
- Modify: `…/infra/notification/package-info.java`、`patra-interaction-infra/build.gradle.kts`（commonmark 注释）、`patra-api/patra-interaction/README.md`（subject 行加「正文转纯文本」）
- Test: `ExcerptTextTest`、`ExcerptRulesTest`、`CommentNotificationListenerTest`

**Interfaces:**
- Consumes: Task 1 的 `PlainText`、`PlainTextPort`
- Produces: `static ExcerptText fromPlainText(PlainText text)`

- [x] `ExcerptTextTest.should_build_segments_from_plain_text`：`fromPlainText(PlainText.of("问 <@42>\n好"))` 的 `segments()` = `Literal("问 ")`、`Mention(42)`、`Literal(" 好")`；`should_keep_broken_token_literal`：`fromPlainText(PlainText.of("<⁠@7>"))` 只有一段 `Literal("<⁠@7>")`、`mentionIds()` 为空
- [x] `ExcerptRulesTest.should_stop_before_mention_that_does_not_fit`：117 个 `字` + 提及 5（`chen-yu`，主页可见）→ `text` = `字`×117、`truncated` 为真（117 + 8 > 120）；`should_strip_trailing_space_when_cut`：116 个 `字` + `" "` + 提及 5 → `字`×116，没有末尾空格；原有四个用例不改
- [x] `ExcerptRules.render`：按段累加，字面段按字素截，提及段整段放得下才放；截了就 `stripTrailing()`
- [x] `CommentNotificationListenerTest`：mock 底座 `PlainTextPort`，`toPlainText` 返回 `PlainText.of("摘录")`；`verify(plainText).toPlainText("正文", Set.of(C))`
- [x] 监听器 `excerptOf`：`ExcerptText.fromPlainText(plainText.toPlainText(body, Set.copyOf(ids))).cut(ExcerptRules.MAX_STORED_UNITS)`
- [x] 删除旧端口、旧实现与旧测试；更新 package-info、构建注释、README
- [x] 编译与单测：`./gradlew :patra-api:patra-interaction:patra-interaction-domain:test :patra-api:patra-interaction:patra-interaction-app:test :patra-api:patra-interaction:patra-interaction-infra:test`
- [x] 架构：`./gradlew :patra-api:patra-interaction:patra-interaction-boot:test --tests "*InteractionArchitectureTest*"` 通过

### Task 4: 流程测试与全量验证

**Files:**
- Modify: `patra-api/patra-interaction/patra-interaction-boot/src/integrationTest/java/dev/patra/interaction/notification/NotificationRulesFlowIT.java`

- [x] 新增 `should_render_excerpt_by_plain_text_rules`：B 回复 A，正文 `"> 原文\n\n看 ![图](u.png)\n第二行"`；A 的「回复我的」第一条 `excerpt` = `“原文” 看 ![图](u.png) 第二行`，`excerptTruncated` 为假
- [x] 跑：`./gradlew :patra-api:patra-interaction:patra-interaction-boot:integrationTest --tests "*NotificationRulesFlowIT*"`
- [x] `./gradlew spotlessApply` 后 `./gradlew check`（后台），全部通过
- [x] 交接笔记写进共享笔记目录（给 PAP-141 / 143 / 146）
