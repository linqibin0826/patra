---
name: receiving-code-review
description: 收到代码审查反馈后、实施建议之前使用，尤其当反馈不明确或技术上有疑问时——需要技术严谨性和验证，而非敷衍附和或盲目执行
---

# 接收代码审查

## 概述

代码审查需要的是技术评估，不是情绪表演。

**核心原则：** 先验证再实施。先提问再假设。技术正确性优先于社交舒适度。

## 反馈来源

Patra 是单人项目（见 `CLAUDE.md`）。代码审查反馈通常来自一个来源：

- **`patra:requesting-code-review` 派遣的 general-purpose subagent** —— 审查者只看 git diff + plan.html，没有你的完整会话上下文，可能误判"为什么这样写"。所有外部反馈都需要用 patra-api 项目规范交叉验证。

只有 GitHub PR 上偶尔会有真人审查（社区 PR、ultrareview 输出的评论等）——同样按"无完整上下文"对待。

## 响应模式

```
收到代码审查反馈时：

1. 阅读：完整阅读反馈，不急于反应
2. 理解：用自己的话复述需求（或提问）
3. 验证：对照 CLAUDE.md / tech/*.md / 代码库实际情况检查
4. 评估：对 patra-api 来说技术上合理吗？是否违反规范？
5. 回应：技术性确认或有理有据的反驳
6. 实施：按 Critical → Important → Minor 顺序，一次一项，逐个测试
```

## 按 Critical / Important / Minor 分档响应

审查反馈用 `patra:requesting-code-review` 的三档分级。响应方式不同：

| 等级 | 典型问题 | 响应方式 |
|------|---------|---------|
| **Critical** | 编译失败、convention plugin check 失败（六边形污染）、CommandBus 绕过、Domain 引 Spring、安全问题 | **立即修复**，不可推迟；修完跑 `./gradlew check` 验证 |
| **Important** | Record VO 缺防御性拷贝、Port 命名违规、HTTP Interface 直接捕 `RestClientException`、JPA Entity 未继承 `BaseJpaEntity`、缺集成测试 | **继续下个任务之前修复**；跑相关模块 test/integrationTest 验证 |
| **Minor** | JavaDoc 用了 `/** */`、Lombok 该用没用、方法 >80 行、日志等级不当 | **评估后决定**：是 YAGNI（不影响任何东西）？立即修但不阻塞流程？或者记录到 plan.html 的 follow-up 节稍后处理 |

## 禁止的回应

**绝不要说：**
- "你说得太对了！"（明确违反 CLAUDE.md 规定）
- "好观点！"/"反馈很棒！"（敷衍表演）
- "让我立刻实施"（在验证之前）
- "感谢你发现这个！"/任何感谢的表达

**应该这样做：**
- 复述技术需求
- 提出澄清性问题
- 如果审查意见有误，用技术理由反驳
- 直接动手做（行动胜于言辞）

## 绿地项目无借口呼应

Patra 是绿地项目（见 `CLAUDE.md`），反驳代码审查反馈时禁止使用以下"借口"作为理由：

- ❌ "为了向后兼容所以保留" —— 绿地无历史包袱
- ❌ "分阶段实施，稍后再改" —— 禁止分阶段交付
- ❌ "保留 deprecated 标记一段时间" —— 直接删除或重写
- ❌ "团队 deadline 来不及" —— 单人项目无外部 deadline
- ❌ "向后兼容 adapter 已经写了，删了浪费" —— 即刻删除

**反驳必须是技术理由**：
- ✅ 六边形架构约束（如"domain 不能引 Spring 注解"）
- ✅ Port 命名规范（按 `tech/port-service.md`）
- ✅ Record VO 设计规则（按 `code-style.md`）
- ✅ 异常体系（按 `tech/error-handling.md`）
- ✅ Lombok / JavaDoc 风格（按 `code-style.md`）
- ✅ TDD 红-绿循环已经验证

## 处理不明确的反馈

```
如果有任何一项不明确：
  停下来——先不要实施任何内容
  就不明确的项目提出澄清

为什么：各项之间可能有关联。部分理解 = 错误实施。
```

