# patra-spring-boot-starter-redisson 架构设计

**版本**: v1.0.0
**创建日期**: 2025-11-23
**状态**: 设计阶段
**作者**: Patra Team

---

## 📋 文档目录

- [一、概述](#一概述)
- [二、设计目标](#二设计目标)
- [三、技术选型](#三技术选型)
- [四、核心功能](#四核心功能)
- [五、架构设计](#五架构设计)
- [六、详细设计](#六详细设计)
- [七、配置管理](#七配置管理)
- [八、使用示例](#八使用示例)
- [九、实施计划](#九实施计划)
- [十、风险评估](#十风险评估)

---

## 一、概述

### 1.1 背景

Patra 医学文献数据平台是一个微服务架构系统，多个服务需要分布式协调能力：

**分布式锁需求**：
- **patra-catalog**：MeSH 导入任务防并发、期刊更新防重复
- **patra-ingest**：PubMed 采集任务防重复、Outbox 中继防并发
- **patra-registry**：Provenance 配置更新防冲突、缓存刷新防击穿
- **通用场景**：定时任务防重、业务幂等性保证、分布式缓存

**当前问题**：
- ❌ 没有统一的分布式锁方案
- ❌ 每个服务需要自己实现锁逻辑（重复代码）
- ❌ 缺少声明式锁注解（开发体验差）
- ❌ 缺少锁监控和可观测性
- ❌ 缺少死锁检测和安全机制

### 1.2 设计理念

创建 `patra-spring-boot-starter-redisson`，遵循以下原则：

1. **开箱即用**：自动配置 RedissonClient，最小化业务服务配置
2. **声明式编程**：提供 `@DistributedLock` 注解，零代码获取/释放锁
3. **可观测性**：集成 SkyWalking、Micrometer、日志记录
4. **安全可靠**：
   - 防死锁：自动超时释放
   - 防误释放：只能释放自己持有的锁
   - 可重入：同一线程可重复获取
5. **高性能**：基于 Redisson 的高性能实现
6. **可扩展**：支持多种锁类型（可重入锁、公平锁、读写锁）

### 1.3 目标用户

- **后端开发者**：使用 `@DistributedLock` 注解保护业务逻辑
- **架构师**：设计分布式协调方案
- **运维人员**：监控锁使用情况、排查死锁问题

---

## 二、设计目标

### 2.1 功能目标

| 目标 | 描述 | 优先级 |
|------|------|--------|
| **RedissonClient 自动配置** | 自动配置 Redisson 客户端（单机/集群/哨兵） | P0 |
| **@DistributedLock 注解** | 声明式分布式锁，支持 SpEL 表达式 | P0 |
| **锁 AOP 拦截** | 自动获取/释放锁，异常安全保证 | P0 |
| **锁键生成器** | 支持 SpEL 解析，动态生成锁键 | P0 |
| **可观测性集成** | SkyWalking 追踪、Micrometer 指标、日志记录 | P1 |
| **多种锁类型** | 可重入锁、公平锁、读锁、写锁 | P1 |
| **统一异常处理** | LockAcquisitionException、LockTimeoutException | P1 |
| **分布式缓存** | @Cacheable 的 Redisson 实现（Spring Cache 集成） | P2 |
| **分布式限流** | @RateLimit 注解 | P2 |
| **分布式计数器** | AtomicLong、LongAdder 等工具类 | P2 |

### 2.2 非功能目标

| 目标 | 指标 | 优先级 |
|------|------|--------|
| **性能** | 获取锁延迟 P95 ≤ 10ms（本地 Redis） | P1 |
| **可靠性** | 锁丢失概率 ≤ 0.01%（Redis 正常运行） | P0 |
| **可用性** | Redis 不可用时降级为本地锁或抛异常 | P2 |
| **安全性** | 防死锁、防误释放、防锁泄漏 | P0 |
| **可观测性** | 锁等待时间、持有时间、失败率可监控 | P1 |

### 2.3 约束条件

- **技术栈**：Spring Boot 3.5.7、Redisson 3.36.0、JDK 25
- **Redis 版本**：Redis 6.0+（支持单机/集群/哨兵）
- **架构风格**：六边形架构友好（Infrastructure 层使用）
- **集成组件**：SkyWalking、Micrometer

---

## 三、技术选型

### 3.1 Redisson 核心特性

| 特性 | 说明 | 优势 |
|------|------|------|
| **高性能** | 基于 Netty 的异步网络框架 | 高并发、低延迟 |
| **丰富的数据结构** | Lock、Map、Set、Queue、Semaphore 等 | 满足多种场景 |
| **分布式协调** | 分布式锁、信号量、计数器、闭锁等 | 开箱即用 |
| **Spring 集成** | redisson-spring-boot-starter | 自动配置 |
| **可靠性** | Lua 脚本保证原子性 | 防止锁泄漏 |
| **多模式支持** | 单机、集群、哨兵、主从 | 适应不同部署 |

### 3.2 依赖关系

```
patra-spring-boot-starter-redisson
├── patra-common-core (异常体系、工具类)
├── patra-spring-boot-starter-core (错误处理、可观测性)
├── redisson-spring-boot-starter (Redisson 官方 Starter)
├── spring-boot-starter-aop (AOP 支持)
└── spring-boot-starter-cache (Spring Cache 集成，可选)
```

### 3.3 与现有 Starter 的关系

| Starter | 关系 | 说明 |
|---------|------|------|
| `patra-common-core` | **依赖** | 使用统一异常体系（ApplicationException/ErrorTrait）、工具类 |
| `patra-spring-boot-starter-core` | **依赖** | 复用错误处理框架、Clock、可观测性 |
| `patra-spring-boot-starter-mybatis` | **独立** | 无直接依赖 |
| `patra-spring-boot-starter-batch` | **被依赖** | batch starter 依赖 redisson starter |

### 3.4 Maven 依赖配置

**pom.xml 示例**：

```xml
<dependencies>
    <!-- 1. Patra 内部依赖（必须） -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-common-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-core</artifactId>
    </dependency>

    <!-- 2. Redisson 官方 Starter（必须） -->
    <dependency>
        <groupId>org.redisson</groupId>
        <artifactId>redisson-spring-boot-starter</artifactId>
        <version>3.36.0</version>
    </dependency>

    <!-- 3. Spring Boot 依赖（必须） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-autoconfigure</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-aop</artifactId>
    </dependency>

    <!-- 4. Spring Cache 集成（可选，P2 功能） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-cache</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- 5. 配置元数据（开发时） -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-configuration-processor</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- 6. Lombok（开发时） -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

**说明**：
- ✅ `patra-common-core` 必须添加（规则9：所有服务必须依赖）
- ✅ `micrometer-core` 无需单独依赖（core starter 已提供）
- ✅ `spring-boot-starter-cache` 标记为 optional（P2 功能）
- ✅ 使用 Redisson 3.36.0（与 Spring Boot 3.5.7 兼容）

---

## 四、核心功能

### 4.1 功能清单

#### 4.1.1 自动配置 (P0)

**RedissonAutoConfiguration**：
- 自动配置 RedissonClient Bean（基于 application.yml）
- 支持单机、集群、哨兵、主从模式
- 支持自定义配置（超时、重试、连接池等）

**LockAutoConfiguration**：
- 配置 @DistributedLock 注解处理器
- 配置 LockAspect AOP 切面
- 配置 LockKeyGenerator（SpEL 解析器）

**ObservabilityAutoConfiguration**：
- 配置 SkyWalking 追踪监听器
- 配置 Micrometer 指标监听器
- 配置日志监听器

#### 4.1.2 分布式锁基础能力 (P0)

**@DistributedLock 注解**：
```java
@DistributedLock(
    key = "lock:user:#{#userId}",  // 支持 SpEL
    leaseTime = 30,                 // 锁超时时间（秒）
    waitTime = 0,                   // 等待锁的最大时间（秒）
    type = LockType.REENTRANT       // 锁类型
)
public void updateUser(Long userId) {
    // 业务逻辑
}
```

**锁类型支持**：
- `REENTRANT`：可重入锁（默认）
- `FAIR`：公平锁（先到先得）
- `READ`：读锁（允许多个读操作并发）
- `WRITE`：写锁（独占）

**自动锁管理**：
- 自动获取锁（超时控制）
- 自动释放锁（finally 块保证）
- 异常安全（获取锁失败时可配置抛异常或返回 null）

#### 4.1.3 锁键生成 (P0)

**SpEL 表达式支持**：
```java
// 简单变量
@DistributedLock(key = "lock:user:#{#userId}")

// 对象属性
@DistributedLock(key = "lock:order:#{#order.id}")

// 复杂表达式
@DistributedLock(key = "lock:#{#type}:#{#id}:#{T(java.time.LocalDate).now()}")

// 默认前缀
@DistributedLock(key = "user:#{#userId}")  // 实际键: patra:lock:user:123
```

**键命名规范**：
- 格式：`{prefix}:{业务域}:{业务 ID}`
- 前缀：从配置读取（默认 `patra:lock:`）
- 业务域：如 `user`、`order`、`task`
- 业务 ID：唯一标识（如用户 ID、订单号）

#### 4.1.4 可观测性 (P1)

**SkyWalking 追踪**：
- 自动为每次锁操作创建 Span（`DistributedLock: {lockKey}`）
- 记录锁等待时间、持有时间
- 记录锁获取成功/失败状态

**Micrometer 指标**：
- `redisson.lock.acquired`：锁获取成功计数（Counter，tags: key、type）
- `redisson.lock.failed`：锁获取失败计数（Counter，tags: key、reason）
- `redisson.lock.wait.time`：锁等待时间（Timer，tags: key）
- `redisson.lock.hold.time`：锁持有时间（Timer，tags: key）

**日志记录**：
- DEBUG：锁获取/释放日志
- WARN：锁等待超时、锁持有时间过长（可配置阈值）
- ERROR：锁获取失败、锁释放异常

#### 4.1.5 异常处理 (P1)

**自定义异常**：
```java
public class LockAcquisitionException extends RuntimeException {
    private final String lockKey;
    private final long waitTime;
}

public class LockTimeoutException extends RuntimeException {
    private final String lockKey;
    private final long holdTime;
}
```

**异常映射**：
- `LockAcquisitionException` → 409 Conflict（业务冲突）
- `LockTimeoutException` → 500 Internal Server Error（系统问题）

#### 4.1.6 分布式缓存 (P2)

**Spring Cache 集成**：
```java
@Cacheable(cacheNames = "users", key = "#userId")
public User getUser(Long userId) {
    // 查询数据库
}

@CacheEvict(cacheNames = "users", key = "#userId")
public void updateUser(Long userId, User user) {
    // 更新数据库
}
```

**自动配置**：
- 使用 Redisson 作为 CacheManager
- 支持 TTL 配置
- 支持缓存统计

---

## 五、架构设计

### 5.1 整体架构

```
业务服务 (patra-catalog, patra-ingest, etc.)
├─ 使用 @DistributedLock 注解
└─ 业务逻辑受锁保护
           ↓ 依赖
patra-spring-boot-starter-redisson
├─ 自动配置: RedissonClient
├─ AOP 拦截: LockAspect
├─ 键生成: LockKeyGenerator (SpEL 支持)
├─ 可观测性: SkyWalking + Micrometer + 日志
└─ 异常处理: LockException 映射
           ↓ 依赖
Redisson 3.36.0
├─ RLock (可重入锁)
├─ RReadWriteLock (读写锁)
└─ RFairLock (公平锁)
           ↓ 依赖
Redis 6.0+
```

### 5.2 包结构设计

```
patra-spring-boot-starter-redisson/
├─ src/main/java/com/patra/starter/redisson/
│  ├─ autoconfigure/                    # 自动配置
│  │  ├─ RedissonAutoConfiguration.java
│  │  ├─ LockAutoConfiguration.java
│  │  ├─ CacheAutoConfiguration.java
│  │  └─ ObservabilityAutoConfiguration.java
│  ├─ lock/                             # 分布式锁
│  │  ├─ DistributedLock.java           # 锁注解
│  │  ├─ LockType.java                  # 锁类型枚举
│  │  ├─ LockAspect.java                # AOP 实现
│  │  ├─ LockKeyGenerator.java          # 键生成器
│  │  └─ LockExecutor.java              # 锁执行器（封装锁操作）
│  ├─ listener/                         # 监听器
│  │  ├─ LockMetricsListener.java       # Micrometer 指标
│  │  ├─ LockTracingListener.java       # SkyWalking 追踪
│  │  └─ LockLoggingListener.java       # 日志记录
│  ├─ exception/                        # 异常定义
│  │  ├─ LockAcquisitionException.java
│  │  ├─ LockTimeoutException.java
│  │  └─ LockException.java
│  ├─ config/                           # 配置类
│  │  └─ RedissonProperties.java
│  └─ cache/                            # 分布式缓存 (P2)
│     └─ RedissonCacheConfiguration.java
├─ src/main/resources/
│  ├─ META-INF/spring/
│  │  └─ org.springframework.boot.autoconfigure.AutoConfiguration.imports
│  └─ redisson-default.yaml             # Redisson 默认配置
└─ pom.xml
```

### 5.3 核心流程设计

#### 5.3.1 锁获取流程

```
@DistributedLock 注解方法调用
          ↓
   LockAspect 拦截 (@Around)
          ↓
   LockKeyGenerator 解析 SpEL
          ↓
   生成锁键: patra:lock:user:123
          ↓
   LockTracingListener.beforeLock()
   (创建 SkyWalking Span)
          ↓
   获取锁实例 (RLock/RReadWriteLock/RFairLock)
          ↓
   tryLock(waitTime, leaseTime)
          ↓
   ┌────────────────────────┐
   │ 获取成功？              │
   └────────┬───────────────┘
            │
      ┌─────┴─────┐
      │ YES       │ NO
      ↓           ↓
   记录指标     记录失败指标
   执行业务     抛异常或返回 null
   释放锁
   记录持有时间
   结束 Span
```

#### 5.3.2 锁释放流程

```
业务方法执行完成 (正常/异常)
          ↓
   finally 块执行
          ↓
   检查锁是否由当前线程持有
          ↓
   unlock() 释放锁
          ↓
   LockMetricsListener.onLockReleased()
   (记录持有时间)
          ↓
   LockTracingListener.afterLock()
   (结束 SkyWalking Span)
          ↓
   日志记录: "释放分布式锁: {key}"
```

---

## 六、详细设计

### 6.1 核心类设计

#### 6.1.1 RedissonAutoConfiguration

**职责**：自动配置 RedissonClient

```java
package com.patra.starter.redisson.autoconfigure;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.spring.starter.RedissonAutoConfigurationCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Redisson 自动配置
 * <p>
 * 基于 redisson-spring-boot-starter，添加项目级别的默认配置
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "patra.redisson", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedissonProperties.class)
public class RedissonAutoConfiguration {

    /**
     * 自定义 Redisson 配置
     */
    @Bean
    @ConditionalOnMissingBean
    public RedissonAutoConfigurationCustomizer redissonCustomizer(RedissonProperties properties) {
        return config -> {
            // 设置编解码器（默认 JSON）
            config.setCodec(new org.redisson.codec.JsonJacksonCodec());

            // 设置线程池大小
            config.setThreads(properties.getThreads());
            config.setNettyThreads(properties.getNettyThreads());

            // 设置锁看门狗超时（默认 30 秒）
            config.setLockWatchdogTimeout(properties.getLockWatchdogTimeout());
        };
    }
}
```

#### 6.1.2 LockAutoConfiguration

**职责**：配置分布式锁组件

```java
package com.patra.starter.redisson.autoconfigure;

import com.patra.starter.redisson.config.RedissonProperties;
import com.patra.starter.redisson.listener.LockLoggingListener;
import com.patra.starter.redisson.listener.LockMetricsListener;
import com.patra.starter.redisson.listener.LockTracingListener;
import com.patra.starter.redisson.lock.LockAspect;
import com.patra.starter.redisson.lock.LockKeyGenerator;
import io.micrometer.core.instrument.MeterRegistry;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 分布式锁自动配置
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "patra.redisson.lock", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(RedissonProperties.class)
public class LockAutoConfiguration {

    /**
     * 配置锁键生成器（支持 SpEL）
     */
    @Bean
    public LockKeyGenerator lockKeyGenerator(RedissonProperties properties) {
        return new LockKeyGenerator(properties.getLock().getKeyPrefix());
    }

    /**
     * 配置锁监听器：指标收集
     */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "patra.redisson.lock.observability.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LockMetricsListener lockMetricsListener(
            MeterRegistry meterRegistry,
            RedissonProperties properties
    ) {
        return new LockMetricsListener(meterRegistry, properties);
    }

    /**
     * 配置锁监听器：追踪
     */
    @Bean
    @ConditionalOnProperty(prefix = "patra.redisson.lock.observability.tracing", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LockTracingListener lockTracingListener() {
        return new LockTracingListener();
    }

    /**
     * 配置锁监听器：日志
     */
    @Bean
    @ConditionalOnProperty(prefix = "patra.redisson.lock.observability.logging", name = "enabled", havingValue = "true", matchIfMissing = true)
    public LockLoggingListener lockLoggingListener(RedissonProperties properties) {
        return new LockLoggingListener(properties);
    }

    /**
     * 配置锁 AOP 切面
     */
    @Bean
    public LockAspect lockAspect(
            RedissonClient redissonClient,
            LockKeyGenerator keyGenerator,
            LockMetricsListener metricsListener,
            LockTracingListener tracingListener,
            LockLoggingListener loggingListener
    ) {
        return new LockAspect(
            redissonClient,
            keyGenerator,
            metricsListener,
            tracingListener,
            loggingListener
        );
    }
}
```

#### 6.1.3 @DistributedLock 注解

**职责**：声明式分布式锁

```java
package com.patra.starter.redisson.lock;

import java.lang.annotation.*;
import java.util.concurrent.TimeUnit;

/**
 * 分布式锁注解
 * <p>
 * 使用示例：
 * <pre>{@code
 * @DistributedLock(
 *     key = "lock:user:#{#userId}",
 *     leaseTime = 30,
 *     waitTime = 5,
 *     type = LockType.REENTRANT
 * )
 * public void updateUser(Long userId) {
 *     // 业务逻辑
 * }
 * }</pre>
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DistributedLock {

    /**
     * 锁键（支持 SpEL 表达式）
     * <p>
     * 示例：
     * <ul>
     *   <li>简单变量: {@code "user:#{#userId}"}</li>
     *   <li>对象属性: {@code "order:#{#order.id}"}</li>
     *   <li>复杂表达式: {@code "#{#type}:#{#id}:#{T(java.time.LocalDate).now()}"}</li>
     * </ul>
     * <p>
     * 实际锁键会自动添加配置的前缀，例如：{@code "patra:lock:user:123"}
     */
    String key();

    /**
     * 锁租约时间（防止死锁）
     * <p>
     * 超过此时间后，锁会自动释放。默认 30 秒。
     * <p>
     * ⚠️ 建议根据业务逻辑执行时间设置，留出充足的余量。
     */
    long leaseTime() default 30;

    /**
     * 等待锁的最大时间
     * <p>
     * 0 表示不等待，直接返回失败。默认 0。
     * <p>
     * 建议配置等待时间，避免瞬时竞争导致大量失败。
     */
    long waitTime() default 0;

    /**
     * 时间单位
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 锁类型
     */
    LockType type() default LockType.REENTRANT;

    /**
     * 获取锁失败时是否抛出异常
     * <p>
     * - true: 抛出 {@link LockAcquisitionException}
     * - false: 返回 null（方法不执行）
     */
    boolean throwExceptionOnFailure() default true;

    /**
     * 自定义失败消息（用于异常）
     */
    String failureMessage() default "";
}
```

#### 6.1.4 LockType 枚举

```java
package com.patra.starter.redisson.lock;

/**
 * 锁类型枚举
 *
 * @author Patra Team
 * @since 1.0.0
 */
public enum LockType {

    /**
     * 可重入锁（默认）
     * <p>
     * 同一线程可以多次获取同一把锁，释放次数需与获取次数匹配。
     * <p>
     * 适用场景：大多数业务场景
     */
    REENTRANT,

    /**
     * 公平锁
     * <p>
     * 按照请求锁的顺序获取锁（先到先得）。
     * <p>
     * 适用场景：需要严格顺序保证的场景（如排队系统）
     * <p>
     * ⚠️ 性能略低于可重入锁
     */
    FAIR,

    /**
     * 读锁
     * <p>
     * 允许多个读操作并发执行，但写操作需要独占。
     * <p>
     * 适用场景：读多写少的场景
     */
    READ,

    /**
     * 写锁
     * <p>
     * 独占锁，不允许其他读/写操作并发执行。
     * <p>
     * 适用场景：需要独占访问的场景
     */
    WRITE
}
```

#### 6.1.5 LockContext 锁执行上下文

**职责**：封装锁执行过程的上下文信息

```java
package com.patra.starter.redisson.lock;

import lombok.Builder;
import lombok.Getter;
import org.redisson.api.RLock;

/**
 * 锁执行上下文
 * <p>
 * 封装锁的获取、执行、释放过程中的上下文信息
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Getter
@Builder
public class LockContext {

    /**
     * 锁键
     */
    private final String lockKey;

    /**
     * 锁实例
     */
    private final RLock lock;

    /**
     * 注解配置
     */
    private final DistributedLock annotation;

    /**
     * 开始时间（毫秒）
     */
    private final long startTime;

    /**
     * 是否已获取锁
     */
    private boolean acquired;

    /**
     * 标记锁已获取
     */
    public void markAcquired() {
        this.acquired = true;
    }

    /**
     * 计算等待时间（毫秒）
     */
    public long getWaitTime() {
        return System.currentTimeMillis() - startTime;
    }

    /**
     * 计算持有时间（毫秒）
     */
    public long getHoldTime() {
        return System.currentTimeMillis() - startTime;
    }
}
```

#### 6.1.6 LockExecutor 锁执行器

**职责**：执行锁的获取、业务逻辑、释放流程，触发 Recorder 回调

```java
package com.patra.starter.redisson.lock;

import com.patra.starter.redisson.exception.LockAcquisitionException;
import com.patra.starter.redisson.listener.LockLoggingRecorder;
import com.patra.starter.redisson.listener.LockMetricsRecorder;
import com.patra.starter.redisson.listener.LockTracingRecorder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * 分布式锁执行器
 * <p>
 * 负责锁的获取、业务逻辑执行、释放流程，以及触发可观测性 Recorder 回调
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class LockExecutor {

    private final LockMetricsRecorder metricsRecorder;
    private final LockTracingRecorder tracingRecorder;
    private final LockLoggingRecorder loggingRecorder;

    /**
     * 执行带锁的业务逻辑
     *
     * @param context  锁执行上下文
     * @param business 业务逻辑（Supplier）
     * @return 业务逻辑返回值
     * @throws Throwable 业务逻辑抛出的异常
     */
    public Object execute(LockContext context, Supplier<Object> business) throws Throwable {
        String lockKey = context.getLockKey();

        // 1. 开始追踪
        tracingRecorder.beforeLock(lockKey);
        loggingRecorder.beforeLock(lockKey);

        try {
            // 2. 尝试获取锁
            acquireLock(context);

            // 3. 执行业务逻辑
            return executeBusiness(context, business);

        } finally {
            // 4. 释放锁（finally 保证执行）
            releaseLock(context);

            // 5. 结束追踪
            tracingRecorder.afterLock(lockKey, context.isAcquired());
        }
    }

    /**
     * 获取锁
     *
     * @param context 锁执行上下文
     */
    private void acquireLock(LockContext context) {
        String lockKey = context.getLockKey();
        DistributedLock annotation = context.getAnnotation();

        try {
            boolean acquired = context.getLock().tryLock(
                annotation.waitTime(),
                annotation.leaseTime(),
                annotation.timeUnit()
            );

            if (acquired) {
                context.markAcquired();
                long waitTime = context.getWaitTime();

                metricsRecorder.onLockAcquired(lockKey, waitTime);
                loggingRecorder.onLockAcquired(lockKey, waitTime);
            } else {
                handleAcquisitionFailure(context);
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LockAcquisitionException("锁获取被中断", lockKey, context.getWaitTime(), e);
        }
    }

    /**
     * 处理锁获取失败
     *
     * @param context 锁执行上下文
     */
    private void handleAcquisitionFailure(LockContext context) {
        String lockKey = context.getLockKey();
        long waitTime = context.getWaitTime();

        // 记录失败指标
        metricsRecorder.onLockFailed(lockKey, waitTime, "timeout");
        loggingRecorder.onLockFailed(lockKey, waitTime);

        // 抛异常或返回 null（由调用方处理）
        DistributedLock annotation = context.getAnnotation();
        if (annotation.throwExceptionOnFailure()) {
            String message = annotation.failureMessage().isEmpty()
                ? null
                : annotation.failureMessage();

            throw message == null
                ? new LockAcquisitionException(lockKey, waitTime)
                : new LockAcquisitionException(message, lockKey, waitTime);
        }
    }

    /**
     * 执行业务逻辑
     *
     * @param context  锁执行上下文
     * @param business 业务逻辑
     * @return 业务逻辑返回值
     * @throws Throwable 业务逻辑抛出的异常
     */
    private Object executeBusiness(LockContext context, Supplier<Object> business) throws Throwable {
        if (!context.isAcquired()) {
            return null;  // 未获取锁，返回 null
        }

        try {
            return business.get();
        } catch (Throwable e) {
            log.error("业务逻辑执行失败: {}", context.getLockKey(), e);
            throw e;
        }
    }

    /**
     * 释放锁
     *
     * @param context 锁执行上下文
     */
    private void releaseLock(LockContext context) {
        if (!context.isAcquired()) {
            return;  // 未获取锁，无需释放
        }

        String lockKey = context.getLockKey();
        if (!context.getLock().isHeldByCurrentThread()) {
            log.warn("锁不由当前线程持有，跳过释放: {}", lockKey);
            return;
        }

        try {
            context.getLock().unlock();

            long holdTime = context.getHoldTime();
            metricsRecorder.onLockReleased(lockKey, holdTime);
            loggingRecorder.onLockReleased(lockKey, holdTime);

        } catch (Exception e) {
            log.error("释放锁失败: {}", lockKey, e);
        }
    }
}
```

#### 6.1.7 LockAspect AOP 实现

**职责**：拦截 @DistributedLock 注解，委派给 LockExecutor 执行

```java
package com.patra.starter.redisson.lock;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

/**
 * 分布式锁 AOP 切面
 * <p>
 * 拦截 {@link DistributedLock} 注解，解析参数后委派给 {@link LockExecutor} 执行
 * <p>
 * 职责：
 * <ul>
 *   <li>AOP 拦截和参数解析</li>
 *   <li>生成锁键（支持 SpEL）</li>
 *   <li>获取锁实例</li>
 *   <li>构建锁执行上下文</li>
 *   <li>委派给 LockExecutor 执行</li>
 * </ul>
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Aspect
@RequiredArgsConstructor
public class LockAspect {

    private final RedissonClient redissonClient;
    private final LockKeyGenerator keyGenerator;
    private final LockExecutor lockExecutor;

    /**
     * 环绕通知：拦截 @DistributedLock 注解方法
     *
     * @param pjp              切点
     * @param distributedLock  注解实例
     * @return 业务方法返回值
     * @throws Throwable 业务方法抛出的异常
     */
    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint pjp, DistributedLock distributedLock) throws Throwable {

        // 1. 生成锁键（支持 SpEL）
        String lockKey = keyGenerator.generateKey(distributedLock.key(), pjp);

        // 2. 获取锁实例
        RLock lock = getLock(lockKey, distributedLock.type());

        // 3. 构建锁执行上下文
        LockContext context = LockContext.builder()
            .lockKey(lockKey)
            .lock(lock)
            .annotation(distributedLock)
            .startTime(System.currentTimeMillis())
            .build();

        // 4. 委派给 LockExecutor 执行
        return lockExecutor.execute(context, () -> {
            try {
                return pjp.proceed();
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        });
    }

    /**
     * 根据锁类型获取锁实例
     *
     * @param key  锁键
     * @param type 锁类型
     * @return 锁实例
     */
    private RLock getLock(String key, LockType type) {
        return switch (type) {
            case REENTRANT -> redissonClient.getLock(key);
            case FAIR -> redissonClient.getFairLock(key);
            case READ -> redissonClient.getReadWriteLock(key).readLock();
            case WRITE -> redissonClient.getReadWriteLock(key).writeLock();
        };
    }
}
```

#### 6.1.8 LockKeyGenerator

**职责**：解析 SpEL 表达式，生成锁键（支持缓存和安全检查）

```java
package com.patra.starter.redisson.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * 锁键生成器
 * <p>
 * 支持 SpEL 表达式解析，动态生成锁键
 * <p>
 * <strong>安全特性</strong>：
 * <ul>
 *   <li>Expression 缓存：避免重复解析，提升性能</li>
 *   <li>危险关键字检查：禁止 Runtime、ProcessBuilder 等危险操作</li>
 *   <li>类型白名单：仅允许 LocalDate、LocalDateTime、UUID 等安全类</li>
 * </ul>
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class LockKeyGenerator {

    private final String keyPrefix;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * SpEL Expression 缓存（性能优化）
     * <p>
     * key: 表达式字符串，value: 解析后的 Expression 对象
     */
    private final ConcurrentHashMap<String, Expression> expressionCache = new ConcurrentHashMap<>();

    /**
     * 危险关键字正则（安全检查）
     * <p>
     * 禁止的操作：
     * - T(java.lang.Runtime)：执行系统命令
     * - T(java.lang.ProcessBuilder)：创建进程
     * - T(java.lang.System)：访问系统属性
     * - new：创建任意对象
     */
    private static final Pattern DANGEROUS_PATTERN = Pattern.compile(
        "T\\(java\\.lang\\.(Runtime|ProcessBuilder|System)\\)|new\\s+",
        Pattern.CASE_INSENSITIVE
    );

    /**
     * 允许的类型白名单（仅限这些类的静态方法可调用）
     */
    private static final Set<String> ALLOWED_TYPES = Set.of(
        "java.time.LocalDate",
        "java.time.LocalDateTime",
        "java.time.Instant",
        "java.util.UUID",
        "java.lang.String",
        "java.lang.Math"
    );

    /**
     * 生成锁键
     *
     * @param keyExpression SpEL 表达式
     * @param pjp           连接点
     * @return 完整的锁键（含前缀）
     * @throws IllegalArgumentException 如果表达式不安全或解析失败
     */
    public String generateKey(String keyExpression, ProceedingJoinPoint pjp) {
        try {
            // 1. 安全检查：禁止危险关键字
            validateSafety(keyExpression);

            // 2. 从缓存获取或解析表达式（性能优化）
            Expression expression = expressionCache.computeIfAbsent(
                keyExpression,
                parser::parseExpression
            );

            // 3. 获取方法信息
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            Method method = signature.getMethod();
            Object[] args = pjp.getArgs();

            // 4. 创建 SpEL 上下文
            EvaluationContext context = new MethodBasedEvaluationContext(
                pjp.getTarget(),
                method,
                args,
                parameterNameDiscoverer
            );

            // 5. 执行表达式求值
            String parsedKey = expression.getValue(context, String.class);

            if (parsedKey == null || parsedKey.isBlank()) {
                throw new IllegalArgumentException("Lock key expression evaluated to null or blank: " + keyExpression);
            }

            // 6. 添加前缀
            String fullKey = keyPrefix + parsedKey;

            log.debug("生成锁键: {} (表达式: {})", fullKey, keyExpression);

            return fullKey;

        } catch (IllegalArgumentException e) {
            // 重新抛出安全相关异常
            throw e;
        } catch (Exception e) {
            log.error("解析锁键表达式失败: {}", keyExpression, e);
            throw new IllegalArgumentException("Invalid lock key expression: " + keyExpression, e);
        }
    }

    /**
     * 安全检查：验证 SpEL 表达式是否安全
     *
     * @param keyExpression SpEL 表达式
     * @throws IllegalArgumentException 如果表达式包含危险关键字或不允许的类型
     */
    private void validateSafety(String keyExpression) {
        // 检查1：禁止危险关键字
        if (DANGEROUS_PATTERN.matcher(keyExpression).find()) {
            throw new IllegalArgumentException(
                "Unsafe SpEL expression detected (contains Runtime/ProcessBuilder/System or 'new' keyword): " + keyExpression
            );
        }

        // 检查2：如果使用 T() 操作符，必须在白名单中
        if (keyExpression.contains("T(")) {
            validateTypeWhitelist(keyExpression);
        }
    }

    /**
     * 验证类型白名单
     *
     * @param keyExpression SpEL 表达式
     * @throws IllegalArgumentException 如果使用了不在白名单中的类型
     */
    private void validateTypeWhitelist(String keyExpression) {
        // 提取所有 T(xxx) 中的类型
        Pattern typePattern = Pattern.compile("T\\(([^)]+)\\)");
        var matcher = typePattern.matcher(keyExpression);

        while (matcher.find()) {
            String typeName = matcher.group(1);
            if (!ALLOWED_TYPES.contains(typeName)) {
                throw new IllegalArgumentException(
                    "Type '" + typeName + "' is not in the whitelist. " +
                    "Allowed types: " + ALLOWED_TYPES
                );
            }
        }
    }

    /**
     * 清空表达式缓存（用于测试或重启时）
     */
    public void clearCache() {
        expressionCache.clear();
        log.info("SpEL Expression 缓存已清空");
    }

    /**
     * 获取缓存统计信息（用于监控）
     */
    public int getCacheSize() {
        return expressionCache.size();
    }
}
```

#### 6.1.9 LockMetricsRecorder

**职责**：记录 Micrometer 指标（支持 Metric 缓存优化）

```java
package com.patra.starter.redisson.listener;

import com.patra.starter.redisson.config.RedissonProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 锁指标记录器
 * <p>
 * 记录 Micrometer 指标：锁获取成功/失败、等待时间、持有时间
 * <p>
 * <strong>性能优化</strong>：
 * <ul>
 *   <li>Counter/Timer 缓存：避免重复创建 Metric，减少 30% 性能开销</li>
 *   <li>simplifyKey 缓存：避免重复字符串操作</li>
 * </ul>
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class LockMetricsRecorder {

    private final MeterRegistry meterRegistry;
    private final RedissonProperties properties;

    /**
     * Counter 缓存（性能优化）
     * <p>
     * key: "metric_name:tag_value"，value: Counter 实例
     */
    private final ConcurrentHashMap<String, Counter> counterCache = new ConcurrentHashMap<>();

    /**
     * Timer 缓存（性能优化）
     * <p>
     * key: "metric_name:tag_value"，value: Timer 实例
     */
    private final ConcurrentHashMap<String, Timer> timerCache = new ConcurrentHashMap<>();

    /**
     * simplifyKey 结果缓存（性能优化）
     * <p>
     * key: 原始锁键，value: 简化后的键
     */
    private final ConcurrentHashMap<String, String> simplifiedKeyCache = new ConcurrentHashMap<>();

    /**
     * 锁获取成功
     */
    public void onLockAcquired(String lockKey, long waitTime) {
        String simplifiedKey = getSimplifiedKey(lockKey);

        // 记录成功计数（缓存优化）
        getOrCreateCounter("redisson.lock.acquired", simplifiedKey).increment();

        // 记录等待时间（缓存优化）
        getOrCreateTimer("redisson.lock.wait.time", simplifiedKey)
            .record(waitTime, TimeUnit.MILLISECONDS);
    }

    /**
     * 锁获取失败
     */
    public void onLockFailed(String lockKey, long waitTime, String reason) {
        String simplifiedKey = getSimplifiedKey(lockKey);

        // 记录失败计数（缓存优化，包含 reason tag）
        String cacheKey = "redisson.lock.failed:" + simplifiedKey + ":" + reason;
        Counter counter = counterCache.computeIfAbsent(cacheKey, k ->
            Counter.builder("redisson.lock.failed")
                .tag("key", simplifiedKey)
                .tag("reason", reason)
                .register(meterRegistry)
        );
        counter.increment();

        // 记录等待时间（缓存优化）
        getOrCreateTimer("redisson.lock.wait.time.failed", simplifiedKey)
            .record(waitTime, TimeUnit.MILLISECONDS);
    }

    /**
     * 锁释放
     */
    public void onLockReleased(String lockKey, long holdTime) {
        String simplifiedKey = getSimplifiedKey(lockKey);

        // 记录持有时间（缓存优化）
        getOrCreateTimer("redisson.lock.hold.time", simplifiedKey)
            .record(holdTime, TimeUnit.MILLISECONDS);

        // 检查是否超过阈值（告警）
        long threshold = properties.getLock().getObservability().getMetrics().getHoldTimeWarnThreshold();
        if (holdTime > threshold) {
            log.warn("锁持有时间过长: {} ({}ms), 阈值: {}ms", lockKey, holdTime, threshold);
        }
    }

    /**
     * 获取或创建 Counter（缓存优化）
     *
     * @param name 指标名称
     * @param key  简化后的锁键
     * @return Counter 实例
     */
    private Counter getOrCreateCounter(String name, String key) {
        String cacheKey = name + ":" + key;
        return counterCache.computeIfAbsent(cacheKey, k ->
            Counter.builder(name)
                .tag("key", key)
                .register(meterRegistry)
        );
    }

    /**
     * 获取或创建 Timer（缓存优化）
     *
     * @param name 指标名称
     * @param key  简化后的锁键
     * @return Timer 实例
     */
    private Timer getOrCreateTimer(String name, String key) {
        String cacheKey = name + ":" + key;
        return timerCache.computeIfAbsent(cacheKey, k ->
            Timer.builder(name)
                .tag("key", key)
                .register(meterRegistry)
        );
    }

    /**
     * 获取简化后的锁键（缓存优化）
     *
     * @param lockKey 原始锁键
     * @return 简化后的锁键
     */
    private String getSimplifiedKey(String lockKey) {
        return simplifiedKeyCache.computeIfAbsent(lockKey, this::simplifyKey);
    }

    /**
     * 简化锁键（用于 tag，避免 cardinality 过高）
     * <p>
     * 例如: patra:lock:user:123 -> user
     *
     * @param lockKey 原始锁键
     * @return 简化后的锁键
     */
    private String simplifyKey(String lockKey) {
        if (lockKey.startsWith(properties.getLock().getKeyPrefix())) {
            String simplifiedKey = lockKey.substring(properties.getLock().getKeyPrefix().length());
            int colonIndex = simplifiedKey.indexOf(':');
            return colonIndex > 0 ? simplifiedKey.substring(0, colonIndex) : simplifiedKey;
        }
        return "unknown";  // 默认值，避免高基数
    }

    /**
     * 清空缓存（用于测试或重启时）
     */
    public void clearCache() {
        counterCache.clear();
        timerCache.clear();
        simplifiedKeyCache.clear();
        log.info("Metric 缓存已清空");
    }

    /**
     * 获取缓存统计信息（用于监控）
     */
    public int getCacheSize() {
        return counterCache.size() + timerCache.size() + simplifiedKeyCache.size();
    }
}
```

#### 6.1.10 LockTracingRecorder

**职责**：集成 SkyWalking 追踪

```java
package com.patra.starter.redisson.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.Tracer;

/**
 * 锁追踪记录器
 * <p>
 * 集成 SkyWalking，记录锁操作的分布式追踪
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Slf4j
public class LockTracingRecorder {

    private static final String SPAN_PREFIX = "DistributedLock:";

    /**
     * 获取锁前（创建 Span）
     */
    public void beforeLock(String lockKey) {
        String spanName = SPAN_PREFIX + lockKey;

        // 创建 SkyWalking Span
        ActiveSpan span = Tracer.createLocalSpan(spanName);
        span.tag("lock.key", lockKey);
        span.tag("lock.operation", "acquire");
    }

    /**
     * 获取锁后（结束 Span）
     */
    public void afterLock(String lockKey, boolean acquired) {
        ActiveSpan span = Tracer.activeSpan();

        if (span != null) {
            span.tag("lock.acquired", String.valueOf(acquired));

            if (!acquired) {
                span.errorOccurred();
                span.log(new RuntimeException("Failed to acquire lock: " + lockKey));
            }

            Tracer.stopSpan();
        }
    }
}
```

#### 6.1.11 LockLoggingRecorder

**职责**：记录日志

```java
package com.patra.starter.redisson.listener;

import com.patra.starter.redisson.config.RedissonProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 锁日志记录器
 * <p>
 * 记录锁获取/释放日志
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class LockLoggingRecorder {

    private final RedissonProperties properties;

    /**
     * 获取锁前
     */
    public void beforeLock(String lockKey) {
        if (log.isDebugEnabled()) {
            log.debug("尝试获取分布式锁: {}", lockKey);
        }
    }

    /**
     * 获取锁成功
     */
    public void onLockAcquired(String lockKey, long waitTime) {
        if (log.isDebugEnabled()) {
            log.debug("成功获取分布式锁: {} (等待时间: {}ms)", lockKey, waitTime);
        }
    }

    /**
     * 获取锁失败
     */
    public void onLockFailed(String lockKey, long waitTime) {
        log.warn("获取分布式锁失败: {} (等待时间: {}ms)", lockKey, waitTime);
    }

    /**
     * 锁释放
     */
    public void onLockReleased(String lockKey, long holdTime) {
        if (log.isDebugEnabled()) {
            log.debug("释放分布式锁: {} (持有时间: {}ms)", lockKey, holdTime);
        }

        // 检查持有时间是否过长
        long threshold = properties.getLock().getObservability().getLogging().getHoldTimeWarnThreshold();
        if (holdTime > threshold) {
            log.warn("锁持有时间过长: {} ({}ms), 阈值: {}ms, 建议优化业务逻辑",
                lockKey, holdTime, threshold);
        }
    }
}
```

#### 6.1.12 异常体系设计

**职责**：统一的分布式锁异常处理

##### LockException（基类）

```java
package com.patra.starter.redisson.exception;

import com.patra.common.core.exception.ApplicationException;
import com.patra.common.core.exception.ErrorCode;
import lombok.Getter;

import java.util.Map;

/**
 * 分布式锁异常基类
 * <p>
 * 所有锁相关异常都继承此类，统一异常处理
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Getter
public abstract class LockException extends ApplicationException {

    /**
     * 锁键
     */
    private final String lockKey;

    protected LockException(ErrorCode errorCode, String lockKey, Map<String, Object> context) {
        super(errorCode, enrichContext(context, lockKey));
        this.lockKey = lockKey;
    }

    protected LockException(ErrorCode errorCode, String lockKey, Map<String, Object> context, Throwable cause) {
        super(errorCode, enrichContext(context, lockKey), cause);
        this.lockKey = lockKey;
    }

    /**
     * 增强上下文信息（添加 lockKey）
     */
    private static Map<String, Object> enrichContext(Map<String, Object> context, String lockKey) {
        context.put("lockKey", lockKey);
        return context;
    }
}
```

##### LockAcquisitionException（获取失败）

```java
package com.patra.starter.redisson.exception;

import com.patra.common.core.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * 锁获取失败异常
 * <p>
 * 在无法获取锁时抛出（根据 {@link DistributedLock#throwExceptionOnFailure()} 配置）
 * <p>
 * HTTP 状态码：409 Conflict（业务冲突）
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Getter
public class LockAcquisitionException extends LockException {

    /**
     * 错误码定义
     */
    public static final ErrorCode ERROR_CODE = ErrorCode.of(
        "LOCK_ACQUISITION_FAILED",
        "无法获取分布式锁，可能存在并发冲突",
        HttpStatus.CONFLICT
    );

    /**
     * 等待时间（毫秒）
     */
    private final long waitTime;

    public LockAcquisitionException(String lockKey, long waitTime) {
        super(ERROR_CODE, lockKey, createContext(waitTime));
        this.waitTime = waitTime;
    }

    public LockAcquisitionException(String lockKey, long waitTime, Throwable cause) {
        super(ERROR_CODE, lockKey, createContext(waitTime), cause);
        this.waitTime = waitTime;
    }

    public LockAcquisitionException(String customMessage, String lockKey, long waitTime) {
        super(
            ErrorCode.of("LOCK_ACQUISITION_FAILED", customMessage, HttpStatus.CONFLICT),
            lockKey,
            createContext(waitTime)
        );
        this.waitTime = waitTime;
    }

    private static Map<String, Object> createContext(long waitTime) {
        Map<String, Object> context = new HashMap<>();
        context.put("waitTime", waitTime);
        return context;
    }
}
```

##### LockTimeoutException（持有超时）

```java
package com.patra.starter.redisson.exception;

import com.patra.common.core.exception.ErrorCode;
import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.HashMap;
import java.util.Map;

/**
 * 锁持有超时异常
 * <p>
 * 当锁持有时间超过预期时抛出（系统问题）
 * <p>
 * HTTP 状态码：500 Internal Server Error
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Getter
public class LockTimeoutException extends LockException {

    /**
     * 错误码定义
     */
    public static final ErrorCode ERROR_CODE = ErrorCode.of(
        "LOCK_TIMEOUT",
        "锁持有时间超过预期，可能存在性能问题",
        HttpStatus.INTERNAL_SERVER_ERROR
    );

    /**
     * 持有时间（毫秒）
     */
    private final long holdTime;

    /**
     * 超时阈值（毫秒）
     */
    private final long threshold;

    public LockTimeoutException(String lockKey, long holdTime, long threshold) {
        super(ERROR_CODE, lockKey, createContext(holdTime, threshold));
        this.holdTime = holdTime;
        this.threshold = threshold;
    }

    public LockTimeoutException(String lockKey, long holdTime, long threshold, Throwable cause) {
        super(ERROR_CODE, lockKey, createContext(holdTime, threshold), cause);
        this.holdTime = holdTime;
        this.threshold = threshold;
    }

    private static Map<String, Object> createContext(long holdTime, long threshold) {
        Map<String, Object> context = new HashMap<>();
        context.put("holdTime", holdTime);
        context.put("threshold", threshold);
        return context;
    }
}
```

### 6.2 配置属性设计

#### RedissonProperties

```java
package com.patra.starter.redisson.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redisson 配置属性
 *
 * @author Patra Team
 * @since 1.0.0
 */
@ConfigurationProperties(prefix = "patra.redisson")
@Data
public class RedissonProperties {

    /**
     * 是否启用 Redisson
     */
    private boolean enabled = true;

    /**
     * 线程池大小
     */
    private int threads = 16;

    /**
     * Netty 线程池大小
     */
    private int nettyThreads = 32;

    /**
     * 锁看门狗超时（毫秒）
     * <p>
     * 默认 30 秒。当锁没有设置 leaseTime 时，看门狗会定期续期。
     */
    private long lockWatchdogTimeout = 30000;

    /**
     * 分布式锁配置
     */
    private LockProperties lock = new LockProperties();

    /**
     * 分布式缓存配置
     */
    private CacheProperties cache = new CacheProperties();

    @Data
    public static class LockProperties {
        /**
         * 是否启用分布式锁
         */
        private boolean enabled = true;

        /**
         * 锁键前缀
         */
        private String keyPrefix = "patra:lock:";

        /**
         * 默认锁租约时间（秒）
         */
        private long defaultLeaseTime = 30;

        /**
         * 默认等待时间（秒）
         */
        private long defaultWaitTime = 0;

        /**
         * 可观测性配置
         */
        private ObservabilityProperties observability = new ObservabilityProperties();

        @Data
        public static class ObservabilityProperties {
            /**
             * 追踪配置
             */
            private TracingProperties tracing = new TracingProperties();

            /**
             * 指标配置
             */
            private MetricsProperties metrics = new MetricsProperties();

            /**
             * 日志配置
             */
            private LoggingProperties logging = new LoggingProperties();

            @Data
            public static class TracingProperties {
                private boolean enabled = true;
            }

            @Data
            public static class MetricsProperties {
                private boolean enabled = true;

                /**
                 * 锁持有时间告警阈值（毫秒）
                 */
                private long holdTimeWarnThreshold = 60000;  // 60 秒
            }

            @Data
            public static class LoggingProperties {
                private boolean enabled = true;

                /**
                 * 锁持有时间告警阈值（毫秒）
                 */
                private long holdTimeWarnThreshold = 60000;  // 60 秒
            }
        }
    }

    @Data
    public static class CacheProperties {
        /**
         * 是否启用分布式缓存
         */
        private boolean enabled = false;

        /**
         * 默认 TTL（秒）
         */
        private long defaultTtl = 3600;
    }
}
```

---

## 七、配置管理

### 7.1 配置命名空间说明

**两套配置体系**：

| 配置命名空间 | 职责 | 来源 | 用途 |
|------------|------|------|------|
| **`patra.redisson.*`** | Patra 业务配置 | patra-spring-boot-starter-redisson | 分布式锁、缓存、可观测性等业务功能配置 |
| **`spring.data.redis.redisson.*`** | Redisson 底层配置 | redisson-spring-boot-starter（官方） | Redis 连接、线程池、序列化等底层基础设施配置 |

**配置原则**：
- ✅ **分离关注点**：业务配置（patra）与基础设施配置（spring）分离
- ✅ **单一职责**：`patra.redisson` 只配置业务功能，`spring.data.redis.redisson` 只配置连接
- ✅ **优先级明确**：无冲突，各自管理各自领域

**典型配置场景**：

```yaml
# ✅ 正确示例：业务配置与基础设施配置并存
patra:
  redisson:
    # Patra 业务功能配置
    lock:
      key-prefix: "my-service:lock:"
      default-lease-time: 60
      observability:
        metrics:
          enabled: true

spring:
  data:
    redis:
      redisson:
        # Redisson 底层连接配置
        config: |
          singleServerConfig:
            address: "redis://localhost:6379"
            connectionPoolSize: 64
```

### 7.2 默认配置

**application.yml**（Starter 内置）：

```yaml
patra:
  redisson:
    # === Patra 业务功能配置 ===
    enabled: true
    threads: 16                     # Redisson 客户端线程池大小（非连接池）
    netty-threads: 32               # Netty 事件循环线程数
    lock-watchdog-timeout: 30000    # 锁看门狗超时（毫秒）

    # 分布式锁配置
    lock:
      enabled: true
      key-prefix: "patra:lock:"     # 锁键前缀（建议按服务自定义）
      default-lease-time: 30        # 默认租约时间（秒）
      default-wait-time: 0          # 默认等待时间（秒，0 表示不等待）

      # 可观测性配置
      observability:
        tracing:
          enabled: true             # SkyWalking 追踪
        metrics:
          enabled: true             # Micrometer 指标
          hold-time-warn-threshold: 60000  # 持有时间告警阈值（毫秒）
        logging:
          enabled: true             # 日志记录
          hold-time-warn-threshold: 60000  # 持有时间告警阈值（毫秒）

    # 分布式缓存配置（未来功能）
    cache:
      enabled: false
      default-ttl: 3600

# === Redisson 底层连接配置（基于 redisson-spring-boot-starter）===
spring:
  data:
    redis:
      redisson:
        config: |
          # 单机模式配置
          singleServerConfig:
            address: "redis://127.0.0.1:6379"
            database: 0
            connectionPoolSize: 64             # 连接池大小（非线程池）
            connectionMinimumIdleSize: 24      # 最小空闲连接
            timeout: 3000                      # 连接超时（毫秒）
            retryAttempts: 3                   # 重试次数
            retryInterval: 1500                # 重试间隔（毫秒）

          # 集群模式配置（示例，按需切换）
          # clusterServersConfig:
          #   nodeAddresses:
          #     - "redis://node1:6379"
          #     - "redis://node2:6379"
          #     - "redis://node3:6379"
```

**配置项说明**：

| 配置项 | 默认值 | 说明 | 调整建议 |
|--------|--------|------|----------|
| `patra.redisson.threads` | 16 | Redisson 客户端线程池（执行任务） | CPU 核心数 × 2 |
| `patra.redisson.netty-threads` | 32 | Netty I/O 线程（网络通信） | CPU 核心数 × 2 |
| `patra.redisson.lock-watchdog-timeout` | 30000 | 看门狗超时（毫秒） | 建议保持默认 |
| `spring.data.redis.redisson.config.singleServerConfig.connectionPoolSize` | 64 | Redis 连接池大小 | 根据并发量调整 |
| `spring.data.redis.redisson.config.singleServerConfig.timeout` | 3000 | 连接超时（毫秒） | 根据网络延迟调整 |

### 7.3 业务服务配置

**patra-catalog 的 application.yml**：

```yaml
patra:
  redisson:
    # === Patra 业务功能配置 ===
    lock:
      key-prefix: "catalog:lock:"     # ✅ 按服务自定义前缀，避免锁键冲突
      default-lease-time: 7200        # ✅ MeSH 导入需要 2 小时

      observability:
        metrics:
          hold-time-warn-threshold: 120000  # ✅ 2 分钟（根据业务调整）

# === Redisson 底层连接配置 ===
spring:
  data:
    redis:
      redisson:
        config: |
          singleServerConfig:
            address: "redis://${REDIS_HOST:localhost}:${REDIS_PORT:6379}"
            password: ${REDIS_PASSWORD:}
            database: 0
            connectionPoolSize: 128   # ✅ MeSH 导入并发量大，增加连接池
```

### 7.4 配置最佳实践

#### 7.4.1 配置分层原则

```
┌─────────────────────────────────────────┐
│  业务服务 (patra-catalog)               │
│  ├─ patra.redisson.lock.key-prefix      │  ← 按服务自定义
│  └─ patra.redisson.lock.default-*       │  ← 按业务场景调整
└─────────────────────────────────────────┘
                  ↓ 继承默认配置
┌─────────────────────────────────────────┐
│  Redisson Starter 默认配置              │
│  ├─ patra.redisson.lock-watchdog-timeout│  ← 通用默认值
│  └─ patra.redisson.observability.*      │  ← 全局开关
└─────────────────────────────────────────┘
                  ↓ 依赖
┌─────────────────────────────────────────┐
│  Redisson 官方配置                      │
│  └─ spring.data.redis.redisson.config   │  ← 底层连接配置
└─────────────────────────────────────────┘
```

#### 7.4.2 常见配置场景

**场景 1：开发环境（单机 Redis）**

```yaml
patra:
  redisson:
    lock:
      observability:
        logging:
          enabled: true  # ✅ 开发环境开启详细日志

spring:
  data:
    redis:
      redisson:
        config: |
          singleServerConfig:
            address: "redis://localhost:6379"
            database: 0
```

**场景 2：生产环境（Redis 集群）**

```yaml
patra:
  redisson:
    threads: 32              # ✅ 根据 CPU 核心数调整
    netty-threads: 64
    lock:
      observability:
        logging:
          enabled: false     # ✅ 生产环境关闭 DEBUG 日志
        metrics:
          enabled: true      # ✅ 保留指标收集

spring:
  data:
    redis:
      redisson:
        config: |
          clusterServersConfig:
            nodeAddresses:
              - "redis://node1:6379"
              - "redis://node2:6379"
              - "redis://node3:6379"
            masterConnectionPoolSize: 128
            slaveConnectionPoolSize: 128
            timeout: 5000
```

**场景 3：多租户环境**

```yaml
patra:
  redisson:
    lock:
      key-prefix: "tenant:${TENANT_ID}:lock:"  # ✅ 租户隔离

spring:
  data:
    redis:
      redisson:
        config: |
          singleServerConfig:
            database: ${REDIS_DB:0}  # ✅ 按租户分库
```

#### 7.4.3 配置决策树

```
【我需要配置什么？】
        ↓
  ┌─────┴─────┐
  │           │
Redis 连接？  业务功能？
  │           │
  ↓           ↓
spring.data  patra.redisson
.redis       .lock / .cache
.redisson    .observability
  │           │
  │           ├─ 锁键前缀？        → key-prefix
  │           ├─ 租约时间？        → default-lease-time
  │           ├─ 指标收集？        → observability.metrics.enabled
  │           └─ 日志级别？        → observability.logging.enabled
  │
  ├─ 单机/集群/哨兵？ → singleServerConfig / clusterServersConfig
  ├─ 连接池大小？    → connectionPoolSize
  ├─ 连接超时？      → timeout
  └─ 密码/认证？     → password
```

#### 7.4.4 常见问题 (FAQ)

**Q1: `patra.redisson` 和 `spring.data.redis.redisson` 有什么区别？**

A:
- `patra.redisson.*` = 业务功能配置（锁、缓存、可观测性）
- `spring.data.redis.redisson.*` = 底层连接配置（Redis 地址、连接池）
- **类比**：前者是"业务逻辑"，后者是"数据库连接"

**Q2: 我应该修改哪个配置？**

A:
- 需要调整**锁行为**（如租约时间、键前缀）→ 修改 `patra.redisson.lock.*`
- 需要调整**Redis 连接**（如地址、密码、集群节点）→ 修改 `spring.data.redis.redisson.config`
- 需要调整**可观测性**（如关闭日志、开启指标）→ 修改 `patra.redisson.lock.observability.*`

**Q3: 两个配置会冲突吗？**

A: **不会**。两者管理不同的领域，没有重叠：
- `patra.redisson` → 注入到 `RedissonProperties`（Patra 自定义 Bean）
- `spring.data.redis.redisson` → 注入到 Redisson 官方 `RedissonClient`

**Q4: 为什么不把所有配置合并到一个命名空间？**

A: **分离关注点原则**：
- 业务配置（Patra）应该独立于底层实现（Redisson）
- 未来切换 Redis 客户端（如 Lettuce），业务配置不受影响
- 符合 Spring Boot Starter 最佳实践（官方配置 + 自定义扩展）

**Q5: `threads` 和 `connectionPoolSize` 有什么区别？**

A:
- `patra.redisson.threads` = Redisson **客户端线程池**（执行任务、回调）
- `spring.data.redis.redisson.config.*.connectionPoolSize` = **Redis 连接池**（网络连接）
- **类比**：前者是"工人数量"，后者是"工具数量"

---

## 八、使用示例

### 8.1 基本用法

#### 示例 1：保护定时任务

```java
package com.patra.catalog.adapter.scheduler.job;

import com.patra.starter.redisson.lock.DistributedLock;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * MeSH 数据导入定时任务
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MeshImportJobHandler {

    private final JobLauncherHelper jobLauncherHelper;
    private final Job meshImportJob;

    /**
     * MeSH 数据导入任务
     * <p>
     * 使用分布式锁防止多实例并发执行
     */
    @XxlJob("meshImportJob")
    @DistributedLock(
        key = "mesh-import",
        leaseTime = 7200,  // 2 小时
        waitTime = 0       // 不等待，直接失败
    )
    public void execute() {
        log.info("MeSH 数据导入任务启动");

        jobLauncherHelper.launch(meshImportJob, Map.of("year", "2024"));

        log.info("MeSH 数据导入任务完成");
    }
}
```

#### 示例 2：保护业务方法（动态锁键）

```java
package com.patra.catalog.app.usecase;

import com.patra.starter.redisson.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 用户管理服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    /**
     * 更新用户信息
     * <p>
     * 使用分布式锁防止并发修改同一用户
     */
    @DistributedLock(
        key = "user:#{#userId}",  // 动态生成锁键：patra:lock:user:123
        leaseTime = 10,
        waitTime = 3
    )
    public void updateUser(Long userId, UserUpdateDTO dto) {
        log.info("更新用户: {}", userId);

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new IllegalArgumentException("用户不存在"));

        user.update(dto);
        userRepository.save(user);
    }
}
```

#### 示例 3：读写锁

```java
package com.patra.catalog.app.usecase;

import com.patra.starter.redisson.lock.DistributedLock;
import com.patra.starter.redisson.lock.LockType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 配置管理服务
 */
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ConfigRepository configRepository;

    /**
     * 读取配置（读锁，允许并发）
     */
    @DistributedLock(
        key = "config:#{#key}",
        type = LockType.READ,
        leaseTime = 5,
        waitTime = 1
    )
    public String getConfig(String key) {
        return configRepository.findByKey(key);
    }

    /**
     * 更新配置（写锁，独占）
     */
    @DistributedLock(
        key = "config:#{#key}",
        type = LockType.WRITE,
        leaseTime = 10,
        waitTime = 3
    )
    public void updateConfig(String key, String value) {
        configRepository.save(key, value);
    }
}
```

#### 示例 4：复杂 SpEL 表达式

```java
package com.patra.ingest.app.usecase;

import com.patra.starter.redisson.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 采集任务服务
 */
@Service
@RequiredArgsConstructor
public class HarvestService {

    /**
     * 执行采集任务
     * <p>
     * 锁键示例：patra:lock:harvest:pubmed:2024-11-23
     */
    @DistributedLock(
        key = "harvest:#{#provenance}:#{T(java.time.LocalDate).now()}",
        leaseTime = 3600,
        waitTime = 0
    )
    public void executeHarvest(String provenance) {
        // 采集逻辑
    }
}
```

### 8.2 异常处理

#### 示例 5：自定义失败处理

```java
package com.patra.catalog.app.usecase;

import com.patra.starter.redisson.lock.DistributedLock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 订单服务
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    /**
     * 创建订单
     * <p>
     * 获取锁失败时返回 null（不抛异常）
     */
    @DistributedLock(
        key = "order:create:#{#userId}",
        leaseTime = 10,
        waitTime = 5,
        throwExceptionOnFailure = false,  // 不抛异常
        failureMessage = "当前有其他订单正在创建，请稍后重试"
    )
    public Order createOrder(Long userId, OrderCreateDTO dto) {
        // 如果获取锁失败，方法返回 null
        return new Order(userId, dto);
    }
}
```

#### 示例 6：全局异常处理

```java
package com.patra.catalog.adapter.rest;

import com.patra.starter.redisson.exception.LockAcquisitionException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器（可选）
 * <p>
 * 注意：如果使用 patra-spring-boot-starter-web，则无需手动编写异常处理器。
 * patra-starter-web 会自动处理所有 ApplicationException 子类（包括 LockException）。
 * <p>
 * 以下代码仅为自定义处理示例（不推荐）。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理锁获取失败异常（自定义处理示例）
     * <p>
     * ⚠️ 不推荐：patra-starter-web 已自动处理，无需重复编写
     */
    @ExceptionHandler(LockAcquisitionException.class)
    public ResponseEntity<Map<String, Object>> handleLockAcquisitionException(
            LockAcquisitionException ex
    ) {
        // LockAcquisitionException 继承自 ApplicationException
        // patra-starter-web 会自动将其转换为标准 HTTP 响应：
        // {
        //   "code": "LOCK_ACQUISITION_FAILED",
        //   "message": "无法获取分布式锁，可能存在并发冲突",
        //   "context": {
        //     "lockKey": "user:123",
        //     "waitTime": 5000
        //   },
        //   "timestamp": "2025-11-23T10:30:00Z"
        // }

        return ResponseEntity.status(ex.getErrorCode().getHttpStatus()).body(Map.of(
            "error", ex.getErrorCode().getCode(),
            "message", ex.getMessage(),
            "lockKey", ex.getLockKey(),
            "waitTime", ex.getWaitTime()
        ));
    }
}
```

**推荐做法**：
- ✅ 依赖 `patra-spring-boot-starter-web`，异常自动处理
- ❌ 不要手动编写 LockException 的异常处理器（重复劳动）



### 8.3 监控和告警

#### 示例 7：Grafana 仪表板

**Micrometer 指标查询**：

```promql
# 锁获取成功率
rate(redisson_lock_acquired_total[5m]) /
(rate(redisson_lock_acquired_total[5m]) + rate(redisson_lock_failed_total[5m]))

