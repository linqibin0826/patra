# TDD 工作流程详解

## Red-Green-Refactor 循环深度剖析

### 🔴 Red 阶段：编写失败的测试

#### 目标
- 定义期望的行为
- 确保测试真正验证新功能
- 提前思考 API 设计

#### 步骤

**1. 选择下一个最简单的测试**

```java
// 从最基础的开始
@Test
@DisplayName("应该能够创建文章")
void should_create_article() {
    var article = Article.create("标题", "内容");
    assertThat(article).isNotNull();
}
```

**2. 编写测试代码**

使用 AAA 模式：
```java
@Test
void should_xxx_when_yyy() {
    // Arrange (准备)
    var input = ...;

    // Act (执行)
    var result = service.doSomething(input);

    // Assert (断言)
    assertThat(result).isEqualTo(expected);
}
```

**3. 运行测试，确认失败**

```bash
# 必须看到红色（失败）
mvn test -Dtest=ArticleTest

# 失败原因应该是：
# - 编译错误（类或方法不存在）
# - 运行时失败（返回值不符合预期）
```

**4. 检查失败原因**

确保测试因为正确的原因失败：
- ✅ 类或方法不存在（编译错误）
- ✅ 方法返回错误的值（断言失败）
- ❌ 测试写错了（语法错误）
- ❌ 环境问题（配置错误）

---

### 🟢 Green 阶段：最简实现

#### 目标
- 用最快的方式让测试通过
- 不追求完美实现
- 不过度设计

#### 原则

**1. 最简实现（Fake It）**

```java
// 测试
@Test
void should_return_sum() {
    assertThat(calculator.add(2, 3)).isEqualTo(5);
}

// 最简实现（硬编码）
public int add(int a, int b) {
    return 5;  // 硬编码让测试通过
}

// 下一个测试会迫使我们实现真正的逻辑
@Test
void should_return_sum_for_different_numbers() {
    assertThat(calculator.add(1, 1)).isEqualTo(2);
}

// 现在必须实现真正的逻辑
public int add(int a, int b) {
    return a + b;
}
```

**2. 显而易见的实现（Obvious Implementation）**

当实现显而易见时，直接写正确的代码：

```java
// 测试
@Test
void should_get_full_name() {
    var person = new Person("John", "Doe");
    assertThat(person.getFullName()).isEqualTo("John Doe");
}

// 显而易见的实现
public String getFullName() {
    return firstName + " " + lastName;
}
```

**3. 三角测量（Triangulation）**

当不确定实现时，写多个测试来推导：

```java
// 第一个测试
@Test
void should_return_0_for_empty_string() {
    assertThat(romanParser.parse("")).isEqualTo(0);
}

// 实现
public int parse(String roman) {
    return 0;  // 硬编码
}

// 第二个测试
@Test
void should_return_1_for_I() {
    assertThat(romanParser.parse("I")).isEqualTo(1);
}

// 现在可以推导真正的逻辑
public int parse(String roman) {
    if (roman.isEmpty()) return 0;
    if (roman.equals("I")) return 1;
    return 0;
}

// 继续添加测试，逐步完善实现
```

#### 步骤

**1. 写最简单的实现**

```java
// 可以：
// - 返回硬编码值
// - 返回 null
// - 抛出异常
// - 使用最简单的算法

// 目标：让测试通过，不管代码多丑陋
```

**2. 运行测试**

```bash
mvn test -Dtest=ArticleTest

# 确认：
# ✅ 新测试通过（绿色）
# ✅ 所有旧测试仍然通过
```

**3. 如果测试失败**

- 检查实现是否有错误
- 检查测试是否写错
- 不要修改测试来适应实现

---

### 🔵 Refactor 阶段：重构优化

#### 目标
- 改善代码质量
- 消除重复
- 提高可读性
- 优化设计

#### 重构类型

**1. 消除重复（DRY）**

```java
// Before: 重复的代码
@Test
void test1() {
    var article = new Article();
    article.setTitle("标题");
    article.setContent("内容");
    article.setAuthor("作者");
    // ...
}

@Test
void test2() {
    var article = new Article();
    article.setTitle("标题2");
    article.setContent("内容2");
    article.setAuthor("作者2");
    // ...
}

// After: 提取 Test Data Builder
@Test
void test1() {
    var article = anArticle()
        .withTitle("标题")
        .build();
}

@Test
void test2() {
    var article = anArticle()
        .withTitle("标题2")
        .build();
}
```

**2. 改善命名**

```java
// Before: 不清晰的命名
public class X {
    private String s;

    public void doIt(String p) {
        this.s = p;
    }
}

// After: 清晰的命名
public class Article {
    private String title;

    public void updateTitle(String newTitle) {
        this.title = newTitle;
    }
}
```

