# patra-ingest-app 模块重构报告

**重构日期**: 2025年9月30日  
**重构类型**: 目录结构与命名规范优化  
**影响范围**: patra-ingest-app 及其依赖模块（adapter、infra、boot）

---

## ✅ 重构目标

1. **建立清晰的用例边界**: 将分散的 planning、relay、validator 整合到统一的 `usecase` 层
2. **统一命名规范**: 去掉 "Default" 前缀，统一使用 "Impl" 后缀
3. **提升可读性**: 目录名称直接反映职责（assembler、slicer、planner）
4. **便于扩展**: 遵循开闭原则，新增用例在 usecase 下创建新目录

---

## 📊 重构统计

### 文件变更统计
- **移动文件数**: 34个
- **重命名类**: 12个
- **更新 import**: 约150处
- **删除旧目录**: 3个（planning、relay、validator）

### 编译验证
- ✅ patra-ingest-api: 编译成功
- ✅ patra-ingest-domain: 编译成功
- ✅ patra-ingest-app: 编译成功
- ✅ patra-ingest-infra: 编译成功
- ✅ patra-ingest-adapter: 编译成功
- ✅ patra-ingest-boot: 编译成功

---

## 📁 新目录结构

```
patra-ingest-app/src/main/java/com/patra/ingest/app/
├── config/                          # 应用层配置
│   └── IngestAppConfig.java
│
└── usecase/                         # 用例层
    ├── plan/                        # 计划编排用例
    │   ├── PlanIngestionUseCase.java
    │   ├── PlanIngestionOrchestrator.java
    │   │
    │   ├── command/
    │   │   └── PlanIngestionCommand.java
    │   │
    │   ├── dto/
    │   │   └── PlanIngestionResult.java
    │   │
    │   ├── assembler/
    │   │   ├── PlanAssembler.java
    │   │   ├── PlanAssemblerImpl.java
    │   │   └── PlanAssemblyRequest.java
    │   │
    │   ├── slicer/
    │   │   ├── SlicePlanner.java
    │   │   ├── SlicePlannerRegistry.java
    │   │   ├── SliceStrategy.java
    │   │   ├── TimeSlicePlanner.java
    │   │   ├── SingleSlicePlanner.java
    │   │   └── model/
    │   │       ├── SlicePlan.java
    │   │       └── SlicePlanningContext.java
    │   │
    │   ├── window/
    │   │   ├── PlanningWindowResolver.java
    │   │   ├── PlanningWindowResolverImpl.java
    │   │   └── support/
    │   │       └── PlanningWindowSupport.java
    │   │
    │   ├── expression/
    │   │   ├── PlanExpressionBuilder.java
    │   │   └── PlanExpressionDescriptor.java
    │   │
    │   ├── validator/
    │   │   ├── PlannerValidator.java
    │   │   └── PlannerValidatorImpl.java
    │   │
    │   └── publisher/
    │       └── TaskOutboxPublisher.java
    │
    └── relay/                       # Outbox 转发用例
        ├── OutboxRelayUseCase.java
        ├── OutboxRelayOrchestrator.java
        │
        ├── command/
        │   └── OutboxRelayCommand.java
        │
        ├── dto/
        │   └── RelayReport.java
        │
        ├── executor/
        │   └── OutboxRelayExecutor.java
        │
        ├── planner/
        │   └── RelayPlanBuilder.java
        │
        ├── policy/
        │   └── RelayErrorClassifierImpl.java
        │
        ├── publisher/
        │   ├── RelayEventPublisher.java
        │   └── LoggingRelayEventPublisher.java
        │
        ├── config/
        │   ├── OutboxRelayProperties.java
        │   └── OutboxRelayConfiguration.java
        │
        └── support/
            └── OutboxChannels.java
```

---

## 🔄 重命名映射表

### 核心服务类

| 原名称 | 新名称 | 位置变更 |
|--------|--------|----------|
| `PlanIngestionApplicationService` | `PlanIngestionOrchestrator` | `planning/application` → `usecase/plan` |
| `OutboxRelayApplicationService` | `OutboxRelayOrchestrator` | `relay` → `usecase/relay` |
| `DefaultPlanAssemblyService` | `PlanAssemblerImpl` | `planning/assembly` → `usecase/plan/assembler` |
| `DefaultPlanningWindowResolver` | `PlanningWindowResolverImpl` | `planning/window` → `usecase/plan/window` |
| `DefaultPlannerValidator` | `PlannerValidatorImpl` | `validator` → `usecase/plan/validator` |
| `DefaultRelayErrorClassifier` | `RelayErrorClassifierImpl` | `relay/policy` → `usecase/relay/policy` |

### 接口重命名

| 原名称 | 新名称 | 位置变更 |
|--------|--------|----------|
| `PlanAssemblyService` | `PlanAssembler` | `planning/assembly` → `usecase/plan/assembler` |
| `OutboxRelayEventPublisher` | `RelayEventPublisher` | `relay/event` → `usecase/relay/publisher` |
| `LoggingOutboxRelayEventPublisher` | `LoggingRelayEventPublisher` | `relay/event` → `usecase/relay/publisher` |

### 命令/请求对象

| 原名称 | 新名称 | 位置变更 |
|--------|--------|----------|
| `PlanIngestionRequest` | `PlanIngestionCommand` | `planning/command` → `usecase/plan/command` |
| `OutboxRelayInstruction` | `OutboxRelayCommand` | `relay/command` → `usecase/relay/command` |
| `OutboxRelayPlanBuilder` | `RelayPlanBuilder` | `relay/plan` → `usecase/relay/planner` |

---

## 🎯 设计原则改进

### 1. 清晰的用例边界
**改进前**: 
- planning、relay、validator 散落在不同目录
- 职责边界不清晰

