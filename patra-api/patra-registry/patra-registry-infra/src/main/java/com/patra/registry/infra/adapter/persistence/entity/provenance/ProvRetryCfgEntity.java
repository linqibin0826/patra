package com.patra.registry.infra.adapter.persistence.entity.provenance;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/// 重试配置 JPA 实体，映射到表 `reg_prov_retry_cfg`。
///
/// 包含与指定操作类型的数据源交互时应用的重试和退避规则。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "reg_prov_retry_cfg")
public class ProvRetryCfgEntity extends ValueObjectJpaEntity {

  /// 外键，引用 `reg_provenance.id`。
  @Column(name = "provenance_id", nullable = false)
  private Long provenanceId;

  /// 重试配置的操作类型鉴别器。
  @Column(name = "operation_type", nullable = false, length = 20)
  private String operationType;

  /// 重试策略生效时的包含时间戳。
  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  /// 重试策略过期时的排除时间戳(可为空)。
  @Column(name = "effective_to")
  private Instant effectiveTo;

  /// 允许的最大重试尝试次数。
  @Column(name = "max_retry_times")
  private Integer maxRetryTimes;

  /// 退避策略类型(例如，FIXED/EXPONENTIAL/EXP_JITTER)。
  @Column(name = "backoff_policy_type_code", length = 20)
  private String backoffPolicyTypeCode;

  /// 第一次重试尝试前的初始延迟，单位毫秒。
  @Column(name = "initial_delay_millis")
  private Integer initialDelayMillis;

  /// 任何重试延迟的上限，单位毫秒。
  @Column(name = "max_delay_millis")
  private Integer maxDelayMillis;

  /// 使用指数退避时连续重试之间应用的乘数。
  @Column(name = "exp_multiplier_value")
  private Double expMultiplierValue;

  /// 指定应用于退避延迟的抖动幅度的分数。
  @Column(name = "jitter_factor_ratio")
  private Double jitterFactorRatio;

  /// 描述应触发重试的 HTTP 状态码的 JSON 数组。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "retry_http_status_json", columnDefinition = "JSON")
  private JsonNode retryHttpStatusJson;

  /// 描述应跳过重试的 HTTP 状态码的 JSON 数组。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "giveup_http_status_json", columnDefinition = "JSON")
  private JsonNode giveupHttpStatusJson;

  /// 指示网络级错误是否符合重试条件的标志。
  @Column(name = "retry_on_network_error")
  private Boolean retryOnNetworkError;

  /// 触发熔断器所需的连续失败次数。
  @Column(name = "circuit_break_threshold")
  private Integer circuitBreakThreshold;

  /// 熔断器触发后的冷却间隔，单位毫秒。
  @Column(name = "circuit_cooldown_millis")
  private Integer circuitCooldownMillis;

  /// 生命周期状态代码，反映策略是否激活。
  @Column(name = "lifecycle_status_code", length = 20)
  private String lifecycleStatusCode;
}
