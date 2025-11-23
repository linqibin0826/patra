# Patra 可观测性 Starter 快速开始

> **5 分钟快速集成指南**

---

## 🚀 快速集成（3 步完成）

### 步骤 1：添加依赖

在你的服务模块的 `pom.xml` 中添加：

```xml
<dependencies>
    <!-- Patra 可观测性 Starter（通过 core starter 间接依赖）-->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-core</artifactId>
    </dependency>
</dependencies>
```

**注意**：如果你的服务已经依赖了 `patra-spring-boot-starter-core`，则无需修改依赖，observability starter 会自动生效。

### 步骤 2：配置 application.yml

在 `src/main/resources/application.yml` 中添加：

```yaml
spring:
  application:
    name: your-service-name  # 替换为实际服务名

patra:
  observability:
    enabled: true  # 启用可观测性
```

**就这样！** 使用默认配置即可开始工作。

### 步骤 3：启动服务

#### 方式 A：使用 Docker Compose（推荐）

```bash
cd /Users/linqibin/Desktop/Patra-api/docker

# 确保 SkyWalking 后端已启动
docker-compose -f docker-compose.dev.yaml up -d skywalking-oap skywalking-ui

# 启动你的服务（确保 Dockerfile 配置了 SkyWalking Agent）
docker-compose -f docker-compose.dev.yaml up -d your-service
```

#### 方式 B：本地启动（开发调试）

```bash
cd /Users/linqibin/Desktop/Patra-api

# 不使用 SkyWalking Agent（仅 Metrics 和 Logging）
mvn spring-boot:run -pl your-service-boot

# 或使用 SkyWalking Agent
java -javaagent:/path/to/skywalking-agent.jar \
     -Dskywalking.agent.service_name=your-service \
     -Dskywalking.collector.backend_service=localhost:11800 \
     -jar target/your-service.jar
```

---

## 🔍 验证集成

### 1. 检查服务健康

```bash
curl http://localhost:YOUR_PORT/actuator/health
```

**预期输出**：
```json
{
  "status": "UP"
}
```

### 2. 检查指标端点

```bash
curl http://localhost:YOUR_PORT/actuator/prometheus
```

**预期输出**：包含 Prometheus 格式的指标，如：
```
# HELP patra_http_client_requests_success REST 客户端成功请求总数
# TYPE patra_http_client_requests_success counter
patra_http_client_requests_success{application="your-service",environment="dev"} 10.0
```

### 3. 检查 SkyWalking UI

访问 http://localhost:8088

**验证**：
- 服务列表中出现你的服务名
- 可以看到追踪链路（需要发送一些请求）

### 4. 检查日志中的 traceId

查看应用日志：

```bash
docker logs your-service | grep -i trace
```

**预期输出**：
```
2025-11-23 10:15:32.123 [abc123.001.789] [your-service,abc123,789] [http-nio-8080-exec-1] INFO  com.patra.YourController - 处理请求
```

---

## 📊 自定义配置（可选）

### 添加公共标签

```yaml
patra:
  observability:
    metrics:
      common-tags:
        team: your-team-name
        service: your-service
        version: 1.0.0
```

### 调整采样率

```yaml
patra:
  observability:
    tracing:
      sampling-rate: 0.1  # 10% 采样（生产环境建议）
```

### 配置慢操作阈值

```yaml
patra:
  observability:
    handlers:
      performance:
        slow-threshold: 2s  # 超过 2 秒记录警告
```

---

## 💡 使用示例

### 示例 1：使用 @Observed 注解

```java
import io.micrometer.observation.annotation.Observed;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    @Observed(
        name = "user.create",
        contextualName = "create-user",
        lowCardinalityKeyValues = {"operation", "create"}
    )
    public User createUser(CreateUserRequest request) {
        // 业务逻辑
        return user;
    }
}
```

**效果**：
- 自动生成 Observation
- 自动收集耗时指标
- 自动记录追踪信息

### 示例 2：自定义指标

