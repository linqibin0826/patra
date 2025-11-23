# Patra 统一可观测性 Starter 架构评审总结

> **评审日期**: 2025-11-23
> **评审类型**: 三维度并行评审（六边形架构 + DDD + 技术选型）
> **项目背景**: 绿地项目，允许破坏性重构，追求生产级别的完整方案

---

## 📊 评审结果总览

| 评审维度 | 评分 | 评审结论 | 关键问题数量 |
|---------|------|---------|------------|
| 六边形架构合规性 | **7.8/10** | 🟡 有条件通过 | 1 个 Critical |
| DDD & 领域建模 | **5.4/10** | 🟡 有条件通过 | 3 个 P0 |
| 技术选型与生产级别 | **7.3/10** | 🟡 有条件通过 | 4 个 P0 |

**综合结论**: 🟡 **有条件批准** - 必须完成所有 P0 级别改进后才可进入生产环境

---

## 🚨 关键问题汇总（P0 - 必须修复）

### 问题 1: "合并重复拦截器"策略存在致命缺陷（已升级为更优方案）

**来源**: 六边形架构评审 + 用户架构优化
**严重性**: ❌ CRITICAL → ✅ **已优化为最佳方案**
**问题描述**:

原始设计提议将 `patra-starter-core` 和 `patra-starter-rest-client` 的 `TracingInterceptor` 合并，后修正为"保留在各自模块"。但用户提出了更优的架构方案：**完全移除各 Starter 中的可观测性代码，采用插件式架构**。

**为什么原方案（即使修正后）仍有问题**:
```
修正后的方案（仍有缺陷）:
patra-starter-core:
├─ ErrorResolutionPipeline（核心功能）
└─ ErrorPipelineTracingInterceptor（可观测性）⚠️ 职责混淆

patra-starter-rest-client:
├─ RestClient（核心功能）
└─ RestClientTracingInterceptor（可观测性）⚠️ 职责混淆

问题：
1. 强制依赖：所有使用 core 的模块都被迫引入 Micrometer
2. 职责混淆：core 应该是"通用基础设施"，而非"可观测性+基础设施"
3. 无法可选：可观测性成了强制功能，而非可选插件
4. 违反关注点分离原则（Separation of Concerns）
```

**✅ 最优方案（用户提出，已采纳）**:
```
插件式架构（完全解耦）:

patra-starter-core（纯净）:
├─ ErrorResolutionPipeline（核心功能）
└─ ErrorInterceptor 接口（扩展点）✅ 开放扩展，封闭修改

patra-starter-rest-client（纯净）:
├─ RestClient（核心功能）
└─ ClientInterceptor 接口（扩展点）✅ 开放扩展，封闭修改

patra-starter-batch（纯净）:
├─ Batch 配置（核心功能）
└─ JobExecutionListener 接口（Spring Batch 原生扩展点）

patra-spring-boot-starter-observability（插件）:
├─ ErrorPipelineObservationInterceptor（实现 ErrorInterceptor）✅
├─ RestClientObservationInterceptor（实现 ClientInterceptor）✅
├─ BatchObservationJobListener（实现 JobExecutionListener）✅
└─ 所有 ObservationHandlers、MeterFilters

依赖方向验证：
- ✅ observability → core（单向依赖，符合 DIP）
- ✅ observability → rest-client（单向依赖，符合 DIP）
- ✅ observability → batch（单向依赖，符合 DIP）
- ✅ core ❌→ observability（无依赖，正确！）
```

**架构优势对比**:

| 维度 | 原方案（修正后） | 最优方案（用户方案）|
|------|----------------|-------------------|
| **关注点分离** | ❌ 混合 | ✅ 完全分离 |
| **依赖方向** | core ⇄ Micrometer（耦合）| observability → core（单向）|
| **可选性** | ❌ 强制启用 | ✅ 可选插件 |
| **遵循原则** | 违反 SRP、DIP | ✅ 符合 SRP、DIP、OCP |
| **未来扩展** | 难以替换框架 | ✅ 易于替换（如迁移到 OpenTelemetry）|
| **测试隔离** | 需要 Mock Micrometer | ✅ 可完全禁用可观测性 |

**影响范围**:
- ✅ 设计文档第 6 章已更新为"完全移除可观测性代码（插件式架构）"
- ⏳ 实施指南需要更新为"从各 Starter 删除可观测性代码，在 observability 中统一实现"
- ⏳ 需要在 core/rest-client/batch 中定义清晰的扩展点接口