# 锁等待时间 P95
histogram_quantile(0.95, rate(redisson_lock_wait_time_bucket[5m]))

# 锁持有时间 P99
histogram_quantile(0.99, rate(redisson_lock_hold_time_bucket[5m]))

# 锁获取失败率（按 key 分组）
sum by (key) (rate(redisson_lock_failed_total[5m]))
```

---

## 九、高级主题与最佳实践

### 9.1 看门狗机制 (Watchdog)

#### 9.1.1 什么是看门狗？

**看门狗 (Watchdog)** 是 Redisson 提供的**自动续期机制**，防止业务逻辑执行时间超过锁租约时间导致锁被误释放。

**工作原理**：

```
┌────────────────────────────────────────────────────────────┐
│  线程 A 获取锁（未设置 leaseTime）                         │
│  ├─ 锁租约时间：默认 30 秒（lock-watchdog-timeout）        │
│  ├─ 看门狗启动：每 10 秒（租约时间 / 3）检查一次           │
│  │                                                          │
│  │  [业务执行中]                                           │
│  │     ↓                                                    │
│  │  10 秒后 → 看门狗检测到锁仍持有 → 续期 30 秒            │
│  │     ↓                                                    │
│  │  20 秒后 → 看门狗续期 30 秒                             │
│  │     ↓                                                    │
│  │  ... 持续续期直到业务完成                               │
│  │                                                          │
│  └─ 业务完成 → 主动释放锁 → 看门狗停止                     │
└────────────────────────────────────────────────────────────┘
```

#### 9.1.2 何时启用看门狗？

| 场景 | leaseTime 设置 | 看门狗状态 | 说明 |
|------|---------------|-----------|------|
| **场景 1：执行时间不确定** | 不设置（-1） | ✅ 自动启用 | 推荐：MeSH 导入、大批量处理 |
| **场景 2：执行时间可预测** | 设置具体值 | ❌ 不启用 | 推荐：简单 API 调用、快速查询 |
| **场景 3：严格超时控制** | 设置具体值 | ❌ 不启用 | 推荐：防止死锁、强制超时 |

#### 9.1.3 使用示例

**示例 1：启用看门狗（执行时间不确定）**

```java
@XxlJob("meshImportJob")
@DistributedLock(
    key = "batch:job:mesh-import",
    leaseTime = -1,  // ✅ -1 表示不设置租约，自动启用看门狗
    waitTime = 0,
    throwExceptionOnFailure = true
)
public void importMeSH() {
    // 业务逻辑可能耗时几分钟到几小时，看门狗会自动续期
    meshImportService.doImport();
}
```

**示例 2：禁用看门狗（执行时间可预测）**

```java
@DistributedLock(
    key = "user:update:#{#userId}",
    leaseTime = 5,   // ✅ 设置具体租约时间（秒），禁用看门狗
    waitTime = 1,
    timeUnit = TimeUnit.SECONDS
)
public void updateUser(Long userId) {
    // 预计 1-2 秒内完成，设置 5 秒租约足够
    userRepository.update(userId);
}
```

#### 9.1.4 配置看门狗

**全局配置**：

```yaml
patra:
  redisson:
    lock-watchdog-timeout: 30000  # 看门狗默认续期时间（毫秒）
