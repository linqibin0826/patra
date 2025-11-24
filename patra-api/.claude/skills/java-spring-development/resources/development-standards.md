# 开发规范详细指南

## 命名约定

### 类命名规范

| 层级 | 类型 | 命名模式 | 示例 |
|------|------|---------|------|
| Controller | REST 控制器 | XxxController | ResourceController |
| Application | 编排器 | XxxOrchestrator | ResourceOrchestrator |
| Application | 协调器 | XxxCoordinator | PaymentCoordinator |
| Domain | 领域实体 | Xxx | Resource, Order |
| Domain | 值对象 | XxxVO 或具体名称 | Money, Address |
| Domain | 领域服务 | XxxDomainService | PricingDomainService |
| Domain | 仓储接口 | XxxRepository | ResourceRepository |
| Infrastructure | 仓储实现 | XxxRepositoryImpl | ResourceRepositoryImpl |
| Infrastructure | 数据对象 | XxxDO | ResourceDO |
| Infrastructure | Mapper | XxxMapper | ResourceMapper |
| Infrastructure | 转换器 | XxxConverter | ResourceConverter |
| Adapter | 监听器 | XxxListener | OrderEventListener |
| Adapter | 定时任务 | XxxJob | DataSyncJob |

### 方法命名规范

```java
// 查询方法
findById(Long id)
findByXxx(String xxx)
search(Query query)
list(Criteria criteria)
exists(Long id)

// 命令方法
create(Command command)
update(Long id, Command command)
delete(Long id)
execute(Command command)

// 转换方法
toDTO(Entity entity)
toDO(Domain domain)
toDomain(DO dataObject)
from(DTO dto)

// 校验方法
validate(Input input)
verify(Condition condition)
check(State state)

// 事件方法
handle(Event event)
publish(Event event)
on(Event event)
```

## 包结构组织

### 标准包结构

```
com.patra.{service}
├── adapter           # 适配层
│   ├── rest         # REST 控制器
│   ├── message      # 消息监听器
│   └── job          # 定时任务
├── app              # 应用层
│   ├── orchestrator # 编排器
│   ├── coordinator  # 协调器
│   └── assembler    # 装配器
├── domain           # 领域层
│   ├── model        # 领域模型
│   ├── service      # 领域服务
│   ├── repository   # 仓储接口
│   └── event        # 领域事件
├── infra            # 基础设施层
│   ├── repository   # 仓储实现
│   ├── mapper       # MyBatis Mapper
│   ├── dataobject   # 数据对象
│   ├── converter    # 转换器
│   └── client       # 外部服务客户端
└── api              # API 层
    ├── dto          # 数据传输对象
    ├── client       # Feign 客户端
    └── event        # 事件定义
```

## 代码风格规范

### 注解顺序

```java
// 类级别注解顺序
@Slf4j                      // 1. Lombok 日志
@RestController             // 2. Spring 核心注解
@RequestMapping("/api/v1")  // 3. 映射注解
@RequiredArgsConstructor    // 4. Lombok 构造器
@Validated                  // 5. 校验注解
public class ResourceController {
    // ...
}

// 字段注解顺序
@NotNull                    // 1. 校验注解
@Size(min = 1, max = 100)   // 2. 约束注解
@ApiModelProperty("名称")    // 3. 文档注解
private String name;

// 方法注解顺序
@Override                   // 1. Override
@Transactional             // 2. 事务注解
@Cacheable                 // 3. 缓存注解
public void process() {
    // ...
}
```

### 常量定义

```java
public class Constants {
    // 数值常量
    public static final int DEFAULT_PAGE_SIZE = 20;
    public static final int MAX_RETRY_TIMES = 3;
    public static final long CACHE_TTL_SECONDS = 3600L;

    // 字符串常量
    public static final String DEFAULT_CHARSET = "UTF-8";
    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    // 业务常量
    public static final String ORDER_STATUS_CREATED = "CREATED";
    public static final String ORDER_STATUS_PAID = "PAID";
}

// 枚举替代常量
public enum OrderStatus {
    CREATED("created", "已创建"),
    PAID("paid", "已支付"),
    SHIPPED("shipped", "已发货"),
    COMPLETED("completed", "已完成");

    private final String code;
    private final String desc;

    OrderStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
```

## 异常处理规范

### 异常定义

```java
// 业务异常
public class BusinessException extends RuntimeException {
    private final String errorCode;
    private final Object[] args;

    public BusinessException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.args = null;
    }

    public BusinessException(String errorCode, String message, Object... args) {
        super(message);
        this.errorCode = errorCode;
        this.args = args;
    }
}

// 特定业务异常
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super("RESOURCE_NOT_FOUND", message);
    }
}

public class DuplicateResourceException extends BusinessException {
    public DuplicateResourceException(String message) {
        super("DUPLICATE_RESOURCE", message);
    }
}
```

### 异常处理

```java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        log.warn("业务异常: {}", e.getMessage());
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of(e.getErrorCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        var errors = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining("; "));

        log.warn("参数校验失败: {}", errors);
        return ResponseEntity.badRequest()
            .body(ErrorResponse.of("VALIDATION_ERROR", errors));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception e) {
        log.error("未预期的错误", e);
        return ResponseEntity.internalServerError()
            .body(ErrorResponse.of("INTERNAL_ERROR", "服务器内部错误"));
    }
}
```

## 日志规范

### 日志级别使用

