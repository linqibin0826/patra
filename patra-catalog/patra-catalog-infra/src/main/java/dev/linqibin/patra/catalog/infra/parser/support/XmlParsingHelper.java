package dev.linqibin.patra.catalog.infra.parser.support;

import static dev.linqibin.patra.catalog.infra.parser.LsiouXmlElements.Date.DAY;
import static dev.linqibin.patra.catalog.infra.parser.LsiouXmlElements.Date.HOUR;
import static dev.linqibin.patra.catalog.infra.parser.LsiouXmlElements.Date.MINUTE;
import static dev.linqibin.patra.catalog.infra.parser.LsiouXmlElements.Date.MONTH;
import static dev.linqibin.patra.catalog.infra.parser.LsiouXmlElements.Date.SECOND;
import static dev.linqibin.patra.catalog.infra.parser.LsiouXmlElements.Date.YEAR;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/// XML 解析辅助方法工具类。
///
/// 提供 StAX XML 解析的通用辅助方法，消除策略类中的重复代码。
/// 所有方法均为静态方法，线程安全。
///
/// **使用约定**：
/// - 所有方法假定 `reader` 已定位到相应的起始元素
/// - 方法执行完毕后，`reader` 位置取决于具体方法文档
///
/// @author linqibin
/// @since 0.1.0
public final class XmlParsingHelper {

  private XmlParsingHelper() {
    throw new UnsupportedOperationException("工具类禁止实例化");
  }

  /// 解析日期元素（Year/Month/Day 结构）。
  ///
  /// 支持 MeSH XML 中标准的日期格式：
  /// ```xml
  /// <DateCreated>
  ///   <Year>2025</Year>
  ///   <Month>1</Month>
  ///   <Day>5</Day>
  /// </DateCreated>
  /// ```
  ///
  /// @param reader XML 读取器（已定位到日期元素）
  /// @param elementName 日期元素名称（如 "DateCreated"）
  /// @return 解析后的 LocalDate，缺少任何组件时返回 `null`
  /// @throws XMLStreamException XML 解析异常
  public static LocalDate parseDate(XMLStreamReader reader, String elementName)
      throws XMLStreamException {
    String year = null;
    String month = null;
    String day = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case YEAR -> year = reader.getElementText();
          case MONTH -> month = reader.getElementText();
          case DAY -> day = reader.getElementText();
          default -> {
            /* ignore other elements */
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && elementName.equals(reader.getLocalName())) {
        break;
      }
    }

