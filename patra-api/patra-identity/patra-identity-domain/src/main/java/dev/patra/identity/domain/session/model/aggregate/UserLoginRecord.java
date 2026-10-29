package dev.patra.identity.domain.session.model.aggregate;

import dev.patra.identity.domain.session.model.enums.DeviceClass;
import dev.patra.identity.domain.session.model.enums.LoginEndReason;
import dev.patra.identity.domain.session.model.vo.ClientOrigin;
import dev.patra.identity.domain.session.model.vo.DeviceId;
import dev.patra.identity.domain.session.model.vo.LoginClient;
import dev.patra.tool.core.security.ClientType;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;

/// 一次登录的记录：ID 就是会话 ID。登录时间是审计列 `createdAt`。
///
/// 设备、UA、IP 与属地是登录时的值（PAP-99），登录设备列表与新设备判定读它们。
/// 只用于审计和查看登录设备，不在请求路径上。会话过期不回写这里。
@Getter
public final class UserLoginRecord {

  private Long id;
  private Long userId;
  private ClientType clientType;

  @Getter(AccessLevel.NONE)
  private DeviceId deviceId;

  private String userAgent;
  private String deviceName;
  private DeviceClass deviceClass;
  private String clientIp;
  private String ipRegion;
  private Instant expiresAt;
  private Instant endedAt;
  private LoginEndReason endReason;
  private Long version;
  private Instant createdAt;
  private Instant updatedAt;

  /// 只能经工厂方法创建。
  private UserLoginRecord() {}

  /// 开始一条记录。ID 由仓储在保存时分配。
  ///
  /// @param userId 用户 ID
  /// @param client 客户端信息
  /// @param origin 请求来源（UA、设备名、端、IP、属地）
  /// @param expiresAt 会话的绝对过期时间
  /// @return 未结束的记录
  public static UserLoginRecord start(
      long userId, LoginClient client, ClientOrigin origin, Instant expiresAt) {
    Objects.requireNonNull(client, "client 不能为 null");
    Objects.requireNonNull(origin, "origin 不能为 null");
    UserLoginRecord login = new UserLoginRecord();
    login.userId = userId;
    login.clientType = client.clientType();
    login.deviceId = client.deviceId();
    login.userAgent = origin.userAgent();
    login.deviceName = origin.deviceName();
    login.deviceClass = origin.deviceClass();
    login.clientIp = origin.ip();
    login.ipRegion = origin.ipRegion();
    login.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
    return login;
  }

  /// 从库里恢复。用 `UserLoginRecord.restore()…build()` 调用。
  ///
  /// @param id 记录 ID
  /// @param userId 用户 ID
  /// @param clientType 客户端类型
  /// @param deviceId 设备标识，可以为 `null`
  /// @param userAgent User-Agent，可以为 `null`
  /// @param deviceName 设备名，可以为 `null`
  /// @param deviceClass 端
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param ipRegion IP 属地，可以为 `null`
  /// @param expiresAt 绝对过期时间
  /// @param endedAt 结束时间，未结束时为 `null`
  /// @param endReason 结束原因，未结束时为 `null`
  /// @param version 乐观锁版本
  /// @param createdAt 创建时间，也是登录时间
  /// @param updatedAt 更新时间
  /// @return 记录
  @Builder(builderMethodName = "restore")
  private static UserLoginRecord restored(
      Long id,
      Long userId,
      ClientType clientType,
      DeviceId deviceId,
      String userAgent,
      String deviceName,
      DeviceClass deviceClass,
      String clientIp,
      String ipRegion,
      Instant expiresAt,
      Instant endedAt,
      LoginEndReason endReason,
      Long version,
      Instant createdAt,
      Instant updatedAt) {
    if ((endedAt == null) != (endReason == null)) {
      throw new IllegalArgumentException("结束时间和结束原因要么都有，要么都没有");
    }
    UserLoginRecord login = new UserLoginRecord();
    login.id = Objects.requireNonNull(id, "id 不能为 null");
    login.userId = Objects.requireNonNull(userId, "userId 不能为 null");
    login.clientType = Objects.requireNonNull(clientType, "clientType 不能为 null");
    login.deviceId = deviceId;
    login.userAgent = userAgent;
    login.deviceName = deviceName;
    login.deviceClass = Objects.requireNonNull(deviceClass, "deviceClass 不能为 null");
    login.clientIp = clientIp;
    login.ipRegion = ipRegion;
    login.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
    login.endedAt = endedAt;
    login.endReason = endReason;
    login.version = version;
    login.createdAt = createdAt;
    login.updatedAt = updatedAt;
    return login;
  }

  /// 设备标识。
  ///
  /// @return 设备标识；没有时为空
  public Optional<DeviceId> getDeviceId() {
    return Optional.ofNullable(deviceId);
  }

  /// 结束这条记录。已经结束时什么都不做，保留第一次的时间和原因。
  ///
  /// @param reason 结束原因
  /// @param now 当前时间
  public void end(LoginEndReason reason, Instant now) {
    Objects.requireNonNull(reason, "reason 不能为 null");
    Objects.requireNonNull(now, "now 不能为 null");
    if (endedAt != null) {
      return;
    }
    endedAt = now;
    endReason = reason;
  }

  /// 是否已结束。
  ///
  /// @return 已结束时为 `true`
  public boolean isEnded() {
    return endedAt != null;
  }

  /// 不输出设备标识、UA 与 IP。
  ///
  /// @return 描述
  @Override
  public String toString() {
    return "UserLoginRecord[id="
        + id
        + ", userId="
        + userId
        + ", clientType="
        + clientType
        + ", endReason="
        + endReason
        + "]";
  }
}
