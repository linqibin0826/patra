package com.patra.catalog.infra.adapter.parser;

import com.patra.catalog.domain.exception.XmlParseException;
import com.patra.catalog.domain.model.dto.serfile.SerialRecord;
import com.patra.catalog.domain.port.parser.SerfileParserPort;
import com.patra.catalog.infra.adapter.parser.strategy.SerialParsingStrategy;
import com.patra.catalog.infra.adapter.parser.support.AbstractStaxParserAdapter;
import java.io.InputStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/// NLM Serfile XML 解析器实现。
///
/// 使用 StAX（Streaming API for XML）实现流式解析 NLM Serfile 文件。
/// 支持处理约 35,000 条期刊记录的大型 XML 文件。
///
/// **设计原则**：
///
/// - **策略模式**：委托 {@link SerialParsingStrategy} 处理单条记录解析
/// - **资源管理**：继承 {@link AbstractStaxParserAdapter} 统一管理资源生命周期
/// - **流式处理**：使用 {@link java.util.stream.Stream} 逐元素读取，内存占用可控
/// - **惰性求值**：返回 Stream，由调用方控制处理速度
///
/// **性能特征**：
///
/// - 内存占用：<1GB（流式处理，不一次性加载整个 XML）
/// - 处理速度：约 1000 条/秒
/// - 文件大小：支持 SerfileBase 2025（约 35,000 条记录）
///
/// @author linqibin
/// @since 0.1.0
@Component
public class SerfileParserAdapter extends AbstractStaxParserAdapter<SerialRecord>
    implements SerfileParserPort {

  @Override
  public Stream<SerialRecord> parse(InputStream inputStream) {
    return doParse(
        inputStream,
        SerialParsingStrategy.INSTANCE,
        "开始解析 NLM Serfile XML 输入流",
        XmlParseException::new);
  }
}
