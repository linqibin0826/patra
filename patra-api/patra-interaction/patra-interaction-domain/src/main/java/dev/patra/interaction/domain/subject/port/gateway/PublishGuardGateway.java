package dev.patra.interaction.domain.subject.port.gateway;

import dev.patra.interaction.domain.subject.exception.PublishIneligibleException;
import dev.patra.interaction.domain.subject.exception.PublishRateLimitedException;
import dev.patra.interaction.domain.subject.exception.UserDirectoryUnavailableException;
import dev.patra.interaction.domain.subject.model.enums.PublishKind;
import dev.patra.interaction.domain.subject.model.read.UserCard;

/// 发表守卫（实现在 app）：讨论与投稿经验写入前的资格与频率检查（决策 E）。
///
/// 调用顺序：事务外先 {@link #requireEligible}（远程调用不占连接），再在写入内容的事务里、第一个写操作之前
/// {@link #admit}。异常都直接传播，由错误引擎映射成 409 / 429 / 503。
public interface PublishGuardGateway {

  /// 资格：回查 identity，要求账号 `ACTIVE`、邮箱已验证。先判账号状态再判邮箱。
  ///
  /// @param userId 作者
  /// @return 自己的名片（访客是自己），调用方可以直接拿来拼响应
  /// @throws PublishIneligibleException 账号状态异常（含 identity 不认识这个人）或邮箱未验证
  /// @throws UserDirectoryUnavailableException identity 不可用
  UserCard requireEligible(long userId);

  /// 频率：拿作者锁，按当天条数与间隔放行一次，并记一笔。**须在写入内容的同一事务里调用**：调用方没开事务时抛
  /// `IllegalTransactionStateException`；事务回滚时这一笔也一起回滚。
  ///
  /// @param userId 作者
  /// @param kind 发表种类
  /// @throws PublishRateLimitedException 离上一条太近或今天的条数到了上限
  void admit(long userId, PublishKind kind);
}
