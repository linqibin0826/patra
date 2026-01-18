package com.patra.catalog.infra.adapter.parser;

import com.patra.catalog.domain.exception.XmlParseException;
import com.patra.catalog.domain.model.vo.publication.pubmed.PubmedArticle;
import com.patra.catalog.domain.port.parser.PubmedXmlParserPort;
import com.patra.catalog.infra.adapter.parser.strategy.PubmedArticleParsingStrategy;
import com.patra.catalog.infra.adapter.parser.support.AbstractStaxParserAdapter;
import java.io.InputStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/// PubMed XML 解析适配器。
///
/// 基于 StAX 流式解析 PubMed Baseline XML 文件，返回 `PubmedArticle` DTO 流。
///
/// **实现细节**：
///
/// - 继承 `AbstractStaxParserAdapter` 模板，复用资源管理逻辑
/// - 使用 `PubmedArticleParsingStrategy` 解析单条记录
/// - 无效记录（缺少 PMID）自动跳过，不中断流处理
///
/// **性能特点**：
///
/// - 流式解析，内存占用恒定（不受文件大小影响）
/// - 惰性求值，可随时中断处理
/// - 约 30,000 条/文件，解析速度约 1,000 条/秒
///
/// @author linqibin
/// @since 0.1.0
@Component
public class PubmedXmlParserAdapter extends AbstractStaxParserAdapter<PubmedArticle>
    implements PubmedXmlParserPort {

  /// 解析 PubMed XML 输入流。
  ///
  /// @param inputStream XML 输入流（调用方负责关闭）
  /// @return 文献记录流（惰性求值）
  /// @throws XmlParseException 解析失败时抛出
  @Override
  public Stream<PubmedArticle> parse(InputStream inputStream) {
    return doParse(
        inputStream,
        PubmedArticleParsingStrategy.INSTANCE,
        "开始解析 PubMed Baseline XML",
        XmlParseException::new);
  }
}
