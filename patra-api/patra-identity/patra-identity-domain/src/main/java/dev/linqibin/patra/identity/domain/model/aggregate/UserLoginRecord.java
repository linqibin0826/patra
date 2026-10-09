package dev.linqibin.patra.identity.domain.model.aggregate;

import dev.linqibin.patra.common.security.ClientType;
import dev.linqibin.patra.identity.domain.model.enums.LoginEndReason;
import dev.linqibin.patra.identity.domain.model.vo.DeviceId;
import dev.linqibin.patra.identity.domain.model.vo.LoginClient;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.Getter;

/// 一次登录的记录：ID 就是会话 ID。登录时间是审计列 `createdAt`。
///
/// 只用于审计和查看登录设备，不在请求路径上。会话过期不回写这里。
@Getter
public final class UserLoginRecord {

  private Long id;
  private Long userId;
  private ClientType clientType;

  @Getter(AccessLevel.NONE)
  private DeviceId deviceId;

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
  /// @param expiresAt 会话的绝对过期时间
  /// @return 未结束的记录
  public static UserLoginRecord start(long userId, LoginClient client, Instant expiresAt) {
    Objects.requireNonNull(client, "client 不能为 null");
    UserLoginRecord login = new UserLoginRecord();
    login.userId = userId;
    login.clientType = client.clientType();
    login.deviceId = client.deviceId();
    login.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt 不能为 null");
    return login;
  }

  /// 从库里恢复。
  ///
  /// @param id 记录 ID
  /// @param userId 用户 ID
  /// @param clientType 客户端类型
  /// @param deviceId 设备标识，可以为 `null`
  /// @param expiresAt 绝对过期时间
  /// @param endedAt 结束时间，未结束时为 `null`
  /// @param endReason 结束原因，未结束时为 `null`
  /// @param version 乐观锁版本
  /// @param createdAt 创建时间，也是登录时间
  /// @param updatedAt 更新时间
  /// @return 记录
  public static UserLoginRecord restore(
      Long id,
      Long userId,
      ClientType clientType,
      DeviceId deviceId,
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

  /// 不输出设备标识。
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
