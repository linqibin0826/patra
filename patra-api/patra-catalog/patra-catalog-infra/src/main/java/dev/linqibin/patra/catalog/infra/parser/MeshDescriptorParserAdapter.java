package dev.linqibin.patra.catalog.infra.parser;

import dev.linqibin.patra.catalog.domain.exception.XmlParseException;
import dev.linqibin.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import dev.linqibin.patra.catalog.domain.port.parser.MeshDescriptorParserPort;
import dev.linqibin.patra.catalog.infra.parser.strategy.DescriptorParsingStrategy;
import dev.linqibin.patra.catalog.infra.parser.support.AbstractStaxParserAdapter;
import java.io.InputStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/// MeSH 主题词 XML 解析适配器。
///
/// 使用 StAX 流式解析 MeSH Descriptor XML 文件。
///
/// **设计原则**：
///
/// - **单一职责**：只负责主题词解析，与限定词解析分离
/// - **资源管理**：继承 {@link AbstractStaxParserAdapter} 统一管理资源生命周期
/// - **流式处理**：使用 Stream 返回，支持大文件惰性求值
///
/// **性能特征**：
///
/// - 内存占用：<2GB（流式处理，不一次性加载整个 XML）
/// - 处理速度：约 1000 条/秒
/// - 数据量：约 35,000 条主题词
///
/// @author linqibin
/// @since 0.1.0
@Component
public class MeshDescriptorParserAdapter extends AbstractStaxParserAdapter<MeshDescriptorAggregate>
    implements MeshDescriptorParserPort {

  @Override
  public Stream<MeshDescriptorAggregate> parse(InputStream inputStream) {
    return doParse(
        inputStream,
        DescriptorParsingStrategy.INSTANCE,
        "开始解析 MeSH Descriptor XML 输入流",
        XmlParseException::new);
  }
}
