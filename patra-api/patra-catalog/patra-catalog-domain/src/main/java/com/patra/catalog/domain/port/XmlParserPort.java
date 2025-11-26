package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.aggregate.MeshQualifierAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.stream.Stream;

/// MeSH XML 解析接口（Port）。
///
/// 定义 MeSH XML 文件的解析操作，由 Infrastructure 层实现。
///
/// **设计原则**：
///
/// - 流式处理：使用 Stream 返回大量数据，避免内存溢出
///   - 纯接口定义：不包含实现逻辑
///   - 面向领域对象：返回值都是领域对象
///   - 六边形架构：领域层定义接口，基础设施层实现
///
/// **实现说明**：
///
/// - Infrastructure 层使用 StAX（Streaming API for XML）实现流式解析
///   - 单次解析处理约 35,000 个 Descriptor 记录
///   - 使用 Stream 返回，支持按批次处理（如 1000 条/批）
///   - 解析器应该处理 XML 命名空间和特殊字符
///
/// **XML 结构示例**：
///
/// ```java
/// <DescriptorRecordSet>
///   <DescriptorRecord>
///     <DescriptorUI>D000001</DescriptorUI>
///     <DescriptorName><String>Calcimycin</String></DescriptorName>
///     <TreeNumberList>
///       <TreeNumber>D03.438.221</TreeNumber>
///     </TreeNumberList>
///   </DescriptorRecord>
/// </DescriptorRecordSet>
/// ```
///
/// @author linqibin
/// @since 0.1.0
public interface XmlParserPort {

  /// 解析限定词（Qualifier）。
  ///
  /// 从 MeSH 限定词 XML 文件中解析所有 Qualifier 记录。
  ///
  /// 实现说明：
  ///
  /// - 解析 `<QualifierRecord>` 元素
  ///   - 限定词是独立的聚合根
  ///   - 约 80 条记录
  ///   - 必须先于主题词导入
  ///
  /// @param xmlInputStream 限定词 XML 文件输入流
  /// @return 限定词聚合根流（Stream）
  Stream<MeshQualifierAggregate> parseQualifiers(InputStream xmlInputStream);

  /// 解析限定词（Qualifier）- 从文件路径。
  ///
  /// 从指定文件路径读取并解析 MeSH 限定词 XML 文件。
  ///
  /// @param filePath XML 文件路径
  /// @return 限定词聚合根流（Stream）
  Stream<MeshQualifierAggregate> parseQualifiers(Path filePath);

  /// 解析主题词（Descriptor）。
  ///
  /// 从 MeSH 主题词 XML 文件中解析所有 Descriptor 记录。
  ///
  /// 实现说明：
  ///
  /// - 解析 `<DescriptorRecord>` 元素
  ///   - 包含嵌套的树形编号、入口术语、概念
  ///   - 返回完整的聚合根对象
  ///   - 约 35,000 条记录
  ///
  /// @param xmlInputStream 主题词 XML 文件输入流
  /// @return 主题词聚合根流（Stream）
  Stream<MeshDescriptorAggregate> parseDescriptors(InputStream xmlInputStream);

  /// 解析树形编号（TreeNumber）。
  ///
  /// 从 MeSH 主题词 XML 文件中解析所有树形编号。
  ///
  /// 实现说明：
  ///
  /// - 解析 `<TreeNumber>` 元素
  ///   - 关联到对应的 Descriptor UI
  ///   - 约 80,000 条记录（平均每个 Descriptor 2.3 个）
  ///
  /// @param xmlInputStream 主题词 XML 文件输入流
  /// @return 树形编号实体流（Stream）
  Stream<MeshTreeNumber> parseTreeNumbers(InputStream xmlInputStream);

  /// 解析入口术语（EntryTerm）。
  ///
  /// 从 MeSH 主题词 XML 文件中解析所有入口术语（同义词）。
  ///
  /// 实现说明：
  ///
  /// - 解析 `<Term>` 元素
  ///   - 关联到对应的 Descriptor UI
  ///   - 约 250,000 条记录（平均每个 Descriptor 7-8 个）
  ///
  /// @param xmlInputStream 主题词 XML 文件输入流
  /// @return 入口术语实体流（Stream）
  Stream<MeshEntryTerm> parseEntryTerms(InputStream xmlInputStream);

  /// 解析概念（Concept）。
  ///
  /// 从 MeSH 主题词 XML 文件中解析所有概念。
  ///
  /// 实现说明：
  ///
  /// - 解析 `<Concept>` 元素
  ///   - 关联到对应的 Descriptor UI
  ///   - 约 180,000 条记录（平均每个 Descriptor 5-6 个）
  ///
  /// @param xmlInputStream 主题词 XML 文件输入流
  /// @return 概念实体流（Stream）
  Stream<MeshConcept> parseConcepts(InputStream xmlInputStream);
}
