package dev.patra.catalog.infra.kernel.support;

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
/// **实体大小上限**：JDK 24 起的安全默认值把 `jdk.xml.maxGeneralEntitySizeLimit` 与
/// `jdk.xml.totalEntitySizeLimit` 设为 100,000，且把整篇文档里每个预定义实体引用（`&amp;` 等）都累计进去。
/// PubMed 的大文件（如 `pubmed26n1379`，参考文献密集）一个文件就超过 10 万个 `&amp;`，解析在文件中途失败。
/// 这里把两项设为 0（不限）：DTD 已禁用，文档里只可能出现预定义实体与字符引用，不存在可膨胀的自定义实体。
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
    // 预定义实体引用不计入实体大小上限（JDK 24+ 默认 100,000，PubMed 大文件会超）
    INSTANCE.setProperty("jdk.xml.maxGeneralEntitySizeLimit", 0);
    INSTANCE.setProperty("jdk.xml.totalEntitySizeLimit", 0);
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
