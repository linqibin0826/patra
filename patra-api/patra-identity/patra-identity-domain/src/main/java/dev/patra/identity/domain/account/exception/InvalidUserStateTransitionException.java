package dev.patra.identity.domain.account.exception;

import dev.patra.identity.domain.account.model.enums.UserStatus;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.Objects;
import lombok.Getter;

/// 账号当前状态不允许这个迁移（比如对已注销的用户封禁、解封），返回 409。
///
/// 起始状态和动作只进字段，不进文案：`detail` 是固定的。
@Getter
public final class InvalidUserStateTransitionException extends DomainException {

  private final UserStatus from;
  private final String action;

  /// 创建异常。
  ///
  /// @param from 起始状态
  /// @param action 被拒绝的动作，用聚合方法名
  public InvalidUserStateTransitionException(UserStatus from, String action) {
    super("账号当前状态不允许此操作", StandardErrorTrait.CONFLICT);
    this.from = Objects.requireNonNull(from, "from 不能为 null");
    this.action = Objects.requireNonNull(action, "action 不能为 null");
  }
}
