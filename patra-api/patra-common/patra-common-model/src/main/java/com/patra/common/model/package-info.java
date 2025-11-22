/// Patra 平台共享数据模型包（医学出版物领域）。
///
/// 提供跨服务共享的标准化数据结构，采用 DDD 的 **Shared Kernel** 模式，
/// 定义服务间的契约，确保数据交换的一致性和兼容性。
///
/// ## 设计定位
///
/// - **Shared Kernel** - 多个限界上下文共享的核心领域模型
///   - **医学出版物专用** - 基于 PubMed/MEDLINE 标准设计
///   - **框架无关** - 仅依赖 Jackson，保持可移植性
///   - **不可变模型** - 使用 `@Value` 和 `@Builder` 确保不可变性
///
/// ## 核心组件
///
/// - {@link com.patra.common.model.CanonicalPublication} - 规范化医学出版物模型（主模型）
///   - {@link com.patra.common.model.CanonicalPublication.MeshHeading} - MeSH 主题标引（医学索引核心）
///   - {@link com.patra.common.model.CanonicalPublication.SupplMeshName} - 补充 MeSH 概念
///   - {@link com.patra.common.model.CanonicalPublication.Investigator} - 研究者信息（临床试验）
///   - {@link com.patra.common.model.CanonicalPublication.Reference} - 参考文献
///   - {@link com.patra.common.model.CanonicalPublication.Author} - 作者信息
///   - {@link com.patra.common.model.CanonicalPublication.Journal} - 期刊信息
///   - {@link com.patra.common.model.CanonicalPublication.Abstract} - 摘要（支持结构化）
///   - {@link com.patra.common.model.CanonicalPublication.Pagination} - 页码信息（结构化）
///
/// ## 医学领域特性
///
/// - **MeSH 术语支持** - 完整支持 MeSH 主题标引、限定词、补充概念
///   - **临床试验字段** - 研究者信息、外部数据库引用（ClinicalTrials.gov）
///   - **化学物质** - 物质列表（CAS 号、MeSH 词表）
///   - **基因符号** - 基因列表（GenBank 引用）
///   - **参考文献** - 完整的引用列表和数量统计
///   - **质量标注** - 撤稿、更正、评论等相关项目
///
/// ## 国际标准对齐
///
/// - **PubMed/MEDLINE** - 医学出版物元数据标准（主要数据源）
///   - **MeSH** - 美国国家医学图书馆医学主题词表
///   - **Dublin Core** - 核心元数据标准（title, creator, identifier 等）
///   - **Schema.org** - ScholarlyArticle 规范（author, abstract, keywords 等）
///
/// ## 设计原则
///
/// - **无行为** - 模型仅包含数据结构，不包含业务逻辑
///   - **不可变性** - 使用 `@Value` 确保线程安全和缓存友好
///   - **医学领域化** - 使用医学标准术语（MeSH, Investigator, Substance 等）
///   - **契约优先** - 变更需要考虑所有消费者的兼容性
///   - **类型安全** - 优先使用嵌套类型而非 Map
///
/// ## 使用场景
///
/// - **数据采集层** - 将 PubMed/EPMC 响应转换为规范化模型
///   - **数据存储层** - 作为持久化模型的基础
///   - **服务间通信** - 作为微服务间的数据交换契约
///   - **API 响应** - 作为 REST API 的响应模型
///
/// ## 使用示例
///
/// ```java
/// // 示例 1: 创建规范化医学出版物
/// CanonicalPublication publication = CanonicalPublication.builder()
///     .title("Deep Learning in Medical Image Analysis")
///     .abstractContent(Abstract.builder()
///         .text("This study presents...")
///         .build())
///     .authors(List.of(
///         Author.builder()
///             .lastName("Smith")
///             .foreName("John")
///             .affiliation(List.of(Affiliation.builder()
///                 .name("Stanford University")
///                 .build()))
///             .build()
///     ))
///     .journal(Journal.builder()
///         .title("Nature Medicine")
///         .issn("1546-170X")
///         .build())
///     .identifiers(List.of(
///         Identifier.builder()
///             .type("pmid")
///             .value("12345678")
///             .build()
///     ))
///     .meshHeadings(List.of(
///         MeshHeading.builder()
///             .descriptorName(DescriptorName.builder()
///                 .ui("D000086382")
///                 .term("COVID-19")
///                 .majorTopic(true)
///                 .build())
///             .build()
///     ))
///     .build();
///
/// // 示例 2: 访问医学领域特定字段
/// List<MeshHeading> meshHeadings = publication.getMeshHeadings();
/// for (MeshHeading heading : meshHeadings) {
///     DescriptorName descriptor = heading.getDescriptorName();
///     System.out.println("MeSH 主题词: " + descriptor.getTerm());
///     System.out.println("主要主题: " + descriptor.getMajorTopic());
///
///     List<QualifierName> qualifiers = heading.getQualifierNames();
///     for (QualifierName qualifier : qualifiers) {
///         System.out.println("  限定词: " + qualifier.getTerm());
///
/// // 示例 3: 访问研究者信息（临床试验常见）
/// List<Investigator> investigators = publication.getInvestigators();
/// for (Investigator investigator : investigators) {
///     String name = investigator.getLastName() + ", " + investigator.getForeName();
///     List<Affiliation> affiliations = investigator.getAffiliations();
///     // 处理研究者信息
///
/// // 示例 4: 访问参考文献
/// Integer refCount = publication.getNumberOfReferences();
/// List<Reference> references = publication.getReferences();
/// for (Reference ref : references) {
///     String citation = ref.getCitation();
///     List<Identifier> ids = ref.getIdentifiers();
///     // 处理参考文献
/// ```
///
/// ## 下游消费者
///
/// - **patra-ingest-domain** - 端口接口定义（DataSourcePort 返回类型）
///   - **patra-ingest-app** - 应用服务编排（批次规划器使用）
///   - **patra-ingest-infra** - 基础设施层（适配器实现）
///   - **patra-spring-boot-starter-provenance** - Provenance 适配器实现（PubMed、EPMC 等）
///   - 未来: `patra-search` 等下游服务
///
/// ## 变更策略
///
/// - **向后兼容** - 新增字段使用可选类型（允许 null）
///   - **破坏性变更** - 必须通知所有消费者，协调升级
///   - **弃用流程** - 标记 `@Deprecated` → 迁移期 → 移除
///   - **医学标准优先** - 优先采用医学领域标准术语和结构
///
/// ## 架构优势
///
/// - **解耦服务间依赖** - 服务不直接依赖彼此的内部模型
///   - **统一数据理解** - 所有服务对"医学出版物"概念有一致理解
///   - **医学语义清晰** - 使用医学标准术语，避免歧义
///   - **易于扩展** - 新增服务可直接使用现有模型
///   - **测试友好** - 不可变对象易于断言和构造测试数据
///
/// @since 0.1.0
/// @author linqibin
/// @see com.patra.common.model.CanonicalPublication
/// @see <a href="https://www.nlm.nih.gov/mesh/">MeSH - Medical Subject Headings</a>
/// @see <a href="https://www.nlm.nih.gov/bsd/mms/medlineelements.html">MEDLINE Data Elements</a>
package com.patra.common.model;
