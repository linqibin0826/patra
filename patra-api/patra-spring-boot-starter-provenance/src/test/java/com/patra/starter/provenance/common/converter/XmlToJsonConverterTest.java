package com.patra.starter.provenance.common.converter;

import static org.assertj.core.api.Assertions.*;

import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// XmlToJsonConverter 单元测试
///
/// @author linqibin
@DisplayName("XmlToJsonConverter 测试")
class XmlToJsonConverterTest {

  private XmlToJsonConverter converter;

  @BeforeEach
  void setUp() {
    converter = new XmlToJsonConverter();
  }

  @Test
  @DisplayName("convert - 简单XML转换为对象")
  void convert_shouldParseSimpleXml_toObject() {
    // Arrange
    String xml =
        """
        <Person>
          <name>John Doe</name>
          <age>30</age>
          <email>john@example.com</email>
        </Person>
        """;

    // Act
    TestPerson result = converter.convert(xml, TestPerson.class);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.name).isEqualTo("John Doe");
    assertThat(result.age).isEqualTo(30);
    assertThat(result.email).isEqualTo("john@example.com");
  }

  @Test
  @DisplayName("convert - 嵌套XML转换")
  void convert_shouldParseNestedXml_toObject() {
    // Arrange
    String xml =
        """
        <Document>
          <title>Test Document</title>
          <author>
            <name>Jane Smith</name>
            <affiliation>University</affiliation>
          </author>
        </Document>
        """;

    // Act
    TestDocument result = converter.convert(xml, TestDocument.class);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.title).isEqualTo("Test Document");
    assertThat(result.author).isNotNull();
    assertThat(result.author.name).isEqualTo("Jane Smith");
    assertThat(result.author.affiliation).isEqualTo("University");
  }

  @Test
  @DisplayName("convert - 包含列表的XML转换")
  void convert_shouldParseXmlWithList_toObject() {
    // Arrange
    String xml =
        """
        <Library>
          <name>City Library</name>
          <books>
            <book>Book 1</book>
            <book>Book 2</book>
            <book>Book 3</book>
          </books>
        </Library>
        """;

    // Act
    TestLibrary result = converter.convert(xml, TestLibrary.class);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.name).isEqualTo("City Library");
    assertThat(result.books).isNotNull();
    assertThat(result.books.book).hasSize(3);
    assertThat(result.books.book).containsExactly("Book 1", "Book 2", "Book 3");
  }

  @Test
  @DisplayName("convert - 单个元素作为数组处理")
  void convert_shouldAcceptSingleValueAsArray() {
    // Arrange
    String xml =
        """
        <Library>
          <name>Small Library</name>
          <books>
            <book>Only Book</book>
          </books>
        </Library>
        """;

    // Act
    TestLibrary result = converter.convert(xml, TestLibrary.class);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.books.book).hasSize(1);
    assertThat(result.books.book).containsExactly("Only Book");
  }

  @Test
  @DisplayName("convert - 忽略未知属性")
  void convert_shouldIgnoreUnknownProperties() {
    // Arrange
    String xml =
        """
        <Person>
          <name>John</name>
          <age>30</age>
          <unknownField>should be ignored</unknownField>
          <anotherUnknownField>also ignored</anotherUnknownField>
        </Person>
        """;

    // Act & Assert - 不应抛出异常
    assertThatCode(() -> converter.convert(xml, TestPerson.class)).doesNotThrowAnyException();

    TestPerson result = converter.convert(xml, TestPerson.class);
    assertThat(result.name).isEqualTo("John");
    assertThat(result.age).isEqualTo(30);
  }

  @Test
  @DisplayName("convert - 空XML抛出异常")
  void convert_shouldThrowException_whenXmlIsEmpty() {
    // Act & Assert
    assertThatThrownBy(() -> converter.convert("", TestPerson.class))
        .isInstanceOf(ProvenanceClientException.class)
        .hasMessageContaining("XML payload is empty");
  }

  @Test
  @DisplayName("convert - null XML抛出异常")
  void convert_shouldThrowException_whenXmlIsNull() {
    // Act & Assert
    assertThatThrownBy(() -> converter.convert(null, TestPerson.class))
        .isInstanceOf(ProvenanceClientException.class)
        .hasMessageContaining("XML payload is empty");
  }

  @Test
  @DisplayName("convert - 无效XML抛出异常")
  void convert_shouldThrowException_whenXmlIsInvalid() {
    // Arrange
    String invalidXml = "<Person><name>John</name><unclosed>";

    // Act & Assert
    assertThatThrownBy(() -> converter.convert(invalidXml, TestPerson.class))
        .isInstanceOf(ProvenanceClientException.class)
        .hasMessageContaining("Failed to convert XML to JSON");
  }

  @Test
  @DisplayName("convert - 格式错误的XML抛出异常")
  void convert_shouldThrowException_whenXmlIsMalformed() {
    // Arrange
    String malformedXml = "not xml at all { json: 'data' }";

    // Act & Assert
    assertThatThrownBy(() -> converter.convert(malformedXml, TestPerson.class))
        .isInstanceOf(ProvenanceClientException.class)
        .hasMessageContaining("Failed to convert XML to JSON");
  }

  @Test
  @DisplayName("convert - null responseClass抛出异常")
  void convert_shouldThrowException_whenResponseClassIsNull() {
    // Arrange
    String xml = "<Person><name>John</name></Person>";

    // Act & Assert
    assertThatThrownBy(() -> converter.convert(xml, null))
        .isInstanceOf(NullPointerException.class)
        .hasMessageContaining("responseClass cannot be null");
  }

  @Test
  @DisplayName("convert - 包含特殊字符的XML正确解析")
  void convert_shouldParseXmlWithSpecialCharacters() {
    // Arrange
    String xml =
        """
        <Person>
          <name>John &amp; Jane</name>
          <age>30</age>
          <email>test@example.com</email>
        </Person>
        """;

    // Act
    TestPerson result = converter.convert(xml, TestPerson.class);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.name).isEqualTo("John & Jane");
  }

  @Test
  @DisplayName("convert - 包含CDATA的XML正确解析")
  void convert_shouldParseXmlWithCDATA() {
    // Arrange
    String xml =
        """
        <Person>
          <name><![CDATA[John <Doe>]]></name>
          <age>30</age>
        </Person>
        """;

    // Act
    TestPerson result = converter.convert(xml, TestPerson.class);

    // Assert
    assertThat(result).isNotNull();
    assertThat(result.name).isEqualTo("John <Doe>");
  }

  // 测试用辅助类
  static class TestPerson {
    public String name;
    public int age;
    public String email;
  }

  static class TestDocument {
    public String title;
    public TestAuthor author;
  }

  static class TestAuthor {
    public String name;
    public String affiliation;
  }

  static class TestLibrary {
    public String name;
    public TestBooks books;
  }

  static class TestBooks {
    public java.util.List<String> book;
  }
}
