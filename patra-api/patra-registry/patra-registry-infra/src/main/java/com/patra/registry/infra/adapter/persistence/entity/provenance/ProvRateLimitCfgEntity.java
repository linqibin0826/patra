package com.patra.registry.infra.adapter.persistence.entity.provenance;

import com.patra.starter.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

/// 速率限制配置 JPA 实体，映射到表 `reg_prov_rate_limit_cfg`。
///
/// 捕获数据源/操作组合的并发和凭据级节流限制。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "reg_prov_rate_limit_cfg")
public class ProvRateLimitCfgEntity extends BaseJpaEntity {

  /// 外键，引用 `reg_provenance.id`。
  @Column(name = "provenance_id", nullable = false)
  private Long provenanceId;

  /// 控制规则应用于哪些执行的操作类型鉴别器。
  @Column(name = "operation_type", nullable = false, length = 20)
  private String operationType;

  /// 标记速率限制生效时的包含开始时间戳。
  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  /// 速率限制不再应用后的排除结束时间戳。
  @Column(name = "effective_to")
  private Instant effectiveTo;

  /// 此数据源/操作对允许的最大并发 HTTP 请求数。
  @Column(name = "max_concurrent_requests")
  private Integer maxConcurrentRequests;

  /// 每个凭据允许的每秒最大请求数；`null` 保持引擎默认值。
  @Column(name = "per_credential_qps_limit")
  private Integer perCredentialQpsLimit;

  /// 生命周期状态代码，标记配置是否激活。
  @Column(name = "lifecycle_status_code", length = 20)
  private String lifecycleStatusCode;
}
