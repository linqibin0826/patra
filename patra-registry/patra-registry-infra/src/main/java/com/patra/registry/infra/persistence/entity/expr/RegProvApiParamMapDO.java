package com.patra.registry.infra.persistence.entity.expr;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 数据库实体,映射到表 {@code reg_prov_api_param_map}.
 *
 * <p>Tracks provider-specific parameter names for standardized query keys.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_api_param_map")
public class RegProvApiParamMapDO extends BaseDO {

  /** 外键,引用 {@code reg_provenance.id}。 */
  @TableField("provenance_id")
  private Long provenanceId;

  /** 操作类型鉴别器 associated with the mapping. */
  @TableField("operation_type")
  private String operationType;

  /** 生命周期状态代码 for the mapping record. */
  @TableField("lifecycle_status_code")
  private String lifecycleStatusCode;

  /** Endpoint name this mapping applies to; {@code null} means all endpoints. */
  @TableField("endpoint_name")
  private String endpointName;

  /** 标准化键 resolved by expression rendering (e.g., {@code from}, {@code term}). */
  @TableField("std_key")
  private String stdKey;

  /** 特定于提供方的 parameter name aligned with the standard key. */
  @TableField("provider_param_name")
  private String providerParamName;

  /** 可选的 transform applied to the value before sending to the provider. */
  @TableField("transform_code")
  private String transformCode;

  /** 自由格式 JSON notes describing special handling. */
  @TableField("notes")
  private JsonNode notes;

  /** 包含时间戳 indicating when the mapping becomes effective. */
  @TableField("effective_from")
  private Instant effectiveFrom;

  /** 排除时间戳 indicating when the mapping expires. */
  @TableField("effective_to")
  private Instant effectiveTo;
}
