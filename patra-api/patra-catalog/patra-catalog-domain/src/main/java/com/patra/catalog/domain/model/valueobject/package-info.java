/// Catalog 值对象包。
/// 
/// 包含 Catalog 领域的所有值对象（Value Object），提供不可变的、值语义的领域概念。
/// 
/// ## 职责
/// 
/// - 封装领域概念：将相关属性封装为单一值对象，提供类型安全和语义清晰性
///   - 确保不可变性：值对象一旦创建不可修改，任何修改返回新实例
///   - 提供业务计算：值对象包含与自身相关的业务逻辑和计算方法
///   - 强类型 ID：使用强类型 ID 替代原始类型（Long），提供编译期类型安全
/// 
/// ## 核心组件
/// 
/// - {@link com.patra.catalog.domain.model.valueobject.MeshImportId} - MeSH 导入任务强类型 ID
///     
/// - 封装 Long 类型的任务 ID
///       - 提供类型安全，防止 ID 混淆（如 DescriptorId vs MeshImportId）
///   - {@link com.patra.catalog.domain.model.valueobject.TableProgress} - 表导入进度值对象
///     
/// - 记录单张表的导入进度（已处理数/总数、状态、最后批次号）
///       - 支持断点续传（保存最后处理批次号）
///       - 提供进度计算方法（getProgressPercentage()）
///       - 不可变性：任何修改（updateProgress、incrementFailedCount）返回新实例
///   - {@link com.patra.catalog.domain.model.valueobject.FailedBatch} - 失败批次值对象
///     
/// - 记录失败批次的详细信息（批次号、失败原因、失败时间、重试次数）
///       - 用于失败批次追踪和重试
///   - 其他强类型 ID：DescriptorId、QualifierId、TreeNumberId、EntryTermId、ConceptId
/// 
/// ## 设计原则
/// 
/// - **不可变性**：值对象使用 `@Value` 注解或 `record` 实现不可变性
///   - **值语义**：相等性基于属性值（equals 比较所有字段），不基于引用
///   - **自包含**：值对象包含与自身相关的业务逻辑和验证规则
///   - **无副作用**：值对象的方法不修改状态，返回新实例或计算结果
///   - **可替换性**：值对象可以自由替换，不影响实体的身份
/// 
/// ## 使用示例
/// ```java
/// // 示例 1：强类型 ID 提供类型安全
/// MeshImportId importId = MeshImportId.of(1234567890L);
/// DescriptorId descriptorId = DescriptorId.of(9876543210L);
/// 
/// // 编译期类型检查，防止 ID 混淆
/// meshImportPort.findById(importId); // 正确
/// // meshImportPort.findById(descriptorId); // 编译错误！类型不匹配
/// 
/// // 示例 2：不可变值对象的修改返回新实例
/// TableProgress progress = TableProgress.builder()
///     .tableName("descriptor")
///     .totalCount(35000)
///     .processedCount(0)
///     .failedCount(0)
///     .status(MeshTableImportStatus.NOT_STARTED)
///     .lastBatchNum(0)
///     .lastUpdateTime(Instant.now())
///     .build();
/// 
/// // 更新进度（返回新实例）
/// TableProgress updated = progress.updateProgress(5000, 5);
/// 
/// // 原实例不变
/// assert progress.getProcessedCount() == 0;
/// assert updated.getProcessedCount() == 5000;
/// 
/// // 计算进度百分比
/// Double percentage = updated.getProgressPercentage(); // 14.29%
/// 
/// // 示例 3：失败批次值对象
/// FailedBatch failedBatch = FailedBatch.builder()
///     .batchId(123L)
///     .tableName("descriptor")
///     .batchNum(10)
///     .failureReason("网络超时")
///     .failureTime(Instant.now())
///     .retryCount(2)
///     .build();
/// ```
/// 
/// @since 0.2.0
/// @author Patra Team
package com.patra.catalog.domain.model.valueobject;