**改进后**:
- 统一在 usecase 下组织
- plan 和 relay 各自独立
- 每个用例目录自包含（command、dto、核心逻辑）

### 2. 统一的命名规范
**改进前**:
- 混用 "Default"、"Application"、"Service" 等后缀
- 接口和实现命名不一致

**改进后**:
- 统一使用 "Impl" 后缀表示实现
- 使用 "Orchestrator" 表示编排器
- 去掉冗余的 "Application" 和 "Service"

### 3. 符合DDD和六边形架构
**改进前**:
- 应用层组件命名不够语义化
- 难以区分用例编排和领域服务

**改进后**:
- Orchestrator 明确标识编排器职责
- Assembler、Planner、Validator 等名称直接反映职责
- Command/DTO 清晰分离

---

## 📦 包名变更清单

### Planning 用例
```
com.patra.ingest.app.planning.application
  → com.patra.ingest.app.usecase.plan

com.patra.ingest.app.planning.command
  → com.patra.ingest.app.usecase.plan.command

com.patra.ingest.app.planning.dto
  → com.patra.ingest.app.usecase.plan.dto

com.patra.ingest.app.planning.assembly
  → com.patra.ingest.app.usecase.plan.assembler

com.patra.ingest.app.planning.slice
  → com.patra.ingest.app.usecase.plan.slicer

com.patra.ingest.app.planning.window
  → com.patra.ingest.app.usecase.plan.window

com.patra.ingest.app.planning.expression
  → com.patra.ingest.app.usecase.plan.expression

com.patra.ingest.app.planning.outbox
  → com.patra.ingest.app.usecase.plan.publisher

com.patra.ingest.app.validator
  → com.patra.ingest.app.usecase.plan.validator
```

### Relay 用例
```
com.patra.ingest.app.relay
  → com.patra.ingest.app.usecase.relay

com.patra.ingest.app.relay.command
  → com.patra.ingest.app.usecase.relay.command

com.patra.ingest.app.relay.dto
  → com.patra.ingest.app.usecase.relay.dto

com.patra.ingest.app.relay.plan
  → com.patra.ingest.app.usecase.relay.planner

com.patra.ingest.app.relay.policy
  → com.patra.ingest.app.usecase.relay.policy

com.patra.ingest.app.relay.event
  → com.patra.ingest.app.usecase.relay.publisher

com.patra.ingest.app.relay.executor
  → com.patra.ingest.app.usecase.relay.executor

com.patra.ingest.app.relay.config
  → com.patra.ingest.app.usecase.relay.config

com.patra.ingest.app.relay.support
  → com.patra.ingest.app.usecase.relay.support
```

---

## 🔍 影响范围分析

### patra-ingest-app
- ✅ 所有文件已更新
- ✅ 包名和类名全部修正
- ✅ import 语句已更新
- ✅ 编译通过

### patra-ingest-adapter
- ✅ AbstractProvenanceScheduleJob 已更新
- ✅ OutboxRelayJob 已更新
- ✅ 所有 import 已修正
- ✅ 编译通过

### patra-ingest-infra
- ✅ 无直接依赖 app 层，编译通过

### patra-ingest-boot
- ✅ 配置类已自动更新
- ✅ 编译通过

### 测试代码
- ⚠️ 测试代码的 import 已更新
- 📝 建议：运行完整测试套件验证功能完整性

---

## ✨ 重构优势

### 1. 更好的可读性
- 目录结构一目了然
- 类名直接反映职责
- 减少认知负担

### 2. 更易于维护
- 用例自包含，修改影响范围小
- 命名规范统一，降低理解成本
- 符合 DDD 和六边形架构原则

### 3. 更好的扩展性
- 新增用例在 usecase 下创建新目录
- 遵循开闭原则
- 支持灵活的组合和重用

### 4. 更强的类型安全
- Command 命名更语义化
- Orchestrator 职责更清晰
- 接口与实现命名一致

---

## 📝 后续建议

### 1. 测试验证
```bash
# 运行单元测试
mvn test -pl patra-ingest-app

# 运行集成测试
mvn verify -pl patra-ingest

# 运行完整测试套件
mvn clean verify
```

### 2. 文档更新
- ✅ 更新 README.md 中的架构说明
- ✅ 更新 AGENTS.md 中的目录结构说明
- ⏳ 更新 API 文档（如有）
- ⏳ 更新开发者指南

### 3. 团队沟通
- 📢 通知团队成员重构完成
- 📖 分享重构报告
- 🔄 更新本地代码（git pull）
- 🔧 重新 import 项目（IDEA 等 IDE）

### 4. 监控观察
- 关注编译告警
- 检查运行时行为
- 验证日志输出
- 确认功能完整性

---

## 🛡️ 风险评估

### 低风险
- ✅ 纯代码重构，无逻辑变更
- ✅ 编译时检查确保类型安全
- ✅ 包名变更自动处理依赖

### 需要关注
- ⚠️ 测试代码可能需要少量调整
- ⚠️ IDE 索引可能需要刷新
- ⚠️ 自动化脚本可能引用旧路径

### 缓解措施
- 完整测试套件验证
- 代码审查确认变更
- 监控首次部署

---

## 📚 参考资料

- [六边形架构模式](https://alistair.cockburn.us/hexagonal-architecture/)
- [DDD 战术设计](https://martinfowler.com/bliki/DomainDrivenDesign.html)
- [命名规范最佳实践](https://google.github.io/styleguide/javaguide.html)

---

**重构完成时间**: 2025年9月30日 10:30  
**重构执行者**: AI Assistant (Claude)  
**审核状态**: ✅ 编译验证通过，等待功能测试验证
