---
name: java-hexagonal-architecture
description: 六边形架构和DDD专家。设计领域模型、评审架构方案、技术选型决策。用于架构设计、领域建模、方案评审、技术选型、依赖管理、模块划分、数据库设计。关键词：六边形架构、DDD、Clean Architecture、技术方案、架构决策、微服务架构。
allowed-tools: Read, Grep, Glob, Skill, mcp__sequential-thinking__sequentialthinking, mcp__serena__get_symbols_overview, mcp__serena__find_symbol, mcp__serena__find_referencing_symbols, mcp__context7__resolve-library-id, mcp__context7__get-library-docs, WebSearch
---

# 六边形架构和 DDD 专家

## 核心原则
**六边形架构 + DDD**：领域驱动设计，清晰的层次边界，纯粹的领域逻辑，依赖倒置。

## 架构决策流程

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

## 层次职责与依赖规则

| 层 | 职责 | 关键类型 | 依赖规则 |
|---|------|---------|----------|
| **Adapter** | 接收外部触发 | Controller, Job, MessageListener | → Application |
| **Application** | 编排用例，管理事务 | Orchestrator, Coordinator | → Domain |
| **Domain** | 业务逻辑，纯 Java | Entity, Aggregate, Port | 无框架依赖 |
| **Infrastructure** | 实现端口，数据访问 | RepositoryImpl, DO, Converter | → Domain |

## 架构铁律

1. **Domain 层必须是纯 Java** - 仅允许 Lombok、Hutool、patra-common
2. **依赖方向必须向内** - Adapter → App → Domain ← Infra
3. **事务边界在 Orchestrator** - @Transactional 仅在应用层
4. **永不暴露 DO** - DO 实体不能离开基础设施层

## 模块结构规范

```
patra-{service}/
├── -api/         # 外部契约 (DTO, 错误码)
├── -domain/      # 纯 Java (Entity, Port, Event)
├── -app/         # 应用层 (Orchestrator, Coordinator)
├── -infra/       # 基础设施 (RepositoryImpl, DO, Converter)
├── -adapter/     # 适配器 (Controller, Job, MessageListener)
└── -boot/        # Spring Boot 主应用
```

## 架构评审检查点

### 领域建模检查
- [ ] 聚合边界是否合理？
- [ ] 实体间关系是否正确？
- [ ] 领域事件是否完整？
- [ ] Port 接口是否抽象得当？

### 依赖方向检查
- [ ] Domain 层是否纯净（无框架依赖）？
- [ ] 各层依赖方向是否正确？
- [ ] 是否存在循环依赖？
- [ ] DO 是否被正确封装？

### 技术选型检查
- [ ] 技术栈是否与现有系统兼容？
- [ ] 是否有更好的替代方案？
- [ ] 性能影响是否可接受？
- [ ] 维护成本是否合理？

## 常见架构模式

### 聚合设计模式
```java
// Aggregate Root
public class Order {
    private OrderId id;
    private List<OrderItem> items;  // 聚合内实体
    private OrderStatus status;

    // 业务不变量
    public void addItem(Product product, int quantity) {
        // 确保业务规则
        validateCanAddItem();
        items.add(new OrderItem(product, quantity));
        recalculateTotal();
    }
}
```

### Port-Adapter 模式
```java
// Domain Port (领域层)
public interface OrderPort {
    void save(Order order);
    Optional<Order> findById(OrderId id);
}

// Infrastructure Adapter (基础设施层)
@Repository
public class OrderRepositoryImpl implements OrderPort {
    // MyBatis-Plus 实现
}
```

## 决策指南

**Q: 这是哪一层的职责？**
```
业务规则？→ Domain 层
协调用例？→ Application 层
外部触发？→ Adapter 层
数据访问？→ Infrastructure 层
```

**Q: 如何处理跨聚合事务？**
```
使用 Saga 模式或领域事件
避免分布式事务
考虑最终一致性
```

## 详细资源

需要深入了解时，查看以下资源文件：

- [architecture-overview.md](resources/architecture-overview.md) - 六边形架构详解
- [dependency-rules.md](resources/dependency-rules.md) - 层依赖规则
- [domain-modeling-patterns.md](resources/domain-modeling-patterns.md) - 领域建模
- [architecture-review-checklist.md](resources/architecture-review-checklist.md) - 架构评审清单
- [complete-examples.md](resources/complete-examples.md) - 完整示例