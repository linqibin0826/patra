package com.patra.catalog.infra.adapter.parser.support;

import com.patra.catalog.infra.adapter.parser.XmlParsingException;
import com.patra.catalog.infra.adapter.parser.strategy.RecordParsingStrategy;
import java.util.Spliterator;
import java.util.function.Consumer;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import lombok.extern.slf4j.Slf4j;

/// XML 记录 Spliterator 抽象基类。
///
/// 统一流化逻辑，消除各 Spliterator 子类的重复代码。
/// 将记录查找与解析分离：基类负责在 XML 流中定位目标元素，策略负责解析具体内容。
///
/// **特性**：
/// - ORDERED: 保持 XML 中的顺序
/// - NONNULL: 不返回 null 元素（策略返回 null 时跳过）
/// - IMMUTABLE: 元素不可变
/// - 不支持并行（trySplit 返回 null）
///
/// **使用方式**：
/// ```java
/// var reader = xmlInputFactory.createXMLStreamReader(inputStream);
/// var spliterator = new ConcreteRecordSpliterator(reader, strategy, context);
/// return StreamSupport.stream(spliterator, false)
///     .onClose(() -> closeQuietly(reader));
/// ```
///
/// @param <T> 记录类型（领域对象）
/// @author linqibin
/// @since 0.1.0
@Slf4j
public abstract class AbstractRecordSpliterator<T> implements Spliterator<T> {

  /// XML 流读取器。
  protected final XMLStreamReader reader;

  /// 记录解析策略。
  protected final RecordParsingStrategy<T> strategy;

  /// 解析上下文。
  protected final XmlParsingContext context;

  /// 是否已完成（流已耗尽）。
  protected boolean finished = false;

  /// 构造 Spliterator。
  ///
  /// @param reader XML 流读取器
  /// @param strategy 记录解析策略
  /// @param context 解析上下文
  protected AbstractRecordSpliterator(
      XMLStreamReader reader, RecordParsingStrategy<T> strategy, XmlParsingContext context) {
    this.reader = reader;
    this.strategy = strategy;
    this.context = context;
  }

  @Override
  public boolean tryAdvance(Consumer<? super T> action) {
    if (finished) {
      return false;
    }

    try {
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT && strategy.matches(reader)) {
          // 找到匹配的元素，委托策略解析
          T record = strategy.parseRecord(reader, context);
          if (record != null) {
            action.accept(record);
            return true;
          }
          // 策略返回 null，继续查找下一个
        }
      }
      // 流已耗尽
      finished = true;
      return false;
    } catch (XMLStreamException e) {
      log.error("解析 {} 记录失败", strategy.rootElementName(), e);
      throw new XmlParsingException("XML 解析失败", e);
    }
  }

  /// 不支持并行拆分。
  ///
  /// XML 流式解析天然是顺序的，不适合并行处理。
  ///
  /// @return 始终返回 `null`
  @Override
  public final Spliterator<T> trySplit() {
    return null;
  }

  /// 估计剩余元素数量。
  ///
  /// XML 流式解析无法预知总数。
  ///
  /// @return `Long.MAX_VALUE` 表示未知
  @Override
  public final long estimateSize() {
    return Long.MAX_VALUE;
  }

  /// Spliterator 特性。
  ///
  /// @return ORDERED | NONNULL | IMMUTABLE
  @Override
  public final int characteristics() {
    return ORDERED | NONNULL | IMMUTABLE;
  }
}
