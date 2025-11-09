---

description: "特性实施的任务列表模板"
---

# 任务: [特性名称]

**输入**: 来自 `/specs/[###-feature-name]/` 的设计文档
**前置条件**: plan.md（必需）、spec.md（用户故事所需）、research.md、data-model.md、contracts/

**测试**: 以下示例包含测试任务。测试是可选的 - 仅在特性规格说明中明确请求时才包含它们。

**组织**: 任务按用户故事分组，以实现每个故事的独立实施和测试。

## 格式: `[ID] [P?] [Layer?] [Story] 描述`

- **[P]**: 可并行运行（不同文件，无依赖）
- **[Layer]**: 六边形架构层标签（可选但推荐）
  - `[Domain]`: Domain 层（纯 Java 领域模型）
  - `[App]`: Application 层（Orchestrator/Coordinator）
  - `[Infra]`: Infrastructure 层（Repository 实现/MyBatis Mapper）
  - `[Adapter]`: Adapter 层（Controller/Listener/Job）
  - `[API]`: API/Contract 层（DTO/Facade）
- **[Story]**: 此任务属于哪个用户故事（例如，US1、US2、US3）
- 描述中包含确切的文件路径（使用 Patra 模块路径：`patra-{service}-{layer}/src/...`）

## 路径约定

**Patra 项目使用六边形架构的多模块结构**：

- **Domain 层**: `patra-{service}-domain/src/main/java/com/patra/{service}/domain/`
- **Application 层**: `patra-{service}-app/src/main/java/com/patra/{service}/app/`
- **Infrastructure 层**: `patra-{service}-infra/src/main/java/com/patra/{service}/infra/`
- **Adapter 层**: `patra-{service}-adapter/src/main/java/com/patra/{service}/adapter/`
- **API 层**: `patra-{service}-api/src/main/java/com/patra/{service}/api/`
- **测试**:
  - 单元测试: `src/test/java/`（与源码同结构）
  - IT 测试: `patra-{service}-infra/src/test/java/`（`*IT.java`）
  - E2E 测试: `patra-{service}-boot/src/test/java/`（`*E2ETest.java`）

<!--
  ============================================================================
  重要提示：以下任务是仅用于说明的示例任务。

  /speckit.tasks 命令必须根据以下内容替换这些任务：
  - 来自 spec.md 的用户故事（及其优先级 P1、P2、P3...）
  - 来自 plan.md 的特性需求
  - 来自 data-model.md 的实体
  - 来自 contracts/ 的端点

  任务必须按用户故事组织，以便每个故事可以：
  - 独立实施
  - 独立测试
  - 作为 MVP 增量交付

  不要在生成的 tasks.md 文件中保留这些示例任务。
  ============================================================================
-->

## 阶段 1：设置（共享基础设施）

**目的**: 项目初始化和基本结构

- [ ] T001 按实现计划创建项目结构
- [ ] T002 使用 [framework] 依赖初始化 [language] 项目
- [ ] T003 [P] 配置代码检查和格式化工具

---

## 阶段 2：基础（阻塞前置条件）

**目的**: 在任何用户故事可以实施之前必须完成的核心基础设施

**⚠️ 关键**: 在此阶段完成之前，不能开始任何用户故事工作

基础任务示例（根据你的项目调整）：

- [ ] T004 设置数据库架构和迁移框架
- [ ] T005 [P] 实现认证/授权框架
- [ ] T006 [P] 设置 API 路由和中间件结构
- [ ] T007 创建所有故事依赖的基础模型/实体
- [ ] T008 配置错误处理和日志基础设施
- [ ] T009 设置环境配置管理

**检查点**: 基础就绪 - 现在可以开始并行实施用户故事

---

## 阶段 3：用户故事 1 - [标题]（优先级: P1）🎯 MVP

**目标**: [此故事交付内容的简要描述]

**独立测试**: [如何独立验证此故事有效]

### 用户故事 1 的测试（可选 - 仅在请求测试时）⚠️

> **注意：首先编写这些测试，确保在实施之前它们失败**

- [ ] T010 [P] [US1] 在 tests/contract/test_[name].py 中为 [endpoint] 编写契约测试
- [ ] T011 [P] [US1] 在 tests/integration/test_[name].py 中为 [user journey] 编写集成测试

### 用户故事 1 的实施

**按六边形架构顺序执行: Domain → App → Infra → Adapter**

