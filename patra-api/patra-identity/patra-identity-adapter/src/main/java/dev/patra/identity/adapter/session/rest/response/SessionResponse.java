package dev.patra.identity.adapter.session.rest.response;

import dev.patra.identity.app.session.usecase.query.SessionView;
import dev.patra.identity.domain.session.model.enums.DeviceClass;
import java.time.Instant;
import lombok.Builder;

/// 登录设备列表的一项。`id` 是会话 ID（按全局约定输出成字符串）。
///
/// @param id 会话 ID
/// @param current 是不是本机
/// @param deviceName 设备名（浏览器主版本 · 系统族），解析不出为 `null`
/// @param deviceClass 端：`DESKTOP`、`MOBILE`、`TABLET`、`UNKNOWN`
/// @param ipAddress 登录时的 IP，可以为 `null`
/// @param ipRegion 属地（国家 / 省 / 市，境外到国家），内网与查不到为 `null`
/// @param privateNetwork IP 是不是内网地址（门户显示「内网」）
/// @param signedInAt 登录时间
/// @param lastActiveAt 最后活跃，精度 60 秒
@Builder
public record SessionResponse(
    Long id,
    boolean current,
    String deviceName,
    DeviceClass deviceClass,
    String ipAddress,
    String ipRegion,
    boolean privateNetwork,
    Instant signedInAt,
    Instant lastActiveAt) {

  /// 从查询结果映射。
  ///
  /// @param view 查询结果
  /// @return 响应
  public static SessionResponse from(SessionView view) {
    return SessionResponse.builder()
        .id(view.id())
        .current(view.current())
        .deviceName(view.deviceName())
        .deviceClass(view.deviceClass())
        .ipAddress(view.ipAddress())
        .ipRegion(view.ipRegion())
        .privateNetwork(view.privateNetwork())
        .signedInAt(view.signedInAt())
        .lastActiveAt(view.lastActiveAt())
        .build();
  }
}
