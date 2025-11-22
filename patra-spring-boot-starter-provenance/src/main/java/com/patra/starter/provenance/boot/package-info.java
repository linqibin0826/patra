/// Provenance Starter 启动配置包。
/// 
/// 本包提供 Spring Boot 自动配置支持，用于快速集成 Provenance 数据源客户端。自动注册 PubMed 和 Europe PMC
/// 客户端实现，并支持通过属性文件进行声明式配置。
/// 
/// ## 职责
/// 
/// - 自动配置 PubMed 和 EPMC 客户端（基于 Spring RestClient）
///   - 创建专用 RestClient Bean（含超时和默认 Headers）
///   - 注册 {@link com.patra.starter.provenance.common.provider.ProvenanceDataProvider} 到 {@link
///       com.patra.starter.provenance.common.provider.ProviderRegistry}
///   - 加载并验证 `patra.provenance.*` 配置属性
///   - 按需集成 Micrometer 指标监控
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.starter.provenance.boot.ProvenanceAutoConfiguration} - 主自动配置类
///   - {@link com.patra.starter.provenance.boot.ProvenanceProperties} - 绑定 `patra.provenance.*` 属性
/// 
/// ## HTTP 客户端实现
/// 
/// 自动配置创建以下 RestClient Bean:
/// 
/// - `pubMedRestClient` - PubMed 专用 RestClient（配置从 `patra.provenance.sources.pubmed` 提取）
///   - `epmcRestClient` - EPMC 专用 RestClient（配置从 `patra.provenance.sources.epmc`
///       提取）
///   - 底层使用 JDK 21 HttpClient（通过 JdkClientHttpRequestFactory）
/// 
/// ## 使用方式
/// 
/// 添加依赖后，Starter 会自动配置，无需额外代码：
/// 
/// ```java
/// <!-- Maven -->
/// <dependency>
///     <groupId>com.patra</groupId>
///     <artifactId>patra-spring-boot-starter-provenance</artifactId>
/// </dependency>
/// ```
/// 
/// 在 `application.yml` 中配置数据源：
/// 
/// ```java
/// patra:
///   provenance:
///     enabled: true
///     defaults:
///       http:
///         timeout-connect-millis: 10000
///         timeout-read-millis: 30000
///     sources:
///       pubmed:
///         base-url: https://eutils.ncbi.nlm.nih.gov/entrez/eutils
///         api-key: your-api-key
///       epmc:
///         base-url: https://www.ebi.ac.uk/europepmc/webservices/rest
/// ```
/// 
/// ## 扩展点
/// 
/// - 实现 {@link com.patra.starter.provenance.common.provider.ProvenanceDataProvider} 添加新数据源
///   - 自定义 {@link com.patra.starter.provenance.common.metrics.ProvenanceMetrics} 替换指标记录器
///   - 使用 `patra.provenance.enabled=false` 禁用自动配置
///   - 手动创建 RestClient Bean 以完全控制 HTTP 配置
/// 
/// @since 0.1.0
/// @author linqibin
package com.patra.starter.provenance.boot;