**3. 提取方法**

```java
// Before: 复杂的方法
public void createOrder(CreateOrderCommand command) {
    // 验证库存
    for (Item item : command.items()) {
        if (inventory.get(item.productId()) < item.quantity()) {
            throw new InsufficientInventoryException();
        }
    }

    // 创建订单
    Order order = new Order();
    order.setCustomerId(command.customerId());
    order.setItems(command.items());

    // 保存订单
    repository.save(order);

    // 发布事件
    eventPublisher.publish(new OrderCreatedEvent(order.getId()));
}

// After: 提取小方法
public void createOrder(CreateOrderCommand command) {
    checkInventory(command.items());
    Order order = buildOrder(command);
    saveOrder(order);
    publishOrderCreatedEvent(order);
}

private void checkInventory(List<Item> items) {
    for (Item item : items) {
        if (inventory.get(item.productId()) < item.quantity()) {
            throw new InsufficientInventoryException();
        }
    }
}

private Order buildOrder(CreateOrderCommand command) {
    Order order = new Order();
    order.setCustomerId(command.customerId());
    order.setItems(command.items());
    return order;
}

private void saveOrder(Order order) {
    repository.save(order);
}

private void publishOrderCreatedEvent(Order order) {
    eventPublisher.publish(new OrderCreatedEvent(order.getId()));
}
```

**4. 引入设计模式**

```java
// Before: 复杂的条件逻辑
public double calculatePrice(Order order) {
    if (order.getType() == OrderType.NORMAL) {
        return order.getTotal();
    } else if (order.getType() == OrderType.VIP) {
        return order.getTotal() * 0.9;
    } else if (order.getType() == OrderType.SUPER_VIP) {
        return order.getTotal() * 0.8;
    }
    return order.getTotal();
}

// After: 策略模式
public interface PricingStrategy {
    double calculate(Order order);
}

public class NormalPricing implements PricingStrategy {
    public double calculate(Order order) {
        return order.getTotal();
    }
}

public class VipPricing implements PricingStrategy {
    public double calculate(Order order) {
        return order.getTotal() * 0.9;
    }
}

public double calculatePrice(Order order) {
    PricingStrategy strategy = pricingStrategyFactory.create(order.getType());
    return strategy.calculate(order);
}
```

**5. 优化数据结构**

```java
// Before: 使用 List 查找慢
private List<Product> products = new ArrayList<>();

public Product findById(Long id) {
    for (Product product : products) {
        if (product.getId().equals(id)) {
            return product;
        }
    }
    return null;
}

// After: 使用 Map 查找快
private Map<Long, Product> products = new HashMap<>();

public Product findById(Long id) {
    return products.get(id);
}
```

#### 重构步骤

**1. 识别改进机会**

```
检查：
[ ] 有重复代码吗？
[ ] 命名清晰吗？
[ ] 方法太长吗（>20行）？
[ ] 类职责单一吗？
[ ] 有复杂的条件逻辑吗？
[ ] 有魔法数字吗？
```

**2. 小步重构**

```java
// 每次只做一个重构
// 1. 提取常量
private static final int MAX_RETRY_COUNT = 3;

// 运行测试 ✅

// 2. 提取方法
private void validateInput() { ... }

// 运行测试 ✅

// 3. 改名
rename: processData → enrichArticleMetadata

// 运行测试 ✅
```

**3. 持续运行测试**

```bash
# 每次重构后立即运行测试
mvn test

# 确保所有测试仍然通过
# 如果失败，立即回滚
```

**4. 提交代码**

```bash
# 重构完成后提交
git add .
git commit -m "refactor: 提取 Article 创建逻辑到工厂方法"
```

---

## TDD 循环的节奏

### 典型的开发节奏

```
时间轴：
0:00  🔴 写测试（2-5 分钟）
0:05  🔴 运行测试，看到失败
0:06  🟢 写实现（3-10 分钟）
0:15  🟢 运行测试，看到通过
0:16  🔵 重构（0-5 分钟）
0:20  🔵 运行测试，确认重构正确
0:21  ✅ 提交代码
0:22  🔴 开始下一个循环
```

### 保持循环短小

**理想循环时间：5-15 分钟**

- 太快（<2 分钟）→ 步子太小，效率低
- 太慢（>30 分钟）→ 步子太大，风险高

### 何时打破循环

**可以暂停的情况：**
- 需要调研技术方案
- 需要理解现有代码
- 需要讨论需求

**暂停后的恢复：**
```bash
# 回到安全点
git reset --hard

# 或者暂存当前工作
git stash

# 调研完成后继续
git stash pop
```

