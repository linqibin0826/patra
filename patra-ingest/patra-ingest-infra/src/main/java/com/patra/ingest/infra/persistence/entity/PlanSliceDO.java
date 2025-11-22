package com.patra.ingest.infra.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashCode;

/// 计划切片数据库实体,映射到表 `ing_plan_slice`。
/// 
/// 表结构: 表示通过策略从计划派生的最小幂等执行单元。每个切片对应一个任务。
/// 
/// 关键字段说明:
/// 
/// - `slice_signature_hash` 是 `window_spec` 的规范化哈希(唯一约束: uk_slice_signature),防止重复
///   - `window_spec` 和 `expr_snapshot` 通过 {@link JacksonTypeHandler} 以 JSON AST 形式存储
/// 
/// @author linqibin
/// @since 0.1.0
@Data
@EqualsAndHashCode(callSuper = true)
@TableName(value = "ing_plan_slice", autoResultMap = true)
public class PlanSliceDO extends BaseDO {

  /// Associated plan id.
  @TableField("plan_id")
  private Long planId;

  /// Redundant provenance code.
  @TableField("provenance_code")
  private String provenanceCode;

  /// Slice sequence number (0..N).
  @TableField("slice_no")
  private Integer sliceNo;

  /// Slice signature hash (based on normalized window_spec).
  @TableField("slice_signature_hash")
  private String sliceSignatureHash;

  /// Window boundary spec (JSON).
  @TableField(value = "window_spec", typeHandler = JacksonTypeHandler.class)
  private JsonNode windowSpec;

  /// Localized expression hash.
  @TableField("expr_hash")
  private String exprHash;

  /// Localized expression snapshot (JSON AST; replayable).
  @TableField(value = "expr_snapshot", typeHandler = JacksonTypeHandler.class)
  private JsonNode exprSnapshot;

  /// Slice status (DICT: ing_slice_status).
  @TableField("status_code")
  private String statusCode;
}
