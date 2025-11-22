package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 采集计划数据库实体,映射到表 `ing_plan`。
///
/// 表结构: 表示单个采集批次蓝图,捕获数据源配置、表达式原型和切片策略。
///
/// 关键字段说明:
///
/// - `plan_key` 是可读的幂等键(唯一约束: uk_plan_key)
///   - `expr_proto_snapshot` 和 `provenance_config_snapshot` 以 JSON 快照形式存储,用于重放和比较
///   - `slice_strategy_code` + `slice_params` 决定如何派生子切片
///   - `window_spec` 以 JSON
///       存储窗口边界;支持多种策略(TIME/DATE/ID_RANGE/CURSOR_LANDMARK/VOLUME_BUDGET/SINGLE)
///
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_plan", autoResultMap = true)
public class PlanDO extends BaseDO {

  /// 关联的调度实例 ID
  @TableField("schedule_instance_id")
  private Long scheduleInstanceId;

  /// 外部幂等键(可读)
  @TableField("plan_key")
  private String planKey;

  /// 数据源代码冗余(用于按数据源分组)
  @TableField("provenance_code")
  private String provenanceCode;

  /// 操作类型代码(字典: ing_operation)
  @TableField("operation_code")
  private String operationCode;

  /// 表达式原型哈希(规范化 AST 指纹)
  @TableField("expr_proto_hash")
  private String exprProtoHash;

  /// 表达式原型快照(JSON AST,不含本地条件)
  @TableField(value = "expr_proto_snapshot", typeHandler = JacksonTypeHandler.class)
  private JsonNode exprProtoSnapshot;

  /// 数据源配置快照(JSON,运行时不变参数)
  @TableField(value = "provenance_config_snapshot", typeHandler = JacksonTypeHandler.class)
  private JsonNode provenanceConfigSnapshot;

  /// 数据源配置快照哈希(用于变更检测)
  @TableField("provenance_config_hash")
  private String provenanceConfigHash;

  /// 切片策略代码(TIME/DATE/ID_RANGE/CURSOR 等)
  @TableField("slice_strategy_code")
  private String sliceStrategyCode;

  /// 切片参数快照(JSON;策略特定细节)
  @TableField(value = "slice_params", typeHandler = JacksonTypeHandler.class)
  private JsonNode sliceParams;

  /// 窗口边界规格(JSON;模式因 slice_strategy_code 而异)
  @TableField(value = "window_spec", typeHandler = JacksonTypeHandler.class)
  private JsonNode windowSpec;

  /// TIME 策略的反规范化窗口起始时间戳(应用层维护)。
  ///
  /// 当 `slice_strategy_code = "TIME"` 时由应用层填充,以支持高效的时间范围查询。非 TIME 策略应设为 `null`。
  @TableField("window_from_ts")
  private Instant windowFromTs;

  /// TIME 策略的反规范化窗口结束时间戳(应用层维护)。
  ///
  /// 当 `slice_strategy_code = "TIME"` 时由应用层填充,以支持高效的时间范围查询。非 TIME 策略应设为 `null`。
  @TableField("window_to_ts")
  private Instant windowToTs;

  /// 状态代码(字典: ing_plan_status)
  @TableField("status_code")
  private String statusCode;
}
