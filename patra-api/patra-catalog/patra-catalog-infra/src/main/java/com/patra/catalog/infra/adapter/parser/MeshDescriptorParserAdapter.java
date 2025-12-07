package com.patra.catalog.infra.adapter.parser;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.port.parser.MeshDescriptorParserPort;
import com.patra.catalog.infra.adapter.parser.strategy.DescriptorParsingStrategy;
import com.patra.catalog.infra.adapter.parser.support.AbstractStaxParserAdapter;
import java.nio.file.Path;
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
  public Stream<MeshDescriptorAggregate> parse(Path filePath) {
    return doParse(
        filePath,
        DescriptorParsingStrategy.INSTANCE,
        "开始解析 MeSH Descriptor XML 文件：{}",
        XmlParsingException::new);
  }
}
