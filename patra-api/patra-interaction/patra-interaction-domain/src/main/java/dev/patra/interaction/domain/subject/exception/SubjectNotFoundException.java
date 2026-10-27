package dev.patra.interaction.domain.subject.exception;

import dev.patra.interaction.domain.subject.model.enums.SubjectType;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.ErrorTrait;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import lombok.Getter;

/// 对象不存在、已被删除，或没有身份锚（404）。不建登记行。
///
/// 已登记但失效、首次解析时来源已软删的，另带原因码 `SUBJECT_UNAVAILABLE`（{@link #unavailable}）；其余只有
/// `NOT_FOUND`（{@link #notFound}）。
@Getter
public final class SubjectNotFoundException extends DomainException {

  private final SubjectType type;
  private final String key;

  /// 创建异常。
  ///
  /// @param type 对象种类
  /// @param key 对外 key
  /// @param traits 语义特征
  private SubjectNotFoundException(SubjectType type, String key, ErrorTrait... traits) {
    super(type.displayName() + "不存在或已被删除", traits);
    this.type = type;
    this.key = key;
  }

  /// 从未存在：来源说不存在、没有身份锚，或本地对象没有登记。
  ///
  /// @param type 对象种类
  /// @param key 对外 key
  /// @return 只带 `NOT_FOUND` 的异常
  public static SubjectNotFoundException notFound(SubjectType type, String key) {
    return new SubjectNotFoundException(type, key, StandardErrorTrait.NOT_FOUND);
  }

  /// 存在过但已失效：已登记但 UNAVAILABLE，或首次解析时来源已软删。
  ///
  /// @param type 对象种类
  /// @param key 对外 key
  /// @return 带 `NOT_FOUND` 与 `SUBJECT_UNAVAILABLE` 的异常
  public static SubjectNotFoundException unavailable(SubjectType type, String key) {
    return new SubjectNotFoundException(
        type, key, StandardErrorTrait.NOT_FOUND, SubjectErrorTrait.SUBJECT_UNAVAILABLE);
  }
}
