# 聚合设计规则

本文档基于 Vaughn Vernon 的《Implementing Domain-Driven Design》（红皮书）总结聚合设计的核心规则。

## Vaughn Vernon 的聚合设计四规则

### 规则 1：在一致性边界内建模真正的不变量

**核心问题**：这个业务规则是否**必须立即**保持一致？

**判断标准**：
- 如果规则在几秒/几分钟后验证，业务是否能接受？
- 违反规则是否会导致不可恢复的业务损失？

**示例**：

| 场景 | 需要立即一致？ | 理由 |
|------|--------------|------|
| 订单总金额 = Σ 订单项金额 | ✅ 是 | 金额计算错误会导致财务问题 |
| 库存扣减 | ✅ 是 | 超卖会导致履约失败 |
| 用户积分更新 | ❌ 否 | 延迟几秒更新可接受 |
| 文章浏览量统计 | ❌ 否 | 统计数据可以最终一致 |

**检查清单**：
- [ ] 列出所有业务规则
- [ ] 标记哪些规则必须立即一致
- [ ] 只有必须立即一致的规则才放入同一聚合

### 规则 2：设计小聚合

**原则**：聚合越小越好，除非有强一致性需求。

**为什么要小聚合**：
1. **并发性**：大聚合更容易发生乐观锁冲突
2. **性能**：加载整个聚合的成本更低
3. **可维护性**：职责更清晰，变更影响更小
4. **事务边界**：事务越小，锁定时间越短

**设计检查清单**：
- [ ] 聚合内实体数量 < 5？
- [ ] 聚合加载时间 < 100ms？
- [ ] 并发修改概率低？
- [ ] 每个实体都有强一致性需求？

**反模式警示**：

```
❌ 错误：大聚合
Order
├── OrderItems (100+)
├── Payments
├── Shipments
├── Reviews
└── RefundRequests

✅ 正确：拆分为多个小聚合
Order (聚合根)
├── OrderItems (有限数量)

Payment (独立聚合)
Shipment (独立聚合)
Review (独立聚合)
RefundRequest (独立聚合)
```

### 规则 3：通过唯一标识引用其他聚合

**原则**：聚合之间只能通过 ID 引用，禁止对象引用。

**为什么**：
1. **边界清晰**：防止跨聚合事务
2. **加载解耦**：按需加载关联聚合
3. **存储灵活**：关联聚合可以在不同存储

**正确做法**：

```java
// ✅ 正确：通过 ID 引用
public class Order {
    private Long id;
    private Long customerId;  // 引用 Customer 聚合的 ID
    private Long venueId;     // 引用 Venue 聚合的 ID
}

// ❌ 错误：对象引用
public class Order {
    private Long id;
    private Customer customer;  // 直接引用 Customer 对象
    private Venue venue;        // 直接引用 Venue 对象
}
```

**应用层编排**：

```java
// 需要关联数据时，在 Application 层组装
public OrderDetailResult getOrderDetail(Long orderId) {
    Order order = orderRepository.findById(orderId);
    Customer customer = customerRepository.findById(order.getCustomerId());
    Venue venue = venueRepository.findById(order.getVenueId());
    return new OrderDetailResult(order, customer, venue);
}
```

### 规则 4：使用最终一致性更新其他聚合

**原则**：跨聚合更新使用领域事件 + 最终一致性。

**模式**：

```
聚合 A 变更 → 发布领域事件 → 异步处理 → 更新聚合 B
```

**示例**：

```java
// 聚合 A：订单完成
public class Order {
    public void complete() {
        this.status = OrderStatus.COMPLETED;
        registerEvent(new OrderCompletedEvent(this.id, this.customerId));
    }
}

// 事件处理器：更新聚合 B（客户积分）
@EventListener
public void handleOrderCompleted(OrderCompletedEvent event) {
    Customer customer = customerRepository.findById(event.customerId());
    customer.addPoints(calculatePoints(event));
    customerRepository.save(customer);
}
```

**何时可以同步更新**：
- 两个聚合在**同一限界上下文**内
- 业务容忍**部分失败**（需要补偿机制）
- 性能要求不高

## 何时可以打破规则

### 打破规则 2（大聚合）的场景

1. **复杂不变量**：多个实体之间有复杂的一致性规则
2. **原子操作**：业务要求多个实体必须同时创建/更新
3. **历史原因**：遗留系统迁移时的临时妥协

**权衡分析**：

| 大聚合代价 | 收益 |
|-----------|------|
| 并发冲突增加 | 强一致性保证 |
| 加载性能下降 | 简化编程模型 |
| 事务时间变长 | 避免分布式事务 |

### 打破规则 3（ID 引用）的场景

1. **值对象共享**：如 Address、Money 等通用值对象
2. **聚合内实体**：聚合内实体可以持有同聚合其他实体的引用
3. **只读场景**：查询优化时可以预加载关联对象

## 聚合边界决策流程

```
1. 识别核心业务概念
   ↓
2. 列出所有业务不变量
   ↓
3. 标记必须立即一致的规则
   ↓
4. 将强一致性规则相关的实体划入同一聚合
   ↓
5. 其他实体作为独立聚合，通过 ID 引用
   ↓
6. 定义领域事件处理跨聚合协作
```

## 医学领域示例

### 文献聚合边界分析

| 候选实体 | 是否需要强一致性？ | 决策 |
|----------|------------------|------|
| Publication 基本信息 | ✅ 核心 | 聚合根 |
| PublicationIdentifier | ✅ 标识符必须与文献一致 | 聚合内实体 |
| Author 列表 | ⚠️ 作者顺序有业务含义 | 聚合内值对象列表 |
| MeSH 标引 | ❌ 可独立更新 | 通过 ID 引用 |
| 引用关系 | ❌ 异步计算 | 独立聚合 |
| 全文内容 | ❌ 大对象，独立存储 | 独立聚合 |

### 期刊聚合边界分析

| 候选实体 | 是否需要强一致性？ | 决策 |
|----------|------------------|------|
| Venue 基本信息 | ✅ 核心 | 聚合根 |
| VenueIdentifier | ✅ ISSN/NLM ID 必须与期刊一致 | 聚合内实体 |
| VenuePublicationStats | ✅ 年度指标与期刊绑定 | 聚合内实体 |
| VenueRating (JCR/CAS) | ❌ 有独立来源和更新周期 | 独立聚合 |
| Publication 列表 | ❌ 数量巨大 | 通过 venueId 反向查询 |
