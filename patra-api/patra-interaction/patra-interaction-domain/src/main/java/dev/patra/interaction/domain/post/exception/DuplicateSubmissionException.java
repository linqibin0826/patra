package dev.patra.interaction.domain.post.exception;

import dev.patra.interaction.domain.post.model.vo.PostId;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.field.FieldViolation;
import dev.patra.tool.core.error.field.HasFieldViolations;
import dev.patra.tool.core.error.trait.StandardErrorTrait;
import java.util.List;

/// 同一作者在同一期刊下已有同一投稿日期的未删除经验（409 `DUPLICATE_SUBMISSION`）。
///
/// `errors[]` 带 `submittedOn` / `DUPLICATE`（门户把焦点放回投稿日期）；已有经验的 ID 由 adapter 写进响应的
/// `existingPostId`（门户给「查看那条经验」）。只会命中本人的经验，不会泄露别人的匿名经验。
public final class DuplicateSubmissionException extends DomainException
    implements HasFieldViolations {

  private static final String MESSAGE = "同一天投这本期刊的经验只能写一条";

  /// 存原始值：异常可序列化，`PostId` 不是。
  private final long existingPostId;

  /// 创建异常。
  ///
  /// @param existingPostId 已有经验的 ID
  public DuplicateSubmissionException(PostId existingPostId) {
    super(MESSAGE, StandardErrorTrait.CONFLICT, PostErrorTrait.DUPLICATE_SUBMISSION);
    this.existingPostId = existingPostId.value();
  }

  /// 已有经验的 ID。
  ///
  /// @return ID
  public PostId existingPostId() {
    return PostId.of(existingPostId);
  }

  /// 投稿日期字段上的错误。
  ///
  /// @return 一条字段错误
  @Override
  public List<FieldViolation> getFieldViolations() {
    return List.of(FieldViolation.of("submittedOn", "DUPLICATE", MESSAGE));
  }
}
