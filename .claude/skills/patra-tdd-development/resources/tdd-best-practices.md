# TDD 最佳实践

## 核心原则

### 1. 测试三定律（Three Laws of TDD）

**by Robert C. Martin (Uncle Bob)**

1. **不可编写生产代码，除非它能让一个失败的测试通过**
   - ❌ 先写实现代码
   - ✅ 先写失败的测试

2. **只编写恰好能导致失败的测试代码**
   - ❌ 一次写完整个测试套件
   - ✅ 每次只写一个测试

3. **只编写恰好能让测试通过的生产代码**
   - ❌ 实现未测试的功能
   - ✅ 只实现让测试通过的代码

### 2. YAGNI 原则（You Ain't Gonna Need It）

**不要实现未来可能需要的功能**

```java
// ❌ 错误：过度设计
public class Article {
    // 现在用不到的功能
    private List<Tag> tags;
    private List<Comment> comments;
    private List<Like> likes;
    private ViewCounter viewCounter;
    private ShareCounter shareCounter;

    // 只有 title 和 content 有测试
}

// ✅ 正确：只实现测试需要的
public class Article {
    private String title;
    private String content;
    private ArticleStatus status;
    // 有测试覆盖的功能
}
```

### 3. KISS 原则（Keep It Simple, Stupid）

**保持实现简单**

```java
// ❌ 错误：复杂的实现
public class IdGenerator {
    private static final SecureRandom random = new SecureRandom();
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    public static String generate() {
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}

// ✅ 正确：最简单的实现（先让测试通过）
public class IdGenerator {
    private static long counter = 0L;

    public static String generate() {
        return String.valueOf(++counter);
    }
}

// 🔵 如果测试需要更复杂的 ID，再重构
```

### 4. DRY 原则（Don't Repeat Yourself）

**在重构阶段消除重复**

```java
// 🔴 Red: 写第一个测试
@Test
void should_create_article() {
    var article = new Article();
    article.setTitle("测试标题");
    article.setContent("测试内容");
    article.setAuthor("作者");

    assertThat(article.getTitle()).isEqualTo("测试标题");
}

// 🔴 Red: 写第二个测试（发现重复）
@Test
void should_publish_article() {
    var article = new Article();
    article.setTitle("测试标题");
    article.setContent("测试内容");
    article.setAuthor("作者");

    article.publish();

    assertThat(article.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
}

// 🔵 Refactor: 消除重复
public class ArticleTestDataBuilder {
    private String title = "测试标题";
    private String content = "测试内容";
    private String author = "作者";

    public static ArticleTestDataBuilder anArticle() {
        return new ArticleTestDataBuilder();
    }

    public ArticleTestDataBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public Article build() {
        var article = new Article();
        article.setTitle(title);
        article.setContent(content);
        article.setAuthor(author);
        return article;
    }
}

// 使用 Builder
@Test
void should_create_article() {
    var article = anArticle().build();
    assertThat(article.getTitle()).isEqualTo("测试标题");
}

@Test
void should_publish_article() {
    var article = anArticle().build();
    article.publish();
    assertThat(article.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
}
```

---

## 测试编写技巧

### 1. AAA 模式（Arrange-Act-Assert）

**清晰的测试结构**

```java
@Test
void should_calculate_total_price() {
    // Arrange（准备）- 设置测试数据
    var order = new Order();
    order.addItem(new Item("商品A", 100.0, 2));
    order.addItem(new Item("商品B", 50.0, 1));

    // Act（执行）- 调用被测试的方法
    var total = order.calculateTotal();

    // Assert（断言）- 验证结果
    assertThat(total).isEqualTo(250.0);
}
```

### 2. Given-When-Then 模式（BDD 风格）

**更接近业务语言**

```java
@Test
@DisplayName("库存不足时应该抛出异常")
void should_throw_exception_when_inventory_insufficient() {
    // given - 给定条件
    var product = new Product("iPhone", 10);
    var inventory = new Inventory();
    inventory.add(product, 5);

    // when - 当执行动作
    var exception = catchThrowable(() ->
        inventory.reserve(product, 10)
    );

    // then - 那么应该得到结果
    assertThat(exception)
        .isInstanceOf(InsufficientInventoryException.class)
        .hasMessage("库存不足：需要 10，实际 5");
}
```

