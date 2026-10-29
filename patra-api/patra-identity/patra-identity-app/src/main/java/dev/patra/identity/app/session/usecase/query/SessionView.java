package dev.patra.identity.app.session.usecase.query;

import dev.patra.identity.domain.session.model.enums.DeviceClass;
import java.time.Instant;
import java.util.Objects;
import lombok.Builder;

/// 登录设备列表的一项（PAP-99 设计第 11.1 节）。
///
/// @param id 会话 ID
/// @param current 是不是本机
/// @param deviceName 设备名，可以为 `null`
/// @param deviceClass 端
/// @param ipAddress 登录时的 IP，可以为 `null`
/// @param ipRegion 属地，可以为 `null`
/// @param privateNetwork IP 是不是内网地址
/// @param signedInAt 登录时间
/// @param lastActiveAt 最后活跃，精度 60 秒
@Builder
public record SessionView(
    long id,
    boolean current,
    String deviceName,
    DeviceClass deviceClass,
    String ipAddress,
    String ipRegion,
    boolean privateNetwork,
    Instant signedInAt,
    Instant lastActiveAt) {

  /// 端和两个时间不能为 `null`。
  public SessionView {
    Objects.requireNonNull(deviceClass, "deviceClass 不能为 null");
    Objects.requireNonNull(signedInAt, "signedInAt 不能为 null");
    Objects.requireNonNull(lastActiveAt, "lastActiveAt 不能为 null");
  }
}
