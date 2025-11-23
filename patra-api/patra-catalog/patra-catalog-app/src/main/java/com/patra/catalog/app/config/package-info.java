/// 应用配置包。
///
/// 包含 App 层的配置属性类，使用 Spring Boot @ConfigurationProperties 实现外部化配置。
///
/// ## 职责
///
/// - **配置绑定**：从 Nacos 配置中心读取配置并绑定到 Java 对象
///   - **配置验证**：提供配置项的校验和默认值
///   - **配置隔离**：按功能模块划分配置类（如 MeshImportConfig）
///   - **动态刷新**：支持 Nacos 配置热更新（无需重启应用）
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.app.config.MeshImportConfig} - MeSH 数据导入配置
///
/// - 配置前缀：`patra.catalog.mesh.import`
///       - 数据源 URL：descriptorSourceUrl、qualifierSourceUrl
///       - 批次大小：defaultBatchSize、batchSizeMap（表级别定制）
///       - 超时重试：downloadTimeout、retryMaxAttempts
///       - 数据验证：expectedCounts、countDifferenceThreshold
///
/// ## 设计原则
///
/// - **类型安全**：使用强类型 Java 对象，编译期检查配置项
///   - **默认值提供**：所有配置项提供合理的默认值，避免配置缺失
///   - **文档化配置**：每个字段添加 JavaDoc 注释，说明用途和示例
///   - **分组配置**：使用 Map 存储同类配置（如 batchSizeMap）
///
/// ## 配置示例
///
/// ```yaml
/// # Nacos 配置中心（data-id: patra-catalog.yaml）
/// patra:
///   catalog:
///     mesh:
///       import:
///         # 数据源 URL
///         descriptor-source-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml
///         qualifier-source-url: https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/qual2025.xml
///
///         # 批次大小配置
///         default-batch-size: 1000
///         batch-size-map:
///           descriptor: 1000
///           tree-number: 1500
///           entry-term: 2000
///           concept: 2000
///
///         # 超时与重试
///         download-timeout: 10m
///         retry-max-attempts: 3
///
///         # 数据量验证
///         expected-counts:
///           descriptor: 35000
///           qualifier: 80
///           tree-number: 80000
///           entry-term: 250000
///           concept: 180000
///         count-difference-threshold: 5.0
///
///         # 文件大小验证
///         expected-file-size: 313524224  # 约 299 MB
///         file-size-difference-threshold: 10.0
/// ```
///
/// ## 使用示例
///
/// ```java
/// @Service
/// @RequiredArgsConstructor
/// public class MeshImportOrchestrator {
///
///     private final MeshImportConfig meshImportConfig;
///
///     public void startImport() {
///         // 获取配置项
///         String url = meshImportConfig.getDescriptorSourceUrl();
///         Integer batchSize = meshImportConfig.getBatchSizeForTable("descriptor");
///         Duration timeout = meshImportConfig.getDownloadTimeout();
///
///         log.info("数据源 URL: {}", url);
///         log.info("批次大小: {}", batchSize);
///         log.info("超时时间: {} 秒", timeout.getSeconds());
///     }
/// }
/// ```
///
/// ## 配置刷新
///
/// 配置支持动态刷新（通过 Nacos @RefreshScope 或重启应用）：
///
/// ```java
/// // 在 Nacos 控制台修改配置后，应用会自动重新绑定配置对象
/// // 无需重启应用（但某些配置可能需要重启才能生效，如线程池大小）
/// ```
///
/// ## 架构位置
///
/// **App 层 - 配置管理**：
///
/// - 属于应用层的基础设施配置
/// - 不包含业务逻辑，仅提供配置数据
/// - 被 Orchestrator、Validator 等组件依赖
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.app.config;