### 3. 一个测试只验证一个行为

**❌ 错误：一个测试验证多个行为**

```java
@Test
void should_create_and_publish_article() {
    // 创建
    var article = Article.create("标题", "内容");
    assertThat(article.getStatus()).isEqualTo(ArticleStatus.DRAFT);

    // 发布
    article.publish();
    assertThat(article.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);

    // 归档
    article.archive();
    assertThat(article.getStatus()).isEqualTo(ArticleStatus.ARCHIVED);

    // 问题：如果发布失败，归档测试就无法运行
}
```

**✅ 正确：拆分成独立的测试**

```java
@Test
void should_create_article_with_draft_status() {
    var article = Article.create("标题", "内容");
    assertThat(article.getStatus()).isEqualTo(ArticleStatus.DRAFT);
}

@Test
void should_publish_draft_article() {
    var article = Article.create("标题", "内容");

    article.publish();

    assertThat(article.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
}

@Test
void should_archive_published_article() {
    var article = Article.create("标题", "内容");
    article.publish();

    article.archive();

    assertThat(article.getStatus()).isEqualTo(ArticleStatus.ARCHIVED);
}
```

### 4. 测试命名要清晰

**使用业务语言描述测试**

```java
// ❌ 不好的命名
@Test
void test1() { ... }

@Test
void testArticle() { ... }

@Test
void testPublish() { ... }

// ✅ 好的命名（使用 should_xxx_when_yyy 模式）
@Test
@DisplayName("创建文章时应该设置为草稿状态")
void should_set_draft_status_when_create_article() { ... }

@Test
@DisplayName("草稿文章可以发布")
void should_publish_when_article_is_draft() { ... }

@Test
@DisplayName("已发布的文章不能重复发布")
void should_throw_exception_when_publish_already_published_article() { ... }
```

### 5. 使用流畅断言（AssertJ）

**更可读的断言**

```java
// ❌ JUnit 断言
assertEquals(expected, actual);
assertTrue(list.isEmpty());
assertNotNull(result);

// ✅ AssertJ 流畅断言
assertThat(actual).isEqualTo(expected);
assertThat(list).isEmpty();
assertThat(result).isNotNull();

// ✅ 链式断言
assertThat(article)
    .isNotNull()
    .extracting(Article::getTitle, Article::getStatus)
    .containsExactly("测试标题", ArticleStatus.DRAFT);

// ✅ 集合断言
assertThat(orders)
    .hasSize(3)
    .extracting(Order::getStatus)
    .containsOnly(OrderStatus.PENDING);
```

---

## Mock 使用技巧

### 1. 只 Mock 外部依赖

**Domain 层：不 Mock**

```java
// ✅ Domain 层纯单元测试，不使用 Mock
@Test
void should_add_item_to_order() {
    var order = new Order();

    order.addItem(new Item("商品", 100.0, 2));

    assertThat(order.getItems()).hasSize(1);
    assertThat(order.getTotal()).isEqualTo(200.0);
}
```

**Application 层：Mock Ports**

```java
// ✅ Application 层 Mock 外部端口
@ExtendWith(MockitoExtension.class)
class OrderOrchestratorTest {

    @Mock
    private OrderPort orderPort;

    @Mock
    private InventoryPort inventoryPort;

    @InjectMocks
    private OrderOrchestrator orchestrator;

    @Test
    void should_check_inventory_before_create_order() {
        when(inventoryPort.checkAvailable(anyList())).thenReturn(true);

        orchestrator.createOrder(command);

        verify(inventoryPort).checkAvailable(anyList());
    }
}
```

### 2. 不要 Mock 值对象

**❌ 错误：Mock 值对象**

```java
@Mock
private Money price;  // Money 是值对象，不应该 Mock

when(price.getAmount()).thenReturn(100.0);
```

**✅ 正确：直接创建值对象**

```java
var price = Money.of(100.0);  // 直接创建
```

### 3. 不要过度验证

**❌ 错误：验证所有调用**