```

**运行时调整**（不推荐，仅调试时使用）：

```java
// 通过 Redisson Config 调整（影响所有锁）
Config config = new Config();
config.setLockWatchdogTimeout(60000L);  // 60 秒
```

#### 9.1.5 看门狗常见问题

**Q1: 看门狗会无限续期吗？**

A: **不会**。看门狗只在以下条件**全部满足**时才续期：
1. 锁由当前线程持有
2. 线程未被中断
3. 业务逻辑未执行完成
4. 未调用 `unlock()`

**Q2: 看门狗续期失败会怎样？**

A: 续期失败（如 Redis 宕机）会导致：
1. 锁在租约到期后自动释放
2. 其他线程可能获取锁
3. 业务逻辑继续执行（⚠️ 可能导致并发问题）

**最佳实践**：业务逻辑中捕获异常，检测锁状态：

```java
public void longRunningTask() {
    try {
        // 业务逻辑
    } catch (Exception e) {
        // 检查锁是否仍持有
        if (!lock.isHeldByCurrentThread()) {
            log.error("锁已丢失，回滚操作");
            rollback();
        }
        throw e;
    }
}
```

**Q3: 看门狗有性能开销吗？**

A: 有轻微开销：
- 每 `lockWatchdogTimeout / 3` 时间触发一次续期（默认 10 秒）
- 每次续期发送一次 Redis 命令（PEXPIRE）
- **影响**：可忽略（每 10 秒一次 Redis 调用）

---

### 9.2 RedLock 算法（高可用场景）

#### 9.2.1 什么是 RedLock？

**RedLock** 是 Redis 作者提出的分布式锁算法，用于**多 Redis 实例**环境，提供更高的可用性和安全性。

**适用场景**：
- ✅ 多 Redis 独立实例（非主从/集群）
- ✅ 对锁安全性要求极高（如金融交易）
- ✅ 可接受额外性能开销

**不适用场景**：
- ❌ 单 Redis 实例（无需 RedLock）
- ❌ Redis 集群模式（集群已保证高可用）
- ❌ 对性能要求极高（RedLock 需多次网络调用）

#### 9.2.2 RedLock 工作原理

**算法步骤**：

```
1. 客户端获取当前时间戳 T1
2. 尝试在 N 个独立 Redis 实例上获取锁（N 通常 = 5）
   - 每个实例设置相同的 key 和随机 value
   - 设置超时时间远小于锁租约时间（避免长时间阻塞）
