package com.patra.catalog.infra.adapter.parser.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.patra.catalog.infra.adapter.parser.strategy.RecordParsingStrategy;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Spliterator;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// AbstractRecordSpliterator 基类单元测试。
///
/// 验证 Spliterator 的基本行为和策略委托。
@DisplayName("AbstractRecordSpliterator 基类")
class AbstractRecordSpliteratorTest {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  // ========== Spliterator 特性测试 ==========

  @Nested
  @DisplayName("Spliterator 特性")
  class SpliteratorCharacteristics {

    @Test
    @DisplayName("trySplit() 应始终返回 null（不支持并行）")
    void trySplit_shouldAlwaysReturnNull() throws Exception {
      var spliterator = createTestSpliterator("<Root><Record>value</Record></Root>");
      assertNull(spliterator.trySplit());
    }

    @Test
    @DisplayName("estimateSize() 应返回 Long.MAX_VALUE（未知大小）")
    void estimateSize_shouldReturnMaxValue() throws Exception {
      var spliterator = createTestSpliterator("<Root><Record>value</Record></Root>");
      assertEquals(Long.MAX_VALUE, spliterator.estimateSize());
    }

    @Test
    @DisplayName("characteristics() 应包含 ORDERED")
    void characteristics_shouldContainOrdered() throws Exception {
      var spliterator = createTestSpliterator("<Root><Record>value</Record></Root>");
      assertTrue(spliterator.hasCharacteristics(Spliterator.ORDERED));
    }

    @Test
    @DisplayName("characteristics() 应包含 NONNULL")
    void characteristics_shouldContainNonnull() throws Exception {
      var spliterator = createTestSpliterator("<Root><Record>value</Record></Root>");
      assertTrue(spliterator.hasCharacteristics(Spliterator.NONNULL));
    }

    @Test
    @DisplayName("characteristics() 应包含 IMMUTABLE")
    void characteristics_shouldContainImmutable() throws Exception {
      var spliterator = createTestSpliterator("<Root><Record>value</Record></Root>");
      assertTrue(spliterator.hasCharacteristics(Spliterator.IMMUTABLE));
    }
  }

  // ========== tryAdvance 测试 ==========

  @Nested
  @DisplayName("tryAdvance() 行为")
  class TryAdvance {

    @Test
    @DisplayName("应委托策略解析记录")
    void shouldDelegateToStrategy() throws Exception {
      var xml = "<Root><Record>value</Record></Root>";
      var spliterator = createTestSpliterator(xml);

      var results = new ArrayList<String>();
      boolean advanced = spliterator.tryAdvance(results::add);

      assertTrue(advanced);
      assertThat(results).containsExactly("parsed:value");
    }

    @Test
    @DisplayName("应解析多条记录")
    void shouldParseMultipleRecords() throws Exception {
      var xml = "<Root><Record>A</Record><Record>B</Record><Record>C</Record></Root>";
      var spliterator = createTestSpliterator(xml);

      var results = new ArrayList<String>();
      while (spliterator.tryAdvance(results::add)) {
        // 继续迭代
      }

      assertThat(results).containsExactly("parsed:A", "parsed:B", "parsed:C");
    }

    @Test
    @DisplayName("无匹配记录时应返回 false")
    void shouldReturnFalseWhenNoMatchingRecords() throws Exception {
      var xml = "<Root><Other>value</Other></Root>";
      var spliterator = createTestSpliterator(xml);

      var results = new ArrayList<String>();
      boolean advanced = spliterator.tryAdvance(results::add);

      assertFalse(advanced);
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("空 XML 应立即返回 false")
    void shouldReturnFalseForEmptyXml() throws Exception {
      var xml = "<Root></Root>";
      var spliterator = createTestSpliterator(xml);

      var results = new ArrayList<String>();
      boolean advanced = spliterator.tryAdvance(results::add);

      assertFalse(advanced);
      assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("解析完毕后继续调用应返回 false")
    void shouldReturnFalseAfterExhausted() throws Exception {
      var xml = "<Root><Record>only</Record></Root>";
      var spliterator = createTestSpliterator(xml);

      var results = new ArrayList<String>();
      spliterator.tryAdvance(results::add);

      boolean secondAdvance = spliterator.tryAdvance(results::add);
      assertFalse(secondAdvance);
      assertThat(results).hasSize(1);
    }

    @Test
    @DisplayName("策略返回 null 时应跳过该记录")
    void shouldSkipWhenStrategyReturnsNull() throws Exception {
      var xml = "<Root><Record>skip</Record><Record>keep</Record></Root>";
      var strategy = new SkippingStrategy();
      var spliterator = createSpliteratorWithStrategy(xml, strategy);

      var results = new ArrayList<String>();
      while (spliterator.tryAdvance(results::add)) {
        // 继续迭代
      }

      // "skip" 应被跳过，只保留 "keep"
      assertThat(results).containsExactly("parsed:keep");
    }
  }

  // ========== 辅助方法和测试策略 ==========

  /// 创建使用默认策略的测试 Spliterator。
  private TestSpliterator createTestSpliterator(String xml) throws XMLStreamException {
    var reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(xml));
    return new TestSpliterator(reader, new SimpleStrategy(), XmlParsingContext.empty());
  }

  /// 创建使用指定策略的 Spliterator。
  private TestSpliterator createSpliteratorWithStrategy(
      String xml, RecordParsingStrategy<String> strategy) throws XMLStreamException {
    var reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(xml));
    return new TestSpliterator(reader, strategy, XmlParsingContext.empty());
  }

  /// 测试用 Spliterator 实现（继承抽象基类）。
  static class TestSpliterator extends AbstractRecordSpliterator<String> {

    public TestSpliterator(
        XMLStreamReader reader,
        RecordParsingStrategy<String> strategy,
        XmlParsingContext context) {
      super(reader, strategy, context);
    }
  }

  /// 简单测试策略：返回 "parsed:" + 元素文本。
  static class SimpleStrategy implements RecordParsingStrategy<String> {

    @Override
    public String rootElementName() {
      return "Record";
    }

    @Override
    public String parseRecord(XMLStreamReader reader, XmlParsingContext context)
        throws XMLStreamException {
      String text = reader.getElementText();
      return "parsed:" + text;
    }
  }

  /// 跳过特定内容的策略：内容为 "skip" 时返回 null。
  static class SkippingStrategy implements RecordParsingStrategy<String> {

    @Override
    public String rootElementName() {
      return "Record";
    }

    @Override
    public String parseRecord(XMLStreamReader reader, XmlParsingContext context)
        throws XMLStreamException {
      String text = reader.getElementText();
      if ("skip".equals(text)) {
        return null; // 返回 null 表示跳过此记录
      }
      return "parsed:" + text;
    }
  }
}
