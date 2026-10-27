package dev.patra.interaction.domain.subject.exception;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import lombok.Getter;

/// 来源（catalog）不可用：连不上、超时、没有实例或回了错误（503）。首次互动直接失败、提示重试，
/// 不先收下再补校验。
@Getter
public final class SubjectSourceUnavailableException extends DomainException {

  private final SubjectType type;

  /// 创建异常并保留原因。
  ///
  /// @param type 对象种类
  /// @param cause 底层异常
  public SubjectSourceUnavailableException(SubjectType type, Throwable cause) {
    super(type.displayName() + "信息暂时无法获取，请稍后再试", cause, StandardErrorTrait.DEP_UNAVAILABLE);
    this.type = type;
  }
}
