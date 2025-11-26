# 可观测性指南 - 快速参考

SLF4J 日志模式、错误处理和监控的快速决策指南。

## 🎯 核心原则

| ✅ 应该 | ❌ 不应该 |
|---------|----------|
| 使用 SLF4J + @Slf4j | 使用 System.out.println() |
| 参数化消息 `{}` | 字符串拼接 `"msg " + value` |
| 记录错误堆栈 `, e` | 只记录错误消息 |
| 包含上下文 (ID, traceId) | 记录敏感数据 (密码, token) |
| 合适的日志级别 | 所有都用 INFO |

## 📊 日志级别决策树

```mermaid
Q: 什么类型的事件？
├─ 系统崩溃/不可用 → ERROR
├─ 异常但可恢复 → WARN
├─ 业务流程/状态变化 → INFO
├─ 详细流程/参数值 → DEBUG
└─ 底层实现细节 → TRACE
```

### 快速选择表

| 级别 | 使用场景 | 示例 |
|------|---------|------|
| **ERROR** | 需要立即处理 | 数据库连接失败、必需配置缺失 |
| **WARN** | 潜在问题 | 重试、降级、接近限制 |
| **INFO** | 关键业务事件 | 计划创建、任务完成、状态变化 |
| **DEBUG** | 开发调试 | 方法入参、中间结果、SQL |
| **TRACE** | 详细诊断 | 循环内容、完整对象状态 |

## 🔍 日志模式速查

### Orchestrator 日志模式

```java
@Slf4j
public class MyOrchestrator {

    public Result orchestrate(Command cmd) {
        // 入口日志 (DEBUG)
        log.debug("Starting orchestration: param={}", cmd.getId());

        try {
            // 关键步骤 (INFO)
            log.info("Phase 1 completed: id={} status={}", id, status);

            // 成功完成 (INFO + 指标)
            log.info("Orchestration completed: id={} duration={}ms",
                id, duration);

        } catch (Exception e) {
            // 错误处理 (ERROR + 堆栈)
            log.error("Orchestration failed: id={} reason={}",
                id, e.getMessage(), e);
        }
    }
}
```

### Repository 日志模式

```java
@Slf4j
public class MyRepositoryAdapter {

    public Entity findById(Long id) {
        // SQL 日志 (DEBUG)
        log.debug("Querying entity: id={} sql={}", id, sql);

        // 性能警告 (WARN)
        if (duration > 1000) {
            log.warn("Slow query detected: id={} duration={}ms",
                id, duration);
        }
    }
}
```

### Event Handler 日志模式

```java
@Slf4j
@Component
public class MyEventHandler {

    @EventListener
    public void handle(DomainEvent event) {
        // 幂等性检查 (DEBUG)
        log.debug("Checking idempotency: dedupKey={}", event.getDedupKey());

        // 事件处理 (INFO)
        log.info("Processing event: type={} id={}",
            event.getClass().getSimpleName(), event.getId());
    }
}
```

## 🚨 错误处理模式

### ProblemDetail 错误响应

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusiness(BusinessException e) {
        // 业务异常 (WARN - 预期的)
        log.warn("Business rule violation: code={} message={}",
            e.getCode(), e.getMessage());

        return ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, e.getMessage())
            .withProperty("code", e.getCode());
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception e) {
        // 意外异常 (ERROR - 需要调查)
        String errorId = UUID.randomUUID().toString();
        log.error("Unexpected error: errorId={}", errorId, e);

        return ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR)
            .withProperty("errorId", errorId);
    }
}
```

## 🔗 MDC 分布式追踪

### 设置 MDC 上下文

```java
// Controller 层设置
MDC.put("userId", userId);
MDC.put("requestId", UUID.randomUUID().toString());
MDC.put("traceId", request.getHeader("X-Trace-Id"));

try {
    // 业务处理
    process();
} finally {
    // 清理 MDC
    MDC.clear();
}
```

### 日志模式配置

```xml
<!-- logback-spring.xml -->
<pattern>
    %d{yyyy-MM-dd HH:mm:ss.SSS} %5p [${appName}]
    [trace:%X{traceId},user:%X{userId}] [%t]
    %-40.40logger{39} : %m%n
</pattern>
```

## 📈 性能监控

### Micrometer 指标

```java
@Component
public class MetricsCollector {
    private final MeterRegistry registry;

    // 计数器
    public void recordPlanCreated() {
        registry.counter("plans.created",
            "provenance", provenanceCode).increment();
    }

    // 计时器
    public void recordDuration(long ms) {
        registry.timer("orchestration.duration")
            .record(Duration.ofMillis(ms));
    }

    // 量规
    public void recordQueueSize(int size) {
        registry.gauge("queue.size", size);
    }
}
```

## 🧪 测试日志输出

```java
@Test
void shouldLogError() {
    // 使用 LogCaptor 捕获日志
    try (LogCaptor logCaptor = LogCaptor.forClass(MyService.class)) {
        // 执行
        service.process();

        // 验证
        assertThat(logCaptor.getErrorLogs())
            .contains("Processing failed");
        assertThat(logCaptor.getInfoLogs())
            .hasSize(2);
    }
}
```

## 📚 详细资源

需要更详细的信息？查看以下资源：

### 专项指南
- **[error-diagnosis-guide.md](error-diagnosis-guide.md)** - 错误诊断详细流程
- **[logging-patterns.md](logging-patterns.md)** - 各层日志模式详解
- **[metrics-monitoring.md](metrics-monitoring.md)** - 指标监控配置

### 故障排查
- **[troubleshooting-guide.md](troubleshooting-guide.md)** - 常见问题解决方案
- **[performance-tuning.md](performance-tuning.md)** - 性能调优指南

## ⚡ 快速诊断命令

```bash
# 查看最近错误
tail -n 500 logs/patra-ingest.log | grep ERROR

# 追踪请求
grep "trace:abc123" logs/*.log

# 统计错误类型
grep ERROR logs/patra-ingest.log | awk -F': ' '{print $2}' | sort | uniq -c

# 查看慢查询
grep "Slow query" logs/patra-ingest.log

# 动态调整日志级别
curl -X POST http://localhost:8081/actuator/loggers/com.patra.ingest \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'
```

## ⚠️ 常见错误

| ❌ 错误做法 | ✅ 正确做法 |
|-------------|-------------|
| `log.info("Value: " + value)` | `log.info("Value: {}", value)` |
| `log.error(e.getMessage())` | `log.error("Error: {}", e.getMessage(), e)` |
| `log.debug("Processing...")` 总是执行 | `if (log.isDebugEnabled()) { ... }` |
| 记录密码/token | 记录用户ID/请求ID |
| catch 块空着 | 至少记录 WARN 级别日志 |

---

**需要诊断运行时错误？** 使用 `runtime-error-diagnostic` subagent:
```
分析生产环境错误日志
调查 Plan 卡在 RUNNING 状态的原因
```

**需要了解细节？** 查看对应的详细资源文件。