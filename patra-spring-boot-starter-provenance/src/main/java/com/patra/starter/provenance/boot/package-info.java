/**
 * Provenance Starter 启动配置包。
 *
 * <p>本包提供 Spring Boot 自动配置支持，用于快速集成 Provenance 数据源客户端。自动注册
 * PubMed 和 Europe PMC 端口实现，并支持通过属性文件进行声明式配置。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>自动配置 PubMed 和 EPMC 客户端
 *   <li>注册 {@link com.patra.starter.provenance.common.adapter.DataSourcePort} 到 {@link
 *       com.patra.starter.provenance.common.adapter.AdapterRegistry}
 *   <li>加载并验证 {@code patra.provenance.*} 配置属性
 *   <li>按需集成 Micrometer 指标监控
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.provenance.boot.ProvenanceAutoConfiguration} - 主自动配置类
 *   <li>{@link com.patra.starter.provenance.boot.ProvenanceProperties} - 绑定 {@code
 *       patra.provenance.*} 属性
 * </ul>
 *
 * <h2>使用方式</h2>
 *
 * <p>添加依赖后，Starter 会自动配置，无需额外代码：
 *
 * <pre>{@code
 * <!-- Maven -->
 * <dependency>
 *     <groupId>com.patra</groupId>
 *     <artifactId>patra-spring-boot-starter-provenance</artifactId>
 * </dependency>
 * }</pre>
 *
 * <p>在 {@code application.yml} 中配置数据源：
 *
 * <pre>{@code
 * patra:
 *   provenance:
 *     enabled: true
 *     pubmed:
 *       base-url: https://eutils.ncbi.nlm.nih.gov/entrez/eutils
 *       api-key: your-api-key
 *     epmc:
 *       base-url: https://www.ebi.ac.uk/europepmc/webservices/rest
 * }</pre>
 *
 * <h2>扩展点</h2>
 *
 * <ul>
 *   <li>实现 {@link com.patra.starter.provenance.common.adapter.DataSourcePort} 添加新数据源
 *   <li>自定义 {@link com.patra.starter.provenance.common.metrics.ProvenanceMetrics} 替换指标记录器
 *   <li>使用 {@code patra.provenance.enabled=false} 禁用自动配置
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 */
package com.patra.starter.provenance.boot;