    // 构造 LocalDate 对象
    if (year != null && month != null && day != null) {
      return LocalDate.of(Integer.parseInt(year), Integer.parseInt(month), Integer.parseInt(day));
    }
    return null;
  }

  /// 解析 Name 元素（包含 String 子元素）。
  ///
  /// MeSH 中名称元素的标准结构：
  /// ```xml
  /// <DescriptorName>
  ///   <String>Abdomen</String>
  /// </DescriptorName>
  /// ```
  ///
  /// @param reader XML 读取器（已定位到名称元素）
  /// @return String 子元素的文本内容，不存在时返回 `null`
  /// @throws XMLStreamException XML 解析异常
  public static String parseNameElement(XMLStreamReader reader) throws XMLStreamException {
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT && "String".equals(reader.getLocalName())) {
        return reader.getElementText();
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        // 遇到任何结束标签即退出
        break;
      }
    }
    return null;
  }

  /// 跳过整个 XML 元素（包括所有子元素）。
  ///
  /// 使用深度计数器正确处理嵌套元素。
  ///
  /// @param reader XML 读取器（已定位到要跳过的元素）
  /// @param elementName 要跳过的元素名称
  /// @throws XMLStreamException XML 解析异常
  public static void skipElement(XMLStreamReader reader, String elementName)
      throws XMLStreamException {
    int depth = 1; // 当前嵌套深度
    while (reader.hasNext() && depth > 0) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        depth++;
      } else if (event == XMLStreamConstants.END_ELEMENT) {
        depth--;
        if (depth == 0 && elementName.equals(reader.getLocalName())) {
          break;
        }
      }
    }
  }

  /// 解析字符串列表元素。
  ///
  /// 通用方法，适用于各种列表结构：
  /// - RegistryNumberList / RegistryNumber
  /// - ThesaurusIDlist / ThesaurusID
  /// - PreviousIndexingList / PreviousIndexing
  /// - RelatedRegistryNumberList / RelatedRegistryNumber
  ///
  /// @param reader XML 读取器（已定位到列表元素）
  /// @param listElementName 列表元素名称（如 "RegistryNumberList"）
  /// @param itemElementName 列表项元素名称（如 "RegistryNumber"）
  /// @return 字符串列表（自动去除空白项）
  /// @throws XMLStreamException XML 解析异常
  public static List<String> parseStringList(
      XMLStreamReader reader, String listElementName, String itemElementName)
      throws XMLStreamException {
    List<String> items = new ArrayList<>();
    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT
          && itemElementName.equals(reader.getLocalName())) {
        String text = reader.getElementText();
        if (text != null && !text.trim().isEmpty()) {
          items.add(text.trim());
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && listElementName.equals(reader.getLocalName())) {
        break;
      }
    }
    return items;
  }

  /// 解析 Y/N 布尔属性。
  ///
  /// MeSH XML 中常用的布尔属性格式：
  /// - `PreferredConceptYN="Y"` → true
  /// - `IsPermutedTermYN="N"` → false
  ///
  /// @param reader XML 读取器（已定位到包含属性的元素）
  /// @param attributeName 属性名称
  /// @param defaultValue 属性不存在时的默认值
  /// @return 布尔值（Y/y → true，其他 → false）
  public static boolean parseYesNoAttribute(
      XMLStreamReader reader, String attributeName, boolean defaultValue) {
    String value = reader.getAttributeValue(null, attributeName);
    if (value == null) {
      return defaultValue;
    }
    return "Y".equalsIgnoreCase(value);
  }

  /// 解析 Y/N 属性为可空 Boolean。
  ///
  /// @param reader XML 读取器（已定位到包含属性的元素）
  /// @param attributeName 属性名称
  /// @return Y → true，N → false，null → null
  public static Boolean parseYesNoAttributeNullable(XMLStreamReader reader, String attributeName) {
    String value = reader.getAttributeValue(null, attributeName);
    if (value == null) {
      return null;
    }
    return "Y".equalsIgnoreCase(value);
  }

  /// 获取属性值或默认值。
  ///
  /// @param reader XML 读取器（已定位到包含属性的元素）
  /// @param attributeName 属性名称
  /// @param defaultValue 属性不存在时的默认值
  /// @return 属性值，不存在时返回默认值
  public static String getAttributeOrDefault(
      XMLStreamReader reader, String attributeName, String defaultValue) {
    String value = reader.getAttributeValue(null, attributeName);
    return value != null ? value : defaultValue;
  }

  /// 解析时间戳元素（Year/Month/Day/Hour/Minute/Second 结构）。
  ///
  /// 支持 NLM Serfile XML 中的时间戳格式：
  /// ```xml
  /// <IlsCreatedTimestamp>
  ///   <Year>2020</Year>
  ///   <Month>01</Month>
  ///   <Day>15</Day>
  ///   <Hour>10</Hour>
  ///   <Minute>30</Minute>
  ///   <Second>45</Second>
  /// </IlsCreatedTimestamp>
  /// ```
  ///
  /// 时分秒为可选组件，缺少时默认为 0。
  ///
  /// @param reader XML 读取器（已定位到时间戳元素）
  /// @param elementName 时间戳元素名称（如 "IlsCreatedTimestamp"）
  /// @return 解析后的 LocalDateTime，缺少年/月/日时返回 `null`
  /// @throws XMLStreamException XML 解析异常
  public static LocalDateTime parseTimestamp(XMLStreamReader reader, String elementName)
      throws XMLStreamException {
    String year = null;
    String month = null;
    String day = null;
    int hour = 0;
    int minute = 0;
    int second = 0;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        switch (localName) {
          case YEAR -> year = reader.getElementText();
          case MONTH -> month = reader.getElementText();
          case DAY -> day = reader.getElementText();
          case HOUR -> hour = parseIntSafe(reader.getElementText(), 0);
          case MINUTE -> minute = parseIntSafe(reader.getElementText(), 0);
          case SECOND -> second = parseIntSafe(reader.getElementText(), 0);
          default -> {
            /* ignore other elements */
          }
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && elementName.equals(reader.getLocalName())) {
        break;
      }
    }

    // 年月日为必需，时分秒可选（默认 0）
    if (year != null && month != null && day != null) {
      return LocalDateTime.of(
          Integer.parseInt(year.trim()),
          Integer.parseInt(month.trim()),
          Integer.parseInt(day.trim()),
          hour,
          minute,
          second);
    }
    return null;
  }

  /// 提取元素文本内容（支持 mixed content 内联标签）。
  ///
  /// 与 `XMLStreamReader.getElementText()` 的区别：
  /// - 遇到嵌套标签不会抛异常，而是保留标签原样输出
  /// - 例如 `<ArticleTitle>Role of <i>E. coli</i></ArticleTitle>` → `"Role of <i>E. coli</i>"`
  ///
  /// **语义契约**（与 `getElementText()` 一致）：
  /// - 前置条件：`reader` 定位在 `START_ELEMENT`
  /// - 后置条件：`reader` 定位在对应的 `END_ELEMENT`
  ///
  /// @param reader XML 读取器（已定位到起始元素）
  /// @return 元素的文本内容（内联标签保留原样）
  /// @throws XMLStreamException XML 解析异常
  public static String getElementTextWithMixedContent(XMLStreamReader reader)
      throws XMLStreamException {
    var sb = new StringBuilder(256);
    int depth = 1;

    while (reader.hasNext()) {
      int event = reader.next();
      switch (event) {
        case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE ->
            sb.append(reader.getText());
        case XMLStreamConstants.ENTITY_REFERENCE ->
            sb.append('&').append(reader.getLocalName()).append(';');
        case XMLStreamConstants.START_ELEMENT -> {
          depth++;
          sb.append('<').append(reader.getLocalName());
          for (int i = 0; i < reader.getAttributeCount(); i++) {
            sb.append(' ')
                .append(reader.getAttributeLocalName(i))
                .append("=\"")
                .append(escapeXmlAttribute(reader.getAttributeValue(i)))
                .append('"');
          }
          sb.append('>');
        }
        case XMLStreamConstants.END_ELEMENT -> {
          depth--;
          if (depth == 0) {
            return sb.toString();
          }
          sb.append("</").append(reader.getLocalName()).append('>');
        }
        default -> {
          // 忽略 COMMENT、PROCESSING_INSTRUCTION 等事件
        }
      }
    }
    // 不应到达此处（意味着 XML 格式错误）
    return sb.toString();
  }

  /// 转义 XML 属性值中的特殊字符。
  ///
  /// @param value 属性值
  /// @return 转义后的字符串
  private static String escapeXmlAttribute(String value) {
    if (value == null) {
      return "";
    }
    // 快速路径：大部分属性值无需转义
    boolean needsEscape = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (c == '&' || c == '"' || c == '<' || c == '>') {
        needsEscape = true;
        break;
      }
    }
    if (!needsEscape) {
      return value;
    }
    var sb = new StringBuilder(value.length() + 8);
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '&' -> sb.append("&amp;");
        case '"' -> sb.append("&quot;");
        case '<' -> sb.append("&lt;");
        case '>' -> sb.append("&gt;");
        default -> sb.append(c);
      }
    }
    return sb.toString();
  }

  /// 安全解析整数，解析失败时返回默认值。
  ///
  /// @param value 要解析的字符串
  /// @param defaultValue 解析失败时的默认值
  /// @return 解析后的整数值，失败时返回默认值
  private static int parseIntSafe(String value, int defaultValue) {
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }
}
