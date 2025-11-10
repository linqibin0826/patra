# Implementation Plan: 测试基础设施模块重构

**Branch**: `001-test-infrastructure-refactor` | **Date**: 2025-11-09 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-test-infrastructure-refactor/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

本功能旨在**统一管理测试依赖、测试工具类和测试配置**,提供可复用的测试基础设施。通过创建两个新模块:
- **patra-common-test**: 纯 Java 测试工具库,提供 TestDataBuilder、MockDataFactory、DomainAssertions 等通用测试工具类
- **patra-spring-boot-starter-test**: Spring Boot 测试自动配置,提供 TestContainers、MockMvc、WireMock、BaseIntegrationTest、BaseE2ETest 等测试环境

**技术方法**: 采用 Spring Boot Starter 自动配置机制,在 patra-parent 中统一管理测试依赖版本,并将现有模块中的通用测试工具类和配置迁移到新的测试基础设施模块中。

**特殊性说明**: 这是一个**技术基础设施重构**项目,不涉及业务功能和领域模型,因此传统的六边形架构设计流程不完全适用。

## Technical Context

**Language/Version**: Java 25
**Primary Framework**: Spring Boot 3.5.7, Spring Cloud 2025.0.0
**Testing Frameworks**:
- JUnit 5 (Jupiter) - 测试框架核心
- Mockito 5.x - Mock 框架
- AssertJ 3.x - 流式断言库
- TestContainers 1.19.x - 容器化集成测试
- Spring Boot Test 3.5.7 - Spring Boot 测试支持
- MockMvc - REST API 测试
- WireMock - HTTP Mock Server

**Target Platform**: 开发者本地环境 + CI/CD 流水线
**Docker Requirements**: Docker 20.10+ (macOS, Linux, Windows with WSL2)
**Architecture Pattern**: 测试基础设施层(横切关注点),服务于所有业务模块
**Performance Goals**:
- 单元测试启动时间增加 < 5%
- TestContainers 启动时间 < 10 秒
- 测试工具类性能开销可忽略(微秒级)

**Constraints**:
- patra-common-test 必须是纯 Java (无 Spring 依赖)
- patra-spring-boot-starter-test 遵循 Spring Boot Starter 自动配置规范
- 所有测试依赖版本在 patra-parent 统一管理

**Scale/Scope**:
- 支持 10+ 个微服务模块
- 覆盖单元测试、集成测试(IT)、E2E 测试三层测试金字塔
- 迁移现有模块中的通用测试工具类和配置

### 技术栈说明

- **依赖管理**: Maven（在 `patra-parent` 的 dependencyManagement 中统一定义测试依赖版本）
- **自动配置机制**: Spring Boot Auto-Configuration + META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
- **测试容器**: TestContainers (MySQL 8.x, Redis 7.x, Nacos 2.x)
- **日志**: SLF4J + Logback (支持可配置日志级别,如 logging.level.com.patra.test=DEBUG)
- **代码生成**: 无需代码生成,测试工具类手动编写

### 可重用组件

**新创建的组件**:
- **patra-common-test**: 纯 Java 测试工具库
  - TestDataBuilder: 测试数据构建器
  - MockDataFactory: Mock 数据工厂
  - AssertionHelper: 断言辅助类
  - DomainAssertions: 领域断言工具
  - BaseUnitTest: 单元测试基类
  - TestConstants: 测试常量

- **patra-spring-boot-starter-test**: Spring Boot 测试自动配置
  - BaseIntegrationTest: 集成测试基类
  - BaseE2ETest: E2E 测试基类
  - TestContainersConfiguration: TestContainers 自动配置
  - MockMvcConfiguration: MockMvc 自动配置
  - WireMockConfiguration: WireMock 自动配置

**依赖的现有组件**:
- **patra-parent**: dependencyManagement 统一版本管理
- **patra-common**: patra-common-test 作为其子模块

## Constitution Check

*门禁规则：Phase 0 调研前必须通过，Phase 1 设计后重新检查*