---

### 问题 2: 缺少防腐层（Anti-Corruption Layer）

**来源**: DDD 评审
**严重性**: 🔴 P0
**问题描述**:

业务代码直接依赖 Micrometer 框架 API（`ObservationRegistry`、`lowCardinalityKeyValue`），违反 DDD 的依赖倒置原则。

**当前设计中的错误示例**:
```java
// ❌ Domain 层直接依赖 Micrometer（技术框架）
@Service
public class DataImportService {
    private final ObservationRegistry observationRegistry; // 框架依赖

    public void importData(DataSource source) {
        Observation.createNotStarted("data.import", observationRegistry)
            .lowCardinalityKeyValue("source.type", source.getType()) // 技术术语
            .observe(() -> doImport(source));
    }
}
```

**必须执行的修正**:

**步骤 1: 在 Domain 层定义端口接口（Port）**
```java
// patra-{service}-domain/src/main/java/com/patra/{service}/domain/port/ObservabilityPort.java
package com.patra.{service}.domain.port;

import java.util.Map;
import java.util.function.Supplier;

/**
 * 可观测性端口 - 领域层接口，隔离框架依赖
 */
public interface ObservabilityPort {

    /**
     * 追踪操作执行
     *
     * @param context 操作上下文（业务语言）
     * @param action 要执行的操作
     * @return 操作结果
     */
    <T> T trackOperation(OperationContext context, Supplier<T> action);

    /**
     * 记录业务指标
     *
     * @param snapshot 指标快照（业务语言）
     */
    void recordMetric(MetricSnapshot snapshot);
}

/**
 * 操作上下文 - 业务概念
 */
public record OperationContext(
    String operationName,        // 操作名称（业务术语）
    Map<String, String> labels   // 业务标签
) {}

/**
 * 指标快照 - 业务概念
 */
public record MetricSnapshot(
    String metricName,
    double value,
    Map<String, String> tags
) {}
```

**步骤 2: 在 Infrastructure 层实现适配器（Adapter）**
```java
// patra-{service}-infra/src/main/java/com/patra/{service}/infra/observability/MicrometerObservabilityAdapter.java
package com.patra.{service}.infra.observability;

import com.patra.{service}.domain.port.ObservabilityPort;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.stereotype.Component;

@Component
public class MicrometerObservabilityAdapter implements ObservabilityPort {

    private final ObservationRegistry observationRegistry;

    public MicrometerObservabilityAdapter(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public <T> T trackOperation(OperationContext context, Supplier<T> action) {
        Observation observation = Observation.createNotStarted(
            context.operationName(),
            observationRegistry
        );

        // 将业务标签转换为技术标签
        context.labels().forEach(observation::lowCardinalityKeyValue);

        return observation.observe(action);
    }

    @Override
    public void recordMetric(MetricSnapshot snapshot) {
        // 使用 MeterRegistry 记录指标
        // 实现略
    }
}
```

**步骤 3: 业务代码使用端口接口**
```java
// ✅ 正确做法 - Domain 层只依赖自己的端口接口
@Service
public class DataImportService {
    private final ObservabilityPort observabilityPort; // 领域端口

    public void importData(DataSource source) {
        observabilityPort.trackOperation(
            new OperationContext(
                "data.import",
                Map.of("sourceType", source.getType()) // 业务术语
            ),
            () -> doImport(source)
        );
    }
}
```

**影响范围**:
- 设计文档第 4.3 节（API 设计）需要新增"防腐层设计"章节
- 实施指南第 2.2 节需要新增"实现防腐层适配器"步骤
- 快速开始指南的代码示例需要更新为使用 `ObservabilityPort`

---

### 问题 3: 缺少 Domain 层依赖保护机制

**来源**: 六边形架构评审
**严重性**: 🔴 P0
**问题描述**:

虽然设计文档中强调"Domain 层不依赖框架"，但缺少自动化检测机制。绿地项目应该从第一天就建立铁律。

**必须执行的修正**:

**在 pom.xml 中添加 ArchUnit 测试**:
```xml
<!-- patra-{service}-domain/pom.xml -->
<dependency>
    <groupId>com.tngtech.archunit</groupId>
    <artifactId>archunit-junit5</artifactId>
    <version>1.2.1</version>
    <scope>test</scope>
</dependency>
```

