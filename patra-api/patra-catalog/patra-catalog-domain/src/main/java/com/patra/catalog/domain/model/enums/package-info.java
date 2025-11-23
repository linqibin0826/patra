/// 领域枚举包。
///
/// 包含 Catalog 领域的所有枚举类型，定义业务状态、类型、分类等固定值集合。
///
/// ## 职责
///
/// - **类型定义**：定义领域概念的有限取值范围（如任务状态、数据类型）
///   - **业务规则**：封装状态转换规则和业务判断逻辑
///   - **显示名称**：提供中英文双语显示名称，支持国际化
///   - **编码映射**：提供 code ↔ enum 的双向转换方法
///
/// ## 核心组件
///
/// ### MeSH 导入相关
///
/// - {@link com.patra.catalog.domain.model.enums.MeshImportTaskStatus} - MeSH 导入任务状态
///
/// - 定义任务生命周期：PENDING → PROCESSING → SUCCESS/FAILED/CANCELLED
///       - 提供状态判断：isTerminal()、canRetry()、canCancel()
///   - {@link com.patra.catalog.domain.model.enums.MeshDataType} - MeSH 数据类型
///
/// - 定义 5 种数据类型：QUALIFIER、DESCRIPTOR、TREE_NUMBER、ENTRY_TERM、CONCEPT
///       - 定义导入顺序：importOrder（1-5，必须按顺序导入）
///   - {@link com.patra.catalog.domain.model.enums.MeshTableImportStatus} - MeSH 表导入状态
///
/// - 定义表级别状态：PENDING、PROCESSING、COMPLETED、FAILED
///   - {@link com.patra.catalog.domain.model.enums.MeshBatchStatus} - MeSH 批次导入状态
///
/// - 定义批次级别状态：PENDING、PROCESSING、COMPLETED、FAILED
///
/// ### MeSH 主题词相关
///
/// - {@link com.patra.catalog.domain.model.enums.DescriptorClass} - 主题词分类
///
/// - 定义主题词的 4 种分类：1（最常用）、2（常用）、3（不常用）、4（罕见）
///   - {@link com.patra.catalog.domain.model.enums.LexicalTag} - 词汇标签
///
/// - 定义入口术语的词汇属性：ABB（缩写）、ACR（首字母缩略词）等
///
/// ### 文献相关（规划中）
///
/// - {@link com.patra.catalog.domain.model.enums.PublicationStatus} - 文献状态
/// - {@link com.patra.catalog.domain.model.enums.VenueType} - 期刊类型
/// - {@link com.patra.catalog.domain.model.enums.OaStatus} - 开放获取状态
/// - {@link com.patra.catalog.domain.model.enums.IssnType} - ISSN 类型
/// - {@link com.patra.catalog.domain.model.enums.MediaType} - 媒体类型
/// - {@link com.patra.catalog.domain.model.enums.AffiliationType} - 机构类型
///
/// ## 设计原则
///
/// - **不可变性**：枚举类型天然不可变，字段使用 final 修饰
///   - **类型安全**：避免使用字符串或整数魔法值
///   - **业务语义**：枚举名和字段名清晰表达业务含义
///   - **扩展性**：通过字段扩展枚举信息（displayName、code、importOrder）
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：使用 MeshImportTaskStatus 枚举
/// MeshImportTaskStatus status = MeshImportTaskStatus.PROCESSING;
///
/// // 判断任务是否可以重试
/// if (status.canRetry()) {
///     log.info("任务可以重试");
/// }
///
/// // 获取显示名称（中文）
/// String displayName = status.getDisplayName(); // "处理中"
///
/// // 获取编码（用于 API 返回）
/// String code = status.getCode(); // "processing"
///
/// // 从编码反向查找枚举
/// MeshImportTaskStatus parsedStatus = MeshImportTaskStatus.fromCode("failed");
/// assert parsedStatus == MeshImportTaskStatus.FAILED;
///
/// // 示例 2：使用 MeshDataType 枚举（定义导入顺序）
/// List<String> importOrder = MeshDataType.getAllCodes();
/// // ["qualifier", "descriptor", "tree-number", "entry-term", "concept"]
///
/// for (String code : importOrder) {
///     MeshDataType type = MeshDataType.fromCode(code);
///     log.info("导入表 [{}]，顺序：{}", type.getDisplayName(), type.getImportOrder());
///     // 导入表 [限定词]，顺序：1
///     // 导入表 [主题词]，顺序：2
///     // ...
/// }
///
/// // 示例 3：枚举在聚合根中的使用
/// public class MeshImportAggregate {
///     private MeshImportTaskStatus status; // 使用枚举类型，而非 String
///
///     public void startImport() {
///         // 状态转换
///         if (this.status != MeshImportTaskStatus.PENDING) {
///             throw new IllegalStateException("只有 PENDING 状态的任务可以启动");
///         }
///         this.status = MeshImportTaskStatus.PROCESSING;
///     }
/// }
/// ```
///
/// ## 枚举与数据库映射
///
/// **推荐做法**：使用 `@Enumerated(EnumType.STRING)` 存储枚举的 name()：
///
/// ```java
/// @Entity
/// public class MeshImportTaskDO {
///     @Enumerated(EnumType.STRING)
///     @Column(name = "status", length = 20)
///     private MeshImportTaskStatus status;
/// }
/// ```
///
/// **优势**：
///
/// - 数据库可读性强（存储 "PROCESSING"，而非 1）
/// - 避免枚举顺序变更导致的数据错误
/// - 支持枚举新增（无需修改数据库）
///
/// ## 架构位置
///
/// **Domain 层 - 领域基础类型**：
///
/// - 属于领域模型的核心组成部分
/// - 被聚合根、实体、值对象、Repository 使用
/// - 不依赖其他层（纯 Java 枚举）
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.domain.model.enums;
