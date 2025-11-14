/**
 * Catalog 服务数据传输对象包。
 *
 * <p>本包定义了文献目录服务(patra-catalog)的公开 API 契约。所有 DTO 类都是六边形架构 API 层的一部分, 作为服务间通信的"发布语言"(Published
 * Language),确保跨服务集成的稳定性和一致性。
 *
 * <h2>核心 DTO</h2>
 *
 * <ul>
 *   <li>{@link com.patra.catalog.api.dto.LiteratureDTO} - 文献数据传输对象,包含标题、摘要、作者、期刊等完整元数据
 *   <li>{@link com.patra.catalog.api.dto.JournalDTO} - 期刊数据传输对象,包含期刊名称、ISSN、出版商等信息
 *   <li>{@link com.patra.catalog.api.dto.AuthorDTO} - 作者数据传输对象,包含姓名、机构、ORCID 等作者信息
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><strong>不可变性</strong>: 使用 Java Record 确保 DTO 不可变,防止意外修改
 *   <li><strong>Builder 模式</strong>: 集成 Lombok {@code @Builder} 简化对象构造
 *   <li><strong>数据验证</strong>: 使用 Jakarta Validation 约束保证数据完整性
 *   <li><strong>向后兼容</strong>: 新增字段必须有默认值,确保老版本客户端兼容
 *   <li><strong>契约稳定</strong>: 已发布字段不得删除或修改类型,仅允许追加新字段
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><strong>服务间通信</strong>: patra-ingest → patra-catalog 传递采集的文献数据
 *   <li><strong>API 响应</strong>: patra-catalog 对外暴露的 REST API 查询结果(规划中)
 *   <li><strong>事件载荷</strong>: RocketMQ 消息体携带文献数据(未来扩展)
 * </ul>
 *
 * <h2>示例</h2>
 *
 * <pre>{@code
 * // 构造文献 DTO
 * LiteratureDTO literature = LiteratureDTO.builder()
 *     .title("Deep Learning for Medical Image Analysis")
 *     .abstractText("This paper presents a novel approach...")
 *     .authors(List.of(
 *         AuthorDTO.builder()
 *             .lastName("Smith")
 *             .foreName("John")
 *             .affiliations(List.of("MIT"))
 *             .build()
 *     ))
 *     .journal(JournalDTO.builder()
 *         .title("Nature Medicine")
 *         .issn("1078-8956")
 *         .issnType("Electronic")
 *         .build()
 *     )
 *     .identifiers(Map.of(
 *         "pmid", "12345678",
 *         "doi", "10.1038/nm.1234"
 *     ))
 *     .publicationDate(LocalDate.of(2024, 1, 15))
 *     .keywords(List.of("Deep Learning", "Medical Imaging"))
 *     .language("en")
 *     .publicationTypes(List.of("Journal Article"))
 *     .build();
 * }</pre>
 *
 * <h2>相关文档</h2>
 *
 * <ul>
 *   <li>模块文档: {@code patra-catalog/README.md}
 *   <li>架构指南: 六边形架构 + DDD 实践
 * </ul>
 *
 * @author linqibin
 * @since 0.1.0
 */
package com.patra.catalog.api.dto;
