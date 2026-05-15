package dev.linqibin.patra.registry.infra.adapter.persistence.entity.provenance;

import com.patra.starter.jpa.entity.ValueObjectJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/// HTTP 配置 JPA 实体，映射到表 `reg_prov_http_cfg`。
///
/// 保存数据源/操作组合的 HTTP 策略覆盖，如默认头和超时定义。
///
/// @author linqibin
/// @since 0.1.0
@Getter
@Setter
@Entity
@Table(name = "reg_prov_http_cfg")
public class ProvHttpCfgEntity extends ValueObjectJpaEntity {

  /// 外键，引用 `reg_provenance.id`。
  @Column(name = "provenance_id", nullable = false)
  private Long provenanceId;

  /// 操作类型鉴别器(ALL/HARVEST/UPDATE/BACKFILL)。
  @Column(name = "operation_type", nullable = false, length = 20)
  private String operationType;

  /// 此 HTTP 策略切片的包含生效开始时间戳。
  @Column(name = "effective_from", nullable = false)
  private Instant effectiveFrom;

  /// 排除生效结束时间戳；`null` 表示开放式切片。
  @Column(name = "effective_to")
  private Instant effectiveTo;

  /// 定义要合并到出站请求中的默认头的 JSON 载荷。
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "default_headers_json", columnDefinition = "JSON")
  private JsonNode defaultHeadersJson;

  /// 连接超时(TCP/TLS 握手)，单位毫秒。
  @Column(name = "timeout_connect_millis")
  private Integer timeoutConnectMillis;

  /// 下载响应体时的读取超时，单位毫秒。
  @Column(name = "timeout_read_millis")
  private Integer timeoutReadMillis;

  /// 覆盖整个调用窗口的总请求超时，单位毫秒。
  @Column(name = "timeout_total_millis")
  private Integer timeoutTotalMillis;

  /// 指示是否强制执行 TLS 证书验证的标志。
  @Column(name = "tls_verify_enabled")
  private Boolean tlsVerifyEnabled;

  /// 可选的代理端点(例如，`http://user:pass@host:port`)。
  @Column(name = "proxy_url_value", length = 500)
  private String proxyUrlValue;

  /// 描述如何解释服务器 `Retry-After` 头的策略代码。
  @Column(name = "retry_after_policy_code", length = 20)
  private String retryAfterPolicyCode;

  /// 当策略遵守 `Retry-After` 时的最大等待时长，单位毫秒。
  @Column(name = "retry_after_cap_millis")
  private Integer retryAfterCapMillis;

  /// 为写操作注入的幂等性头名称。
  @Column(name = "idempotency_header_name", length = 50)
  private String idempotencyHeaderName;

  /// 提供方侧幂等性键的生存时间，单位秒。
  @Column(name = "idempotency_ttl_seconds")
  private Integer idempotencyTtlSeconds;

  /// 生命周期状态代码，标记记录当前是否激活。
  @Column(name = "lifecycle_status_code", length = 20)
  private String lifecycleStatusCode;
}
