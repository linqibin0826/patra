# ADR-001: 可观测性组件 SPI 解耦架构

## 状态

已采纳 (2025-11-25)

## 背景

在 `patra-spring-boot-starter-redisson` 和 `patra-spring-boot-starter-observability` 的集成设计中，最初采用直接依赖方式：redisson 模块直接 import observability 模块中的 `LockMetricsRecorder` 类。

这种设计导致以下问题：

1. **编译期强依赖**：尽管 pom.xml 声明 observability 为 optional 依赖，但源码直接 import 导致编译必须依赖 observability
2. **配置分散**：可观测性配置（如 `patra.redisson.observability.*`）散落在各个 starter 中，难以统一管理
3. **违反单一职责**：各业务 starter 不应该管理可观测性逻辑

## 决策

采用 **SPI（Service Provider Interface）解耦模式**：

1. 在 `patra-spring-boot-starter-redisson` 模块定义 `LockObserver` 接口
2. 在 `patra-spring-boot-starter-observability` 模块实现 `LockMetricsRecorder implements LockObserver`
3. 通过 Spring 依赖注入，自动发现和注入实现

### 依赖关系变化

**重构前**：
```
redisson ---(optional)---> observability
    LockExecutor --> LockMetricsRecorder (编译期依赖)
```

**重构后**：
```
redisson <---(optional)--- observability
    LockExecutor --> LockObserver (接口)
                        ^
                        |
    LockMetricsRecorder -+ (实现)
```

### 接口定义

```java
// redisson 模块
public interface LockObserver {
    void onLockAcquired(String lockKey, String lockType, long waitTimeMs);
    void onLockFailed(String lockKey, String lockType, String reason);
    void onLockReleased(String lockKey, String lockType, long holdTimeMs);
}
```

### 自动配置

```java
// observability 模块
@Bean
@ConditionalOnClass(name = "org.redisson.api.RedissonClient")
@ConditionalOnBean(MeterRegistry.class)
public LockMetricsRecorder lockMetricsRecorder(MeterRegistry meterRegistry) {
    return new LockMetricsRecorder(meterRegistry);
}

// redisson 模块
@Bean
public LockExecutor lockExecutor(
    RedissonClient redissonClient,
    @Autowired(required = false) LockObserver lockObserver
) {
    return new LockExecutor(redissonClient, lockObserver);
}
```

## 影响

### 正面影响

1. **依赖倒置**：redisson 不再编译期依赖 observability，遵循 DIP 原则
2. **可选可观测性**：用户可以选择是否启用可观测性，不使用时不会引入额外依赖
3. **配置统一**：所有可观测性配置集中在 `patra.observability.*` 命名空间
4. **可扩展性**：用户可以自定义 `LockObserver` 实现

### 负面影响

1. **observability 依赖 redisson**：为了实现 `LockObserver` 接口，observability 需要 optional 依赖 redisson
2. **测试复杂度**：集成测试需要创建测试用的 `LockObserver` 实现

### 风险缓解

- observability 对 redisson 的依赖声明为 optional，不会传递给最终用户
- 使用 `@ConditionalOnClass` 确保只有当 redisson 在类路径时才注册 bean

## 替代方案

### 方案 1：事件驱动（ApplicationEvent）

通过 Spring 事件机制解耦：

```java
// redisson 发布事件
publisher.publishEvent(new LockAcquiredEvent(lockKey, lockType, waitTime));

// observability 监听事件
@EventListener
void handleLockAcquired(LockAcquiredEvent event) { ... }
```

**未采纳原因**：事件机制增加运行时开销，对于高频操作（锁获取/释放）不够高效。

### 方案 2：保持原有设计

保持 redisson optional 依赖 observability，接受编译期依赖。

**未采纳原因**：violates DIP，且导致配置分散。

## 相关文件

- `patra-spring-boot-starter-redisson/src/main/java/com/patra/starter/redisson/listener/LockObserver.java`
- `patra-spring-boot-starter-observability/src/main/java/com/patra/starter/observability/interceptor/redisson/LockMetricsRecorder.java`
- `patra-spring-boot-starter-redisson/src/main/java/com/patra/starter/redisson/lock/LockExecutor.java`
- `patra-spring-boot-starter-observability/src/main/java/com/patra/starter/observability/autoconfigure/ObservationInterceptorsAutoConfiguration.java`

## 参考

- [依赖倒置原则 (DIP)](https://en.wikipedia.org/wiki/Dependency_inversion_principle)
- [Java SPI 机制](https://docs.oracle.com/javase/tutorial/sound/SPI-intro.html)
- [Spring Conditional Beans](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.developing-auto-configuration.condition-annotations)
