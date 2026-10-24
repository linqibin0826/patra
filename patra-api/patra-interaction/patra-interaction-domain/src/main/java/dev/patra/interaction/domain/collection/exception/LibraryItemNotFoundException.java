package dev.patra.interaction.domain.collection.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 文献库里没有这一条（404）。
public final class LibraryItemNotFoundException extends DomainException {

  /// 创建异常。
  public LibraryItemNotFoundException() {
    super("文献库里没有这一条", StandardErrorTrait.NOT_FOUND);
  }
}
