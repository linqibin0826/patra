package dev.patra.interaction.domain.subject.exception;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import lombok.Getter;

/// 对外 key 的格式不对（422）。key 原文不进消息，只留给日志。
@Getter
public final class InvalidSubjectKeyException extends DomainException {

  private final SubjectType type;
  private final String key;

  /// 创建异常。
  ///
  /// @param type 对象种类
  /// @param key key 原文
  public InvalidSubjectKeyException(SubjectType type, String key) {
    super("对象标识格式不正确", StandardErrorTrait.RULE_VIOLATION);
    this.type = type;
    this.key = key;
  }
}
