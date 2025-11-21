# 实施计划：MeSH 数据首次导入

**分支**: `001-mesh-data-import` | **日期**: 2025-11-20 | **规格说明**: [spec.md](./spec.md)
**输入**: 来自 `/specs/001-mesh-data-import/spec.md` 的功能规格说明

**说明**: 本模板由 `/speckit.plan` 命令填充。执行工作流请参见 `.specify/templates/commands/plan.md`。

## 摘要

实现 MeSH 医学主题词表数据的首次导入功能，从 NLM 官方网站下载并解析 XML 数据文件，批量导入约 35 万条记录到 patra-catalog 数据库的 6 张表中。采用 StAX 流式解析避免内存溢出，使用 MeshImportAggregate 聚合根管理任务生命周期，支持断点续传和批次级别的错误恢复。通过 XXL-Job 调度执行，提供 REST API 进行任务管理和进度监控。

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
- **XML 解析**: StAX（JDK 内置流式解析）
- **HTTP 客户端**: Spring RestClient（底层 JDK 21 HttpClient）
- **定时任务**: XXL-Job（分布式任务调度）
- **分布式锁**: Redisson（基于 Redis）

### 可重用组件

- **Starters**: `patra-spring-boot-starter-mybatis-plus`、`patra-spring-boot-starter-mapstruct`
- **Common**: `patra-common-core`（所有模块公用）、`patra-common-model`（通用模型）
- **Registry**: 本功能不依赖 patra-registry（MeSH 本身就是数据字典）

## 宪章检查

*门禁规则：Phase 0 调研前必须通过，Phase 1 设计后重新检查*

基于 `.specify/memory/constitution.md` 的验证项：

### 架构验证

- [x] **CHK-ARCH-001**: 模块结构符合 6 层规范（boot/api/domain/app/infra/adapter）
  - **状态**: PASS
  - **说明**: 功能实现在现有的 patra-catalog 模块中，已符合 6 层架构

- [x] **CHK-ARCH-002**: Domain 层 `pom.xml` 无任何框架依赖（仅 JDK + patra-common-core + Lombok + Hutool + Jackson）
  - **状态**: PASS
  - **说明**: MeshImportAggregate、TableProgress、领域事件等均为纯 Java 实现，无框架依赖

- [x] **CHK-ARCH-003**: 依赖方向符合 `Adapter → App → Domain ← Infra`
  - **状态**: PASS
  - **验证方法**: Controller 调用 Application 服务，Application 编排 Domain 逻辑，Infrastructure 实现 Port 接口

### DDD 验证

- [x] **CHK-DDD-001**: 识别出明确的聚合根（Aggregate Root）
  - **状态**: PASS
  - **聚合根列表**: `MeshImportAggregate`（管理导入任务生命周期）

- [x] **CHK-DDD-002**: 聚合边界清晰（不跨聚合直接访问实体）
  - **状态**: PASS
  - **说明**: MeshImportAggregate 包含 TableProgress 值对象，批次详情存储在 Infrastructure 层，通过 Repository 访问

- [x] **CHK-DDD-003**: 值对象设计为不可变（immutable）
  - **状态**: PASS
  - **值对象列表**: `MeshImportId`、`DescriptorId`、`QualifierId`、`TableProgress`、`TreeNumber`、`EntryTerm`、`Concept`
  - **说明**: Domain 层使用强类型 ID 提供类型安全，Infrastructure 层 DO 使用 Long 雪花 ID（MyBatis-Plus 自动生成）

- [x] **CHK-DDD-004**: 领域事件使用过去时命名
  - **状态**: PASS
  - **事件列表**: `MeshImportStarted`、`MeshImportCompleted`、`MeshImportFailed`

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
  - **集成测试类型**: Repository (@MybatisTest), Feign Client (WireMock), MQ Publisher (TestContainers)

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
  - **状态**: 计划在 Phase 2 实施（需要新建 patra-catalog/README.md）

- [x] **CHK-DOC-002**: 所有文档和注释使用中文
  - **状态**: PASS

- [ ] **CHK-DOC-003**: API 有 Swagger 文档
  - **状态**: 计划在 Phase 2 实施（已生成 OpenAPI 规范）

