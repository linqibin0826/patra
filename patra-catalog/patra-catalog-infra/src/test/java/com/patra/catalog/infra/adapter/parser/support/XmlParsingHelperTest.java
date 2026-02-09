package com.patra.catalog.infra.adapter.parser.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.StringReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

  private static final XMLInputFactory XML_INPUT_FACTORY = SecureXmlInputFactory.getInstance();

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

      assertEquals(LocalDate.of(2025, 1, 5), result);
    }

    @Test
    @DisplayName("应正确解析单位数月份和日期")
    void shouldParseSingleDigitMonthAndDay() throws Exception {
      var xml = "<DateCreated><Year>2025</Year><Month>3</Month><Day>9</Day></DateCreated>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseDate(reader, "DateCreated");

      assertEquals(LocalDate.of(2025, 3, 9), result);
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

      assertEquals(LocalDate.of(2025, 12, 25), result);
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

      var result = XmlParsingHelper.parseStringList(reader, "RegistryNumberList", "RegistryNumber");

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

  // ========== parseYesNoAttributeNullable 测试 ==========

  @Nested
  @DisplayName("parseYesNoAttributeNullable() 可空 Y/N 属性解析")
  class ParseYesNoAttributeNullable {

    @Test
    @DisplayName("Y 应返回 Boolean.TRUE")
    void shouldReturnTrueForY() throws Exception {
      var xml = "<Element MedPrintYN=\"Y\"/>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseYesNoAttributeNullable(reader, "MedPrintYN");

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("N 应返回 Boolean.FALSE")
    void shouldReturnFalseForN() throws Exception {
      var xml = "<Element MedPrintYN=\"N\"/>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseYesNoAttributeNullable(reader, "MedPrintYN");

      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("属性不存在时应返回 null")
    void shouldReturnNullWhenAttributeMissing() throws Exception {
      var xml = "<Element/>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseYesNoAttributeNullable(reader, "MedPrintYN");

      assertThat(result).isNull();
    }

    @Test
    @DisplayName("应忽略大小写（y 返回 true）")
    void shouldBeCaseInsensitive() throws Exception {
      var xml = "<Element Attr=\"y\"/>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseYesNoAttributeNullable(reader, "Attr");

      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("n 应返回 false")
    void shouldReturnFalseForLowercaseN() throws Exception {
      var xml = "<Element Attr=\"n\"/>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseYesNoAttributeNullable(reader, "Attr");

      assertThat(result).isFalse();
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

  // ========== parseTimestamp 测试 ==========

  @Nested
  @DisplayName("parseTimestamp() 时间戳解析")
  class ParseTimestamp {

    @Test
    @DisplayName("应正确解析完整时间戳（年月日时分秒）")
    void shouldParseFullTimestamp() throws Exception {
      var xml =
          """
          <IlsCreatedTimestamp>
            <Year>2020</Year>
            <Month>01</Month>
            <Day>15</Day>
            <Hour>10</Hour>
            <Minute>30</Minute>
            <Second>45</Second>
          </IlsCreatedTimestamp>
          """;
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseTimestamp(reader, "IlsCreatedTimestamp");

      assertEquals(LocalDateTime.of(2020, 1, 15, 10, 30, 45), result);
    }

    @Test
    @DisplayName("应正确解析仅日期的时间戳（时分秒默认为0）")
    void shouldParseDateOnlyTimestamp() throws Exception {
      var xml =
          """
          <MedlineDataUpdatedTimestamp>
            <Year>2024</Year>
            <Month>11</Month>
            <Day>15</Day>
          </MedlineDataUpdatedTimestamp>
          """;
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseTimestamp(reader, "MedlineDataUpdatedTimestamp");

      assertEquals(LocalDateTime.of(2024, 11, 15, 0, 0, 0), result);
    }

    @Test
    @DisplayName("应正确解析部分时间（仅时分，秒默认为0）")
    void shouldParsePartialTime() throws Exception {
      var xml =
          """
          <IlsUpdatedTimestamp>
            <Year>2024</Year>
            <Month>12</Month>
            <Day>01</Day>
            <Hour>14</Hour>
            <Minute>00</Minute>
          </IlsUpdatedTimestamp>
          """;
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseTimestamp(reader, "IlsUpdatedTimestamp");

      assertEquals(LocalDateTime.of(2024, 12, 1, 14, 0, 0), result);
    }

    @Test
    @DisplayName("缺少年月日时应返回 null")
    void shouldReturnNullWhenMissingDateComponents() throws Exception {
      var xml = "<Timestamp><Hour>10</Hour></Timestamp>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseTimestamp(reader, "Timestamp");

      assertNull(result);
    }

    @Test
    @DisplayName("应正确解析单位数月日时分秒")
    void shouldParseSingleDigitValues() throws Exception {
      var xml =
          """
          <Timestamp>
            <Year>2020</Year>
            <Month>3</Month>
            <Day>5</Day>
            <Hour>9</Hour>
            <Minute>5</Minute>
            <Second>1</Second>
          </Timestamp>
          """;
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.parseTimestamp(reader, "Timestamp");

      assertEquals(LocalDateTime.of(2020, 3, 5, 9, 5, 1), result);
    }
  }

  // ========== getElementTextWithMixedContent 测试 ==========

  @Nested
  @DisplayName("getElementTextWithMixedContent() Mixed Content 文本提取")
  class GetElementTextWithMixedContent {

    @Test
    @DisplayName("纯文本（无内联标签）→ 原样返回")
    void should_return_plain_text() throws XMLStreamException {
      var xml = "<ArticleTitle>Plain text</ArticleTitle>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.getElementTextWithMixedContent(reader);

      assertThat(result).isEqualTo("Plain text");
    }

    @Test
    @DisplayName("单个内联标签 → 保留标签原样输出")
    void should_preserve_single_inline_tag() throws XMLStreamException {
      var xml = "<ArticleTitle>Role of <i>E. coli</i></ArticleTitle>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.getElementTextWithMixedContent(reader);

      assertThat(result).isEqualTo("Role of <i>E. coli</i>");
    }

    @Test
    @DisplayName("多个内联标签 → 全部保留")
    void should_preserve_multiple_inline_tags() throws XMLStreamException {
      var xml = "<ArticleTitle><b>Bold</b> and <i>italic</i></ArticleTitle>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.getElementTextWithMixedContent(reader);

      assertThat(result).isEqualTo("<b>Bold</b> and <i>italic</i>");
    }

    @Test
    @DisplayName("下标标签 → 保留 <sub> 标签")
    void should_preserve_subscript_tag() throws XMLStreamException {
      var xml = "<ArticleTitle>H<sub>2</sub>O</ArticleTitle>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.getElementTextWithMixedContent(reader);

      assertThat(result).isEqualTo("H<sub>2</sub>O");
    }

    @Test
    @DisplayName("空元素 → 返回空字符串")
    void should_return_empty_for_empty_element() throws XMLStreamException {
      var xml = "<ArticleTitle></ArticleTitle>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.getElementTextWithMixedContent(reader);

      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("调用后 reader 定位在 END_ELEMENT（与 getElementText 语义一致）")
    void should_leave_reader_at_end_element() throws XMLStreamException {
      var xml = "<ArticleTitle>Role of <i>E. coli</i></ArticleTitle>";
      var reader = createReaderAtStartElement(xml);

      XmlParsingHelper.getElementTextWithMixedContent(reader);

      assertThat(reader.getEventType()).isEqualTo(XMLStreamConstants.END_ELEMENT);
      assertThat(reader.getLocalName()).isEqualTo("ArticleTitle");
    }

    @Test
    @DisplayName("带属性的内联标签 → 保留属性")
    void should_preserve_tag_attributes() throws XMLStreamException {
      var xml = "<AbstractText>See <a href=\"http://example.com\">link</a> here</AbstractText>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.getElementTextWithMixedContent(reader);

      assertThat(result).isEqualTo("See <a href=\"http://example.com\">link</a> here");
    }

    @Test
    @DisplayName("上标标签 → 保留 <sup> 标签")
    void should_preserve_superscript_tag() throws XMLStreamException {
      var xml = "<ArticleTitle>10<sup>3</sup> cells</ArticleTitle>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.getElementTextWithMixedContent(reader);

      assertThat(result).isEqualTo("10<sup>3</sup> cells");
    }

    @Test
    @DisplayName("嵌套内联标签 → 多层嵌套全部保留")
    void should_preserve_nested_inline_tags() throws XMLStreamException {
      var xml = "<ArticleTitle><b>Role of <i>E. coli</i> in disease</b></ArticleTitle>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.getElementTextWithMixedContent(reader);

      assertThat(result).isEqualTo("<b>Role of <i>E. coli</i> in disease</b>");
    }

    @Test
    @DisplayName("属性值含特殊字符 → 正确转义")
    void should_escape_special_chars_in_attribute_value() throws XMLStreamException {
      var xml = "<AbstractText>See <a title=\"A&amp;B\">link</a> here</AbstractText>";
      var reader = createReaderAtStartElement(xml);

      var result = XmlParsingHelper.getElementTextWithMixedContent(reader);

      assertThat(result).isEqualTo("See <a title=\"A&amp;B\">link</a> here");
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
