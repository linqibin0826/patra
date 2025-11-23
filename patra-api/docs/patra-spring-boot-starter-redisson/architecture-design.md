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
├── redisson-spring-boot-starter (Redisson 官方 Starter)
├── patra-spring-boot-starter-core (错误处理、可观测性)
├── spring-boot-starter-aop (AOP 支持)
├── spring-boot-starter-cache (Spring Cache 集成)
└── micrometer-core (指标收集)
```

### 3.3 与现有 Starter 的关系

| Starter | 关系 | 说明 |
|---------|------|------|
| `patra-spring-boot-starter-core` | **依赖** | 复用错误处理框架、Clock、可观测性 |
| `patra-spring-boot-starter-mybatis` | **独立** | 无直接依赖 |
| `patra-spring-boot-starter-batch` | **被依赖** | batch starter 依赖 redisson starter |

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

#### 6.1.5 LockAspect AOP 实现

**职责**：拦截 @DistributedLock 注解，自动获取/释放锁

```java
package com.patra.starter.redisson.lock;

import com.patra.starter.redisson.exception.LockAcquisitionException;
import com.patra.starter.redisson.listener.LockLoggingListener;
import com.patra.starter.redisson.listener.LockMetricsListener;
import com.patra.starter.redisson.listener.LockTracingListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

/**
 * 分布式锁 AOP 切面
 * <p>
 * 拦截 {@link DistributedLock} 注解，自动获取/释放锁
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Aspect
@Slf4j
@RequiredArgsConstructor
public class LockAspect {

    private final RedissonClient redissonClient;
    private final LockKeyGenerator keyGenerator;
    private final LockMetricsListener metricsListener;
    private final LockTracingListener tracingListener;
    private final LockLoggingListener loggingListener;

    /**
     * 环绕通知：拦截 @DistributedLock 注解方法
     */
    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint pjp, DistributedLock distributedLock) throws Throwable {

        // 1. 生成锁键（支持 SpEL）
        String lockKey = keyGenerator.generateKey(distributedLock.key(), pjp);

        // 2. 开始追踪
        tracingListener.beforeLock(lockKey);
        loggingListener.beforeLock(lockKey);

        // 3. 获取锁实例
        RLock lock = getLock(lockKey, distributedLock.type());

        boolean acquired = false;
        long startTime = System.currentTimeMillis();

        try {
            // 4. 尝试获取锁
            acquired = lock.tryLock(
                distributedLock.waitTime(),
                distributedLock.leaseTime(),
                distributedLock.timeUnit()
            );

            // 5. 处理获取失败
            if (!acquired) {
                long waitTime = System.currentTimeMillis() - startTime;

                // 记录失败指标
                metricsListener.onLockFailed(lockKey, waitTime, "timeout");
                loggingListener.onLockFailed(lockKey, waitTime);

                // 抛异常或返回 null
                if (distributedLock.throwExceptionOnFailure()) {
                    String message = distributedLock.failureMessage().isEmpty()
                        ? "无法获取分布式锁: " + lockKey
                        : distributedLock.failureMessage();
                    throw new LockAcquisitionException(message, lockKey, waitTime);
                }

                return null;
            }

            // 6. 获取成功，记录指标
            long waitTime = System.currentTimeMillis() - startTime;
            metricsListener.onLockAcquired(lockKey, waitTime);
            loggingListener.onLockAcquired(lockKey, waitTime);

            // 7. 执行业务逻辑
            return pjp.proceed();

        } finally {
            // 8. 释放锁（finally 保证执行）
            if (acquired && lock.isHeldByCurrentThread()) {
                try {
                    lock.unlock();

                    long holdTime = System.currentTimeMillis() - startTime;
                    metricsListener.onLockReleased(lockKey, holdTime);
                    loggingListener.onLockReleased(lockKey, holdTime);

                } catch (Exception e) {
                    log.error("释放锁失败: {}", lockKey, e);
                }
            }

            // 9. 结束追踪
            tracingListener.afterLock(lockKey, acquired);
        }
    }

    /**
     * 根据锁类型获取锁实例
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

#### 6.1.6 LockKeyGenerator

**职责**：解析 SpEL 表达式，生成锁键

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
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.lang.reflect.Method;

/**
 * 锁键生成器
 * <p>
 * 支持 SpEL 表达式解析，动态生成锁键
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
     * 生成锁键
     *
     * @param keyExpression SpEL 表达式
     * @param pjp           连接点
     * @return 完整的锁键（含前缀）
     */
    public String generateKey(String keyExpression, ProceedingJoinPoint pjp) {
        try {
            // 获取方法信息
            MethodSignature signature = (MethodSignature) pjp.getSignature();
            Method method = signature.getMethod();
            Object[] args = pjp.getArgs();

            // 创建 SpEL 上下文
            EvaluationContext context = new MethodBasedEvaluationContext(
                pjp.getTarget(),
                method,
                args,
                parameterNameDiscoverer
            );

            // 解析表达式
            String parsedKey = parser.parseExpression(keyExpression).getValue(context, String.class);

            // 添加前缀
            String fullKey = keyPrefix + parsedKey;

            log.debug("生成锁键: {} (表达式: {})", fullKey, keyExpression);

            return fullKey;

        } catch (Exception e) {
            log.error("解析锁键表达式失败: {}", keyExpression, e);
            throw new IllegalArgumentException("Invalid lock key expression: " + keyExpression, e);
        }
    }
}
```

#### 6.1.7 LockMetricsListener

**职责**：记录 Micrometer 指标

```java
package com.patra.starter.redisson.listener;

