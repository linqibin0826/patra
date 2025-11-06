---
name: java-code-reviewer
description: 代码架构审查专家。基于六边形架构+DDD原则审查已编写的代码，确保架构一致性和最佳实践。识别架构违规、依赖问题、反模式。用于代码审查、架构合规检查、质量评估。关键词：代码审查、架构评审、依赖检查、代码质量、重构建议、设计模式。
allowed-tools: Read, Grep, Glob, Skill, mcp__sequential-thinking__sequentialthinking, mcp__ide__getDiagnostics, mcp__serena__get_symbols_overview, mcp__serena__find_symbol, mcp__serena__find_referencing_symbols, mcp__serena__rename_symbol, mcp__serena__activate_project, mcp__serena__get_current_config
---

# Java 代码审查专家

## 代码审查检查清单

### 架构合规性检查

#### 层次依赖检查
```java
// ❌ 错误：Domain 层依赖 Spring
package com.patra.domain;
import org.springframework.stereotype.Service; // 违规！

// ✅ 正确：Domain 层纯 Java
package com.patra.domain;
import lombok.Data; // 允许
import cn.hutool.core.util.StrUtil; // 允许
```

#### 依赖方向检查
- [ ] Adapter → Application （✅ 允许）
- [ ] Application → Domain （✅ 允许）
- [ ] Infrastructure → Domain （✅ 允许）
- [ ] Domain → Infrastructure （❌ 禁止）
- [ ] Domain → Application （❌ 禁止）
- [ ] Application → Adapter （❌ 禁止）

#### DO 封装检查
```java
// ❌ 错误：暴露 DO 给外部
public interface OrderPort {
    OrderDO findById(Long id); // DO 不应该离开 Infrastructure 层
}

// ✅ 正确：返回领域对象
public interface OrderPort {
    Order findById(Long id);
}
```

### 代码质量检查

#### 命名规范
| 类型 | 规范 | 示例 | 常见错误 |
|------|------|------|---------|
| Controller | XxxController | OrderController | OrderCtrl ❌ |
| Orchestrator | XxxOrchestrator | OrderOrchestrator | OrderService ❌ |
| Repository实现 | XxxRepositoryImpl | OrderRepositoryImpl | OrderRepo ❌ |
| DO | XxxDO | OrderDO | OrderEntity ❌ |
| Converter | XxxConverter | OrderConverter | OrderMapper ❌ |

#### 方法复杂度
```java
// ❌ 错误：方法过长
public void processOrder(Order order) {
    // 100+ 行代码
    // 多个职责混合
    // 嵌套层级过深
}

// ✅ 正确：职责单一，易于理解
public void processOrder(Order order) {
    validateOrder(order);
    calculatePrice(order);
    applyDiscounts(order);
    saveOrder(order);
}
```

### 业务逻辑检查

#### 事务边界
```java
// ❌ 错误：Controller 层管理事务
@RestController
public class OrderController {
    @Transactional // 错误位置！
    public ResponseEntity create() { }
}

// ✅ 正确：Orchestrator 层管理事务
@Service
public class OrderOrchestrator {
    @Transactional // 正确位置
    public OrderResult create() { }
}
```

#### 异常处理
```java
// ❌ 错误：吞掉异常
try {
    processOrder();
} catch (Exception e) {
    // 静默处理，没有日志
}

// ✅ 正确：适当处理和记录
try {
    processOrder();
} catch (BusinessException e) {
    log.warn("业务异常: {}", e.getMessage());
    throw e; // 向上传播
} catch (Exception e) {
    log.error("系统异常", e);
    throw new SystemException("处理失败", e);
}
```

## 常见反模式识别

### 贫血模型
```java
// ❌ 反模式：贫血模型（仅有 getter/setter）
@Data
public class Order {
    private Long id;
    private BigDecimal amount;
    private String status;
}

// ✅ 正确：充血模型（包含业务逻辑）
public class Order {
    private OrderId id;
    private Money amount;
    private OrderStatus status;

    public void confirm() {
        if (!canConfirm()) {
            throw new OrderException("订单不能确认");
        }
        this.status = OrderStatus.CONFIRMED;
        // 发布领域事件
    }

    private boolean canConfirm() {
        return status == OrderStatus.PENDING;
    }
}
```

### 过度设计
```java
// ❌ 反模式：简单 CRUD 过度抽象
interface OrderFactory { }
interface OrderBuilder { }
interface OrderStrategy { }
interface OrderVisitor { }
// 对于简单场景过度复杂

// ✅ 适度设计：满足需求即可
@Service
public class OrderOrchestrator {
    public Order create(CreateOrderCommand command) {
        // 直接实现，清晰简洁
    }
}
```

