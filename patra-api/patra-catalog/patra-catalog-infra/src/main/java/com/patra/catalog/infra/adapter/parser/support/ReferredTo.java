package com.patra.catalog.infra.adapter.parser.support;

import com.patra.catalog.domain.model.vo.mesh.MeshUI;
import com.patra.catalog.infra.adapter.parser.MeshXmlElements;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/// 引用实体解析结果。
///
/// 封装 MeSH XML 中 `*ReferredTo` 元素的解析结果，替代原有的 `String[]` 返回值，
/// 提供类型安全和语义清晰的数据访问。
///
/// **适用元素**：
///
/// - `DescriptorReferredTo` - 引用的主题词
/// - `QualifierReferredTo` - 引用的限定词
/// - `SupplementalRecordReferredTo` - 引用的补充概念记录（SCR）
///
/// **XML 结构示例**：
///
/// ```xml
/// <DescriptorReferredTo>
///   <DescriptorUI>D000001</DescriptorUI>
///   <DescriptorName>
///     <String>Calcimycin</String>
///   </DescriptorName>
/// </DescriptorReferredTo>
/// ```
///
/// @param ui 实体唯一标识符（如 D000001、Q000001、C000001）
/// @param name 实体名称
/// @author linqibin
/// @since 0.1.0
public record ReferredTo(String ui, String name) {

  /// 创建空的引用结果。
  ///
  /// @return 空的 ReferredTo 实例
  public static ReferredTo empty() {
    return new ReferredTo(null, null);
  }

  /// 检查引用是否有效（UI 和 Name 均不为空）。
  ///
  /// @return 如果引用有效返回 true
  public boolean isValid() {
    return ui != null && name != null;
  }

  /// 检查是否只有 UI（Name 为空）。
  ///
  /// 某些场景下只需要 UI 进行关联，Name 可选。
  ///
  /// @return 如果只有 UI 返回 true
  public boolean hasUiOnly() {
    return ui != null && name == null;
  }

  /// 判断该引用是否为 Major Topic（UI 以星号开头）。
  ///
  /// 在 MeSH 数据中，星号前缀表示该描述符是文献的主要焦点（Major Topic）。
  ///
  /// @return 如果 UI 以星号开头返回 true
  public boolean isMajorTopic() {
    return ui != null && ui.startsWith("*");
  }

  /// 将 UI 转换为 MeshUI 值对象（自动剥离星号前缀）。
  ///
  /// @return MeshUI 实例，UI 为空时返回 null
  public MeshUI toMeshUI() {
    if (ui == null) {
      return null;
    }
    String cleaned = ui.startsWith("*") ? ui.substring(1) : ui;
    return MeshUI.of(cleaned);
  }

  // ========== 静态解析方法 ==========

  /// 解析 DescriptorReferredTo 元素。
  ///
  /// **前置条件**：`reader` 已定位到 `DescriptorReferredTo` 的 START_ELEMENT。
  ///
  /// **后置条件**：`reader` 定位到 `DescriptorReferredTo` 的 END_ELEMENT 之后。
  ///
  /// @param reader XML 流读取器
  /// @return 解析结果
  /// @throws XMLStreamException XML 解析异常
  public static ReferredTo parseDescriptor(XMLStreamReader reader) throws XMLStreamException {
    return parse(
        reader,
        MeshXmlElements.Referred.DESCRIPTOR_REFERRED_TO,
        MeshXmlElements.Identifier.DESCRIPTOR_UI,
        MeshXmlElements.Name.DESCRIPTOR_NAME);
  }

  /// 解析 QualifierReferredTo 元素。
  ///
  /// **前置条件**：`reader` 已定位到 `QualifierReferredTo` 的 START_ELEMENT。
  ///
  /// **后置条件**：`reader` 定位到 `QualifierReferredTo` 的 END_ELEMENT 之后。
  ///
  /// @param reader XML 流读取器
  /// @return 解析结果
  /// @throws XMLStreamException XML 解析异常
  public static ReferredTo parseQualifier(XMLStreamReader reader) throws XMLStreamException {
    return parse(
        reader,
        MeshXmlElements.Referred.QUALIFIER_REFERRED_TO,
        MeshXmlElements.Identifier.QUALIFIER_UI,
        MeshXmlElements.Name.QUALIFIER_NAME);
  }

  /// 解析 SupplementalRecordReferredTo 元素（SCR 引用）。
  ///
  /// **前置条件**：`reader` 已定位到 `SupplementalRecordReferredTo` 的 START_ELEMENT。
  ///
  /// **后置条件**：`reader` 定位到 `SupplementalRecordReferredTo` 的 END_ELEMENT 之后。
  ///
  /// @param reader XML 流读取器
  /// @return 解析结果
  /// @throws XMLStreamException XML 解析异常
  public static ReferredTo parseSupplemental(XMLStreamReader reader) throws XMLStreamException {
    return parse(
        reader,
        MeshXmlElements.Referred.SUPPLEMENTAL_RECORD_REFERRED_TO,
        MeshXmlElements.Identifier.SUPPLEMENTAL_RECORD_UI,
        MeshXmlElements.Name.SUPPLEMENTAL_RECORD_NAME);
  }

  /// 通用解析方法。
  ///
  /// 解析包含 UI 和 Name 子元素的容器元素。
  ///
  /// @param reader XML 流读取器
  /// @param containerElement 容器元素名称
  /// @param uiElement UI 元素名称
  /// @param nameElement Name 元素名称
  /// @return 解析结果
  /// @throws XMLStreamException XML 解析异常
  private static ReferredTo parse(
      XMLStreamReader reader, String containerElement, String uiElement, String nameElement)
      throws XMLStreamException {
    String ui = null;
    String name = null;

    while (reader.hasNext()) {
      int event = reader.next();
      if (event == XMLStreamConstants.START_ELEMENT) {
        String localName = reader.getLocalName();
        if (uiElement.equals(localName)) {
          ui = reader.getElementText();
        } else if (nameElement.equals(localName)) {
          name = XmlParsingHelper.parseNameElement(reader);
        }
      } else if (event == XMLStreamConstants.END_ELEMENT
          && containerElement.equals(reader.getLocalName())) {
        break;
      }
    }

    return new ReferredTo(ui, name);
  }
}
