/// 领域实体包。
///
/// 包含聚合内的实体对象（Entity），这些实体有唯一标识，但不是聚合根。
///
/// ## 职责
///
/// - **聚合内实体**：作为聚合根的组成部分，不能脱离聚合根独立存在
///   - **唯一标识**：拥有 ID 字段，支持实体跟踪和更新
///   - **业务行为**：包含实体自身的业务逻辑和不变量
///   - **生命周期管理**：由聚合根负责创建、修改、删除
///
/// ## 核心组件
///
/// ### MeSH 主题词相关实体
///
/// - {@link com.patra.catalog.domain.model.entity.MeshTreeNumber} - MeSH 树形编号实体
///
/// - 从属于 {@link com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate}
///       - 一个主题词包含多个树形编号（如 "C01.123.456"、"C02.789.012"）
///       - 树形编号表示主题词在 MeSH 层次结构中的位置
///   - {@link com.patra.catalog.domain.model.entity.MeshEntryTerm} - MeSH 入口术语实体
///
/// - 从属于 {@link com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate}
///       - 一个主题词包含多个入口术语（同义词、变体）
///       - 支持词汇标签（ABB=缩写、ACR=首字母缩略词）
///   - {@link com.patra.catalog.domain.model.entity.MeshConcept} - MeSH 概念实体
///
/// - 从属于 {@link com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate}
///       - 一个主题词包含多个概念（平均 5-6 个）
///       - 首选概念（isPreferred=true）是主题词的主要含义
///       - 化学物质概念包含 CAS 号等注册号信息
///
/// ### 文献相关实体（规划中）
///
/// - {@link com.patra.catalog.domain.model.entity.VenueInstance} - 期刊实例实体
///
/// - 从属于 {@link com.patra.catalog.domain.model.aggregate.VenueAggregate}
///       - 表示期刊的某个版本（如纸质版、电子版）
///
/// ## 实体 vs 值对象
///
/// | 特性 | 实体（Entity） | 值对象（Value Object） |
/// |------|---------------|----------------------|
/// | 标识符 | 有 ID，通过 ID 判断相等性 | 无 ID，通过属性判断相等性 |
/// | 可变性 | 可变（状态可修改） | 不可变（属性不可修改） |
/// | 生命周期 | 由聚合根管理 | 由聚合根或实体持有 |
/// | 独立性 | 不能脱离聚合根 | 可以在不同聚合间共享 |
/// | 示例 | MeshConcept、MeshEntryTerm | TableProgress、MeshUI、MeshImportId |
///
/// ## 设计原则
///
/// - **依赖聚合根**：实体不能脱离聚合根独立访问，外部只能通过聚合根操作实体
///   - **唯一标识**：每个实体有 ID 字段（由 Repository 在持久化时分配）
///   - **业务逻辑**：实体包含自身的业务规则和不变量验证
///   - **不可直接持久化**：实体的持久化由聚合根协调（Aggregate Root 负责）
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：聚合根包含实体（正确做法）
/// public class MeshDescriptorAggregate {
///     private Long id;
///     private MeshUI descriptorUi;
///     private String name;
///
///     // 实体集合
///     private List<MeshTreeNumber> treeNumbers; // 树形编号实体列表
///     private List<MeshEntryTerm> entryTerms;   // 入口术语实体列表
///     private List<MeshConcept> concepts;        // 概念实体列表
///
///     // 通过聚合根添加实体
///     public void addTreeNumber(String treeNumber) {
///         MeshTreeNumber entity = MeshTreeNumber.create(this.id, treeNumber);
///         this.treeNumbers.add(entity);
///     }
///
///     // 通过聚合根查询实体
///     public List<MeshTreeNumber> getTreeNumbers() {
///         return Collections.unmodifiableList(treeNumbers);
///     }
/// }
///
/// // 示例 2：错误做法（直接访问实体）
/// // ❌ 不要这样做：
/// MeshTreeNumber treeNumber = treeNumberRepository.findById(123); // 错误！
/// // ✅ 正确做法：通过聚合根访问
/// MeshDescriptorAggregate descriptor = descriptorRepository.findById(descriptorId);
/// List<MeshTreeNumber> treeNumbers = descriptor.getTreeNumbers();
///
/// // 示例 3：实体的创建（通过工厂方法）
/// public class MeshConcept {
///     public static MeshConcept create(Long descriptorId, MeshUI conceptUi, String name, boolean isPreferred) {
///         // 验证业务规则
///         Assert.notNull(descriptorId, "主题词 ID 不能为空");
///         Assert.notNull(conceptUi, "概念 UI 不能为空");
///         Assert.isTrue(conceptUi.isConcept(), "UI 必须是概念类型");
///
///         // 创建实体
///         MeshConcept concept = new MeshConcept();
///         concept.descriptorId = descriptorId;
///         concept.conceptUi = conceptUi;
///         concept.conceptName = name;
///         concept.isPreferred = isPreferred;
///         return concept;
///     }
/// }
/// ```
///
/// ## 持久化策略
///
/// **聚合内实体的持久化由聚合根协调**：
///
/// ```java
/// // Repository 接口（只操作聚合根）
/// public interface MeshDescriptorRepository {
///     MeshDescriptorAggregate save(MeshDescriptorAggregate aggregate);
///     Optional<MeshDescriptorAggregate> findById(Long id);
/// }
///
/// // Repository 实现（保存聚合根及其实体）
/// @Repository
/// public class MeshDescriptorRepositoryImpl implements MeshDescriptorRepository {
///
///     @Override
///     @Transactional
///     public MeshDescriptorAggregate save(MeshDescriptorAggregate aggregate) {
///         // 1. 保存聚合根（Descriptor）
///         MeshDescriptorDO descriptorDO = converter.toDescriptorDO(aggregate);
///         descriptorMapper.insertOrUpdate(descriptorDO);
///
///         // 2. 保存实体（TreeNumber、EntryTerm、Concept）
///         List<MeshTreeNumberDO> treeNumberDOs = converter.toTreeNumberDOs(aggregate);
///         treeNumberMapper.batchInsert(treeNumberDOs);
///
///         List<MeshEntryTermDO> entryTermDOs = converter.toEntryTermDOs(aggregate);
///         entryTermMapper.batchInsert(entryTermDOs);
///
///         List<MeshConceptDO> conceptDOs = converter.toConceptDOs(aggregate);
///         conceptMapper.batchInsert(conceptDOs);
///
///         // 3. 返回聚合根
///         return aggregate;
///     }
/// }
/// ```
///
/// ## 架构位置
///
/// **Domain 层 - 实体**：
///
/// - 属于领域模型的核心组成部分
/// - 被聚合根持有和管理
/// - 不依赖其他层（纯 Java 对象 + Domain 值对象/枚举）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.domain.model.entity;
