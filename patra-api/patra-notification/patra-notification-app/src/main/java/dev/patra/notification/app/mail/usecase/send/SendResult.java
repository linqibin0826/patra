package dev.patra.notification.app.mail.usecase.send;

/// 一轮发送的计数。
///
/// @param sent 发出的（含「同一个幂等键已被接受」）
/// @param skipped 确定不该发的
/// @param deferred 推迟的（含过期的）
/// @param stoppedEarly 是否因依赖不可用或临时发信故障提前停下
public record SendResult(int sent, int skipped, int deferred, boolean stoppedEarly) {

  /// 创建。
  ///
  /// @param sent 发出的
  /// @param skipped 跳过的
  /// @param deferred 推迟的
  /// @param stoppedEarly 是否提前停下
  /// @return 计数
  public static SendResult of(int sent, int skipped, int deferred, boolean stoppedEarly) {
    return new SendResult(sent, skipped, deferred, stoppedEarly);
  }
}
