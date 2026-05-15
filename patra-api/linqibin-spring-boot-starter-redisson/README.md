# Patra Redisson Starter

基于 Redisson 的分布式锁解决方案，提供声明式注解、看门狗自动续期、完整可观测性支持。

## 功能特性

- **声明式分布式锁**：`@DistributedLock` 注解，AOP 自动拦截
- **四种锁类型**：可重入锁、公平锁、读锁、写锁
- **看门狗机制**：自动续期，防止业务执行期间锁过期
- **SpEL 动态键**：支持方法参数、对象属性的动态表达式
- **性能优化**：SpEL 表达式缓存、静态字符串检测
- **可观测性**：Micrometer 指标 + Slf4j 日志
- **异常分类**：409/500/503 HTTP 状态码映射

## 快速开始

### 添加依赖

```xml
<dependency>
    <groupId>dev.linqibin.patra</groupId>
    <artifactId>patra-spring-boot-starter-redisson</artifactId>
    <version>${patra.version}</version>
</dependency>
```

### 配置 Redis

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
```

### 使用注解

```java
@Service
public class OrderService {

    @DistributedLock(key = "order:#{#orderId}")
    public void processOrder(String orderId) {
        // 同一订单同一时间只有一个线程执行
    }
}
```

## 注解属性

| 属性 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `key` | String | 必填 | 锁键表达式，支持 SpEL |
| `lockType` | LockType | REENTRANT | 锁类型 |
| `waitTime` | long | 3000 | 获取锁最大等待时间（毫秒） |
| `leaseTime` | long | -1 | 锁自动过期时间（-1 启用看门狗） |

## 锁类型

| 类型 | 说明 | 适用场景 |
|------|------|----------|
| `REENTRANT` | 可重入锁（默认） | 大多数业务场景 |
| `FAIR` | 公平锁 | 严格顺序保证 |
| `READ` | 读锁 | 多读操作并发 |
| `WRITE` | 写锁 | 独占写操作 |

## 使用示例

### 简单互斥

```java
@DistributedLock(key = "cache:refresh")
public void refreshCache() {
    // 全局唯一执行
}
```

### 动态锁键

```java
// 基于参数
@DistributedLock(key = "user:#{#userId}")
public void updateUser(String userId) { }

// 基于对象属性
@DistributedLock(key = "order:#{#request.orderId}")
public void createOrder(CreateOrderRequest request) { }

// 组合键
@DistributedLock(key = "payment:#{#userId}:#{#orderId}")
public void processPayment(String userId, String orderId) { }
```

### 短任务（关闭看门狗）

```java
@DistributedLock(
    key = "quick:task",
    waitTime = 0,       // 不等待
    leaseTime = 5000    // 5 秒后自动释放
)
public void quickTask() { }
```

### 读写锁

```java
@DistributedLock(key = "config", lockType = LockType.READ)
public Config readConfig() { }

@DistributedLock(key = "config", lockType = LockType.WRITE)
public void updateConfig(Config config) { }
```

## 看门狗机制

**启用条件**：`leaseTime = -1`（默认）

**工作原理**：
- 锁持有期间，每 10 秒检查一次（watchdogTimeout/3）
- 如果锁仍被持有，自动续期至 watchdogTimeout
- 方法执行完成后立即释放
- 应用崩溃时，锁在 watchdogTimeout 后自动过期

**场景选择**：

| 场景 | 建议 |
|------|------|
| 长时间运行任务 | 启用看门狗（默认） |
| 执行时间不确定 | 启用看门狗（默认） |
| 短任务（<1秒） | 手动设置 leaseTime |
| 已知最大执行时间 | 手动设置 leaseTime |

## 异常处理

| 异常类 | HTTP 状态 | 说明 |
|--------|----------|------|
| `LockAcquisitionException` | 409 | 获取锁超时，建议重试 |
| `LockInfrastructureException` | 503 | Redis 连接错误 |
| `LockExpressionException` | 500 | SpEL 表达式错误 |
| `LockTimeoutException` | 500 | 锁操作超时 |

```java
try {
    orderService.processOrder(orderId);
} catch (LockAcquisitionException e) {
    // 订单正在处理中，稍后重试
} catch (LockInfrastructureException e) {
    // 服务暂时不可用
}
```

## 配置属性

```yaml
patra:
  redisson:
    enabled: true                    # 总开关
    lock-watchdog-timeout: 30000     # 看门狗超时（毫秒）

    lock:
      enabled: true                  # 分布式锁开关
      default-wait-time: 3000        # 默认等待时间
      default-lease-time: -1         # 默认租约时间（-1 启用看门狗）
      key-prefix: "patra:lock:"      # 锁键前缀
