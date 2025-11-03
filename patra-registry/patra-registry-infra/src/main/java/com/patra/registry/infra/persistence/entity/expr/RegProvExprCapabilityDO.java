package com.patra.registry.infra.persistence.entity.expr;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.mybatis.entity.BaseDO;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 数据库实体,映射到表 {@code reg_prov_expr_capability}.
 *
 * <p>Describes expression capabilities and constraints for a provenance field.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_expr_capability")
public class RegProvExprCapabilityDO extends BaseDO {

  /** 外键,引用 {@code reg_provenance.id}。 */
  @TableField("provenance_id")
  private Long provenanceId;

  /** 操作类型鉴别器 linked to this capability. */
  @TableField("operation_type")
  private String operationType;

  /** 生命周期状态代码 for the capability record. */
  @TableField("lifecycle_status_code")
  private String lifecycleStatusCode;

  /** 规范字段键 to which this capability applies. */
  @TableField("field_key")
  private String fieldKey;

  /** 包含时间戳 when the capability becomes effective. */
  @TableField("effective_from")
  private Instant effectiveFrom;

  /** 排除时间戳 indicating when the capability expires. */
  @TableField("effective_to")
  private Instant effectiveTo;

  /** JSON array enumerating supported expression operators. */
  @TableField("ops")
  private JsonNode ops;

  /** JSON array listing operators that support negation. */
  @TableField("negatable_ops")
  private JsonNode negatableOps;

  /** 指示 whether the NOT operator is allowed globally. */
  @TableField("supports_not")
  private Boolean supportsNot;

  /** JSON array describing match strategies applicable to TERM operators. */
  @TableField("term_matches")
  private JsonNode termMatches;

  /** Flag denoting whether TERM matches can be case sensitive. */
  @TableField("term_case_sensitive_allowed")
  private Boolean termCaseSensitiveAllowed;

  /** 指示 whether blank TERM values are permitted. */
  @TableField("term_allow_blank")
  private Boolean termAllowBlank;

  /** Minimum length allowed for TERM values. */
  @TableField("term_min_len")
  private Integer termMinLen;

  /** Maximum length allowed for TERM values. */
  @TableField("term_max_len")
  private Integer termMaxLen;

  /** 可选的 regular expression constraining TERM values. */
  @TableField("term_pattern")
  private String termPattern;

  /** Maximum number of elements allowed in an IN operator. */
  @TableField("in_max_size")
  private Integer inMaxSize;

  /** 指示 whether IN comparisons can be case sensitive. */
  @TableField("in_case_sensitive_allowed")
  private Boolean inCaseSensitiveAllowed;

  /** Range kind describing the comparable type (NONE/DATE/DATETIME/NUMBER). */
  @TableField("range_kind_code")
  private String rangeKindCode;

  /** 指示 whether open-start ranges are allowed. */
  @TableField("range_allow_open_start")
  private Boolean rangeAllowOpenStart;

  /** 指示 whether open-end ranges are allowed. */
  @TableField("range_allow_open_end")
  private Boolean rangeAllowOpenEnd;

  /** 指示 whether ranges may be closed at positive or negative infinity. */
  @TableField("range_allow_closed_at_infty")
  private Boolean rangeAllowClosedAtInfty;

  /** Minimum supported calendar date for DATE ranges. */
  @TableField("date_min")
  private LocalDate dateMin;

  /** Maximum supported calendar date for DATE ranges. */
  @TableField("date_max")
  private LocalDate dateMax;

  /** Minimum supported instant for DATETIME ranges. */
  @TableField("datetime_min")
  private Instant datetimeMin;

  /** Maximum supported instant for DATETIME ranges. */
  @TableField("datetime_max")
  private Instant datetimeMax;

  /** Minimum numeric value allowed for NUMBER ranges. */
  @TableField("number_min")
  private BigDecimal numberMin;

  /** Maximum numeric value allowed for NUMBER ranges. */
  @TableField("number_max")
  private BigDecimal numberMax;

  /** 指示 whether the EXISTS operator is supported. */
  @TableField("exists_supported")
  private Boolean existsSupported;

  /** JSON array describing supported token kinds for TOKEN operators. */
  @TableField("token_kinds")
  private JsonNode tokenKinds;

  /** 可选的 regex constraining token values. */
  @TableField("token_value_pattern")
  private String tokenValuePattern;
}
