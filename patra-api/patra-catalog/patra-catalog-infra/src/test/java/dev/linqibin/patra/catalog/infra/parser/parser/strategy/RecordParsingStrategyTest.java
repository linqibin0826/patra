package dev.linqibin.patra.catalog.infra.parser.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.linqibin.patra.catalog.infra.parser.support.XmlParsingContext;
import java.io.StringReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// RecordParsingStrategy 接口单元测试。
///
/// 验证策略接口的默认行为和契约。
@DisplayName("RecordParsingStrategy 策略接口")
class RecordParsingStrategyTest {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  @Nested
  @DisplayName("matches() 默认实现")
  class MatchesDefaultMethod {

    @Test
    @DisplayName("当元素名称匹配时应返回 true")
    void shouldReturnTrueWhenElementNameMatches() throws Exception {
      var strategy = new TestStrategy("TestRecord");
      var reader = createReaderAtStartElement("<TestRecord/>");

      assertTrue(strategy.matches(reader));
    }

    @Test
    @DisplayName("当元素名称不匹配时应返回 false")
    void shouldReturnFalseWhenElementNameDiffers() throws Exception {
      var strategy = new TestStrategy("TestRecord");
      var reader = createReaderAtStartElement("<OtherRecord/>");

      assertFalse(strategy.matches(reader));
    }

    @Test
    @DisplayName("匹配应区分大小写")
    void shouldBeCaseSensitive() throws Exception {
      var strategy = new TestStrategy("TestRecord");
      var reader = createReaderAtStartElement("<testrecord/>");

      assertFalse(strategy.matches(reader));
    }
  }

  @Nested
  @DisplayName("rootElementName() 契约")
  class RootElementNameContract {

    @Test
    @DisplayName("应返回配置的根元素名称")
    void shouldReturnConfiguredRootElementName() {
      var strategy = new TestStrategy("DescriptorRecord");
      assertEquals("DescriptorRecord", strategy.rootElementName());
    }
  }

  // ========== 辅助方法 ==========

  /// 创建定位到起始元素的 XMLStreamReader。
  private XMLStreamReader createReaderAtStartElement(String xml) throws XMLStreamException {
    var reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(xml));
    // 移动到第一个 START_ELEMENT
    while (reader.hasNext()) {
      if (reader.next() == XMLStreamReader.START_ELEMENT) {
        break;
      }
    }
    return reader;
  }

  // ========== 测试用策略实现 ==========

  /// 测试用简单策略实现。
  static class TestStrategy implements RecordParsingStrategy<String> {

    private final String rootElement;

    TestStrategy(String rootElement) {
      this.rootElement = rootElement;
    }

    @Override
    public String rootElementName() {
      return rootElement;
    }

    @Override
    public String parseRecord(XMLStreamReader reader, XmlParsingContext context)
        throws XMLStreamException {
      return "test-result";
    }
  }
}