```

## 可观测性

可观测性功能由 `patra-spring-boot-starter-observability` 提供（可选依赖）。

### 启用方式

添加 observability 依赖后，自动启用分布式锁指标记录：

```xml
<dependency>
    <groupId>dev.linqibin.patra</groupId>
    <artifactId>patra-spring-boot-starter-observability</artifactId>
</dependency>
```

### 指标（Micrometer）

| 指标名 | 类型 | 说明 |
|--------|------|------|
| `patra.redisson.lock.acquired` | Counter | 获取成功次数 |
| `patra.redisson.lock.failed` | Counter | 获取失败次数 |
| `patra.redisson.lock.wait_time` | Timer | 等待时间分布 |
| `patra.redisson.lock.hold_time` | Timer | 持有时间分布 |

**标签**：
- `key_pattern`：锁键模式（低基数，自动去除动态部分）
- `lock_type`：锁类型
- `reason`：失败原因（仅失败时）

### 日志

```
DEBUG - 锁获取成功: key=patra:lock:order:12345, type=REENTRANT, waitTime=15ms
DEBUG - 锁释放: key=patra:lock:order:12345, holdTime=120ms
WARN  - 锁获取失败: key=patra:lock:order:12345, reason=timeout
```

### 扩展点

可以实现 `LockObserver` 接口自定义可观测性行为：

```java
@Bean
public LockObserver customLockObserver() {
    return new LockObserver() {
        @Override
        public void onLockAcquired(String lockKey, String lockType, long waitTimeMs) {
            // 自定义处理
        }
        // ... 其他方法
    };
}
```

## 模块结构

```
patra-spring-boot-starter-redisson/
├── lock/                       # 核心锁实现
│   ├── DistributedLock.java    # 声明式注解
│   ├── LockType.java           # 锁类型枚举
│   ├── LockContext.java        # 锁上下文
│   ├── LockKeyGenerator.java   # SpEL 键生成
│   ├── LockExecutor.java       # 锁执行引擎
│   └── LockAspect.java         # AOP 切面
├── config/                     # 配置
│   └── RedissonProperties.java
├── exception/                  # 异常体系
│   ├── LockErrorCode.java
│   ├── LockAcquisitionException.java
│   ├── LockInfrastructureException.java
│   ├── LockExpressionException.java
│   └── LockTimeoutException.java
├── listener/                   # 扩展点
│   └── LockObserver.java       # SPI 接口（observability 实现）
└── autoconfigure/              # 自动配置
    ├── RedissonAutoConfiguration.java
    └── LockAutoConfiguration.java
```

## 依赖关系

- `redisson-spring-boot-starter`：Redisson 官方 Starter
- `spring-boot-starter-aop`：AOP 支持
- `patra-common-core`：错误码框架

## 设计原则

1. **关注点分离**：锁逻辑与业务逻辑分离（AOP）
2. **性能优先**：SpEL 缓存、静态检测
3. **异常安全**：finally 释放锁，业务异常透传
4. **可扩展性**：通过 LockObserver SPI 支持自定义生命周期观察
