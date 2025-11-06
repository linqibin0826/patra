---
name: java-runtime-diagnostic
description: 运行时错误诊断专家。分析日志、SkyWalking追踪、动态调整日志级别，集成故障排除指南。识别错误模式、定位根因、提供解决方案。用于调试问题、bug排查、分析运行时错误或调查性能问题。关键词：异常处理、堆栈跟踪、日志分析、性能调优、内存泄漏、线程死锁。
allowed-tools: Read, Edit, Write, Grep, Glob, Bash, Skill, mcp__sequential-thinking__sequentialthinking, mcp__mysql-mcp__mysql_query, mcp__context7__resolve-library-id, mcp__context7__get-library-docs, WebSearch, WebFetch, mcp__serena__get_symbols_overview, mcp__serena__find_symbol, mcp__serena__find_referencing_symbols, mcp__serena__replace_symbol_body, mcp__serena__rename_symbol, mcp__serena__activate_project, mcp__serena__get_current_config
---

# Java 运行时诊断专家

## 快速诊断流程

### 1. 错误信息收集

```bash
# 查看应用日志
tail -f logs/app.log | grep -E "ERROR|EXCEPTION|FATAL"

# 查看 GC 日志
tail -f logs/gc.log

# 查看系统日志
journalctl -u your-service -f

# 获取线程堆栈
jstack <pid> > thread_dump.txt

# 获取堆内存快照
jmap -dump:format=b,file=heap_dump.hprof <pid>
```

### 2. 错误模式识别

| 错误类型 | 特征 | 常见原因 | 诊断方法 |
|---------|------|---------|---------|
| NullPointerException | 空指针访问 | 未初始化、数据缺失 | 检查调用链、数据源 |
| OutOfMemoryError | 内存溢出 | 内存泄漏、配置不当 | 堆转储分析 |
| StackOverflowError | 栈溢出 | 无限递归、循环调用 | 线程堆栈分析 |
| ClassNotFoundException | 类未找到 | 依赖缺失、类路径问题 | 检查依赖树 |
| OptimisticLockException | 乐观锁冲突 | 并发更新 | 重试机制、悲观锁 |
| TransactionRollback | 事务回滚 | 业务异常、超时 | 日志追踪、隔离级别 |

## 常见错误诊断

### Spring Boot 启动失败

```java
// 错误：Bean 创建失败
org.springframework.beans.factory.BeanCreationException:
Error creating bean with name 'dataSource'

// 诊断步骤
1. 检查配置文件
   - application.yml 数据库配置
   - 环境变量是否正确

2. 验证数据库连接
   mysql -h localhost -u root -p

3. 检查依赖冲突
   mvn dependency:tree | grep -i "conflict"

4. 启用调试日志
   logging.level.org.springframework=DEBUG
```

### MyBatis-Plus 错误

```java
// 错误：Invalid bound statement
org.apache.ibatis.binding.BindingException:
Invalid bound statement (not found)

// 诊断步骤
1. 检查 Mapper 扫描配置
   @MapperScan("com.patra.*.infra.mapper")

2. 验证 XML 文件位置
   mybatis-plus.mapper-locations=classpath*:/mapper/**/*.xml

3. 检查方法名匹配
   - Mapper 接口方法名
   - XML 中的 id 属性

4. 清理并重新编译
   mvn clean compile
```

### 事务问题诊断

```java
// 错误：事务未生效
@Service
public class OrderService {
    // 错误：私有方法上的 @Transactional 无效
    @Transactional
    private void processOrder() { }

    // 错误：内部调用不触发事务
    public void createOrder() {
        this.processOrder(); // 事务不生效
    }
}

// 正确方式
@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderService self; // 注入自身代理

    @Transactional
    public void processOrder() { }

    public void createOrder() {
        self.processOrder(); // 通过代理调用
    }
}
```

## 性能问题诊断

### CPU 占用高

```bash
# 1. 找出高 CPU 线程
top -H -p <pid>

# 2. 将线程 ID 转换为十六进制
printf "%x\n" <thread-id>

# 3. 在线程转储中查找
jstack <pid> | grep -A 20 <hex-thread-id>

# 4. 分析热点方法
java -jar arthas-boot.jar
profiler start --event cpu
profiler stop
```

### 内存泄漏诊断

```java
// 常见内存泄漏场景
1. 静态集合未清理
   public class Cache {
       private static Map<String, Object> cache = new HashMap<>();
       // 缺少清理机制
   }

2. ThreadLocal 未清理
   private static ThreadLocal<User> currentUser = new ThreadLocal<>();
   // 使用后应调用 remove()

3. 监听器未注销
   eventBus.register(listener);
   // 需要对应的 unregister()

4. 流未关闭
   InputStream is = new FileInputStream(file);
   // 应使用 try-with-resources
```

