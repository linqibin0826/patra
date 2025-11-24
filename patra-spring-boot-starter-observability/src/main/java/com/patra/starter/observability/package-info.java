///
/// 可观测性 Starter
///
/// ## 职责
///
/// 统一集成 **Metrics（指标）**、**Tracing（追踪）**、**Logging（日志）** 三大支柱，提供生产级的、
/// 一步到位的可观测性解决方案。
///
/// 本 Starter 基于 **Micrometer Observation API**，采用**插件式架构**设计，通过实现现有 Starter 的扩展点接口
/// （如 `ResolutionInterceptor`、`ClientInterceptor`、`JobExecutionListener`）来集成可观测性功能，
/// 完全解耦，符合依赖倒置原则（DIP）。
///
/// **技术选型**：
/// - **Micrometer Observation API**：Spring Boot 3.x 官方推荐，统一抽象
/// - **SkyWalking Agent**：APM 完整性、性能优秀、中文友好
/// - **Prometheus**：备份方案、生态丰富
///
/// ## 核心功能
///
/// ### 三大支柱
///
/// - **Metrics（指标）**：
///   - 集成 Micrometer MeterRegistry
///   - 支持 Prometheus 导出（可选）
///   - 支持 SkyWalking Meter（可选）
///   - 自动收集业务指标、错误计数、性能指标
///
/// - **Tracing（追踪）**：
///   - 集成 SkyWalking APM Toolkit
///   - 自动传播 TraceID 到下游服务
///   - 支持分布式追踪链路
///   - 日志中自动显示 TraceID
///
/// - **Logging（日志）**：
///   - 集成 Logback SkyWalking 插件
///   - 自动记录操作日志
///   - 性能监控和慢操作检测
///
/// ### 插件式架构
///
/// 采用插件式架构，通过实现现有扩展点接口来集成可观测性：
///
/// | 扩展点接口 | 实现类 | 功能 | 所属 Starter |
/// |-----------|--------|------|------------|
/// | `ResolutionInterceptor` | `ObservationResolutionInterceptor` | 错误解析管道的可观测性 | patra-starter-core |
/// | `ClientInterceptor` | `RestClientObservationInterceptor` | REST 客户端的可观测性 | patra-starter-rest-client |
/// | `JobExecutionListener` | `BatchObservationJobListener` | Batch 任务的可观测性 | patra-starter-batch |
///
/// **依赖方向**：
/// - ✅ `observability` → `core`（单向依赖，符合 DIP）
/// - ✅ `core` ❌→ `observability`（无依赖，正确）
/// - ✅ 业务服务可随时移除 `observability` 依赖，应用仍能正常启动
///
/// ### 安全特性（P0 级别）
///
/// - **敏感数据脱敏**：
///   - 自动检测并脱敏密码、Token、API Key
///   - 自动检测并脱敏身份证号、手机号、邮箱
///   - 支持自定义敏感数据模式
///   - 生产环境强制启用
///
/// - **Actuator 访问控制**：
///   - 生产环境仅暴露必要端点
///   - HTTP Basic 认证保护
///   - 健康检查端点公开，其他端点需认证
///
/// ## 核心组件
///
/// - `ObservabilityAutoConfiguration`：主配置类，自动配置 Observation Handlers 和 Filters
/// - `MicrometerAutoConfiguration`：Micrometer 配置类，配置 ObservationRegistry 和 MeterRegistry
/// - `PrometheusAutoConfiguration`：Prometheus 配置类（可选）
/// - `SkyWalkingMeterAutoConfiguration`：SkyWalking Meter 配置类（可选）
/// - `ObservationInterceptorsAutoConfiguration`：可观测性拦截器配置类
/// - `ObservabilityProperties`：配置属性类（patra.observability.*）
///
/// ## 使用示例
///
/// ### 1. 添加依赖
///
/// ```xml
/// <dependency>
///     <groupId>com.patra</groupId>
///     <artifactId>patra-spring-boot-starter-observability</artifactId>
/// </dependency>
/// ```
///
/// ### 2. 配置（可选）
///
/// ```yaml
/// patra:
///   observability:
///     enabled: true  # 默认启用
///
///     # Metrics 配置
///     metrics:
///       enabled: true
///       export:
///         prometheus: true  # 启用 Prometheus 导出
///         skywalking: true  # 启用 SkyWalking Meter
///
///     # Tracing 配置
///     tracing:
///       enabled: true
///       sampling-rate: 1.0  # 采样率（0.0-1.0）
///
///     # Logging 配置
///     logging:
///       enabled: true
///       performance-threshold-ms: 1000  # 慢操作阈值（毫秒）
///
///     # 安全配置（P0）
///     security:
///       sensitive-data-masking:
///         enabled: true  # 生产环境强制启用
///         patterns:
///           - "password"
///           - "token"
///           - "apiKey"
/// ```
///
/// ### 3. 启动 SkyWalking Agent
///
/// ```bash
/// java -javaagent:/path/to/skywalking-agent.jar \
///      -Dskywalking.agent.service_name=patra-ingest \
///      -Dskywalking.collector.backend_service=localhost:11800 \
///      -jar patra-ingest-boot.jar
/// ```
///
/// ### 4. 访问 Actuator 端点
///
/// - **健康检查**：`http://localhost:8080/actuator/health`
/// - **Prometheus 指标**：`http://localhost:8080/actuator/prometheus`（需认证）
/// - **SkyWalking UI**：`http://localhost:8080` （SkyWalking 服务）
///
/// ## 架构位置
///
/// 在六边形架构中，本 Starter 位于**框架层（Framework Layer）**，通过实现扩展点接口为其他 Starter
/// 提供可观测性能力。
///
/// ```
/// 业务服务 (patra-xxx-boot)
///   ↓ 依赖
/// 核心 Starter (patra-starter-core/rest-client/batch)
///   ├─ 定义扩展点接口（ResolutionInterceptor、ClientInterceptor、JobExecutionListener）
///   └─ 无 Micrometer 依赖（保持纯净）
///     ↓ 通过扩展点调用
/// 可观测性 Starter (patra-starter-observability) ← 本 Starter
///   ├─ 实现扩展点接口
///   ├─ 提供 Observation Handlers
///   └─ 集成 SkyWalking、Prometheus、Micrometer
/// ```
///
/// ## 依赖关系
///
/// - `micrometer-core`：Micrometer 核心
/// - `micrometer-registry-prometheus`：Prometheus 导出（可选）
/// - `skywalking-apm-toolkit-micrometer-1.5`：SkyWalking Meter（可选）
/// - `patra-spring-boot-starter-core`：错误处理、扩展点接口
/// - `patra-spring-boot-starter-rest-client`：REST 客户端扩展点（可选）
/// - `patra-spring-boot-starter-batch`：Batch 任务扩展点（可选）
///
/// ## 注意事项
///
/// ### 敏感数据脱敏（P0 级别）
///
/// 生产环境必须启用敏感数据脱敏，防止敏感信息泄露到日志、指标、追踪中：
///
/// ```yaml
/// patra:
///   observability:
///     security:
///       sensitive-data-masking:
///         enabled: true  # 生产环境强制启用
/// ```
///
/// ### Actuator 访问控制
///
/// 生产环境必须配置 Actuator 访问控制，避免敏感信息泄露：
///
/// ```yaml
/// management:
///   endpoints:
///     web:
///       exposure:
///         include: health,prometheus  # 仅暴露必要端点
///   endpoint:
///     health:
///       show-details: when-authorized  # 健康详情需认证
///
/// spring:
///   security:
///     user:
///       name: actuator
///       password: ${ACTUATOR_PASSWORD}  # 环境变量注入，禁止硬编码
/// ```
///
/// ### SkyWalking Agent 安全配置
///
/// 生产环境禁用 SQL 参数和 HTTP Body 收集，防止敏感数据泄露：
///
/// ```properties
/// # agent.config
/// plugin.mysql.trace_sql_parameters=false
/// plugin.http.collect_http_params=false
/// ```
///
/// @author linqibin
/// @since 0.1.0
///
package com.patra.starter.observability;