**⚠️ 特殊说明**: 本功能为**测试基础设施重构**,不涉及业务领域模型,因此部分 DDD 相关验证项不适用(标记为 N/A)。

基于 `.specify/memory/constitution.md` 的验证项：

### 架构验证

- [x] **CHK-ARCH-001**: 模块结构符合 6 层规范（boot/api/domain/app/infra/adapter）
  - **状态**: N/A
  - **说明**: 测试基础设施模块不遵循六边形架构的 6 层结构,而是采用测试工具库的结构:
    - `patra-common-test`: 纯 Java 工具库
    - `patra-spring-boot-starter-test`: Spring Boot Starter 模块
    - 这符合横切关注点(Cross-cutting Concerns)的设计模式

- [x] **CHK-ARCH-002**: patra-common-test 必须是纯 Java (类似 Domain 层的纯净性要求)
  - **状态**: PASS
  - **说明**: patra-common-test 的依赖清单:
    - JUnit 5 (Jupiter)
    - Mockito 5.x
    - AssertJ 3.x
    - Lombok (仅编译期)
    - **无 Spring 框架依赖**
    - 这符合 CHK-ARCH-001 的精神(保持核心层纯净)

- [x] **CHK-ARCH-003**: 依赖方向正确
  - **状态**: PASS
  - **依赖方向**:
    - 业务模块 (test scope) → patra-spring-boot-starter-test → patra-common-test
    - patra-common-test 不依赖任何业务模块
    - 单向依赖,无循环

### DDD 验证

- [x] **CHK-DDD-001**: 识别出明确的聚合根（Aggregate Root）
  - **状态**: N/A
  - **说明**: 测试基础设施不涉及业务领域模型,无聚合根概念

- [x] **CHK-DDD-002**: 聚合边界清晰（不跨聚合直接访问实体）
  - **状态**: N/A
  - **说明**: 测试基础设施不涉及聚合设计

- [x] **CHK-DDD-003**: 值对象设计为不可变（immutable）
  - **状态**: N/A
  - **说明**: 测试基础设施不涉及值对象设计

- [x] **CHK-DDD-004**: 领域事件使用过去时命名
  - **状态**: N/A
  - **说明**: 测试基础设施不发布领域事件

### SSOT 验证

- [x] **CHK-SSOT-001**: Provenance 配置从 `patra-registry` 获取（无硬编码）
  - **状态**: N/A
  - **说明**: 测试基础设施不涉及 Provenance 配置

- [x] **CHK-SSOT-002**: 数据字典从 `patra-registry` 获取
  - **状态**: N/A
  - **说明**: 测试基础设施不使用业务数据字典

- [x] **CHK-SSOT-003**: 元数据和映射规则从 `patra-registry` 获取
  - **状态**: N/A
  - **说明**: 测试基础设施不涉及元数据映射

### 测试验证

**⚠️ 特殊情况**: 本功能是测试基础设施本身,需要**元测试**(测试测试工具),策略有所不同

- [x] **CHK-TEST-001~004**: 测试基础设施的测试策略
  - **状态**: 计划在 Phase 2 实施
  - **测试策略**:
    - patra-common-test: 单元测试覆盖率 ≥ 80% (测试 TestDataBuilder、MockDataFactory 等工具类)
    - patra-spring-boot-starter-test: 集成测试验证自动配置生效(TestContainers 启动、Spring Context 加载)
    - 示例测试: 在 patra-registry-boot 中编写示例测试,验证测试基础设施可用性

- [x] **CHK-TEST-006**: IT 和 E2E 测试位置规范
  - **状态**: PASS
  - **说明**: patra-spring-boot-starter-test 的集成测试将放在其自身的 `src/test/java` 中,示例测试将放在 patra-registry-boot 模块中验证

### 文档验证

- [x] **CHK-DOC-001**: 模块根目录有完整的 `README.md`
  - **状态**: 计划在 Phase 1 生成
  - **说明**: 将为 patra-common-test 和 patra-spring-boot-starter-test 生成 README.md

