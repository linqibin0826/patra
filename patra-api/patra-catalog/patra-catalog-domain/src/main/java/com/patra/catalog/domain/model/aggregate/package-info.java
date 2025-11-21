/**
 * Catalog 聚合根包。
 *
 * <p>包含 Catalog 领域的所有聚合根，每个聚合根管理一组相关实体和值对象，确保数据一致性边界。
 *
 * <h2>职责</h2>
 * <ul>
 *   <li>定义聚合边界：每个聚合根封装一组相关对象，保护聚合内一致性
 *   <li>管理生命周期：聚合根负责创建、修改、删除聚合内的实体和值对象
 *   <li>强制业务规则：聚合根包含业务逻辑和不变量验证，防止非法状态
 *   <li>发布领域事件：状态变更时发布领域事件，通知其他聚合或外部系统
 * </ul>
 *
 * <h2>核心组件</h2>
 * <ul>
 *   <li>{@link com.patra.catalog.domain.model.aggregate.MeshImportAggregate} - MeSH 导入任务聚合根
 *     <ul>
 *       <li>管理导入任务的完整生命周期（PENDING → PROCESSING → SUCCESS/FAILED）
 *       <li>追踪各表的导入进度（TableProgress 值对象列表）
 *       <li>提供断点续传能力（记录最后处理批次号）
 *       <li>发布任务状态变更事件（Started、Completed、Failed）
 *     </ul>
 *   </li>
 *   <li>{@link com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate} - MeSH 主题词聚合根
 *     <ul>
 *       <li>管理主题词的完整结构（Descriptor + 关联的 Qualifier、TreeNumber、EntryTerm、Concept）
 *       <li>维护 MeSH 层次结构（树形编号）
 *       <li>支持同义词查询（入口术语）
 *     </ul>
 *   </li>
 *   <li>{@link com.patra.catalog.domain.model.aggregate.AuthorAggregate} - 作者聚合根（规划中）
 *   <li>{@link com.patra.catalog.domain.model.aggregate.AffiliationAggregate} - 机构聚合根（规划中）
 *   <li>{@link com.patra.catalog.domain.model.aggregate.VenueAggregate} - 期刊聚合根（规划中）
 *   <li>{@link com.patra.catalog.domain.model.aggregate.PublicationAggregate} - 文献聚合根（规划中）
 * </ul>
 *
 * <h2>设计原则</h2>
 * <ul>
 *   <li><b>单一聚合根原则</b>：每个聚合只有一个聚合根作为入口，外部只能通过聚合根访问聚合内对象
 *   <li><b>小聚合原则</b>：聚合应该尽可能小，只包含必须保持一致性的对象（如 MeshImportAggregate 不包含批次详情）
 *   <li><b>引用其他聚合使用 ID</b>：聚合之间通过强类型 ID 引用，避免直接持有对象引用
 *   <li><b>事务边界</b>：一个事务只能修改一个聚合根，跨聚合操作通过领域事件实现最终一致性
 *   <li><b>富领域模型</b>：聚合根包含业务逻辑，避免贫血模型（Anemic Domain Model）
 * </ul>
 *
 * <h2>使用示例</h2>
 * <pre>{@code
 * // 示例 1：创建 MeSH 导入任务聚合根
 * MeshImportAggregate task = new MeshImportAggregate(
 *     null,
 *     "2025年MeSH数据导入",
 *     MeshImportTaskStatus.PENDING,
 *     null, null,
 *     "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
 *     null, null,
 *     List.of(),
 *     0, 0, 0, null
 * );
 *
 * // 开始导入（状态转换 + 发布事件）
 * task.startImport(); // PENDING → PROCESSING + MeshImportStarted 事件
 *
 * // 更新进度（值对象不可变，返回新实例）
 * task.updateTableProgress("descriptor", 5000, 5);
 *
 * // 标记完成（发布 MeshImportCompleted 事件）
 * task.markAsCompleted();
 *
 * // 示例 2：查询 MeSH 主题词聚合根
 * MeshDescriptorAggregate descriptor = meshDescriptorPort.findById(descriptorId);
 * List<MeshTreeNumber> treeNumbers = descriptor.getTreeNumbers();
 * List<MeshEntryTerm> synonyms = descriptor.getEntryTerms();
 * }</pre>
 *
 * @since 0.2.0
 * @author Patra Team
 */
package com.patra.catalog.domain.model.aggregate;
