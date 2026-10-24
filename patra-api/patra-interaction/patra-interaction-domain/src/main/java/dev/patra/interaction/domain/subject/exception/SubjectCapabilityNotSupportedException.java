package dev.patra.interaction.domain.subject.exception;

import dev.patra.interaction.domain.subject.model.enums.SubjectCapability;
import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import lombok.Getter;

/// 能力矩阵里这种对象不支持这种互动（422）。
@Getter
public final class SubjectCapabilityNotSupportedException extends DomainException {

  private final SubjectType type;
  private final SubjectCapability capability;

  /// 创建异常。
  ///
  /// @param type 对象种类
  /// @param capability 请求的互动
  public SubjectCapabilityNotSupportedException(SubjectType type, SubjectCapability capability) {
    super("该对象不支持此操作", StandardErrorTrait.RULE_VIOLATION);
    this.type = type;
    this.capability = capability;
  }
}