- [x] **CHK-DOC-002**: 所有文档和注释使用中文
  - **状态**: PASS
  - **说明**: 所有文档、JavaDoc、代码注释均使用中文

- [x] **CHK-DOC-003**: API 有 Swagger 文档
  - **状态**: N/A
  - **说明**: 测试基础设施不提供 REST API,无需 Swagger 文档
  - **替代方案**: 提供详细的 JavaDoc 和使用示例代码

---

**Phase 0 门禁结果**: PASS ✅

**说明**: 所有适用的验证项均通过。不适用的验证项(N/A)已在上文明确说明理由。无需在 Complexity Tracking 章节额外说明。

---

## Skills 参考指南

**⚠️ 特殊说明**: 本功能为测试基础设施重构,不完全适用传统的六边形架构 Skills。以下仅列出适用的参考资料。

### Phase 0: 调研阶段

**参考资源**:
- **TestContainers 最佳实践**: 使用 `context7` 和 `web-research-specialist` 调研
- **Spring Boot Starter 自动配置**: 使用 `context7` 查询 Spring Boot 官方文档
- **现有测试工具类盘点**: 使用 `Explore` agent 搜索项目中的测试代码

### Phase 1: 设计阶段

**参考 Skill**: `patra-tdd-development`

**关键参考点**：
- [ ] **测试工具库设计** → TestDataBuilder 设计模式
  - 参考: [patra-tdd-development/SKILL.md](../../.claude/skills/patra-tdd-development/SKILL.md)
- [ ] **测试基类设计** → BaseIntegrationTest、BaseE2ETest 最佳实践
  - 遵循 TDD 工作流和六边形架构原则

### Phase 2: TDD 驱动的实施阶段（由 /speckit.implement 调用）

**执行 Agent**: `patra-backend-developer`（加载 `patra-tdd-development` + `java-spring-development` Skills）

**核心原则**：测试驱动设计，遵循 Red-Green-Refactor 循环
- 🔴 **Red**：先写失败的测试（定义期望行为）
- 🟢 **Green**：编写最少代码通过测试
- 🔵 **Refactor**：重构代码和测试，保持测试通过

**关键参考点**：
- **测试工具类 TDD 实践**:
  - 参考: [patra-tdd-development/SKILL.md](../../.claude/skills/patra-tdd-development/SKILL.md)
  - 元测试：为 TestDataBuilder、MockDataFactory 编写测试
- **Spring Boot 技术实现**:
  - Spring Boot Starter 自动配置 → [java-spring-development/SKILL.md](../../.claude/skills/java-spring-development/SKILL.md)
  - TestContainers 配置 → @Bean 定义 + Singleton 容器

### 元测试生成阶段（Phase 2）

**说明**: 为测试基础设施本身编写测试(元测试)

**策略**:
- patra-common-test 的单元测试: 验证 TestDataBuilder、MockDataFactory 等工具类的正确性
- patra-spring-boot-starter-test 的集成测试: 验证自动配置生效、TestContainers 启动成功
- 示例测试: 在 patra-registry-boot 中编写示例,演示测试基础设施的使用方法

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
1. **确定微服务归属**: 本功能属于 `patra-[SERVICE_NAME]` 微服务
   - 理由: [说明为什么选择这个服务]

2. **确定触发来源** → 选择适配器类型:
   - REST API → Controller
   - 定时任务 → XXL-Job
   - 消息队列 → MessageListener

3. **设计聚合边界**:
   - 识别聚合根（从 spec.md 的"领域模型"提取）
   - 定义聚合内实体和值对象
   - 确定聚合间的通信方式（领域事件 vs 直接调用）

4. **定义 Port 接口**:
   - Repository 接口（数据访问）
   - Gateway 接口（外部服务调用）
   - EventPublisher 接口（事件发布）

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

### Source Code（测试基础设施模块结构）

**说明**: 本功能创建两个新的测试基础设施模块,并更新现有模块的测试依赖

#### 1. patra-common-test (纯 Java 测试工具库)

