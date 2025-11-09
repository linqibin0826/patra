---
description: 使用规划模板执行实现规划工作流，生成设计工件。
---

## 用户输入

```text
$ARGUMENTS
```

你**必须**在继续之前考虑用户输入（如果不为空）。

## 概述

1. **设置**：从仓库根目录运行 `.specify/scripts/bash/setup-plan.sh --json` 并解析 JSON 以获取 FEATURE_SPEC、IMPL_PLAN、SPECS_DIR、BRANCH。对于包含单引号的参数，如 "I'm Groot"，使用转义语法：例如 'I'\''m Groot'（或者如果可能使用双引号："I'm Groot"）。

2. **加载上下文**：读取 FEATURE_SPEC 和 `.specify/memory/constitution.md`。加载 IMPL_PLAN 模板（已复制）。

3. **🔧 架构设计集成**（新增）：
   - **调用 java-hexagonal-architecture Skill**：用于架构决策和设计指导
   - **参考 Constitution CHK-* 验证项**：确保设计符合架构规范
   - **使用 Skills 中的代码模式**：聚合设计、Port-Adapter 模式

4. **执行规划工作流**：遵循 IMPL_PLAN 模板中的结构：
   - 填写技术上下文（将未知项标记为 "需要澄清"）
   - 从宪章填写宪章检查部分
   - 评估关卡（如果违规未经证明则报错）
   - 阶段 0：生成 research.md（解决所有需要澄清的问题）
   - 阶段 1：生成 data-model.md、contracts/、quickstart.md
   - 设计后重新评估宪章检查

5. **停止并报告**：命令在阶段 1 设计后结束。报告分支、IMPL_PLAN 路径和生成的工件。

## 🎯 Skills 集成指令

### Phase 0 之前：架构设计决策

**使用 java-hexagonal-architecture Skill 进行架构分析**：

1. **确定适配器类型**（参考 [架构决策流程](../../.claude/skills/java-hexagonal-architecture/SKILL.md#架构决策流程)）：
   ```
   - REST API → Controller
   - 定时任务 → XXL-Job
   - 消息队列 → MessageListener
   ```

2. **设计聚合边界**（参考 [领域建模检查](../../.claude/skills/java-hexagonal-architecture/SKILL.md#领域建模检查)）：
   - 识别聚合根（Aggregate Root）
   - 确定聚合内实体
   - 定义业务不变量

3. **定义 Port 接口**（参考 [Port-Adapter 模式](../../.claude/skills/java-hexagonal-architecture/SKILL.md#常见架构模式)）：
   - 领域层定义 Port 接口
   - 基础设施层实现 Adapter

### Phase 1：技术实施参考

**引用 java-spring-development Skill 获取技术栈最佳实践**：

1. **MyBatis-Plus 配置**（参考 [MyBatis-Plus 数据访问](../../.claude/skills/java-spring-development/SKILL.md#mybatis-plus-数据访问)）
2. **MapStruct 转换器**（参考 [MapStruct 对象转换](../../.claude/skills/java-spring-development/SKILL.md#mapstruct-对象转换)）
3. **事务管理策略**（参考 [事务管理最佳实践](../../.claude/skills/java-spring-development/SKILL.md#事务管理最佳实践)）

## 阶段

### 阶段 0：大纲与研究

1. **从上述技术上下文中提取未知项**：
   - 每个需要澄清 → 研究任务
   - 每个依赖项 → 最佳实践任务
   - 每个集成 → 模式任务

2. **🔍 架构设计研究**（新增）：
   - 如果涉及复杂的聚合设计，调用 java-hexagonal-architecture 分析
   - 如果涉及事件驱动架构，参考 [event-driven-architecture.md](../../.claude/skills/java-hexagonal-architecture/resources/event-driven-architecture.md)
   - 如果涉及 Outbox 模式，参考 [outbox-pattern.md](../../.claude/skills/java-hexagonal-architecture/resources/outbox-pattern.md)

3. **生成并派遣研究代理**：

   ```text
   对于技术上下文中的每个未知项：
     任务："为 {特性上下文} 研究 {未知项}"
   对于每个技术选择：
     任务："在 {领域} 中查找 {技术} 的最佳实践"
   ```

4. **在 `research.md` 中整合发现**，使用格式：
   - 决策：[选择了什么]
   - 理由：[为什么选择]
   - 考虑的替代方案：[还评估了什么]

**输出**：research.md，其中所有需要澄清的问题已解决

### 阶段 1：设计与契约

**前置条件**：`research.md` 完成

1. **从特性规格中提取实体** → `data-model.md`：
   - 实体名称、字段、关系
   - 来自需求的验证规则
   - 状态转换（如适用）
   - **🔧 参考 DDD 设计模式**：[domain-modeling-patterns.md](../../.claude/skills/java-hexagonal-architecture/resources/domain-modeling-patterns.md)

2. **从功能需求生成 API 契约**：
   - 对于每个用户操作 → 端点
   - 使用标准 REST/GraphQL 模式
   - 输出 OpenAPI/GraphQL schema 到 `/contracts/`
   - **🔧 参考 Controller 开发模式**：[adapter-layer-patterns.md](../../.claude/skills/java-spring-development/resources/adapter-layer-patterns.md)

3. **项目结构设计**：
   - 基于六边形架构 6 层模块结构
   - 参考 [模块结构规范](../../.claude/skills/java-hexagonal-architecture/SKILL.md#模块结构规范)

**输出**：data-model.md、/contracts/*、quickstart.md

### Phase 2：重新验证 Constitution Check

**在设计完成后，重新评估所有 CHK-* 验证项**：

1. 检查 data-model.md 中的聚合设计是否符合 CHK-DDD-001/002
2. 检查 contracts/ 中的 API 设计是否符合 REST 规范
3. 确认依赖方向符合 CHK-ARCH-002/003
4. 如有违规，在 plan.md 的 "Complexity Tracking" 章节记录

## 关键规则

- 使用绝对路径
- 对于关卡失败或未解决的澄清问题报错
- **优先参考 Skills**：遇到架构设计问题时，先查看 java-hexagonal-architecture
- **引用 CHK-* 编号**：在 Constitution Check 中使用具体的验证项编号
