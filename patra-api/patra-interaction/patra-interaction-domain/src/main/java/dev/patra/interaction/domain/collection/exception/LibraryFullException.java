package dev.patra.interaction.domain.collection.exception;

import dev.patra.tool.core.error.DomainException;
import dev.patra.tool.core.error.trait.StandardErrorTrait;

/// 文献库已满 20,000 条（409 `LIBRARY_FULL`）。
public final class LibraryFullException extends DomainException {

  /// 创建异常。
  public LibraryFullException() {
    super("文献库已满 20,000 篇", StandardErrorTrait.CONFLICT, CollectionErrorTrait.LIBRARY_FULL);
  }
}