import com.patra.starter.redisson.config.RedissonProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.TimeUnit;

/**
 * 锁指标监听器
 * <p>
 * 记录 Micrometer 指标：锁获取成功/失败、等待时间、持有时间
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class LockMetricsListener {

    private final MeterRegistry meterRegistry;
    private final RedissonProperties properties;

    /**
     * 锁获取成功
     */
    public void onLockAcquired(String lockKey, long waitTime) {
        // 记录成功计数
        Counter.builder("redisson.lock.acquired")
            .tag("key", simplifyKey(lockKey))
            .register(meterRegistry)
            .increment();

        // 记录等待时间
        Timer.builder("redisson.lock.wait.time")
            .tag("key", simplifyKey(lockKey))
            .register(meterRegistry)
            .record(waitTime, TimeUnit.MILLISECONDS);
    }

    /**
     * 锁获取失败
     */
    public void onLockFailed(String lockKey, long waitTime, String reason) {
        // 记录失败计数
        Counter.builder("redisson.lock.failed")
            .tag("key", simplifyKey(lockKey))
            .tag("reason", reason)
            .register(meterRegistry)
            .increment();

        // 记录等待时间
        Timer.builder("redisson.lock.wait.time.failed")
            .tag("key", simplifyKey(lockKey))
            .register(meterRegistry)
            .record(waitTime, TimeUnit.MILLISECONDS);
    }

    /**
     * 锁释放
     */
    public void onLockReleased(String lockKey, long holdTime) {
        // 记录持有时间
        Timer.builder("redisson.lock.hold.time")
            .tag("key", simplifyKey(lockKey))
            .register(meterRegistry)
            .record(holdTime, TimeUnit.MILLISECONDS);

        // 检查是否超过阈值（告警）
        long threshold = properties.getLock().getObservability().getMetrics().getHoldTimeWarnThreshold();
        if (holdTime > threshold) {
            log.warn("锁持有时间过长: {} ({}ms), 阈值: {}ms", lockKey, holdTime, threshold);
        }
    }

    /**
     * 简化锁键（用于 tag，避免 cardinality 过高）
     * 例如: patra:lock:user:123 -> user
     */
    private String simplifyKey(String lockKey) {
        if (lockKey.startsWith(properties.getLock().getKeyPrefix())) {
            String simplifiedKey = lockKey.substring(properties.getLock().getKeyPrefix().length());
            int colonIndex = simplifiedKey.indexOf(':');
            return colonIndex > 0 ? simplifiedKey.substring(0, colonIndex) : simplifiedKey;
        }
        return lockKey;
    }
}
```

#### 6.1.8 LockTracingListener

**职责**：集成 SkyWalking 追踪

```java
package com.patra.starter.redisson.listener;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.toolkit.trace.ActiveSpan;
import org.apache.skywalking.apm.toolkit.trace.Tracer;

/**
 * 锁追踪监听器
 * <p>
 * 集成 SkyWalking，记录锁操作的分布式追踪
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Slf4j
public class LockTracingListener {

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

#### 6.1.9 LockLoggingListener

**职责**：记录日志

```java
package com.patra.starter.redisson.listener;

import com.patra.starter.redisson.config.RedissonProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 锁日志监听器
 * <p>
 * 记录锁获取/释放日志
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class LockLoggingListener {

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

#### 6.1.10 LockAcquisitionException

**职责**：锁获取失败异常

```java
package com.patra.starter.redisson.exception;

import lombok.Getter;

/**
 * 锁获取失败异常
 * <p>
 * 在无法获取锁时抛出（根据 {@link DistributedLock#throwExceptionOnFailure()} 配置）
 *
 * @author Patra Team
 * @since 1.0.0
 */