---

## TDD 循环的变体

### Outside-In TDD（外部向内）

适合：API 开发、用户故事实现

```
1. 从 Controller 开始写测试
2. Mock Orchestrator
3. 实现 Controller
4. 实现 Orchestrator（写测试 → 实现）
5. 实现 Domain（写测试 → 实现）
6. 实现 Repository（写测试 → 实现）
```

### Inside-Out TDD（内部向外）

适合：算法开发、核心领域逻辑

```
1. 从 Domain 开始写测试
2. 实现 Domain 逻辑
3. 实现 Application 层（组合 Domain）
4. 实现 Repository（持久化 Domain）
5. 实现 Controller（暴露 API）
```

### Middle-Out TDD（中间向外）

适合：重构现有代码

```
1. 从最需要改变的地方开始
2. 写测试覆盖现有行为
3. 重构代码
4. 向外扩展测试覆盖
```

---

## 完整的 TDD 会话示例

### 需求：实现文章发布功能

**迭代 1：创建文章**

```java
// 🔴 Red (2 分钟)
@Test
@DisplayName("应该能够创建文章")
void should_create_article() {
    var article = Article.create("标题", "内容");
    assertThat(article).isNotNull();
}

// 运行测试 → 编译失败 ❌

// 🟢 Green (3 分钟)
public class Article {
    private String title;
    private String content;

    private Article(String title, String content) {
        this.title = title;
        this.content = content;
    }

    public static Article create(String title, String content) {
        return new Article(title, content);
    }
}

// 运行测试 → 通过 ✅

// 🔵 Refactor (1 分钟)
// 暂无需重构

// ✅ 提交：feat: add Article.create
```

**迭代 2：文章默认为草稿状态**

```java
// 🔴 Red (2 分钟)
@Test
@DisplayName("新创建的文章应该是草稿状态")
void should_be_draft_when_created() {
    var article = Article.create("标题", "内容");
    assertThat(article.getStatus()).isEqualTo(ArticleStatus.DRAFT);
}

// 运行测试 → 失败（方法不存在）❌

// 🟢 Green (2 分钟)
public enum ArticleStatus { DRAFT, PUBLISHED }

public class Article {
    private ArticleStatus status;

    public static Article create(String title, String content) {
        var article = new Article(title, content);
        article.status = ArticleStatus.DRAFT;
        return article;
    }

    public ArticleStatus getStatus() { return status; }
}

// 运行测试 → 通过 ✅

// 🔵 Refactor (0 分钟)
// 暂无需重构

// ✅ 提交：feat: add draft status to new articles
```

**迭代 3：发布文章**

```java
// 🔴 Red (3 分钟)
@Test
@DisplayName("应该能够发布草稿文章")
void should_publish_draft_article() {
    var article = Article.create("标题", "内容");

    article.publish();

    assertThat(article.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
    assertThat(article.getPublishedAt()).isNotNull();
}

// 运行测试 → 失败 ❌

// 🟢 Green (4 分钟)
public class Article {
    private LocalDateTime publishedAt;

    public void publish() {
        this.status = ArticleStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public LocalDateTime getPublishedAt() { return publishedAt; }
}

// 运行测试 → 通过 ✅

// 🔵 Refactor (3 分钟)
// 重构：引入 Clock 便于测试
public void publish(Clock clock) {
    this.status = ArticleStatus.PUBLISHED;
    this.publishedAt = LocalDateTime.now(clock);
}

// 运行测试 → 通过 ✅

// ✅ 提交：feat: add publish functionality
```

**总耗时：约 20 分钟，完成 3 个功能迭代**

---

## TDD 的心理过程

### Red 阶段的心态

```
❓ 这个功能应该怎么使用？
❓ API 长什么样子？
❓ 期望的行为是什么？

✍️ 通过写测试来回答这些问题
```

### Green 阶段的心态

```
🎯 目标：让测试通过
🚫 不要：追求完美实现
🚫 不要：过度设计
🚫 不要：实现未测试的功能

✅ 最简单的方式让测试通过即可
```

### Refactor 阶段的心态

```
🔍 检查代码质量
🔍 寻找重复
🔍 改善命名
🔍 优化结构

✅ 在测试保护下大胆重构
```

---

## 总结

**TDD 循环的本质：**

1. **Red** - 定义期望（What）
2. **Green** - 满足期望（Make it work）
3. **Refactor** - 优化实现（Make it better）

**记住：**
- 保持循环短小（5-15 分钟）
- 小步前进，持续反馈
- 测试先行，设计自然涌现
- 重构不是可选，而是必须
