package dev.patra.catalog.domain.publication.port.parser;

import dev.patra.common.CanonicalPublication;

/// PubMed 文献 XML 解析端口（领域层定义，基础设施层实现）。
///
/// 按篇解析：导入时由读取器把文件逐篇切成 `<PubmedArticle>` 原文，本地重算时从来源表读出原文，
/// 两条路径都调用 [#parseArticle]，因此一篇的解析异常只影响这一篇。
///
/// 返回 `CanonicalPublication`（Shared Kernel 标准化模型），`Journal` 内嵌对象包含 Venue 匹配所需的全部字段。
///
/// @author linqibin
/// @since 0.1.0
public interface PubmedXmlParserPort {

  /// 解析一篇 `<PubmedArticle>` 原文（逐篇切出或从来源表读出）。
  ///
  /// @param rawXml 一篇 `<PubmedArticle>` 的原文
  /// @return 规范化文献；缺 PMID 时为 null
  /// @throws dev.patra.catalog.domain.kernel.exception.XmlParseException 原文不是合法 XML 时
  CanonicalPublication parseArticle(String rawXml);
}