```java
@Slf4j
@Service
public class ResourceService {

    public void process(Request request) {
        // DEBUG: 调试信息
        log.debug("开始处理请求: {}", request);

        // INFO: 重要业务流程
        log.info("创建资源: name={}, type={}", request.getName(), request.getType());

        try {
            // 业务处理
            doProcess(request);

            // INFO: 成功的关键操作
            log.info("资源创建成功: id={}", result.getId());

        } catch (BusinessException e) {
            // WARN: 业务异常
            log.warn("资源创建失败: {}", e.getMessage());
            throw e;

        } catch (Exception e) {
            // ERROR: 系统异常
            log.error("资源创建异常: request={}", request, e);
            throw new SystemException("创建失败", e);
        }
    }
}
```

### 日志格式规范

```java
// ✅ 正确：使用参数化日志
log.info("用户登录成功: userId={}, ip={}", userId, ipAddress);

// ❌ 错误：字符串拼接
log.info("用户登录成功: userId=" + userId + ", ip=" + ipAddress);

// ✅ 正确：记录异常堆栈
log.error("处理失败: orderId={}", orderId, exception);

// ❌ 错误：只记录消息
log.error("处理失败: " + exception.getMessage());

// ✅ 正确：条件日志
if (log.isDebugEnabled()) {
    log.debug("详细信息: {}", expensiveOperation());
}

// ❌ 错误：总是执行昂贵操作
log.debug("详细信息: {}", expensiveOperation());
```

## 注释规范

### 类注释

```java
/**
 * 资源编排器
 * <p>
 * 负责协调资源相关的业务流程，包括：
 * - 资源的创建、更新、删除
 * - 资源状态管理
 * - 事件发布
 * </p>
 *
 * @author 张三
 * @since 2024-01-01
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ResourceOrchestrator {
    // ...
}
```

### 方法注释

```java
/**
 * 创建资源
 *
 * @param command 创建命令，包含资源名称、类型等信息
 * @return 创建的资源结果
 * @throws DuplicateResourceException 如果资源名称已存在
 * @throws ValidationException 如果参数校验失败
 */
@Transactional
public ResourceResult create(CreateResourceCommand command) {
    // ...
}
```

### 复杂逻辑注释

```java
public void complexProcess() {
    // 步骤 1: 验证前置条件
    validatePreconditions();

    // 步骤 2: 执行核心业务逻辑
    // 注意：这里需要考虑并发情况，使用乐观锁
    var result = performBusinessLogic();

    // 步骤 3: 发布领域事件
    // 事件会被异步处理，不影响主流程
    publishDomainEvent(result);

    // TODO: 后续需要添加审计日志功能
    // FIXME: 当前的重试机制可能导致重复数据，需要改进
}
```

## 测试规范

### 单元测试命名

```java
class ResourceOrchestratorTest {

    @Test
    void should_create_resource_when_valid_command() {
        // given
        var command = createValidCommand();

        // when
        var result = orchestrator.create(command);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getName()).isEqualTo(command.getName());
    }

    @Test
    void should_throw_exception_when_duplicate_name() {
        // given
        var command = createCommandWithDuplicateName();

        // when & then
        assertThatThrownBy(() -> orchestrator.create(command))
            .isInstanceOf(DuplicateResourceException.class)
            .hasMessageContaining("资源名称已存在");
    }
}
```

### 测试数据准备

```java
public class TestDataBuilder {

    public static ResourceDO createResourceDO() {
        var resource = new ResourceDO();
        resource.setId(1L);
        resource.setName("测试资源");
        resource.setType("TYPE_A");
        resource.setStatus(1);
        resource.setCreatedAt(LocalDateTime.now());
        return resource;
    }

    public static CreateResourceCommand createCommand() {
        return CreateResourceCommand.builder()
            .name("测试资源")
            .type("TYPE_A")
            .description("测试描述")
            .build();
    }
}
```

## 性能优化建议

### 数据库查询优化

```java
// ❌ 错误：N+1 查询问题
public List<OrderVO> getOrders() {
    var orders = orderMapper.selectList(null);
    return orders.stream().map(order -> {
        var items = itemMapper.selectByOrderId(order.getId()); // N 次查询
        return toVO(order, items);
    }).toList();
}

// ✅ 正确：批量查询
public List<OrderVO> getOrders() {
    var orders = orderMapper.selectList(null);
    var orderIds = orders.stream().map(Order::getId).toList();
    var itemsMap = itemMapper.selectByOrderIds(orderIds).stream()
        .collect(Collectors.groupingBy(Item::getOrderId));

    return orders.stream()
        .map(order -> toVO(order, itemsMap.get(order.getId())))
        .toList();
}
```

### 缓存使用

```java
@Service
@RequiredArgsConstructor
public class ResourceService {

    private final ResourceRepository repository;
    private final RedisTemplate<String, Resource> redisTemplate;

    @Cacheable(value = "resources", key = "#id")
    public Resource findById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("资源不存在"));
    }

    @CacheEvict(value = "resources", key = "#id")
    public void update(Long id, UpdateCommand command) {
        // 更新逻辑
    }

    @CacheEvict(value = "resources", allEntries = true)
    public void clearCache() {
        // 清理所有缓存
    }
}
```

### 异步处理

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class EventPublisher {

    private final ApplicationEventPublisher publisher;
    private final ThreadPoolTaskExecutor taskExecutor;

    @Async
    public void publishAsync(DomainEvent event) {
        log.info("异步发布事件: {}", event);
        publisher.publishEvent(event);
    }

    public CompletableFuture<Void> publishBatch(List<DomainEvent> events) {
        var futures = events.stream()
            .map(event -> CompletableFuture.runAsync(
                () -> publisher.publishEvent(event),
                taskExecutor
            ))
            .toArray(CompletableFuture[]::new);

        return CompletableFuture.allOf(futures);
    }
}
```