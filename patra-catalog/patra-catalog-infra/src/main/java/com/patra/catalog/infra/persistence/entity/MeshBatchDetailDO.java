package com.patra.catalog.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// MeSH 批次详情数据库实体，映射到表 `cat_mesh_batch_detail`。
/// 
/// 表结构：记录每个批次的处理详情，用于错误追踪和重试管理
/// 
/// 关键字段说明：
/// 
/// - `import_id` - 关联任务 ID（外键：cat_mesh_import_task.id）
///   - `table_name` - 表名
///   - `batch_num` - 批次序号（从 1 开始）
///   - `batch_size` - 批次大小（实际处理的记录数）
///   - `status` - 批次状态（PENDING/PROCESSING/SUCCESS/FAILED）
///   - `retry_count` - 重试次数（最多 3 次）
///   - `error_message` - 错误信息（失败时记录）
/// 
/// **设计原则**：
/// 
/// - 继承 {@link BaseDO}：获得10个审计字段
///   - 细粒度追踪：每个批次单独记录，便于定位问题
///   - 重试机制：记录重试次数，防止无限重试
///   - 性能监控：记录批次开始和结束时间，便于性能分析
/// 
/// **使用场景**：
/// 
/// - 失败重试：查询 status = 'FAILED' 且 retry_count < 3 的批次
///   - 错误分析：通过 error_message 分析失败原因
///   - 性能分析：计算批次处理时间（end_time - start_time）
///   - 进度恢复：查询最大 batch_num，从下一批次继续
/// 
/// @author linqibin
/// @since 0.2.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "cat_mesh_batch_detail")
public class MeshBatchDetailDO extends BaseDO {

  /// 关联任务 ID（外键：cat_mesh_import_task.id）
  @TableField("import_id")
  private Long importId;

  /// 表名（如 "cat_mesh_descriptor"）
  @TableField("table_name")
  private String tableName;

  /// 批次序号（从 1 开始）
  @TableField("batch_num")
  private Integer batchNum;

  /// 批次大小（实际处理的记录数）
  @TableField("batch_size")
  private Integer batchSize;

  /// 批次状态（PENDING/PROCESSING/SUCCESS/FAILED）
  @TableField("status")
  private String status;

  /// 重试次数（最多 3 次）
  @TableField("retry_count")
  private Integer retryCount;

  /// 错误信息（失败时记录，最多保存1000字符）
  @TableField("error_message")
  private String errorMessage;

  /// 开始时间（批次开始处理时记录）
  @TableField("start_time")
  private Instant startTime;

  /// 结束时间（批次处理完成时记录）
  @TableField("end_time")
  private Instant endTime;
}