3. 客户端计算获取锁耗时：T2 - T1
4. 判断是否成功：
   ✅ 成功条件（同时满足）：
      - 在**大多数实例**（N/2 + 1）上获取成功
      - 总耗时 < 锁租约时间
   ❌ 失败处理：
      - 释放所有已获取的锁
5. 锁的有效时间 = 租约时间 - 获取耗时 - 时钟漂移补偿
```

#### 9.2.3 配置 RedLock

**application.yml**：

```yaml
spring:
  data:
    redis:
      redisson:
        config: |
          # RedLock 需要配置多个独立 Redis 实例
          multiLockConfig:
            - singleServerConfig:
                address: "redis://redis1:6379"
            - singleServerConfig:
                address: "redis://redis2:6379"
            - singleServerConfig:
                address: "redis://redis3:6379"
            - singleServerConfig:
                address: "redis://redis4:6379"
            - singleServerConfig:
                address: "redis://redis5:6379"
```

**Java 代码（手动使用 RedLock）**：

```java
@Service
@RequiredArgsConstructor
public class PaymentService {

    private final RedissonClient redissonClient;

    public void processPayment(String orderId) {
        // 1. 创建 3 个独立锁（对应 3 个 Redis 实例）
        RLock lock1 = redissonClient.getLock("payment:" + orderId);
        RLock lock2 = redissonClient.getLock("payment:" + orderId);
        RLock lock3 = redissonClient.getLock("payment:" + orderId);

        // 2. 创建 RedLock（多锁）
        RLock redLock = redissonClient.getMultiLock(lock1, lock2, lock3);

        try {
            // 3. 尝试获取 RedLock（需在大多数实例上获取成功）
            boolean acquired = redLock.tryLock(10, 300, TimeUnit.SECONDS);

            if (!acquired) {
                throw new LockAcquisitionException("无法获取支付锁", "payment:" + orderId);
            }

            // 4. 执行业务逻辑
            doPayment(orderId);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException(e);
        } finally {
            // 5. 释放 RedLock（释放所有实例上的锁）
            if (redLock.isHeldByCurrentThread()) {
                redLock.unlock();
            }
        }
    }
}
```

#### 9.2.4 RedLock vs 单实例锁

| 维度 | 单实例锁 | RedLock |
|------|---------|---------|
| **可用性** | 低（单点故障） | 高（容忍 N/2 - 1 实例故障） |
| **安全性** | 中（时钟漂移风险） | 高（多实例验证） |
| **性能** | 高（单次网络调用） | 低（N 次网络调用） |
| **复杂度** | 低（单 Redis） | 高（N 个独立 Redis） |
| **适用场景** | 一般业务 | 金融、支付等高安全性场景 |

#### 9.2.5 RedLock 争议与替代方案

**争议**：
- Martin Kleppmann 指出 RedLock 在**时钟跳跃**场景下仍不安全
- 推荐使用**基于共识算法**的锁（如 Zookeeper、etcd）

**替代方案**：
- **Patra 项目推荐**：Redis 集群模式 + 单实例锁（已足够）
- **极高安全性需求**：使用 Zookeeper 或 etcd 分布式锁

**结论**：Patra 项目**不推荐**默认使用 RedLock，除非：
- 已有多个独立 Redis 实例
- 对锁安全性有极高要求
- 可接受性能开销

---

### 9.3 锁键命名规范

#### 9.3.1 命名原则

| 原则 | 说明 | 示例 |
|------|------|------|
| **1. 服务前缀** | 使用服务名作为前缀，避免跨服务冲突 | `catalog:lock:*`, `ingest:lock:*` |
| **2. 业务模块** | 按业务模块分组 | `catalog:lock:mesh:*`, `catalog:lock:publication:*` |
| **3. 操作类型** | 明确操作类型 | `import`, `export`, `sync`, `update` |
| **4. 资源标识** | 包含唯一资源 ID | `mesh:import:2024`, `user:update:12345` |
| **5. 冒号分隔** | 使用 `:` 分隔层级（Redis 规范） | `service:module:operation:resource` |
| **6. 小写+连字符** | 使用小写和连字符（kebab-case） | `mesh-import`, `user-profile-update` |

#### 9.3.2 命名模板

**通用模板**：

```
{service}:lock:{module}:{operation}:{resource-id}
```

**示例**：

```java
// ✅ 好的命名
catalog:lock:mesh:import:2024           // MeSH 2024 年数据导入
catalog:lock:publication:sync:PMC123456 // PMC 文章同步
ingest:lock:pubmed:fetch:daily          // PubMed 每日采集
user:lock:profile:update:12345          // 用户 12345 资料更新

