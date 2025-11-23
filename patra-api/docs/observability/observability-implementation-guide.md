# Patra 可观测性 Starter 实施指南

> **版本**: 1.0.0
> **日期**: 2025-11-23
> **相关文档**: [架构设计文档](./observability-starter-design.md)

---

## 📋 目录

- [前提条件](#前提条件)
- [实施步骤](#实施步骤)
- [配置迁移指南](#配置迁移指南)
- [验证清单](#验证清单)
- [常见问题](#常见问题)
- [回滚方案](#回滚方案)

---

## 前提条件

### 开发环境要求

- **JDK**: 21+
- **Maven**: 3.9+
- **Docker**: 20.10+
- **Docker Compose**: 2.0+

### 必需的后端服务

确保以下服务已启动（通过 Docker Compose）：

```bash
cd /Users/linqibin/Desktop/Patra-api/docker
docker-compose -f docker-compose.dev.yaml up -d skywalking-oap skywalking-ui elasticsearch
```

验证服务状态：

```bash
# 检查 SkyWalking OAP
curl http://localhost:12800/healthcheck

# 检查 SkyWalking UI
curl http://localhost:8088

# 检查 Elasticsearch
curl http://localhost:9200/_cluster/health
```

---

## 实施步骤

### 阶段 1：创建 Observability Starter 模块

#### 1.1 在 patra-parent 中添加模块声明

**文件**: `/Users/linqibin/Desktop/Patra-api/pom.xml`

```xml
<modules>
    <!-- 现有模块 -->
    <module>patra-common</module>
    <module>patra-spring-boot-starter-core</module>
    <!-- ... 其他模块 ... -->

    <!-- 新增：可观测性 Starter -->
    <module>patra-spring-boot-starter-observability</module>
</modules>
```

#### 1.2 创建模块目录

```bash
cd /Users/linqibin/Desktop/Patra-api
mkdir -p patra-spring-boot-starter-observability/src/main/java/com/patra/starter/observability/{autoconfigure,config,handler,filter,convention,context,spi}
mkdir -p patra-spring-boot-starter-observability/src/main/resources/META-INF/spring
mkdir -p patra-spring-boot-starter-observability/src/test/java/com/patra/starter/observability
```

#### 1.3 创建 pom.xml

**文件**: `patra-spring-boot-starter-observability/pom.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
         http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.patra</groupId>
        <artifactId>patra-parent</artifactId>
        <version>0.0.1-SNAPSHOT</version>
        <relativePath>../patra-parent</relativePath>
    </parent>

    <artifactId>patra-spring-boot-starter-observability</artifactId>
    <name>Patra :: Spring Boot Starter :: Observability</name>
    <description>Patra 统一可观测性 Starter（Metrics、Tracing、Logging）</description>

    <dependencies>
        <!-- Spring Boot Actuator（必需） -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-actuator</artifactId>
        </dependency>

        <!-- Spring AOP（支持 @Observed）-->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-aop</artifactId>
        </dependency>

        <!-- Micrometer Core（Actuator 自动引入） -->
        <!-- Micrometer Observation（Actuator 自动引入） -->

        <!-- SkyWalking Toolkit - Trace -->
        <dependency>
            <groupId>org.apache.skywalking</groupId>
            <artifactId>apm-toolkit-trace</artifactId>
        </dependency>

        <!-- SkyWalking Toolkit - Logback -->
        <dependency>
            <groupId>org.apache.skywalking</groupId>
            <artifactId>apm-toolkit-logback-1.x</artifactId>
        </dependency>

        <!-- SkyWalking Micrometer Registry -->
        <dependency>
            <groupId>org.apache.skywalking</groupId>
            <artifactId>apm-toolkit-micrometer-registry</artifactId>
        </dependency>

        <!-- Prometheus Registry（可选） -->
        <dependency>
            <groupId>io.micrometer</groupId>
            <artifactId>micrometer-registry-prometheus</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot Configuration Processor -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-configuration-processor</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- 测试依赖 -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

#### 1.4 创建 AutoConfiguration.imports

**文件**: `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

```
com.patra.starter.observability.autoconfigure.ObservabilityAutoConfiguration
com.patra.starter.observability.autoconfigure.MicrometerAutoConfiguration
com.patra.starter.observability.autoconfigure.SkyWalkingMeterAutoConfiguration
com.patra.starter.observability.autoconfigure.PrometheusAutoConfiguration
com.patra.starter.observability.autoconfigure.ObservationHandlerAutoConfiguration
```

#### 1.5 编译验证

```bash
cd /Users/linqibin/Desktop/Patra-api
mvn clean install -pl patra-spring-boot-starter-observability -am
```

### 阶段 2：实现核心配置类

#### 2.1 实现 ObservabilityProperties

将设计文档中的 `ObservabilityProperties` 代码复制到：
`src/main/java/com/patra/starter/observability/config/ObservabilityProperties.java`

#### 2.2 实现 ObservabilityAutoConfiguration

将设计文档中的 `ObservabilityAutoConfiguration` 代码复制到：
`src/main/java/com/patra/starter/observability/autoconfigure/ObservabilityAutoConfiguration.java`

#### 2.3 实现 MicrometerAutoConfiguration

**文件**: `src/main/java/com/patra/starter/observability/autoconfigure/MicrometerAutoConfiguration.java`

```java
package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.config.ObservabilityProperties;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import java.util.Map;

/**
 * Micrometer 自动配置
 *
 * @author Jobs
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnClass(MeterRegistry.class)
@EnableConfigurationProperties(ObservabilityProperties.class)
@ConditionalOnProperty(
    prefix = "patra.observability.metrics",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public class MicrometerAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MicrometerAutoConfiguration.class);

    /**
     * 配置公共标签
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTagsCustomizer(
            ObservabilityProperties properties
    ) {
        return registry -> {
            Map<String, String> tags = properties.getMetrics().getCommonTags();

            // 添加应用标识标签
            registry.config().commonTags(
                "application", properties.getApplicationName(),
                "environment", properties.getEnvironment(),
                "region", properties.getRegion(),
                "cluster", properties.getCluster()
            );

            // 添加自定义标签
            tags.forEach((key, value) -> {
                if (value != null && !value.isEmpty()) {
                    registry.config().commonTags(key, value);
                }
            });

            log.info("已配置公共标签: application={}, environment={}, region={}, cluster={}, custom={}",
                properties.getApplicationName(),
                properties.getEnvironment(),
                properties.getRegion(),
                properties.getCluster(),
                tags);
        };
    }

    /**
     * 指标过滤器：统一命名规范
     */
    @Bean
    public MeterFilter metricNamingFilter() {
        return MeterFilter.renameTag("", "unknown", "UNKNOWN");
    }
}
```

#### 2.4 实现其他 AutoConfiguration 类

按照类似方式实现：
- `SkyWalkingMeterAutoConfiguration`
- `PrometheusAutoConfiguration`
- `ObservationHandlerAutoConfiguration`

（代码见设计文档中的示例）

### 阶段 3：重构 patra-starter-core

#### 3.1 添加对 observability starter 的依赖

**文件**: `patra-spring-boot-starter-core/pom.xml`

```xml
<dependencies>
    <!-- 新增：统一可观测性 Starter -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-observability</artifactId>
    </dependency>

    <!-- 保留其他依赖 -->
</dependencies>
```

#### 3.2 移除重复配置

**检查并移除以下重复配置**（如果存在）：
- 重复的 MeterRegistry 配置
- 重复的 TracingProperties
- 重复的公共标签配置

**保留以下配置**（核心功能）：
- `ErrorObservationRecorder`（错误观测）
- `TraceProvider` SPI 接口（追踪上下文提取）
- `HeaderBasedTraceProvider`（默认实现）

#### 3.3 更新 CoreAutoConfiguration

**文件**: `patra-spring-boot-starter-core/.../CoreAutoConfiguration.java`

```java
// 移除：不再创建独立的 MeterRegistry
// @Bean
// public MeterRegistry meterRegistry() { ... }

// 保留：使用注入的 MeterRegistry
@Bean
public ErrorObservationRecorder errorObservationRecorder(
    ErrorProperties errorProperties,
    ObjectProvider<MeterRegistry> meterRegistryProvider
) {
    // 现有逻辑保持不变
    // MeterRegistry 由 observability starter 提供
}
```

### 阶段 4：重构各业务 Starter

#### 4.1 patra-starter-rest-client 重构

**步骤 1**：更新指标命名

**文件**: `MetricsInterceptor.java`

```java
// 重构前
private static final String METRIC_SUCCESS = "rest_client_requests_success_total";
private static final String METRIC_FAILURE = "rest_client_requests_failure_total";
private static final String METRIC_DURATION = "rest_client_request_duration_seconds";

// 重构后（遵循 Patra 命名规范）
private static final String METRIC_SUCCESS = "patra.http.client.requests.success";
private static final String METRIC_FAILURE = "patra.http.client.requests.failure";
private static final String METRIC_DURATION = "patra.http.client.requests.duration";
```

**步骤 2**：使用统一的 MeterRegistry

```java
// 构造函数注入（无需修改，Spring 自动注入）
public MetricsInterceptor(MeterRegistry meterRegistry) {
    this.successCounter = Counter.builder(METRIC_SUCCESS)
        .description("REST 客户端成功请求总数")
        .register(meterRegistry);

    // ... 其他指标
}
```

#### 4.2 patra-starter-batch 重构

**实现 ObservationJobListener**

**文件**: `src/main/java/com/patra/starter/batch/listener/ObservationJobListener.java`

```java
package com.patra.starter.batch.listener;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;

/**
 * Spring Batch Job 观测监听器
 *
 * <p>基于 Micrometer Observation API 实现 Job 执行的可观测性。
 *
 * @author Jobs
 * @since 1.0.0
 */
public class ObservationJobListener implements JobExecutionListener {

    private static final Logger log = LoggerFactory.getLogger(ObservationJobListener.class);

    private final ObservationRegistry observationRegistry;

    public ObservationJobListener(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();

        Observation observation = Observation.createNotStarted("batch.job.execution", observationRegistry)
            .lowCardinalityKeyValue("job.name", jobName)
            .lowCardinalityKeyValue("job.id", String.valueOf(jobExecution.getJobId()))
            .start();

        // 将 Observation 存储到 ExecutionContext 中，以便在 afterJob 中使用
        jobExecution.getExecutionContext().put("observation", observation);

        log.info("Batch Job 开始执行: jobName={}, jobId={}", jobName, jobExecution.getJobId());
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        String jobName = jobExecution.getJobInstance().getJobName();
        String status = jobExecution.getStatus().name();

        // 从 ExecutionContext 中获取 Observation
        Observation observation = (Observation) jobExecution.getExecutionContext().get("observation");

        if (observation != null) {
            observation.lowCardinalityKeyValue("status", status);

            if (jobExecution.getAllFailureExceptions().isEmpty()) {
                observation.stop();
            } else {
                observation.error(jobExecution.getAllFailureExceptions().get(0));
                observation.stop();
            }
        }

        log.info("Batch Job 执行完成: jobName={}, jobId={}, status={}, duration={}ms",
            jobName,
            jobExecution.getJobId(),
            status,
            jobExecution.getEndTime().getTime() - jobExecution.getStartTime().getTime());
    }
}
```

**更新 ObservabilityAutoConfiguration**

**文件**: `patra-starter-batch/.../ ObservabilityAutoConfiguration.java`

```java
// 移除 TODO 标记，实现 Bean
@Bean
@ConditionalOnBean(ObservationRegistry.class)
@ConditionalOnProperty(
    prefix = "patra.batch.observability.tracing",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true
)
public ObservationJobListener observationJobListener(ObservationRegistry observationRegistry) {
    log.info("创建 ObservationJobListener");
    return new ObservationJobListener(observationRegistry);
}
```

### 阶段 5：配置 SkyWalking Agent

#### 5.1 下载 SkyWalking Agent

```bash
cd /Users/linqibin/Desktop/Patra-api/docker
mkdir -p skywalking-agent
cd skywalking-agent

# 下载 SkyWalking Agent（版本与 Toolkit 一致）
wget https://archive.apache.org/dist/skywalking/java-agent/9.5.0/apache-skywalking-java-agent-9.5.0.tgz
tar -xzf apache-skywalking-java-agent-9.5.0.tgz
mv skywalking-agent/* .
rm -rf skywalking-agent apache-skywalking-java-agent-9.5.0.tgz
```

#### 5.2 配置 agent.config

**文件**: `docker/skywalking-agent/config/agent.config`

```properties
# 服务名称（从环境变量获取）
agent.service_name=${SW_AGENT_NAME:patra-api}

# OAP 服务器地址
collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:skywalking-oap:11800}

# 采样配置
agent.sample_n_per_3_secs=${SW_AGENT_SAMPLE:3}

# 忽略端点（健康检查、指标等）
trace.ignore_path=/health,/actuator/**,/metrics,/prometheus,/favicon.ico

# SQL 参数收集
plugin.jdbc.trace_sql_parameters=true
plugin.jdbc.sql_parameters_max_length=512

# HTTP 参数收集
plugin.http.http_params_length_threshold=2048

# 日志级别
logging.level=${SW_LOGGING_LEVEL:INFO}

# 实例属性
agent.instance_properties[region]=${REGION:unknown}
agent.instance_properties[cluster]=${CLUSTER_NAME:default}
```

#### 5.3 创建启动脚本

**文件**: `docker/scripts/start-with-skywalking.sh`

```bash
#!/bin/bash
set -e

# SkyWalking Agent 路径
SKYWALKING_AGENT_PATH="${SKYWALKING_AGENT_PATH:-/opt/skywalking-agent}"

# 验证 Agent 是否存在
if [ ! -f "${SKYWALKING_AGENT_PATH}/skywalking-agent.jar" ]; then
    echo "错误: SkyWalking Agent 未找到，路径: ${SKYWALKING_AGENT_PATH}"
    exit 1
fi

# 打印配置信息
echo "====================================="
echo "启动 Spring Boot 应用（带 SkyWalking Agent）"
echo "====================================="
echo "应用名称: ${SW_AGENT_NAME:-patra-api}"
echo "OAP 地址: ${SW_AGENT_COLLECTOR_BACKEND_SERVICES:-skywalking-oap:11800}"
echo "Agent 路径: ${SKYWALKING_AGENT_PATH}"
echo "====================================="

# 启动应用
exec java \
    -javaagent:${SKYWALKING_AGENT_PATH}/skywalking-agent.jar \
    -Dskywalking.agent.service_name=${SW_AGENT_NAME:-patra-api} \
    -Dskywalking.collector.backend_service=${SW_AGENT_COLLECTOR_BACKEND_SERVICES:-skywalking-oap:11800} \
    ${JAVA_OPTS} \
    -jar /app/application.jar
```

赋予执行权限：

```bash
chmod +x docker/scripts/start-with-skywalking.sh
```

#### 5.4 更新 Docker Compose 配置

**文件**: `docker/docker-compose.dev.yaml`

```yaml
version: '3.8'

services:
  # patra-ingest 服务示例
  patra-ingest:
    build:
      context: ../patra-ingest/patra-ingest-boot
      dockerfile: Dockerfile
    container_name: patra-ingest
    environment:
      # Spring 配置
      SPRING_PROFILES_ACTIVE: dev

      # SkyWalking Agent 配置
      SW_AGENT_NAME: patra-ingest
      SW_AGENT_COLLECTOR_BACKEND_SERVICES: skywalking-oap:11800
      SW_AGENT_SAMPLE: 3
      SKYWALKING_AGENT_PATH: /opt/skywalking-agent

      # 可观测性配置
      ENVIRONMENT: dev
      REGION: local
      CLUSTER_NAME: dev-cluster
    volumes:
      # 挂载 SkyWalking Agent
      - ./skywalking-agent:/opt/skywalking-agent:ro
    command: ["/app/scripts/start-with-skywalking.sh"]
    ports:
      - "6100:6100"
    depends_on:
      - skywalking-oap
      - nacos
```

### 阶段 6：各服务集成

#### 6.1 patra-ingest-boot 集成

**步骤 1**：添加依赖（通过 core starter 间接依赖，无需修改 pom.xml）

**步骤 2**：调整 application.yml

**文件**: `patra-ingest/patra-ingest-boot/src/main/resources/application.yml`

```yaml
spring:
  application:
    name: patra-ingest

# Patra 可观测性配置
patra:
  observability:
    enabled: true
    application-name: ${spring.application.name}
    environment: ${ENVIRONMENT:dev}
    region: ${REGION:local}
    cluster: ${CLUSTER_NAME:dev-cluster}

    metrics:
      common-tags:
        service: ingest
        team: data-ingestion

    tracing:
      sampling-rate: ${TRACING_SAMPLING_RATE:1.0}

# Spring Boot Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  endpoint:
    health:
      show-details: when-authorized
```

**步骤 3**：验证启动

```bash
cd /Users/linqibin/Desktop/Patra-api
mvn clean install -pl patra-ingest/patra-ingest-boot -am
```

#### 6.2 其他服务集成

按照相同方式集成：
- patra-catalog-boot
- patra-registry-boot
- patra-gateway-boot

---

## 配置迁移指南

### 自动化迁移脚本

**文件**: `scripts/migrate-observability-config.sh`

```bash
#!/bin/bash

# 配置迁移脚本：将旧的分散配置迁移到新的统一配置

set -e

PROJECT_ROOT="/Users/linqibin/Desktop/Patra-api"

echo "====================================="
echo "Patra 可观测性配置迁移脚本"
echo "====================================="

# 查找所有 application.yml 文件
find "$PROJECT_ROOT" -name "application*.yml" -type f | while read -r file; do
    echo "处理文件: $file"

    # 备份原文件
    cp "$file" "${file}.backup"

    # 迁移 rest-client 追踪配置
    sed -i '' 's/patra\.rest-client\.interceptors\.tracing/patra.observability.tracing/g' "$file"
    sed -i '' 's/patra\.rest-client\.interceptors\.metrics/patra.observability.metrics/g' "$file"

    # 迁移错误观测配置
    sed -i '' 's/patra\.error\.observation/patra.observability.metrics/g' "$file"

    echo "  ✓ 已迁移配置"
done

echo "====================================="
echo "迁移完成！"
echo "原配置已备份为 *.backup 文件"
echo "请手动验证迁移结果"
echo "====================================="
```

赋予执行权限并运行：

```bash
chmod +x scripts/migrate-observability-config.sh
./scripts/migrate-observability-config.sh
```

### 手动迁移步骤

#### 旧配置示例

```yaml
# 旧配置（分散在各 Starter）
patra:
  rest-client:
    interceptors:
      tracing:
        enabled: true
        header-names:
          - X-Trace-ID
      metrics:
        enabled: true

  error:
    observation:
      enabled: true
```

#### 新配置示例

```yaml
# 新配置（统一管理）
patra:
  observability:
    enabled: true
    application-name: ${spring.application.name}
    environment: ${ENVIRONMENT:dev}

    tracing:
      enabled: true
      header-names:
        - X-Trace-ID

    metrics:
      enabled: true
```

---

## 验证清单

### 功能验证

#### 1. 追踪功能验证

**步骤 1**：启动服务

```bash
cd /Users/linqibin/Desktop/Patra-api/docker
docker-compose -f docker-compose.dev.yaml up -d patra-ingest
```

**步骤 2**：发送测试请求

```bash
curl -H "X-Trace-ID: test-trace-123" http://localhost:6100/api/test
```

**步骤 3**：查看 SkyWalking UI

访问 http://localhost:8088，验证：
- [ ] 服务列表中出现 "patra-ingest"
- [ ] 可以看到追踪链路
- [ ] traceId 正确传播

#### 2. 指标功能验证

**步骤 1**：访问 Prometheus 端点

```bash
curl http://localhost:6100/actuator/prometheus
```

**验证**：
- [ ] 包含 `patra_http_client_requests_success` 指标
- [ ] 包含公共标签 `application="patra-ingest"`
- [ ] 包含环境标签 `environment="dev"`

**步骤 2**：查看 SkyWalking Metrics

访问 SkyWalking UI → Metrics，验证：
- [ ] 可以看到自定义指标
- [ ] 指标数据正常上报

#### 3. 日志功能验证

**步骤 1**：查看应用日志

```bash
docker logs patra-ingest
```

**验证**：
- [ ] 日志中包含 traceId
- [ ] 格式符合配置的 pattern
- [ ] 可以通过 traceId 关联日志

### 性能验证

#### 1. 基准测试

使用 JMeter 或 Gatling 进行压力测试：

```bash
# 启动性能测试
jmeter -n -t performance-test.jmx -l results.jtl
```

**验证指标**：
- [ ] QPS 下降 < 5%
- [ ] P99 延迟增加 < 10%
- [ ] CPU 使用率增加 < 15%
- [ ] 内存使用增加 < 50MB

#### 2. SkyWalking Agent 性能影响

**对比测试**：

| 场景 | 无 Agent | 有 Agent | 影响 |
|-----|---------|---------|------|
| QPS | _______ | _______ | ___% |
| P99 延迟 | _____ms | _____ms | ___% |
| CPU 使用 | _____% | _____% | ___% |
| 内存使用 | _____MB | _____MB | ___MB |

---

## 常见问题

### Q1: SkyWalking UI 看不到服务

**症状**：启动服务后，SkyWalking UI 服务列表为空

**排查步骤**：

1. 检查 Agent 是否启动
   ```bash
   docker exec patra-ingest ps aux | grep skywalking
   ```

2. 检查 OAP 连接
   ```bash
   docker exec patra-ingest telnet skywalking-oap 11800
   ```

3. 检查日志
   ```bash
   docker logs patra-ingest | grep -i skywalking
   ```

**常见原因**：
- Agent 路径配置错误
- OAP 服务器地址不可达
- 网络隔离问题

### Q2: 指标未上报到 Prometheus

**症状**：访问 `/actuator/prometheus` 返回空或错误

**排查步骤**：

1. 检查 Actuator 端点是否启用
   ```bash
   curl http://localhost:6100/actuator
   ```

2. 检查 MeterRegistry 是否创建
   ```bash
   # 查看启动日志
   docker logs patra-ingest | grep "MeterRegistry"
   ```

3. 检查配置
   ```yaml
   management:
     endpoints:
       web:
         exposure:
           include: prometheus
   ```

### Q3: 日志中 traceId 显示为空

**症状**：日志格式正确，但 traceId 为空字符串

**排查步骤**：

1. 检查 SkyWalking Agent 是否启动
2. 检查 Logback 配置是否正确引入 TraceIdConverter
3. 检查请求是否被 Agent 拦截（查看 SkyWalking UI）

**解决方案**：
```xml
<!-- logback-spring.xml -->
<conversionRule conversionWord="tid"
                converterClass="org.apache.skywalking.apm.toolkit.log.logback.v1.x.TraceIdConverter"/>
```

---

## 回滚方案

### 紧急回滚步骤

如果在生产环境中遇到严重问题，可按以下步骤回滚：

#### 1. 禁用 SkyWalking Agent

**临时方案**：修改启动命令，移除 `-javaagent` 参数

**Docker Compose**：
```yaml
# 注释掉 javaagent 相关配置
# command: ["/app/scripts/start-with-skywalking.sh"]
command: ["java", "-jar", "/app/application.jar"]
```

#### 2. 禁用可观测性 Starter

**application.yml**：
```yaml
patra:
  observability:
    enabled: false  # 全局禁用
```

#### 3. 恢复旧配置

```bash
# 恢复备份的配置文件
find /Users/linqibin/Desktop/Patra-api -name "application*.yml.backup" | while read -r backup; do
    original="${backup%.backup}"
    cp "$backup" "$original"
    echo "已恢复: $original"
done
```

#### 4. 回滚代码

```bash
cd /Users/linqibin/Desktop/Patra-api
git revert <commit-hash>  # 回滚到实施前的版本
mvn clean install
```

---

## 下一步

完成实施后，建议：

1. **编写 README 文档**：为 observability starter 编写详细的使用文档
2. **团队培训**：组织培训会，介绍新的可观测性架构
3. **监控告警配置**：在 SkyWalking 中配置关键指标的告警规则
4. **Grafana Dashboard**：创建 Grafana 仪表板用于日常监控
5. **性能优化**：根据实际运行情况调整采样率和配置

---

**祝实施顺利！** 🚀

如有问题，请参考：
- [架构设计文档](./observability-starter-design.md)
- [SkyWalking 官方文档](https://skywalking.apache.org/docs/)
- [Micrometer 官方文档](https://micrometer.io/docs/)
