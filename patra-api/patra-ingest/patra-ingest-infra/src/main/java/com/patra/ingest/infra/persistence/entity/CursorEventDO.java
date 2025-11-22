package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 游标推进事件数据库实体,映射到表 `ing_cursor_event`。
/// 
/// 表结构: 仅追加审计日志,记录从旧游标值到新游标值的每次推进,支持重放和端到端追踪。
/// 
/// 关键字段说明:
/// 
/// - `idempotent_key` 具有唯一约束(UK: uk_cur_evt_idem)以防止重复写入
///   - 同时持久化时间/数值规范化值(prev/new + observed),用于跨类型排序和统计
///   - 冗余血缘字段链(schedule → plan → slice → task → run → batch)用于故障排查
///   - `direction_code` 标记回填(BACKFILL)与前向(FORWARD)推进
/// 
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ing_cursor_event")
public class CursorEventDO extends BaseDO {

  /// 数据源代码
  @TableField("provenance_code")
  private String provenanceCode;

  /// 操作类型代码
  @TableField("operation_code")
  private String operationCode;

  /// 游标逻辑键
  @TableField("cursor_key")
  private String cursorKey;

  /// 命名空间范围代码
  @TableField("namespace_scope_code")
  private String namespaceScopeCode;

  /// 命名空间键
  @TableField("namespace_key")
  private String namespaceKey;

  /// 游标类型代码(字典: ing_cursor_type)
  @TableField("cursor_type_code")
  private String cursorTypeCode;

  /// 推进前的原始值
  @TableField("prev_value")
  private String prevValue;

  /// 推进后的原始值
  @TableField("new_value")
  private String newValue;

  /// 推进期间观察到的最大值
  @TableField("observed_max_value")
  private String observedMaxValue;

  /// 推进前的规范化时间
  @TableField("prev_instant")
  private Instant prevInstant;

  /// 推进后的规范化时间
  @TableField("new_instant")
  private Instant newInstant;

  /// 推进前的规范化数值
  @TableField("prev_numeric")
  private BigDecimal prevNumeric;

  /// 推进后的规范化数值
  @TableField("new_numeric")
  private BigDecimal newNumeric;

  /// 覆盖窗口起始时间(UTC,含)
  @TableField("window_from")
  private Instant windowFrom;

  /// 覆盖窗口结束时间(UTC,不含)
  @TableField("window_to")
  private Instant windowTo;

  /// 推进方向代码(FORWARD/BACKFILL)
  @TableField("direction_code")
  private String directionCode;

  /// 事件幂等键(唯一约束: uk_cur_evt_idem)
  @TableField("idempotent_key")
  private String idempotentKey;

  /// 关联的最近调度实例 ID
  @TableField("schedule_instance_id")
  private Long scheduleInstanceId;

  /// 关联的最近计划 ID
  @TableField("plan_id")
  private Long planId;

  /// 关联的最近切片 ID
  @TableField("slice_id")
  private Long sliceId;

  /// 关联的最近任务 ID
  @TableField("task_id")
  private Long taskId;

  /// 关联的最近运行 ID
  @TableField("run_id")
  private Long runId;

  /// 关联的最近批次 ID
  @TableField("batch_id")
  private Long batchId;

  /// 推进时使用的表达式哈希
  @TableField("expr_hash")
  private String exprHash;
}