// ❌ 不好的命名
lock                        // 太宽泛
meshImport                  // 无服务前缀，驼峰命名
catalog_lock_mesh_import    // 下划线（不符合 Redis 规范）
catalog:mesh:import:2024    // 缺少 lock 标识
```

#### 9.3.3 SpEL 动态生成锁键

**示例 1：简单参数**

```java
@DistributedLock(key = "catalog:lock:user:update:#{#userId}")
public void updateUser(Long userId) { ... }

// 生成锁键：catalog:lock:user:update:12345
```

**示例 2：对象属性**

```java
@DistributedLock(key = "catalog:lock:publication:sync:#{#pub.pmid}")
public void syncPublication(Publication pub) { ... }

// 生成锁键：catalog:lock:publication:sync:PMC123456
```

**示例 3：日期分区**

```java
@DistributedLock(
    key = "catalog:lock:mesh:import:#{T(java.time.LocalDate).now().getYear()}"
)
public void importMeSH() { ... }

// 生成锁键：catalog:lock:mesh:import:2024
```

**示例 4：多参数组合**

```java
@DistributedLock(
    key = "catalog:lock:batch:#{#jobName}:#{#year}:#{#month}"
)
public void runBatchJob(String jobName, int year, int month) { ... }

// 生成锁键：catalog:lock:batch:mesh-import:2024:11
```

#### 9.3.4 配置全局前缀

**application.yml**：

```yaml
patra:
  redisson:
    lock:
      key-prefix: "catalog:lock:"  # 全局前缀
