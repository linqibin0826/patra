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
 * 数据库实体,映射到表 {@code reg_prov_window_offset_cfg}。
 *
 * <p>定义如何为给定数据源和操作类型计算增量窗口和偏移。
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

  /** 外键,引用 {@code reg_provenance.id}。 */
  @TableField("provenance_id")
  private Long provenanceId;

  /** 操作类型鉴别器(ALL/HARVEST/UPDATE/BACKFILL)。 */
  @TableField("operation_type")
  private String operationType;

  /** 表示窗口配置生效时的包含时间戳。 */
  @TableField("effective_from")
  private Instant effectiveFrom;

  /** 标记配置切片结束的排除时间戳。 */
  @TableField("effective_to")
  private Instant effectiveTo;

  /** 窗口模式(SLIDING 或 CALENDAR)。 */
  @TableField("window_mode_code")
  private String windowModeCode;

  /** 窗口长度的数值组件。 */
  @TableField("window_size_value")
  private Integer windowSizeValue;

  /** {@link #windowSizeValue} 的单位(SECOND/MINUTE/HOUR/DAY)。 */
  @TableField("window_size_unit_code")
  private String windowSizeUnitCode;

  /** 使用 CALENDAR 窗口时的对齐粒度(例如,DAY/WEEK)。 */
  @TableField("calendar_align_to")
  private String calendarAlignTo;

  /** 应用于补偿延迟到达数据的回溯持续时间值。 */
  @TableField("lookback_value")
  private Integer lookbackValue;

  /** 与 {@link #lookbackValue} 配对的单位(SECOND/MINUTE/HOUR/DAY)。 */
  @TableField("lookback_unit_code")
  private String lookbackUnitCode;

  /** 连续窗口之间的重叠长度。 */
  @TableField("overlap_value")
  private Integer overlapValue;

  /** 与 {@link #overlapValue} 配对的单位(SECOND/MINUTE/HOUR/DAY)。 */
  @TableField("overlap_unit_code")
  private String overlapUnitCode;

  /** 消费流时水印的最大容忍延迟,单位秒。 */
  @TableField("watermark_lag_seconds")
  private Integer watermarkLagSeconds;

  /** 偏移类型(DATE/ID/COMPOSITE),描述如何跟踪增量。 */
  @TableField("offset_type_code")
  private String offsetTypeCode;

  /** 用作偏移枢轴的统一字段键(std_key)。 */
  @TableField("offset_field_key")
  private String offsetFieldKey;

  /** 当偏移类型基于日期时使用的日期格式。 */
  @TableField("offset_date_format")
  private String offsetDateFormat;

  /** 派生增量窗口时使用的统一日期字段键。 */
  @TableField("window_date_field_key")
  private String windowDateFieldKey;

  /** 单个窗口内允许的最大 ID 数量;{@code null} 使用引擎默认值。 */
  @TableField("max_ids_per_window")
  private Integer maxIdsPerWindow;

  /** 一个窗口允许的最大跨度,单位秒,超过后将被拆分。 */
  @TableField("max_window_span_seconds")
  private Integer maxWindowSpanSeconds;

  /** 生命周期状态代码,指示行是否激活。 */
  @TableField("lifecycle_status_code")
  private String lifecycleStatusCode;
}
