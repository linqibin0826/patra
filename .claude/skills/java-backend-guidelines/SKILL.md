---
name: java-backend-guidelines
description: >
  Java 后端开发指南：六边形架构 + DDD 模式。
  当你处理以下任务时使用：创建/修改 Controller、Orchestrator、Coordinator、Repository、Domain Entity、Aggregate、
  定时任务 (XXL-Job)、事件处理器、MyBatis-Plus 映射、MapStruct 转换器、事务管理 (@Transactional)、
  Outbox 模式、领域事件。
  关键词：REST API、Spring Boot、微服务架构、patra-ingest、patra-registry、批处理、数据采集、
  ProblemDetail 错误处理、乐观锁、幂等性、性能优化、单元测试、集成测试、ArchUnit。
allowed-tools: Read, Edit, Write, Grep, Glob, Bash, NotebookEdit, Task
---

# Java 后端开发指南 (Patra)

## 核心原则
**六边形架构 + DDD**：领域驱动设计，清晰的层次边界，纯粹的领域逻辑，依赖倒置。

## 快速参考

### 🎯 架构决策流程
遇到新功能需求时，按以下顺序思考：

1. **触发来源** → 决定适配器类型
   - REST API → Controller
   - 定时任务 → XXL-Job
   - 消息队列 → MessageListener

2. **用例编排** → 在应用层组织
   - 创建 Orchestrator（主编排者）
   - 必要时添加 Coordinator（关注点分离）

3. **业务逻辑** → 在领域层建模
   - 设计 Aggregate/Entity
   - 定义 Port 接口
   - 发布 Domain Event

4. **数据持久化** → 在基础设施层实现
   - 创建 DO 实体
   - 实现 RepositoryImpl
   - 使用 MapStruct 转换

---

### ⚡ 层次职责

| 层 | 职责 | 关键类型 | 依赖规则 |
|---|------|---------|----------|
| **Adapter** | 接收外部触发 | Controller, Job, MessageListener | → Application |
| **Application** | 编排用例，管理事务 | Orchestrator, Coordinator | → Domain |
| **Domain** | 业务逻辑，纯 Java | Entity, Aggregate, Port | 无框架依赖 |
| **Infrastructure** | 实现端口，数据访问 | RepositoryImpl, DO, Converter | → Domain |

### 🚫 铁律
1. **Domain 层必须是纯 Java** - 仅允许 Lombok、Hutool、patra-common
2. **依赖方向必须向内** - Adapter → App → Domain ← Infra
3. **事务边界在 Orchestrator** - @Transactional 仅在应用层
4. **永不暴露 DO** - DO 实体不能离开基础设施层

## 快速示例

### Controller → Orchestrator → Domain → Repository

```java
// 1. Adapter Layer: Controller
@RestController
@RequestMapping("/api/v1/provenances")
public class ProvenanceController {
    private final ProvenanceOrchestrator orchestrator;

    @PostMapping
    public ResponseEntity<ProvenanceResponse> create(
        @Valid @RequestBody CreateProvenanceCommand command
    ) {
        var result = orchestrator.create(command);
        return ResponseEntity.ok(ProvenanceResponse.from(result));
    }
}

// 2. Application Layer: Orchestrator
@Service
@RequiredArgsConstructor
public class ProvenanceOrchestrator {
    private final ProvenancePort provenancePort;

    @Transactional  // 事务边界
    public ProvenanceResult create(CreateProvenanceCommand command) {
        // 组装领域对象
        var provenance = Provenance.create(command.getName());
        // 业务逻辑在领域层
        provenance.validate();
        // 持久化
        provenancePort.save(provenance);
        return ProvenanceResult.from(provenance);
    }
}

// 3. Domain Layer: Pure Java
@Data
public class Provenance {
    private ProvenanceId id;
    private String name;

    public void validate() {
        if (name.length() < 3) {
            throw new ProvenanceException("Name too short");
        }
    }
}

// 4. Infrastructure Layer: Repository Implementation
@Repository
public class ProvenanceRepositoryImpl implements ProvenancePort {
    private final ProvenanceMapper mapper;
    private final ProvenanceConverter converter;

    @Override
    public void save(Provenance provenance) {
        ProvenanceDO dataObject = converter.toDO(provenance);
        mapper.insert(dataObject);
    }
}

```