```java
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final Counter orderCounter;

    public OrderService(MeterRegistry meterRegistry) {
        this.orderCounter = Counter.builder("patra.order.processed")
            .description("订单处理总数")
            .tag("status", "success")
            .register(meterRegistry);
    }

    public void processOrder(Order order) {
        // 业务逻辑
        doProcess(order);

        // 记录指标
        orderCounter.increment();
    }
}
```

### 示例 3：手动追踪

```java
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final ObservationRegistry observationRegistry;

    public PaymentService(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    public void processPayment(String orderId, BigDecimal amount) {
        Observation.createNotStarted("payment.process", observationRegistry)
            .lowCardinalityKeyValue("order.id", orderId)
            .lowCardinalityKeyValue("operation", "process")
            .observe(() -> {
                // 业务逻辑
                doProcessPayment(orderId, amount);
            });
    }
}
```

---

## 🎯 常见任务

### 任务 1：查看某个请求的完整追踪链路

1. 发送请求并记录 traceId（从日志或响应头获取）
2. 访问 SkyWalking UI：http://localhost:8088
3. 点击 "Trace" → 输入 traceId 搜索
4. 查看完整的调用链路

### 任务 2：创建 Grafana Dashboard

1. 启动 Prometheus（采集 `/actuator/prometheus` 端点）
2. 启动 Grafana 并添加 Prometheus 数据源
3. 导入 Dashboard 模板（见 `docs/grafana-dashboards/`）
4. 查看实时指标

### 任务 3：设置告警规则

**Prometheus 告警规则示例**：

```yaml
# alerts.yml
groups:
  - name: patra-alerts
    rules:
      - alert: HighErrorRate
        expr: rate(patra_http_server_requests_total{status=~"5.."}[5m]) > 0.1
        for: 5m
        labels:
          severity: critical
        annotations:
          summary: "高错误率告警"
          description: "服务 {{ $labels.application }} 的错误率超过 10%"
```

---

## 📚 进阶配置

### 配置多环境

**开发环境**（application-dev.yml）：
```yaml
patra:
  observability:
    environment: dev
    tracing:
      sampling-rate: 1.0  # 100% 采样
```

**生产环境**（application-prod.yml）：
```yaml
patra:
  observability:
    environment: prod
    tracing:
      sampling-rate: 0.1  # 10% 采样
```

### 使用环境变量

```bash
# Docker Compose
environment:
  ENVIRONMENT: prod
  TRACING_SAMPLING_RATE: 0.1
  SKYWALKING_OAP_ADDRESS: skywalking-oap:11800
```

```yaml
# application.yml
patra:
  observability:
    environment: ${ENVIRONMENT:dev}
    tracing:
      sampling-rate: ${TRACING_SAMPLING_RATE:1.0}
```

---

## 🐛 故障排查

### 问题：SkyWalking UI 看不到服务

**检查**：
```bash
# 1. 检查 SkyWalking Agent 是否启动
ps aux | grep skywalking

# 2. 检查 OAP 连接
telnet skywalking-oap 11800

# 3. 查看日志
tail -f logs/skywalking-api.log
```

### 问题：指标未上报

**检查**：
```bash
# 1. 访问 Actuator 端点
curl http://localhost:YOUR_PORT/actuator

# 2. 检查 Prometheus 端点
curl http://localhost:YOUR_PORT/actuator/prometheus

# 3. 查看启动日志
grep -i "MeterRegistry" logs/application.log
```

### 问题：日志中无 traceId

**检查 Logback 配置**：

```xml
<!-- logback-spring.xml -->
<configuration>
    <conversionRule conversionWord="tid"
                    converterClass="org.apache.skywalking.apm.toolkit.log.logback.v1.x.TraceIdConverter"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%tid] - %msg%n</pattern>
        </encoder>
    </appender>
</configuration>
```

---

## 📖 完整文档

- [架构设计文档](./observability-starter-design.md)
- [实施指南](./observability-implementation-guide.md)
- [配置示例](./observability-config-examples.yaml)

---

## 🤝 获取帮助

如有问题，请查阅：
1. [常见问题 FAQ](./observability-starter-design.md#附录)
2. [SkyWalking 官方文档](https://skywalking.apache.org/docs/)
3. [Micrometer 官方文档](https://micrometer.io/docs/)

---

**开始你的可观测性之旅吧！** 🎉
