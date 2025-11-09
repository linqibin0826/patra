---
description: 通过处理和执行 tasks.md 中定义的所有任务来执行实现计划
---

## 用户输入

```text
$ARGUMENTS
```

你**必须**在继续之前考虑用户输入（如果不为空）。

## 概述

1. 从仓库根目录运行 `.specify/scripts/bash/check-prerequisites.sh --json --require-tasks --include-tasks` 并解析 FEATURE_DIR 和 AVAILABLE_DOCS 列表。所有路径必须是绝对路径。对于包含单引号的参数，如 "I'm Groot"，使用转义语法：例如 'I'\''m Groot'（或者如果可能使用双引号："I'm Groot"）。

2. **检查检查清单状态**（如果 FEATURE_DIR/checklists/ 存在）：
   - 扫描 checklists/ 目录中的所有检查清单文件
   - 对于每个检查清单，计数：
     - 总项目：所有匹配 `- [ ]` 或 `- [X]` 或 `- [x]` 的行
     - 已完成项目：匹配 `- [X]` 或 `- [x]` 的行
     - 未完成项目：匹配 `- [ ]` 的行
   - 创建状态表：

     ```text
     | 检查清单 | 总计 | 已完成 | 未完成 | 状态 |
     |---------|------|--------|--------|------|
     | ux.md     | 12   | 12     | 0      | ✓ 通过 |
     | test.md   | 8    | 5      | 3      | ✗ 失败 |
     | security.md | 6  | 6      | 0      | ✓ 通过 |
     ```

   - 计算整体状态：
     - **通过**：所有检查清单都有 0 个未完成项目
     - **失败**：一个或多个检查清单有未完成项目

   - **如果任何检查清单未完成**：
     - 显示包含未完成项目数的表
     - **停止**并询问："某些检查清单未完成。你仍想继续实现吗？（是/否）"
     - 等待用户响应后再继续
     - 如果用户说 "否" 或 "等待" 或 "停止"，停止执行
     - 如果用户说 "是" 或 "继续" 或 "进行"，进入步骤 3

   - **如果所有检查清单完成**：
     - 显示显示所有检查清单通过的表
     - 自动进入步骤 3

3. 加载并分析实现上下文：
   - **必需**：读取 tasks.md 以获取完整的任务列表和执行计划
   - **必需**：读取 plan.md 以获取技术栈、架构和文件结构
   - **必需**：读取 `.specify/memory/constitution.md` 以获取架构规范
   - **如果存在**：读取 data-model.md 以获取实体和关系
   - **如果存在**：读取 contracts/ 以获取 API 规格说明和测试要求
   - **如果存在**：读取 research.md 以获取技术决策和约束
   - **如果存在**：读取 quickstart.md 以获取集成场景

4. **项目设置验证**：
   - **必需**：基于实际项目设置创建/验证忽略文件：

   **检测与创建逻辑**：
   - 检查以下命令是否成功以确定仓库是否为 git 仓库（如果是则创建/验证 .gitignore）：

     ```sh
     git rev-parse --git-dir 2>/dev/null
     ```

   - 检查 Dockerfile* 是否存在或 plan.md 中有 Docker → 创建/验证 .dockerignore
   - 检查 .eslintrc* 或 eslint.config.* 是否存在 → 创建/验证 .eslintignore
   - 检查 .prettierrc* 是否存在 → 创建/验证 .prettierignore
   - 检查 .npmrc 或 package.json 是否存在 → 创建/验证 .npmignore（如果发布）
   - 检查 terraform 文件 (*.tf) 是否存在 → 创建/验证 .terraformignore
   - 检查是否需要 .helmignore（存在 helm charts）→ 创建/验证 .helmignore

   **如果忽略文件已存在**：验证它包含基本模式，仅追加缺失的关键模式
   **如果忽略文件缺失**：为检测到的技术创建完整的模式集

   **按技术划分的常见模式**（来自 plan.md 技术栈）：
   - **Java**：`target/`、`*.class`、`*.jar`、`.gradle/`、`build/`
   - **通用**：`.DS_Store`、`Thumbs.db`、`*.tmp`、`*.swp`、`.vscode/`、`.idea/`