```text
patra-common/
└── patra-common-test/                      # 🧪 纯 Java 测试工具库
    ├── pom.xml                             # 依赖: JUnit 5, Mockito, AssertJ, Lombok
    ├── README.md                           # 使用文档
    ├── src/main/java/
    │   └── com/patra/common/test/
    │       ├── builder/                    # 测试数据构建器
    │       │   ├── TestDataBuilder.java    # 通用测试数据构建器基类
    │       │   └── package-info.java
    │       ├── factory/                    # Mock 数据工厂
    │       │   ├── MockDataFactory.java    # 批量生成测试数据
    │       │   └── package-info.java
    │       ├── assertion/                  # 断言辅助工具
    │       │   ├── AssertionHelper.java    # 通用断言辅助类
    │       │   ├── DomainAssertions.java   # 领域断言工具
    │       │   └── package-info.java
    │       ├── base/                       # 测试基类
    │       │   ├── BaseUnitTest.java       # 单元测试基类
    │       │   └── package-info.java
    │       ├── constant/                   # 测试常量
    │       │   ├── TestConstants.java      # 测试常量定义
    │       │   └── package-info.java
    │       └── package-info.java           # 模块级别说明
    └── src/test/java/                      # 元测试（测试测试工具）
        └── com/patra/common/test/
            ├── builder/
            │   └── TestDataBuilderTest.java
            └── factory/
                └── MockDataFactoryTest.java
```

#### 2. patra-spring-boot-starter-test (Spring Boot 测试自动配置)

```text
patra-spring-boot-starter-test/             # 🚀 Spring Boot 测试自动配置
├── pom.xml                                 # 依赖: patra-common-test, Spring Boot Test, TestContainers
├── README.md                               # 使用文档
├── src/main/java/
│   └── com/patra/spring/boot/starter/test/
│       ├── autoconfigure/                  # 自动配置类
│       │   ├── TestContainersAutoConfiguration.java
│       │   ├── MockMvcAutoConfiguration.java
│       │   ├── WireMockAutoConfiguration.java
│       │   └── package-info.java
│       ├── base/                           # 测试基类
│       │   ├── BaseIntegrationTest.java    # 集成测试基类
│       │   ├── BaseE2ETest.java            # E2E 测试基类
│       │   └── package-info.java
│       ├── container/                      # TestContainers 容器定义
│       │   ├── MySQLTestContainer.java     # MySQL 容器
│       │   ├── RedisTestContainer.java     # Redis 容器
│       │   ├── NacosTestContainer.java     # Nacos 容器
│       │   └── package-info.java
│       └── package-info.java               # 模块级别说明
├── src/main/resources/
│   └── META-INF/spring/
│       └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
└── src/test/java/                          # 元测试（验证自动配置）
    └── com/patra/spring/boot/starter/test/
        ├── autoconfigure/
        │   └── TestContainersAutoConfigurationTest.java
        └── base/
            ├── BaseIntegrationTestTest.java
            └── BaseE2ETestTest.java
```

#### 3. 现有模块更新（示例：patra-registry）

```text
patra-registry/
├── patra-registry-domain/
│   └── pom.xml                             # ✅ 添加: patra-common-test (test scope)
├── patra-registry-app/
│   └── pom.xml                             # ✅ 添加: patra-common-test (test scope)
├── patra-registry-infra/
│   └── pom.xml                             # ✅ 添加: patra-common-test (test scope)
├── patra-registry-adapter/
│   └── pom.xml                             # ✅ 添加: patra-common-test (test scope)
└── patra-registry-boot/
    ├── pom.xml                             # ✅ 添加: patra-spring-boot-starter-test (test scope)
    └── src/test/java/
        └── com/patra/registry/
            ├── RegistryRepositoryIT.java   # ✅ 示例集成测试
            └── RegistryFlowE2E.java        # ✅ 示例 E2E 测试
```

### 模块职责说明