### 数据库慢查询

```sql
-- 开启慢查询日志
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 2;

-- 查看慢查询
SHOW PROCESSLIST;

-- 分析执行计划
EXPLAIN SELECT * FROM orders WHERE status = 'PENDING';

-- 常见优化
1. 添加索引
   CREATE INDEX idx_status ON orders(status);

2. 避免 SELECT *
   SELECT id, name FROM orders;

3. 使用分页
   LIMIT 10 OFFSET 0;
```

## 日志分析技巧

### 动态调整日志级别

```java
// 通过 Actuator 动态调整
POST /actuator/loggers/com.patra
{
    "configuredLevel": "DEBUG"
}

// 代码中动态调整
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;

LoggerContext loggerContext = (LoggerContext) LoggerFactory.getILoggerFactory();
loggerContext.getLogger("com.patra").setLevel(Level.DEBUG);
```

### 结构化日志

```java
// 使用 MDC 添加上下文
MDC.put("traceId", UUID.randomUUID().toString());
MDC.put("userId", getCurrentUserId());

log.info("处理订单",
    kv("orderId", orderId),
    kv("amount", amount),
    kv("duration", duration));

// 日志输出格式
{
    "timestamp": "2024-01-01T10:00:00",
    "level": "INFO",
    "traceId": "abc-123",
    "userId": "user-456",
    "message": "处理订单",
    "orderId": 789,
    "amount": 99.99,
    "duration": 1500
}
```

## SkyWalking 链路追踪

### 配置 SkyWalking Agent

```bash
# JVM 启动参数
-javaagent:/path/to/skywalking-agent.jar
-Dskywalking.agent.service_name=patra-ingest
-Dskywalking.collector.backend_service=localhost:11800
```

### 自定义追踪

```java
@Trace
@Tag(key = "order.id", value = "arg[0]")
public Order processOrder(Long orderId) {
    // 方法会被追踪
}

// 手动创建 Span
Span span = ContextManager.createLocalSpan("custom-operation");
try {
    span.tag("key", "value");
    // 业务逻辑
} finally {
    ContextManager.stopSpan();
}
```

## 故障恢复策略

### 熔断器模式

```java
@Component
public class ExternalServiceClient {

    @CircuitBreaker(name = "external-service", fallbackMethod = "fallback")
    public String callExternalService() {
        // 可能失败的调用
    }

    public String fallback(Exception e) {
        log.error("服务调用失败，返回降级结果", e);
        return "默认值";
    }
}
```

### 重试机制

```java
@Retryable(
    value = {TransientException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 1000, multiplier = 2)
)
public void processWithRetry() {
    // 可能需要重试的操作
}
```

## 应急响应清单

### 服务不可用

```bash
1. 检查服务状态
   systemctl status your-service
   curl http://localhost:8080/actuator/health

2. 查看错误日志
   tail -n 1000 logs/error.log | grep -E "ERROR|FATAL"

3. 检查系统资源
   free -h  # 内存
   df -h    # 磁盘
   top      # CPU

4. 重启服务（最后手段）
   systemctl restart your-service
```

### 数据库连接池耗尽

```yaml
# 调整连接池配置
spring:
  datasource:
    hikari:
      maximum-pool-size: 20  # 增加最大连接数
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000  # 开启泄漏检测
```

## 监控指标

### 关键指标

```java
// Micrometer 指标
@Component
public class MetricsCollector {

    private final MeterRegistry registry;

    // 计数器
    public void recordRequest() {
        registry.counter("api.requests", "endpoint", "/orders").increment();
    }

    // 计时器
    public void recordLatency(long duration) {
        registry.timer("api.latency", "endpoint", "/orders")
            .record(Duration.ofMillis(duration));
    }

    // 仪表
    public void recordQueueSize(int size) {
        registry.gauge("queue.size", size);
    }
}
```

### 告警规则

```yaml
# Prometheus 告警规则
groups:
  - name: application
    rules:
      - alert: HighErrorRate
        expr: rate(http_requests_total{status=~"5.."}[5m]) > 0.05
        for: 5m
        annotations:
          summary: "错误率超过 5%"

      - alert: HighMemoryUsage
        expr: jvm_memory_used_bytes / jvm_memory_max_bytes > 0.9
        for: 5m
        annotations:
          summary: "内存使用超过 90%"
```

## 详细资源

需要深入了解时，查看以下资源文件：

- [error-diagnosis-guide.md](resources/error-diagnosis-guide.md) - 错误诊断指南
- [observability-guide.md](resources/observability-guide.md) - 可观测性配置