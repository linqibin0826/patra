package com.patra.catalog.infra.adapter.parser.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/// XmlParsingHelper 辅助方法单元测试。
///
/// 验证各种 XML 解析辅助方法的正确性。
@DisplayName("XmlParsingHelper 辅助方法")
class XmlParsingHelperTest {

  private static final XMLInputFactory XML_INPUT_FACTORY = XMLInputFactory.newInstance();

  // ========== parseDate 测试 ==========

  @Nested
  @DisplayName("parseDate() 日期解析")
  class ParseDate {

    @Test
    @DisplayName("应正确解析完整日期")
    void shouldParseValidDate() throws Exception {
      var xml = "<DateCreated><Year>2025</Year><Month>1</Month><Day>5</Day></DateCreated>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseDate(reader, "DateCreated");

      assertEquals("20250105", result);
    }

    @Test
    @DisplayName("应补零单位数月份和日期")
    void shouldPadSingleDigitMonthAndDay() throws Exception {
      var xml = "<DateCreated><Year>2025</Year><Month>3</Month><Day>9</Day></DateCreated>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseDate(reader, "DateCreated");

      assertEquals("20250309", result);
    }

    @Test
    @DisplayName("缺少组件时应返回 null")
    void shouldReturnNullWhenMissingComponents() throws Exception {
      var xml = "<DateCreated><Year>2025</Year></DateCreated>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseDate(reader, "DateCreated");

      assertNull(result);
    }

    @Test
    @DisplayName("应支持两位数月份和日期")
    void shouldSupportTwoDigitMonthAndDay() throws Exception {
      var xml = "<DateCreated><Year>2025</Year><Month>12</Month><Day>25</Day></DateCreated>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseDate(reader, "DateCreated");

      assertEquals("20251225", result);
    }
  }

  // ========== parseNameElement 测试 ==========

  @Nested
  @DisplayName("parseNameElement() 名称元素解析")
  class ParseNameElement {

    @Test
    @DisplayName("应提取 String 子元素内容")
    void shouldExtractStringContent() throws Exception {
      var xml = "<DescriptorName><String>Abdomen</String></DescriptorName>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseNameElement(reader);

      assertEquals("Abdomen", result);
    }

    @Test
    @DisplayName("无 String 子元素时应返回 null")
    void shouldReturnNullWhenNoStringElement() throws Exception {
      var xml = "<DescriptorName></DescriptorName>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseNameElement(reader);

      assertNull(result);
    }
  }

  // ========== skipElement 测试 ==========

  @Nested
  @DisplayName("skipElement() 跳过元素")
  class SkipElement {