@Getter
public class LockAcquisitionException extends RuntimeException {

    /**
     * 锁键
     */
    private final String lockKey;

    /**
     * 等待时间（毫秒）
     */
    private final long waitTime;

    public LockAcquisitionException(String message, String lockKey, long waitTime) {
        super(message);
        this.lockKey = lockKey;
        this.waitTime = waitTime;
    }

    public LockAcquisitionException(String message, String lockKey, long waitTime, Throwable cause) {
        super(message, cause);
        this.lockKey = lockKey;
        this.waitTime = waitTime;
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

### 7.1 默认配置

**application.yml**（Starter 内置）：

```yaml
patra:
  redisson:
    enabled: true
    threads: 16
    netty-threads: 32
    lock-watchdog-timeout: 30000

    # 分布式锁配置
    lock:
      enabled: true
      key-prefix: "patra:lock:"
      default-lease-time: 30
      default-wait-time: 0

      # 可观测性配置
      observability:
        tracing:
          enabled: true
        metrics:
          enabled: true
          hold-time-warn-threshold: 60000  # 60 秒
        logging:
          enabled: true
          hold-time-warn-threshold: 60000  # 60 秒

    # 分布式缓存配置
    cache:
      enabled: false
      default-ttl: 3600

# Redisson 官方配置（基于 redisson-spring-boot-starter）
spring:
  data:
    redis:
      redisson:
        config: |
          singleServerConfig:
            address: "redis://127.0.0.1:6379"
            database: 0
            connectionPoolSize: 64
            connectionMinimumIdleSize: 24
            timeout: 3000
            retryAttempts: 3
            retryInterval: 1500
```

### 7.2 业务服务配置

**patra-catalog 的 application.yml**：

```yaml
patra:
  redisson:
    lock:
      key-prefix: "catalog:lock:"  # 自定义前缀
      default-lease-time: 7200     # MeSH 导入需要 2 小时

      observability:
        metrics:
          hold-time-warn-threshold: 120000  # 2 分钟

# Redis 连接配置
spring:
  data:
    redis:
      redisson:
        config: |
          singleServerConfig:
            address: "redis://${REDIS_HOST:localhost}:${REDIS_PORT:6379}"
            password: ${REDIS_PASSWORD:}
            database: 0
```

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
 * 全局异常处理器
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理锁获取失败异常
     */
    @ExceptionHandler(LockAcquisitionException.class)
    public ResponseEntity<Map<String, Object>> handleLockAcquisitionException(
            LockAcquisitionException ex
    ) {
        return ResponseEntity.status(409).body(Map.of(
            "error", "LOCK_ACQUISITION_FAILED",
            "message", ex.getMessage(),
            "lockKey", ex.getLockKey(),
            "waitTime", ex.getWaitTime()
        ));
    }
}
```

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

## 九、实施计划

### 9.1 开发阶段

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

### 9.2 验证阶段

| 任务 | 工作量 | 优先级 |
|------|--------|--------|
| 在 patra-catalog 中使用（MeSH 导入任务） | 2h | P0 |
| 在 patra-ingest 中使用（采集任务） | 2h | P0 |
| 性能测试（并发 1000 锁获取） | 2h | P1 |
| 压力测试（Redis 故障场景） | 2h | P2 |

**总计**：约 **8 工时**（1 个工作日）

### 9.3 时间表

| 里程碑 | 预计完成时间 | 交付物 |
|--------|------------|--------|
| **M1: Starter 开发完成** | D+4 | patra-spring-boot-starter-redisson 1.0.0（P0 功能） |
| **M2: 业务服务集成** | D+5 | patra-catalog、patra-ingest 使用 redisson starter |
| **M3: 文档完善** | D+5 | 完整的设计文档、使用指南、示例代码 |

---

## 十、风险评估

### 10.1 技术风险

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

## 十一、后续优化方向

### 11.1 短期优化（v1.1.0）

- [ ] 支持分布式缓存（@Cacheable 集成）
- [ ] 支持分布式限流（@RateLimit 注解）
- [ ] 支持锁降级（Redis 不可用时使用本地锁）
- [ ] 提供 Grafana Dashboard 模板

### 11.2 中期优化（v1.2.0）

- [ ] 支持分布式信号量（Semaphore）
- [ ] 支持分布式闭锁（CountDownLatch）
- [ ] 支持分布式计数器（AtomicLong）
- [ ] 支持多 Redis 集群

### 11.3 长期优化（v2.0.0）

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