**创建架构规则测试**:
```java
// patra-{service}-domain/src/test/java/com/patra/{service}/domain/HexagonalArchitectureTest.java
package com.patra.{service}.domain;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

class HexagonalArchitectureTest {

    private final JavaClasses classes = new ClassFileImporter()
        .importPackages("com.patra");

    @Test
    void domain_should_not_depend_on_infrastructure() {
        ArchRule rule = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat()
            .resideInAnyPackage(
                "..infra..",
                "..adapter..",
                "org.springframework..",
                "io.micrometer..",
                "org.apache.skywalking.."
            );

        rule.check(classes);
    }

    @Test
    void hexagonal_architecture_should_be_respected() {
        layeredArchitecture()
            .consideringAllDependencies()
            .layer("Domain").definedBy("..domain..")
            .layer("Application").definedBy("..app..")
            .layer("Infrastructure").definedBy("..infra..")
            .layer("Adapter").definedBy("..adapter..")

            .whereLayer("Domain").mayOnlyBeAccessedByLayers("Application", "Infrastructure", "Adapter")
            .whereLayer("Application").mayOnlyBeAccessedByLayers("Infrastructure", "Adapter")
            .whereLayer("Infrastructure").mayNotBeAccessedByAnyLayer()
            .whereLayer("Adapter").mayNotBeAccessedByAnyLayer()

            .check(classes);
    }
}
```

**影响范围**:
- 设计文档第 7.2 节（测试策略）需要新增"架构测试"章节
- 实施指南阶段 1 需要包含"添加 ArchUnit 测试"步骤

---

### 问题 4: 缺少实际 PoC 性能测试

**来源**: 技术选型评审
**严重性**: 🔴 P0
**问题描述**:

设计文档中的性能数据全部引用自 SkyWalking 官方 Benchmark（CPU < 5%, 内存 < 20MB），但未在 Patra 项目环境中进行实际验证。

**必须执行的修正**:

**PoC 测试范围**:
```
测试服务: patra-ingest（最繁重的批处理服务）
测试场景:
├─ 基准场景（不启用 SkyWalking Agent）
├─ 启用 SkyWalking Agent（默认配置）
└─ 启用完整可观测性（SkyWalking + Prometheus + 自定义 Handler）

测试指标:
├─ CPU 使用率（平均值、峰值）
├─ 内存占用（堆内存、非堆内存）
├─ GC 频率和停顿时间
├─ 吞吐量（TPS 下降百分比）
└─ 响应时间（P50、P95、P99）

测试工具:
├─ JMH（微基准测试）
├─ JMeter（负载测试）
└─ Arthas（运行时监控）
```

**PoC 实施步骤**:
1. 在 `patra-ingest` 创建 PoC 分支
2. 编写 JMH 基准测试（模拟批量数据处理）
3. 使用 JMeter 进行负载测试（1000 TPS 持续 10 分钟）
4. 使用 Arthas 监控运行时指标
5. 对比三种场景的性能数据
6. 记录结果到设计文档附录

**批准条件**:
- CPU 开销 < 10%（相对基准场景）
- 内存开销 < 50MB（相对基准场景）
- TPS 下降 < 5%
- P99 响应时间增加 < 10%

**影响范围**:
- 设计文档第 8 节（性能评估）需要更新为实际 PoC 数据
- 实施指南需要在阶段 3 之前插入"PoC 性能测试"步骤

---

### 问题 5: 敏感数据泄露风险

**来源**: 技术选型评审
**严重性**: 🔴 P0
**问题描述**:

设计中启用了 SQL 参数收集和 HTTP Body 收集，可能导致敏感数据（密码、身份证号、API Key）泄露到追踪系统。

**必须执行的修正**:

**步骤 1: 创建敏感数据过滤器**
```java
// patra-spring-boot-starter-observability/src/main/java/com/patra/starter/observability/filter/SensitiveDataMaskingHandler.java
package com.patra.starter.observability.filter;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class SensitiveDataMaskingHandler implements ObservationHandler<Observation.Context> {

    private static final Pattern[] SENSITIVE_PATTERNS = {
        Pattern.compile("(?i)password=([^&\\s]+)"),       // 密码
        Pattern.compile("(?i)token=([^&\\s]+)"),          // Token
        Pattern.compile("(?i)api[_-]?key=([^&\\s]+)"),    // API Key
        Pattern.compile("\\d{15,19}"),                    // 身份证号
        Pattern.compile("\\d{3}-?\\d{4}-?\\d{4}")         // 手机号
    };

    @Override
    public void onStart(Observation.Context context) {
        // 检查 KeyValues 中是否包含敏感数据
        context.getLowCardinalityKeyValues().forEach(keyValue -> {
            String maskedValue = maskSensitiveData(keyValue.getValue());
            if (!maskedValue.equals(keyValue.getValue())) {
                // 替换为脱敏后的值
                context.addLowCardinalityKeyValue(keyValue.getKey(), maskedValue);
            }
        });
    }

    private String maskSensitiveData(String value) {
        if (value == null) return null;

        String result = value;
        for (Pattern pattern : SENSITIVE_PATTERNS) {
            result = pattern.matcher(result).replaceAll("***MASKED***");
        }
        return result;
    }

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }
}
```

**步骤 2: SkyWalking Agent 配置加固**
```properties
# agent/config/agent.config

# 禁用 SQL 参数收集（生产环境）
plugin.mysql.trace_sql_parameters=${SW_MYSQL_TRACE_SQL_PARAMETERS:false}

# 禁用 HTTP Body 收集（生产环境）
plugin.http.collect_http_params=${SW_HTTP_COLLECT_PARAMS:false}

# 仅在开发环境启用
# export SW_MYSQL_TRACE_SQL_PARAMETERS=true  # 仅 dev 环境
```

**步骤 3: 添加配置属性**
```yaml
# application.yml
patra:
  observability:
    security:
      mask-sensitive-data: true                    # 启用敏感数据脱敏
      sensitive-patterns:                          # 自定义敏感数据模式
        - "(?i)password=([^&\\s]+)"
        - "(?i)token=([^&\\s]+)"
      sql-parameter-collection:
        enabled: false                             # 生产环境禁用
        allowed-in-environments: [ "dev", "test" ] # 仅在特定环境启用
```

**影响范围**:
- 设计文档第 4.2 节（ObservationHandler）需要新增"敏感数据脱敏 Handler"
- 配置示例文档需要新增"安全配置"章节
- 实施指南需要新增"安全加固"步骤

---

### 问题 6: Actuator 端点暴露风险

**来源**: 技术选型评审
**严重性**: 🔴 P0
**问题描述**:

设计中暴露了 `/actuator/metrics`、`/actuator/health` 等端点，但未配置访问控制，可能泄露系统内部信息。

**必须执行的修正**:

```yaml
# application-prod.yml（生产环境配置）
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus  # 仅暴露必要端点
        exclude: "*"                     # 默认禁用所有
      base-path: /actuator
  endpoint:
    health:
      show-details: when-authorized      # 仅授权用户可见详情
    metrics:
      enabled: true
  security:
    enabled: true                        # 启用安全控制

# Spring Security 配置
spring:
  security:
    user:
      name: ${ACTUATOR_USERNAME:admin}
      password: ${ACTUATOR_PASSWORD}     # 必须通过环境变量注入，不可硬编码
```

**Spring Security 配置类**:
```java
@Configuration
@ConditionalOnProperty(name = "management.security.enabled", havingValue = "true")
public class ActuatorSecurityConfiguration {

    @Bean
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator/**")
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()  // 健康检查公开
                .requestMatchers("/actuator/info").permitAll()    // 信息端点公开
                .anyRequest().authenticated()                     // 其他端点需认证
            )
            .httpBasic(Customizer.withDefaults());                // 使用 HTTP Basic 认证

        return http.build();
    }
}
```

**影响范围**:
- 配置示例文档需要新增"Actuator 安全配置"章节
- 实施指南需要在阶段 4（服务集成）新增"配置 Actuator 安全"步骤

---

## 📋 完整改进行动计划

### P0 级别（必须完成，阻塞生产部署）

