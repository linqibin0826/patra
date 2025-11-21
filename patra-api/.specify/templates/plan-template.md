# 实施计划：[FEATURE]

**分支**: `[###-feature-name]` | **日期**: [DATE] | **规格说明**: [link]
**输入**: 来自 `/specs/[###-feature-name]/spec.md` 的功能规格说明

**说明**: 本模板由 `/speckit.plan` 命令填充。执行工作流请参见 `.specify/templates/commands/plan.md`。

## 摘要

[从功能规格说明中提取：主要需求 + 研究得出的技术方案]

## 技术上下文

**语言/版本**: Java 25
**主要框架**: Spring Boot 3.5.7, Spring Cloud 2025.0.0
**持久化**: MyBatis-Plus 3.5.12
**对象映射**: MapStruct
**服务发现**: Nacos
**测试**: JUnit 5 + Mockito（单元测试），TestContainers（IT 集成测试）
**目标平台**: Linux 服务器 / Docker 容器
**架构模式**: 六边形架构（Hexagonal Architecture）+ DDD

### 技术栈说明

- **依赖管理**: Maven（继承 `patra-parent`）
- **配置管理**: Nacos Config
- **日志**: SLF4J + Logback
- **API 文档**: Swagger/OpenAPI 3.0

### 可重用组件

- **Starters**: `patra-spring-boot-starter-*`（根据需要选择）
- **Common**: `patra-common-core`（所有模块公用）、`patra-common-model`（通用模型）
- **Registry**: `patra-registry-api`（获取字典）

## 宪章检查

*门禁规则：Phase 0 调研前必须通过，Phase 1 设计后重新检查*

基于 `.specify/memory/constitution.md` 的验证项：

### 架构验证

- [ ] **CHK-ARCH-001**: 模块结构符合 6 层规范（boot/api/domain/app/infra/adapter）
  - **状态**: [PASS / FAIL / N/A]
  - **说明**: [如果 FAIL，说明偏差和理由]

- [ ] **CHK-ARCH-002**: Domain 层 `pom.xml` 无任何框架依赖（仅 JDK + patra-common-core + Lombok + Hutool + Jackson）
  - **状态**: [PASS / FAIL / N/A]
  - **说明**: [列出 domain 层的依赖清单]

- [ ] **CHK-ARCH-003**: 依赖方向符合 `Adapter → App → Domain ← Infra`
  - **状态**: [PASS / FAIL / N/A]
  - **验证方法**: 将在 Phase 2 实施时通过代码审查或 ArchUnit 测试验证

### DDD 验证

- [ ] **CHK-DDD-001**: 识别出明确的聚合根（Aggregate Root）
  - **状态**: [PASS / FAIL / N/A]
  - **聚合根列表**: [列出识别的聚合根，如：`PublicationAggregate`]

- [ ] **CHK-DDD-002**: 聚合边界清晰（不跨聚合直接访问实体）
  - **状态**: [PASS / FAIL / N/A]
  - **说明**: [描述聚合边界]

- [ ] **CHK-DDD-003**: 值对象设计为不可变（immutable）
  - **状态**: [PASS / FAIL / N/A]
  - **值对象列表**: [列出值对象，如：`PublicationId`、`Email`]

- [ ] **CHK-DDD-004**: 领域事件使用过去时命名
  - **状态**: [PASS / FAIL / N/A]
  - **事件列表**: [列出领域事件，如：`PublicationCreated`、`PublicationPublished`]

### 测试验证（将在 Phase 2 实施）

- [ ] **CHK-TEST-001**: Domain 层单元测试覆盖率 ≥ 80%
  - **状态**: [计划在 Phase 2 实施]
  - **测试位置**: `patra-{service}-domain/src/test/java/`

- [ ] **CHK-TEST-002**: Application 层单元测试覆盖率 ≥ 70%
  - **状态**: [计划在 Phase 2 实施]
  - **测试位置**: `patra-{service}-app/src/test/java/`

- [ ] **CHK-TEST-003**: Infrastructure 层有单元测试和集成测试(轻量, MybatisTest等)
  - **状态**: [计划在 Phase 2 实施]
  - **测试位置**: `patra-{service}-infra/src/test/java/`
  - **集成测试类型**: Repository (@MybatisPlusTest), Feign Client (WireMock), MQ Publisher (TestContainers)

- [ ] **CHK-TEST-004**: Adapter 层有单元测试和切片测试
  - **状态**: [计划在 Phase 2 实施]
  - **测试位置**: `patra-{service}-adapter/src/test/java/`
  - **切片测试类型**: Controller (@WebMvcTest), Listener (单元测试), Job (单元测试)

