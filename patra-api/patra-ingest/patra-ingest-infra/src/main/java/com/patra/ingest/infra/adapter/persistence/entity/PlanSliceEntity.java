package com.patra.ingest.infra.adapter.persistence.entity;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// 计划切片 JPA 实体，映射到表 `ing_plan_slice`。
///
/// 表结构: 表示通过策略从计划派生的最小幂等执行单元。每个切片对应一个任务。
///
/// 关键字段说明:
///
/// - `slice_signature_hash` 是 `window_spec` 的规范化哈希（唯一约束: uk_slice_signature），防止重复
/// - `window_spec` 和 `expr_snapshot` 以 JSON AST 形式存储
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(
    name = "ing_plan_slice",
    indexes = {
      @Index(name = "uk_slice_signature", columnList = "slice_signature_hash", unique = true),
      @Index(name = "idx_slice_plan", columnList = "plan_id"),
      @Index(name = "idx_slice_status", columnList = "status_code")
    })
public class PlanSliceEntity extends ValueObjectJpaEntity {

  /// 关联的计划 ID
  @Column(name = "plan_id", nullable = false)
  private Long planId;

  /// 冗余数据源代码
  @Column(name = "provenance_code", length = 64)
  private String provenanceCode;

  /// 切片序号（0..N）
  @Column(name = "slice_no", nullable = false)
  private Integer sliceNo;

  /// 切片签名哈希（基于规范化 window_spec）
  @Column(name = "slice_signature_hash", nullable = false, length = 64)
  private String sliceSignatureHash;

  /// 窗口边界规格（JSON）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "window_spec", columnDefinition = "JSON")
  private JsonNode windowSpec;

  /// 本地化表达式哈希
  @Column(name = "expr_hash", length = 64)
  private String exprHash;

  /// 本地化表达式快照（JSON AST；可重放）
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "expr_snapshot", columnDefinition = "JSON")
  private JsonNode exprSnapshot;

  /// 切片状态（字典: ing_slice_status）
  @Column(name = "status_code", nullable = false, length = 32)
  private String statusCode;
}
