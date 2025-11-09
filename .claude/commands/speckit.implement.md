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
   - **验证 SSOT 原则**：检查 plan.md 中的 Provenance 配置是否从 `patra-registry` 获取（CHK-SSOT-001），禁止硬编码数据源配置
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
   - **强制 TDD（项目规范）**：所有实现任务必须先编写测试，遵循测试驱动开发方法（参考 java-test-architect）
   - **基于文件的协调**：影响相同文件的任务必须顺序运行
   - **验证检查点**：在继续之前验证每个阶段的完成

7. 实现执行规则：
   - **设置优先**：初始化项目结构、依赖、配置
   - **测试驱动开发（TDD 默认启用）**：所有 Domain/Application/Infrastructure/Adapter 层代码都应先编写测试（测试是强制的，不是可选的）
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

9. **📝 Polish 阶段：文档生成**（增强）：

   **当执行 Polish 阶段的文档任务时，详细执行逻辑如下**：

   ### A. 生成 package-info.java

   **识别标志**：任务描述包含 "生成 package-info.java for"

   **执行步骤**：
   ```
   1. 从任务描述提取：
      - 包路径（如：com.patra.ingest.domain）
      - 文件位置（如：patra-ingest-domain/src/main/java/.../package-info.java）

   2. 调用 java-documentation-architect skill

   3. 收集上下文信息：
      - spec.md 的"领域模型"章节（提取该包对应层的描述）
      - 扫描该包内的所有类（使用 Glob 工具：**/{package-path}/*.java）
      - plan.md 的 Constitution Check（提取设计原则）

   4. 生成 package-info.java 内容：
      模板：java-documentation-architect/SKILL.md 的 package-info.java 模板

      填充内容：
      - 包描述：
        * Domain 层：从 spec.md 的"领域模型"提取
        * App 层："应用层用例编排包"
        * Infra 层："基础设施层技术实现包"
        * Adapter 层："适配器层外部交互包"

      - 主要组件列表：
        * 从扫描结果提取类名
        * 添加 @link 引用
        * 按类型分组（聚合根、实体、值对象）
        * 添加特性标记（CHK-DOC-005，Patra 增量更新规范）：`<!-- 特性 ###-feature-name -->` 和 `<!-- /特性 ###-feature-name -->`，特性编号与特性目录一致

      - 设计原则：
        * Domain 层：聚合根负责维护业务不变量、值对象不可变
        * App 层：事务边界、用例编排
        * Infra 层：MyBatis-Plus、MapStruct、依赖倒置
        * Adapter 层：REST API、事件监听

      - 使用示例：
        * AI 生成典型用法代码
        * 使用 @code 标记

      - @since：从 spec.md 的创建日期提取版本号

   5. 检查文件是否已存在：
      - 如果不存在 → 写入完整内容
      - 如果存在 → 增量更新（在主要组件列表中追加新类）

   6. 写入文件
   ```

   **参考**：[java-documentation-architect/SKILL.md#package-info.java模板](../../.claude/skills/java-documentation-architect/SKILL.md#package-infojava-模板)

   ---

   ### B. 更新模块 README.md

   **识别标志**：任务描述包含 "更新模块 README.md"

   **执行步骤**：
   ```
   1. 从任务描述提取：
      - 模块路径（如：patra-ingest/README.md）

   2. 检查 README.md 是否存在：
      a. 如果不存在 → 使用 /speckit.plan 生成的骨架
      b. 如果存在 → 读取现有内容

   3. 收集更新内容：
      - 核心类列表：
        * 从 tasks.md 的所有 Domain/App/Infra/Adapter 任务提取
        * 格式：类名（从文件路径提取）
      - 职责描述：
        * 从 spec.md 的"领域模型"匹配类名
        * 如果找不到，根据类名和层次推断
      - 层次：从任务的 [Layer] 标签提取

   4. 增量更新逻辑（重要！）：
      a. 定位"🎯 核心类说明"章节
      b. 在该章节末尾添加：
         ```markdown
         ### 特性 ###-feature-name
         | 类名 | 职责 | 层次 |
         |-----|------|------|
         | Article | 文章聚合根 | Domain |
         | ArticleOrchestrator | 文章业务编排 | Application |
         ...
         ```
      c. 定位"📝 变更日志"章节
      d. 在该章节顶部添加：
         ```markdown
         ### v1.x.0 (日期)
         - 新增：[从 spec.md 的概览提取]
         - 核心类：Article、ArticleOrchestrator...
         ```
      e. 版本号递增规则：
         - 读取现有最高版本号
         - 小版本号 +1（v1.0.0 → v1.1.0）

   5. 保持原有内容不变（只追加，不覆盖）

   6. 写入文件
   ```

   **参考**：[java-documentation-architect/SKILL.md#模块README模板](../../.claude/skills/java-documentation-architect/SKILL.md#模块-readme-模板)

   ---

   ### C. 生成/更新 API 文档

   **识别标志**：任务描述包含 "生成/更新 API 文档"

   **执行步骤**：
   ```
   1. 从任务描述提取：
      - 目标文件（如：specs/###-feature/contracts/API.md）

   2. 检测 API 端点来源：
      a. 如果 /speckit.plan 已生成 API.md 骨架 → 补充详细内容
      b. 如果没有骨架 → 从头生成

   3. 提取 API 信息：
      - 从 tasks.md 扫描 Controller 任务：
        * 查找包含 "[Adapter]" 和 "Controller.java" 的任务
        * 提取 Controller 类名（如：ArticleController）

      - 推断端点路径：
        * ArticleController → /api/v1/articles
        * 规则：类名去掉 Controller 后缀，转为小写复数

      - 推断 HTTP 方法：
        * 从 spec.md 的"用户场景"匹配动作：
          - 创建 → POST
          - 查询 → GET
          - 更新 → PUT
          - 删除 → DELETE

   4. 生成 API 文档条目：
      模板：java-documentation-architect/SKILL.md 的 API 文档模板

      每个端点：
      - 端点路径
      - HTTP 方法
      - 请求示例（从 spec.md 的用户场景 Given/When 提取）
      - 响应示例（从 spec.md 的用户场景 Then 提取）
      - 错误码（从 spec.md 的"成功标准"推断）

   5. 写入/更新文件
   ```

   **参考**：[java-documentation-architect/SKILL.md#API文档模板](../../.claude/skills/java-documentation-architect/SKILL.md#api-文档模板)

   ---

   ### D. 为核心类添加 JavaDoc

   **识别标志**：任务描述包含 "为聚合根添加 JavaDoc" 或 "为 Port 接口添加 JavaDoc"

   **执行步骤**：
   ```
   1. 从任务描述提取：
      - 目标类类型（聚合根 / Port 接口）
      - 文件路径

   2. 识别目标类：
      - 聚合根：从 spec.md 的"领域模型" → "聚合根"章节提取
      - Port 接口：从 tasks.md 扫描包含 "Repository.java" 的任务

   3. 对于聚合根，生成类级 JavaDoc：
      /**
       * [类名] 聚合根，负责 [职责]。
       *
       * <p>[从 spec.md 提取详细描述]</p>
       *
       * <p>状态转换：</p>
       * <pre>
       * [如果有状态枚举，生成状态机图]
       * PENDING -> CONFIRMED -> COMPLETED
       *         \-> CANCELLED
       * </pre>
       *
       * <p>线程安全性：此类不是线程安全的，需要外部同步。</p>
       *
       * @author [团队名称]
       * @since [从 spec.md 的创建日期推断版本号]
       * @see [关联的实体、值对象]
       */

   4. 对于每个公共方法，生成方法级 JavaDoc：
      /**
       * [方法描述]。
       *
       * <p>此方法会：</p>
       * <ul>
       *   <li>[业务逻辑步骤1]</li>
       *   <li>[业务逻辑步骤2]</li>
       * </ul>
       *
       * @param [参数名] [参数说明]，不能为 null
       * @return [返回值说明]
       * @throws [异常类] 如果 [触发条件]
       *
       * @example
       * <pre>{@code
       * [使用示例代码]
       * }</pre>
       */

   5. 对于 Port 接口，生成接口级 JavaDoc：
      /**
       * [接口名] 仓储接口。
       *
       * <p>负责 [职责描述]。</p>
       *
       * <p>实现位置：@see [RepositoryImpl 的包路径]</p>
       *
       * @since [版本号]
       */

   6. 将 JavaDoc 添加到现有代码中（使用 Edit 工具）
   ```

   **参考**：[java-documentation-architect/SKILL.md#JavaDoc最佳实践](../../.claude/skills/java-documentation-architect/SKILL.md#javadoc-最佳实践)

   ---

   ### E. 文档任务执行总结

   **执行完所有文档任务后，输出报告**：
   ```
   ✅ 文档生成完成

   package-info.java：
   - ✅ com.patra.ingest.domain
   - ✅ com.patra.ingest.app
   - ✅ com.patra.ingest.infra
   - ✅ com.patra.ingest.adapter

   模块 README.md：
   - ✅ patra-ingest/README.md（增量更新，添加特性 001-pubmed-datasource）

   API 文档：
   - ✅ specs/001-pubmed-datasource/contracts/API.md（3 个端点）

   JavaDoc：
   - ✅ Article.java（聚合根）
   - ✅ ArticleRepository.java（Port 接口）

   建议后续操作：
   - 运行 /speckit.document check 验证文档完整性
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
