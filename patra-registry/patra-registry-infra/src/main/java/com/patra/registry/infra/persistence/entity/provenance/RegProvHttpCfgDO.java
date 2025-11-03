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

/**
 * 数据库实体,映射到表 {@code reg_prov_http_cfg}。
 *
 * <p>保存数据源/操作组合的 HTTP 策略覆盖,如默认头和超时定义。
 *
 * @author linqibin
 * @since 0.1.0
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("reg_prov_http_cfg")
public class RegProvHttpCfgDO extends BaseDO {

  /** 外键,引用 {@code reg_provenance.id}。 */
  @TableField("provenance_id")
  private Long provenanceId;

  /** 操作类型鉴别器(ALL/HARVEST/UPDATE/BACKFILL)。 */
  @TableField("operation_type")
  private String operationType;

  /** 此 HTTP 策略切片的包含生效开始时间戳。 */
  @TableField("effective_from")
  private Instant effectiveFrom;

  /** 排除生效结束时间戳;{@code null} 表示开放式切片。 */
  @TableField("effective_to")
  private Instant effectiveTo;

  /** 定义要合并到出站请求中的默认头的 JSON 载荷。 */
  @TableField("default_headers_json")
  private JsonNode defaultHeadersJson;

  /** 连接超时(TCP/TLS 握手),单位毫秒。 */
  @TableField("timeout_connect_millis")
  private Integer timeoutConnectMillis;

  /** 下载响应体时的读取超时,单位毫秒。 */
  @TableField("timeout_read_millis")
  private Integer timeoutReadMillis;

  /** 覆盖整个调用窗口的总请求超时,单位毫秒。 */
  @TableField("timeout_total_millis")
  private Integer timeoutTotalMillis;

  /** 指示是否强制执行 TLS 证书验证的标志。 */
  @TableField("tls_verify_enabled")
  private Boolean tlsVerifyEnabled;

  /** 可选的代理端点(例如,{@code http://user:pass@host:port})。 */
  @TableField("proxy_url_value")
  private String proxyUrlValue;

  /** 描述如何解释服务器 {@code Retry-After} 头的策略代码。 */
  @TableField("retry_after_policy_code")
  private String retryAfterPolicyCode;

  /** 当策略遵守 {@code Retry-After} 时的最大等待时长,单位毫秒。 */
  @TableField("retry_after_cap_millis")
  private Integer retryAfterCapMillis;

  /** 为写操作注入的幂等性头名称。 */
  @TableField("idempotency_header_name")
  private String idempotencyHeaderName;

  /** 提供方侧幂等性键的生存时间,单位秒。 */
  @TableField("idempotency_ttl_seconds")
  private Integer idempotencyTtlSeconds;

  /** 生命周期状态代码,标记记录当前是否激活。 */
  @TableField("lifecycle_status_code")
  private String lifecycleStatusCode;
}
