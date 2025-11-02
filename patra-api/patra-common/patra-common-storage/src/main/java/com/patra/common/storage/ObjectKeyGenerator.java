package com.patra.common.storage;

/**
 * 生成标准化对象存储键的策略接口。
 *
 * <p>实现定义特定的键生成模式(例如,日期分区、层次化、自定义), 同时确保所有 Papertrace 微服务的结构一致性。
 *
 * <p><b>设计模式</b>:策略模式 — 允许在运行时交换不同的键生成策略或按服务/用例配置。
 *
 * <p><b>线程安全</b>:实现应该是无状态且线程安全的,以支持整个应用程序中的单例使用。
 *
 * <p><b>标准实现</b>:
 *
 * <ul>
 *   <li>{@link DatePartitionedKeyGenerator} — 按日分区的键({@code service/type/yyyy/MM/dd/id.ext})
 *   <li>未来:{@code MonthPartitionedKeyGenerator}、{@code HierarchicalKeyGenerator} 等。
 * </ul>
 *
 * @author linqibin
 * @see ObjectKeyContext
 * @see DatePartitionedKeyGenerator
 * @since 0.1.0
 */
@FunctionalInterface
public interface ObjectKeyGenerator {

  /**
   * 从提供的上下文生成对象存储键。
   *
   * <p>返回的键应该是相对路径(不包含存储桶名称),在存储桶内唯一标识对象。
   *
   * <p><b>规范化要求</b>:
   *
   * <ul>
   *   <li>服务名称应为小写
   *   <li>业务类型应遵循 kebab-case 约定
   *   <li>日期段应使用一致的格式(yyyy/MM/dd)
   *   <li>路径分隔符应使用正斜杠({@code /})
   * </ul>
   *
   * @param context 包含所有键生成参数的不可变上下文
   * @return 生成的对象键路径(例如,{@code ingest/literature-batch/2025/10/26/pubmed-123.json})
   * @throws IllegalArgumentException 如果上下文包含无效数据
   */
  String generate(ObjectKeyContext context);
}
