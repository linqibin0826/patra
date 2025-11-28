package com.patra.catalog.infra.adapter.parser.strategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.infra.adapter.parser.MeshXmlElements;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import java.io.StringReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// TreeNumberParsingStrategy 单元测试。
///
/// 验证树形编号解析策略的正确性。
@DisplayName("TreeNumberParsingStrategy 策略")
class TreeNumberParsingStrategyTest {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  private final TreeNumberParsingStrategy strategy = TreeNumberParsingStrategy.INSTANCE;

  // ========== 基本契约测试 ==========

  @Nested
  @DisplayName("策略契约")
  class StrategyContract {

    @Test
    @DisplayName("rootElementName() 应返回 TreeNumber")
    void rootElementName_shouldReturnTreeNumber() {
      assertEquals(MeshXmlElements.Record.TREE_NUMBER, strategy.rootElementName());
    }

    @Test
    @DisplayName("INSTANCE 应为非空单例")
    void instance_shouldBeNonNullSingleton() {
      assertNotNull(TreeNumberParsingStrategy.INSTANCE);
      assertEquals(TreeNumberParsingStrategy.INSTANCE, strategy);
    }
  }

  // ========== parseRecord 测试 ==========

  @Nested
  @DisplayName("parseRecord() 解析")
  class ParseRecord {

    @Test
    @DisplayName("应正确解析树形编号文本")
    void shouldParseTreeNumberText() throws Exception {
      var xml = "<TreeNumber>A01.001</TreeNumber>";
      var reader = createReaderAtStartElement(xml);

      MeshTreeNumber result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertNotNull(result);
      assertEquals("A01.001", result.getTreeNumber());
    }

    @Test
    @DisplayName("应正确计算层级深度")
    void shouldCalculateTreeLevel() throws Exception {
      var xml = "<TreeNumber>C04.557.337.428</TreeNumber>";
      var reader = createReaderAtStartElement(xml);

      MeshTreeNumber result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(4, result.getTreeLevel());
    }

    @Test
    @DisplayName("顶层节点层级应为 1")
    void topLevelShouldHaveLevelOne() throws Exception {
      var xml = "<TreeNumber>D03</TreeNumber>";
      var reader = createReaderAtStartElement(xml);

      MeshTreeNumber result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(1, result.getTreeLevel());
    }

    @Test
    @DisplayName("默认 isPrimary 应为 false")
    void defaultIsPrimaryShouldBeFalse() throws Exception {
      var xml = "<TreeNumber>A01.001</TreeNumber>";
      var reader = createReaderAtStartElement(xml);

      MeshTreeNumber result = strategy.parseRecord(reader, XmlParsingContext.empty());

      // 独立解析时默认 isPrimary = false
      // isPrimary 的判断由调用方（如 DescriptorParsingStrategy）在列表解析时处理
      assertFalse(result.isPrimary());
    }
  }

  // ========== 边界情况测试 ==========

  @Nested
  @DisplayName("边界情况")
  class EdgeCases {

    @Test
    @DisplayName("应正确处理最大层级深度")
    void shouldHandleMaxDepth() throws Exception {
      // 10 层深度
      var xml = "<TreeNumber>A01.001.002.003.004.005.006.007.008.009</TreeNumber>";
      var reader = createReaderAtStartElement(xml);

      MeshTreeNumber result = strategy.parseRecord(reader, XmlParsingContext.empty());

      assertEquals(10, result.getTreeLevel());
    }

    @Test
    @DisplayName("应处理所有根分类字母")
    void shouldHandleAllRootCategories() throws Exception {
      String[] categories = {
        "A01", "B01", "C01", "D01", "E01", "F01", "G01", "H01", "I01", "J01", "K01", "L01", "M01",
        "N01", "V01", "Z01"
      };

      for (String category : categories) {
        var xml = "<TreeNumber>" + category + "</TreeNumber>";
        var reader = createReaderAtStartElement(xml);

        MeshTreeNumber result = strategy.parseRecord(reader, XmlParsingContext.empty());

        assertEquals(category, result.getTreeNumber());
        assertTrue(result.getRootCategory().matches("[A-Z]"));
      }
    }
  }

  // ========== 辅助方法 ==========

  /// 创建定位到起始元素的 XMLStreamReader。
  private XMLStreamReader createReaderAtStartElement(String xml) throws XMLStreamException {
    var reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(xml));
    while (reader.hasNext()) {
      if (reader.next() == XMLStreamConstants.START_ELEMENT) {
        break;
      }
    }
    return reader;
  }
}
