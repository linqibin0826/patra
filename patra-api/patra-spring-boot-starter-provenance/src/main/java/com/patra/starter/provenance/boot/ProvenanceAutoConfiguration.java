package com.patra.starter.provenance.boot;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.patra.starter.provenance.common.config.DefaultConfigProvider;
import com.patra.starter.provenance.common.config.HttpConfig;
import com.patra.starter.provenance.common.config.ProvenanceConfig;
import com.patra.starter.provenance.common.metrics.ProvenanceMetrics;
import com.patra.starter.provenance.common.provider.ProvenanceDataProvider;
import com.patra.starter.provenance.common.provider.ProviderRegistry;
import com.patra.starter.provenance.epmc.EPMCClient;
import com.patra.starter.provenance.epmc.EpmcClientAdapter;
import com.patra.starter.provenance.pubmed.PubMedClient;
import com.patra.starter.provenance.pubmed.PubMedClientAdapter;
import com.patra.starter.provenance.pubmed.PubmedDataProvider;
import com.patra.starter.provenance.pubmed.converter.PubmedPublicationConverter;
import com.patra.starter.provenance.pubmed.processor.PubmedPublicationProcessor;
import com.patra.starter.provenance.pubmed.request.PubMedESearchRequestAssembler;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/// Provenance Starter 自动配置类
///
/// 自动配置 PubMed 和 Europe PMC 客户端。出站 HTTP 请求直接由 Starter 执行,无独立的出站网关服务。
///
/// 配置说明:
///
/// - 使用 `patra.provenance.enabled=false` 可禁用此自动配置
///   - 默认启用,自动注册 PubMed 和 EPMC 数据源提供者
///   - 集成 Micrometer 进行指标监控(如果可用)
///
/// @author linqibin
/// @since 0.1.0
@Slf4j
@AutoConfiguration
@EnableConfigurationProperties(ProvenanceProperties.class)
@ConditionalOnProperty(
    prefix = "patra.provenance",
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

  /// 创建用于解析 PubMed XML 响应的 XML 映射器
  ///
  /// 配置为使用宽松的解析选项处理 PubMed 的 XML 格式。
  ///
  /// @return 配置好的 XML 映射器
  @Bean
  @ConditionalOnMissingBean
  public XmlMapper provenanceXmlMapper() {
    return XmlMapper.builder()
        .findAndAddModules()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(DeserializationFeature.ACCEPT_EMPTY_STRING_AS_NULL_OBJECT, true)
        .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
        .configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES, true)
        .defaultUseWrapper(false)
        .build();
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
  /// @param xmlMapper 用于解析 PubMed XML 响应的 XML 映射器
  /// @param objectMapper 用于解析 PubMed JSON 响应的 JSON 映射器
  /// @param metrics 可选的指标记录器
  /// @return PubMed 客户端实现
  @Bean
  @ConditionalOnMissingBean
  public PubMedClient pubMedClient(
      RestClient defaultRestClient,
      DefaultConfigProvider configProvider,
      XmlMapper xmlMapper,
      ObjectMapper objectMapper,
      Optional<ProvenanceMetrics> metrics) {
    log.info("自动配置 PubMed 客户端,用于访问 E-utilities API (使用 defaultRestClient)");
    return new PubMedClientAdapter(
        defaultRestClient, configProvider, objectMapper, xmlMapper, metrics.orElse(null));
  }

  /// 创建用于直接 HTTP 访问 EPMC API 的 Europe PMC 客户端
  ///
  /// 使用 patra-spring-boot-starter-rest-client 提供的 defaultRestClient，统一管理 HTTP 客户端配置。
  ///
  /// @param defaultRestClient 统一的 RestClient（来自 rest-client starter）
  /// @param configProvider EPMC 设置的配置提供者
  /// @param objectMapper 用于解析 EPMC 响应的 JSON 映射器
  /// @param metrics 可选的指标记录器
  /// @return Europe PMC 客户端实现
  @Bean
  @ConditionalOnMissingBean
  public EPMCClient epmcClient(
      RestClient defaultRestClient,
      DefaultConfigProvider configProvider,
      ObjectMapper objectMapper,
      Optional<ProvenanceMetrics> metrics) {
    log.info("自动配置 Europe PMC 客户端,用于访问 EPMC API (使用 defaultRestClient)");
    return new EpmcClientAdapter(defaultRestClient, configProvider, objectMapper, metrics.orElse(null));
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
