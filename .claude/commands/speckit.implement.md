---
description: 通过处理和执行 tasks.md 中定义的所有任务来执行实现计划
important: 对于已完成的任务，确保在任务文件中将任务标记为 [X]。
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
     | architecture.md  | 12   | 12     | 0      | ✓ 通过 |
     | domain-model.md  | 8    | 5      | 3      | ✗ 失败 |
     | api.md           | 6    | 6      | 0      | ✓ 通过 |
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

   **步骤 A：加载设计文档**
   - **必需**：读取 tasks.md 以获取完整的任务列表和执行计划
   - **必需**：读取 plan.md 以获取技术栈、架构和文件结构
   - **必需**：读取 `.specify/memory/constitution.md` 以获取架构规范
   - **验证 SSOT 原则**：检查 plan.md 中的 Provenance 配置是否从 `patra-registry` 获取（CHK-SSOT-001），禁止硬编码数据源配置
   - **如果存在**：读取 data-model.md 以获取实体和关系
   - **如果存在**：读取 contracts/ 以获取 API 规格说明和测试要求
   - **如果存在**：读取 research.md 以获取技术决策和约束
   - **如果存在**：读取 quickstart.md 以获取集成场景

   **步骤 B：查看项目中已有的相关代码**（实施前必须执行）

   **目标**：理解项目现有实现模式，确保新代码符合项目规范

   - **参考 tasks.md 中的代码引用**：
     - 任务描述中如果包含 `（参考 [文件路径]:[行号]）`，先阅读这些参考文件
     - 理解现有实现的设计模式和代码风格

   - **识别可复用的代码模式**：
     - **Domain 层**：聚合根、值对象、领域事件的实现方式
     - **Application 层**：Orchestrator、Coordinator 的编排模式
     - **Infrastructure 层**：Repository、Mapper、Converter 的实现方式
     - **Adapter 层**：Controller、Job、MessageListener 的实现方式
     - **测试层**：单元测试、集成测试、E2E 测试的组织方式

   - **记录实施注意事项**：
     - 需要保持一致的命名规范
     - 需要遵循的代码组织方式
     - 需要复用的工具类或基础类

4. **初始化实施变更日志**（🆕 仅在需要时）：

   **检查 implementation-log.md**：
   - 路径：`FEATURE_DIR/implementation-log.md`
   - **仅在首次发现变更时创建**，不需要在实施开始时就创建
   - 如果文件不存在且发现变更，使用以下模板创建：

   ```markdown
   # Implementation Log - {特性名称}

   > 📋 本文档**仅记录**实施过程中与原始规划的偏差和技术决策变更
   >
   > ⚠️ **重要**：正常按计划实施的任务不需要记录在此文档中

   ## 📊 元数据

   | 项目 | 值 |
   |------|-----|
   | 特性 ID | {从目录名提取} |
   | 基线文档 | spec.md, plan.md, tasks.md |
   | 创建时间 | {首次记录变更的时间} |
   | 变更总数 | {当前变更数量} |

   ## 📝 变更记录

   > 按时间倒序记录（最新的在最上面）
   > **只记录变更，不记录正常实施**

   ---

   {变更记录将在这里追加}

   ---
   ```

   **如果文件已存在**：
   - 读取现有内容
   - 继续追加新的变更记录

5. **项目设置验证**（Patra 后端项目）：
   - **必需**：基于实际项目设置创建/验证忽略文件

   **检测与创建逻辑**：
   - 检查是否为 git 仓库（如果是则创建/验证 .gitignore）：

     ```sh
     git rev-parse --git-dir 2>/dev/null
     ```

   **如果忽略文件已存在**：验证它包含基本模式，仅追加缺失的关键模式
   **如果忽略文件缺失**：为检测到的技术创建完整的模式集

   **Java 项目必需模式**：
   - **Maven 构建产物**：`target/`、`*.class`、`*.jar`、`*.war`
   - **IDE 配置**：`.idea/`、`*.iml`、`.vscode/`、`.settings/`、`.project`、`.classpath`
   - **日志文件**：`*.log`、`logs/`
   - **操作系统**：`.DS_Store`、`Thumbs.db`
   - **临时文件**：`*.tmp`、`*.swp`、`*~`

6. 解析 tasks.md 结构并提取：
   - **任务阶段**：设置、测试、核心、集成、润色
   - **任务依赖**：顺序执行 vs 并行执行规则
   - **任务详情**：ID、描述、文件路径、并行标记 [P]、层标签 [Domain] [App] [Infra] [Adapter]
   - **执行流程**：顺序和依赖要求

