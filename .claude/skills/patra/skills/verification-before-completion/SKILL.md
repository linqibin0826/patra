---
name: verification-before-completion
description: 在宣称工作完成、已修复或测试通过之前使用，在提交或创建 PR 之前——必须运行验证命令并确认输出后才能声称成功；始终用证据支撑断言
---

# 完成前验证

## 概述

在没有验证的情况下宣称工作完成，这不是高效，而是不诚实。

**核心原则：** 始终用证据支撑结论。

**对这条规则敷衍了事，就等于违背了它的精神。**

## 铁律

```
没有新鲜的验证证据，不许宣称完成
```

如果你在这条消息中没有运行验证命令，就不能声称测试通过。

## 绿地项目无借口

Patra 是单人维护的绿地项目（见 `CLAUDE.md`），不存在以下"合理化"借口：

- ❌ "时间不够" —— 项目明确"质量优先，可投入任何必要时间"
- ❌ "团队要 deadline 先合并" —— 没有团队，没有外部 deadline
- ❌ "先合并稍后补测试" —— 禁止分阶段交付（CLAUDE.md 明令禁止）
- ❌ "只跑单元测试就够了" —— 集成测试 + 架构纯净性 + 编译，全栈门控才算门控
- ❌ "向后兼容/数据迁移所以验证可以放宽" —— 绿地项目没有历史包袱，不存在这类妥协

绿地项目的特殊之处：**没有外部压力来逼你妥协，所以也没有借口可以躲在背后**。

## 门控函数

```
在宣称任何状态或表达满意之前：

1. 确定：什么 Gradle/JUnit 命令能证明这个结论？
2. 运行：执行完整命令（重新运行，完整执行）
3. 阅读：完整输出，检查 BUILD SUCCESSFUL/FAILED，统计失败数
4. 验证：输出是否支持这个结论？
   - 如果否：用证据说明实际状态
   - 如果是：带证据陈述结论
5. 只有这时：才能做出结论

跳过任何一步 = 说谎，不是验证
```

## 常见失败模式

| 结论 | 需要 | Patra 实际命令 | 不够格 |
|------|------|---------------|--------|
| 单元测试通过 | JUnit 输出：tests successful，failed: 0 | `./gradlew :patra-{svc}-{layer}:test` | 之前的运行结果、"应该会通过" |
| 集成测试通过 | TestContainers 起容器后 BUILD SUCCESSFUL | `./gradlew :patra-{svc}-infra:integrationTest` | 单元测试通过 ≠ 集成测试通过 |
| 编译成功 | javac exit 0，零错误 | `./gradlew compileJava compileTestJava` | IDE 标红消失、"代码看起来没问题" |
| 六边形架构纯净 | convention plugin 校验通过 | `./gradlew :patra-{svc}-domain:check` | "我没引入 Spring 注解吧" |
| 全栈门控 | check 任务全绿（编译+test+integrationTest+架构约束） | `./gradlew check` | 部分检查、单模块通过 |
| Bug 已修复 | 复现 bug 的测试现在通过 | `./gradlew test --tests "BugRegressionTest"` | 代码改了，假设已修复 |
| 回归测试有效 | 红-绿循环已验证 | 见下文"关键模式"红-绿示例 | 测试只通过了一次 |
| Subagent 已完成 | `git diff` 显示真实变更 + HTML data-status | `git diff HEAD~1 --stat` | Subagent 报告"成功" |
| 需求已满足 | 逐项核对 plan.html `<li class="step">` | 重读 plan.html，对照实现 | 测试通过 ≠ 需求完整 |

## 红线——停下来

- 使用"应该"、"大概"、"似乎"
- 验证前就表达满意（"太好了！"、"完美！"、"搞定！"等）
- 即将 `git push` / 创建 PR 却没跑过 `./gradlew check`
- 信任 subagent 的成功报告
- 依赖部分验证（只跑了 unit 没跑 integrationTest）
- 想着"就这一次"
- 累了想赶紧收工
- **任何暗示成功但实际未运行验证的措辞**

## 防止合理化

| 借口 | 现实 |
|------|------|
| "应该能行了" | 运行 `./gradlew check` |
| "我有信心" | 信心 ≠ JUnit 输出 |
| "就这一次" | 没有例外 |
| "IDE 没标红" | IDE ≠ Gradle 编译 |
| "Linter 通过了" | Linter ≠ 编译器 ≠ 测试 |
| "Subagent 说成功了" | 独立验证 `git diff` + HTML 进度 |
| "我累了" | 疲劳 ≠ 借口 |
| "部分检查就够了" | `:patra-ingest-app:test` 通过 ≠ `check` 通过 |
| "TestContainers 太慢，跳过 integrationTest" | 慢 ≠ 可以跳过 |
| "换个说法这条规则就不适用了" | 精神大于字面 |

## 关键模式

### 单元测试

```
✅ $ ./gradlew :patra-ingest-app:test --tests "IngestRequestServiceTest"
   > Task :patra-ingest-app:test
   IngestRequestServiceTest > shouldRejectBlankProvenance() PASSED
   IngestRequestServiceTest > shouldEnqueuePlan() PASSED
   BUILD SUCCESSFUL in 8s
   "全部测试通过（2/2）"

❌ "应该能通过了" / "看起来对了"
```