---

**Phase 0 门禁结果**: 通过 ✅

**Phase 1 重新验证结果**: 通过 ✅

**说明**: 所有架构和 DDD 验证项均通过，测试和文档将在 Phase 2 实施阶段完成。

---

## Skills 参考指南

**说明**: 以下 Skills 提供详细的实施规范和代码模板，在 Phase 1 设计和 Phase 2 实施时参考。

### 架构设计阶段（Phase 1）

**参考 Skill**: `java-hexagonal-architecture`

**关键决策点**：
- [x] **触发来源** → 确定适配器类型
  - **决策**: REST API Controller + XXL-Job 定时任务
  - **理由**: Controller 提供管理接口，XXL-Job 执行实际导入任务
  - 参考: [java-hexagonal-architecture/SKILL.md#架构决策流程](../../.claude/skills/java-hexagonal-architecture/SKILL.md#架构决策流程)
- [x] **聚合设计** → 确定聚合根和聚合边界
  - **决策**: MeshImportAggregate 作为唯一聚合根，包含 TableProgress 值对象
  - **边界**: 批次详情不属于聚合，存储在 Infrastructure 层
  - 参考: [java-hexagonal-architecture/SKILL.md#聚合设计模式](../../.claude/skills/java-hexagonal-architecture/SKILL.md#常见架构模式)
- [x] **Port 接口定义** → 设计 Repository 和其他端口接口
  - **决策**: MeshImportPort（任务管理）、MeshDescriptorPort（数据持久化）、XmlParserPort（XML 解析）
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
  - **Repository**：@MybatisTest + TestContainers（MySQL）
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

1. **查看代码**: 已阅读 `specs/001-mesh-data-import/spec.md` 理解功能需求，查看了现有的 MeSH 相关代码。

2. **确定微服务归属**: 本功能属于 `patra-catalog` 微服务
   - 理由: MeSH 是医学主题词目录数据，属于 catalog（目录）服务的核心数据，且已有相关 Repository 框架

3. **确定触发来源** → 选择适配器类型:
   - REST API → MeshImportController（管理接口，位于 adapter/rest 包）
   - 定时任务 → MeshImportJob（XXL-Job 执行器）
   - 消息监听 → TaskReadyMessageListener（RocketMQ 消费者，位于 adapter/rocketmq 包）

4. **设计聚合边界**:
   - 聚合根：MeshImportAggregate（管理导入任务生命周期）
   - 聚合内值对象：TableProgress（表进度）、MeshImportId（强类型 ID）
   - 聚合外实体：BatchDetail（批次详情，通过 Repository 访问）
   - 通信方式：领域事件（MeshImportCompleted、MeshImportFailed）

5. **定义 Port 接口**:
   - MeshImportPort：任务管理仓储接口
   - MeshDescriptorPort：MeSH 数据持久化接口（已存在）
   - XmlParserPort：XML 解析接口
   - MeshFileDownloadPort：MeSH 文件下载接口（基于 RestClient）

6. **模块代码结构**:
   - domain：聚合根、值对象、领域事件、Port 接口定义
   - application：MeshImportOrchestrator（任务编排）、MeshImportErrorMappingContributor（异常映射）、MeshDataValidator（数据验证）
   - infrastructure：Repository 实现、StAX XML 解析实现、RestClient 文件下载实现
   - adapter：
     * rest：REST Controller（管理接口）
     * rocketmq：消息监听器和 DTO
     * scheduler：XXL-Job 任务处理器
   - api：Command 对象、DTO 定义
    
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
- [x] 模块 README.md 骨架（patra-catalog 模块尚无 README，需创建）
- [x] API 文档骨架（contracts/mesh-import-api.yaml）
- [x] 数据模型文档（data-model.md）

---

## 项目结构

### 文档（本功能）

```text
specs/001-mesh-data-import/
├── spec.md                     # 功能规格说明（输入）
├── plan.md                     # 本文件（实施计划）
├── research.md                 # Phase 0 研究输出
├── data-model.md               # Phase 1 数据模型设计
├── quickstart.md               # Phase 1 快速开始指南
├── contracts/
│   └── mesh-import-api.yaml    # Phase 1 OpenAPI 规范
└── tasks.md                    # Phase 2 输出（待 /speckit.tasks 命令创建）
```

### 源代码（代码仓库结构）

```text
patra-catalog/
├── patra-catalog-boot/          # Spring Boot 启动入口
├── patra-catalog-api/           # 对外接口定义
│   ├── command/
│   │   └── StartImportCommand.java
│   └── dto/
│       ├── MeshImportResultDTO.java
│       └── MeshProgressDTO.java
├── patra-catalog-adapter/       # 适配器层
│   ├── rest/
│   │   └── MeshImportController.java
│   ├── rocketmq/
│   │   ├── TaskReadyMessageListener.java
│   │   └── dto/
│   │       └── TaskReadyPayload.java
│   └── scheduler/
│       └── MeshImportJob.java
├── patra-catalog-app/            # 应用层
│   ├── error/
│   │   └── MeshImportErrorMappingContributor.java
│   └── usecase/
│       └── meshimport/
│           ├── MeshImportOrchestrator.java
│           ├── MeshProgressQueryOrchestrator.java
│           └── validator/
│               └── MeshDataValidator.java
├── patra-catalog-domain/        # 领域层（纯 Java）
│   ├── model/
│   │   ├── aggregate/
│   │   │   └── MeshImportAggregate.java
│   │   ├── valueobject/
│   │   │   ├── MeshImportId.java
│   │   │   ├── TableProgress.java
│   │   │   └── DescriptorId.java
│   │   └── event/
│   │       ├── MeshImportStarted.java
│   │       ├── MeshImportCompleted.java
│   │       └── MeshImportFailed.java
│   └── port/
│       ├── MeshImportPort.java
│       ├── MeshDescriptorPort.java
│       ├── XmlParserPort.java
│       └── MeshFileDownloadPort.java
└── patra-catalog-infra/         # 基础设施层
    ├── persistence/
    │   ├── repository/
    │   │   ├── MeshImportRepositoryImpl.java
    │   │   └── MeshDescriptorRepositoryImpl.java（已存在）
    │   ├── mapper/
    │   │   ├── MeshImportTaskMapper.java
    │   │   └── MeshTableProgressMapper.java
    │   └── entity/
    │       ├── MeshImportTaskDO.java
    │       └── MeshTableProgressDO.java
    ├── parser/
    │   └── StaxXmlParserImpl.java
    └── download/
        └── RestClientMeshFileDownloadImpl.java
```


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

**微服务选择**: 本功能将在 `patra-catalog` 微服务中实施，因为 MeSH 是医学主题词目录数据，属于 catalog（目录）服务的核心数据域。

**模块创建**: 在现有的 patra-catalog 微服务各层模块中添加代码，无需创建新的微服务。需要创建 patra-catalog/README.md 文档。

**依赖管理**: 继承 `patra-parent` 的依赖管理，新增以下依赖：

**patra-catalog-infra 模块**：
- `spring-web`（仅引入 RestClient，不引入 Web 容器）

**patra-catalog-boot 模块**：
- `redisson-spring-boot-starter`（分布式锁，Redis 已集成）

复用现有依赖：
- JDK 21 HttpClient（RestClient 底层实现，JDK 内置）
- MyBatis-Plus、XXL-Job、Micrometer（项目已有）

**技术选型理由**:
- **StAX 而非 JAXB**：35 万条记录的 XML 文件需要流式处理，避免 OOM
- **spring-web 而非 spring-boot-starter-web**：遵循最小依赖原则，Infrastructure 层只需要 RestClient，不引入 Web 容器（Tomcat）和 Spring MVC
- **RestClient 而非 Apache HttpClient 5**：保持技术栈一致性，项目已在 patra-spring-boot-starter-provenance 中使用 RestClient
- **XXL-Job 而非 @Scheduled**：支持分布式任务调度，任务状态可视化管理
- **Redisson 分布式锁而非数据库锁**：项目已集成 Redis，Redisson 提供可靠的分布式锁实现，性能优于数据库行锁
- **批次重试而非全量重试**：提高效率，减少重复处理

## 复杂度跟踪

> **仅在宪章检查有违规需要说明时填写**

**本功能无宪章违规项，所有验证均通过。**
