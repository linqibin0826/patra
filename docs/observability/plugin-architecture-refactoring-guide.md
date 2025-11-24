# Patra 可观测性插件式架构重构指南

> **版本**: 2.0（采用用户优化方案）
> **状态**: 最优架构方案
> **重构类型**: 破坏性重构（绿地项目）
> **最后更新**: 2025-11-23

---

## 📋 目录

- [架构决策](#架构决策)
- [重构范围](#重构范围)
- [删除清单](#删除清单)
- [新增清单](#新增清单)
- [实施步骤](#实施步骤)
- [验证清单](#验证清单)

---

## 架构决策

### 核心原则：**横切关注点完全分离**

**关键决策**: 将可观测性从各 Starter 中**完全移除**，作为**独立的可选插件**，通过扩展点机制实现观测逻辑。

### 架构设计

**插件式架构**：

```
patra-starter-core（纯净）
├─ ErrorResolutionPipeline
└─ ErrorInterceptor 接口（扩展点）

patra-starter-rest-client（纯净）
├─ RestClient
└─ ClientInterceptor 接口（扩展点）

patra-starter-observability（插件）
├─ ErrorPipelineObservationInterceptor（实现扩展点）
├─ RestClientObservationInterceptor（实现扩展点）
├─ BatchObservationJobListener（实现扩展点）
└─ 所有 ObservationHandlers、MeterFilters

架构优势：
✅ 可观测性完全可选
✅ 依赖方向正确（observability → core）
✅ 符合 SRP、DIP、OCP 原则
✅ 易于替换框架（如迁移到 OpenTelemetry）
```

---

## 重构范围

### 涉及模块

| 模块 | 重构类型 | 说明 |
|------|---------|------|
| `patra-starter-core` | **删除** | 移除所有可观测性代码，保留扩展点接口 |
| `patra-starter-rest-client` | **删除** | 移除所有可观测性代码，保留扩展点接口 |
| `patra-starter-batch` | **删除** | 移除所有可观测性代码（TODO 标记） |
| `patra-spring-boot-starter-observability` | **新增** | 统一实现所有扩展点 |

---

## 删除清单

### 1. patra-starter-core

#### 删除文件
```bash
# 删除 TracingInterceptor 相关代码
rm -rf patra-spring-boot-starter-core/src/main/java/com/patra/starter/core/infra/interceptor/TracingInterceptor.java

# 删除 Metrics 相关代码（如果有）
find patra-spring-boot-starter-core -name "*Metrics*.java" -delete
```

#### 删除依赖
```xml
<!-- pom.xml 中删除 Micrometer 依赖 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-observation</artifactId>
</dependency>
<!-- ❌ 删除 -->

<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
</dependency>
<!-- ❌ 删除 -->
```

#### 新增扩展点接口
```java
// patra-spring-boot-starter-core/src/main/java/com/patra/starter/core/interceptor/ErrorInterceptor.java
package com.patra.starter.core.interceptor;

import com.patra.starter.core.error.ErrorContext;
import com.patra.starter.core.error.ErrorResult;

/**
 * 错误处理拦截器扩展点
 * <p>
 * 允许外部模块（如 observability）注入自定义逻辑
 */
public interface ErrorInterceptor {

    /**
     * 错误解析开始前执行
     */
    void beforeResolve(ErrorContext context);

    /**
     * 错误解析完成后执行
     */
    void afterResolve(ErrorContext context, ErrorResult result);

    /**
     * 错误解析过程中发生异常时执行
     */
    void onError(ErrorContext context, Exception e);

    /**
     * 执行优先级（值越小优先级越高）
     */
    default int getOrder() {
        return 0;
    }
}
```

#### 修改 ErrorResolutionPipeline
```java
// patra-spring-boot-starter-core/src/main/java/com/patra/starter/core/error/ErrorResolutionPipeline.java
package com.patra.starter.core.error;

import com.patra.starter.core.interceptor.ErrorInterceptor;
import java.util.List;

public class ErrorResolutionPipeline {

    private final List<ErrorInterceptor> interceptors; // 注入所有拦截器

    public ErrorResolutionPipeline(List<ErrorInterceptor> interceptors) {
        this.interceptors = interceptors != null ? interceptors : List.of();
    }

    public ErrorResult resolve(ErrorContext context) {
        // 1. 执行前置拦截器
        interceptors.forEach(interceptor -> interceptor.beforeResolve(context));

        try {
            // 2. 执行实际的错误解析逻辑
            ErrorResult result = doResolve(context);

            // 3. 执行后置拦截器
            interceptors.forEach(interceptor -> interceptor.afterResolve(context, result));

            return result;
        } catch (Exception e) {
            // 4. 执行异常拦截器
            interceptors.forEach(interceptor -> interceptor.onError(context, e));
            throw e;
        }
    }

    private ErrorResult doResolve(ErrorContext context) {
        // 原有的错误解析逻辑
        // ...
    }
}
```

---

### 2. patra-starter-rest-client

#### 删除文件
```bash
# 删除 TracingInterceptor 相关代码
rm -rf patra-spring-boot-starter-rest-client/src/main/java/com/patra/starter/restclient/infra/interceptor/TracingInterceptor.java

# 删除 Metrics 相关代码
find patra-spring-boot-starter-rest-client -name "*Metrics*.java" -delete
```

#### 删除依赖
```xml
<!-- pom.xml 中删除 Micrometer 依赖 -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-observation</artifactId>
</dependency>
<!-- ❌ 删除 -->
```

#### 新增扩展点接口
```java
// patra-spring-boot-starter-rest-client/src/main/java/com/patra/starter/restclient/interceptor/ClientInterceptor.java
package com.patra.starter.restclient.interceptor;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;

/**
 * HTTP 客户端拦截器扩展点
 * <p>
 * 允许外部模块（如 observability）注入自定义逻辑
 */
public interface ClientInterceptor {

    /**
     * HTTP 请求发送前执行
     */
    void beforeRequest(HttpRequest request);

    /**
     * HTTP 响应接收后执行
     */
    void afterResponse(HttpRequest request, ClientHttpResponse response);

    /**
     * HTTP 请求过程中发生异常时执行
     */
    void onError(HttpRequest request, Exception e);

    /**
     * 执行优先级（值越小优先级越高）
     */
    default int getOrder() {
        return 0;
    }
}
```

#### 修改 RestClient 配置
```java
// patra-spring-boot-starter-rest-client/src/main/java/com/patra/starter/restclient/autoconfigure/RestClientAutoConfiguration.java
package com.patra.starter.restclient.autoconfigure;

import com.patra.starter.restclient.interceptor.ClientInterceptor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@AutoConfiguration
public class RestClientAutoConfiguration {

    @Bean
    public RestTemplate restTemplate(
        RestTemplateBuilder builder,
        List<ClientInterceptor> clientInterceptors  // 自动注入所有拦截器
    ) {
        // 将 ClientInterceptor 转换为 Spring 的 ClientHttpRequestInterceptor
        return builder
            .interceptors((request, body, execution) -> {
                // 前置拦截
                clientInterceptors.forEach(interceptor -> interceptor.beforeRequest(request));

                try {
                    // 执行请求
                    ClientHttpResponse response = execution.execute(request, body);

                    // 后置拦截
                    clientInterceptors.forEach(interceptor -> interceptor.afterResponse(request, response));

                    return response;
                } catch (Exception e) {
                    // 异常拦截
                    clientInterceptors.forEach(interceptor -> interceptor.onError(request, e));
                    throw e;
                }
            })
            .build();
    }
}
```

---

### 3. patra-starter-batch

#### 删除 TODO 标记的代码
```bash
# 查找并删除所有 TODO 标记的 Metrics 相关代码
grep -r "TODO.*metric\|TODO.*observ" patra-spring-boot-starter-batch/src/main/java/
# 手动删除相关代码
```

#### 删除依赖
```xml
<!-- pom.xml 中删除 Micrometer 依赖（如果有） -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
</dependency>
<!-- ❌ 删除 -->
```

**注意**: Batch 模块使用 Spring Batch 原生的 `JobExecutionListener` 扩展点，无需额外定义。

---

## 新增清单

### patra-spring-boot-starter-observability

#### 目录结构
```
patra-spring-boot-starter-observability/
├── pom.xml
├── README.md
├── src/main/java/com/patra/starter/observability/
│   ├── autoconfigure/                                   # 自动配置
│   │   ├── ObservabilityAutoConfiguration.java
│   │   ├── ObservationInterceptorsAutoConfiguration.java  # ⭐ 新增
│   │   └── ...
│   ├── interceptor/                                     # ⭐ 新增目录
│   │   ├── ErrorPipelineObservationInterceptor.java     # 实现 core 扩展点
│   │   ├── RestClientObservationInterceptor.java        # 实现 rest-client 扩展点
│   │   └── BatchObservationJobListener.java             # 实现 batch 扩展点
│   ├── filter/                                          # ObservationFilter
│   │   └── SensitiveDataObservationFilter.java          # 敏感数据脱敏（创建阶段）
│   ├── handler/                                         # ObservationHandler
│   │   ├── LoggingObservationHandler.java
│   │   ├── PerformanceObservationHandler.java
│   │   └── ...
│   └── ...
└── src/test/java/...
```

---

### 核心实现代码

#### 1. ErrorPipelineObservationInterceptor

```java
// patra-spring-boot-starter-observability/src/main/java/com/patra/starter/observability/interceptor/ErrorPipelineObservationInterceptor.java
package com.patra.starter.observability.interceptor;

import com.patra.starter.core.interceptor.ErrorInterceptor;
import com.patra.starter.core.error.ErrorContext;
import com.patra.starter.core.error.ErrorResult;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

/**
 * 错误处理管道可观测性拦截器
 * <p>
 * 实现 patra-starter-core 提供的 ErrorInterceptor 扩展点，
 * 为错误处理流程注入观测逻辑
 *
 * @author Jobs
 * @since 1.0.0
 */
@Component
public class ErrorPipelineObservationInterceptor implements ErrorInterceptor {

    private static final String OBSERVATION_KEY = "error.pipeline.observation";

    private final ObservationRegistry observationRegistry;

    public ErrorPipelineObservationInterceptor(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public void beforeResolve(ErrorContext context) {
        // 创建 Observation
        Observation observation = Observation.createNotStarted(
            "patra.error.resolution",
            observationRegistry
        );

        // 添加低基数标签
        observation.lowCardinalityKeyValue("error.type", context.getErrorType());
        observation.lowCardinalityKeyValue("error.code", context.getErrorCode());

        // 启动观测
        observation.start();

        // 存储到上下文中，供后续使用
        context.setAttribute(OBSERVATION_KEY, observation);
    }

    @Override
    public void afterResolve(ErrorContext context, ErrorResult result) {
        // 从上下文中取出 Observation
        Observation observation = context.getAttribute(OBSERVATION_KEY);
        if (observation != null) {
            observation.lowCardinalityKeyValue("resolution.status", result.getStatus());
            observation.stop();
        }
    }

    @Override
    public void onError(ErrorContext context, Exception e) {
        // 从上下文中取出 Observation
        Observation observation = context.getAttribute(OBSERVATION_KEY);
        if (observation != null) {
            observation.error(e);
            observation.stop();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE; // 最高优先级
    }
}
```

---

#### 2. RestClientObservationInterceptor

```java
// patra-spring-boot-starter-observability/src/main/java/com/patra/starter/observability/interceptor/RestClientObservationInterceptor.java
package com.patra.starter.observability.interceptor;

import com.patra.starter.restclient.interceptor.ClientInterceptor;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.core.Ordered;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * HTTP 客户端可观测性拦截器
 * <p>
 * 实现 patra-starter-rest-client 提供的 ClientInterceptor 扩展点，
 * 为 HTTP 调用注入观测逻辑
 *
 * @author Jobs
 * @since 1.0.0
 */
@Component
public class RestClientObservationInterceptor implements ClientInterceptor {

    private final ObservationRegistry observationRegistry;
    private final Map<HttpRequest, Observation> observations = new ConcurrentHashMap<>();

    public RestClientObservationInterceptor(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public void beforeRequest(HttpRequest request) {
        // 创建 Observation
        Observation observation = Observation.createNotStarted(
            "patra.http.client.request",
            observationRegistry
        );

        // 添加标签
        observation.lowCardinalityKeyValue("http.method", request.getMethod().name());
        observation.lowCardinalityKeyValue("http.url.host", request.getURI().getHost());
        observation.lowCardinalityKeyValue("http.url.path", request.getURI().getPath());

        // 启动观测
        observation.start();

        // 存储（用于后续关联）
        observations.put(request, observation);
    }

    @Override
    public void afterResponse(HttpRequest request, ClientHttpResponse response) {
        Observation observation = observations.remove(request);
        if (observation != null) {
            try {
                observation.lowCardinalityKeyValue("http.status_code", String.valueOf(response.getStatusCode().value()));
                observation.stop();
            } catch (Exception e) {
                observation.error(e);
                observation.stop();
            }
        }
    }

    @Override
    public void onError(HttpRequest request, Exception e) {
        Observation observation = observations.remove(request);
        if (observation != null) {
            observation.error(e);
            observation.stop();
        }
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
```

---

#### 3. BatchObservationJobListener

```java
// patra-spring-boot-starter-observability/src/main/java/com/patra/starter/observability/interceptor/BatchObservationJobListener.java
package com.patra.starter.observability.interceptor;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.stereotype.Component;

/**
 * Spring Batch 任务可观测性监听器
 * <p>
 * 实现 Spring Batch 原生的 JobExecutionListener 扩展点，
 * 为批处理任务注入观测逻辑
 *
 * @author Jobs
 * @since 1.0.0
 */
@Component
public class BatchObservationJobListener implements JobExecutionListener {

    private static final String OBSERVATION_KEY = "batch.job.observation";

    private final ObservationRegistry observationRegistry;

    public BatchObservationJobListener(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public void beforeJob(JobExecution jobExecution) {
        // 创建 Observation
        Observation observation = Observation.createNotStarted(
            "patra.batch.job",
            observationRegistry
        );

        // 添加标签
        observation.lowCardinalityKeyValue("job.name", jobExecution.getJobName());
        observation.lowCardinalityKeyValue("job.id", String.valueOf(jobExecution.getJobId()));

        // 启动观测
        observation.start();

        // 存储到 ExecutionContext
        jobExecution.getExecutionContext().put(OBSERVATION_KEY, observation);
    }

    @Override
    public void afterJob(JobExecution jobExecution) {
        // 从 ExecutionContext 取出 Observation
        Observation observation = (Observation) jobExecution.getExecutionContext().get(OBSERVATION_KEY);

        if (observation != null) {
            observation.lowCardinalityKeyValue("job.status", jobExecution.getStatus().name());
            observation.lowCardinalityKeyValue("job.exit_code", jobExecution.getExitStatus().getExitCode());

            // 记录处理数量
            observation.highCardinalityKeyValue("job.read_count", String.valueOf(jobExecution.getStepExecutions().stream()
                .mapToLong(step -> step.getReadCount())
                .sum()));

            observation.stop();
        }
    }
}
```

---

#### 4. AutoConfiguration

```java
// patra-spring-boot-starter-observability/src/main/java/com/patra/starter/observability/autoconfigure/ObservationInterceptorsAutoConfiguration.java
package com.patra.starter.observability.autoconfigure;

import com.patra.starter.observability.interceptor.ErrorPipelineObservationInterceptor;
import com.patra.starter.observability.interceptor.RestClientObservationInterceptor;
import com.patra.starter.observability.interceptor.BatchObservationJobListener;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * 可观测性拦截器自动配置
 * <p>
 * 根据类路径中是否存在对应的 Starter，自动注册相应的拦截器
 *
 * @author Jobs
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(name = "patra.observability.enabled", havingValue = "true", matchIfMissing = true)
public class ObservationInterceptorsAutoConfiguration {

    /**
     * 错误处理管道观测拦截器
     * <p>
     * 仅在 patra-starter-core 存在时注册
     */
    @Bean
    @ConditionalOnClass(name = "com.patra.starter.core.interceptor.ErrorInterceptor")
    public ErrorPipelineObservationInterceptor errorPipelineObservationInterceptor(
        ObservationRegistry observationRegistry
    ) {
        return new ErrorPipelineObservationInterceptor(observationRegistry);
    }

    /**
     * HTTP 客户端观测拦截器
     * <p>
     * 仅在 patra-starter-rest-client 存在时注册
     */
    @Bean
    @ConditionalOnClass(name = "com.patra.starter.restclient.interceptor.ClientInterceptor")
    public RestClientObservationInterceptor restClientObservationInterceptor(
        ObservationRegistry observationRegistry
    ) {
        return new RestClientObservationInterceptor(observationRegistry);
    }

    /**
     * Batch 任务观测监听器
     * <p>
     * 仅在 patra-starter-batch（Spring Batch）存在时注册
     */
    @Bean
    @ConditionalOnClass(name = "org.springframework.batch.core.JobExecutionListener")
    public BatchObservationJobListener batchObservationJobListener(
        ObservationRegistry observationRegistry
    ) {
        return new BatchObservationJobListener(observationRegistry);
    }
}
```

---

## 实施步骤

> **注**：单人开发项目，无时间压力，优先追求质量和架构卓越。以下步骤仅供参考。

### 阶段 1: 准备工作

```bash
# 1. 创建重构分支
git checkout -b refactor/plugin-architecture-observability

# 2. 备份当前代码（可选）
git tag backup-before-observability-refactor

# 3. 确认所有测试通过（基准）
mvn clean test
```

---

### 阶段 2: 重构 patra-starter-core

#### 步骤 2.1: 删除可观测性代码
```bash
# 删除 TracingInterceptor（如果存在）
find patra-spring-boot-starter-core -name "*Tracing*" -delete
find patra-spring-boot-starter-core -name "*Metrics*" -delete

# 删除 Micrometer 依赖
# 手动编辑 pom.xml
```

#### 步骤 2.2: 新增扩展点接口
```bash
# 创建扩展点接口
mkdir -p patra-spring-boot-starter-core/src/main/java/com/patra/starter/core/interceptor

# 创建 ErrorInterceptor.java（见上文代码）
```

#### 步骤 2.3: 修改 ErrorResolutionPipeline
```bash
# 修改 ErrorResolutionPipeline 支持拦截器注入（见上文代码）
```

#### 步骤 2.4: 验证编译
```bash
cd patra-spring-boot-starter-core
mvn clean compile
```

---

### 阶段 3: 重构 patra-starter-rest-client

重复阶段 2 的步骤，参考上文的删除和新增清单。

---

### 阶段 4: 重构 patra-starter-batch

```bash
# 删除所有 TODO 标记的 Metrics 代码
grep -r "TODO.*metric\|TODO.*observ" patra-spring-boot-starter-batch/src/main/java/
# 手动删除

# 删除 Micrometer 依赖（如果有）
# 手动编辑 pom.xml
```

---

### 阶段 5: 创建 patra-starter-observability

#### 步骤 5.1: 创建模块结构
```bash
# 创建目录
mkdir -p patra-spring-boot-starter-observability/src/main/java/com/patra/starter/observability/interceptor
mkdir -p patra-spring-boot-starter-observability/src/test/java/com/patra/starter/observability/interceptor

# 创建 pom.xml
```

#### 步骤 5.2: 添加依赖
```xml
<!-- patra-spring-boot-starter-observability/pom.xml -->
<dependencies>
    <!-- 依赖 core（编译期）-->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-core</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- 依赖 rest-client（编译期）-->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-rest-client</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- 依赖 batch（编译期）-->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-batch</artifactId>
        <optional>true</optional>
    </dependency>

    <!-- Micrometer -->
    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-observation</artifactId>
    </dependency>

    <dependency>
        <groupId>io.micrometer</groupId>
        <artifactId>micrometer-core</artifactId>
    </dependency>

    <!-- ... 其他依赖 -->
</dependencies>
```

#### 步骤 5.3: 实现拦截器
```bash
# 创建 ErrorPipelineObservationInterceptor.java（见上文）
# 创建 RestClientObservationInterceptor.java（见上文）
# 创建 BatchObservationJobListener.java（见上文）
```

#### 步骤 5.4: 配置自动装配
```bash
# 创建 META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
echo "com.patra.starter.observability.autoconfigure.ObservationInterceptorsAutoConfiguration" >> \
  patra-spring-boot-starter-observability/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
```

---

### 阶段 6: 集成测试

#### 步骤 6.1: 更新业务服务依赖
```xml
<!-- patra-catalog-boot/pom.xml -->
<dependencies>
    <!-- 保留原有依赖 -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-core</artifactId>
    </dependency>

    <!-- 新增 observability 依赖 -->
    <dependency>
        <groupId>com.patra</groupId>
        <artifactId>patra-spring-boot-starter-observability</artifactId>
    </dependency>
</dependencies>
```

#### 步骤 6.2: 验证功能
```bash
# 启动应用
cd patra-catalog-boot
mvn spring-boot:run

# 验证可观测性是否生效
# 1. 查看日志，确认拦截器已注册
# 2. 触发 HTTP 请求，查看 Observation 是否记录
# 3. 访问 /actuator/metrics，确认指标是否存在
```

#### 步骤 6.3: 测试可选性
```bash
# 临时移除 observability 依赖
# 确认应用仍能正常启动（可观测性被禁用）
```

---

### 阶段 7: 文档更新

```bash
# 更新各模块 README.md
# - patra-starter-core: 说明扩展点机制
# - patra-starter-rest-client: 说明扩展点机制
# - patra-starter-observability: 说明如何使用
```

---

## 验证清单

### ✅ 功能验证

- [ ] patra-starter-core 编译通过，无 Micrometer 依赖
- [ ] patra-starter-rest-client 编译通过，无 Micrometer 依赖
- [ ] patra-starter-batch 编译通过，无 Micrometer 依赖
- [ ] patra-starter-observability 编译通过
- [ ] 业务服务引入 observability 后，可观测性功能正常
- [ ] 业务服务移除 observability 后，应用仍能正常启动

### ✅ 架构验证

- [ ] **依赖方向正确**: observability → core（单向）
- [ ] **依赖方向正确**: observability → rest-client（单向）
- [ ] **依赖方向正确**: observability → batch（单向）
- [ ] **无反向依赖**: core ❌→ observability
- [ ] **无反向依赖**: rest-client ❌→ observability
- [ ] **无反向依赖**: batch ❌→ observability

### ✅ 可观测性验证

- [ ] ErrorResolutionPipeline 的追踪正常
- [ ] RestClient HTTP 调用的追踪正常
- [ ] Batch 任务的追踪正常
- [ ] SkyWalking UI 能看到完整的追踪链路
- [ ] Prometheus 能收集到所有指标

### ✅ 性能验证

- [ ] CPU 开销 < 10%（相对基准）
- [ ] 内存开销 < 50MB（相对基准）
- [ ] TPS 下降 < 5%

---

## 回滚方案

如果重构遇到问题，可以执行以下回滚：

```bash
# 方案 1: 回退到备份标签
git reset --hard backup-before-observability-refactor

# 方案 2: 撤销重构分支
git checkout main
git branch -D refactor/plugin-architecture-observability
```

---

## 总结

### 关键成果

1. ✅ **完全解耦**: core/rest-client/batch 不再依赖 Micrometer
2. ✅ **可选插件**: 可观测性成为可选功能，不引入 observability starter 时完全禁用
3. ✅ **依赖倒置**: observability → core（单向依赖），符合 DIP 原则
4. ✅ **易于替换**: 未来迁移到 OpenTelemetry 只需修改 observability starter

### 架构优势

- **符合 SOLID 原则**: SRP（单一职责）、OCP（开闭）、DIP（依赖倒置）
- **符合六边形架构**: 基础设施作为可插拔的适配器
- **符合 DDD**: 横切关注点不污染领域层
- **绿地项目最佳实践**: 充分利用无历史包袱的优势

---

**🎉 恭喜！你已经完成了 Patra 可观测性的插件式架构重构！**
