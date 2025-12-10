# 聚合设计规则

本文档基于 Vaughn Vernon 的《Implementing Domain-Driven Design》（红皮书）总结聚合设计的核心规则，并扩展了值对象归属判断、逻辑分组重构和性能优化策略。

## 核心矛盾：业务完整性 vs 系统性能

聚合设计的核心挑战是平衡：
- **业务完整性（Consistency）**：确保不变量规则在任何时候都成立
- **系统性能（Performance）**：避免加载过多数据、减少事务锁定时间

**解决路径**：
1. 通过**不变性检查**判断值对象是否真的属于该聚合
2. 通过**逻辑分组**减少聚合根的认知负担
3. 通过**技术手段**（懒加载/CQRS）解决"大聚合"性能问题

---

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

---

## 值对象归属判断：不变性检查（Invariant Check）

### 核心问题

> **当我修改"值对象 A"时，是否需要立即检查聚合根或其他属性的状态，以确保业务合法？**

这是判断值对象是否应该属于某个聚合的**黄金法则**。

### 三种场景分类

#### Case 1：强一致性（必须在一起）

**特征**：修改该值对象时，必须立即验证/更新聚合的其他状态。

**示例**：
- `OrderItems`（订单项）：删除一个 Item，必须立即重算 `TotalAmount`
- `VenueIdentifier`（期刊标识符）：ISSN 变更必须与 Venue 基本信息保持一致

**结论**：**必须作为聚合根的属性，由同一个 Repository 管理。**

```java
// ✅ 正确：OrderItem 属于 Order 聚合
public class Order {
    private List<OrderItem> items;
    private Money totalAmount;

    public void removeItem(Long itemId) {
        items.removeIf(i -> i.getId().equals(itemId));
        this.totalAmount = calculateTotal();  // 必须立即重算
    }
}
```

#### Case 2：弱一致性（可以分离）

**特征**：修改该数据不影响聚合根或其他属性的业务合法性。

**示例**：
- `ProductReviews`（商品评价）：新增评价不影响商品的价格、库存、状态
- `VenueRating`（期刊评级）：JCR 分区更新不影响期刊基本信息的合法性

**结论**：**应该拆分为独立的聚合，拥有自己的 Repository。**

```java
// ✅ 正确：Review 是独立聚合，通过 productId 关联
public class Review {
    private Long id;
    private Long productId;  // 通过 ID 引用 Product
    private String content;
    private Integer rating;
}
```

#### Case 3：纯信息流（边缘数据）

**特征**：这些数据仅用于展示，修改它们通常不涉及复杂的业务校验。

**示例**：
- `ChangeLogs`（操作日志）：仅记录历史，不参与业务校验
- `RichTextDescription`（富文本描述）：纯展示内容

**结论**：**属于聚合的一部分，但是"边缘数据"，可以懒加载。**

### 不变性检查清单

对聚合的每个候选值对象/实体，执行以下检查：

| 检查项 | 判断 |
|--------|------|
| 修改它时，是否必须校验聚合根状态？ | 是 → Case 1 |
| 修改它时，是否需要更新聚合的其他属性？ | 是 → Case 1 |
| 它有独立的数据来源和更新周期吗？ | 是 → Case 2 |
| 它只用于展示，不参与业务规则？ | 是 → Case 3 |

---

## 逻辑分组重构：防止聚合根代码爆炸

### 问题场景

当聚合有 10+ 个值对象时，聚合根类会变得臃肿难维护：

```java
// ❌ 问题：扁平且乱，10+ 个零散字段
class User {
    String province;
    String city;
    String street;     // --- 地址相关
    String bankName;
    String accountNo;  // --- 银行相关
    String wechatId;
    String alipayId;   // --- 社交相关
    // ... 更多字段
}
```

### 解决方案：语义分组

将相关字段打包成更大的值对象，按业务含义归类：

```java
// ✅ 正确：结构清晰，聚合根只管理 3 个大对象
class User {
    Address address;       // 值对象组 A
    BankAccount account;   // 值对象组 B
    SocialProfile social;  // 值对象组 C
}

// 值对象组定义
record Address(String province, String city, String street) {}
record BankAccount(String bankName, String accountNo) {}
record SocialProfile(String wechatId, String alipayId) {}
```

### 分组原则

1. **业务内聚**：同一业务概念的属性放在一起
2. **变更频率**：经常一起变更的属性放在一起
3. **访问模式**：经常一起读取的属性放在一起

