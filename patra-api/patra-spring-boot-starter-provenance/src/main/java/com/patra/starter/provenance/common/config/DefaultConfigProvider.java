package com.patra.starter.provenance.common.config;

import com.patra.starter.provenance.boot.ProvenanceProperties;
import com.patra.starter.provenance.boot.ProvenanceProperties.SourceProperties;
import java.util.Objects;

/**
 * 默认配置提供者
 *
 * <p>基于 {@link ProvenanceProperties} 提供不可变的 {@link ProvenanceConfig} 实例。
 * 该类从应用配置中读取已注册的数据源配置,并转换为标准的 ProvenanceConfig 对象供客户端使用。
 */
public class DefaultConfigProvider {

  private final ProvenanceProperties properties;

  public DefaultConfigProvider(ProvenanceProperties properties) {
    this.properties = Objects.requireNonNull(properties, "properties cannot be null");
  }

  /**
   * 返回 PubMed 的默认配置
   *
   * @return PubMed 的不可变配置对象
   */
  public ProvenanceConfig getPubMedDefaultConfig() {
    return getDefaultConfig("pubmed");
  }

  /**
   * 返回 Europe PMC 的默认配置
   *
   * @return Europe PMC 的不可变配置对象
   */
  public ProvenanceConfig getEPMCDefaultConfig() {
    return getDefaultConfig("epmc");
  }

  /**
   * 返回指定数据源代码的默认配置
   *
   * @param provenanceCode 数据源标识符(如 "pubmed", "epmc")
   * @return 不可变的配置对象
   * @throws IllegalStateException 如果配置缺少必需字段(如 baseUrl)
   */
  public ProvenanceConfig getDefaultConfig(String provenanceCode) {
    SourceProperties source = properties.getConfigForSource(provenanceCode);
    try {
      return source.toProvenanceConfig();
    } catch (IllegalStateException ex) {
      throw new IllegalStateException("数据源配置无效: " + provenanceCode, ex);
    }
  }
}