```java
@Test
void should_create_order() {
    orchestrator.createOrder(command);

    // 过度验证
    verify(orderPort, times(1)).save(any());
    verify(inventoryPort, times(1)).checkAvailable(anyList());
    verify(eventPublisher, times(1)).publish(any());
    verify(logger, times(3)).info(anyString());
    verify(metrics, times(1)).increment(anyString());
}
```

**✅ 正确：只验证关键行为**

```java
@Test
void should_save_order_and_publish_event() {
    var result = orchestrator.createOrder(command);

    // 只验证核心行为和顺序
    InOrder inOrder = inOrder(orderPort, eventPublisher);
    inOrder.verify(orderPort).save(any());
    inOrder.verify(eventPublisher).publish(any());

    // 验证结果
    assertThat(result.getId()).isNotNull();
}
```

### 4. 使用参数匹配器

```java
// ✅ 精确匹配
verify(orderPort).save(expectedOrder);

// ✅ 类型匹配
verify(orderPort).save(any(Order.class));

// ✅ 条件匹配
verify(orderPort).save(argThat(order ->
    order.getStatus() == OrderStatus.PENDING
));

// ✅ 捕获参数
ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
verify(orderPort).save(captor.capture());
assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.PENDING);
```

---

## TDD 的节奏

### 1. 保持循环短小

**理想节奏：5-15 分钟/循环**

```
🔴 Red   (2-5 分钟)  - 写测试
🟢 Green (3-10 分钟) - 实现
🔵 Refactor (0-5 分钟) - 重构
```

### 2. 小步前进

**从最简单的测试开始**

```java
// 第 1 步：最基础的功能
@Test
void should_create_article() {
    var article = Article.create("标题", "内容");
    assertThat(article).isNotNull();
}

// 第 2 步：验证属性
@Test
void should_set_title_when_create() {
    var article = Article.create("测试标题", "内容");
    assertThat(article.getTitle()).isEqualTo("测试标题");
}

// 第 3 步：验证状态
@Test
void should_be_draft_when_created() {
    var article = Article.create("标题", "内容");
    assertThat(article.getStatus()).isEqualTo(ArticleStatus.DRAFT);
}

// 第 4 步：添加行为
@Test
void should_publish_article() {
    var article = Article.create("标题", "内容");
    article.publish();
    assertThat(article.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
}
```

### 3. 频繁提交

**每个 Green 阶段后提交**

```bash
# Red + Green + Refactor 完成后
git add .
git commit -m "feat: add article creation"

# 不要积累太多未提交的代码
# 每个完整的 TDD 循环后就提交
```

---

## 常见陷阱及避免方法

### 陷阱 1：测试实现细节

**❌ 错误：测试私有方法**

```java
// 测试私有方法（通过反射）
@Test
void should_validate_title() throws Exception {
    var method = Article.class.getDeclaredMethod("validateTitle", String.class);
    method.setAccessible(true);

    var result = method.invoke(article, "标题");

    assertThat(result).isEqualTo(true);
}
```

**✅ 正确:通过公共 API 测试**

```java
@Test
void should_throw_exception_when_title_is_invalid() {
    assertThatThrownBy(() -> Article.create("", "内容"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("标题不能为空");
}
```

### 陷阱 2：测试过于脆弱

**❌ 错误：依赖调用次数**

```java
@Test
void should_create_order() {
    orchestrator.createOrder(command);

    // 脆弱：实现改变时容易失败
    verify(logger, times(3)).info(anyString());
    verify(cache, times(2)).get(anyString());
}
```

**✅ 正确：测试行为结果**

```java
@Test
void should_create_order() {
    var result = orchestrator.createOrder(command);

    // 稳定：只要行为正确就通过
    assertThat(result.getId()).isNotNull();
    assertThat(result.getStatus()).isEqualTo(OrderStatus.CREATED);
}
```

### 陷阱 3：测试相互依赖

**❌ 错误：测试依赖执行顺序**

```java
private static Article sharedArticle;

@Test
void test1_create() {
    sharedArticle = Article.create("标题", "内容");
}

@Test
void test2_publish() {
    sharedArticle.publish();  // 依赖 test1
    assertThat(sharedArticle.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
}
```

