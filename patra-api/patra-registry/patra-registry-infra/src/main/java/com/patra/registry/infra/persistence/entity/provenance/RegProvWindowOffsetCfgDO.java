package com.patra.registry.infra.persistence.entity.provenance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.patra.starter.mybatis.entity.BaseDO;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * Persistence entity mapped to {@code reg_prov_window_offset_cfg}.
 *
 * <p>Defines how incremental windows and offsets are calculated for a given provenance and
 * operation type.
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_window_offset_cfg")
public class RegProvWindowOffsetCfgDO extends BaseDO {

  /** Foreign key referencing {@code reg_provenance.id}. */
  @TableField("provenance_id")
  private Long provenanceId;

  /** Operation type discriminator (ALL/HARVEST/UPDATE/BACKFILL). */
  @TableField("operation_type")
  private String operationType;

  /** Inclusive timestamp denoting when the window configuration takes effect. */
  @TableField("effective_from")
  private Instant effectiveFrom;

  /** Exclusive timestamp marking the end of the configuration slice. */
  @TableField("effective_to")
  private Instant effectiveTo;

  /** Windowing mode (SLIDING or CALENDAR). */
  @TableField("window_mode_code")
  private String windowModeCode;

  /** Numeric component of the window length. */
  @TableField("window_size_value")
  private Integer windowSizeValue;

  /** Unit for {@link #windowSizeValue} (SECOND/MINUTE/HOUR/DAY). */
  @TableField("window_size_unit_code")
  private String windowSizeUnitCode;

  /** Alignment granularity when using CALENDAR windows (e.g., DAY/WEEK). */
  @TableField("calendar_align_to")
  private String calendarAlignTo;

  /** Lookback duration value applied to compensate for late-arriving data. */
  @TableField("lookback_value")
  private Integer lookbackValue;

  /** Unit paired with {@link #lookbackValue} (SECOND/MINUTE/HOUR/DAY). */
  @TableField("lookback_unit_code")
  private String lookbackUnitCode;

  /** Overlap length between consecutive windows. */
  @TableField("overlap_value")
  private Integer overlapValue;

  /** Unit paired with {@link #overlapValue} (SECOND/MINUTE/HOUR/DAY). */
  @TableField("overlap_unit_code")
  private String overlapUnitCode;

  /** Maximum tolerated delay (seconds) for watermarks when consuming streams. */
  @TableField("watermark_lag_seconds")
  private Integer watermarkLagSeconds;

  /** Offset type (DATE/ID/COMPOSITE) describing how increments are tracked. */
  @TableField("offset_type_code")
  private String offsetTypeCode;

  /** Name or JSON path of the field used as the offset pivot. */
  @TableField("offset_field_name")
  private String offsetFieldName;

  /** Date format used when the offset type is date-based. */
  @TableField("offset_date_format")
  private String offsetDateFormat;

  /** Default date field referenced when deriving incremental windows. */
  @TableField("default_date_field_name")
  private String defaultDateFieldName;

  /** Maximum number of IDs allowed within a single window; {@code null} uses engine defaults. */
  @TableField("max_ids_per_window")
  private Integer maxIdsPerWindow;

  /** Maximum span in seconds permitted for one window before it is split. */
  @TableField("max_window_span_seconds")
  private Integer maxWindowSpanSeconds;

  /** Lifecycle status code indicating whether the row is active. */
  @TableField("lifecycle_status_code")
  private String lifecycleStatusCode;
}
