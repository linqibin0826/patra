package dev.patra.identity.infra.session.persistence.entity;

import dev.patra.tool.jpa.entity.BaseJpaEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/// `idn_user_login_record` 表的 JPA 实体。`toString()` 不输出设备标识。
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Entity
@Table(name = "idn_user_login_record")
public class UserLoginRecordEntity extends BaseJpaEntity {

  /// 所属用户 ID
  @Column(name = "user_id", nullable = false)
  private Long userId;

  /// 客户端类型：WEB
  @Column(name = "client_type", nullable = false, length = 16)
  private String clientType;

  /// 设备标识，可空
  @ToString.Exclude
  @Column(name = "device_id", length = 43)
  private String deviceId;

  @ToString.Exclude
  @Column(name = "user_agent", length = 512)
  private String userAgent;

  @Column(name = "device_name", length = 64)
  private String deviceName;

  @Column(name = "device_class", nullable = false, length = 16)
  private String deviceClass;

  @ToString.Exclude
  @Column(name = "client_ip", length = 45)
  private String clientIp;

  @Column(name = "ip_region", length = 64)
  private String ipRegion;

  /// 会话的绝对过期时间
  @Column(name = "expires_at", nullable = false)
  private Instant expiresAt;

  /// 结束时间，未结束时为 null
  @Column(name = "ended_at")
  private Instant endedAt;

  /// 结束原因：LOGOUT / BANNED / REPLACED / REVOKED，未结束时为 null
  @Column(name = "end_reason", length = 32)
  private String endReason;
}