## 项目结构 & 命名约定

### 模块结构
```
patra-{service}/
├── -api/         # 外部契约 (DTO, 错误码)
├── -domain/      # 纯 Java (Entity, Port, Event)
├── -app/         # 应用层 (Orchestrator, Coordinator)
├── -infra/       # 基础设施 (RepositoryImpl, DO, Converter)
├── -adapter/     # 适配器 (Controller, Job, MessageListener)
└── -boot/        # Spring Boot 主应用
```

### 命名模式
| 类型 | 后缀 | 示例 |
|-----|------|------|
| REST 控制器 | Controller | ProvenanceController |
| 编排者 | Orchestrator | PlanIngestionOrchestrator |
| 协调者 | Coordinator | RelayCoordinator |
| 领域实体 | 无后缀 | BatchPlan, Provenance |
| 端口接口 | Port | ProvenancePort |
| 仓储实现 | RepositoryImpl | ProvenanceRepositoryImpl |
| 数据对象 | DO | ProvenanceDO |
| 转换器 | Converter | ProvenanceConverter |
| 领域事件 | Event | PlanCreatedEvent |



## 常见反模式

### ❌ 要避免的错误

| 错误类型 | 错误示例 | 正确做法 |
|---------|----------|----------|
| **领域层违规** | 在领域层使用 @Service/@Autowired | 领域层必须是纯 Java |
| **依赖方向错误** | Domain 引用 Infrastructure | Infrastructure 引用 Domain |
| **业务逻辑错位** | Controller 包含业务验证 | 业务逻辑放在 Domain 层 |
| **暴露 DO** | Port 返回 ProvenanceDO | Port 返回领域实体 |

## 决策快速指南

**Q: 代码应该放在哪一层？**

```
业务规则？→ Domain 层
协调用例？→ Application 层 (Orchestrator)
外部触发？→ Adapter 层 (Controller/Job/MessageListener)
数据访问？→ Infrastructure 层 (RepositoryImpl)
```

**Q: 遇到问题找哪个资源？**

```
架构设计 → architecture-overview.md
依赖规则 → dependency-rules.md
事务处理 → transaction-error-handling.md
测试策略 → testing-guide.md
完整示例 → complete-examples.md
```

## 详细资源

需要深入了解时，查看以下资源文件：

- [architecture-overview.md](resources/architecture-overview.md) - 六边形架构详解
- [dependency-rules.md](resources/dependency-rules.md) - 层依赖规则
- [adapter-layer-patterns.md](resources/adapter-layer-patterns.md) - REST/Job/Consumer模式
- [orchestrator-coordinator-patterns.md](resources/orchestrator-coordinator-patterns.md) - 编排模式
- [domain-modeling-patterns.md](resources/domain-modeling-patterns.md) - 领域建模
- [mybatis-plus-patterns.md](resources/mybatis-plus-patterns.md) - 数据访问模式
- [transaction-error-handling.md](resources/transaction-error-handling.md) - 事务与错误处理
- [outbox-pattern.md](resources/outbox-pattern.md) - Outbox 模式
- [event-driven-architecture.md](resources/event-driven-architecture.md) - 事件驱动架构
- [testing-guide.md](resources/testing-guide.md) - 测试指南
- [architecture-review-checklist.md](resources/architecture-review-checklist.md) - 架构评审检查清单
- [documentation-templates.md](resources/documentation-templates.md) - 文档模板库
- [complete-examples.md](resources/complete-examples.md) - 完整示例

