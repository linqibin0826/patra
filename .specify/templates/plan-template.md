# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]
**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  Patra 项目技术栈：基于实际使用的框架和工具填充
  如有特殊需求，可以在下方调整具体版本或增加额外依赖
-->

**Language/Version**: Java 25
**Primary Framework**: Spring Boot 3.5.7, Spring Cloud 2025.0.0
**Persistence**: MyBatis-Plus 3.x
**Object Mapping**: MapStruct
**Service Discovery**: Nacos
**Testing**: JUnit 5 + Mockito（单元测试），TestContainers（IT 集成测试）
**Target Platform**: Linux 服务器 / Docker 容器
**Architecture Pattern**: 六边形架构（Hexagonal Architecture）+ DDD
**Performance Goals**: [根据具体功能确定，如：API 响应时间 < 200ms P95]
**Constraints**: [根据具体功能确定，如：支持 1000 并发请求]
**Scale/Scope**: [根据具体功能确定，如：日处理 100 万条数据]

### 技术栈说明

- **依赖管理**: Maven（继承 `patra-parent`）
- **配置管理**: Nacos Config（从 `patra-registry` 获取配置）
- **日志**: SLF4J + Logback
- **API 文档**: Swagger/OpenAPI 3.0
- **代码生成**: MyBatis-Plus Generator + MapStruct Processor

### 可重用组件

- **Starters**: `patra-spring-boot-starter-*`（根据需要选择）
- **Common**: `patra-common-util`（工具类）、`patra-common-model`（通用模型）
- **Registry**: `patra-registry-api`（获取配置和字典）

## Constitution Check

*门禁规则：Phase 0 调研前必须通过，Phase 1 设计后重新检查*

基于 `.specify/memory/constitution.md` 的验证项：

### 架构验证

- [ ] **CHK-ARCH-001**: 模块结构符合 6 层规范（boot/api/domain/app/infra/adapter）
  - **状态**: [PASS / FAIL / N/A]
  - **说明**: [如果 FAIL，说明偏差和理由]

- [ ] **CHK-ARCH-002**: Domain 层 `pom.xml` 无任何框架依赖（仅 JDK + patra-common-util + Lombok + Validation API）
  - **状态**: [PASS / FAIL / N/A]
  - **说明**: [列出 domain 层的依赖清单]

- [ ] **CHK-ARCH-003**: 依赖方向符合 `Adapter → App → Domain ← Infra`
  - **状态**: [PASS / FAIL / N/A]
  - **验证方法**: 将在 Phase 2 实施时通过代码审查或 ArchUnit 测试验证

### DDD 验证

- [ ] **CHK-DDD-001**: 识别出明确的聚合根（Aggregate Root）
  - **状态**: [PASS / FAIL / N/A]
  - **聚合根列表**: [列出识别的聚合根，如：`Article`、`Provenance`]

- [ ] **CHK-DDD-002**: 聚合边界清晰（不跨聚合直接访问实体）
  - **状态**: [PASS / FAIL / N/A]
  - **说明**: [描述聚合边界]

- [ ] **CHK-DDD-003**: 值对象设计为不可变（immutable）
  - **状态**: [PASS / FAIL / N/A]
  - **值对象列表**: [列出值对象，如：`ArticleId`、`Email`]

- [ ] **CHK-DDD-004**: 领域事件使用过去时命名
  - **状态**: [PASS / FAIL / N/A]
  - **事件列表**: [列出领域事件，如：`ArticleCreated`、`ArticlePublished`]

### SSOT 验证

- [ ] **CHK-SSOT-001**: Provenance 配置从 `patra-registry` 获取（无硬编码）
  - **状态**: [PASS / FAIL / N/A]
  - **说明**: [如果需要 Provenance 配置，说明如何获取]

- [ ] **CHK-SSOT-002**: 数据字典从 `patra-registry` 获取
  - **状态**: [PASS / FAIL / N/A]
  - **字典类型**: [列出需要的字典，如：`ArticleType`、`PublicationStatus`]

- [ ] **CHK-SSOT-003**: 元数据和映射规则从 `patra-registry` 获取
  - **状态**: [PASS / FAIL / N/A]
  - **说明**: [如果需要元数据映射，说明用途]

### 测试验证（将在 Phase 2 实施）

- [ ] **CHK-TEST-001**: Domain 层单元测试覆盖率 ≥ 80%
  - **状态**: [计划在 Phase 2 实施]

- [ ] **CHK-TEST-002**: Application 层单元测试覆盖率 ≥ 70%
  - **状态**: [计划在 Phase 2 实施]

- [ ] **CHK-TEST-003**: Infrastructure 层有 IT 集成测试
  - **状态**: [计划在 Phase 2 实施]

- [ ] **CHK-TEST-004**: Adapter 层有 E2E 测试
  - **状态**: [计划在 Phase 2 实施]

### 文档验证

- [ ] **CHK-DOC-001**: 模块根目录有完整的 `README.md`
  - **状态**: [计划在 Phase 2 实施]

