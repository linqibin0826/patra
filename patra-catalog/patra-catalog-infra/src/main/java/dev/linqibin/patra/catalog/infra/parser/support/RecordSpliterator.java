package dev.linqibin.patra.catalog.infra.parser.support;

import dev.linqibin.patra.catalog.infra.parser.strategy.RecordParsingStrategy;
import javax.xml.stream.XMLStreamReader;

/// 通用 XML 记录 Spliterator。
///
/// 基于 {@link AbstractRecordSpliterator} 的具体实现，可与任意 {@link RecordParsingStrategy} 组合使用。
///
/// **使用方式**：
/// ```java
/// var reader = xmlInputFactory.createXMLStreamReader(inputStream);
/// var context = XmlParsingContext.of("2025");
/// var spliterator = new RecordSpliterator<>(reader, DescriptorParsingStrategy.INSTANCE, context);
/// return StreamSupport.stream(spliterator, false)
///     .onClose(() -> closeQuietly(reader));
/// ```
///
/// @param <T> 记录类型（领域对象）
/// @author linqibin
/// @since 0.1.0
public final class RecordSpliterator<T> extends AbstractRecordSpliterator<T> {

  /// 构造通用 Spliterator。
  ///
  /// @param reader XML 流读取器
  /// @param strategy 记录解析策略
  /// @param context 解析上下文
  public RecordSpliterator(
      XMLStreamReader reader, RecordParsingStrategy<T> strategy, XmlParsingContext context) {
    super(reader, strategy, context);
  }
}
