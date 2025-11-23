# Redisson Starter 与 Batch Starter 集成指南

**版本**: v1.0.0
**创建日期**: 2025-11-23
**适用场景**: patra-spring-boot-starter-batch 依赖 patra-spring-boot-starter-redisson

---

## 📋 目录

- [一、依赖关系调整](#一依赖关系调整)
- [二、Batch Starter 重构](#二batch-starter-重构)
- [三、使用示例](#三使用示例)
- [四、对比说明](#四对比说明)

---

## 一、依赖关系调整

### 1.1 原设计（❌ 不推荐）

```
patra-spring-boot-starter-batch
├─ 直接依赖 redisson-spring-boot-starter
├─ 自己实现 @DistributedJobLock 注解
├─ 自己实现 BatchJobLockAspect AOP
└─ 自己实现 JobLockKeyGenerator
```

**问题**：
- ❌ 重复造轮子（锁逻辑与通用场景重复）
- ❌ 职责不清（batch starter 承担了非批处理职责）
- ❌ 无法复用（其他场景无法使用分布式锁）

### 1.2 新设计（✅ 推荐）

```
patra-spring-boot-starter-redisson       ← 基础设施
├─ 提供 @DistributedLock（通用）
├─ 提供 LockAspect（通用）
└─ 提供 LockKeyGenerator（通用）

patra-spring-boot-starter-batch          ← 业务设施
├─ 依赖 patra-spring-boot-starter-redisson
├─ 提供 Spring Batch 自动配置
└─ 直接使用 @DistributedLock（不再自己实现）
```

**优势**：
- ✅ 职责清晰（redisson starter 专注锁，batch starter 专注批处理）
- ✅ 代码复用（@DistributedLock 可用于任何场景）
- ✅ 架构解耦（batch starter 代码更简洁）

---

## 二、Batch Starter 重构

### 2.1 pom.xml 调整

**删除**：

```xml
<!-- ❌ 删除：不再直接依赖 Redisson -->
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
</dependency>
```

**新增**：

```xml
<!-- ✅ 新增：依赖 Redisson Starter -->
<dependency>
    <groupId>com.patra</groupId>
    <artifactId>patra-spring-boot-starter-redisson</artifactId>
    <version>1.0.0</version>
</dependency>
```

### 2.2 删除自定义锁代码

**删除以下类**：

```bash
# 删除分布式锁相关代码
rm patra-spring-boot-starter-batch/src/main/java/com/patra/starter/batch/lock/DistributedJobLock.java
rm patra-spring-boot-starter-batch/src/main/java/com/patra/starter/batch/lock/BatchJobLockAspect.java
rm patra-spring-boot-starter-batch/src/main/java/com/patra/starter/batch/lock/JobLockKeyGenerator.java

# 删除锁相关的自动配置
rm patra-spring-boot-starter-batch/src/main/java/com/patra/starter/batch/autoconfigure/DistributedLockAutoConfiguration.java
```

### 2.3 更新 Batch Starter 设计文档

**修改**：`docs/patra-spring-boot-starter-batch/architecture-design.md`

```markdown
## 三、技术选型

### 3.1 依赖关系

```
patra-spring-boot-starter-batch
├── spring-boot-starter-batch (Spring Batch 核心)
├── patra-spring-boot-starter-core (错误处理、可观测性)
├── patra-spring-boot-starter-redisson (分布式锁) ← 新增
└── micrometer-core (指标收集)
```

### 3.2 与现有 Starter 的关系

| Starter | 关系 | 说明 |
|---------|------|------|
| `patra-spring-boot-starter-core` | **依赖** | 复用错误处理框架、Clock、可观测性 |
| `patra-spring-boot-starter-redisson` | **依赖** | 使用 @DistributedLock 分布式锁 |
| `patra-spring-boot-starter-mybatis` | **可选依赖** | ItemWriter 可使用 MyBatis Mapper |
```

---

## 三、使用示例

### 3.1 业务服务使用（patra-catalog）

**pom.xml**：

```xml
<dependencies>
    <!-- 依赖 Batch Starter（会自动传递依赖 Redisson Starter） -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-batch</artifactId>
    </dependency>
</dependencies>
```

**XXL-Job Handler**：

```java
package com.patra.catalog.adapter.scheduler.job;

import com.patra.starter.batch.core.JobLauncherHelper;
import com.patra.starter.redisson.lock.DistributedLock;  // ← 使用 Redisson Starter 的注解
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.stereotype.Component;

import java.util.Map;

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
     * 使用 @DistributedLock 防止多实例并发执行
     */
    @XxlJob("meshImportJob")
    @DistributedLock(                          // ← 来自 Redisson Starter
        key = "batch:job:mesh-import",
        leaseTime = 7200,                       // 2 小时
        waitTime = 0,                           // 不等待
        throwExceptionOnFailure = true
    )
    public void execute() {
        log.info("MeSH 数据导入任务启动");

        Long executionId = jobLauncherHelper.launch(meshImportJob, Map.of(
            "year", "2024",
            "source", "NLM"
        ));

        log.info("MeSH 数据导入任务已提交，执行 ID: {}", executionId);
    }
}
```

### 3.2 与原设计对比

#### 原设计（❌ 使用自定义注解）

```java
@XxlJob("meshImportJob")
@DistributedJobLock(                         // ❌ Batch Starter 自定义注解
    key = "batch:job:mesh-import:#{#param}",
    timeout = 7200
)
public void execute(String param) {
    // ...
}
```

**问题**：
- ❌ 只能用于批处理任务
- ❌ 其他场景（如 REST API）无法使用
- ❌ 功能重复（与通用分布式锁重复）

#### 新设计（✅ 使用通用注解）

```java
@XxlJob("meshImportJob")
@DistributedLock(                            // ✅ Redisson Starter 通用注解
    key = "batch:job:mesh-import",
    leaseTime = 7200
)
public void execute() {
    // ...
}
```

**优势**：
- ✅ 通用注解，所有场景都能用
- ✅ 功能统一，不重复造轮子
- ✅ Batch Starter 代码更简洁

---

## 四、对比说明

### 4.1 代码量对比

| 维度 | 原设计（自定义锁） | 新设计（依赖 Redisson Starter） | 减少量 |
|------|------------------|-------------------------------|-------|
| **Batch Starter 类数** | 27 | 23 | -15% |
| **代码行数** | ~1800 行 | ~1400 行 | -22% |
| **锁相关代码** | ~400 行 | 0 行（复用） | -100% |

### 4.2 功能对比

| 功能 | 原设计 | 新设计 | 说明 |
|------|--------|--------|------|
| **分布式锁** | ✅ 自己实现 | ✅ 复用 Redisson Starter | 功能相同 |
| **SpEL 支持** | ✅ 自己实现 | ✅ 复用 Redisson Starter | 功能相同 |
| **可观测性** | ⚠️ 需自己集成 | ✅ Redisson Starter 内置 | 新设计更完善 |
| **多种锁类型** | ❌ 仅可重入锁 | ✅ 可重入/公平/读写 | 新设计更丰富 |
| **通用性** | ❌ 仅批处理 | ✅ 所有场景 | 新设计更通用 |

### 4.3 架构清晰度对比

#### 原设计

```
patra-spring-boot-starter-batch
├─ Spring Batch 自动配置        ✅ 核心职责
├─ Job/Step 编排                ✅ 核心职责
├─ 分布式锁实现                 ❌ 非核心职责（应该抽离）
└─ 可观测性集成                 ✅ 核心职责
```

#### 新设计

```
patra-spring-boot-starter-redisson
├─ 分布式锁实现                 ✅ 单一职责
└─ 可观测性集成

patra-spring-boot-starter-batch
├─ Spring Batch 自动配置        ✅ 单一职责
├─ Job/Step 编排                ✅ 单一职责
└─ 依赖 Redisson Starter        ✅ 职责清晰
```

**结论**：新设计符合**单一职责原则**，架构更清晰。

---

## 五、FAQ

### Q1: 为什么要创建独立的 Redisson Starter？

**A**: 分布式锁是通用能力，不仅批处理需要。创建独立 Starter 符合单一职责原则，避免重复代码。

### Q2: Batch Starter 还需要自己实现锁吗？

**A**: 不需要。直接使用 `@DistributedLock` 注解即可。

### Q3: 如果只用批处理，也要依赖 Redisson Starter？

**A**: 是的。Batch Starter 依赖 Redisson Starter 提供分布式锁能力。这样架构更清晰，未来其他场景也能复用。

### Q4: Redisson Starter 会增加额外开销吗？

**A**: 几乎没有。Redisson Starter 只提供基础设施，不使用就不会有额外开销。

### Q5: 可以不使用 Redisson Starter，自己实现锁吗？

**A**: 可以，但不推荐。这样会违反 DRY 原则，增加维护成本。

---

## 六、总结

### ✅ 推荐做法

1. **创建独立的 Redisson Starter**（提供通用分布式锁能力）
2. **Batch Starter 依赖 Redisson Starter**（复用分布式锁）
3. **所有服务统一使用 @DistributedLock**（标准化）

### ❌ 不推荐做法

1. ❌ Batch Starter 自己实现锁（重复造轮子）
2. ❌ 每个服务自己实现锁（重复代码）
3. ❌ 使用不同的锁实现（不统一）

### 🎯 核心原则

- **单一职责**：每个 Starter 专注一个领域
- **DRY（Don't Repeat Yourself）**：避免重复代码
- **可复用性**：通用能力统一提供
- **架构清晰**：依赖关系明确

---

**文档结束**
