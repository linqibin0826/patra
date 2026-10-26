package dev.patra.catalog.infra.parser.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/// `SecureXmlInputFactory` 单元测试：大文件里的预定义实体引用不受 JDK 的实体大小上限约束，XXE 防护不变。
///
/// @author linqibin
/// @since 0.1.0
@DisplayName("SecureXmlInputFactory 单元测试")
class SecureXmlInputFactoryTest {

  @Test
  @DisplayName("全文超过 100,000 个 &amp; 照常解析（JDK 25 默认把预定义实体引用累计到 10 万上限）")
  void should_parse_document_with_more_than_100000_predefined_entity_references() throws Exception {
    StringBuilder xml = new StringBuilder("<?xml version=\"1.0\"?><r>");
    for (int i = 0; i < 100_001; i++) {
      xml.append("<c>A &amp; B</c>");
    }
    xml.append("</r>");

    XMLEventReader reader =
        SecureXmlInputFactory.getInstance()
            .createXMLEventReader(
                new ByteArrayInputStream(xml.toString().getBytes(StandardCharsets.UTF_8)));
    long elements = 0;
    while (reader.hasNext()) {
      if (reader.nextEvent().isStartElement()) {
        elements++;
      }
    }

    assertThat(elements).isEqualTo(100_002);
  }

  @Test
  @DisplayName("DTD 里声明的实体仍不展开（防 XXE / 实体膨胀）")
  void should_still_reject_declared_entities() {
    String xml =
        "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY a \"aaaaaaaaaa\">"
            + "<!ENTITY b \"&a;&a;&a;&a;&a;&a;&a;&a;&a;&a;\">]><r>&b;</r>";

    assertThatThrownBy(
            () -> {
              XMLEventReader reader =
                  SecureXmlInputFactory.getInstance().createXMLEventReader(new StringReader(xml));
              while (reader.hasNext()) {
                reader.nextEvent();
              }
            })
        .isInstanceOf(XMLStreamException.class);
  }
}
