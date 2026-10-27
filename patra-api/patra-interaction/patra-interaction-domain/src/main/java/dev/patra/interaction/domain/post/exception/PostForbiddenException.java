package dev.patra.interaction.domain.post.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 不是作者却要修改或删除（403）。经验本来公开，不用 404 掩盖它的存在。
public final class PostForbiddenException extends DomainException {

  /// 创建异常。
  public PostForbiddenException() {
    super("只有作者能修改或删除这条投稿经验", StandardErrorTrait.FORBIDDEN);
  }
}
