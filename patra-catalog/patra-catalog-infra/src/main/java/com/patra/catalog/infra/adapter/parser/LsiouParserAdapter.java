package com.patra.catalog.infra.adapter.parser;

import com.patra.catalog.domain.exception.XmlParseException;
import com.patra.catalog.domain.model.vo.venue.pubmed.PubmedSerialData;
import com.patra.catalog.domain.port.parser.LsiouParserPort;
import com.patra.catalog.infra.adapter.parser.strategy.SerialParsingStrategy;
import com.patra.catalog.infra.adapter.parser.support.AbstractStaxParserAdapter;
import java.io.InputStream;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/// NLM LSIOU XML 解析器实现。
///
/// 使用 StAX（Streaming API for XML）实现流式解析 NLM LSIOU 文件。
/// 支持处理约 15,000 条期刊记录的大型 XML 文件。
///
/// **设计原则**：
///
/// - **策略模式**：委托 {@link SerialParsingStrategy} 处理单条记录解析
/// - **资源管理**：继承 {@link AbstractStaxParserAdapter} 统一管理资源生命周期
/// - **流式处理**：使用 {@link java.util.stream.Stream} 逐元素读取，内存占用可控
/// - **惰性求值**：返回 Stream，由调用方控制处理速度
/// - **直接产出 Domain**：解析器直接产出领域模型，无需中间 DTO 转换
///
/// **架构说明**：
///
/// 此适配器采用「务实六边形架构」原则，直接产出 Domain 模型：
/// - 单向只读数据流（XML → Domain），无需中间 DTO 层
/// - 减少模型重复和转换开销
/// - 参考：Victor Rentea「仅在多通道暴露时才需 DTO」
///
/// **性能特征**：
///
/// - 内存占用：<1GB（流式处理，不一次性加载整个 XML）
/// - 处理速度：约 1000 条/秒
/// - 文件大小：支持 LSIOU 2025（约 15,000 条记录）
///
/// @author linqibin
/// @since 0.1.0
@Component
public class LsiouParserAdapter extends AbstractStaxParserAdapter<PubmedSerialData>
    implements LsiouParserPort {

  @Override
  public Stream<PubmedSerialData> parse(InputStream inputStream) {
    // 解析器直接产出领域模型，无需转换
    return doParse(
        inputStream,
        SerialParsingStrategy.INSTANCE,
        "开始解析 NLM LSIOU XML 输入流",
        XmlParseException::new);
  }
}
