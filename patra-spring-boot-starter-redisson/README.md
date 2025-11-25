# patra-spring-boot-starter-redisson

分布式锁和 Redis 基础设施 Starter，基于 Redisson 实现。

## 功能特性

### P0 核心功能

- ✅ **RedissonClient 自动配置**：支持单机/集群/哨兵/主从模式
- ✅ **@DistributedLock 声明式注解**：零代码获取/释放锁
- ✅ **SpEL 表达式支持**：动态生成锁键
- ✅ **多种锁类型**：可重入锁、公平锁、读写锁
- ✅ **统一异常处理**：集成 patra-common-core 异常体系

### P1 高级功能

- ✅ **Micrometer 指标集成**：锁等待时间、持有时间、成功/失败率（符合 Patra 命名规范）
- ✅ **性能优化**：SpEL 表达式缓存、静态字符串检测
- 🔜 **SkyWalking 追踪集成**：计划在 v1.2.0 支持

## 快速开始

### 1. 添加依赖

```xml
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-redisson</artifactId>
</dependency>
```

### 2. 配置 Redis

```yaml
spring:
  data:
    redis:
      redisson:
        config: |
          singleServerConfig:
            address: "redis://127.0.0.1:6379"
```

### 3. 使用 @DistributedLock 注解

```java
@Service
public class UserService {

    @DistributedLock(key = "user:#{#userId}", leaseTime = 30)
    public void updateUser(Long userId, User user) {
        // 业务逻辑自动受锁保护
    }
}
```

## 使用示例

### 基本用法

```java
// 简单变量
@DistributedLock(key = "user:#{#userId}")
public void updateUser(Long userId) { ... }

// 对象属性
@DistributedLock(key = "order:#{#order.id}")
public void processOrder(Order order) { ... }

// 复杂表达式
@DistributedLock(
    key = "#{#type}:#{#id}:#{T(java.time.LocalDate).now()}"
)
public void processTask(String type, Long id) { ... }
```

### 多种锁类型

```java
// 读锁（允许并发读）
@DistributedLock(
    key = "config:#{#key}",
    type = LockType.READ
)
public String getConfig(String key) { ... }

// 写锁（独占）
@DistributedLock(
    key = "config:#{#key}",
    type = LockType.WRITE
)
public void updateConfig(String key, String value) { ... }

// 公平锁（先到先得）
@DistributedLock(
    key = "queue:#{#queueId}",
    type = LockType.FAIR
)
public void processQueue(Long queueId) { ... }
```

### 看门狗机制

```java
// 显式设置 leaseTime（推荐）
@DistributedLock(
    key = "task:#{#taskId}",
    leaseTime = 60  // 业务需要 20s，设置 60s（业务时间 × 3）
)
public void processTask(Long taskId) { ... }

// 启用看门狗（业务时间不确定）
@DistributedLock(
    key = "task:#{#taskId}",
    leaseTime = -1  // 自动续期直到业务完成
)
public void processLongTask(Long taskId) { ... }
```

⚠️ **看门狗机制说明**：
- `leaseTime > 0`：锁在指定时间后自动释放（即使业务未完成）
- `leaseTime = -1`：启用看门狗，锁会自动续期直到业务完成
- `leaseTime = 0`：永不过期（不推荐，可能导致死锁）

**风险提示**：
- 使用 `-1` 时，如果业务逻辑挂起（如数据库死锁），锁将永久占用
- 看门狗续期失败会释放锁，但主线程不会收到通知
- **建议**：显式设置 leaseTime，而非依赖看门狗

### 等待锁配置

```java
// 不等待（默认）
@DistributedLock(
    key = "user:#{#userId}",
    waitTime = 0  // 直接返回失败
)
public void updateUser(Long userId) { ... }

// 等待 5 秒
@DistributedLock(
    key = "user:#{#userId}",
    waitTime = 5  // 等待最多 5 秒
)
public void updateUser(Long userId) { ... }
```

### 异常处理

```java
// 获取锁失败抛异常（默认）
@DistributedLock(
    key = "user:#{#userId}",
    throwExceptionOnFailure = true
)
public void updateUser(Long userId) { ... }

// 获取锁失败返回 null（方法不执行）
@DistributedLock(
    key = "user:#{#userId}",
    throwExceptionOnFailure = false
)
public void updateUser(Long userId) { ... }
```

