/// MeSH 导入数据验证器包。
///
/// 包含 MeSH 导入过程的数据验证逻辑，确保导入数据的完整性和准确性。
///
/// ## 职责
///
/// - **数据量验证**：验证实际导入量与预期量是否一致
///   - **差异计算**：计算实际数量与预期数量的差异百分比
///   - **警告生成**：差异超过阈值时生成警告信息
///   - **不阻塞任务**：验证失败生成警告，不阻止任务完成
///
/// ## 核心组件
///
/// - {@link com.patra.catalog.app.usecase.meshimport.validator.MeshDataValidator} - MeSH 数据量验证器
///
/// - 验证 5 张表的数据量（descriptor、qualifier、tree-number、entry-term、concept）
///       - 计算差异百分比：|实际 - 预期| / 预期 × 100%
///       - 生成警告：差异超过 5%（可配置）
///
/// ## 验证规则
///
/// **预期数量配置**（从 {@link com.patra.catalog.app.config.MeshImportConfig} 读取）：
///
/// | 表名 | 预期数量 | 差异阈值 | 实际范围（±5%） |
/// |------|---------|---------|----------------|
/// | descriptor | 35,000 | 5% | 33,250 - 36,750 |
/// | qualifier | 80 | 5% | 76 - 84 |
/// | tree-number | 80,000 | 5% | 76,000 - 84,000 |
/// | entry-term | 250,000 | 5% | 237,500 - 262,500 |
/// | concept | 180,000 | 5% | 171,000 - 189,000 |
///
/// **验证结果**：
///
/// - `isValid = true`：所有表差异都在阈值内
/// - `isValid = false`：至少有一个表差异超过阈值
/// - `warnings`：差异超过阈值的表的详细信息列表
///
/// ## 使用示例
///
/// ```java
/// // 示例 1：在 Orchestrator 中验证数据量
/// @Service
/// @RequiredArgsConstructor
/// public class MeshImportOrchestrator {
///
///     private final MeshDataValidator validator;
///
///     public void completeImport(MeshImportAggregate task) {
///         // 收集实际导入量
///         Map<String, Integer> actualCounts = Map.of(
///             "descriptor", 35200,
///             "qualifier", 82,
///             "tree-number", 81000,
///             "entry-term", 252000,
///             "concept", 182000
///         );
///
///         // 验证数据量
///         ValidationResult result = validator.validateDataCounts(actualCounts);
///
///         if (result.isValid()) {
///             log.info("数据量验证通过");
///         } else {
///             log.warn("数据量验证发现 {} 个警告：{}", result.warningCount(), result.warnings());
///         }
///
///         // 标记任务完成（即使验证失败也完成任务）
///         task.markAsCompleted();
///     }
/// }
///
/// // 示例 2：验证结果示例
/// // 如果 descriptor 实际导入 38000 条（超过预期 35000 的 8.57%）
/// // 输出警告：
/// // "表 [descriptor] 数据量差异超过 5.0%: 预期 35000, 实际 38000 (差异 8.57%)"
/// ```
///
/// ## 验证结果记录
///
/// {@link com.patra.catalog.app.usecase.meshimport.validator.MeshDataValidator.ValidationResult} 封装验证结果：
///
/// ```java
/// public record ValidationResult(boolean isValid, List<String> warnings) {
///     public boolean hasWarnings() { ... }
///     public int warningCount() { ... }
/// }
/// ```
///
/// ## 设计原则
///
/// - **非阻塞验证**：验证失败不阻止任务完成，只生成警告
///   - **可配置阈值**：差异容忍度可通过配置调整（默认 5%）
///   - **详细日志**：记录每张表的验证结果，便于排查问题
///   - **不可变结果**：ValidationResult 使用 record，确保不可变
///
/// ## 架构位置
///
/// **App 层 - 数据验证**：
///
/// - 属于应用层的辅助组件
/// - 被 Orchestrator 调用，不直接暴露给外部
/// - 依赖 MeshImportConfig 获取预期值
///
/// @author linqibin
/// @since 0.1.0
package com.patra.catalog.app.usecase.meshimport.validator;