- [ ] T012 [P] [Domain] [US1] 定义 [AggregateRoot] 聚合根 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/model/[AggregateRoot].java
- [ ] T013 [P] [Domain] [US1] 定义 [ValueObject] 值对象 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/model/[ValueObject].java
- [ ] T014 [P] [Domain] [US1] 定义 [DomainEvent] 领域事件 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/event/[DomainEvent].java
- [ ] T015 [Domain] [US1] 定义 [Repository] 仓储接口 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/repository/[Repository].java
- [ ] T016 [App] [US1] 实现 [Coordinator] 协调器 in patra-[service]-app/src/main/java/com/patra/[service]/app/coordinator/[Coordinator].java（依赖 T012-T015）
- [ ] T017 [P] [Infra] [US1] 实现 [RepositoryImpl] 仓储实现 in patra-[service]-infra/src/main/java/com/patra/[service]/infra/repository/[RepositoryImpl].java
- [ ] T018 [P] [Infra] [US1] 创建 MyBatis Mapper in patra-[service]-infra/src/main/java/com/patra/[service]/infra/repository/mapper/[EntityMapper].java
- [ ] T019 [Adapter] [US1] 实现 REST API Controller in patra-[service]-adapter/src/main/java/com/patra/[service]/adapter/controller/[Controller].java（依赖 T016）

**检查点**: 此时，用户故事 1 应该完全可用且可以独立测试

---

## 阶段 4：用户故事 2 - [标题]（优先级: P2）

**目标**: [此故事交付内容的简要描述]

**独立测试**: [如何独立验证此故事有效]

### 用户故事 2 的测试（可选 - 仅在请求测试时）⚠️

- [ ] T018 [P] [US2] 在 tests/contract/test_[name].py 中为 [endpoint] 编写契约测试
- [ ] T019 [P] [US2] 在 tests/integration/test_[name].py 中为 [user journey] 编写集成测试

### 用户故事 2 的实施

**按六边形架构顺序执行: Domain → App → Infra → Adapter**

- [ ] T020 [P] [Domain] [US2] 定义 [AggregateRoot] 聚合根 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/model/[AggregateRoot].java
- [ ] T021 [P] [Domain] [US2] 定义 [ValueObject] 值对象 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/model/[ValueObject].java
- [ ] T022 [App] [US2] 实现 [Orchestrator] 编排器 in patra-[service]-app/src/main/java/com/patra/[service]/app/orchestrator/[Orchestrator].java（依赖 T020-T021）
- [ ] T023 [P] [Infra] [US2] 实现 [RepositoryImpl] 仓储实现 in patra-[service]-infra/src/main/java/com/patra/[service]/infra/repository/[RepositoryImpl].java
- [ ] T024 [Adapter] [US2] 实现 REST API Controller in patra-[service]-adapter/src/main/java/com/patra/[service]/adapter/controller/[Controller].java（依赖 T022）
- [ ] T025 [US2] 如需要，与用户故事 1 组件集成（跨聚合调用需通过 Application 层）

**检查点**: 此时，用户故事 1 和 2 都应该独立工作

---

## 阶段 5：用户故事 3 - [标题]（优先级: P3）

**目标**: [此故事交付内容的简要描述]

**独立测试**: [如何独立验证此故事有效]

### 用户故事 3 的测试（可选 - 仅在请求测试时）⚠️

- [ ] T024 [P] [US3] 在 tests/contract/test_[name].py 中为 [endpoint] 编写契约测试
- [ ] T025 [P] [US3] 在 tests/integration/test_[name].py 中为 [user journey] 编写集成测试

### 用户故事 3 的实施

**按六边形架构顺序执行: Domain → App → Infra → Adapter**

- [ ] T026 [P] [Domain] [US3] 定义 [AggregateRoot] 聚合根 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/model/[AggregateRoot].java
- [ ] T027 [P] [Domain] [US3] 定义 [ValueObject] 值对象 in patra-[service]-domain/src/main/java/com/patra/[service]/domain/model/[ValueObject].java
- [ ] T028 [App] [US3] 实现 [Coordinator] 协调器 in patra-[service]-app/src/main/java/com/patra/[service]/app/coordinator/[Coordinator].java（依赖 T026-T027）
- [ ] T029 [P] [Infra] [US3] 实现 [RepositoryImpl] 仓储实现 in patra-[service]-infra/src/main/java/com/patra/[service]/infra/repository/[RepositoryImpl].java
- [ ] T030 [Adapter] [US3] 实现 REST API Controller in patra-[service]-adapter/src/main/java/com/patra/[service]/adapter/controller/[Controller].java（依赖 T028）