## 配置说明

### 完整配置示例

```yaml
patra:
  redisson:
    # 是否启用 Redisson Starter
    enabled: true
    # 看门狗超时时间（毫秒）
    lock-watchdog-timeout: 30000

    # 锁配置
    lock:
      enabled: true
      key-prefix: "patra:lock:"  # 锁键前缀

    # 可观测性配置
    observability:
      metrics-enabled: true   # Micrometer 指标
      logging-enabled: true   # 日志记录
      log-level: DEBUG        # 日志级别（DEBUG/INFO/WARN/ERROR）
      tracing-enabled: false  # SkyWalking 追踪（v1.2.0 计划支持）
```

### 锁键命名规范

**推荐格式**：`{服务名}:{业务域}:{业务 ID}`

**示例**：

```java
// patra-catalog 服务
@DistributedLock(key = "catalog:mesh-import:#{#year}")
public void importMesh(int year) { ... }

// patra-ingest 服务
@DistributedLock(key = "ingest:harvest:#{#provenance}:#{#date}")
public void executeHarvest(String provenance, LocalDate date) { ... }
```

**配置服务专属前缀**：

```yaml
# patra-catalog/application.yml
patra:
  redisson:
    lock:
      key-prefix: "catalog:lock:"

# patra-ingest/application.yml
patra:
  redisson:
    lock:
      key-prefix: "ingest:lock:"
```

### ⚠️ 避免锁键冲突

不同服务使用相同的锁键会导致竞争，务必加上服务名前缀。

❌ **错误示例**：

```java
// patra-catalog
@DistributedLock(key = "data:#{#id}")

// patra-ingest（不同业务）
@DistributedLock(key = "data:#{#id}")

// 当 id=123 时，两个服务会竞争同一把锁 ❌
```

✅ **正确示例**：

```java
// patra-catalog
@DistributedLock(key = "catalog:data:#{#id}")

// patra-ingest
@DistributedLock(key = "ingest:data:#{#id}")
```

## 高可用场景：RedLock

### 适用场景

- 多 Redis 集群部署
- 对锁的可靠性要求极高（金融、支付场景）
- 可以容忍主从切换导致的锁丢失

### 配置示例

虽然 Patra Starter 不直接支持 RedLock，但可以通过以下方式集成：

```java
@Configuration
public class RedLockConfiguration {

    @Bean
    public RedissonClient redissonClient1() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://redis1:6379");
        return Redisson.create(config);
    }

    @Bean
    public RedissonClient redissonClient2() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://redis2:6379");
        return Redisson.create(config);
    }

    @Bean
    public RedissonClient redissonClient3() {
        Config config = new Config();
        config.useSingleServer().setAddress("redis://redis3:6379");
        return Redisson.create(config);
    }

    @Bean
    public RLock redLock(
        RedissonClient redissonClient1,
        RedissonClient redissonClient2,
        RedissonClient redissonClient3
    ) {
        return new RedissonRedLock(
            redissonClient1.getLock("myLock"),
            redissonClient2.getLock("myLock"),
            redissonClient3.getLock("myLock")
        );
    }
}

// 使用时需要手动获取锁（不能用 @DistributedLock）
@Service
public class PaymentService {

    @Autowired
    private RLock redLock;

    public void processPayment(Order order) {
        try {
            redLock.lock();
            // 业务逻辑
        } finally {
            redLock.unlock();
        }
    }
}
```

### 未来计划

在 v1.2.0 中考虑增加 RedLock 的注解支持。

## 可观测性

### Micrometer 指标

自动记录以下指标（符合 `patra.{module}.{metric}` 命名规范）：

| 指标名称 | 类型 | 描述 |
|----------|------|------|
| `patra.redisson.lock.acquired` | Counter | 锁获取成功计数 |
| `patra.redisson.lock.failed` | Counter | 锁获取失败计数 |
| `patra.redisson.lock.wait_time` | Timer | 锁等待时间 |
| `patra.redisson.lock.hold_time` | Timer | 锁持有时间 |

**标签（Tags）**：

