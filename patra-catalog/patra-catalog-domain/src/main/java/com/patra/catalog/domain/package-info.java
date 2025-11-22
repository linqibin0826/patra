/// Catalog 领域模型包。
///
/// 包含医学文献目录管理的核心领域模型，包括 MeSH 主题词表、作者、机构、期刊等目录数据的聚合根、实体和值对象。
///
/// ## 职责
///
/// - 定义 MeSH 导入任务的完整生命周期管理（{@link com.patra.catalog.domain.model.aggregate.MeshImportAggregate}）
///   - 管理 MeSH 主题词表的层次结构和关联关系（Descriptor、Qualifier、TreeNumber、EntryTerm、Concept）
///   - 维护作者、机构、期刊等目录数据的聚合根和实体
///   - 提供领域事件以发布任务状态变更（{@link com.patra.catalog.domain.event}）
///   - 定义仓储接口和外部服务接口（Port）以支持依赖倒置（{@link com.patra.catalog.domain.port}）
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.domain.model.aggregate.MeshImportAggregate} - MeSH
// 导入任务聚合根，管理导入任务的完整生命周期和状态转换
///   - {@link com.patra.catalog.domain.model.aggregate.MeshDescriptorAggregate} - MeSH
// 主题词聚合根，包含树形编号、入口术语和概念
///   - {@link com.patra.catalog.domain.model.valueobject.TableProgress} - 表导入进度值对象，支持断点续传和进度计算
///   - {@link com.patra.catalog.domain.model.valueobject.MeshImportId} - 导入任务强类型 ID，提供类型安全
///   - {@link com.patra.catalog.domain.event.MeshImportStarted} - 导入任务启动事件
///   - {@link com.patra.catalog.domain.event.MeshImportCompleted} - 导入任务完成事件
///   - {@link com.patra.catalog.domain.event.MeshImportFailed} - 导入任务失败事件
///
/// ## 设计原则
///
/// - **纯 Java 实现**：不依赖任何框架（Spring、MyBatis），仅依赖 JDK + patra-common-core + Lombok + Hutool
///   - **六边形架构**：通过 Port 接口定义边界，实现依赖倒置（Infrastructure 层实现 Port 接口）
///   - **DDD 战术设计**：使用聚合根、实体、值对象、领域事件、强类型 ID 等战术模式
///   - **不可变值对象**：值对象使用 @Value 注解确保不可变性，修改返回新实例
///   - **富领域模型**：聚合根包含业务规则和状态转换逻辑，非贫血模型
///
/// ## 使用示例
/// ```java
/// // 创建导入任务聚合根
/// MeshImportAggregate aggregate = new MeshImportAggregate(
///     null, // ID 由仓储生成
///     "2025年MeSH数据导入",
///     MeshImportTaskStatus.PENDING,
///     null, null,
///     "https://nlmpubs.nlm.nih.gov/projects/mesh/MESH_FILES/xmlmesh/desc2025.xml",
///     null, null,
///     initializeTableProgressList(),
///     0, 0, 0, null
/// );
///
/// // 开始导入（状态转换：PENDING → PROCESSING）
/// aggregate.startImport();
///
/// // 更新表进度（断点续传）
/// aggregate.updateTableProgress("descriptor", 5000, 5);
///
/// // 标记任务完成（发布 MeshImportCompleted 事件）
/// aggregate.markAsCompleted();
///
/// // 获取领域事件
/// List<DomainEvent> events = aggregate.getDomainEvents();
/// ```
///
/// @since 0.1.0
/// @author Patra Team
package com.patra.catalog.domain;
