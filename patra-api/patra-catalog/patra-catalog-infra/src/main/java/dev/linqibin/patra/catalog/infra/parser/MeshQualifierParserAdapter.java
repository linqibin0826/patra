package dev.linqibin.patra.catalog.infra.parser;

import dev.linqibin.patra.catalog.domain.exception.XmlParseException;
import dev.linqibin.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import dev.linqibin.patra.catalog.domain.port.parser.MeshQualifierParserPort;
import dev.linqibin.patra.catalog.infra.parser.strategy.QualifierParsingStrategy;
import dev.linqibin.patra.catalog.infra.parser.support.AbstractStaxParserAdapter;
import java.io.InputStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/// MeSH 限定词 XML 解析适配器。
///
/// 使用 StAX 流式解析 MeSH Qualifier XML 文件。
///
/// **设计原则**：
///
/// - **单一职责**：只负责限定词解析，与主题词解析分离
/// - **资源管理**：继承 {@link AbstractStaxParserAdapter} 统一管理资源生命周期
/// - **流式处理**：使用 Stream 返回，支持大文件惰性求值
///
/// **性能特征**：
///
/// - 内存占用：<1GB（流式处理）
/// - 处理速度：约 1000 条/秒
/// - 数据量：约 80 条限定词
///
/// @author linqibin
/// @since 0.1.0
@Component
public class MeshQualifierParserAdapter extends AbstractStaxParserAdapter<MeshQualifierAggregate>
    implements MeshQualifierParserPort {

  @Override
  public Stream<MeshQualifierAggregate> parse(InputStream inputStream) {
    return doParse(
        inputStream,
        QualifierParsingStrategy.INSTANCE,
        "开始解析 MeSH Qualifier XML 输入流",
        XmlParseException::new);
  }
}
