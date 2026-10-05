package dev.linqibin.starter.jpa.audit;

import java.util.Optional;

/// 当前操作人提供者：告诉 JPA 审计「这次写入是谁做的」。
///
/// 容器里有这个接口的实现时，`created_by` / `updated_by` 填它返回的 ID；
/// 没有实现时两列为空。实现方通常是安全模块，从当前登录用户取 ID。
@FunctionalInterface
public interface CurrentAuditorProvider {

  /// 返回当前操作人的 ID。
  ///
  /// @return 操作人 ID；匿名请求、不带身份的后台线程返回空
  Optional<Long> currentAuditorId();
}
