package dev.patra.identity.domain.account.port.throttle;

import java.time.Duration;

/// 按客户端网络的登录失败计数，用来发现撞库：一个网络试很多邮箱时，按邮箱计数到不了门槛。
///
/// 只负责计数，不封禁，到门槛后由人机验证把关。登录成功不清零，否则攻击者用自己的账号登录一次就能清掉计数。
/// 拿不到 IP 或 IP 不合法时不计数，也不把这些请求算到同一个键。
public interface LoginIpFailurePort {

  /// 读失败次数。
  ///
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @return 失败次数；拿不到 IP 时为 0
  int count(String clientIp);

  /// 记一次失败。窗口从第一次失败算起，后面的失败不续期。
  ///
  /// @param clientIp 客户端 IP，可以为 `null`
  /// @param window 计数窗口
  void record(String clientIp, Duration window);
}
