package com.patra.catalog.domain.port;

import com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate;
import com.patra.catalog.domain.model.entity.MeshConcept;
import com.patra.catalog.domain.model.entity.MeshEntryTerm;
import com.patra.catalog.domain.model.entity.MeshTreeNumber;
import java.io.InputStream;
import java.util.stream.Stream;

/**
 * MeSH XML 解析接口（Port）。
 *
 * <p>定义 MeSH XML 文件的解析操作，由 Infrastructure 层实现。
 *
 * <p><b>设计原则</b>：
 *
 * <ul>
 *   <li>流式处理：使用 Stream 返回大量数据，避免内存溢出
 *   <li>纯接口定义：不包含实现逻辑
 *   <li>面向领域对象：返回值都是领域对象
 *   <li>六边形架构：领域层定义接口，基础设施层实现
 * </ul>
 *
 * <p><b>实现说明</b>：
 *
 * <ul>
 *   <li>Infrastructure 层使用 StAX（Streaming API for XML）实现流式解析
 *   <li>单次解析处理约 35,000 个 Descriptor 记录
 *   <li>使用 Stream 返回，支持按批次处理（如 1000 条/批）
 *   <li>解析器应该处理 XML 命名空间和特殊字符
 * </ul>
 *
 * <p><b>XML 结构示例</b>：
 *
 * <pre>{@code
 * <DescriptorRecordSet>
 *   <DescriptorRecord>
 *     <DescriptorUI>D000001</DescriptorUI>
 *     <DescriptorName><String>Calcimycin</String></DescriptorName>
 *     <TreeNumberList>
 *       <TreeNumber>D03.438.221</TreeNumber>
 *     </TreeNumberList>
 *   </DescriptorRecord>
 * </DescriptorRecordSet>
 * }</pre>
 *
 * @author linqibin
 * @since 0.2.0
 */
public interface XmlParserPort {

  /**
   * 解析主题词（Descriptor）。
   *
   * <p>从 MeSH XML 文件中解析所有 Descriptor 记录。
   *
   * <p>实现说明：
   *
   * <ul>
   *   <li>解析 {@code <DescriptorRecord>} 元素
   *   <li>包含嵌套的树形编号、入口术语、概念
   *   <li>返回完整的聚合根对象
   *   <li>约 35,000 条记录
   * </ul>
   *
   * @param xmlInputStream XML 文件输入流
   * @return 主题词聚合根流（Stream）
   */
  Stream<MeshDescriptorAggregate> parseDescriptors(InputStream xmlInputStream);

  /**
   * 解析树形编号（TreeNumber）。
   *
   * <p>从 MeSH XML 文件中解析所有树形编号。
   *
   * <p>实现说明：
   *
   * <ul>
   *   <li>解析 {@code <TreeNumber>} 元素
   *   <li>关联到对应的 Descriptor UI
   *   <li>约 80,000 条记录（平均每个 Descriptor 2.3 个）
   * </ul>
   *
   * @param xmlInputStream XML 文件输入流
   * @return 树形编号实体流（Stream）
   */
  Stream<MeshTreeNumber> parseTreeNumbers(InputStream xmlInputStream);

  /**
   * 解析入口术语（EntryTerm）。
   *
   * <p>从 MeSH XML 文件中解析所有入口术语（同义词）。
   *
   * <p>实现说明：
   *
   * <ul>
   *   <li>解析 {@code <Term>} 元素
   *   <li>关联到对应的 Descriptor UI
   *   <li>约 250,000 条记录（平均每个 Descriptor 7-8 个）
   * </ul>
   *
   * @param xmlInputStream XML 文件输入流
   * @return 入口术语实体流（Stream）
   */
  Stream<MeshEntryTerm> parseEntryTerms(InputStream xmlInputStream);

  /**
   * 解析概念（Concept）。
   *
   * <p>从 MeSH XML 文件中解析所有概念。
   *
   * <p>实现说明：
   *
   * <ul>
   *   <li>解析 {@code <Concept>} 元素
   *   <li>关联到对应的 Descriptor UI
   *   <li>约 180,000 条记录（平均每个 Descriptor 5-6 个）
   * </ul>
   *
   * @param xmlInputStream XML 文件输入流
   * @return 概念实体流（Stream）
   */
  Stream<MeshConcept> parseConcepts(InputStream xmlInputStream);
}
