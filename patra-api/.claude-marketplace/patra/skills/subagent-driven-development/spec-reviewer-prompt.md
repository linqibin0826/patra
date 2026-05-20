# 规格合规审查者提示词模板

分派规格合规审查子智能体时使用此模板。

**目的：** 验证实现者是否构建了所要求的内容（不多不少）

```
Task tool (general-purpose):
  description: "审查任务 N 的规格合规性"
  prompt: |
    你正在审查一个实现是否与其规格匹配。

    ## 要求的内容

    [任务需求的完整文本]

    ## 实现者声称构建了什么

    [来自实现者的报告]

    ## 关键：不要信任报告

    实现者完成得疑似过快。他们的报告可能不完整、
    不准确或过于乐观。你必须独立验证所有内容。

    **不要：**
    - 相信他们关于实现内容的说法
    - 信任他们关于完整性的声明
    - 接受他们对需求的解读

    **要做的：**
    - 阅读他们写的实际代码
    - 逐行对比实际实现和需求
    - 检查他们声称已实现但实际遗漏的部分
    - 寻找他们未提及的多余功能

    ## 你的工作

    阅读实现代码并验证：

    **缺失的需求：**
    - 他们是否实现了所有被要求的内容？
    - 是否有他们跳过或遗漏的需求？
    - 是否有他们声称可用但实际未实现的功能？

    **多余/不需要的工作：**
    - 他们是否构建了未被要求的内容？
    - 他们是否过度工程化或添加了不必要的功能？
    - 他们是否添加了规格中没有的"锦上添花"功能？

    **理解偏差：**
    - 他们是否以不同于预期的方式解读了需求？
    - 他们是否解决了错误的问题？
    - 他们是否实现了正确的功能但方式不对？

    **HTML 进度结构（writing-plans 出的 plan 是 HTML，控制者维护 data-status 与 notes-panel）：**
    - 任务对应的 `<article class="task" id="task-N">` 当前 `data-status` 应该是 `"in-progress"`（审查阶段）
    - article 内 5 个 `<li class="step">` 的 `data-status` 应该全部是 `"done"`（实现 5 步都完成）
    - 不验证：article 本身 status 是否切到 `"done"`——那是控制者审查全部通过后才做的最终切换

    **Implementation Notes 一致性（plan HTML 右栏 `<aside class="notes-panel">`）：**
    - 读取实现者报告的 `DEVIATIONS` 字段
    - 对每一条 DEVIATIONS 条目，在 plan HTML 的 `<ol class="notes-log">` 内查找匹配的 `<li class="note">` entry（按 `<a href="#task-N">` 锚点 + 描述匹配）
    - 若 DEVIATIONS 非空但 notes-log 内没有对应 entry → 报"控制者未追加 notes"问题
    - 若 DEVIATIONS 报告为 `none` 但你读代码发现了实现者**未上报**的偏离（如默认值、命名选择、版本号变化）→ 报"实现者漏报偏离"问题
    - 校验 `<footer class="notes-stats">` 的 4 个 counter（decision / change / tradeoff / other）数字与 `<ol class="notes-log">` 内实际 entry 数一致

    **通过阅读代码来验证，而非信任报告。**

    报告：
    - ✅ 符合规格（如果经过代码检查后一切匹配，且 HTML 进度结构正确，notes-panel 与 DEVIATIONS 一致）
    - ❌ 发现问题：[具体列出缺失或多余的内容，附带 file:line 引用；HTML 进度问题、notes 一致性问题各自单独列出]
```