7. 遵循任务计划执行实现：
   - **阶段性执行**：在进入下一阶段前完成每个阶段
   - **遵守依赖**：按顺序运行顺序任务，并行任务 [P] 可以一起运行
   - **强制 TDD（项目规范）**：所有实现任务必须先编写测试，遵循测试驱动开发方法（参考 patra-tdd-development）
   - **基于文件的协调**：影响相同文件的任务必须顺序运行
   - **验证检查点**：在继续之前验证每个阶段的完成

8. 实现执行规则（六边形架构 + TDD）：
   - **阶段 1 - 设置优先**：初始化项目结构、Maven 依赖、配置文件（application.yml、bootstrap.yml）
   - **阶段 2 - 基础层开发**：共享组件、基础工具类
   - **阶段 3+ - 用户故事开发**（每个故事遵循六边形架构层次）：
     1. **测试驱动开发（TDD 强制）**：所有层代码都必须先编写测试
     2. **Domain 层**：聚合根、值对象、领域事件、Port 接口（纯 Java）
     3. **Application 层**：Orchestrator、Coordinator（业务流程编排）
     4. **Infrastructure 层**：Repository 实现、MyBatis Mapper、外部服务适配器
     5. **Adapter 层**：Controller（REST API）、MessageListener、XXL-Job
     6. **集成测试**：IT 测试、E2E 测试（在 boot 模块）
   - **最后阶段 - 润色**：性能优化、文档生成

9. **🔍 阶段性代码审查**：

   在每个阶段完成后，可以人工审查代码质量和架构合规性

10. **进度跟踪、变更记录和错误处理**：

   ### A. 任务进度追踪
   - 在每个完成的任务后报告进度
   - **重要**：对于已完成的任务，确保在 tasks.md 中将任务标记为 [X]
   - **正常按计划实施的任务不需要记录到 implementation-log.md**

   ### B. 🚨 变更检测与记录（强制流程）

   **触发条件**（满足任一条件即需记录变更）：
   1. **技术方案偏差**：实际实施与 plan.md 中的技术选型不一致
   2. **任务调整**：需要修改 tasks.md 中的任务描述、依赖或新增/删除任务
   3. **数据模型变更**：实体、字段、关系与 data-model.md 不符
   4. **API 契约变更**：端点、请求/响应格式与 contracts/ 不符
   5. **架构决策变更**：违反或调整 spec.md 中的设计原则
   6. **依赖或配置变更**：新增/修改 pom.xml、application.yml 等关键配置

   **变更记录流程**（4 步强制执行）：
   - 步骤 1：检测变更 → 对比实施与原始文档，发现偏差立即暂停
   - 步骤 2：记录变更到 implementation-log.md → 使用标准模板记录详细信息
   - 步骤 3：同步更新受影响的原始文档 → 逐一更新并添加引用标记
   - 步骤 4：继续实施 → 基于变更后的方案继续执行

   **变更记录模板和示例**：参考 `.specify/templates/implementation-log-template.md`

   ### C. 错误处理
   - 如果任何非并行任务失败则停止执行
   - 对于并行任务 [P]，继续成功的任务，报告失败的任务
   - 提供带上下文的清晰错误消息以进行调试
   - 如果实现无法继续则建议下一步
   - **记录错误到 implementation-log.md**（使用任务失败记录模板）

11. 完成验证：
   - 验证所有必需任务已完成
   - 检查实现的特性是否符合原始规格说明
   - 验证测试通过且覆盖率满足要求（参考 CHK-TEST-* 验证项）
   - 确认实现遵循技术计划
   - 报告最终状态及已完成工作的摘要

## 🎯 Skills 集成指令（实施阶段）

### 阶段执行时的 Skills 参考

**根据任务类型，参考对应的 Skills 获取代码模板和最佳实践**：

#### 1. Domain 层任务
- **聚合根和实体**：参考 java-spring-development Skill 中的聚合设计模式
- **Port 接口定义**：参考 java-spring-development Skill 中的 Port-Adapter 模式
- **领域事件**：参考 [event-driven-architecture.md](../../.claude/skills/java-spring-development/resources/event-driven-architecture.md)