- [ ] **CHK-DOC-002**: 所有文档和注释使用中文
  - **状态**: [PASS]

- [ ] **CHK-DOC-003**: API 有 Swagger 文档
  - **状态**: [计划在 Phase 2 实施]

---

**Phase 0 门禁结果**: [PASS / FAIL]

**如果 FAIL**: 必须在 Complexity Tracking 章节说明违规理由和替代方案。

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code（代码仓库结构）

本功能将在以下微服务中实施：**`patra-[SERVICE_NAME]`**

```text
patra-[SERVICE_NAME]/
│
├── patra-[SERVICE_NAME]-boot/              # 🚀 启动模块
│   ├── src/main/java/
│   │   └── com/patra/[service]/
│   │       └── Application.java            # Spring Boot 启动类
│   └── src/main/resources/
│       ├── application.yml                 # 应用配置
│       └── bootstrap.yml                   # Nacos 配置
│
├── patra-[SERVICE_NAME]-api/               # 📋 契约层（对外接口定义）
│   └── src/main/java/
│       └── com/patra/[service]/api/
│           ├── dto/                        # 请求/响应 DTO
│           │   ├── [Entity]Request.java
│           │   └── [Entity]Response.java
│           └── facade/                     # 服务接口（Dubbo/OpenFeign）
│               └── [Service]Facade.java
│
├── patra-[SERVICE_NAME]-domain/            # 🏛️ 领域层（纯 Java，无框架依赖）
│   ├── src/main/java/
│   │   └── com/patra/[service]/domain/
│   │       ├── model/                      # 聚合根、实体、值对象
│   │       │   ├── [AggregateRoot].java    # 聚合根
│   │       │   ├── [Entity].java           # 实体
│   │       │   └── [ValueObject].java      # 值对象
│   │       ├── event/                      # 领域事件
│   │       │   ├── [Event]Created.java
│   │       │   └── [Event]Updated.java
│   │       ├── service/                    # 领域服务（跨聚合的业务逻辑）
│   │       │   └── [Domain]Service.java
│   │       └── repository/                 # 仓储接口（实现在 infra 层）
│   │           └── [Aggregate]Repository.java
│   └── src/test/java/                      # 单元测试（覆盖率 ≥ 80%）
│       └── com/patra/[service]/domain/
│           └── model/[AggregateRoot]Test.java
│
├── patra-[SERVICE_NAME]-app/               # 🎯 应用层（用例编排）
│   ├── src/main/java/
│   │   └── com/patra/[service]/app/
│   │       ├── orchestrator/               # 复杂用例编排器（多聚合协作）
│   │       │   └── [UseCase]Orchestrator.java
│   │       ├── coordinator/                # 简单协调器（单聚合操作）
│   │       │   └── [UseCase]Coordinator.java
│   │       └── assembler/                  # DTO 组装器
│   │           └── [Entity]Assembler.java
│   └── src/test/java/                      # 单元测试（覆盖率 ≥ 70%）
│       └── com/patra/[service]/app/
│           └── orchestrator/[UseCase]OrchestratorTest.java
│
├── patra-[SERVICE_NAME]-infra/             # 🔧 基础设施层（技术实现）
│   ├── src/main/java/
│   │   └── com/patra/[service]/infra/
│   │       ├── repository/                 # 仓储实现（MyBatis-Plus）
│   │       │   ├── [Aggregate]RepositoryImpl.java
│   │       │   └── mapper/
│   │       │       └── [Entity]Mapper.java # MyBatis-Plus Mapper
│   │       ├── converter/                  # 对象转换器（MapStruct）
│   │       │   └── [Entity]Converter.java
│   │       ├── config/                     # 配置类
│   │       │   ├── MyBatisPlusConfig.java
│   │       │   └── DataSourceConfig.java
│   │       └── gateway/                    # 外部服务网关（调用其他服务）
│   │           └── [External]Gateway.java
│   └── src/test/java/                      # IT 集成测试（TestContainers）
│       └── com/patra/[service]/infra/
│           └── repository/[Aggregate]RepositoryIT.java
│
└── patra-[SERVICE_NAME]-adapter/           # 🔌 适配器层（外部交互）
    ├── src/main/java/
    │   └── com/patra/[service]/adapter/
    │       ├── controller/                 # REST API 控制器
    │       │   └── [Entity]Controller.java
    │       ├── listener/                   # 事件监听器（Kafka/RocketMQ）
    │       │   └── [Event]Listener.java
    │       └── job/                        # 定时任务（XXL-Job）
    │           └── [Task]Job.java
    └── src/test/java/                      # E2E 测试（MockMvc）
        └── com/patra/[service]/adapter/
            └── controller/[Entity]ControllerIT.java
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

**微服务选择**: 本功能将在 `patra-[SERVICE_NAME]` 微服务中实施，因为 [说明为什么选择这个服务]。

**模块创建**: [如果是新微服务，说明需要创建所有 6 个子模块；如果是现有微服务，说明只需要在相关模块中添加代码]

**依赖管理**: 继承 `patra-parent` 的依赖管理，确保版本一致性。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
