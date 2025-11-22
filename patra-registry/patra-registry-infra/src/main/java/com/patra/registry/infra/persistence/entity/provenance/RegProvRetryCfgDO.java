package com.patra.registry.infra.persistence.entity.provenance;

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

/// 数据库实体,映射到表 `reg_prov_retry_cfg`。
///
/// 包含与指定操作类型的数据源交互时应用的重试和退避规则。
///
/// @author linqibin
/// @since 0.1.0
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_retry_cfg")
public class RegProvRetryCfgDO extends BaseDO {

  /// 外键,引用 `reg_provenance.id`。
  @TableField("provenance_id")
  private Long provenanceId;

  /// 重试配置的操作类型鉴别器。
  @TableField("operation_type")
  private String operationType;

  /// 重试策略生效时的包含时间戳。
  @TableField("effective_from")
  private Instant effectiveFrom;

  /// 重试策略过期时的排除时间戳(可为空)。
  @TableField("effective_to")
  private Instant effectiveTo;

  /// 允许的最大重试尝试次数。
  @TableField("max_retry_times")
  private Integer maxRetryTimes;

  /// 退避策略类型(例如,FIXED/EXPONENTIAL/EXP_JITTER)。
  @TableField("backoff_policy_type_code")
  private String backoffPolicyTypeCode;

  /// 第一次重试尝试前的初始延迟,单位毫秒。
  @TableField("initial_delay_millis")
  private Integer initialDelayMillis;

  /// 任何重试延迟的上限,单位毫秒。
  @TableField("max_delay_millis")
  private Integer maxDelayMillis;

  /// 使用指数退避时连续重试之间应用的乘数。
  @TableField("exp_multiplier_value")
  private Double expMultiplierValue;

  /// 指定应用于退避延迟的抖动幅度的分数。
  @TableField("jitter_factor_ratio")
  private Double jitterFactorRatio;

  /// 描述应触发重试的 HTTP 状态码的 JSON 数组。
  @TableField("retry_http_status_json")
  private JsonNode retryHttpStatusJson;

  /// 描述应跳过重试的 HTTP 状态码的 JSON 数组。
  @TableField("giveup_http_status_json")
  private JsonNode giveupHttpStatusJson;

  /// 指示网络级错误是否符合重试条件的标志。
  @TableField("retry_on_network_error")
  private Boolean retryOnNetworkError;

  /// 触发熔断器所需的连续失败次数。
  @TableField("circuit_break_threshold")
  private Integer circuitBreakThreshold;

  /// 熔断器触发后的冷却间隔,单位毫秒。
  @TableField("circuit_cooldown_millis")
  private Integer circuitCooldownMillis;

  /// 生命周期状态代码,反映策略是否激活。
  @TableField("lifecycle_status_code")
  private String lifecycleStatusCode;
}
