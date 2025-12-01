---
type: adr
adr_id: 8
date: 2025-12-01
status: accepted
date_decided: 2025-12-01
deciders: [Qibin Lin]
technical_debt: none
tags:
  - decision/architecture
  - tech/async
  - tech/spring-boot
---

# ADR-008: 通用异步线程池管理

## 状态

**accepted**

## 背景

Patra 微服务架构中，多个服务需要执行异步任务：
- **patra-catalog**：MeSH 文件缓存异步上传
- **patra-ingest**：数据采集后的异步处理
- **patra-registry**：配置变更异步通知

当前存在的问题：
1. **重复配置**：每个服务独立创建 `ThreadPoolTaskExecutor`，配置分散
2. **缺乏统一管理**：线程池生命周期（启动、关闭）各自处理
3. **监控盲区**：线程池状态（队列长度、活跃线程数）无法统一监控
4. **命名混乱**：各服务的线程命名规范不一致，排查问题困难
5. **配置硬编码**：线程池参数往往硬编码在代码中，无法动态调整

## 决策

我们将在 `patra-spring-boot-starter-core` 中引入通用异步线程池管理机制：

1. **AsyncExecutorRegistry**：命名线程池注册表，管理多个独立的线程池
2. **配置驱动**：通过 `patra.async.pools.*` 配置动态创建线程池
3. **Micrometer 集成**：自动注册线程池指标（当 MeterRegistry 可用时）
4. **优雅关闭**：实现 `DisposableBean`，应用关闭时正确终止所有线程池

配置示例：
```yaml
patra:
  async:
    enabled: true
    pools:
      cache-upload:
        core-size: 2
        max-size: 4
        queue-capacity: 50
        thread-name-prefix: cache-upload-
      data-sync:
        core-size: 4
        max-size: 8
        queue-capacity: 200
```

使用方式：
```java
asyncExecutorRegistry.getExecutor("cache-upload")
```

## 后果

### 正面影响

- **配置统一**：所有线程池配置集中在 YAML 中，易于管理和调整
- **监控完善**：自动注册 Micrometer 指标，Grafana 可视化线程池状态
- **命名规范**：统一的线程命名规范，便于日志排查和问题定位
- **生命周期管理**：统一的启动和关闭逻辑，避免资源泄露
- **复用便捷**：各服务通过注入 `AsyncExecutorRegistry` 获取线程池，无需自行创建

### 负面影响

- **学习成本**：开发者需要了解 `AsyncExecutorRegistry` 的使用方式
- **依赖增加**：使用异步功能的模块需要依赖 `patra-spring-boot-starter-core`

### 风险

- 配置错误可能导致线程池不存在（`getExecutor` 抛出异常）
- 队列容量设置不当可能导致内存压力

## 替代方案

### 方案 A：每个服务自行创建线程池

各服务独立配置 `@Bean ThreadPoolTaskExecutor`。

**优点**：
- 无额外依赖
- 完全自主控制

**缺点**：
- 配置分散，难以统一管理
- 重复代码（每个服务都要写相似的配置）
- 监控需要各自实现
- 关闭逻辑容易遗漏

### 方案 B：使用 Spring @Async

使用 Spring 的 `@Async` 注解和 `@EnableAsync`。

**优点**：
- Spring 原生支持
- 声明式编程，使用简单

**缺点**：
- 方法级别的异步，粒度较粗
- 难以区分不同场景的线程池
- 异常处理复杂
- 与响应式编程模型不兼容

### 方案 C：使用 CompletableFuture 默认线程池

直接使用 `CompletableFuture.runAsync()` 的默认 `ForkJoinPool`。

**优点**：
- 无需配置
- JDK 原生支持

**缺点**：
- 共享 `ForkJoinPool.commonPool()`，可能与其他框架冲突
- 无法控制线程数和队列大小
- 无法监控线程池状态
- I/O 密集型任务不适合 ForkJoinPool

## 参考资料

- [Spring ThreadPoolTaskExecutor](https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/concurrent/ThreadPoolTaskExecutor.html)
- [Micrometer ExecutorServiceMetrics](https://micrometer.io/docs/ref/jvm)
- [Java Concurrency in Practice - Chapter 8: Applying Thread Pools](https://jcip.net/)
