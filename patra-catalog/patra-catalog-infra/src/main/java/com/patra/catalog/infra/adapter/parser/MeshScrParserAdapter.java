package com.patra.catalog.infra.adapter.parser;

import com.patra.catalog.domain.exception.XmlParseException;
import com.patra.catalog.domain.model.aggregate.MeshScrAggregate;
import com.patra.catalog.domain.port.parser.MeshScrParserPort;
import com.patra.catalog.infra.adapter.parser.strategy.ScrParsingStrategy;
import com.patra.catalog.infra.adapter.parser.support.AbstractStaxParserAdapter;
import java.io.InputStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/// MeSH 补充概念记录（SCR）XML 解析适配器。
///
/// 使用 StAX 流式解析 MeSH Supplemental Record XML 文件。
///
/// **设计原则**：
///
/// - **单一职责**：只负责 SCR 解析，与 Descriptor 解析分离
/// - **资源管理**：继承 {@link AbstractStaxParserAdapter} 统一管理资源生命周期
/// - **流式处理**：使用 Stream 返回，支持大文件惰性求值
///
/// **性能特征**：
///
/// - 内存占用：<2GB（流式处理，不一次性加载整个 XML）
/// - 处理速度：约 1000 条/秒
/// - 数据量：约 350,000 条 SCR 记录
///
/// @author linqibin
/// @since 0.1.0
@Component
public class MeshScrParserAdapter extends AbstractStaxParserAdapter<MeshScrAggregate>
    implements MeshScrParserPort {

  @Override
  public Stream<MeshScrAggregate> parse(InputStream inputStream) {
    return doParse(
        inputStream, ScrParsingStrategy.INSTANCE, "开始解析 MeSH SCR XML 输入流", XmlParseException::new);
  }
}