**示例：**
```
审查者："修复 Critical 1-2 项 + Important 1-2 项"
你理解 Critical 1、Critical 2、Important 1。对 Important 2 不确定。

❌ 错误做法：先实施 3 项，稍后再问 Important 2
✅ 正确做法："Critical 1、2 + Important 1 我理解了。Important 2 需要澄清后再动手。"
```

## 验证反馈（实施前必查）

```
实施之前：
  1. 对 patra-api 项目规范来说技术上正确吗？（查 CLAUDE.md / tech/*.md）
  2. 是否会破坏现有功能？跑相关 test 看看
  3. 当前实现这样写是否有原因？（看 git log / 之前 commit 的设计动机）
  4. 跨模块一致吗？（如改 Port 命名要查所有引用）
  5. 审查者了解完整上下文吗？（审查者没有会话历史，可能误判）

如果建议似乎有误：
  用技术理由反驳

如果无法轻易验证：
  说明情况："没有 [X] 我无法验证这一点。我应该 [调查/提问/先做]？"

如果与 CLAUDE.md / tech/*.md / 之前架构决策冲突：
  先停下来跟用户讨论
```

**项目原则：** "对外部反馈要持怀疑态度，但要仔细核实。"

## YAGNI 检查——针对"完善实现"建议

```
如果审查者建议"完善实现"或"加上 X 功能"：
  在代码库中 grep 实际使用情况

  如果没人用："这个接口没有被调用。删掉它（YAGNI）？"
  如果有人用：那就实施
```

**项目原则：** 如果项目不需要这个功能，就不要加。Patra 绿地项目尤其禁止"预防性"代码。

**真实场景：**

```
审查者："给 ProvenanceLookupPort 加 batch 接口提升性能"

✅ 你：
   $ rg "ProvenanceLookupPort" --type java -l
   只在 IngestRequestService 用到，单次调用没有 N+1 问题
   "grep 了，目前只有单条查询场景，没有 batch 调用方。
    按 YAGNI 删除建议；如果未来出现 N+1，再加 batch port。"
```

## 实施顺序

```
对于包含多项的反馈：
  1. 先澄清所有不明确的项
  2. 然后按以下顺序实施：
     - Critical（编译失败、六边形破坏、CommandBus 绕过、安全）
     - Important（Record VO、Port 命名、参数对象、集成测试）
     - Minor（JavaDoc 风格、Lombok 用法、命名细节）
  3. 逐个测试每项修复：
     - Critical 修完跑 `./gradlew check`
     - Important 修完跑 `./gradlew :patra-{svc}-{layer}:test`
     - Minor 修完编译过即可
  4. 验证没有回归（同一模块所有原本通过的测试仍通过）
```

## 何时反驳

在以下情况反驳：

