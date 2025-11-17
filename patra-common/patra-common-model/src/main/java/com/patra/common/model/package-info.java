/**
 * Patra 平台共享数据模型包（医学出版物领域）。
 *
 * <p>提供跨服务共享的标准化数据结构，采用 DDD 的 <b>Shared Kernel</b> 模式，
 * 定义服务间的契约，确保数据交换的一致性和兼容性。
 *
 * <h2>设计定位</h2>
 *
 * <ul>
 *   <li><b>Shared Kernel</b> - 多个限界上下文共享的核心领域模型
 *   <li><b>医学出版物专用</b> - 基于 PubMed/MEDLINE 标准设计
 *   <li><b>框架无关</b> - 仅依赖 Jackson，保持可移植性
 *   <li><b>不可变模型</b> - 使用 {@code @Value} 和 {@code @Builder} 确保不可变性
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.common.model.CanonicalPublication} - 规范化医学出版物模型（主模型）
 *   <li>{@link com.patra.common.model.CanonicalPublication.MeshHeading} - MeSH 主题标引（医学索引核心）
 *   <li>{@link com.patra.common.model.CanonicalPublication.SupplMeshName} - 补充 MeSH 概念
 *   <li>{@link com.patra.common.model.CanonicalPublication.Investigator} - 研究者信息（临床试验）
 *   <li>{@link com.patra.common.model.CanonicalPublication.Reference} - 参考文献
 *   <li>{@link com.patra.common.model.CanonicalPublication.Author} - 作者信息
 *   <li>{@link com.patra.common.model.CanonicalPublication.Journal} - 期刊信息
 *   <li>{@link com.patra.common.model.CanonicalPublication.Abstract} - 摘要（支持结构化）
 *   <li>{@link com.patra.common.model.CanonicalPublication.Pagination} - 页码信息（结构化）
 * </ul>
 *
 * <h2>医学领域特性</h2>
 *
 * <ul>
 *   <li><b>MeSH 术语支持</b> - 完整支持 MeSH 主题标引、限定词、补充概念
 *   <li><b>临床试验字段</b> - 研究者信息、外部数据库引用（ClinicalTrials.gov）
 *   <li><b>化学物质</b> - 物质列表（CAS 号、MeSH 词表）
 *   <li><b>基因符号</b> - 基因列表（GenBank 引用）
 *   <li><b>参考文献</b> - 完整的引用列表和数量统计
 *   <li><b>质量标注</b> - 撤稿、更正、评论等相关项目
 * </ul>
 *
 * <h2>国际标准对齐</h2>
 *
 * <ul>
 *   <li><b>PubMed/MEDLINE</b> - 医学出版物元数据标准（主要数据源）
 *   <li><b>MeSH</b> - 美国国家医学图书馆医学主题词表
 *   <li><b>Dublin Core</b> - 核心元数据标准（title, creator, identifier 等）
 *   <li><b>Schema.org</b> - ScholarlyArticle 规范（author, abstract, keywords 等）
 * </ul>
 *
 * <h2>设计原则</h2>
 *
 * <ul>
 *   <li><b>无行为</b> - 模型仅包含数据结构，不包含业务逻辑
 *   <li><b>不可变性</b> - 使用 {@code @Value} 确保线程安全和缓存友好
 *   <li><b>医学领域化</b> - 使用医学标准术语（MeSH, Investigator, Substance 等）
 *   <li><b>契约优先</b> - 变更需要考虑所有消费者的兼容性
 *   <li><b>类型安全</b> - 优先使用嵌套类型而非 Map
 * </ul>
 *
 * <h2>使用场景</h2>
 *
 * <ul>
 *   <li><b>数据采集层</b> - 将 PubMed/EPMC 响应转换为规范化模型
 *   <li><b>数据存储层</b> - 作为持久化模型的基础
 *   <li><b>服务间通信</b> - 作为微服务间的数据交换契约
 *   <li><b>API 响应</b> - 作为 REST API 的响应模型
 * </ul>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 示例 1: 创建规范化医学出版物
 * CanonicalPublication publication = CanonicalPublication.builder()
 *     .title("Deep Learning in Medical Image Analysis")
 *     .abstractContent(Abstract.builder()
 *         .text("This study presents...")
 *         .build())
 *     .authors(List.of(
 *         Author.builder()
 *             .lastName("Smith")
 *             .foreName("John")
 *             .affiliation(List.of(Affiliation.builder()
 *                 .name("Stanford University")
 *                 .build()))
 *             .build()
 *     ))
 *     .journal(Journal.builder()
 *         .title("Nature Medicine")
 *         .issn("1546-170X")
 *         .build())
 *     .identifiers(List.of(
 *         Identifier.builder()
 *             .type("pmid")
 *             .value("12345678")
 *             .build()
 *     ))
 *     .meshHeadings(List.of(
 *         MeshHeading.builder()
 *             .descriptorName(DescriptorName.builder()
 *                 .ui("D000086382")
 *                 .term("COVID-19")
 *                 .majorTopic(true)
 *                 .build())
 *             .build()
 *     ))
 *     .build();
 *
 * // 示例 2: 访问医学领域特定字段
 * List<MeshHeading> meshHeadings = publication.getMeshHeadings();
 * for (MeshHeading heading : meshHeadings) {
 *     DescriptorName descriptor = heading.getDescriptorName();
 *     System.out.println("MeSH 主题词: " + descriptor.getTerm());
 *     System.out.println("主要主题: " + descriptor.getMajorTopic());
 *
 *     List<QualifierName> qualifiers = heading.getQualifierNames();
 *     for (QualifierName qualifier : qualifiers) {
 *         System.out.println("  限定词: " + qualifier.getTerm());
 *     }
 * }
 *
 * // 示例 3: 访问研究者信息（临床试验常见）
 * List<Investigator> investigators = publication.getInvestigators();
 * for (Investigator investigator : investigators) {
 *     String name = investigator.getLastName() + ", " + investigator.getForeName();
 *     List<Affiliation> affiliations = investigator.getAffiliations();
 *     // 处理研究者信息
 * }
 *
 * // 示例 4: 访问参考文献
 * Integer refCount = publication.getNumberOfReferences();
 * List<Reference> references = publication.getReferences();
 * for (Reference ref : references) {
 *     String citation = ref.getCitation();
 *     List<Identifier> ids = ref.getIdentifiers();
 *     // 处理参考文献
 * }
 * }</pre>
 *
 * <h2>下游消费者</h2>
 *
 * <ul>
 *   <li><b>patra-ingest-domain</b> - 端口接口定义（DataSourcePort 返回类型）
 *   <li><b>patra-ingest-app</b> - 应用服务编排（批次规划器使用）
 *   <li><b>patra-ingest-infra</b> - 基础设施层（适配器实现）
 *   <li><b>patra-spring-boot-starter-provenance</b> - Provenance 适配器实现（PubMed、EPMC 等）
 *   <li>未来: {@code patra-search} 等下游服务
 * </ul>
 *
 * <h2>变更策略</h2>
 *
 * <ul>
 *   <li><b>向后兼容</b> - 新增字段使用可选类型（允许 null）
 *   <li><b>破坏性变更</b> - 必须通知所有消费者，协调升级
 *   <li><b>弃用流程</b> - 标记 {@code @Deprecated} → 迁移期 → 移除
 *   <li><b>医学标准优先</b> - 优先采用医学领域标准术语和结构
 * </ul>
 *
 * <h2>架构优势</h2>
 *
 * <ul>
 *   <li><b>解耦服务间依赖</b> - 服务不直接依赖彼此的内部模型
 *   <li><b>统一数据理解</b> - 所有服务对"医学出版物"概念有一致理解
 *   <li><b>医学语义清晰</b> - 使用医学标准术语，避免歧义
 *   <li><b>易于扩展</b> - 新增服务可直接使用现有模型
 *   <li><b>测试友好</b> - 不可变对象易于断言和构造测试数据
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 * @see com.patra.common.model.CanonicalPublication
 * @see <a href="https://www.nlm.nih.gov/mesh/">MeSH - Medical Subject Headings</a>
 * @see <a href="https://www.nlm.nih.gov/bsd/mms/medlineelements.html">MEDLINE Data Elements</a>
 */
package com.patra.common.model;
