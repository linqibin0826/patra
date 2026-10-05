package dev.linqibin.patra.starter.security.context;

import dev.linqibin.patra.common.security.CurrentUser;
import dev.linqibin.patra.starter.security.authentication.CurrentUserAuthentication;
import java.util.Objects;
import java.util.function.Supplier;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;

/// 以某个用户的身份执行一段代码。
///
/// 定时任务、消息消费这些不在请求里的线程，需要带身份时用它。不调它时，
/// `CurrentUserPort.current()` 得到空结果，审计列留空。
public final class CurrentUserRunner {

  /// 工具类，不允许实例化。
  private CurrentUserRunner() {}

  /// 以指定用户的身份执行动作。
  ///
  /// @param user 用户
  /// @param action 要执行的动作
  public static void runAs(CurrentUser user, Runnable action) {
    Objects.requireNonNull(action, "action 不能为 null");
    callAs(
        user,
        () -> {
          action.run();
          return null;
        });
  }

  /// 以指定用户的身份执行动作并返回结果。
  ///
  /// 进去时把这个用户放进安全上下文；出来时恢复原来的上下文，原来是空的就清空。
  /// 动作抛异常时同样恢复。
  ///
  /// @param user 用户
  /// @param action 要执行的动作
  /// @param <T> 结果类型
  /// @return 动作的结果
  public static <T> T callAs(CurrentUser user, Supplier<T> action) {
    Objects.requireNonNull(user, "user 不能为 null");
    Objects.requireNonNull(action, "action 不能为 null");
    SecurityContextHolderStrategy strategy = SecurityContextHolder.getContextHolderStrategy();
    SecurityContext original = strategy.getContext();
    SecurityContext context = strategy.createEmptyContext();
    context.setAuthentication(new CurrentUserAuthentication(user));
    strategy.setContext(context);
    try {
      return action.get();
    } finally {
      if (strategy.createEmptyContext().equals(original)) {
        strategy.clearContext();
      } else {
        strategy.setContext(original);
      }
    }
  }
}