| 反驳理由 | 例子 |
|---------|------|
| 建议会破坏现有功能 | 改 Port 命名但漏了 5 个调用方 |
| 审查者缺少完整上下文 | 不知道这个 record 是某个 event 序列化的，改成 class 会破坏 Jackson |
| 违反 YAGNI（功能没人用） | 给 LookupPort 加 batch 接口但没调用方 |
| 对 patra-api 技术栈不正确 | 建议用 `@RequiredArgsConstructor` 但项目这一层用 manual constructor 注入 |
| 与 CLAUDE.md / tech/*.md 规范冲突 | 建议 Repository 用 `Port` 后缀，但 `tech/port-service.md` 明确禁止 |
| 与历史架构决策冲突 | 之前明确删了某 adapter，审查者又建议加回 |

**绝对不能用的反驳理由：**
- ❌ 向后兼容、deprecated 保留、分阶段实施（绿地禁令）
- ❌ "我累了"、"工期紧"、"先合并稍后改"
- ❌ 个人偏好（必须是项目规范支持）

**如何反驳：**
- 用技术理由，不要带防御情绪
- 引用 CLAUDE.md / tech/*.md 具体条款
- 引用可正常工作的测试 / `./gradlew check` 输出
- 如果涉及架构问题，让用户拍板

## 确认正确的反馈

当反馈确实正确时：
```
✅ "已修复。[简要说明改了什么]"
✅ "发现得好——[具体问题]。已在 [位置] 修复。"
✅ [直接修复并在代码中体现]

❌ "你说得太对了！"
❌ "好观点！"
❌ "感谢你发现了这个！"
❌ "感谢你 [任何内容]"
❌ 任何感谢的表达
```

**为什么不用感谢：** 行动说明一切。直接修复。代码本身就能表明你收到了反馈。

**如果你发现自己要写"感谢"：** 删掉它。直接说明修复内容。

## 优雅地纠正自己的反驳

如果你反驳了但事后发现自己错了：
```
✅ "你是对的——我检查了 [X]，确实 [Y]。正在实施。"
✅ "验证后确认你是对的。我最初的理解有误，因为 [原因]。正在修复。"

❌ 长篇道歉
❌ 为自己的反驳辩护
❌ 过度解释
```

如实陈述纠正，然后继续。

## 常见错误

| 错误 | 修正 |
|------|------|
| 敷衍附和 | 复述需求或直接行动 |
| 盲目实施 | 先对照 CLAUDE.md / tech/*.md 验证 |
| 批量实施不测试 | 一次一项，逐个测试 |
| 假设审查者一定对 | 检查是否会破坏现有功能、是否符合 Patra 规范 |
| 回避反驳 | 技术正确性 > 社交舒适度 |
| 部分理解就开始实施 | 先澄清所有项 |
| 无法验证却继续推进 | 说明限制，请求指导 |
| 用"向后兼容"反驳 | 绿地项目禁止；必须是技术理由 |

## 真实案例

**敷衍附和（反面例子）：**
```
审查者："删除 @Deprecated 兼容层"
❌ "你说得太对了！让我删掉它……"
```

**技术验证 + 绿地纠正（正面例子）：**
```
审查者：建议保留 @Deprecated 兼容层，给后续渐进式迁移留时间
✅ "Patra 是单人绿地项目，CLAUDE.md 明确禁止 deprecated 标记和分阶段实施。
    @Deprecated 兼容层应该直接删除，调用方一并改完。已删除并修了 3 处调用方
    （IngestRequestService.java:42, PublicationGateway.java:88, ProvenanceMapper.java:15），
    `./gradlew check` 全绿。"
```

**技术反驳六边形破坏（正面例子）：**
```
审查者：建议在 ProvenanceCode VO 上加 @Component 方便注入
✅ "ProvenanceCode 在 patra-{svc}-domain 模块。CLAUDE.md tech/port-service.md 和
    convention plugin 都禁止 domain 层引入 Spring 注解，加 @Component 会让
    `./gradlew :patra-{svc}-domain:check` 直接 FAILED（六边形纯净性）。
    VO 应该通过工厂方法构造，由 Application 层装配，不需要 DI。"
```

**YAGNI（正面例子）：**
```
审查者："给 ProvenanceLookupPort 加 batch 接口和缓存预热"
✅ "rg 了一下——目前只有 IngestRequestService 单次调用，没有 N+1 场景。
    按 YAGNI 删除建议；未来出现性能问题再加 batch port + 缓存装饰器
    （按 tech/port-service.md 的 LookupPort 装饰器模式）。"
```

**不明确的项（正面例子）：**
```
审查者："修复 Critical 1-2 项 + Important 1-2 项"
你理解 Critical 1、Critical 2、Important 1。对 Important 2 不确定。
✅ "Critical 1、2 + Important 1 我理解了。Important 2 需要澄清后再动手。"
```

## GitHub 评论回复

在 GitHub 上回复行内审查评论时（ultrareview / 真人 PR 审查），在评论线程中回复
（`gh api repos/{owner}/{repo}/pulls/{pr}/comments/{id}/replies`），不要发顶层 PR 评论——
保持上下文聚焦。

## 底线

**外部反馈 = 待评估的建议，不是必须执行的命令。**

验证。质疑。然后实施。

不要敷衍附和。始终保持技术严谨。
