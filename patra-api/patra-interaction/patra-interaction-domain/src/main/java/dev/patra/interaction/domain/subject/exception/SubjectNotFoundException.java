package dev.patra.interaction.domain.subject.exception;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import lombok.Getter;

/// 对象不存在、已被来源软删，或没有身份锚（404）。不建登记行。
@Getter
public final class SubjectNotFoundException extends DomainException {

  private final SubjectType type;
  private final String key;

  /// 创建异常。
  ///
  /// @param type 对象种类
  /// @param key 对外 key
  public SubjectNotFoundException(SubjectType type, String key) {
    super("文献不存在或已被删除", StandardErrorTrait.NOT_FOUND);
    this.type = type;
    this.key = key;
  }
}
