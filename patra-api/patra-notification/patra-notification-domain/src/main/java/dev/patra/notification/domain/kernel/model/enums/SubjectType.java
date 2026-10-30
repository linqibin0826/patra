package dev.patra.notification.domain.kernel.model.enums;

import java.util.Arrays;
import java.util.Optional;

/// 评论所在对象的类型（interaction 登记表里的对象种类），收件人规则与对象视图都要用。只认提醒涉及的两种。
public enum SubjectType {

  /// 文献。
  PUBLICATION,

  /// 投稿经验。
  POST;

  /// 按 interaction 给的枚举名解析；不认识的不抛异常（PAP-141 契约：枚举可能加值）。
  ///
  /// @param raw 原始值，可空
  /// @return 类型；不认识为空
  public static Optional<SubjectType> parse(String raw) {
    return Arrays.stream(values()).filter(t -> t.name().equals(raw)).findFirst();
  }
}
