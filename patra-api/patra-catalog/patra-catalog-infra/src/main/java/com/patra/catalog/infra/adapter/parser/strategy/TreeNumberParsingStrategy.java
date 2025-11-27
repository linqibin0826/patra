package com.patra.catalog.infra.adapter.parser.strategy;

import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import com.patra.catalog.infra.adapter.parser.MeshXmlElements;
import com.patra.catalog.infra.adapter.parser.support.XmlParsingContext;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/// TreeNumber 解析策略。
///
/// 解析 MeSH XML 中的 `<TreeNumber>` 元素，创建 `MeshTreeNumber` 领域实体。
///
/// **XML 结构**：
/// ```xml
/// <TreeNumber>C04.557.337.428</TreeNumber>
/// ```
///
/// **解析规则**：
/// - 提取元素文本作为树形编号
/// - 层级深度自动计算（点号数量 + 1）
/// - `isPrimary` 默认为 `false`（由调用方在列表解析时确定首项）
///
/// **使用场景**：
/// - 独立解析 `<TreeNumber>` 元素流
/// - 被 `DescriptorParsingStrategy` 用于解析 `<TreeNumberList>` 内的条目
///
/// @author linqibin
/// @since 0.1.0
public final class TreeNumberParsingStrategy implements RecordParsingStrategy<MeshTreeNumber> {

  /// 单例实例。
  public static final TreeNumberParsingStrategy INSTANCE = new TreeNumberParsingStrategy();

  private TreeNumberParsingStrategy() {}

  @Override
  public String rootElementName() {
    return MeshXmlElements.Record.TREE_NUMBER;
  }

  /// 解析单个 TreeNumber 元素。
  ///
  /// **前置条件**：reader 已定位到 `<TreeNumber>` 的 START_ELEMENT。
  ///
  /// **后置条件**：reader 位于 `<TreeNumber>` 的 END_ELEMENT 之后（由 getElementText 消费）。
  ///
  /// @param reader XML 流读取器
  /// @param context 解析上下文（本策略未使用）
  /// @return MeshTreeNumber 实体（isPrimary = false）
  /// @throws XMLStreamException XML 解析异常
  @Override
  public MeshTreeNumber parseRecord(XMLStreamReader reader, XmlParsingContext context)
      throws XMLStreamException {
    String treeNumberText = reader.getElementText();

    // 独立解析时 isPrimary 默认为 false
    // 在列表解析场景（如 DescriptorParsingStrategy），由调用方根据索引设置 isPrimary
    return MeshTreeNumber.create(treeNumberText, false);
  }
}
