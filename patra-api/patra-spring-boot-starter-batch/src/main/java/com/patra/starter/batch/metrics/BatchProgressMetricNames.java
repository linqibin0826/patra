package com.patra.starter.batch.metrics;

/// Spring Batch 进度指标名称常量。
///
/// 定义补充 Spring Batch 内置指标的进度计数器（Counter）名称。
///
/// **命名规范**：
///
/// - 使用 `.` 分隔符（Prometheus 会自动转换为 `_`）
/// - 遵循 `{domain}.{component}.{metric}` 层级结构
/// - **不包含** `patra_` 前缀（由 OTel Collector 统一添加）
///
/// **与内置指标的关系**：
///
/// Spring Batch 内置指标（`spring.batch.*`）主要是 Timer 类型（耗时统计），
/// 本类定义的是 Counter 类型（数量统计），两者互补使用。
///
/// | 内置指标 | 类型 | 说明 |
/// |---------|------|------|
/// | `spring.batch.job` | Timer | Job 执行耗时 |
/// | `spring.batch.step` | Timer | Step 执行耗时 |
/// | `spring.batch.item.read` | Timer | Item 读取耗时 |
/// | `spring.batch.chunk.write` | Timer | Chunk 写入耗时 |
///
/// | 补充指标 | 类型 | 说明 |
/// |---------|------|------|
/// | `batch.step.items.read` | Counter | 累计读取数量 |
/// | `batch.step.items.written` | Counter | 累计写入数量 |
/// | `batch.step.items.skipped` | Counter | 累计跳过数量 |
/// | `batch.step.commits` | Counter | 累计提交次数 |
/// | `batch.step.rollbacks` | Counter | 累计回滚次数 |
///
/// @author linqibin
/// @since 0.1.0
public final class BatchProgressMetricNames {

  // ==================== 指标名称 ====================

  /// 累计读取数量指标。
  ///
  /// 记录 Step 执行过程中的总读取记录数。
  public static final String ITEMS_READ = "batch.step.items.read";

  /// 累计写入数量指标。
  ///
  /// 记录 Step 执行过程中的总写入记录数。
  public static final String ITEMS_WRITTEN = "batch.step.items.written";

  /// 累计跳过数量指标。
  ///
  /// 记录 Step 执行过程中因异常跳过的记录数。
  public static final String ITEMS_SKIPPED = "batch.step.items.skipped";

  /// 累计提交次数指标。
  ///
  /// 记录 Step 执行过程中的事务提交次数。
  public static final String COMMITS = "batch.step.commits";

  /// 累计回滚次数指标。
  ///
  /// 记录 Step 执行过程中的事务回滚次数。
  public static final String ROLLBACKS = "batch.step.rollbacks";

  // ==================== 标签名称 ====================
  // 注意：标签名需与 Spring Batch 内置指标保持一致，以便 Grafana 变量联动

  /// Job 名称标签。
  ///
  /// 与 Spring Batch 内置指标的 `spring.batch.job.name` 标签一致。
  public static final String TAG_JOB_NAME = "spring.batch.job.name";

  /// Step 名称标签。
  ///
  /// 与 Spring Batch 内置指标的 `spring.batch.step.name` 标签一致。
  public static final String TAG_STEP_NAME = "spring.batch.step.name";

  /// 私有构造函数，防止实例化。
  private BatchProgressMetricNames() {
    throw new UnsupportedOperationException("常量类不允许实例化");
  }
}
