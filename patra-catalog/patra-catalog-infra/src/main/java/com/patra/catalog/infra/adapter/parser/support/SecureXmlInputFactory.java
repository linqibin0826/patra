package com.patra.catalog.infra.adapter.parser.support;

import javax.xml.stream.XMLInputFactory;

/// 安全的 XMLInputFactory 单例。
///
/// 禁用外部实体引用，防止 XXE 攻击（OWASP A03:2021 Injection）。
///
/// **安全配置**：
///
/// - 禁用外部实体：`IS_SUPPORTING_EXTERNAL_ENTITIES = false`
/// - 禁用 DTD：`SUPPORT_DTD = false`
/// - 空 XMLResolver：忽略所有外部 DTD/实体引用
///
/// @author linqibin
/// @since 0.1.0
public final class SecureXmlInputFactory {

  private static final XMLInputFactory INSTANCE;

  static {
    INSTANCE = XMLInputFactory.newInstance();
    // 禁用外部实体引用，防止 XXE 攻击（OWASP A03:2021 Injection）
    INSTANCE.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, Boolean.FALSE);
    INSTANCE.setProperty(XMLInputFactory.SUPPORT_DTD, Boolean.FALSE);
    // 设置空的 XMLResolver，忽略所有外部 DTD/实体引用，避免 DOCTYPE 解析错误
    INSTANCE.setXMLResolver((publicID, systemID, baseURI, namespace) -> null);
  }

  private SecureXmlInputFactory() {
    // 工具类禁止实例化
  }

  /// 获取安全配置的 XMLInputFactory 实例。
  ///
  /// @return 安全的 XMLInputFactory 单例
  public static XMLInputFactory getInstance() {
    return INSTANCE;
  }
}
