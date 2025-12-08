# DDD 战术模式

本文档基于 Eric Evans 的《Domain-Driven Design》（蓝皮书）和 Vaughn Vernon 的《DDD Distilled》总结 DDD 战术模式。

## 聚合 (Aggregate)

### 定义

聚合是一组相关对象的集合，作为数据修改的单元对待。每个聚合有一个根实体（聚合根），外部只能通过聚合根访问聚合内部对象。

### 核心规则

1. **聚合根是唯一入口**：外部对象只能持有聚合根的引用
2. **边界内保持一致性**：聚合内的所有不变量在事务结束时必须满足
3. **整体存取**：Repository 只能操作聚合根，不能直接操作聚合内实体
4. **级联删除**：删除聚合根时，聚合内所有对象一并删除

### 设计检查清单

- [ ] 聚合根是否有全局唯一标识？
- [ ] 聚合内实体是否只有局部标识（聚合内唯一）？
- [ ] 所有不变量是否都在聚合边界内？
- [ ] 聚合是否足够小？

## 实体 (Entity)

### 定义

实体是具有唯一标识的对象，其标识在整个生命周期内保持不变，即使属性完全改变。

### 实体 vs 值对象判断标准

| 问题 | 实体 | 值对象 |
|------|------|--------|
| 是否需要跨时间追踪？ | ✅ 是 | ❌ 否 |
| 两个对象属性相同，是否相等？ | ❌ 否（看 ID） | ✅ 是 |
| 是否可以被替换？ | ❌ 否（需要更新） | ✅ 是 |
| 是否有生命周期？ | ✅ 是 | ❌ 否 |

### 实体设计原则

1. **标识明确**：使用有意义的业务标识或生成的技术标识
2. **行为丰富**：封装业务逻辑，避免贫血模型
3. **不变量保护**：构造时验证，状态变更时验证

```java
public class Author {
    private final Long id;
    private final String orcid;  // 业务标识
    private PersonName name;

    // 标识不变，属性可变
    public void updateName(PersonName newName) {
        this.name = newName;
    }
}
```

## 值对象 (Value Object)

### 定义

值对象没有概念上的标识，用于描述事物的特征。两个值对象如果属性相同则相等。

### 核心特征

1. **不可变性**：创建后不能修改
2. **可替换性**：可以用另一个等值的值对象替换
3. **无副作用**：方法不修改状态，返回新对象
4. **自验证**：构造时验证有效性

### 何时使用值对象

| 场景 | 使用值对象 |
|------|-----------|
| 度量/数量 | Money, Weight, Distance |
| 描述性概念 | Address, DateRange, Color |
| 复合属性 | PersonName (firstName + lastName) |
| 类型安全包装 | Email, PhoneNumber, ISSN |

### Java 实现（推荐 record）

```java
// 简单值对象
public record Email(String value) {
    public Email {
        Assert.notBlank(value, "邮箱不能为空");
        Assert.isTrue(value.contains("@"), "邮箱格式无效");
    }
}

// 复合值对象
public record PersonName(String familyName, String givenName, String suffix) {
    public String fullName() {
        return suffix != null
            ? "%s %s, %s".formatted(givenName, familyName, suffix)
            : "%s %s".formatted(givenName, familyName);
    }
}

// 带行为的值对象
public record Money(BigDecimal amount, Currency currency) {
    public Money add(Money other) {
        Assert.isTrue(currency.equals(other.currency), "货币类型必须相同");
        return new Money(amount.add(other.amount), currency);
    }
}
```

## 仓储 (Repository)

### 定义

仓储为聚合提供集合式的持久化抽象，隐藏数据访问细节。

### 核心原则

1. **每个聚合一个仓储**：仓储与聚合根一一对应
2. **整体存取**：保存/加载完整聚合，不能部分操作
3. **领域接口**：接口定义在 Domain 层，实现在 Infrastructure 层
4. **返回领域对象**：不返回 DO/DTO，只返回聚合根

### 接口设计规范

```java
// Domain 层定义接口
public interface VenueRepository {
    // 基本 CRUD
    void save(VenueAggregate venue);
    Optional<VenueAggregate> findById(Long id);
    void delete(VenueAggregate venue);

    // 业务查询方法
    Optional<VenueAggregate> findByIssnL(String issnL);
    List<VenueAggregate> findByType(VenueType type);
    boolean existsByOpenalexId(String openalexId);
}
```

### 禁止行为

- ❌ 提供 `findByXxxId` 返回聚合内实体
- ❌ 在仓储中实现业务逻辑
- ❌ 返回 DO、DTO 或其他非领域对象
- ❌ 暴露分页/排序等技术细节到领域层

## 领域服务 (Domain Service)

