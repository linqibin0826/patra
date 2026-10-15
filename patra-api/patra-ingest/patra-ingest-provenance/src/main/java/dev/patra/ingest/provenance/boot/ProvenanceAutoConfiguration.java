package dev.patra.ingest.provenance.boot;

import dev.patra.ingest.provenance.common.config.DefaultConfigProvider;
import dev.patra.ingest.provenance.common.metrics.ProvenanceMetrics;
import dev.patra.ingest.provenance.common.provider.ProvenanceDataProvider;
import dev.patra.ingest.provenance.common.provider.ProviderRegistry;
import dev.patra.ingest.provenance.epmc.EPMCClient;
import dev.patra.ingest.provenance.epmc.EpmcClientAdapter;
import dev.patra.ingest.provenance.pubmed.PubMedClient;
import dev.patra.ingest.provenance.pubmed.PubMedClientAdapter;
import dev.patra.ingest.provenance.pubmed.PubmedDataProvider;
import dev.patra.ingest.provenance.pubmed.converter.PubmedPublicationConverter;
import dev.patra.ingest.provenance.pubmed.processor.PubmedPublicationProcessor;
import dev.patra.ingest.provenance.pubmed.request.PubMedESearchRequestAssembler;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.jackson.autoconfigure.XmlMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.xml.XmlMapper;