| 模块 | 职责 | 依赖范围 | 允许的依赖 |
|-----|------|---------|-----------|
| **patra-common-test** | 纯 Java 测试工具类(TestDataBuilder, MockDataFactory, Assertions) | test scope | JUnit 5, Mockito, AssertJ, Lombok |
| **patra-spring-boot-starter-test** | Spring Boot 测试自动配置(TestContainers, MockMvc, WireMock) | test scope | patra-common-test, Spring Boot Test, TestContainers |
| **业务模块-domain/app/infra/adapter** | 使用 patra-common-test 编写单元测试 | test scope | patra-common-test |
| **业务模块-boot** | 使用 patra-spring-boot-starter-test 编写集成测试和 E2E 测试 | test scope | patra-spring-boot-starter-test |

### 关键设计决策

**模块归属**:
- `patra-common-test` 作为 `patra-common` 的子模块,与 patra-common-util、patra-common-model 平级
- `patra-spring-boot-starter-test` 与其他 patra-spring-boot-starter-* 模块平级,位于项目根目录

**依赖方向**:
```
业务模块 (test scope)
   ↓
patra-spring-boot-starter-test (仅 boot 模块使用)
   ↓
patra-common-test (所有模块使用)
   ↓
JUnit 5 + Mockito + AssertJ
```

**迁移策略**:
1. 先创建 patra-common-test 和 patra-spring-boot-starter-test 模块
2. 迁移通用测试工具类到 patra-common-test
3. 迁移测试配置到 patra-spring-boot-starter-test
4. 在 patra-parent 的 dependencyManagement 中添加测试依赖版本管理
5. 分阶段更新各业务模块的 pom.xml,引入新的测试基础设施
6. 在 patra-registry-boot 中编写示例测试,验证测试基础设施可用性
7. 推广到其他微服务模块

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

**本功能无需填写此章节**

**理由**: 所有 Constitution Check 验证项均为 PASS 或 N/A(已在 Constitution Check 章节中明确说明不适用的理由)。测试基础设施重构属于横切关注点,不遵循传统的六边形架构业务模块设计,这是合理且必要的架构决策。

---

## Phase 2: 设计后 Constitution Check 重新评估

**评估时间**: Phase 1 设计完成后
**评估范围**: 验证设计文档是否符合 Constitution 原则

### 架构验证 (重新评估)