- [ ] **CHK-TEST-005**: Boot 层有 E2E 端到端测试
  - **状态**: [计划在 Phase 2 实施]
  - **测试位置**: `patra-{service}-boot/src/test/java/`
  - **测试类型**: 完整业务流程 E2E 测试 (@SpringBootTest + TestContainers)

### 文档验证

- [ ] **CHK-DOC-001**: 模块根目录有完整的 `README.md`
  - **状态**: [计划在 Phase 2 实施]

- [ ] **CHK-DOC-002**: 所有文档和注释使用中文
  - **状态**: [PASS]

- [ ] **CHK-DOC-003**: API 有 Swagger 文档
  - **状态**: [计划在 Phase 2 实施]

---

**Phase 0 门禁结果**: [通过 / 失败]

**如果失败**: 必须在复杂度跟踪章节说明违规理由和替代方案。

---

## Skills 参考指南

**说明**: 以下 Skills 提供详细的实施规范和代码模板，在 Phase 1 设计和 Phase 2 实施时参考。

### 架构设计阶段（Phase 1）

**参考 Skill**: `java-hexagonal-architecture`

**关键决策点**：
- [ ] **触发来源** → 确定适配器类型（Controller / Job / MessageListener）
  - 参考: [java-hexagonal-architecture/SKILL.md#架构决策流程](../../.claude/skills/java-hexagonal-architecture/SKILL.md#架构决策流程)
- [ ] **聚合设计** → 确定聚合根和聚合边界
  - 参考: [java-hexagonal-architecture/SKILL.md#聚合设计模式](../../.claude/skills/java-hexagonal-architecture/SKILL.md#常见架构模式)
- [ ] **Port 接口定义** → 设计 Repository 和其他端口接口
  - 参考: [java-hexagonal-architecture/SKILL.md#Port-Adapter模式](../../.claude/skills/java-hexagonal-architecture/SKILL.md#常见架构模式)

### TDD 驱动的后端开发阶段（Phase 2 - 由 /speckit.implement 调用）

**执行 Agent**: `patra-backend-developer`（加载 `patra-tdd-development` + `java-spring-development` Skills）

**核心原则**：测试驱动设计，遵循 Red-Green-Refactor 循环
- 🔴 **Red**：先写失败的测试（定义期望行为）
- 🟢 **Green**：编写最少代码通过测试
- 🔵 **Refactor**：重构代码和测试，保持测试通过

**六边形架构各层的 TDD 实践**：
- **Domain 层**：纯单元测试，验证业务规则和状态转换
  - 参考: [patra-tdd-development/SKILL.md#Domain层TDD](../../.claude/skills/patra-tdd-development/SKILL.md)
  - 测试框架：JUnit 5 + Mockito + AssertJ
  - 测试位置：`patra-{service}-domain/src/test/java/`
  - 无框架依赖，纯 Java 测试

- **Application 层**：Mock 单元测试，验证用例编排逻辑和调用顺序
  - 参考: [patra-tdd-development/SKILL.md#Application层TDD](../../.claude/skills/patra-tdd-development/SKILL.md)
  - 技术实现: [java-spring-development/SKILL.md#Orchestrator编排模式](../../.claude/skills/java-spring-development/SKILL.md#快速开发指南)
  - 测试框架：JUnit 5 + Mockito + AssertJ
  - 测试位置：`patra-{service}-app/src/test/java/`
  - Mock 所有 Port 接口

- **Infrastructure 层**：单元测试 + 集成测试（根据实现类型选择）
  - 参考: [patra-tdd-development/SKILL.md#Infrastructure层TDD](../../.claude/skills/patra-tdd-development/SKILL.md)
  - 技术实现: [java-spring-development/SKILL.md#MyBatis-Plus数据访问](../../.claude/skills/java-spring-development/SKILL.md#快速开发指南)
  - 测试位置：`patra-{service}-infra/src/test/java/`
  - **Repository**：@MybatisPlusTest + TestContainers（MySQL）
  - **Feign Client**：单元测试 + WireMock
  - **MQ Publisher**：单元测试 + TestContainers（RocketMQ）
  - **ES Client**：@DataElasticsearchTest + TestContainers（ES）

- **Adapter 层**：单元测试 + Web 切片测试
  - 参考: [patra-tdd-development/SKILL.md#Adapter层TDD](../../.claude/skills/patra-tdd-development/SKILL.md)
  - 技术实现: [java-spring-development/SKILL.md#Controller开发模式](../../.claude/skills/java-spring-development/SKILL.md#快速开发指南)
  - 测试位置：`patra-{service}-adapter/src/test/java/`
  - **Controller**：@WebMvcTest 切片测试，验证 HTTP 请求/响应、参数校验、异常处理
  - **Listener**：单元测试，Mock 业务层依赖
  - **Job**：单元测试，Mock 业务层依赖

- **Boot 层**：@SpringBootTest 端到端测试
  - 参考: [patra-tdd-development/SKILL.md#Boot层E2E测试](../../.claude/skills/patra-tdd-development/SKILL.md)
  - 测试位置：`patra-{service}-boot/src/test/java/`
  - 验证完整业务流程（HTTP → 业务 → DB → MQ → ES）
  - 使用 TestContainers + Awaitility

**技术组件参考**：
- MapStruct 对象转换 → [java-spring-development/SKILL.md#MapStruct对象转换](../../.claude/skills/java-spring-development/SKILL.md#快速开发指南)
- 事务管理 → [java-spring-development/SKILL.md#事务管理最佳实践](../../.claude/skills/java-spring-development/SKILL.md#快速开发指南)
- 错误处理 → [java-spring-development/SKILL.md#错误处理模式](../../.claude/skills/java-spring-development/SKILL.md#快速开发指南)

### 代码审查阶段（Phase 2 完成后）

**参考 Skill**: `java-code-reviewer`

**审查检查点**：
- 架构合规性 → [java-code-reviewer/SKILL.md#架构合规性检查](../../.claude/skills/java-code-reviewer/SKILL.md#代码审查检查清单)
- 代码质量 → [java-code-reviewer/SKILL.md#代码质量检查](../../.claude/skills/java-code-reviewer/SKILL.md#代码审查检查清单)
- 常见反模式 → [java-code-reviewer/SKILL.md#常见反模式识别](../../.claude/skills/java-code-reviewer/SKILL.md#常见反模式识别)

### 文档生成阶段（Phase 2 最后）

**参考 Skill**: `java-documentation-architect`

**文档模板位置**：
- 模块 README.md → [java-documentation-architect/SKILL.md#模块README模板](../../.claude/skills/java-documentation-architect/SKILL.md#模块-readme-模板)
- package-info.java → [java-documentation-architect/SKILL.md#package-info.java模板](../../.claude/skills/java-documentation-architect/SKILL.md#package-infojava-模板)

---

## Phase 1: 设计与文档骨架生成

> **说明**: 本阶段由 `/speckit.plan` 命令执行，在架构设计的同时生成文档骨架

### 1. 架构设计

**参考**: `java-hexagonal-architecture` skill

**决策流程**：

1. **查看代码**: 阅读 `specs/[###-feature-name]/spec.md` 理解功能需求,找到并阅读需求相关的类和文档。

2. **确定微服务归属**: 本功能属于 `patra-[SERVICE_NAME]` 微服务
   - 理由: [说明为什么选择这个服务]

3. **确定触发来源** → 选择适配器类型:
   - REST API → Controller
   - 定时任务 → XXL-Job
   - 消息队列 → MessageListener

4. **设计聚合边界**:
   - 识别聚合根（从 spec.md 的"领域模型"提取）
   - 定义聚合内实体和值对象
   - 确定聚合间的通信方式（领域事件 vs 直接调用）

5. **定义 Port 接口**:
   - Repository 接口（数据访问）

6. 其他模块代码 infra、application、api 等
    
### 2. 文档骨架生成

<!-- AI 执行指令：
当执行 /speckit.plan 命令时，在完成架构设计后自动执行以下文档生成操作：

1. **检测是否为新模块**：
   - 读取上文的"微服务选择"决策
   - 检查 patra-[SERVICE_NAME]/pom.xml 是否存在
   - 如果不存在 → 标记为"新模块"

2. **生成模块 README.md 骨架**（如果是新模块）：

   📍 位置: patra-[SERVICE_NAME]/README.md

   内容映射：
   - 📋 概述 ← spec.md 的"概览"章节（前 2-3 段）
   - 🏗️ 架构位置 ← 本 plan.md 的"微服务选择"说明
   - 🔧 主要功能 ← spec.md 的"功能需求"（FR-*）转为功能列表
   - 📦 依赖关系 ← 本 plan.md 的"Project Structure"中的模块依赖
   - 🎯 核心类说明 ← 标记为 [待 /speckit.implement 阶段补充]
   - 🔌 接口定义 ← spec.md 的"领域事件"章节
   - 📊 数据模型 ← 引用链接: "见 specs/###-feature/data-model.md"
   - 🧪 测试覆盖 ← 标记为 [待测试运行后更新]
   - 📝 变更日志 ← 添加首个版本: "v1.0.0 - 初始版本"

   模板参考: java-documentation-architect/SKILL.md 的"模块 README 模板"

   特殊处理（如果是现有模块）：
   - 不生成新的 README.md（将在 /speckit.implement 阶段增量更新）

3. **生成 API 文档骨架**（如果有 REST API）：

   📍 位置: specs/###-feature/contracts/API.md

   检测条件：
   - "Phase 1: 设计"中确定了需要 Controller
   - 或 spec.md 的"用户场景"中涉及 HTTP 请求

   内容生成：
   - 从 spec.md 的"用户场景"推断 API 端点
   - 每个用户故事 → 一个 API 端点
   - 格式:
     ```markdown
     ### [端点名称]
     **请求**: [从用户场景的 When 推断]
     **响应**: [从用户场景的 Then 推断]
     **错误码**: [从 spec.md 的"成功标准"推断]
     ```

   模板参考: java-documentation-architect/SKILL.md 的"API 文档模板"

4. **生成 data-model.md**（如果有数据模型）：

   📍 位置: specs/###-feature/data-model.md

   内容来源：
   - spec.md 的"领域模型"章节
   - 聚合根、实体、值对象的属性设计
   - 数据库表设计（DO 层）

5. **输出报告**：

   在 plan.md 生成完成后，输出文档生成报告：

   ```
   ✅ Phase 1 文档骨架生成完成

   生成的文档：
   - [✅/❌] patra-[SERVICE_NAME]/README.md（新模块骨架）
   - [✅/❌] specs/###-feature/contracts/API.md（API 文档骨架）
   - [✅/❌] specs/###-feature/data-model.md（数据模型）

   待补充章节（将在 /speckit.implement 阶段自动填充）：
   - 🎯 核心类说明
   - 🧪 测试覆盖率
   ```
-->

**文档生成输出**：
- [ ] 模块 README.md 骨架（如果是新模块）
- [ ] API 文档骨架（如果有 REST API）
- [ ] 数据模型文档

---

## 项目结构

### 文档（本功能）

```text
specs/[###-feature]/
├── plan.md              # 本文件（/speckit.plan 命令输出）
├── research.md          # Phase 0 输出（/speckit.plan 命令）
├── data-model.md        # Phase 1 输出（/speckit.plan 命令）
├── quickstart.md        # Phase 1 输出（/speckit.plan 命令）
├── contracts/           # Phase 1 输出（/speckit.plan 命令）
└── tasks.md             # Phase 2 输出（/speckit.tasks 命令 - 不由 /speckit.plan 创建）
```

### 源代码（代码仓库结构）

查看 整个项目 的目录接口，重点关注 `patra-[SERVICE_NAME]` 模块结构


### 层次职责说明

| 层 | 模块 | 职责 | 允许的依赖 |
|----|------|------|-----------|
| **启动层** | `-boot` | Spring Boot 应用入口、配置加载 | 所有其他层 |
| **契约层** | `-api` | 对外接口定义（DTO、Facade） | 无（纯定义） |
| **适配器层** | `-adapter` | REST API、事件监听、定时任务 | `-app`、`-api` |
| **应用层** | `-app` | 用例编排、事务管理 | `-domain`、`-api` |
| **领域层** | `-domain` | 业务逻辑、领域模型 | **仅 JDK + patra-common-util** |
| **基础设施层** | `-infra` | 数据持久化、外部服务调用 | `-domain` |

### 关键设计决策

**微服务选择**: 本功能将在 `patra-[SERVICE_NAME]` 微服务中实施，因为 [说明为什么选择这个服务]。

**模块创建**: [如果是新微服务，说明需要创建所有 6 个子模块；如果是现有微服务，说明只需要在相关模块中添加代码]

**依赖管理**: 继承 `patra-parent` 的依赖管理，确保版本一致性。

## 复杂度跟踪

> **仅在宪章检查有违规需要说明时填写**

| 违规项 | 为何需要 | 为何拒绝更简单的替代方案 |
|--------|----------|-------------------------|
| [例如：Repository 模式] | [具体问题] | [为何直接数据库访问不够] |
