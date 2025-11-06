---
name: java-test-architect
description: Java测试生成专家，识别六边形架构代码模式，生成正确的测试策略。生成单元测试、集成测试、ArchUnit架构测试、MockMvc测试、TestContainers集成测试。当你需要测试覆盖率分析、幂等性测试、乐观锁测试、@Transactional测试时使用。关键词：JUnit、Mockito、TestContainers、ArchUnit、测试金字塔、TDD。
allowed-tools: Read, Edit, Write, Grep, Glob, Bash, mcp__sequential-thinking__sequentialthinking, mcp__serena__get_symbols_overview, mcp__serena__find_symbol, mcp__context7__resolve-library-id, mcp__context7__get-library-docs, mcp__ide__getDiagnostics, WebSearch, WebFetch, TodoWrite, KillShell
---

# Java 测试架构师

## 测试策略概览

### 测试金字塔

```
         /\
        /E2E\      (少量) 端到端测试
       /------\
      /  集成  \    (中等) 集成测试
     /----------\
    /   单元测试   \  (大量) 单元测试
   /--------------\
```

### 六边形架构测试策略

| 层次 | 测试类型 | 测试重点 | 使用框架 |
|------|---------|----------|---------|
| Domain | 单元测试 | 业务逻辑、不变量 | JUnit, AssertJ |
| Application | 单元测试 | 编排逻辑、事务 | JUnit, Mockito |
| Infrastructure | 集成测试 | 数据访问、外部系统 | TestContainers, H2 |
| Adapter | 集成测试 | API 契约、序列化 | MockMvc, RestAssured |
| Architecture | 架构测试 | 依赖规则、命名约定 | ArchUnit |

## 单元测试模板

### Domain 层单元测试

```java
class OrderTest {

    @Test
    @DisplayName("创建订单时应该生成订单ID")
    void should_generate_id_when_create_order() {
        // given
        var customerId = CustomerId.of(1L);

        // when
        var order = Order.create(customerId);

        // then
        assertThat(order.getId()).isNotNull();
        assertThat(order.getCustomerId()).isEqualTo(customerId);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("添加订单项时应该重新计算总价")
    void should_recalculate_total_when_add_item() {
        // given
        var order = Order.create(CustomerId.of(1L));
        var product = new Product("iPhone", Money.of(999.99));

        // when
        order.addItem(product, 2);

        // then
        assertThat(order.getItems()).hasSize(1);
        assertThat(order.getTotalAmount()).isEqualTo(Money.of(1999.98));
    }

    @Test
    @DisplayName("已确认订单不能添加商品")
    void should_throw_exception_when_add_item_to_confirmed_order() {
        // given
        var order = createConfirmedOrder();

        // when & then
        assertThatThrownBy(() -> order.addItem(product, 1))
            .isInstanceOf(OrderException.class)
            .hasMessage("已确认订单不能修改");
    }
}
```

### Application 层单元测试

```java
@ExtendWith(MockitoExtension.class)
class OrderOrchestratorTest {

    @Mock
    private OrderPort orderPort;

    @Mock
    private InventoryPort inventoryPort;

    @Mock
    private EventPublisher eventPublisher;

    @InjectMocks
    private OrderOrchestrator orchestrator;

    @Test
    @DisplayName("创建订单应该检查库存、保存订单、发布事件")
    void should_check_inventory_save_order_and_publish_event() {
        // given
        var command = new CreateOrderCommand(customerId, items);
        when(inventoryPort.checkAvailable(anyList())).thenReturn(true);
        when(orderPort.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // when
        var result = orchestrator.createOrder(command);

        // then
        assertThat(result).isNotNull();

        // 验证调用顺序
        InOrder inOrder = inOrder(inventoryPort, orderPort, eventPublisher);
        inOrder.verify(inventoryPort).checkAvailable(anyList());
        inOrder.verify(orderPort).save(any(Order.class));
        inOrder.verify(eventPublisher).publish(any(OrderCreatedEvent.class));
    }

    @Test
    @DisplayName("库存不足时应该抛出异常")
    void should_throw_exception_when_inventory_not_available() {
        // given
        when(inventoryPort.checkAvailable(anyList())).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> orchestrator.createOrder(command))
            .isInstanceOf(InsufficientInventoryException.class);

        verify(orderPort, never()).save(any());
        verify(eventPublisher, never()).publish(any());
    }
}
```

## 集成测试模板

### Repository 集成测试（TestContainers）

```java
@DataJpaTest
@AutoConfigureMockMvc
@Testcontainers
@Import({MapperConfig.class, RepositoryConfig.class})
class OrderRepositoryImplTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("test")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private OrderRepositoryImpl repository;

    @Test
    @DisplayName("保存订单后应该能够查询")
    void should_find_order_after_save() {
        // given
        var order = createTestOrder();

        // when
        repository.save(order);
        var found = repository.findById(order.getId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getId()).isEqualTo(order.getId());
    }

    @Test
    @Sql("/test-data/orders.sql")
    @DisplayName("应该正确查询待处理订单")
    void should_find_pending_orders() {
        // when
        var orders = repository.findPendingOrders();

        // then
        assertThat(orders).hasSize(3);
        assertThat(orders).extracting(Order::getStatus)
            .containsOnly(OrderStatus.PENDING);
    }
}
```

### Controller 集成测试（MockMvc）

