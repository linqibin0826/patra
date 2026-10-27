package dev.patra.interaction.domain.discussion.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.error.field.HasFieldViolations;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;

/// 正文不合法（422），`errors[]` 里是 `body` 字段上的一条错误：空、含不允许的字符、超长（字素或字节）、@ 超过 10 人。
public final class InvalidCommentException extends DomainException implements HasFieldViolations {

  /// 字段名。
  public static final String FIELD = "body";

  /// 字段码：去掉首尾空白后为空。
  public static final String REQUIRED = "REQUIRED";

  /// 字段码：含控制字符或行 / 段分隔符。
  public static final String INVALID_CHARACTER = "INVALID_CHARACTER";

  /// 字段码：超过 5,000 字素或 20,000 字节。
  public static final String TOO_LONG = "TOO_LONG";

  /// 字段码：@ 超过 10 人（或候选超过 100 个）。
  public static final String TOO_MANY_MENTIONS = "TOO_MANY_MENTIONS";

  private final String code;

  /// 创建异常。
  ///
  /// @param code 字段码
  /// @param message 默认文案
  private InvalidCommentException(String code, String message) {
    super(message, StandardErrorTrait.RULE_VIOLATION);
    this.code = code;
  }

  /// 正文为空。
  ///
  /// @return 异常
  public static InvalidCommentException required() {
    return new InvalidCommentException(REQUIRED, "写点什么再发表吧");
  }

  /// 正文含不允许的字符。
  ///
  /// @return 异常
  public static InvalidCommentException invalidCharacter() {
    return new InvalidCommentException(INVALID_CHARACTER, "含有不允许的字符");
  }

  /// 正文超长。
  ///
  /// @return 异常
  public static InvalidCommentException tooLong() {
    return new InvalidCommentException(TOO_LONG, "最多 5,000 字");
  }

  /// @ 的人太多。
  ///
  /// @return 异常
  public static InvalidCommentException tooManyMentions() {
    return new InvalidCommentException(TOO_MANY_MENTIONS, "一条评论最多 @ 10 人");
  }

  /// 正文字段上的错误。
  ///
  /// @return 一条字段错误
  @Override
  public List<FieldViolation> getFieldViolations() {
    return List.of(FieldViolation.of(FIELD, code, getMessage()));
  }
}