/// Provenance Starter 自动配置类
///
/// 自动配置 PubMed 和 Europe PMC 客户端。出站 HTTP 请求直接由 Starter 执行,无独立的出站网关服务。
///
/// 配置说明:
///
/// - 使用 `patra.starter.provenance.enabled=false` 可禁用此自动配置
///   - 默认启用,自动注册 PubMed 和 EPMC 数据源提供者
///   - 集成 Micrometer 进行指标监控(如果可用)
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(ProvenanceProperties.class)
@ConditionalOnProperty(
    prefix = "patra.starter.provenance",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
public class ProvenanceAutoConfiguration {

  /// 创建读取应用配置属性的默认配置提供者
  ///
  /// @param properties 绑定的 Provenance 配置属性
  /// @return 默认配置提供者实例
  @Bean
  @ConditionalOnMissingBean
  public DefaultConfigProvider defaultConfigProvider(ProvenanceProperties properties) {
    log.info("初始化 Provenance 配置提供者,包含 PubMed 和 EPMC 默认配置");
    return new DefaultConfigProvider(properties);
  }

  /// 创建基于 Micrometer 的指标记录器(当 MeterRegistry 存在时)
  ///
  /// @param meterRegistry Micrometer 指标注册表
  /// @return Provenance 指标记录器实例
  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnBean(MeterRegistry.class)
  public ProvenanceMetrics provenanceMetrics(MeterRegistry meterRegistry) {
    log.info("启用基于 Micrometer 的 Provenance 指标监控");
    return new ProvenanceMetrics(meterRegistry);
  }

  /// 注册提供者注册表,允许 Ingest 引擎发现可用的数据源提供者实现
  ///
  /// @param providersProvider 提供者实现的提供者
  /// @return 提供者注册表实例
  @Bean
  @ConditionalOnMissingBean
  public ProviderRegistry providerRegistry(
      ObjectProvider<List<ProvenanceDataProvider>> providersProvider) {
    List<ProvenanceDataProvider> providers = providersProvider.getIfAvailable(List::of);
    return new ProviderRegistry(providers);
  }

  /// 定制 Boot 装配的 XML 映射器，使用宽松的解析选项处理 PubMed 的 XML 格式
  ///
  /// `XmlMapper` 由 Boot 的 Jackson 自动配置创建，本 starter 只贡献定制器。这组配置只作用于
  /// `XmlMapper`，不放进 `spring.jackson.*`，避免同时改到容器里的 `JsonMapper`。
  ///
  /// @return XML 映射器构建器定制器
  @Bean
  public XmlMapperBuilderCustomizer provenanceXmlMapperBuilderCustomizer() {
    return builder ->
        builder
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
            .defaultUseWrapper(false);
  }

  /// 创建 PubMed 文章转换器,用于将 PubMed 响应转换为 CanonicalPublication
  ///
  /// @return PubMed 文章转换器实例
  @Bean
  @ConditionalOnMissingBean
  public PubmedPublicationConverter pubmedArticleConverter() {
    return new PubmedPublicationConverter();
  }

  /// 创建用于直接 HTTP 访问 E-utilities API 的 PubMed 客户端
  ///
  /// 使用 patra-spring-boot-starter-rest-client 提供的 defaultRestClient，统一管理 HTTP 客户端配置。
  ///
  /// @param defaultRestClient 统一的 RestClient（来自 rest-client starter）
  /// @param configProvider PubMed 设置的配置提供者
  /// @param xmlMapper 用于解析 PubMed XML 响应的 XML 映射器（Boot 装配）
  /// @param jsonMapper 用于解析 PubMed JSON 响应的 JSON 映射器
  /// @param metrics 可选的指标记录器
  /// @return PubMed 客户端实现
  @Bean
  @ConditionalOnMissingBean
  public PubMedClient pubMedClient(
      RestClient defaultRestClient,
      DefaultConfigProvider configProvider,
      XmlMapper xmlMapper,
      JsonMapper jsonMapper,
      Optional<ProvenanceMetrics> metrics) {
    log.info("自动配置 PubMed 客户端,用于访问 E-utilities API (使用 defaultRestClient)");
    return new PubMedClientAdapter(
        defaultRestClient, configProvider, jsonMapper, xmlMapper, metrics.orElse(null));
  }

  /// 创建用于直接 HTTP 访问 EPMC API 的 Europe PMC 客户端
  ///
  /// 使用 patra-spring-boot-starter-rest-client 提供的 defaultRestClient，统一管理 HTTP 客户端配置。
  ///
  /// @param defaultRestClient 统一的 RestClient（来自 rest-client starter）
  /// @param configProvider EPMC 设置的配置提供者
  /// @param jsonMapper 用于解析 EPMC 响应的 JSON 映射器
  /// @param metrics 可选的指标记录器
  /// @return Europe PMC 客户端实现
  @Bean
  @ConditionalOnMissingBean
  public EPMCClient epmcClient(
      RestClient defaultRestClient,
      DefaultConfigProvider configProvider,
      JsonMapper jsonMapper,
      Optional<ProvenanceMetrics> metrics) {
    log.info("自动配置 Europe PMC 客户端,用于访问 EPMC API (使用 defaultRestClient)");
    return new EpmcClientAdapter(
        defaultRestClient, configProvider, jsonMapper, metrics.orElse(null));
  }

  /// 创建 PubMed ESearch 请求组装器
  ///
  /// @return PubMed ESearch 请求组装器实例
  @Bean
  @ConditionalOnMissingBean
  public PubMedESearchRequestAssembler pubMedESearchRequestAssembler() {
    log.debug("初始化 PubMed ESearch 请求组装器");
    return new PubMedESearchRequestAssembler();
  }

  /// 创建 PubMed 出版物处理器
  ///
  /// @param pubMedClient PubMed 客户端
  /// @param converter PubMed 出版物转换器
  /// @param properties Provenance 配置属性
  /// @param metrics 可选的指标记录器
  /// @return PubMed 出版物处理器实例
  @Bean
  @ConditionalOnMissingBean
  public PubmedPublicationProcessor pubmedPublicationProcessor(
      PubMedClient pubMedClient,
      PubmedPublicationConverter converter,
      ProvenanceProperties properties,
      Optional<ProvenanceMetrics> metrics) {
    log.debug("初始化 PubMed 出版物处理器");
    return new PubmedPublicationProcessor(
        pubMedClient, converter, properties, metrics.orElse(null));
  }

  /// 创建 PubMed 数据源提供者
  ///
  /// @param publicationProcessor PubMed 出版物处理器
  /// @param pubMedClient PubMed 客户端
  /// @param requestAssembler 请求组装器
  /// @return PubMed 数据源提供者实例
  @Bean
  @ConditionalOnMissingBean
  public PubmedDataProvider pubmedProvenanceDataProvider(
      PubmedPublicationProcessor publicationProcessor,
      PubMedClient pubMedClient,
      PubMedESearchRequestAssembler requestAssembler) {
    log.info("自动配置 PubMed 数据源提供者，将注册到 ProviderRegistry");
    return new PubmedDataProvider(publicationProcessor, pubMedClient, requestAssembler);
  }
}
