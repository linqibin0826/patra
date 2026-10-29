package dev.patra.catalog.domain.fulltext.port.parser;

import dev.patra.catalog.domain.fulltext.model.vo.FullTextMedia;
import dev.patra.catalog.domain.fulltext.model.vo.JatsParseResult;
import java.io.InputStream;
import java.util.List;

/// JATS XML → Patra 正文结构的解析端口（只解析 `<body>` 与 `<back>`）。
///
/// @author linqibin
/// @since 0.11.0
public interface JatsBodyParserPort {

  /// 解析器版本。改动解析规则时 +1，解析任务据此重新解析旧结果（全程不访问 PMC）。
  int PARSER_VERSION = 2;

  /// 解析一篇文章。
  ///
  /// @param xml JATS XML 输入流（调用方负责关闭）
  /// @param media 该篇已下载的图片清单，图块按它匹配文件名与宽高；解析器不访问存储
  /// @return 解析结果
  /// @throws dev.patra.catalog.domain.kernel.exception.XmlParseException XML 不合法时抛出
  JatsParseResult parse(InputStream xml, List<FullTextMedia> media);
}
