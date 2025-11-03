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
 * 数据库实体,映射到表 {@code reg_prov_rate_limit_cfg}。
 *
 * <p>捕获数据源/操作组合的并发和凭据级节流限制。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_rate_limit_cfg")
public class RegProvRateLimitCfgDO extends BaseDO {

  /** 外键,引用 {@code reg_provenance.id}。 */
  @TableField("provenance_id")
  private Long provenanceId;

  /** 控制规则应用于哪些执行的操作类型鉴别器。 */
  @TableField("operation_type")
  private String operationType;

  /** 标记速率限制生效时的包含开始时间戳。 */
  @TableField("effective_from")
  private Instant effectiveFrom;

  /** 速率限制不再应用后的排除结束时间戳。 */
  @TableField("effective_to")
  private Instant effectiveTo;

  /** 此数据源/操作对允许的最大并发 HTTP 请求数。 */
  @TableField("max_concurrent_requests")
  private Integer maxConcurrentRequests;

  /** 每个凭据允许的每秒最大请求数;{@code null} 保持引擎默认值。 */
  @TableField("per_credential_qps_limit")
  private Integer perCredentialQpsLimit;

  /** 生命周期状态代码,标记配置是否激活。 */
  @TableField("lifecycle_status_code")
  private String lifecycleStatusCode;
}