#### 2. Application 层任务
- **Orchestrator/Coordinator**：参考 [Orchestrator 编排模式](../../.claude/skills/java-spring-development/SKILL.md#orchestrator-编排模式)
- **事务管理**：参考 [事务管理最佳实践](../../.claude/skills/java-spring-development/SKILL.md#事务管理最佳实践)

#### 3. Infrastructure 层任务
- **Repository 实现**：参考 [MyBatis-Plus 数据访问](../../.claude/skills/java-spring-development/SKILL.md#mybatis-plus-数据访问)
- **DO 和 Converter**：参考 [MapStruct 对象转换](../../.claude/skills/java-spring-development/SKILL.md#mapstruct-对象转换)

#### 4. Adapter 层任务
- **Controller**：参考 [Controller 开发模式](../../.claude/skills/java-spring-development/SKILL.md#controller-开发模式)
- **XXL-Job**：参考 [XXL-Job 定时任务](../../.claude/skills/java-spring-development/SKILL.md#xxl-job-定时任务)
- **错误处理**：参考 [错误处理模式](../../.claude/skills/java-spring-development/SKILL.md#错误处理模式)

#### 5. 所有层开发（TDD 驱动）

**重要**：遵循 TDD 工作流（Red-Green-Refactor），测试先行驱动设计

- **TDD 工作流**：参考 [patra-tdd-development Skill](../../.claude/skills/patra-tdd-development/SKILL.md)
  - Red：先写失败的测试
  - Green：编写最少代码通过测试
  - Refactor：重构代码和测试

- **六边形架构各层的 TDD 实践**：
  - Domain 层：无框架依赖，纯业务逻辑测试
  - Application 层：Mock Port 接口，测试编排逻辑
  - Infrastructure 层：使用 TestContainers，测试数据访问
  - Adapter 层：使用 MockMvc，测试 HTTP 接口

**关键规范（测试位置）**：
- ⚠️ **E2E 测试（*E2E.java）必须在 patra-{service}-boot 模块**
- ⚠️ **单元测试（*Test.java）与被测试类在同一模块**

## 关键规则

### 🚨 强制性规则（不可违反）

1. **使用绝对路径**：所有文件操作必须使用绝对路径，避免相对路径错误

2. **测试模块位置**：IT 和 E2E 测试必须在 boot 模块（CHK-TEST-006）
   - `*IT.java` → `patra-{service}-boot/src/test/java/`
   - `*E2E.java` → `patra-{service}-boot/src/test/java/`

3. **强制记录实施变更**（🆕 最重要）：
   - ✅ **必须**在 `specs/{特性}/implementation-log.md` **仅记录**与原始计划的偏差
   - ✅ **必须**在首次发现变更时创建 implementation-log.md（如不存在）
   - ✅ **必须**在发现变更时立即暂停，先记录再继续
   - ✅ **必须**同步更新受影响的原始文档（tasks.md、plan.md 等）
   - ✅ **必须**在变更文档中添加引用标记：`<!-- 实施变更：见 implementation-log.md#变更-XXX -->`
   - ❌ **禁止**不记录变更就继续实施
   - ❌ **禁止**在实施完成后才补记录（必须实时记录）
   - ❌ **禁止**记录正常按计划实施的任务（只记录变更和偏差）

4. **implementation-log.md 是实施过程的 SSOT**：
   - 所有技术决策变更必须有据可查
   - 所有任务调整必须记录原因和影响
   - 所有错误和阻塞必须记录解决方案

### 📋 推荐规则

5. **参考 Skills 获取代码模板**：不要凭空编写，先查看 Skills 中的示例
   - 架构模式与 Spring Boot 技术模式 → java-spring-development
   - TDD 工作流 → patra-tdd-development

6. **阶段性审查**：每个层完成后进行人工审查
   - Domain 层完成 → 审查 CHK-ARCH-001, CHK-CODE-003
   - Application 层完成 → 审查 CHK-ARCH-003, CHK-CODE-002
   - Infrastructure 层完成 → 审查 CHK-ARCH-004, CHK-CODE-005
   - Adapter 层完成 → 审查 CHK-ARCH-002, 错误处理

7. **最后生成文档**：所有代码完成后生成必要文档
   - 模块 README.md
   - API 文档
   - JavaDoc

### 📊 变更记录检查清单

**仅在存在变更或偏差时**验证以下内容：

- [ ] implementation-log.md 已创建（如有变更发生）
- [ ] 所有变更都已记录（技术方案、任务、数据模型、API、配置）
- [ ] 每个变更都包含：时间、类型、任务 ID、原因、影响范围、决策依据
- [ ] 受影响的原始文档已同步更新
- [ ] 变更引用标记已添加到原始文档：`<!-- 实施变更：见 implementation-log.md#变更-XXX -->`
- [ ] 如有错误或阻塞，已记录详细的错误信息和解决方案

**如果所有任务都按计划实施，无需创建 implementation-log.md**

---

**注意**：此命令假设 tasks.md 中存在完整的任务分解。如果任务不完整或缺失，建议首先运行 `/speckit.tasks` 以重新生成任务列表。