| 编号 | 问题 | 责任人 | 预计工时 | 依赖 |
|-----|------|-------|---------|-----|
| P0-1 | 修正"合并拦截器"设计缺陷 | 架构师 | 2 小时 | 无 |
| P0-2 | 设计并实现防腐层（ObservabilityPort） | 架构师 + 后端开发 | 1 天 | P0-1 |
| P0-3 | 添加 ArchUnit 架构测试 | 后端开发 | 4 小时 | P0-2 |
| P0-4 | 在 patra-ingest 执行 PoC 性能测试 | 后端开发 + DevOps | 2 天 | P0-2 |
| P0-5 | 实现敏感数据脱敏机制 | 后端开发 | 1 天 | 无 |
| P0-6 | 配置 Actuator 访问控制 | 后端开发 | 4 小时 | 无 |

**总计工时**: 约 **5 个工作日**

**执行顺序**:
```
第 1 天:
├─ 上午: P0-1 修正设计文档（2 小时）
├─ 下午: P0-5 实现敏感数据脱敏（4 小时）
└─ 晚上: P0-6 配置 Actuator 安全（2 小时）

第 2 天:
└─ 全天: P0-2 设计并实现防腐层（8 小时）

第 3 天:
├─ 上午: P0-3 添加 ArchUnit 测试（4 小时）
└─ 下午: 开始 P0-4 PoC 测试准备（4 小时）

第 4-5 天:
└─ 全天: P0-4 执行 PoC 性能测试（16 小时）
```

---

### P1 级别（强烈推荐，提升方案质量）

| 编号 | 问题 | 预计工时 |
|-----|------|---------|
| P1-1 | 提供业务友好的注解（@Trackable） | 4 小时 |
| P1-2 | 集成 DomainEvent 系统 | 1 天 |
| P1-3 | OpenTelemetry 对比和迁移路径设计 | 1 天 |
| P1-4 | 提供完整的 Prometheus 告警规则 | 1 天 |
| P1-5 | 实现 HighCardinalityMeterFilter | 4 小时 |

**总计工时**: 约 **3.5 个工作日**

---

### P2 级别（可选改进，不阻塞实施）

| 编号 | 问题 | 预计工时 |
|-----|------|---------|
| P2-1 | 值对象设计（配置属性） | 4 小时 |
| P2-2 | 术语表（技术 vs 业务） | 2 小时 |
| P2-3 | 模块级配置覆盖 | 1 天 |
| P2-4 | Kubernetes 部署示例 | 1 天 |

**总计工时**: 约 **2.5 个工作日**

---

## 🎯 更新后的实施时间线

```
原计划: 10-12 天
P0 改进: +5 天
P1 改进: +3.5 天（可选）
-----------------------------------
总计: 15-17 天（包含 P0 + P1）
     或 10-12 天（仅 P0，最低标准）
```

**关键里程碑**:
- **Day 1-5**: 完成所有 P0 改进
- **Day 6**: PoC 性能测试结果评审（Go/No-Go 决策点）
- **Day 7-15**: 实施 Observability Starter（原计划阶段 1-5）
- **Day 16-17**: 集成 P1 改进（可选）

---

## 📝 文档更新清单

以下文档需要根据评审结果进行更新:

### 1. observability-starter-design.md

**需要修改的章节**:
- ✏️ **第 6 章（破坏性重构清单）**:
  - 移除: "合并 core 和 rest-client 的 TracingInterceptor"
  - 新增: "重构拦截器以使用统一的 ObservationRegistry"

- ✏️ **第 4.2 节（ObservationHandler 设计）**:
  - 新增: `SensitiveDataMaskingHandler`（P0-5）

- ✏️ **第 4.3 节（API 设计）**:
  - 新增: "防腐层设计"章节（P0-2）

- ✏️ **第 7.2 节（测试策略）**:
  - 新增: "架构测试（ArchUnit）"章节（P0-3）

- ✏️ **第 8 节（性能评估）**:
  - 替换: 官方 Benchmark 数据 → Patra PoC 实际数据（P0-4）

- ✏️ **第 9 节（风险评估）**:
  - 新增: "敏感数据泄露"风险及缓解措施（P0-5）
  - 新增: "Actuator 暴露"风险及缓解措施（P0-6）

### 2. observability-implementation-guide.md

**需要修改的章节**:
- ✏️ **阶段 1（创建 Observability Starter）**:
  - 新增步骤: "实现 SensitiveDataMaskingHandler"

- ✏️ **阶段 2（重构现有 Starter）**:
  - 修改: "合并拦截器" → "重构拦截器使用 ObservationRegistry"
  - 新增步骤: "实现防腐层适配器（MicrometerObservabilityAdapter）"
  - 新增步骤: "添加 ArchUnit 架构测试"

