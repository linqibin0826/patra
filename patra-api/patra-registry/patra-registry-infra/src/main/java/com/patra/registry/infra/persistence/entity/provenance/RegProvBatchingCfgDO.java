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

/// 数据库实体,映射到表 `reg_prov_batching_cfg`。
///
/// 表示每个数据源的批处理设置,控制如何将 ID 分组到特定操作类型的下游请求中。
///
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_batching_cfg")
public class RegProvBatchingCfgDO extends BaseDO {

  /// 外键,引用 `reg_provenance.id`。
  @TableField("provenance_id")
  private Long provenanceId;

  /// 操作类型鉴别器(例如,ALL/HARVEST/UPDATE/BACKFILL)。
  @TableField("operation_type")
  private String operationType;

  /// 此配置切片的包含生效开始时间戳。
  @TableField("effective_from")
  private Instant effectiveFrom;

  /// 排除生效结束时间戳;`null` 表示开放式切片。
  @TableField("effective_to")
  private Instant effectiveTo;

  /// 每个详情批处理请求建议获取的最大 ID 数量。
  @TableField("detail_fetch_batch_size")
  private Integer detailFetchBatchSize;

  /// 携带批处理 ID 列表的 HTTP 参数名称。
  @TableField("ids_param_name")
  private String idsParamName;

  /// 将 ID 连接为单个参数值时使用的分隔符。
  @TableField("ids_join_delimiter")
  private String idsJoinDelimiter;

  /// 每个下游请求允许的 ID 硬上限。
  @TableField("max_ids_per_request")
  private Integer maxIdsPerRequest;

  /// 生命周期状态代码,有效行通常为 `ACTIVE`。
  @TableField("lifecycle_status_code")
  private String lifecycleStatusCode;
}
