package com.patra.registry.infra.adapter.persistence.entity.provenance;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/// 批处理配置 JPA 实体，映射到表 `reg_prov_batching_cfg`。
///
/// 表示每个数据源的批处理设置，控制如何将 ID 分组到特定操作类型的下游请求中。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "reg_prov_batching_cfg")
public class ProvBatchingCfgEntity extends BaseJpaEntity {

  /// 外键，引用 `reg_provenance.id`。
  @Column(name = "provenance_id", nullable = false)
  private Long provenanceId;

  /// 操作类型鉴别器(例如，ALL/HARVEST/UPDATE/BACKFILL)。
  @Column(name = "operation_type", nullable = false, length = 20)
  private String operationType;

  /// 此配置切片的包含生效开始时间戳。
  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  /// 排除生效结束时间戳；`null` 表示开放式切片。
  @Column(name = "effective_to")
  private Instant effectiveTo;

  /// 每个详情批处理请求建议获取的最大 ID 数量。
  @Column(name = "detail_fetch_batch_size")
  private Integer detailFetchBatchSize;

  /// 携带批处理 ID 列表的 HTTP 参数名称。
  @Column(name = "ids_param_name", length = 50)
  private String idsParamName;

  /// 将 ID 连接为单个参数值时使用的分隔符。
  @Column(name = "ids_join_delimiter", length = 10)
  private String idsJoinDelimiter;

  /// 每个下游请求允许的 ID 硬上限。
  @Column(name = "max_ids_per_request")
  private Integer maxIdsPerRequest;

  /// 生命周期状态代码，有效行通常为 `ACTIVE`。
  @Column(name = "lifecycle_status_code", length = 20)
  private String lifecycleStatusCode;
}