### 医学领域示例

```java
// ✅ Venue 聚合的逻辑分组
class VenueAggregate {
    // 核心身份信息
    VenueIdentity identity;        // name, abbreviation, type

    // 外部标识符集合
    List<VenueIdentifier> identifiers;  // ISSN, NLM ID, OpenAlex ID

    // 数据来源追踪
    VenueSourceData sourceData;    // provenance info, raw data

    // 评级信息（如果属于聚合）
    VenueRating rating;            // JCR, CAS 分区
}
```

---

## 性能优化：懒加载与 CQRS

### ⚠️ 反模式警示

**绝对不要**通过"两个不同的 Repository"来管理属于同一个聚合的数据：

```java
// ❌ 严重错误！违反 DDD 原则
class OrderService {
    OrderCoreRepository coreRepo;      // 管理订单核心数据
    OrderExtraRepository extraRepo;    // 管理订单附加数据

    public void updateOrder(Order order) {
        coreRepo.save(order.getCore());
        extraRepo.save(order.getExtra());  // 如果忘了调用？数据就丢了！
    }
}
```

**后果**：
1. **事务一致性崩溃**：必须记得同时调用两个 save，新人容易遗漏
2. **领域逻辑泄漏**：业务校验散落在 Service 层，聚合根变得"残缺不全"

### 正确方案 1：Repository 层懒加载

在 **Infrastructure 层**实现按需加载，对 **Domain 层**透明：

```java
// Infrastructure Layer - Repository 实现
public class VenueRepositoryAdapter implements VenueRepository {

    public VenueAggregate findById(Long id) {
        // 1. 查主表 -> 核心数据
        VenueDO venueDO = venueMapper.selectById(id);

        // 2. 构建聚合根（核心数据）
        VenueAggregate venue = VenueConverter.toDomain(venueDO);

        // 3. 对于"边缘数据"（Case 3），暂时不加载
        //    只有当业务真正需要时，才通过专门的方法加载
        return venue;
    }

    /// 按需加载边缘数据
    public void loadRichDescription(VenueAggregate venue) {
        VenueDescriptionDO desc = descMapper.selectByVenueId(venue.getId());
        venue.setRichDescription(desc.getContent());
    }
}
```

### 正确方案 2：CQRS 读写分离

**核心思想**：写操作走聚合根，读操作直接走 DTO。

```
写操作（Command）                读操作（Query）
     ↓                              ↓
Application Layer              Application Layer
     ↓                              ↓
聚合根（只加载校验需要的数据）    Mapper（直接查 DTO）
     ↓                              ↓
Repository                     VenueQueryMapper
```

**实现示例**：

```java
// 写操作：通过聚合根，只加载业务校验需要的数据
public class VenueUpdateOrchestrator {
    public void updateVenueName(Long venueId, String newName) {
        // Repository 不加载 RichDescription（不参与校验）
        VenueAggregate venue = venueRepository.findById(venueId);
        venue.rename(newName);  // 业务校验在聚合内
        venueRepository.save(venue);
    }
}

// 读操作：绕过 Repository，直接查 DTO
public class VenueQueryService {
    public VenueDetailDTO getVenueDetail(Long venueId) {
        // 直接用 Mapper 联表查询，返回 DTO
        return venueQueryMapper.selectDetailById(venueId);
    }
}
```

### 策略选择指南

| 场景 | 推荐策略 |
|------|----------|
| 写操作涉及复杂业务校验 | 通过聚合根 + 懒加载 |
| 读操作需要展示详情（99% 流量） | CQRS - 直接查 DTO |
| 边缘数据（Case 3）按需使用 | Repository 懒加载方法 |
| 性能敏感的列表查询 | 绕过 Repository，用 Mapper |

---

## 最佳实践路线图

当面对"大聚合"性能问题时，按以下步骤处理：

```
1. 清洗模型
   └── 对每个值对象执行"不变性检查"
   └── 识别 Case 2（可拆分为独立聚合）

2. 逻辑分组
   └── 剩余的值对象按业务含义归类
   └── 封装成 3-4 个语义值对象组

3. CQRS 分离
   └── 查询（99%流量）：直接写 SQL 查 DTO
   └── 修改（1%流量）：Repository 只加载核心字段
   └── 边缘数据：懒加载或按需加载
```
