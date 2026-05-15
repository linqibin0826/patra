package dev.linqibin.patra.registry.infra.adapter.persistence.entity.provenance;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/// 窗口偏移配置 JPA 实体，映射到表 `reg_prov_window_offset_cfg`。
///
/// 定义如何为给定数据源和操作类型计算增量窗口和偏移。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "reg_prov_window_offset_cfg")
public class ProvWindowOffsetCfgEntity extends ValueObjectJpaEntity {

  /// 外键，引用 `reg_provenance.id`。
  @Column(name = "provenance_id", nullable = false)
  private Long provenanceId;

  /// 操作类型鉴别器(ALL/HARVEST/UPDATE/BACKFILL)。
  @Column(name = "operation_type", nullable = false, length = 20)
  private String operationType;

  /// 表示窗口配置生效时的包含时间戳。
  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  /// 标记配置切片结束的排除时间戳。
  @Column(name = "effective_to")
  private Instant effectiveTo;

  /// 窗口模式(SLIDING 或 CALENDAR)。
  @Column(name = "window_mode_code", length = 20)
  private String windowModeCode;

  /// 窗口长度的数值组件。
  @Column(name = "window_size_value")
  private Integer windowSizeValue;

  /// `windowSizeValue` 的单位(SECOND/MINUTE/HOUR/DAY)。
  @Column(name = "window_size_unit_code", length = 10)
  private String windowSizeUnitCode;

  /// 使用 CALENDAR 窗口时的对齐粒度(例如，DAY/WEEK)。
  @Column(name = "calendar_align_to", length = 10)
  private String calendarAlignTo;

  /// 应用于补偿延迟到达数据的回溯持续时间值。
  @Column(name = "lookback_value")
  private Integer lookbackValue;

  /// 与 `lookbackValue` 配对的单位(SECOND/MINUTE/HOUR/DAY)。
  @Column(name = "lookback_unit_code", length = 10)
  private String lookbackUnitCode;

  /// 连续窗口之间的重叠长度。
  @Column(name = "overlap_value")
  private Integer overlapValue;

  /// 与 `overlapValue` 配对的单位(SECOND/MINUTE/HOUR/DAY)。
  @Column(name = "overlap_unit_code", length = 10)
  private String overlapUnitCode;

  /// 消费流时水印的最大容忍延迟，单位秒。
  @Column(name = "watermark_lag_seconds")
  private Integer watermarkLagSeconds;

  /// 偏移类型(DATE/ID/COMPOSITE)，描述如何跟踪增量。
  @Column(name = "offset_type_code", length = 20)
  private String offsetTypeCode;

  /// 用作偏移枢轴的统一字段键(std_key)。
  @Column(name = "offset_field_key", length = 50)
  private String offsetFieldKey;

  /// 当偏移类型基于日期时使用的日期格式。
  @Column(name = "offset_date_format", length = 50)
  private String offsetDateFormat;

  /// 派生增量窗口时使用的统一日期字段键。
  @Column(name = "window_date_field_key", length = 50)
  private String windowDateFieldKey;

  /// 单个窗口内允许的最大 ID 数量；`null` 使用引擎默认值。
  @Column(name = "max_ids_per_window")
  private Integer maxIdsPerWindow;

  /// 一个窗口允许的最大跨度，单位秒，超过后将被拆分。
  @Column(name = "max_window_span_seconds")
  private Integer maxWindowSpanSeconds;

  /// 生命周期状态代码，指示行是否激活。
  @Column(name = "lifecycle_status_code", length = 20)
  private String lifecycleStatusCode;
}