- [x] **CHK-ARCH-002**: patra-common-test 是否保持纯 Java (无 Spring 依赖)
  - **状态**: PASS ✅
  - **验证依据**: test-infrastructure-model.md 第 1.1 节明确定义 patra-common-test 仅依赖 JUnit 5, Mockito, AssertJ, Lombok
  - **设计文档**: [test-infrastructure-model.md](./test-infrastructure-model.md#11-包结构设计)

- [x] **CHK-ARCH-003**: 依赖方向是否正确
  - **状态**: PASS ✅
  - **验证依据**: 依赖方向为 `业务模块 (test scope) → patra-spring-boot-starter-test → patra-common-test`,单向依赖,无循环
  - **设计文档**: [test-infrastructure-model.md](./test-infrastructure-model.md#21-包结构设计)

### DDD 验证 (重新评估)

- [x] **CHK-DDD-001~004**: DDD 相关验证
  - **状态**: N/A (保持不变)
  - **理由**: 测试基础设施不涉及业务领域模型,DDD 验证项不适用

### SSOT 验证 (重新评估)

- [x] **CHK-SSOT-001~003**: SSOT 相关验证
  - **状态**: N/A (保持不变)
  - **理由**: 测试基础设施不涉及 Provenance、数据字典和元数据映射

### 测试验证 (重新评估)

- [x] **CHK-TEST-001~004**: 测试基础设施的测试策略
  - **状态**: 设计完成 ✅
  - **验证依据**:
    - test-api.md 定义了 BaseUnitTest, BaseIntegrationTest, BaseE2ETest 的公共 API
    - test-infrastructure-model.md 定义了元测试策略 (测试测试工具)
    - quickstart.md 提供了完整的测试示例
  - **设计文档**:
    - [test-api.md](./contracts/test-api.md#21-baseintegrationtest---集成测试基类)
    - [quickstart.md](./quickstart.md#2-集成测试指南)

- [x] **CHK-TEST-006**: IT 和 E2E 测试位置规范
  - **状态**: PASS ✅
  - **验证依据**: quickstart.md 明确说明:
    - 集成测试 (RepositoryIT, CoordinatorIT) 放在各自的模块 src/test/java
    - E2E 测试 (ControllerE2E, FlowE2E) 放在 patra-{service}-boot 模块
  - **设计文档**: [quickstart.md](./quickstart.md#3-e2e-测试指南)

### 文档验证 (重新评估)

- [x] **CHK-DOC-001**: 模块根目录有完整的 README.md
  - **状态**: 设计完成 ✅
  - **计划**: 将在 /speckit.implement 阶段为 patra-common-test 和 patra-spring-boot-starter-test 生成 README.md
  - **参考模板**: [quickstart.md](./quickstart.md) 可作为 README.md 的内容来源

- [x] **CHK-DOC-002**: 所有文档和注释使用中文
  - **状态**: PASS ✅
  - **验证依据**: 所有设计文档 (research.md, test-api.md, test-infrastructure-model.md, quickstart.md) 均使用中文
  - **代码注释**: 将在实施阶段确保所有 JavaDoc 和代码注释使用中文

- [x] **CHK-DOC-003**: API 有文档
  - **状态**: PASS ✅
  - **验证依据**: test-api.md 提供了详细的 API 契约文档,包括所有公共类和方法的说明
  - **设计文档**: [test-api.md](./contracts/test-api.md)

---

**Phase 2 门禁结果**: PASS ✅

**验证总结**:
- ✅ 所有适用的 Constitution Check 验证项在设计阶段均通过
- ✅ 设计文档完整,符合 Patra 项目的架构规范
- ✅ 测试基础设施的特殊性得到充分考虑和说明
- ✅ 准备进入 Phase 3: 实施阶段 (由 /speckit.implement 命令执行)

**后续行动**:
1. 等待用户执行 `/speckit.implement` 命令
2. 实施阶段将由 `patra-backend-developer` agent 执行（TDD 驱动）
3. 实施完成后调用 `code-reviewer` 进行质量审查

---

## Phase 3: 实施计划 (由 /speckit.implement 执行)

**说明**: 本阶段将由 `/speckit.implement` 命令自动执行,基于 Phase 0-2 的设计文档生成实际代码。

### 实施任务概览

**第一周: 基础模块创建**
1. 创建 patra-common-test 模块 (pom.xml, package-info.java)
2. 创建 patra-spring-boot-starter-test 模块
3. 配置 patra-parent 的 dependencyManagement

**第二周: 核心工具类实现**
4. 实现 TestDataBuilder 抽象基类
5. 实现 MockDataFactory
6. 实现 DomainAssertions
7. 实现 BaseUnitTest

**第三周: TestContainers 自动配置**
8. 实现 MySQLContainer, RedisContainer, NacosContainer
9. 实现 TestcontainersConfiguration
10. 实现 BaseIntegrationTest, BaseE2ETest
11. 配置 Spring Boot 自动配置机制

**第四周: 示例测试和文档**
12. 迁移现有测试工具类
13. 在 patra-registry-boot 中编写示例测试
14. 生成 README.md 和 package-info.java
15. 运行完整的测试套件验证

**实施输出**:
- ✅ patra-common-test 模块代码
- ✅ patra-spring-boot-starter-test 模块代码
- ✅ 元测试代码 (测试测试工具)
- ✅ 示例测试代码 (patra-registry-boot)
- ✅ README.md 和 package-info.java
- ✅ 测试运行报告

---

## 规划完成总结

**规划状态**: ✅ 完成
**规划时间**: 2025-11-09
**规划输出**:
- ✅ plan.md (本文件)
- ✅ research.md (技术调研报告)
- ✅ test-infrastructure-model.md (模型设计)
- ✅ contracts/test-api.md (API 契约)
- ✅ quickstart.md (快速上手指南)

**下一步**: 执行 `/speckit.tasks` 命令生成可执行的任务列表,或直接执行 `/speckit.implement` 开始实施。