- ✏️ **新增阶段 2.5（PoC 性能测试）**:
  - 在阶段 3（SkyWalking Agent 配置）之前插入
  - 包含 JMH、JMeter、Arthas 测试步骤

- ✏️ **阶段 4（服务集成）**:
  - 新增步骤: "配置 Actuator 安全控制"
  - 新增步骤: "配置 SkyWalking Agent 安全选项"

### 3. observability-config-examples.yaml

**需要新增的章节**:
- ✏️ **安全配置章节**:
  - 敏感数据脱敏配置
  - Actuator 访问控制配置
  - SkyWalking Agent 安全配置

- ✏️ **生产环境配置**:
  - 禁用 SQL 参数收集
  - 禁用 HTTP Body 收集
  - 强制 HTTPS

### 4. observability-quick-start.md

**需要修改的章节**:
- ✏️ **代码示例**:
  - 更新为使用 `ObservabilityPort` 接口（而不是直接使用 `ObservationRegistry`）

---

## ✅ 批准条件（Production Readiness Checklist）

在进入生产环境之前，必须满足以下所有条件:

### 必要条件（Must Have）

- [ ] **P0-1**: 设计文档已修正"合并拦截器"策略
- [ ] **P0-2**: 所有服务的 Domain 层已实现 `ObservabilityPort` 防腐层
- [ ] **P0-3**: 所有服务已添加 ArchUnit 架构测试，且测试通过
- [ ] **P0-4**: patra-ingest PoC 性能测试完成，且满足性能目标:
  - CPU 开销 < 10%
  - 内存开销 < 50MB
  - TPS 下降 < 5%
  - P99 延迟增加 < 10%
- [ ] **P0-5**: `SensitiveDataMaskingHandler` 已实现并验证
- [ ] **P0-6**: Actuator 端点已配置访问控制，并通过安全扫描

### 强烈推荐（Should Have）

- [ ] **P1-1**: 提供业务友好的注解（如 `@Trackable`）
- [ ] **P1-2**: 与 DomainEvent 系统集成（如 `SlowOperationDetectedEvent`）
- [ ] **P1-3**: 完成 OpenTelemetry 对比分析，并记录迁移路径
- [ ] **P1-4**: 提供完整的 Prometheus 告警规则和 Grafana Dashboard
- [ ] **P1-5**: 实现 `HighCardinalityMeterFilter` 防止标签爆炸

---

## 📞 后续行动

### 立即行动（今天）

1. ✅ **确认评审结果**: 与技术负责人讨论三个评审报告
2. ✅ **批准 P0 改进计划**: 确认 5 天改进工时可接受
3. ✅ **分配资源**: 指定负责人执行 P0 改进

### 本周行动

1. 📝 **更新设计文档**: 根据评审结果修订所有文档（P0-1）
2. 🛡️ **实施安全加固**: 完成 P0-5 和 P0-6（预计 1 天）
3. 🏗️ **设计防腐层**: 完成 `ObservabilityPort` 接口设计（P0-2）

### 下周行动

1. ⚡ **PoC 性能测试**: 在 patra-ingest 执行完整性能测试（P0-4）
2. 🚦 **Go/No-Go 决策**: 根据 PoC 结果决定是否继续实施
3. 🚀 **启动实施**: 如果 PoC 通过，启动阶段 1-5 实施

---

## 🎓 经验教训

本次评审暴露的关键问题:

1. **"合并拦截器"错误**: 即使在绿地项目中，也要区分"技术相似"和"职责相同"。两个都叫 TracingInterceptor 的类，如果拦截点和触发时机不同，就不应该合并。

2. **防腐层不可缺少**: DDD 的防腐层不是"锦上添花"，而是"必需品"。直接依赖框架 API 会导致业务代码与技术细节紧耦合，未来迁移成本巨大。

3. **PoC 测试不可省略**: 官方 Benchmark 只能作为参考，不能替代实际环境测试。不同的业务场景、数据量、并发量会导致完全不同的性能表现。

4. **安全性从第一天考虑**: 可观测性系统会收集大量敏感数据，必须从设计阶段就考虑脱敏、访问控制、加密传输。

---

**📌 下一步**: 请技术负责人确认 P0 改进计划，批准后立即启动文档更新和实施工作。