5. 解析 tasks.md 结构并提取：
   - **任务阶段**：设置、测试、核心、集成、润色
   - **任务依赖**：顺序执行 vs 并行执行规则
   - **任务详情**：ID、描述、文件路径、并行标记 [P]、层标签 [Domain] [App] [Infra] [Adapter]
   - **执行流程**：顺序和依赖要求

## 🎯 Skills 集成指令（实施阶段）

### 阶段执行时的 Skills 参考

**根据任务类型，参考对应的 Skills 获取代码模板和最佳实践**：

#### 1. Domain 层任务 → java-hexagonal-architecture
- **聚合根和实体**：参考 [聚合设计模式](../../.claude/skills/java-hexagonal-architecture/SKILL.md#聚合设计模式)
- **Port 接口定义**：参考 [Port-Adapter 模式](../../.claude/skills/java-hexagonal-architecture/SKILL.md#常见架构模式)
- **领域事件**：参考 [event-driven-architecture.md](../../.claude/skills/java-hexagonal-architecture/resources/event-driven-architecture.md)

#### 2. Application 层任务 → java-spring-development
- **Orchestrator/Coordinator**：参考 [Orchestrator 编排模式](../../.claude/skills/java-spring-development/SKILL.md#orchestrator-编排模式)
- **事务管理**：参考 [事务管理最佳实践](../../.claude/skills/java-spring-development/SKILL.md#事务管理最佳实践)

#### 3. Infrastructure 层任务 → java-spring-development
- **Repository 实现**：参考 [MyBatis-Plus 数据访问](../../.claude/skills/java-spring-development/SKILL.md#mybatis-plus-数据访问)
- **DO 和 Converter**：参考 [MapStruct 对象转换](../../.claude/skills/java-spring-development/SKILL.md#mapstruct-对象转换)

#### 4. Adapter 层任务 → java-spring-development
- **Controller**：参考 [Controller 开发模式](../../.claude/skills/java-spring-development/SKILL.md#controller-开发模式)
- **XXL-Job**：参考 [XXL-Job 定时任务](../../.claude/skills/java-spring-development/SKILL.md#xxl-job-定时任务)
- **错误处理**：参考 [错误处理模式](../../.claude/skills/java-spring-development/SKILL.md#错误处理模式)

#### 5. 测试任务 → java-test-architect

**重要**：根据测试类型和层次，使用对应的测试模板

- **Domain 层单元测试**：参考 [Domain 层单元测试](../../.claude/skills/java-test-architect/SKILL.md#domain-层单元测试)
  - 无 Spring 依赖
  - 使用 JUnit + AssertJ

- **Application 层单元测试**：参考 [Application 层单元测试](../../.claude/skills/java-test-architect/SKILL.md#application-层单元测试)
  - 使用 @ExtendWith(MockitoExtension.class)
  - Mock Port 接口

- **Infrastructure 层集成测试**：参考 [Repository 集成测试](../../.claude/skills/java-test-architect/SKILL.md#repository-集成测试testcontainers)
  - 使用 TestContainers
  - 必须在 `patra-{service}-boot` 模块

- **Adapter 层集成测试**：参考 [Controller 集成测试](../../.claude/skills/java-test-architect/SKILL.md#controller-集成测试mockmvc)
  - 使用 MockMvc
  - 必须在 `patra-{service}-boot` 模块

- **架构测试**：参考 [架构测试（ArchUnit）](../../.claude/skills/java-test-architect/SKILL.md#架构测试archunit)

**关键规范（来自 Constitution CHK-TEST-006）**：
- ⚠️ **IT 测试（*IT.java）必须在 patra-{service}-boot 模块**
- ⚠️ **E2E 测试（*E2E.java）必须在 patra-{service}-boot 模块**

6. 遵循任务计划执行实现：
   - **阶段性执行**：在进入下一阶段前完成每个阶段
   - **遵守依赖**：按顺序运行顺序任务，并行任务 [P] 可以一起运行
   - **遵循 TDD 方法**：在相应实现任务之前执行测试任务
   - **基于文件的协调**：影响相同文件的任务必须顺序运行
   - **验证检查点**：在继续之前验证每个阶段的完成

7. 实现执行规则：
   - **设置优先**：初始化项目结构、依赖、配置
   - **测试先于代码**：如果需要为契约、实体和集成场景编写测试
   - **核心开发**：实现模型、服务、CLI 命令、端点
   - **集成工作**：数据库连接、中间件、日志记录、外部服务
   - **润色和验证**：单元测试、性能优化、文档

8. **🔍 阶段性代码审查**（新增）：

   **在每个阶段完成后，调用 java-code-reviewer 进行审查**：

   - **Domain 层完成后**：
     ```
     调用 java-code-reviewer 审查：
     - CHK-ARCH-001: Domain 层是否纯 Java？
     - CHK-CODE-003: 是否避免了贫血模型？
     - 参考：[架构合规性检查](../../.claude/skills/java-code-reviewer/SKILL.md#架构合规性检查)
     ```

   - **Application 层完成后**：
     ```
     调用 java-code-reviewer 审查：
     - CHK-ARCH-003: 事务边界是否在 Orchestrator？
     - CHK-CODE-002: 方法复杂度是否合理？
     - 参考：[业务逻辑检查](../../.claude/skills/java-code-reviewer/SKILL.md#业务逻辑检查)
     ```

   - **Infrastructure 层完成后**：
     ```
     调用 java-code-reviewer 审查：
     - CHK-ARCH-004: DO 是否被正确封装？
     - CHK-CODE-005: 是否避免了 N+1 查询？
     - 参考：[性能相关审查](../../.claude/skills/java-code-reviewer/SKILL.md#性能相关审查)
     ```

   - **Adapter 层完成后**：
     ```
     调用 java-code-reviewer 审查：
     - CHK-ARCH-002: 依赖方向是否正确？
     - 错误处理是否适当？
     - 参考：[层次依赖检查](../../.claude/skills/java-code-reviewer/SKILL.md#层次依赖检查)
     ```

9. **📝 最后阶段：文档生成**（新增）：

   **在所有代码实施完成后，调用 java-documentation-architect 生成文档**：

   - **模块 README.md**：
     ```
     为每个新模块生成 README.md
     参考：[模块 README 模板](../../.claude/skills/java-documentation-architect/SKILL.md#模块-readme-模板)
     包含：
     - 概述
     - 架构位置
     - 主要功能
     - 核心类说明
     - 快速开始
     ```

   - **package-info.java**：
     ```
     为每个包生成 package-info.java
     参考：[package-info.java 模板](../../.claude/skills/java-documentation-architect/SKILL.md#package-infojava-模板)
     包含：
     - 包职责描述
     - 主要组件列表
     - 设计原则
     - 使用示例
     ```

   - **JavaDoc**：
     ```
     为公共 API 添加 JavaDoc
     参考：[JavaDoc 最佳实践](../../.claude/skills/java-documentation-architect/SKILL.md#javadoc-最佳实践)
     ```

10. 进度跟踪和错误处理：
   - 在每个完成的任务后报告进度
   - 如果任何非并行任务失败则停止执行
   - 对于并行任务 [P]，继续成功的任务，报告失败的任务
   - 提供带上下文的清晰错误消息以进行调试
   - 如果实现无法继续则建议下一步
   - **重要**：对于已完成的任务，确保在任务文件中将任务标记为 [X]。

11. 完成验证：
   - 验证所有必需任务已完成
   - 检查实现的特性是否符合原始规格说明
   - 验证测试通过且覆盖率满足要求（参考 CHK-TEST-* 验证项）
   - 确认实现遵循技术计划
   - **运行最终代码审查**：调用 java-code-reviewer 生成完整审查报告
   - 报告最终状态及已完成工作的摘要

## 关键规则

- 使用绝对路径
- **测试模块位置**：IT 和 E2E 测试必须在 boot 模块（CHK-TEST-006）
- **参考 Skills 获取代码模板**：不要凭空编写，先查看 Skills 中的示例
- **阶段性审查**：每个层完成后调用 java-code-reviewer
- **最后生成文档**：所有代码完成后调用 java-documentation-architect

注意：此命令假设 tasks.md 中存在完整的任务分解。如果任务不完整或缺失，建议首先运行 `/speckit.tasks` 以重新生成任务列表。