    @Test
    @DisplayName("应跳过整个元素及其子元素")
    void shouldSkipEntireElement() throws Exception {
      var xml = "<Root><Parent><Child>text</Child></Parent><Next/></Root>";
      var reader = createReaderAtStartElement(xml);

      // 移动到 Parent 元素
      while (reader.hasNext()) {
        reader.next();
        if (reader.getEventType() == XMLStreamConstants.START_ELEMENT
            && "Parent".equals(reader.getLocalName())) {
          break;
        }
      }

      XmlParsingHelper.skipElement(reader, "Parent");

      // 移动到下一个元素
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          assertEquals("Next", reader.getLocalName());
          return;
        }
      }
    }

    @Test
    @DisplayName("应正确处理嵌套元素")
    void shouldHandleNestedElements() throws Exception {
      var xml = "<Root><Parent><A><B>text</B></A><C/></Parent><After/></Root>";
      var reader = createReaderAtStartElement(xml);

      // 移动到 Parent 元素
      while (reader.hasNext()) {
        reader.next();
        if (reader.getEventType() == XMLStreamConstants.START_ELEMENT
            && "Parent".equals(reader.getLocalName())) {
          break;
        }
      }

      XmlParsingHelper.skipElement(reader, "Parent");

      // 验证下一个元素是 After
      while (reader.hasNext()) {
        int event = reader.next();
        if (event == XMLStreamConstants.START_ELEMENT) {
          assertEquals("After", reader.getLocalName());
          return;
        }
      }
    }
  }

  // ========== parseStringList 测试 ==========

  @Nested
  @DisplayName("parseStringList() 字符串列表解析")
  class ParseStringList {

    @Test
    @DisplayName("应解析 RegistryNumberList")
    void shouldParseRegistryNumberList() throws Exception {
      var xml =
          """
          <RegistryNumberList>
            <RegistryNumber>RN1</RegistryNumber>
            <RegistryNumber>RN2</RegistryNumber>
          </RegistryNumberList>
          """;
      var reader = createReaderAtStartElement(xml);

      var result =
          XmlParsingHelper.parseStringList(reader, "RegistryNumberList", "RegistryNumber");

      assertThat(result).containsExactly("RN1", "RN2");
    }

    @Test
    @DisplayName("应解析 ThesaurusIDlist")
    void shouldParseThesaurusIdList() throws Exception {
      var xml =
          """
          <ThesaurusIDlist>
            <ThesaurusID>ID1</ThesaurusID>
            <ThesaurusID>ID2</ThesaurusID>
            <ThesaurusID>ID3</ThesaurusID>
          </ThesaurusIDlist>
          """;
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseStringList(reader, "ThesaurusIDlist", "ThesaurusID");

      assertThat(result).containsExactly("ID1", "ID2", "ID3");
    }

    @Test
    @DisplayName("应跳过空白条目")
    void shouldSkipEmptyEntries() throws Exception {
      var xml =
          """
          <List>
            <Item>value1</Item>
            <Item>  </Item>
            <Item>value2</Item>
          </List>
          """;
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseStringList(reader, "List", "Item");

      assertThat(result).containsExactly("value1", "value2");
    }

    @Test
    @DisplayName("空列表应返回空集合")
    void shouldReturnEmptyListForEmptyElement() throws Exception {
      var xml = "<List></List>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseStringList(reader, "List", "Item");

      assertThat(result).isEmpty();
    }
  }

  // ========== parseYesNoAttribute 测试 ==========

  @Nested
  @DisplayName("parseYesNoAttribute() Y/N 属性解析")
  class ParseYesNoAttribute {

    @Test
    @DisplayName("Y 应返回 true")
    void shouldReturnTrueForY() throws Exception {
      var xml = "<Element PreferredConceptYN=\"Y\"/>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseYesNoAttribute(reader, "PreferredConceptYN", false);

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("N 应返回 false")
    void shouldReturnFalseForN() throws Exception {
      var xml = "<Element IsPermutedTermYN=\"N\"/>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseYesNoAttribute(reader, "IsPermutedTermYN", true);

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("属性不存在时应返回默认值")
    void shouldReturnDefaultWhenAttributeMissing() throws Exception {
      var xml = "<Element/>";
      var reader = createReaderAtStartElement(xml);

      var resultTrue = XmlParsingHelper.parseYesNoAttribute(reader, "SomeAttr", true);
      assertThat(resultTrue).isTrue();

      reader = createReaderAtStartElement(xml);
      var resultFalse = XmlParsingHelper.parseYesNoAttribute(reader, "SomeAttr", false);
      assertThat(resultFalse).isFalse();
    }

    @Test
    @DisplayName("应忽略大小写")
    void shouldBeCaseInsensitive() throws Exception {
      var xml = "<Element Attr=\"y\"/>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseYesNoAttribute(reader, "Attr", false);

      assertThat(result).isTrue();
    }
  }

  // ========== getAttributeOrDefault 测试 ==========

  @Nested
  @DisplayName("getAttributeOrDefault() 属性获取")
  class GetAttributeOrDefault {

    @Test
    @DisplayName("属性存在时应返回属性值")
    void shouldReturnAttributeValueWhenPresent() throws Exception {
      var xml = "<Element LexicalTag=\"ABB\"/>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.getAttributeOrDefault(reader, "LexicalTag", "NON");

      assertEquals("ABB", result);
    }

    @Test
    @DisplayName("属性不存在时应返回默认值")
    void shouldReturnDefaultWhenAttributeMissing() throws Exception {
      var xml = "<Element/>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.getAttributeOrDefault(reader, "LexicalTag", "NON");

      assertEquals("NON", result);
    }
  }

  // ========== 辅助方法 ==========

  /// 创建定位到起始元素的 XMLStreamReader。
  private XMLStreamReader createReaderAtStartElement(String xml) throws XMLStreamException {
    var reader = XML_INPUT_FACTORY.createXMLStreamReader(new StringReader(xml));
    // 移动到第一个 START_ELEMENT
    while (reader.hasNext()) {
      if (reader.next() == XMLStreamConstants.START_ELEMENT) {
        break;
      }
    }
    return reader;
  }
}