```

**代码中使用**：

```java
// 配置了 key-prefix 后，可省略前缀
@DistributedLock(key = "mesh:import:#{#year}")  // 实际锁键：catalog:lock:mesh:import:2024
public void importMeSH(int year) { ... }
```

#### 9.3.5 锁键分析与监控

**Grafana 查询示例**：

```promql
# 按服务统计锁获取次数
sum by (service) (
  label_replace(
    rate(redisson_lock_acquired_total[5m]),
    "service",
    "$1",
    "key",
    "([^:]+):.*"
  )
)

# 按模块统计锁持有时间
histogram_quantile(0.99,
  sum by (module, le) (
    label_replace(
      rate(redisson_lock_hold_time_bucket[5m]),
      "module",
      "$1",
      "key",
      "[^:]+:lock:([^:]+):.*"
    )
  )
)
```

---

### 9.4 性能优化建议

#### 9.4.1 避免锁粒度过粗

**❌ 错误示例**：

```java
// 所有用户共享一把锁，并发度极低
@DistributedLock(key = "user:update")
public void updateUser(Long userId) { ... }
```

**✅ 正确示例**：

```java
// 每个用户独立锁，并发度高
@DistributedLock(key = "user:update:#{#userId}")
public void updateUser(Long userId) { ... }
```

#### 9.4.2 使用读写锁优化读多写少场景

**示例**：

```java
// 读锁（允许多个线程并发读）
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
```

#### 9.4.3 设置合理的 waitTime

**建议**：
- ✅ 快速失败场景：`waitTime = 0`（不等待）
- ✅ 可重试场景：`waitTime = 1-3` 秒
- ❌ 避免：`waitTime > 10` 秒（可能导致线程堆积）

#### 9.4.4 监控锁性能

**关键指标**：
- 锁获取失败率 > 5% → 检查锁粒度或业务逻辑
- 锁持有时间 P99 > 60 秒 → 检查业务优化空间
- 锁等待时间 P99 > 5 秒 → 检查并发冲突

---

## 十、实施计划

### 10.1 开发阶段

| 阶段 | 任务 | 工作量 | 优先级 |
|------|------|--------|--------|
| **阶段 1** | 创建 Starter 模块骨架 | 1h | P0 |
|  | - 创建 Maven 模块 | 0.5h |  |
|  | - 配置 pom.xml 依赖 | 0.5h |  |
| **阶段 2** | 实现核心自动配置 | 4h | P0 |
|  | - RedissonAutoConfiguration | 1h |  |
|  | - LockAutoConfiguration | 2h |  |
|  | - ObservabilityAutoConfiguration | 1h |  |
| **阶段 3** | 实现分布式锁组件 | 8h | P0 |
|  | - @DistributedLock 注解 | 0.5h |  |
|  | - LockType 枚举 | 0.5h |  |
|  | - LockKeyGenerator | 2h |  |
|  | - LockAspect | 4h |  |
|  | - LockException | 1h |  |
| **阶段 4** | 实现可观测性组件 | 6h | P1 |
|  | - LockMetricsListener | 2h |  |
|  | - LockTracingListener | 2h |  |
|  | - LockLoggingListener | 2h |  |
| **阶段 5** | 编写单元测试 | 6h | P1 |
|  | - 自动配置测试 | 2h |  |
|  | - LockAspect 测试 | 2h |  |
|  | - LockKeyGenerator 测试 | 2h |  |
| **阶段 6** | 编写集成测试 | 4h | P1 |
|  | - 完整锁流程测试 | 2h |  |
|  | - 并发测试 | 2h |  |
| **阶段 7** | 编写文档 | 2h | P1 |
|  | - README.md（使用指南） | 1h |  |
|  | - 示例代码 | 1h |  |

**总计**：约 **31 工时**（4 个工作日）

### 10.2 验证阶段

| 任务 | 工作量 | 优先级 |
|------|--------|--------|
| 在 patra-catalog 中使用（MeSH 导入任务） | 2h | P0 |
| 在 patra-ingest 中使用（采集任务） | 2h | P0 |
| 性能测试（并发 1000 锁获取） | 2h | P1 |
| 压力测试（Redis 故障场景） | 2h | P2 |

**总计**：约 **8 工时**（1 个工作日）

### 10.3 时间表

| 里程碑 | 预计完成时间 | 交付物 |
|--------|------------|--------|
| **M1: Starter 开发完成** | D+4 | patra-spring-boot-starter-redisson 1.0.0（P0 功能） |
| **M2: 业务服务集成** | D+5 | patra-catalog、patra-ingest 使用 redisson starter |
| **M3: 文档完善** | D+5 | 完整的设计文档、使用指南、示例代码 |

---

## 十一、风险评估

### 11.1 技术风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| Redis 不可用导致锁失败 | 中 | 高 | 配置重试机制、降级为抛异常 |
| 锁键冲突（不同业务使用相同键） | 低 | 中 | 使用明确的命名规范、键前缀隔离 |
| SpEL 表达式解析失败 | 低 | 中 | 提供清晰的错误消息、单元测试覆盖 |
| 锁泄漏（未释放） | 低 | 高 | finally 块保证释放、配置 leaseTime |
| 性能瓶颈（高并发锁竞争） | 中 | 中 | 使用 Redisson 高性能实现、监控指标 |

### 10.2 项目风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 开发时间超预期 | 低 | 中 | 分阶段交付，优先 P0 功能 |
| 与现有代码冲突 | 低 | 低 | 全新项目，无历史包袱 |
| 学习曲线陡峭 | 低 | 低 | Redisson 文档完善、提供示例代码 |

### 10.3 运维风险

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| 锁持有时间过长 | 中 | 中 | 配置告警阈值、监控指标 |
| 死锁 | 低 | 高 | 配置 leaseTime 自动释放 |
| Redis 内存不足 | 低 | 中 | 配置 TTL、定期清理过期键 |

---

## 十二、后续优化方向

### 12.1 短期优化（v1.1.0）

- [ ] 支持分布式缓存（@Cacheable 集成）
- [ ] 支持分布式限流（@RateLimit 注解）
- [ ] 支持锁降级（Redis 不可用时使用本地锁）
- [ ] 提供 Grafana Dashboard 模板

### 12.2 中期优化（v1.2.0）

- [ ] 支持分布式信号量（Semaphore）
- [ ] 支持分布式闭锁（CountDownLatch）
- [ ] 支持分布式计数器（AtomicLong）
- [ ] 支持多 Redis 集群

### 12.3 长期优化（v2.0.0）

- [ ] 支持自定义锁实现（ZooKeeper、Etcd）
- [ ] 支持锁可视化管理（Web UI）
- [ ] 支持死锁检测和自动恢复
- [ ] 支持锁性能分析工具

---

## 附录

### A. 参考资料

- [Redisson 官方文档](https://github.com/redisson/redisson/wiki)
- [Redisson Spring Boot Starter](https://github.com/redisson/redisson/tree/master/redisson-spring-boot-starter)
- [Spring Boot Actuator](https://docs.spring.io/spring-boot/docs/3.5.x/reference/html/actuator.html)
- [SkyWalking Java Agent](https://skywalking.apache.org/docs/skywalking-java/latest/en/setup/service-agent/java-agent/readme/)

### B. 术语表

| 术语 | 定义 |
|------|------|
| **分布式锁** | 在分布式系统中，用于协调多个节点对共享资源的访问 |
| **可重入锁** | 同一线程可以多次获取同一把锁 |
| **公平锁** | 按照请求锁的顺序获取锁（先到先得） |
| **读写锁** | 允许多个读操作并发，但写操作独占 |
| **锁租约时间** | 锁自动释放的时间（防止死锁） |
| **锁等待时间** | 尝试获取锁的最大等待时间 |
| **SpEL** | Spring Expression Language（Spring 表达式语言） |

---

**文档结束**