### 定义

领域服务封装不自然属于任何实体或值对象的领域逻辑。

### 何时使用领域服务

1. **复杂业务算法**：计算、验证、转换等无状态操作
2. **领域策略**：复杂的业务规则或策略模式
3. **单聚合内的复杂操作**：需要多步骤但只涉及单个聚合

### 领域服务 vs 聚合方法 vs 应用层编排

| 场景 | 推荐位置 | 理由 |
|------|---------|------|
| 操作自身状态 | 聚合方法 | 行为内聚 |
| 复杂算法/策略 | 领域服务 | 避免聚合臃肿 |
| **需要多个聚合数据** | **Application 层编排** | 聚合间解耦 |
| **跨聚合事务协调** | **Application 层编排** | 事务边界在 Application 层 |

> ⚠️ **重要区分**：领域服务不应该注入多个仓储来协调多个聚合。跨聚合的协调是 Application 层（Orchestrator）的职责。领域服务应该专注于**单一职责的业务逻辑**。

### 示例

```java
// ✅ 正确：领域服务专注于单一业务逻辑
public class VenueMergeService {

    // 接收两个聚合作为参数，执行合并逻辑
    public VenueAggregate merge(VenueAggregate primary, VenueAggregate duplicate) {
        // 合并两个期刊的标识符、属性
        // 返回合并后的聚合
    }
}

// ✅ 正确：Application 层负责协调
@Service
public class VenueMergeOrchestrator {
    private final VenueRepository venueRepository;
    private final VenueMergeService mergeService;

    @Transactional
    public void execute(Long primaryId, Long duplicateId) {
        VenueAggregate primary = venueRepository.findById(primaryId).orElseThrow();
        VenueAggregate duplicate = venueRepository.findById(duplicateId).orElseThrow();

        VenueAggregate merged = mergeService.merge(primary, duplicate);

        venueRepository.save(merged);
        venueRepository.delete(duplicate);
    }
}
```

## 领域事件 (Domain Event)

### 定义

领域事件表示领域中发生的有意义的事情，是已发生的事实。

### 命名规范

- 使用**过去时**：`OrderCreated`、`PaymentReceived`
- 包含**聚合信息**：`VenueCreatedEvent`
- 携带**必要数据**：事件处理所需的数据

### 发布时机

1. **聚合状态变更后**：在业务方法内注册事件
2. **事务提交前**：确保事件与状态变更原子性
3. **同步/异步处理**：根据业务需求选择

### 示例

```java
public class VenueAggregate extends AggregateRoot<Long> {

    public static VenueAggregate create(VenueCreateCommand cmd) {
        VenueAggregate venue = new VenueAggregate(null, cmd.name(), cmd.type());
        venue.registerEvent(new VenueCreatedEvent(venue.getId(), venue.getName()));
        return venue;
    }

    public void merge(VenueAggregate other) {
        // 合并逻辑
        this.registerEvent(new VenueMergedEvent(this.getId(), other.getId()));
    }
}
```

## 工厂 (Factory)

### 定义

工厂封装复杂的对象创建逻辑，返回完整、有效的聚合。

### 何时使用工厂

1. **创建逻辑复杂**：需要多步骤或条件判断
2. **多种创建方式**：从不同数据源创建
3. **隐藏实现**：客户端不需要知道具体类型

### 工厂形式

| 形式 | 适用场景 |
|------|---------|
| 聚合根静态方法 | 简单创建，推荐方式 |
| 独立工厂类 | 复杂创建，需要依赖注入 |
| Builder 模式 | 多可选参数 |

### 示例

```java
// 聚合根静态工厂（推荐）
public class VenueAggregate {
    public static VenueAggregate create(String name, VenueType type) { }
    public static VenueAggregate fromOpenAlex(OpenAlexVenue data) { }
    public static VenueAggregate fromPubMed(NLMJournal data) { }
    public static VenueAggregate restore(Long id, ..., Long version) { }
}

// 独立工厂（复杂场景）
public class VenueFactory {
    private final VenueRepository repository;
    private final VenueDeduplicationService deduplicationService;

    public VenueAggregate createOrMerge(ExternalVenueData data) {
        // 1. 检查是否已存在
        // 2. 如果存在，执行合并
        // 3. 如果不存在，创建新聚合
    }
}
```

## 模式选择速查表

| 需求 | 推荐模式 |
|------|---------|
| 需要跟踪生命周期 | 实体 |
| 描述性属性组合 | 值对象 |
| 复杂一致性边界 | 聚合 |
| 持久化抽象 | 仓储 |
| 跨聚合业务逻辑 | 领域服务 |
| 状态变更通知 | 领域事件 |
| 复杂创建逻辑 | 工厂 |