### 回归测试（TDD 红-绿验证）

绿地项目里，写完测试不仅要"通过"，还要**确认它真的能抓住 bug**——否则你只是写了一个永远不会失败的测试。

```
✅ 真正的红-绿循环（以 ProvenanceCode VO 为例）：

   1. 先写测试（针对 PROVENANCE_REQUIRED 这条不变式）：
      @Test
      void shouldRejectBlankCode() {
        assertThatThrownBy(() -> ProvenanceCode.of(""))
            .isInstanceOf(IllegalArgumentException.class);
      }

   2. 运行 → 必须红（实现还没加校验）：
      $ ./gradlew :patra-ingest-domain:test --tests "ProvenanceCodeTest.shouldRejectBlankCode"
      > FAILED: expected IllegalArgumentException

   3. 加最小实现：
      public static ProvenanceCode of(String code) {
        if (code == null || code.isBlank()) {
          throw new IllegalArgumentException("provenance code blank");
        }
        return new ProvenanceCode(code);
      }

   4. 运行 → 必须绿：
      $ ./gradlew :patra-ingest-domain:test --tests "ProvenanceCodeTest.shouldRejectBlankCode"
      > PASSED

   5. 临时回退实现（注释掉 if）→ 重跑必须红：
      > FAILED: expected IllegalArgumentException
      ↑ 证明这个测试真的能抓到 bug

   6. 恢复实现 → 重跑必须绿：
      > PASSED

❌ "我写了回归测试"（没有经过红-绿验证 → 可能是个永远通过的死测试）
```

### 构建 / 编译

```
✅ $ ./gradlew compileJava compileTestJava
   BUILD SUCCESSFUL in 12s
   "编译通过"

❌ "IDE 没标红了"（IDE 不等于 Gradle，且 IDE 可能用 incremental cache）
❌ "代码看起来对"（人眼 ≠ javac）
```

### 六边形架构纯净性

Patra 用 convention plugin 在 domain 层禁用框架依赖（见 `build-logic/`），违规会编译失败。

```
✅ $ ./gradlew :patra-ingest-domain:check
   BUILD SUCCESSFUL
   "domain 层纯净（无 Spring/JPA 依赖）"

❌ 在 domain 层加了 @Component / @Entity 还说"应该没问题"
```

### 全栈门控（PR 前必跑）

```
✅ $ ./gradlew check
   > Task :patra-ingest-domain:test PASSED
   > Task :patra-ingest-app:test PASSED
   > Task :patra-ingest-infra:test PASSED
   > Task :patra-ingest-infra:integrationTest PASSED (TestContainers 起 PG17)
   > Task :patra-ingest-adapter:test PASSED
   BUILD SUCCESSFUL in 2m 14s
   "全栈门控通过，可提 PR"

❌ "我只跑了单元测试" / "TestContainers 太慢就跳过了"
```

### 需求满足（基于 HTML plan）

writing-plans 输出的 plan 是 HTML，每个任务下挂 `<li class="step">`。声称任务完成前：

```
✅ 重读 plan.html → 对照 <article id="task-N"> 内每个 <li class="step"> → 全部 done
   → <li class="acceptance"> 验收标准对照实际代码与测试 → 通过
   → 把 <article data-status> 切到 "done"
   → 报告 "任务 N 完成（n/n steps done，验收标准 m/m 满足）"

❌ "测试通过了，阶段完成"（没对照 plan 的 acceptance 条目，也没切 data-status）
```

### Subagent 委派验证

信任 subagent 是高风险行为，详见 `patra:subagent-driven-development` 控制者复审章节。最小验证：

```
✅ Subagent 报告"任务 3 完成" → 执行：
   1. git diff HEAD --stat     # 确认有真实代码变更
   2. ./gradlew :patra-{svc}-{layer}:test  # 确认测试真的能跑通
   3. 打开 plan.html 看 task-3 的 data-status 是否切到 "in-progress"
   4. 阅读 5 个 step 的 data-status 是否都是 "done"
   → 全部通过才能进入规格合规审查

❌ "Subagent 说成功了" → 直接切 data-status="done"
```

## 为什么这很重要

来自历史失败记录：
- 搭档说"我不信你"——信任被破坏
- 未定义的函数被交付——会直接崩溃
- 遗漏需求被交付——功能不完整
- 虚假完成浪费的时间 → 返工 → 重做
- 违反原则："诚实是核心价值。如果你说谎，就会被替换。"

绿地项目里，**这些代价由你独自承担**——没人帮你擦屁股。

## 何时使用

**以下情况之前必须使用：**
- 任何形式的成功/完成声明
- 任何满意的表达
- 任何关于工作状态的正面陈述
- `git commit` / `git push` / 创建 PR
- 把 plan.html 的 `<article data-status>` 切到 `"done"`
- 进入下一个任务
- 委派给 subagent（前置）+ subagent 返回后（后置复审）

**本规则适用于：**
- 准确措辞
- 同义词和换一种说法
- 暗示成功
- 任何传达完成/正确性的沟通

## 底线

**验证没有捷径。**

运行 `./gradlew check`。阅读输出。然后才能宣称结果。

这没有商量余地。
