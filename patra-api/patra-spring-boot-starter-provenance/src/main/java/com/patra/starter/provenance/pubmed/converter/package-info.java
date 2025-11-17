/**
 * PubMed 医学出版物转换器包。
 *
 * <p>负责将 PubMed XML/JSON 响应对象转换为 {@link com.patra.common.model.CanonicalPublication} 规范化医学出版物模型。
 *
 * <h2>职责</h2>
 *
 * <ul>
 *   <li>解析 PubMed XML/JSON 响应（通过 Jackson）
 *   <li>提取完整的医学出版物元数据（P0 核心字段 + P1 医学领域字段）
 *   <li>转换为 {@link com.patra.common.model.CanonicalPublication} 标准模型
 *   <li>处理不完整或缺失的字段（可选字段安全处理）
 *   <li>支持医学领域特有字段（MeSH、研究者、参考文献等）
 * </ul>
 *
 * <h2>核心组件</h2>
 *
 * <ul>
 *   <li>{@link com.patra.starter.provenance.pubmed.converter.PubmedPublicationConverter} - PubMed 医学出版物转换器（主类）
 * </ul>
 *
 * <h2>转换方法清单</h2>
 *
 * <h3>P0 核心字段转换（必须支持）</h3>
 *
 * <ul>
 *   <li>{@code buildIdentifiers()} - 构建标识符列表（PMID, DOI, PMC, PII 等）
 *   <li>{@code extractAbstract()} - 提取摘要信息（支持结构化和非结构化摘要）
 *   <li>{@code convertAuthors()} - 转换作者信息（包含 valid 字段，v0.1.0 新增）
 *   <li>{@code convertJournal()} - 转换期刊信息（标题、ISSN、卷期等）
 *   <li>{@code extractPublicationDates()} - 提取出版日期（published, electronic, revised, completed 等）
 *   <li>{@code convertPagination()} - 转换页码信息（v0.1.0 重构为结构化对象：startPage, endPage, medlinePgn）
 *   <li>{@code convertFunding()} - 转换资助信息（v0.1.0 重命名 funderIdentifier → funderAcronym）
 *   <li>{@code convertReferences()} - 转换参考文献列表（v0.1.0 新增）
 *   <li>{@code extractKeywords()} - 提取关键词集合（支持多来源）
 * </ul>
 *
 * <h3>P1 医学领域字段转换（增强功能）</h3>
 *
 * <ul>
 *   <li>{@code convertMeshHeadings()} - 转换 MeSH 主题标引（医学索引核心，v0.1.0 从 convertSubjects() 重构）
 *   <li>{@code convertSupplMeshNames()} - 转换补充 MeSH 概念（疾病、药物试验等，v0.1.0 新增）
 *   <li>{@code convertInvestigators()} - 转换研究者信息（临床试验、多中心研究，v0.1.0 新增）
 *   <li>{@code convertPersonalNameSubjects()} - 转换人物主题（传记、医学史、案例报告，v0.1.0 新增）
 *   <li>{@code convertExternalReferences()} - 转换外部数据库引用（GenBank、ClinicalTrials.gov 等，v0.1.0 新增）
 *   <li>{@code convertSupplementalObjects()} - 转换补充对象（图表、数据集、多媒体，v0.1.0 新增）
 *   <li>{@code convertRelatedItems()} - 转换相关项目（更正、撤稿、评论、转载，v0.1.0 新增）
 *   <li>{@code convertSubstances()} - 转换化学物质列表（CAS 号、MeSH 词表）
 *   <li>{@code convertGenes()} - 转换基因符号列表（GenBank 引用）
 *   <li>{@code convertAlternativeAbstracts()} - 转换其他语言或版本的摘要
 * </ul>
 *
 * <h3>元数据和出版历史</h3>
 *
 * <ul>
 *   <li>{@code extractMetadata()} - 提取出版物元数据（索引方法、所有者、状态等）
 *   <li>{@code extractPublicationHistory()} - 提取发布历史时间线（received, accepted, epublish, pubmed 等）
 *   <li>{@code extractPublicationTypes()} - 提取发表类型列表（Journal Article, Review 等）
 *   <li>{@code extractAuthorsComplete()} - 提取作者列表完整性标识
 * </ul>
 *
 * <h2>转换逻辑示意图</h2>
 *
 * <pre>
 * PubmedPublication (PubMed XML/JSON 响应模型)
 * ├── pmid → CanonicalPublication.identifiers (type=pmid)
 * ├── article
 * │   ├── title → title
 * │   ├── vernacularTitle → originalTitle
 * │   ├── language → language
 * │   ├── abstractSections[] → abstractContent (Abstract)
 * │   ├── authors[] → authors (Author[])
 * │   ├── journal → journal (Journal)
 * │   │   ├── title → journal.title
 * │   │   ├── issn → journal.issn
 * │   │   └── journalIssue
 * │   │       ├── volume → journal.volume
 * │   │       ├── issue → journal.issue
 * │   │       └── pubDate → dates.published
 * │   ├── pagination → pagination (Pagination)
 * │   │   ├── startPage → pagination.startPage
 * │   │   ├── endPage → pagination.endPage
 * │   │   └── medlinePgn → pagination.medlinePgn
 * │   ├── meshHeadings[] → meshHeadings (MeshHeading[])
 * │   │   ├── descriptorName → descriptorName (DescriptorName)
 * │   │   └── qualifierNames[] → qualifierNames (QualifierName[])
 * │   ├── supplMeshList[] → supplMeshNames (SupplMeshName[])
 * │   ├── investigatorList[] → investigators (Investigator[])
 * │   ├── personalNameSubjects[] → personalNameSubjects (PersonalNameSubject[])
 * │   ├── dataBank[] → externalReferences (ExternalReference[])
 * │   ├── grantList[] → funding (FundingInfo[])
 * │   ├── chemicalList[] → substances (Substance[])
 * │   ├── geneSymbols[] → genes (String[])
 * │   ├── keywords[] → keywords (KeywordSet[])
 * │   ├── references[] → references (Reference[])
 * │   ├── otherAbstracts[] → alternativeAbstracts (AlternativeAbstract[])
 * │   └── supplementalObjects[] → supplementalObjects (SupplementalObject[])
 * ├── pubmedData
 * │   ├── articleIds[] → identifiers (Identifier[])
 * │   │   ├── doi → (type=doi)
 * │   │   ├── pmc → (type=pmc)
 * │   │   └── pii → (type=pii)
 * │   ├── publicationStatus → publicationStatus
 * │   ├── history[] → publicationHistory (PublicationHistoryEvent[])
 * │   ├── citationSubset → metadata.citationSubset
 * │   └── pmcRefCount → citationCount
 * ├── journalInfo
 * │   ├── nlmUniqueId → journal.nlmUniqueId
 * │   ├── medlineTa → journal.medlineAbbreviation
 * │   ├── issnLinking → journal.issnLinking
 * │   └── country → journal.country
 * ├── numberOfReferences → numberOfReferences
 * ├── coiStatement → conflictOfInterestStatement
 * └── relatedItems[] → relatedItems (RelatedItem[])
 * </pre>
 *
 * <h2>使用示例</h2>
 *
 * <pre>{@code
 * // 示例 1: 基本使用（自动配置注入）
 * @Component
 * @RequiredArgsConstructor
 * public class PubmedDataAdapter {
 *
 *     private final PubmedPublicationConverter converter;
 *
 *     public CanonicalPublication fetchPublication(String pmid) {
 *         // 1. 从 PubMed 获取原始 XML 响应
 *         PubmedPublication rawArticle = pubMedClient.efetch(...);
 *
 *         // 2. 转换为规范化医学出版物模型
 *         CanonicalPublication publication = converter.toCanonicalPublication(rawArticle);
 *
 *         return publication;
 *     }
 * }
 *
 * // 示例 2: 访问 P0 核心字段
 * CanonicalPublication publication = converter.toCanonicalPublication(rawArticle);
 *
 * // 标识符
 * List<Identifier> identifiers = publication.getIdentifiers();
 * String pmid = identifiers.stream()
 *     .filter(id -> "pmid".equals(id.getType()))
 *     .map(Identifier::getValue)
 *     .findFirst()
 *     .orElse(null);
 *
 * // 摘要（支持结构化和非结构化）
 * Abstract abstractContent = publication.getAbstractContent();
 * if (abstractContent != null) {
 *     String text = abstractContent.getText();  // 纯文本版本
 *     List<AbstractSection> sections = abstractContent.getSections();  // 结构化段落
 *     for (AbstractSection section : sections) {
 *         System.out.println(section.getLabel() + ": " + section.getContent());
 *     }
 * }
 *
 * // 作者信息
 * List<Author> authors = publication.getAuthors();
 * for (Author author : authors) {
 *     String name = author.getLastName() + ", " + author.getForeName();
 *     List<Affiliation> affiliations = author.getAffiliations();
 *     Boolean valid = author.getValid();  // v0.1.0 新增
 * }
 *
 * // 期刊信息
 * Journal journal = publication.getJournal();
 * if (journal != null) {
 *     String title = journal.getTitle();
 *     String issn = journal.getIssn();
 *     String volume = journal.getVolume();
 *     String issue = journal.getIssue();
 * }
 *
 * // 结构化页码（v0.1.0 重构）
 * Pagination pagination = publication.getPagination();
 * if (pagination != null) {
 *     String startPage = pagination.getStartPage();
 *     String endPage = pagination.getEndPage();
 *     String medlinePgn = pagination.getMedlinePgn();
 * }
 *
 * // 参考文献（v0.1.0 新增）
 * Integer refCount = publication.getNumberOfReferences();
 * List<Reference> references = publication.getReferences();
 * for (Reference ref : references) {
 *     String citation = ref.getCitation();
 *     List<Identifier> refIds = ref.getIdentifiers();
 * }
 *
 * // 示例 3: 访问 P1 医学领域字段
 * // MeSH 主题标引（医学索引核心）
 * List<MeshHeading> meshHeadings = publication.getMeshHeadings();
 * for (MeshHeading heading : meshHeadings) {
 *     DescriptorName descriptor = heading.getDescriptorName();
 *     System.out.println("MeSH 主题词: " + descriptor.getTerm());
 *     System.out.println("主要主题: " + descriptor.getMajorTopic());
 *
 *     // 限定词（进一步细化主题）
 *     List<QualifierName> qualifiers = heading.getQualifierNames();
 *     for (QualifierName qualifier : qualifiers) {
 *         System.out.println("  限定词: " + qualifier.getTerm());
 *     }
 * }
 *
 * // 补充 MeSH 概念（疾病、药物试验等）
 * List<SupplMeshName> supplMeshNames = publication.getSupplMeshNames();
 * for (SupplMeshName supplMesh : supplMeshNames) {
 *     System.out.println("补充概念: " + supplMesh.getName());
 *     System.out.println("概念类型: " + supplMesh.getType());  // Protocol, Disease 等
 * }
 *
 * // 研究者信息（临床试验、多中心研究）
 * List<Investigator> investigators = publication.getInvestigators();
 * for (Investigator investigator : investigators) {
 *     String name = investigator.getLastName() + ", " + investigator.getForeName();
 *     List<Affiliation> affiliations = investigator.getAffiliations();
 *     Boolean valid = investigator.getValid();
 * }
 *
 * // 人物主题（传记、医学史、案例报告）
 * List<PersonalNameSubject> personalNameSubjects = publication.getPersonalNameSubjects();
 * for (PersonalNameSubject subject : personalNameSubjects) {
 *     String name = subject.getLastName() + ", " + subject.getForeName();
 * }
 *
 * // 外部数据库引用（GenBank、ClinicalTrials.gov）
 * List<ExternalReference> externalRefs = publication.getExternalReferences();
 * for (ExternalReference ref : externalRefs) {
 *     String type = ref.getType();  // database, clinical-trial, software, dataset
 *     String name = ref.getName();  // GenBank, ClinicalTrials.gov 等
 *     List<String> identifiers = ref.getIdentifiers();  // 登记号列表
 * }
 *
 * // 补充对象（图表、数据集、多媒体）
 * List<SupplementalObject> supplements = publication.getSupplementalObjects();
 * for (SupplementalObject obj : supplements) {
 *     String type = obj.getType();  // keyword, figure, dataset, video
 *     List<ObjectParam> params = obj.getParams();
 * }
 *
 * // 相关项目（更正、撤稿、评论、转载）
 * List<RelatedItem> relatedItems = publication.getRelatedItems();
 * for (RelatedItem item : relatedItems) {
 *     String relationType = item.getRelationType();  // retraction-of, erratum-in, comment-on 等
 *     String citation = item.getCitation();
 *     String identifier = item.getIdentifier();
 * }
 *
 * // 化学物质列表
 * List<Substance> substances = publication.getSubstances();
 * for (Substance substance : substances) {
 *     String name = substance.getName();
 *     String registryNumber = substance.getRegistryNumber();  // CAS 号
 *     String vocabularyId = substance.getVocabularyId();
 * }
 *
 * // 基因符号列表
 * List<String> genes = publication.getGenes();
 *
 * // 示例 4: 访问元数据和出版历史
 * // 出版物元数据
 * PublicationMetadata metadata = publication.getMetadata();
 * if (metadata != null) {
 *     String indexingMethod = metadata.getIndexingMethod();  // Automated, Manual
 *     String owner = metadata.getOwner();  // NLM, NASA 等
 *     String status = metadata.getStatus();  // MEDLINE, PubMed, In-Process
 * }
 *
 * // 发布历史时间线
 * List<PublicationHistoryEvent> history = publication.getPublicationHistory();
 * for (PublicationHistoryEvent event : history) {
 *     String status = event.getStatus();  // received, accepted, epublish, pubmed 等
 *     String date = event.getYear() + "-" + event.getMonth() + "-" + event.getDay();
 * }
 *
 * // 多种日期
 * PublicationDates dates = publication.getDates();
 * if (dates != null) {
 *     LocalDate published = dates.getPublished();
 *     LocalDate electronic = dates.getElectronic();
 *     LocalDate received = dates.getReceived();
 *     LocalDate accepted = dates.getAccepted();
 *     LocalDate completed = dates.getCompleted();  // v0.1.0 新增
 * }
 * }</pre>
 *
 * <h2>v0.1.0 重大变更</h2>
 *
 * <h3>模型重构：从通用学术出版物到医学领域专用</h3>
 *
 * <ul>
 *   <li><b>移除</b>: {@code convertSubjects()} → 替换为 {@code convertMeshHeadings()}
 *   <li><b>重构</b>: {@code convertPagination()} → 返回结构化对象而非 String
 *   <li><b>增强</b>: {@code convertAuthors()} → 新增 valid 字段映射
 *   <li><b>增强</b>: {@code convertFunding()} → funderIdentifier → funderAcronym
 *   <li><b>增强</b>: {@code extractPublicationDates()} → 新增 completed 日期
 * </ul>
 *
 * <h3>新增转换方法（7 个）</h3>
 *
 * <ol>
 *   <li>{@code convertSupplMeshNames()} - 补充 MeSH 概念
 *   <li>{@code convertInvestigators()} - 研究者信息
 *   <li>{@code convertPersonalNameSubjects()} - 人物主题
 *   <li>{@code convertExternalReferences()} - 外部数据库引用
 *   <li>{@code convertSupplementalObjects()} - 补充材料
 *   <li>{@code convertReferences()} - 参考文献列表
 *   <li>{@code convertRelatedItems()} - 相关条目
 * </ol>
 *
 * <h3>迁移指南</h3>
 *
 * <pre>{@code
 * // 旧代码（已废弃）
 * List<Subject> subjects = publication.getSubjects();
 * String pagination = publication.getPagination();
 *
 * // 新代码（推荐）
 * // 1. 使用 MeSH 特定字段
 * List<MeshHeading> meshHeadings = publication.getMeshHeadings();
 * List<SupplMeshName> supplMeshNames = publication.getSupplMeshNames();
 *
 * // 2. 使用结构化页码对象
 * Pagination pagination = publication.getPagination();
 * if (pagination != null) {
 *     String startPage = pagination.getStartPage();
 *     String endPage = pagination.getEndPage();
 *     String medlinePgn = pagination.getMedlinePgn();
 * }
 *
 * // 3. 使用新增的医学领域字段
 * List<Investigator> investigators = publication.getInvestigators();
 * List<PersonalNameSubject> personalNameSubjects = publication.getPersonalNameSubjects();
 * List<Reference> references = publication.getReferences();
 * Integer refCount = publication.getNumberOfReferences();
 * }</pre>
 *
 * <h2>医学领域标准对齐</h2>
 *
 * <ul>
 *   <li><b>MeSH</b> - 美国国家医学图书馆医学主题词表（Medical Subject Headings）
 *   <li><b>MEDLINE</b> - 医学出版物元数据标准（主要数据源）
 *   <li><b>PubMed</b> - PubMed/MEDLINE 数据模型和 XML DTD
 *   <li><b>CAS</b> - 化学文摘社注册号（Chemical Abstracts Service Registry Number）
 * </ul>
 *
 * <h2>技术依赖</h2>
 *
 * <ul>
 *   <li>{@code patra-common-model} - CanonicalPublication 规范化模型
 *   <li>{@code com.patra.starter.provenance.pubmed.model.response} - PubMed 响应模型（XML/JSON 映射）
 *   <li>{@code Jackson} - JSON/XML 序列化/反序列化
 *   <li>{@code Spring Utils} - 字符串和集合工具类
 * </ul>
 *
 * @since 0.1.0
 * @author linqibin
 * @see com.patra.common.model.CanonicalPublication
 * @see com.patra.starter.provenance.pubmed.converter.PubmedPublicationConverter
 * @see <a href="https://www.nlm.nih.gov/mesh/">MeSH - Medical Subject Headings</a>
 * @see <a href="https://www.nlm.nih.gov/bsd/mms/medlineelements.html">MEDLINE Data Elements</a>
 * @see <a href="https://www.ncbi.nlm.nih.gov/books/NBK3827/">PubMed XML DTD</a>
 */
package com.patra.starter.provenance.pubmed.converter;
