package com.patra.common.storage;

/// 生成标准化对象存储键的策略接口。
/// 
/// 实现定义特定的键生成模式(例如,日期分区、层次化、自定义), 同时确保所有 Patra 微服务的结构一致性。
/// 
/// **设计模式**:策略模式 — 允许在运行时交换不同的键生成策略或按服务/用例配置。
/// 
/// **线程安全**:实现应该是无状态且线程安全的,以支持整个应用程序中的单例使用。
/// 
/// **标准实现**:
/// 
/// - {@link DatePartitionedKeyGenerator} — 按日分区的键(`service/type/yyyy/MM/dd/id.ext`)
///   - 未来:`MonthPartitionedKeyGenerator`、`HierarchicalKeyGenerator` 等。
/// 
/// @author linqibin
/// @see ObjectKeyContext
/// @see DatePartitionedKeyGenerator
/// @since 0.1.0
@FunctionalInterface
public interface ObjectKeyGenerator {

  /// 从提供的上下文生成对象存储键。
/// 
/// 返回的键应该是相对路径(不包含存储桶名称),在存储桶内唯一标识对象。
/// 
/// **规范化要求**:
/// 
/// - 服务名称应为小写
///   - 业务类型应遵循 kebab-case 约定
///   - 日期段应使用一致的格式(yyyy/MM/dd)
///   - 路径分隔符应使用正斜杠(`/`)
/// 
/// @param context 包含所有键生成参数的不可变上下文
/// @return 生成的对象键路径(例如,`ingest/publication-batch/2025/10/26/pubmed-123.json`)
/// @throws IllegalArgumentException 如果上下文包含无效数据
  String generate(ObjectKeyContext context);
}