**✅ 正确：每个测试独立**

```java
@Test
void should_create_article() {
    var article = Article.create("标题", "内容");
    assertThat(article).isNotNull();
}

@Test
void should_publish_article() {
    var article = Article.create("标题", "内容");  // 独立创建

    article.publish();

    assertThat(article.getStatus()).isEqualTo(ArticleStatus.PUBLISHED);
}
```

### 陷阱 4：忽略边界条件

**❌ 错误：只测试正常路径**

```java
@Test
void should_create_order() {
    var order = orderService.create(validCommand);
    assertThat(order).isNotNull();
}
```

**✅ 正确：覆盖边界和异常**

```java
@Test
void should_create_order() {
    var order = orderService.create(validCommand);
    assertThat(order).isNotNull();
}

@Test
void should_throw_exception_when_items_empty() {
    var command = new CreateOrderCommand(customerId, List.of());

    assertThatThrownBy(() -> orderService.create(command))
        .isInstanceOf(IllegalArgumentException.class);
}

@Test
void should_throw_exception_when_inventory_insufficient() {
    when(inventoryPort.checkAvailable(anyList())).thenReturn(false);

    assertThatThrownBy(() -> orderService.create(command))
        .isInstanceOf(InsufficientInventoryException.class);
}

@Test
void should_throw_exception_when_customer_not_found() {
    when(customerPort.findById(anyLong())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> orderService.create(command))
        .isInstanceOf(CustomerNotFoundException.class);
}
```

---

## 重构技巧

### 1. 重构的时机

**Green 阶段后立即检查：**

```
检查清单：
[ ] 有重复代码吗？
[ ] 命名清晰吗？
[ ] 方法太长吗？(>20 行)
[ ] 类职责单一吗？
[ ] 有复杂的条件逻辑吗？
[ ] 有魔法数字吗？
```

### 2. 重构的步骤

**小步重构，每步运行测试**

```java
// 原始代码
public void processOrder(Order order) {
    if (order.getTotal() > 1000) {
        order.setDiscount(order.getTotal() * 0.1);
    }
    repository.save(order);
    publisher.publish(new OrderProcessedEvent(order.getId()));
}

// 🔵 Refactor 1: 提取常量
private static final double DISCOUNT_THRESHOLD = 1000.0;
private static final double DISCOUNT_RATE = 0.1;

public void processOrder(Order order) {
    if (order.getTotal() > DISCOUNT_THRESHOLD) {
        order.setDiscount(order.getTotal() * DISCOUNT_RATE);
    }
    repository.save(order);
    publisher.publish(new OrderProcessedEvent(order.getId()));
}

// 运行测试 ✅

// 🔵 Refactor 2: 提取方法
public void processOrder(Order order) {
    applyDiscountIfEligible(order);
    saveOrder(order);
    publishEvent(order);
}

private void applyDiscountIfEligible(Order order) {
    if (order.getTotal() > DISCOUNT_THRESHOLD) {
        order.setDiscount(order.getTotal() * DISCOUNT_RATE);
    }
}

// 运行测试 ✅

// 🔵 Refactor 3: 引入策略模式
public void processOrder(Order order) {
    discountStrategy.apply(order);
    saveOrder(order);
    publishEvent(order);
}

// 运行测试 ✅
```

### 3. 安全重构的保障

**依赖测试，大胆重构**

```bash
# 重构前运行所有测试
mvn test

# 重构
# ...

# 重构后立即运行测试
mvn test

# 如果失败，立即回滚
git checkout .
```

---

## 总结：TDD 黄金法则

1. **测试先行** - 先写测试，后写实现
2. **小步前进** - 每次只添加一个测试
3. **Red-Green-Refactor** - 严格遵循循环
4. **YAGNI** - 只实现测试需要的功能
5. **持续重构** - 在测试保护下改善代码
6. **快速反馈** - 保持测试快速运行
7. **测试独立** - 每个测试相互独立
8. **清晰命名** - 测试即文档
9. **只 Mock 边界** - Domain 层不 Mock
10. **覆盖边界** - 正常路径 + 异常场景

**记住：TDD 不仅是测试技术，更是设计方法论！**