| 标签 | 描述 | 基数 |
|------|------|------|
| `key_pattern` | 锁键模式（去除动态部分） | 低 |
| `lock_type` | 锁类型（REENTRANT/FAIR/READ/WRITE） | 低 |
| `reason` | 失败原因：`timeout`/`interrupted`/`infrastructure_error`（仅失败计数） | 低 |
| `application` | 应用名称（由 observability 模块自动添加） | 低 |
| `environment` | 环境标识（由 observability 模块自动添加） | 低 |

**高基数标签处理**：

锁键中的动态部分（如 ID、日期、UUID）会被自动过滤，只保留静态模式：

```
patra:lock:user:123        -> key_pattern="user"
patra:lock:order:456:item  -> key_pattern="order.item"
catalog:lock:mesh-import:2024 -> key_pattern="mesh-import"
```

### 日志记录

- **DEBUG**：锁获取/释放日志
- **WARN**：锁等待超时、锁持有时间过长
- **ERROR**：锁获取失败、锁释放异常

## 注意事项

### 与 @Transactional 一起使用

❌ **错误用法**：

```java
@Transactional
@DistributedLock(key = "user:#{#userId}")
public void updateUser(Long userId) {
    userRepository.update(userId, ...);
    // 锁在此释放，但事务未提交
}
```

✅ **正确用法 1：手动管理事务**

```java
@DistributedLock(key = "user:#{#userId}")
public void updateUser(Long userId) {
    transactionTemplate.execute(status -> {
        userRepository.update(userId, ...);
        return null;
    });
    // 事务已提交，锁再释放
}
```

✅ **正确用法 2：锁嵌套事务**

```java
@DistributedLock(key = "user:#{#userId}")
public void updateUser(Long userId) {
    updateUserInTransaction(userId);
}

@Transactional
private void updateUserInTransaction(Long userId) {
    userRepository.update(userId, ...);
}
```

## 依赖关系

```
patra-spring-boot-starter-redisson
├── patra-common-core（异常体系、工具类）
├── patra-spring-boot-starter-core（错误处理）
├── patra-spring-boot-starter-observability（可观测性：指标、日志、追踪）
├── redisson-spring-boot-starter（Redisson 官方 Starter）
└── spring-boot-starter-aop（AOP 支持）
```

> **说明**：强制依赖 observability 模块，确保所有指标自动应用命名规范、公共标签和高基数过滤。

## 性能优化

### SpEL 表达式缓存

Starter 内置了 SpEL 表达式缓存机制，自动缓存已解析的表达式：

```java
// 第一次调用：解析 SpEL 表达式并缓存
@DistributedLock(key = "user:#{#userId}")
public void updateUser(Long userId) { ... }

// 后续调用：直接使用缓存的 Expression 对象
// 性能提升：20-30%（高频调用场景）
```

### 静态字符串检测

对于静态锁键，Starter 会跳过 SpEL 解析，直接拼接前缀：

```java
// 静态键：无需 SpEL 解析，直接拼接
@DistributedLock(key = "global-config")
public void updateGlobalConfig() { ... }
// 实际键：patra:lock:global-config

// 动态键：走 SpEL 解析（有缓存）
@DistributedLock(key = "user:#{#userId}")
public void updateUser(Long userId) { ... }
```

**性能提示**：
- 能用静态键就用静态键（性能最优）
- 动态键会自动缓存（首次解析后性能接近静态键）

## 故障排查

### 问题 1：锁一直获取失败

**症状**：日志显示 `无法获取分布式锁`，业务逻辑不执行。

**可能原因**：
1. Redis 连接失败
2. `waitTime=0` 且锁被占用
3. 锁键冲突（多个服务使用相同锁键）

**解决方案**：

```yaml
# 1. 检查 Redis 连接
spring:
  data:
    redis:
      host: localhost
      port: 6379

# 2. 增加 waitTime
patra:
  redisson:
    lock:
      default-wait-time: 3000  # 等待 3 秒

# 3. 配置服务专属前缀
patra:
  redisson:
    lock:
      key-prefix: "catalog:lock:"  # 避免冲突
```

### 问题 2：锁提前释放，业务逻辑未完成

**症状**：业务执行中途，另一个线程获取了锁，导致并发问题。

**原因**：`leaseTime` 小于业务执行时间。

