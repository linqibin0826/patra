package com.patra.catalog.infra.parser.strategy;

import com.patra.catalog.infra.parser.support.XmlParsingContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/// XML 记录解析策略接口。
///
/// 定义单种 XML 记录类型的解析契约，将解析逻辑与流化逻辑分离。
/// 实现类应为**无状态单例**，通过 `INSTANCE` 常量访问。
///
/// **设计原则**：
/// - 每个策略负责一种记录类型（如 DescriptorRecord、QualifierRecord）
/// - 策略无状态，线程安全
/// - 解析方法接收 reader 已定位到根元素的 START_ELEMENT
///
/// **扩展示例**：
/// ```java
/// public class DescriptorParsingStrategy implements RecordParsingStrategy<MeshDescriptor> {
///     public static final DescriptorParsingStrategy INSTANCE = new DescriptorParsingStrategy();
///
///     @Override
///     public String rootElementName() { return "DescriptorRecord"; }
///
///     @Override
///     public MeshDescriptor parseRecord(XMLStreamReader reader, XmlParsingContext context) {
///         // 解析逻辑
///     }
/// }
/// ```
///
/// @param <T> 解析结果类型（领域对象）
/// @author linqibin
/// @since 0.1.0
public interface RecordParsingStrategy<T> {

  /// 获取根元素名称。
  ///
  /// @return 根元素名称（如 "DescriptorRecord"、"QualifierRecord"）
  String rootElementName();

  /// 解析单条记录。
  ///
  /// **前置条件**：调用时 `reader` 已定位到根元素的 `START_ELEMENT`。
  ///
  /// **后置条件**：返回时应消费完整个根元素（包括 `END_ELEMENT`）。
  ///
  /// @param reader XML 流读取器（已定位到根元素）
  /// @param context 解析上下文（包含版本号等信息）
  /// @return 解析结果，无效记录返回 `null`（由调用方跳过）
  /// @throws XMLStreamException XML 解析异常
  T parseRecord(XMLStreamReader reader, XmlParsingContext context) throws XMLStreamException;

  /// 检查当前元素是否匹配此策略。
  ///
  /// 默认实现比较当前元素的本地名称与 `rootElementName()`。
  /// 子类可覆盖以实现更复杂的匹配逻辑。
  ///
  /// @param reader XML 流读取器（应处于 START_ELEMENT）
  /// @return 如果当前元素匹配此策略返回 `true`
  default boolean matches(XMLStreamReader reader) {
    return rootElementName().equals(reader.getLocalName());
  }
}
