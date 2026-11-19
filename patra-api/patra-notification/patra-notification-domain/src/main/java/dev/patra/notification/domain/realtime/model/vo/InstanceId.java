package dev.patra.notification.domain.realtime.model.vo;

import java.util.UUID;

/// 通知服务一个运行实例的身份，用来在未读数广播里认出「这是我自己发的」。
///
/// 每个实例启动时随机生成一个，不取主机名或进程号：集成测试会在同一个 JVM 里起两个实例，它们必须互不相同。
///
/// @param value 标识
public record InstanceId(String value) {

  /// 校验。
  ///
  /// @throws IllegalArgumentException 标识为空或空白
  public InstanceId {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("实例 ID 不能为空");
    }
  }

  /// 随机生成一个。
  ///
  /// @return 新的实例 ID
  public static InstanceId random() {
    return new InstanceId(UUID.randomUUID().toString());
  }
}