**检查点**: 所有用户故事现在都应该独立可用

---

[根据需要添加更多用户故事阶段，遵循相同模式]

---

## 阶段 N：润色和跨领域关注点

**目的**: 影响多个用户故事的改进

- [ ] TXXX [P] 在 docs/ 中更新文档
- [ ] TXXX 代码清理和重构
- [ ] TXXX 跨所有故事的性能优化
- [ ] TXXX [P] 额外的单元测试（如果请求）in tests/unit/
- [ ] TXXX 安全加固
- [ ] TXXX 运行 quickstart.md 验证

---

## 依赖和执行顺序

### 阶段依赖

- **设置（阶段 1）**: 无依赖 - 可以立即开始
- **基础（阶段 2）**: 依赖于设置完成 - 阻塞所有用户故事
- **用户故事（阶段 3+）**: 都依赖于基础阶段完成
  - 如果有人员配置，用户故事可以并行进行
  - 或按优先级顺序依次进行（P1 → P2 → P3）
- **润色（最后阶段）**: 依赖于所有期望的用户故事完成

### 用户故事依赖

- **用户故事 1（P1）**: 可以在基础（阶段 2）之后开始 - 不依赖其他故事
- **用户故事 2（P2）**: 可以在基础（阶段 2）之后开始 - 可能与 US1 集成但应该可以独立测试
- **用户故事 3（P3）**: 可以在基础（阶段 2）之后开始 - 可能与 US1/US2 集成但应该可以独立测试

### 在每个用户故事内

- 测试（如果包含）必须在实施之前编写并失败
- 模型在服务之前
- 服务在端点之前
- 核心实施在集成之前
- 故事完成后再转到下一个优先级

### 并行机会

- 所有标记 [P] 的设置任务可以并行运行
- 所有标记 [P] 的基础任务可以并行运行（在阶段 2 内）
- 一旦基础阶段完成，所有用户故事可以并行开始（如果团队容量允许）
- 用户故事的所有标记 [P] 的测试可以并行运行
- 故事内标记 [P] 的模型可以并行运行
- 不同的用户故事可以由不同的团队成员并行工作

---

## 并行示例：用户故事 1

```bash
# 同时启动用户故事 1 的所有 Domain 层任务（纯 Java，无依赖）:
Task: "定义 Article 聚合根 in patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/Article.java"
Task: "定义 ArticleId 值对象 in patra-ingest-domain/src/main/java/com/patra/ingest/domain/model/ArticleId.java"
Task: "定义 ArticleCreated 领域事件 in patra-ingest-domain/src/main/java/com/patra/ingest/domain/event/ArticleCreated.java"

# 同时启动用户故事 1 的所有 Infra 层任务（不同文件，可并行）:
Task: "实现 ArticleRepositoryImpl in patra-ingest-infra/src/main/java/com/patra/ingest/infra/repository/ArticleRepositoryImpl.java"
Task: "创建 ArticleMapper in patra-ingest-infra/src/main/java/com/patra/ingest/infra/repository/mapper/ArticleMapper.java"
```

---

## 实施策略

### MVP 优先（仅用户故事 1）

1. 完成阶段 1：设置
2. 完成阶段 2：基础（关键 - 阻塞所有故事）
3. 完成阶段 3：用户故事 1
4. **停止并验证**: 独立测试用户故事 1
5. 如果准备好则部署/演示

### 增量交付

1. 完成设置 + 基础 → 基础就绪
2. 添加用户故事 1 → 独立测试 → 部署/演示（MVP！）
3. 添加用户故事 2 → 独立测试 → 部署/演示
4. 添加用户故事 3 → 独立测试 → 部署/演示
5. 每个故事都增加价值而不破坏以前的故事

### 并行团队策略

对于多个开发者：

1. 团队一起完成设置 + 基础
2. 一旦基础完成：
   - 开发者 A：用户故事 1
   - 开发者 B：用户故事 2
   - 开发者 C：用户故事 3
3. 故事独立完成和集成

---

## 注意事项

- [P] 任务 = 不同文件，无依赖
- [Story] 标签将任务映射到特定用户故事以实现可追溯性
- 每个用户故事应该可以独立完成和测试
- 在实施之前验证测试失败
- 在每个任务或逻辑组之后提交
- 在任何检查点停止以独立验证故事
- 避免：模糊任务、同一文件冲突、破坏独立性的跨故事依赖