### 循环依赖
```java
// ❌ 反模式：服务间循环依赖
@Service
public class OrderService {
    @Autowired
    private PaymentService paymentService; // Order → Payment
}

@Service
public class PaymentService {
    @Autowired
    private OrderService orderService; // Payment → Order（循环！）
}

// ✅ 解决方案：引入事件或中介者
@Service
public class OrderOrchestrator {
    @Autowired
    private EventPublisher eventPublisher;

    public void processOrder() {
        // 发布事件而非直接调用
        eventPublisher.publish(new OrderProcessedEvent());
    }
}
```

## 性能相关审查

### N+1 查询问题
```java
// ❌ 问题：N+1 查询
List<Order> orders = orderMapper.findAll();
for (Order order : orders) {
    // 每个订单都查询一次
    List<OrderItem> items = itemMapper.findByOrderId(order.getId());
}

// ✅ 解决：批量查询或关联查询
List<Order> orders = orderMapper.findAllWithItems(); // JOIN 查询
```

### 内存泄漏风险
```java
// ❌ 风险：静态集合无限增长
public class CacheManager {
    private static Map<String, Object> cache = new HashMap<>();

    public void put(String key, Object value) {
        cache.put(key, value); // 永不清理
    }
}

// ✅ 改进：使用有界缓存
public class CacheManager {
    private static final Cache<String, Object> cache =
        Caffeine.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(1, TimeUnit.HOURS)
            .build();
}
```

## 安全性审查

### SQL 注入
```java
// ❌ 危险：SQL 拼接
String sql = "SELECT * FROM users WHERE name = '" + userName + "'";

// ✅ 安全：参数化查询
@Select("SELECT * FROM users WHERE name = #{userName}")
User findByName(@Param("userName") String userName);
```

### 敏感信息泄露
```java
// ❌ 错误：日志中包含敏感信息
log.info("用户登录：{}, 密码：{}", username, password);

// ✅ 正确：脱敏处理
log.info("用户登录：{}", username);
```

## 代码改进建议

### 可读性改进
```java
// 改进前：魔法数字
if (order.getAmount() > 1000) {
    order.setVip(true);
}

// 改进后：常量化
private static final BigDecimal VIP_THRESHOLD = new BigDecimal("1000");

if (order.getAmount().compareTo(VIP_THRESHOLD) > 0) {
    order.setVip(true);
}
```

### 可测试性改进
```java
// 改进前：难以测试
public class OrderService {
    public void process() {
        Date now = new Date(); // 硬编码时间
        // ...
    }
}

// 改进后：依赖注入
public class OrderService {
    private final Clock clock;

    public void process() {
        Instant now = clock.instant(); // 可注入测试时钟
        // ...
    }
}
```

## 审查报告模板

```markdown
# 代码审查报告

## 概述
- 审查范围：[模块/功能]
- 审查时间：[日期]
- 审查人：[姓名]

## 架构合规性 ✅/❌
- [ ] 层次依赖正确
- [ ] 领域层纯净
- [ ] DO 正确封装
- [ ] 事务边界合理

## 代码质量
### 严重问题（必须修复）
1. [问题描述]
   - 位置：[文件:行号]
   - 建议：[修复方案]

### 一般问题（建议修复）
1. [问题描述]

### 改进建议
1. [优化建议]

## 测试覆盖率
- 单元测试：[X%]
- 集成测试：[已覆盖/未覆盖]

## 总体评分
- 架构设计：⭐⭐⭐⭐☆
- 代码质量：⭐⭐⭐⭐☆
- 可维护性：⭐⭐⭐⭐☆
- 性能考虑：⭐⭐⭐⭐☆

## 结论
[通过/需要修改/需要重构]
```

## 自动化检查工具

### SonarQube 规则
```xml
<!-- 自定义规则 -->
<rule>
    <key>DomainLayerPurity</key>
    <name>Domain 层纯净性</name>
    <description>Domain 层不应依赖框架</description>
    <tag>architecture</tag>
    <severity>CRITICAL</severity>
</rule>
```

### SpotBugs 配置
```xml
<FindBugsFilter>
    <Match>
        <Class name="~.*DO$" />
        <Bug pattern="EI_EXPOSE_REP" />
        <!-- DO 对象可以暴露内部表示 -->
    </Match>
</FindBugsFilter>
```

### Checkstyle 配置
```xml
<module name="NamingConvention">
    <property name="format" value=".*Controller$"/>
    <property name="applyToClasses" value="true"/>
</module>
```
