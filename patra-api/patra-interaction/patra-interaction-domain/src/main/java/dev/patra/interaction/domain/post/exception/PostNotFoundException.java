package dev.patra.interaction.domain.post.exception;

import dev.patra.interaction.domain.subject.exception.SubjectErrorTrait;
import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.ErrorTrait;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 投稿经验不存在（404）。作者已删除的另带 `SUBJECT_UNAVAILABLE`（{@link #deleted}），与讨论区「对象已删除」同一个原因码。
public final class PostNotFoundException extends DomainException {

  /// 创建异常。
  ///
  /// @param traits 语义特征
  private PostNotFoundException(ErrorTrait... traits) {
    super("投稿经验不存在或已被删除", traits);
  }

  /// 从未存在。
  ///
  /// @return 只带 `NOT_FOUND` 的异常
  public static PostNotFoundException notFound() {
    return new PostNotFoundException(StandardErrorTrait.NOT_FOUND);
  }

  /// 作者已删除（编辑、看修改历史时）。
  ///
  /// @return 带 `NOT_FOUND` 与 `SUBJECT_UNAVAILABLE` 的异常
  public static PostNotFoundException deleted() {
    return new PostNotFoundException(
        StandardErrorTrait.NOT_FOUND, SubjectErrorTrait.SUBJECT_UNAVAILABLE);
  }
}
