package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// MeSH 表进度记录数据库实体，映射到表 `cat_mesh_table_progress`。
///
/// 表结构：跟踪每张表的导入进度，支持断点续传
///
/// 关键字段说明：
///
/// - `import_id` - 关联任务 ID（外键：cat_mesh_import_task.id）
///   - `table_name` - 表名（如 "cat_mesh_descriptor"、"cat_mesh_qualifier"）
///   - `last_batch_num` - 最后处理批次号（断点续传关键字段）
///   - `status` - 表状态（NOT_STARTED/IN_PROGRESS/COMPLETED/FAILED）
///   - `processed_count` - 已处理数（用于进度计算）
///   - `failed_count` - 失败数（用于错误统计）
///
/// **设计原则**：
///
/// - 继承 {@link BaseDO}：获得10个审计字段
///   - 断点续传：通过 `last_batch_num` 字段记录最后处理批次
///   - 进度追踪：记录总数、已处理数、失败数
///   - 独立状态：每张表有独立的状态，互不影响
///
/// **使用场景**：
///
/// - 任务中断后重启，从 `last_batch_num + 1` 继续处理
///   - 计算整体进度：sum(processed_count) / sum(total_count)
///   - 识别问题表：status = 'FAILED' 的记录
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_table_progress")
public class MeshTableProgressDO extends BaseDO {

  /// 关联任务 ID（外键：cat_mesh_import_task.id）
  @TableField("import_id")
  private Long importId;

  /// 表名（如 "cat_mesh_descriptor"）
  @TableField("table_name")
  private String tableName;

  /// 总记录数（预期值，导入前从配置或XML元数据获取）
  @TableField("total_count")
  private Integer totalCount;

  /// 实际总记录数（导入完成后设置，用于数据差异分析）
  @TableField("actual_total_count")
  private Integer actualTotalCount;

  /// 已处理数（成功导入的记录数）
  @TableField("processed_count")
  private Integer processedCount;

  /// 失败数（导入失败的记录数）
  @TableField("failed_count")
  private Integer failedCount;

  /// 表状态（NOT_STARTED/IN_PROGRESS/COMPLETED/FAILED）
  @TableField("status")
  private String status;

  /// 最后处理批次号（断点续传关键字段，从1开始）
  @TableField("last_batch_num")
  private Integer lastBatchNum;
}
