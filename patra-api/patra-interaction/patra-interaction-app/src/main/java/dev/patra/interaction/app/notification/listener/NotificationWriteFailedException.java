package dev.patra.interaction.app.notification.listener;

import dev.patra.tool.core.error.ApplicationException;
import dev.patra.tool.core.error.codes.HttpStdErrors;

/// 订阅讨论事件时写提醒失败（500）。评论事务随之回滚；原始异常只留在原因链里。
///
/// 不让数据库异常原样冒出去：完整性异常会被映射成 409，按类名兜底的会被映射成 422，发评论的人会看到误导的原因。
public class NotificationWriteFailedException extends ApplicationException {

  /// 创建异常。
  ///
  /// @param cause 原始异常
  public NotificationWriteFailedException(Throwable cause) {
    super(HttpStdErrors.of("INTERACTION").INTERNAL_ERROR(), "提醒写入失败", cause);
  }
}