**解决方案**：

```java
// ❌ 错误：leaseTime 太短
@DistributedLock(key = "task:#{#id}", leaseTime = 5)
public void processTask(Long id) {
    // 业务逻辑需要 10 秒 → 锁会在 5 秒后自动释放
}

// ✅ 正确：leaseTime = 业务时间 × 2-3
@DistributedLock(key = "task:#{#id}", leaseTime = 30)
public void processTask(Long id) {
    // 业务逻辑需要 10 秒 → 设置 30 秒足够
}

// ✅ 或使用看门狗（业务时间不确定）
@DistributedLock(key = "task:#{#id}", leaseTime = -1)
public void processTask(Long id) {
    // 看门狗自动续期
}
```

### 问题 3：SpEL 表达式解析失败

**症状**：抛出 `LockExpressionException`。

**原因**：SpEL 表达式语法错误或参数为 null。

**解决方案**：

```java
// ❌ 错误：参数可能为 null
@DistributedLock(key = "user:#{#user.id}")
public void updateUser(User user) {
    // user 为 null 时抛异常
}

// ✅ 正确：使用 Elvis 运算符
@DistributedLock(key = "user:#{#user?.id ?: 'unknown'}")
public void updateUser(User user) {
    // user 为 null 时使用 'unknown'
}

// ✅ 或在方法开头检查
@DistributedLock(key = "user:#{#userId}")
public void updateUser(Long userId) {
    Objects.requireNonNull(userId, "userId 不能为 null");
    // ...
}
```

### 问题 4：与 @Transactional 一起使用导致数据不一致

**症状**：锁释放后，其他线程读到了旧数据。

**原因**：锁在方法结束时释放，但事务在方法结束后才提交。

**解决方案**：参见[注意事项 - 与 @Transactional 一起使用](#与-transactional-一起使用)。

### 问题 5：看门狗导致死锁

**症状**：使用 `leaseTime=-1` 后，锁永久占用。

**原因**：业务逻辑挂起（如数据库死锁），看门狗持续续期。

**解决方案**：

```java
// ❌ 不推荐：业务可能挂起
@DistributedLock(key = "task:#{#id}", leaseTime = -1)
public void processTask(Long id) {
    // 如果数据库死锁，锁将永久占用
}

// ✅ 推荐：显式设置 leaseTime
@DistributedLock(key = "task:#{#id}", leaseTime = 120)
public void processTask(Long id) {
    // 最多 120 秒，超时自动释放
}
```

## 可观测性监控

### Grafana Dashboard 示例

监控关键指标：

```promql
# 锁获取成功率（全局）
sum(rate(patra_redisson_lock_acquired_total[5m]))
/
(sum(rate(patra_redisson_lock_acquired_total[5m])) + sum(rate(patra_redisson_lock_failed_total[5m])))

# 按应用分组的锁获取成功率
sum(rate(patra_redisson_lock_acquired_total[5m])) by (application)
/
(sum(rate(patra_redisson_lock_acquired_total[5m])) by (application) + sum(rate(patra_redisson_lock_failed_total[5m])) by (application))

# 锁等待时间 P99
histogram_quantile(0.99, sum(rate(patra_redisson_lock_wait_time_seconds_bucket[5m])) by (le))

# 锁持有时间 P99
histogram_quantile(0.99, sum(rate(patra_redisson_lock_hold_time_seconds_bucket[5m])) by (le))

# 按锁模式分组的失败率
sum(rate(patra_redisson_lock_failed_total[5m])) by (key_pattern)

# 按应用和锁类型分组的等待时间
histogram_quantile(0.99, sum(rate(patra_redisson_lock_wait_time_seconds_bucket[5m])) by (le, application, lock_type))
```

## 技术规格

- **Java 版本**：25
- **Spring Boot 版本**：3.5.7
- **Redisson 版本**：3.52.0
- **Redis 最低版本**：6.0+

## 测试覆盖

- **单元测试**：22 个（异常、SpEL 解析、核心逻辑）
- **集成测试**：7 个（完整锁流程、并发场景、读写锁）
- **测试通过率**：100%

## 许可证

内部项目，未开源。

## 维护者

Patra Team

---

**文档版本**：v1.0.0
**最后更新**：2025-11-23
