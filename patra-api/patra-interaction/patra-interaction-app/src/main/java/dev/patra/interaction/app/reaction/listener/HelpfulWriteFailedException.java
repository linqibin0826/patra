package dev.patra.interaction.app.reaction.listener;

import dev.patra.tool.core.error.ApplicationException;
import dev.patra.tool.core.error.codes.HttpStdErrors;

/// 删除联动时清有帮助失败（500）。评论 / 经验的删除随之回滚；原始异常只留在原因链里。
///
/// 不让数据库异常原样冒出去：完整性异常会被映射成 409，删内容的人会看到误导的原因。
public class HelpfulWriteFailedException extends ApplicationException {

  /// 创建异常。
  ///
  /// @param cause 原始异常
  public HelpfulWriteFailedException(Throwable cause) {
    super(HttpStdErrors.of("INTERACTION").INTERNAL_ERROR(), "有帮助清理失败", cause);
  }
}