```java
@WebMvcTest(OrderController.class)
@Import({SecurityConfig.class, JacksonConfig.class})
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderOrchestrator orchestrator;

    @Test
    @DisplayName("创建订单应该返回201")
    void should_return_201_when_create_order() throws Exception {
        // given
        var request = """
            {
                "customerId": 1,
                "items": [
                    {"productId": 100, "quantity": 2}
                ]
            }
            """;
        var expectedResult = new OrderResult(1L, "PENDING");
        when(orchestrator.createOrder(any())).thenReturn(expectedResult);

        // when & then
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(request))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.orderId").value(1))
            .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("参数验证失败应该返回400")
    void should_return_400_when_validation_fails() throws Exception {
        // given
        var invalidRequest = """
            {
                "customerId": null,
                "items": []
            }
            """;

        // when & then
        mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidRequest))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.title").value("验证失败"))
            .andExpect(jsonPath("$.violations").isArray());
    }
}
```

## 架构测试（ArchUnit）

```java
@AnalyzeClasses(packages = "com.patra", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

    @ArchTest
    static final ArchRule domain_should_not_depend_on_infrastructure =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("..infra..");

    @ArchTest
    static final ArchRule domain_should_not_use_spring =
        noClasses().that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAPackage("org.springframework..");

    @ArchTest
    static final ArchRule services_should_be_annotated_with_service =
        classes().that().haveSimpleNameEndingWith("Service")
            .or().haveSimpleNameEndingWith("Orchestrator")
            .should().beAnnotatedWith(Service.class);

    @ArchTest
    static final ArchRule controllers_should_be_in_adapter_layer =
        classes().that().areAnnotatedWith(RestController.class)
            .should().resideInAPackage("..adapter.controller..");
}
```

## 特殊场景测试

### 事务测试

```java
@SpringBootTest
@Transactional
@Rollback
class TransactionalOrchestratorTest {

    @Test
    @DisplayName("事务应该在异常时回滚")
    void should_rollback_when_exception() {
        // given
        doThrow(new RuntimeException("模拟异常"))
            .when(eventPublisher).publish(any());

        // when
        assertThatThrownBy(() -> orchestrator.process())
            .isInstanceOf(RuntimeException.class);

        // then
        assertThat(repository.count()).isZero(); // 验证回滚
    }
}
```

### 幂等性测试

```java
@Test
@DisplayName("重复请求应该返回相同结果")
void should_return_same_result_for_duplicate_request() {
    // given
    var request = createRequest();

    // when
    var result1 = orchestrator.process(request);
    var result2 = orchestrator.process(request); // 重复请求

    // then
    assertThat(result1).isEqualTo(result2);
    verify(repository, times(1)).save(any()); // 只保存一次
}
```

### 乐观锁测试

```java
@Test
@DisplayName("并发更新时应该抛出乐观锁异常")
void should_throw_optimistic_lock_exception() throws Exception {
    // given
    var entity = repository.findById(1L).orElseThrow();

    // when - 模拟并发更新
    CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
        entity.setName("Update 1");
        repository.save(entity);
    });

    CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
        entity.setName("Update 2");
        repository.save(entity);
    });

    // then
    assertThatThrownBy(() -> CompletableFuture.allOf(future1, future2).get())
        .hasCauseInstanceOf(OptimisticLockingFailureException.class);
}
```

## 测试数据构建器

```java
public class OrderTestDataBuilder {
    private CustomerId customerId = CustomerId.of(1L);
    private List<OrderItem> items = new ArrayList<>();
    private OrderStatus status = OrderStatus.PENDING;

    public static OrderTestDataBuilder anOrder() {
        return new OrderTestDataBuilder();
    }

    public OrderTestDataBuilder withCustomerId(Long id) {
        this.customerId = CustomerId.of(id);
        return this;
    }

    public OrderTestDataBuilder withItem(String product, double price, int quantity) {
        items.add(new OrderItem(product, Money.of(price), quantity));
        return this;
    }

    public OrderTestDataBuilder confirmed() {
        this.status = OrderStatus.CONFIRMED;
        return this;
    }

    public Order build() {
        var order = Order.create(customerId);
        items.forEach(item -> order.addItem(item));
        if (status == OrderStatus.CONFIRMED) {
            order.confirm();
        }
        return order;
    }
}

// 使用示例
var order = anOrder()
    .withCustomerId(123L)
    .withItem("iPhone", 999.99, 2)
    .withItem("AirPods", 199.99, 1)
    .confirmed()
    .build();
```

## 测试执行命令

```bash
# 运行所有测试
mvn test

# 运行特定层的测试
mvn test -Dtest="*Domain*Test"
mvn test -Dtest="*Integration*Test"

# 生成测试覆盖率报告
mvn jacoco:prepare-agent test jacoco:report

# 运行 ArchUnit 测试
mvn test -Dtest="*ArchitectureTest"

# 并行执行测试
mvn test -T 4 # 使用 4 个线程
```

## 详细资源

需要深入了解时，查看以下资源文件：

- [testing-guide.md](resources/testing-guide.md) - 测试策略详解
- [test-templates-domain.md](resources/test-templates-domain.md) - 领域层测试模板
- [test-templates-application.md](resources/test-templates-application.md) - 应用层测试模板
- [test-templates-infrastructure.md](resources/test-templates-infrastructure.md) - 基础设施层测试模板
- [test-templates-adapter.md](resources/test-templates-adapter.md) - 适配器层测试模板