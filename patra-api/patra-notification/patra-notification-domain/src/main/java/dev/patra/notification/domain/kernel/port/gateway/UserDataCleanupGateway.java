package dev.patra.notification.domain.kernel.port.gateway;

import java.util.List;

/// 注销清理的扩展点：一个子域里属于某个用户、在他注销后要删除的数据（preference 的偏好与退订令牌，以后 inbox 的
/// 提醒、mail 的发送记录）。
///
/// 由各子域在自己的 app 层实现（命名 `{子域}UserDataCleanupGatewayImpl`），底座的注销清理流程统一枚举用户、回查
/// identity，再逐个子域调 {@link #deleteAllOf}。它是「底座调子域」的方向，和底座自己实现的驱动端口相反：放在
/// `port.gateway`、实现类叫 `*GatewayImpl`，是层规则（端口实现除 `*GatewayImpl` 外必须在 infra）要求的写法。
/// 接口照 interaction 的同名扩展点。
public interface UserDataCleanupGateway {

  /// 子域名，日志里按它列出各子域删掉的行数。
  ///
  /// @return 子域名，如 `preference`
  String subdomain();

  /// 在这个子域里有数据的用户，按用户 ID 升序的一批。
  ///
  /// @param afterUserId 游标（不含）
  /// @param limit 条数
  /// @return 用户 ID
  List<Long> findUsersAfter(long afterUserId, int limit);

  /// 删这个用户在这个子域里的全部数据。在底座开的事务里调用，一个（用户，子域）一个事务。
  ///
  /// @param userId 已注销的用户
  /// @return 删掉的行数
  int deleteAllOf(long userId);
}
